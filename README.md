# XNAT Android v1.2.1

XNAT 官方原生 Android 客户端。v1.2.1 为兼容 XNAT Panel v1.6.3 / Mobile API v1 的问题修复版本，不改现有 UI 架构与主要交互。

## v1.2.1

- 修复小容量 LXC 套餐磁盘被整数截断的问题，正确显示 0.125 / 0.25 / 0.5 / 1.5 GB 等规格。
- 修复重装、删除、开关机等操作失败提示靠近页面底部而不易察觉的问题；错误提示改为顶部醒目显示。
- 统一常见网络、权限、宿主机离线和服务端异常的中文提示，避免直接暴露英文异常与 HTTP 状态码。
- 补充 Alpine 3.24 系统图标识别；购买与重装仍使用 Panel 动态下发的系统镜像。
- 同步兼容信息到 Panel v1.6.3 / Host Agent v1.2.0，Mobile API / Agent API 均保持 v1。

## 兼容关系

- Panel：v1.6.3
- Mobile API：v1
- Host Agent：v1.2.0
- Agent API：v1
- Application ID：`com.xnat.mobile`
- Version Name：`1.2.1`
- Version Code：`10206`
- Min SDK：26
- Target / Compile SDK：36

## GitHub 自动更新约定

正式 Release 至少上传：

- `XNAT-Android-vX.Y.Z.apk`
- `XNAT-Android-vX.Y.Z.apk.sha256`

App 使用 GitHub `releases/latest` 检查正式版本，因此 dev / RC 测试版应发布为 **Pre-release**，不会推送给正式用户。

GitHub Actions 构建时会自动使用当前 `GITHUB_REPOSITORY` 作为更新仓库；本地构建也可通过环境变量 `XNAT_GITHUB_REPO=owner/repo` 覆盖。

## GitHub Actions 正式构建

仓库不保存签名私钥。配置以下 Actions Secrets：

- `XNAT_KEYSTORE_BASE64`
- `XNAT_KEYSTORE_PASSWORD`
- `XNAT_KEY_ALIAS`
- `XNAT_KEY_PASSWORD`

构建产物：

- Artifact：`XNAT-Android-v1.2.1`
- APK：`XNAT-Android-v1.2.1.apk`
- SHA-256：`XNAT-Android-v1.2.1.apk.sha256`

继续使用此前版本相同的正式签名证书时，可直接覆盖升级并保留登录、主题和本地设置。正式版 Version Code 为 `10206`。

**由 NAMELESS 和 GPT 倾力打造。**

---

## 免责声明

本项目仅供学习、研究与技术交流使用。使用者应遵守所在地法律法规，不得将本项目用于任何违法或未经授权的用途。因使用本项目产生的任何违法行为、损失或法律责任均由使用者自行承担，与项目作者及贡献者无关。
