# XNAT Android

> XNAT 官方原生 Android 客户端，通过 **Mobile API v1** 连接 XNAT Panel。

> **项目状态：Beta / 测试版** — 当前版本用于持续测试与功能验证，建议配合 XNAT Panel 测试环境使用。

**当前测试版本：v1.0.5（Beta）**

<div align="center">

### [查看介绍](docs/INTRODUCTION.md)

</div>

---

## GitHub 自动更新

当前 Beta Release 包含：

```text
XNAT-Android-v1.0.5.apk
XNAT-Android-v1.0.5.apk.sha256
```

App 使用 GitHub `releases/latest` 检查正式版本，因此开发版或 RC 应发布为 **Pre-release**，不会作为正式更新推送。

GitHub Actions 构建时自动使用当前 `GITHUB_REPOSITORY` 作为更新仓库；本地构建可通过：

```bash
XNAT_GITHUB_REPO=owner/repo
```

覆盖默认仓库。

---

## Beta 构建

仓库不保存签名私钥。GitHub Actions 需要配置：

```text
XNAT_KEYSTORE_BASE64
XNAT_KEYSTORE_PASSWORD
XNAT_KEY_ALIAS
XNAT_KEY_PASSWORD
```

正式构建流程会：

1. 使用 JDK 17 / Gradle 8.13 / Android SDK 36 构建 Release APK；
2. 使用正式 Keystore 签名；
3. 使用 `apksigner` 验证 APK 签名；
4. 生成 APK 与 SHA-256 校验文件。

当前 Beta 构建产物：

```text
XNAT-Android-v1.0.5.apk
XNAT-Android-v1.0.5.apk.sha256
```

---

## 安全说明

Release 构建默认禁止明文 HTTP Panel；正式使用建议始终配置 HTTPS。Debug 构建可临时连接 HTTP Panel，App 会在发送凭据前提示风险。

正式签名证书应持续使用同一套密钥。只要 Application ID 与签名证书保持一致，后续正式版本可以覆盖安装并保留本地登录、主题和设置。

---

## 免责声明

本项目仅供学习、研究与技术交流使用。使用者应遵守所在地法律法规，不得将本项目用于任何违法或未经授权的用途。因使用本项目产生的任何违法行为、损失或法律责任均由使用者自行承担，与项目作者及贡献者无关。

---

<div align="center">

### XNAT Android

**由 NAMELESS 和 GPT 倾力打造**

</div>
