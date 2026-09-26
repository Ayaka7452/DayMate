# DayMate

一款轻量的 Android 倒数日应用，记录你的期待与坚持。

## 功能

- **倒数日**：还有 N 天 / 已过 N 天，按天 / 月 / 年显示，支持对照日期与循环重复
- **节日卡片**：法定节假日倒计时，休 / 班状态、连休天数一卡看清
- **跟随节日**：事件锚定节日后每年自动倒计时，假期内显示「假期第 x 天」
- **文件夹**：整理事件，支持批量管理
- **Vault**：独立空间，密码 + 生物识别（指纹）双重解锁，防截屏保护
- **周期管家**：私密的经期记录与预测
- **桌面小组件**：不打开应用也能看到最近的倒数
- **数据备份**：本地备份与 WebDAV 云同步
- **应用内更新**：自动检查新版本并安装
- **主题与语言**：自动浅色 / 深色主题，多套配色，支持简体中文 / 繁体中文 / 粤语 / English / 日本語 / 한국어

## 下载

从 [Releases](https://github.com/Ayaka7452/DayMate/releases) 下载最新安装包，或在应用内直接检查更新。

日常更新以体验优化和问题修复为主，具体变更见各版本的 Release 说明。

## 技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose (Material 3) |
| 架构 | MVVM + Repository |
| 存储 | Room（主空间 / Vault 双库）+ DataStore |
| 安全 | PBKDF2 密码哈希 + BiometricPrompt + FLAG_SECURE |
| 构建 | Gradle Kotlin DSL + Version Catalog |
| CI | GitHub Actions |

## 构建

```bash
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/`

GitHub Actions 每次 push 到 `main` 会自动编译，可在仓库 Actions 页下载 Debug APK artifact。

## 许可证

[MIT](LICENSE) © Ayaka7452
