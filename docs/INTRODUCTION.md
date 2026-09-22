# XNAT Android 项目介绍

XNAT Android 是 **XNAT 官方原生 Android 客户端**。

客户端通过 **Mobile API v1** 连接 XNAT Panel，为用户提供移动端的服务器管理、套餐购买、订单、充值与工单等常用功能。App 只连接 Panel，不直接访问 Host Agent。

## 当前正式版本

| 项目 | 版本 |
| --- | --- |
| XNAT Android | v1.0.5 |
| XNAT Panel | v1.1.0 |
| Mobile API | v1 |
| Application ID | `com.xnat.mobile` |
| Min SDK | 26 |
| Target / Compile SDK | 36 |

## 版本说明

v1.0.5 配套 XNAT Panel v1.1.0，同步新的服务器删除语义：普通删除会永久删除 Host 上的真实 VPS、系统盘和端口转发，只有 Host 确认删除成功后 Panel 才清理记录；Host 删除失败时服务器仍保留在 Panel，不会显示为已删除。

自动续费、3 秒实时资源监控、机器编号一键填入等 v1.0.4 功能保持不变。Android 仍只连接 Panel，不直接访问 Host Agent；管理员专用的“强制从 Panel 移除”不会暴露给普通用户客户端。

系统镜像最低系统盘继续直接读取 Panel Mobile API v1 下发的 `min_disk_gb`。LXC 使用 Panel 配置，KVM 保留 3 GiB 技术底线；连接旧 Panel 时仍由后端做最终校验。

## 能做什么

- **连接 XNAT Panel**：填写 Panel 地址并登录，后续请求统一走 Mobile API v1。
- **查看账户与服务器**：首页展示账户概况、服务器列表与主要资源信息。
- **实时资源监控**：服务器详情显示 CPU、内存、硬盘与实时下载 / 上传速率，3 秒刷新并在后台自动停止。
- **自动续费**：支持在服务器详情直接开启 / 关闭单台 VPS 自动续费。
- **危险操作确认**：重装与删除确认页可一键填入机器编号，但最终操作仍需用户手动确认。
- **管理服务器**：支持开机、关机、重启、重装系统、删除等常用操作。
- **购买与订单**：浏览 Panel 动态下发的套餐和系统镜像，并完成购买、续费及订单查看。
- **系统镜像兼容提示**：购买和重装前检查当前系统盘是否满足目标镜像要求。
- **工单支持**：新建工单、查看工单详情并继续回复。
- **主题设置**：保留现有明暗主题与本地设置。
- **应用更新**：正式版通过 GitHub `releases/latest` 检查最新正式 Release。

## 与 XNAT 的关系

```text
XNAT Android
     │
     │ HTTPS / Mobile API v1
     ▼
XNAT Panel v1.1.0
     │
     │ Agent API v2
     ▼
Host Agent / Incus
```

Android 客户端不保存或使用 Host Agent Token，也不会直接连接 Host 管理端口。服务器、套餐、系统镜像与业务状态均由 Panel 统一下发。

## 更新记录

### v1.0.5

- 适配 XNAT Panel v1.1.0 的 Host-first 删除流程。
- 删除确认明确提示会永久删除 Host 上真实实例、系统盘和端口转发。
- Host 删除失败时 Panel 记录会保留，Android 不再使用旧的 Panel-only 删除说明。
- “强制从 Panel 移除”保持管理员后台专用，不暴露给普通用户客户端。
- 自动续费、3 秒实时监控、机器编号一键填入与其他主要交互保持不变。
- Mobile API 继续保持 v1。

### v1.0.4

- 服务器详情新增 iOS 风格自动续费开关，通过 Mobile API v1 与 Panel v1.0.8 同步状态。
- 自动续费保存使用即时反馈；失败自动回滚开关状态，避免界面与服务端不一致。
- 实时资源监控刷新间隔由 5 秒调整为 3 秒，并用“● 实时”状态提示替代固定刷新文案。
- CPU / 内存保持双列，硬盘改为整行；实时网络独立整行，下载 / 上传使用等宽布局。
- 重装和删除确认页新增“填入编号”，仅填写机器编号，不自动触发危险操作。
- 删除说明同步 Panel-only 语义：删除 Panel 记录时不会连接或删除 Host 上可能仍存在的实例。
- 继续使用 Mobile API v1；连接旧 Panel 时自动续费入口安全降级为不可用。

### v1.0.3

- 服务器详情新增一个整体 2×2 实时资源监控区域，展示 CPU、内存、硬盘与实时网络速率。
- CPU、内存、硬盘使用全圆角胶囊进度条；网络分别显示下载 / 上传瞬时速率。
- 每 5 秒刷新；离开服务器详情或 App 进入后台后停止轮询。
- 不保存监控历史，不写本地监控数据库；旧 Panel 无实时指标时安全降级。
- 继续通过 Mobile API v1 连接 Panel，Android 不直接访问 Host Agent。
- 保留 v1.0.2 的 Panel 动态 `min_disk_gb` 镜像磁盘策略，不改其他主要页面和交互。

### v1.0.2

- 购买服务器与重装系统统一读取 Panel `/api/v1/system-images` 下发的 `min_disk_gb`。
- 移除 Android 端 Alpine / Debian / Ubuntu 的固定最低磁盘规则。
- LXC 直接使用 Panel 配置；KVM 仅保留与 Panel v1.0.3 一致的 3 GiB 技术底线。
- 连接旧 Panel 且未返回 `min_disk_gb` 时不做本地误拦截，最终由 Panel 后端校验。
- 不兼容镜像继续置灰并显示 `需 ≥xG`，购买与重装的现有交互保持不变。
- Mobile API 保持 v1；UI、主题、页面布局和其他业务逻辑不变。

### v1.0.1

- 购买服务器和重装系统两个镜像选择入口都会提前校验系统盘兼容性。
- Alpine 默认按至少 1 GB、Debian / Ubuntu 默认按至少 2 GB、KVM 至少按 4 GB 判断，与 XNAT Panel v1.0.2 当前规则一致。
- 不兼容镜像仍保留在列表中，但会置灰并显示 `需 ≥xG`，点击时给出明确中文提示。
- 如果连接的旧 Panel 无法提供服务器磁盘信息，Android 不会误拦截，仍交给 Panel 后端最终校验。
- 代码预留读取未来 `min_disk_gb` 字段的能力，Mobile API 仍保持 v1。
- 不修改现有 UI 架构、主题、页面布局和主要交互方式。

### v1.0.0

v1.0.0 是重新整理后的 Android 正式基线：

- 对外版本重新从 **v1.0.0** 开始。
- 对接 **XNAT Panel / Mobile API v1**。
- 保留小容量 LXC 套餐磁盘规格的正确显示。
- 保留重装、删除、开关机等操作的顶部错误提示。
- 保留网络、权限、Host 离线和服务端异常的中文错误说明。
- 保留 Panel 动态下发的系统镜像与 Alpine 3.24 图标识别。
- 不修改现有 UI 架构、页面布局、主题和主要交互方式。

## 更新与发布

Android v1.0.5 的 `versionName` 为 `1.0.5`，内部 `versionCode` 为 `10212`。正式 Release 包含：

```text
XNAT-Android-v1.0.5.apk
XNAT-Android-v1.0.5.apk.sha256
```

App 使用 GitHub `releases/latest` 检查正式版本，因此开发版或 RC 应发布为 **Pre-release**，不会作为正式更新推送。

## 安全说明

Release 构建默认禁止明文 HTTP Panel；正式使用建议始终配置 HTTPS。Debug 构建可临时连接 HTTP Panel，App 会在发送凭据前提示风险。

仓库不保存正式签名私钥。正式版本应持续使用同一套签名证书，以保证后续版本能够覆盖安装并保留本地登录、主题和设置。

---

**由 NAMELESS 和 GPT 倾力打造**
