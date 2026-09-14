package com.ayaka7452.daymate.core.cloud

import android.util.Xml
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** WebDAV 上的一个条目（文件或集合）。 */
data class WebDavEntry(
    /** 显示名（已 URL 解码）。 */
    val name: String,
    /** 相对 baseUrl 的路径（已解码、无前导/尾随斜杠），例如 `DayMate/daymate.db`。 */
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: String? = null
)

/**
 * WebDAV 操作失败。[code] 为 HTTP 状态码（网络层错误为 0），[message] 可直接展示给用户。
 */
class WebDavException(val code: Int, message: String) : Exception(message)

/**
 * 极简 WebDAV 客户端（只覆盖备份所需：列目录 / 建目录 / 上传 / 下载 / 删除 / 探测）。
 *
 * 为什么用 OkHttp：PROPFIND 与 MKCOL 属于自定义 HTTP 方法，`HttpURLConnection` 白名单
 * 只允许 GET/POST/HEAD/OPTIONS/PUT/DELETE/TRACE，无法发送。
 *
 * 实现要点：
 *  - **Basic 认证**（绝大多数 WebDAV 服务：坚果云 / Nextcloud / 群晖 / Alist 均支持）；
 *  - **手动处理重定向**：OkHttp 默认会把非 GET/HEAD 的 301/302 改成 GET，会让 PUT/PROPFIND
 *    在服务器补斜杠跳转时静默失败，故关闭自动跳转、自己按 Location 重发并保留方法与认证头；
 *  - 可选信任自签名证书（自建 NAS 场景），默认关闭。
 *
 * 所有方法均为阻塞调用，请在 IO 线程使用。
 */
class WebDavClient(private val config: WebDavConfig) {

    private companion object {
        const val MAX_REDIRECTS = 4
        const val XML_TYPE = "application/xml; charset=utf-8"
        val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:displayname/>
                <d:resourcetype/>
                <d:getcontentlength/>
                <d:getlastmodified/>
              </d:prop>
            </d:propfind>
        """.trimIndent()
    }

    /** 规范化后的 baseUrl，保证以 `/` 结尾（URI.resolve 才会把最后一段当目录）。 */
    private val base: String = config.url.trim().let { if (it.endsWith("/")) it else "$it/" }

    private val auth: String? =
        if (config.username.isNotEmpty()) Credentials.basic(config.username, config.password) else null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        // 关闭自动跳转：见类注释（保方法 + 保认证头）
        .followRedirects(false)
        .followSslRedirects(false)
        .apply { if (config.allowSelfSigned) trustAll(this) }
        .build()

    // ===== 对外操作 =====

    /** 测试连通性与凭据：对 baseUrl 做一次 Depth:0 的 PROPFIND，非 207 视为不是 WebDAV 服务。 */
    fun testConnection() {
        val code = perform(
            method = "PROPFIND",
            url = base,
            headers = mapOf("Depth" to "0", "Content-Type" to XML_TYPE),
            bodyFactory = { PROPFIND_BODY.toRequestBody(XML_TYPE.toMediaType()) }
        ) { it.code }
        if (code != 207 && code != 200) {
            throw WebDavException(code, "服务器未按 WebDAV 协议响应（$code），请确认地址与路径")
        }
    }

    /** 列出 [path]（相对 baseUrl 的目录）下的直接子项；不包含它自身。 */
    fun list(path: String): List<WebDavEntry> {
        val xml = perform(
            method = "PROPFIND",
            url = fullUrl(path, isCollection = true),
            headers = mapOf("Depth" to "1", "Content-Type" to XML_TYPE),
            bodyFactory = { PROPFIND_BODY.toRequestBody(XML_TYPE.toMediaType()) }
        ) { it.body?.string().orEmpty() }
        val self = path.trim().trim('/')
        return parseMultiStatus(xml).filter { it.path != self }
    }

    /** 取资源元信息；不存在返回 null。 */
    fun statOrNull(path: String, isCollection: Boolean): WebDavEntry? = try {
        val xml = perform(
            method = "PROPFIND",
            url = fullUrl(path, isCollection),
            headers = mapOf("Depth" to "0", "Content-Type" to XML_TYPE),
            bodyFactory = { PROPFIND_BODY.toRequestBody(XML_TYPE.toMediaType()) }
        ) { it.body?.string().orEmpty() }
        parseMultiStatus(xml).firstOrNull()
    } catch (e: WebDavException) {
        if (e.code == 404) null else throw e
    }

    /** 资源是否存在。 */
    fun exists(path: String, isCollection: Boolean = false): Boolean =
        statOrNull(path, isCollection) != null

    /** 创建集合（目录）。父目录必须已存在；已存在时服务器返回 405，这里静默容忍。 */
    fun mkcol(path: String) {
        try {
            perform("MKCOL", fullUrl(path, isCollection = true)) { "" }
        } catch (e: WebDavException) {
            if (e.code != 405) throw e
        }
    }

    /** 逐级确保 [path] 目录存在。 */
    fun ensureDirectory(path: String) {
        var acc = ""
        for (seg in path.trim().trim('/').split('/')) {
            if (seg.isBlank()) continue
            acc = if (acc.isEmpty()) seg else "$acc/$seg"
            if (statOrNull(acc, isCollection = true) != null) continue
            mkcol(acc)
        }
    }

    /** 上传本地文件到 [path]（覆盖同名远程文件）。 */
    fun upload(path: String, file: File, contentType: String = "application/octet-stream") {
        perform(
            method = "PUT",
            url = fullUrl(path, isCollection = false),
            bodyFactory = { file.asRequestBody(contentType.toMediaType()) }
        ) { it.code }
    }

    /**
     * 下载 [path] 到本地 [dest]。
     *
     * 先写同目录的 `.part` 临时文件、**下载全部成功后**才替换 [dest]：
     * 否则中途失败（网络断开 / 404）会先把 [dest] 截断成空文件——若 [dest] 正是应用主库，
     * 就等于一次静默的数据毁坏。
     */
    fun download(path: String, dest: File) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        tmp.delete()
        try {
            perform(method = "GET", url = fullUrl(path, isCollection = false)) { resp ->
                val body = resp.body ?: throw WebDavException(0, "服务器未返回内容")
                body.byteStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                0
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }

    /** 删除远程资源；不存在时静默返回。 */
    fun delete(path: String) {
        try {
            perform("DELETE", fullUrl(path, isCollection = false)) { "" }
        } catch (e: WebDavException) {
            if (e.code != 404) throw e
        }
    }

    // ===== 内部实现 =====

    /**
     * 发一次请求并把响应交给 [onSuccess] 转换；自动跟随 3xx（保留方法与认证头）。
     * [bodyFactory] 每次尝试都重新构造 body——RequestBody 只能被消费一次，重定向后需要新实例。
     */
    private fun <T> perform(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        bodyFactory: (() -> RequestBody?)? = null,
        onSuccess: (Response) -> T
    ): T {
        var currentUrl = url
        var hops = 0
        while (true) {
            val builder = Request.Builder().url(currentUrl).method(method, bodyFactory?.invoke())
            auth?.let { builder.header("Authorization", it) }
            for ((k, v) in headers) builder.header(k, v)
            val resp = try {
                client.newCall(builder.build()).execute()
            } catch (e: Throwable) {
                throw WebDavException(0, describeNetworkError(e))
            }
            val location = resp.header("Location")
            if (resp.code in 300..399 && hops < MAX_REDIRECTS && location != null && location.isNotBlank()) {
                resp.close()
                currentUrl = resolveLocation(currentUrl, location)
                hops++
                continue
            }
            try {
                checkResponse(resp, currentUrl)
                return onSuccess(resp)
            } finally {
                resp.close()
            }
        }
    }

    private fun checkResponse(resp: Response, url: String) {
        // 207 Multi-Status 是 WebDAV 的正常成功响应，但 isSuccessful 只认 2xx —— 实际含 207，
        // 此处显式放行以免误判。
        if (resp.isSuccessful || resp.code == 207) return
        val msg = when (resp.code) {
            401 -> "认证失败：用户名或密码不正确"
            403 -> "没有权限访问该路径（403）"
            404 -> "远程路径不存在（404）"
            405 -> "服务器不允许该操作（405）"
            409 -> "远程父目录不存在（409）"
            423 -> "远程资源被锁定（423）"
            507 -> "云端空间不足（507）"
            else -> "服务器返回 ${resp.code} ${resp.message}"
        }
        throw WebDavException(resp.code, msg)
    }

    private fun describeNetworkError(e: Throwable): String = when (e) {
        is SSLHandshakeException ->
            "TLS 握手失败：服务器证书不受信任（自建服务器可开启「允许自签名证书」）"
        is UnknownHostException -> "无法解析服务器地址，请检查域名是否正确"
        is ConnectException -> "无法连接服务器，请检查地址与端口"
        is SocketTimeoutException -> "连接超时，请检查网络或服务器状态"
        else -> "网络错误：${e.message ?: e.javaClass.simpleName}"
    }

    /** 拼接完整 URL。目录请求保留尾随 `/`（部分服务器据此区分集合），文件请求不带。 */
    private fun fullUrl(path: String, isCollection: Boolean): String {
        val cleaned = path.trim().trim('/')
        if (cleaned.isEmpty()) return base
        val sb = StringBuilder(base)
        for (seg in cleaned.split('/')) {
            if (seg.isBlank()) continue
            sb.append(encodeSegment(seg)).append('/')
        }
        // 文件路径去掉尾随斜杠（目录保留）
        if (!isCollection) sb.setLength(sb.length - 1)
        return sb.toString()
    }

    private fun parseMultiStatus(xml: String): List<WebDavEntry> {
        if (xml.isBlank()) return emptyList()
        val out = mutableListOf<WebDavEntry>()
        val parser = Xml.newPullParser()
        return try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            var pendingTag: String? = null
            var href: String? = null
            var isDirectory = false
            var size = 0L
            var modified: String? = null
            var display: String? = null
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = localName(parser)
                        pendingTag = name
                        when (name) {
                            "response" -> {
                                href = null; isDirectory = false
                                size = 0L; modified = null; display = null
                            }
                            // 集合标志：<d:resourcetype><d:collection/></d:resourcetype>
                            "collection" -> isDirectory = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) when (pendingTag) {
                            "href" -> href = text
                            "getcontentlength" -> size = text.toLongOrNull() ?: 0L
                            "getlastmodified" -> modified = text
                            "displayname" -> display = text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (localName(parser) == "response") {
                            href?.let { toEntry(it, isDirectory, size, modified, display)?.let(out::add) }
                        }
                        pendingTag = null
                    }
                }
                event = parser.next()
            }
            out
        } catch (_: Throwable) {
            // 非法/截断的 XML 不应让整个备份流程崩溃，已解析出的条目仍然可用
            out
        }
    }

    /** 把响应里的 href 归一成「相对 baseUrl 的路径」并构造条目；无法归一（如 base 之外的资源）返回 null。 */
    private fun toEntry(
        rawHref: String,
        isDirectory: Boolean,
        size: Long,
        modified: String?,
        display: String?
    ): WebDavEntry? {
        val absUrl = runCatching { URI(resolveLocation(base, rawHref)) }.getOrNull() ?: return null
        val fullPath = absUrl.path ?: return null
        val basePath = runCatching { URI(base).path }.getOrNull().orEmpty()
        val rel = if (fullPath.startsWith(basePath)) fullPath.removePrefix(basePath) else fullPath
        val decoded = rel.split('/').filter { it.isNotBlank() }.joinToString("/") { decode(it) }
        if (decoded.isBlank()) return null
        return WebDavEntry(
            name = display?.takeIf { it.isNotBlank() } ?: decoded.substringAfterLast('/'),
            path = decoded,
            isDirectory = isDirectory,
            size = size,
            lastModified = modified
        )
    }

    private fun localName(parser: XmlPullParser): String =
        parser.name?.substringAfterLast(':').orEmpty()

    private fun encodeSegment(seg: String): String =
        URLEncoder.encode(seg, "UTF-8").replace("+", "%20")

    private fun decode(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun resolveLocation(from: String, location: String): String =
        runCatching { URI(from).resolve(location).toString() }.getOrDefault(location)

    /** 只应在用户显式开启「允许自签名证书」时调用：信任一切证书，仅供自建 NAS 使用。 */
    private fun trustAll(builder: OkHttpClient.Builder) {
        runCatching {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), null)
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
    }
}
