# XNAT Android v1.1.0

XNAT 官方原生 Android 客户端。

## v1.1.0

- 重做登录页：普通用户只需输入用户名和密码，不再显示 Panel 地址。
- 内置默认 Panel：`https://xnat.666101.xyz`。
- 保留隐藏维护入口：登录页长按 XNAT 品牌区域，或账户页长按版本信息，可修改连接地址；正式版仅允许 HTTPS。
- 登录体验优化：密码显示/隐藏、键盘直接登录、登录中状态、2FA 继续验证、用户名记忆与令牌自动登录。
- 首页视觉升级：新的概览标题、品牌渐变信息卡、服务状态与余额信息层级优化。
- 底部导航重做：增加原生绘制图标、紧凑选中态与轻量动效，移除粗重 elevation 阴影，改为细描边与更轻的选中背景。
- 账户中心重做：新的用户信息卡、安全与连接状态、主题设置与客户端信息布局。
- 全局卡片使用统一细边框、圆角与浅色/深色视觉规范。
- 继续使用 Android Keystore 加密保存登录令牌。

## 功能

- XNAT 账户登录、2FA 与安全令牌存储
- 首页服务运行概览与余额
- VPS 服务列表与完整详情
- 开机、关机、重启
- NAT 端口映射添加与删除
- 系统重装（Debian / Ubuntu 等系统项带原生绘制系统图标与更细腻的选择状态）
- 账务、订单、余额流水与充值记录
- 支持工单、新建、回复与关闭
- 套餐浏览、系统选择、优惠码试算、余额购买与自动开通
- 浅色 / 深色 / 跟随系统主题
- 页面缓存、下拉刷新、Bottom Sheet 与主题渐变交互

## 运行要求

- Android 8.0（API 26）及以上
- XNAT Panel v1.3.1 或兼容 Mobile API v1 的更高版本
- Mobile API v1，需包含服务、账务、工单、端口、重装和购买接口
- Host Agent v1.1.0 / Agent API v1
- 正式客户端要求 HTTPS Panel

## 构建参数

- Application ID: `com.xnat.mobile`
- Version Name: `1.1.0`
- Version Code: `10103`
- Min SDK: 26
- Target / Compile SDK: 36
- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- JDK: 17

## GitHub Actions 正式构建

仓库不保存签名私钥。在 GitHub 仓库：

`Settings → Secrets and variables → Actions → New repository secret`

配置：

- `XNAT_KEYSTORE_BASE64`
- `XNAT_KEYSTORE_PASSWORD`
- `XNAT_KEY_ALIAS`
- `XNAT_KEY_PASSWORD`

随后进入 `Actions → Build XNAT Android v1.1.0 → Run workflow`，或提交到 `main`。

构建产物：

- Artifact: `XNAT-Android-v1.1.0`
- APK: `XNAT-Android-v1.1.0.apk`
- SHA-256: `XNAT-Android-v1.1.0.apk.sha256`

v1.1.0 的 Version Code 为 `10103`，高于 v1.0.0 的 `10014`。继续使用同一正式签名证书时可直接覆盖升级，并保留本地登录与主题数据。
