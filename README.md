# DayMate

一款轻量的 Android 倒数日应用，记录你的期待与坚持。

## 功能

- **倒数日列表**：创建事件，显示"还有 N 天 / 已过 N 天"
- **文件夹**：整理事件，支持批量管理
- **Vault**：独立空间，密码 + 生物识别（指纹）双重解锁，防截屏保护
- **设置**：自动黑白主题（跟随系统 / 浅色 / 深色）、默认排序
- **关于**：版本信息

## 技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.0 |
| UI | Jetpack Compose (Material 3) |
| 架构 | MVVM + Repository |
| 存储 | Room（主空间 / Vault 双库）+ DataStore |
| 安全 | PBKDF2 密码哈希 + BiometricPrompt + FLAG_SECURE |
| 构建 | Gradle Kotlin DSL + Version Catalog |
| CI | GitHub Actions（自动编译 Debug APK） |

## 构建

```bash
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/`

GitHub Actions 每次 push 到 `main` 会自动编译，可在仓库 Actions 页下载 Debug APK artifact。

## 版本

- v0.1.0-alpha：核心功能（主页 / 事件 / 文件夹 / Vault / 设置 / 关于）
