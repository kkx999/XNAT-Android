# XNAT Android

> XNAT 官方原生 Android 客户端。

XNAT Android 通过 **Mobile API v1** 连接 XNAT Panel，为用户提供移动端的服务器管理、套餐购买、订单与工单等常用功能。App 只连接 Panel，不直接访问 Host Agent。

**当前正式版本：v1.0.1**

| 项目 | 版本 |
| --- | --- |
| XNAT Android | v1.0.1 |
| XNAT Panel | v1.0.2 |
| Mobile API | v1 |
| Application ID | `com.xnat.mobile` |
| Min SDK | 26 |
| Target / Compile SDK | 36 |

> v1.0.1 是兼容性体验更新：系统镜像选择会提前提示最低系统盘要求，不兼容镜像不可选择；现有 UI 架构、页面布局、视觉风格和主要交互保持不变。

---

## 能做什么

- **连接 XNAT Panel**：填写 Panel 地址并登录，后续请求统一走 Mobile API v1。
- **查看账户与服务器**：首页展示账户概况、服务器列表与主要资源信息。
- **管理服务器**：支持开机、关机、重启、重装系统、删除等常用操作。
- **购买与订单**：浏览 Panel 动态下发的套餐和系统镜像，并完成购买、续费及订单查看。
- **工单支持**：新建工单、查看工单详情并继续回复。
- **主题设置**：保留现有明暗主题与本地设置。
- **应用更新**：正式版通过 GitHub `releases/latest` 检查最新正式 Release。

---

## 与 XNAT 的关系

```text
XNAT Android
     │
     │ HTTPS / Mobile API v1
     ▼
XNAT Panel v1.0.2
     │
     │ Agent API v2
     ▼
Host Agent / Incus
```

Android 客户端不保存或使用 Host Agent Token，也不会直接连接 Host 管理端口。服务器、套餐、系统镜像与业务状态均由 Panel 统一下发。

---

## v1.0.1 更新

- 购买服务器和重装系统两个镜像选择入口都会提前校验系统盘兼容性。
- Alpine 默认按至少 1 GB、Debian / Ubuntu 默认按至少 2 GB、KVM 至少按 4 GB 判断，与 XNAT Panel v1.0.2 当前规则一致。
- 不兼容镜像仍保留在列表中，但会置灰并显示 `需 ≥xG`，点击时给出明确中文提示，不再等提交后才看到 409。
- 如果连接的旧 Panel 无法提供服务器磁盘信息，Android 不会误拦截，仍交给 Panel 后端最终校验。
- 代码预留读取未来 `min_disk_gb` 字段的能力，Mobile API 仍保持 v1。
- 不修改现有 UI 架构、主题、页面布局和主要交互方式。

---

## v1.0.0 基线

v1.0.0 是重新整理后的 Android 正式基线，现有 App 功能逻辑继续沿用已经稳定的代码：

- 对外版本重新从 **v1.0.0** 开始。
- 对接 **XNAT Panel / Mobile API v1**。
- 保留小容量 LXC 套餐磁盘规格的正确显示。
- 保留重装、删除、开关机等操作的顶部错误提示。
- 保留网络、权限、Host 离线和服务端异常的中文错误说明。
- 保留 Panel 动态下发的系统镜像与 Alpine 3.24 图标识别。
- **不修改现有 UI 架构、页面布局、主题和主要交互方式。**

Android v1.0.1 的 `versionName` 为 `1.0.1`，内部 `versionCode` 递增为 `10208`，继续保证使用相同正式签名时可以覆盖安装。

---

## GitHub 自动更新

正式 Release 至少包含：

```text
XNAT-Android-v1.0.1.apk
XNAT-Android-v1.0.1.apk.sha256
```

App 使用 GitHub `releases/latest` 检查正式版本，因此开发版或 RC 应发布为 **Pre-release**，不会作为正式更新推送。

GitHub Actions 构建时自动使用当前 `GITHUB_REPOSITORY` 作为更新仓库；本地构建可通过：

```bash
XNAT_GITHUB_REPO=owner/repo
```

覆盖默认仓库。

---

## 正式构建

仓库不保存签名私钥。GitHub Actions 需要配置：

```text
XNAT_KEYSTORE_BASE64
XNAT_KEYSTORE_PASSWORD
XNAT_KEY_ALIAS
XNAT_KEY_PASSWORD
```

正式构建流程会：

1. 使用 JDK 17 / Gradle 8.13 / Android SDK 36 构建 Release APK。
2. 使用正式 Keystore 签名。
3. 使用 `apksigner` 验证 APK 签名。
4. 生成 APK 与 SHA-256 校验文件。

当前正式构建产物：

```text
XNAT-Android-v1.0.1.apk
XNAT-Android-v1.0.1.apk.sha256
```

---

## 安全说明

Release 构建默认禁止明文 HTTP Panel；正式使用建议始终配置 HTTPS。Debug 构建可临时连接 HTTP Panel，App 会在发送凭据前提示风险。

正式签名证书应持续使用同一套密钥。只要 Application ID 与签名证书保持一致，新的 v1.0.1 可以覆盖安装到此前正式客户端上，并保留本地登录、主题和设置。

---

## 免责声明

本项目仅供学习、研究与技术交流使用。使用者应遵守所在地法律法规，不得将本项目用于任何违法或未经授权的用途。因使用本项目产生的任何违法行为、损失或法律责任均由使用者自行承担，与项目作者及贡献者无关。

---

<div align="center">

### XNAT Android

**由 NAMELESS 和 GPT 倾力打造**

</div>
