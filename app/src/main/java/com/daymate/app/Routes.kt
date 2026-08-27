package com.ayaka7452.daymate

/**
 * 路由常量。多 Activity 架构下仅作为 onNavigate(String) 的语义化字符串，
 * 实际跳转由 [route] 解析为 startActivity。
 */
object Routes {
    const val HOME = "home"
    const val EVENT_FORM = "event_form"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val VAULT = "vault"
    const val FOLDER = "folder/{folderId}"
    const val VAULT_FOLDER = "vault_folder/{folderId}"
}
