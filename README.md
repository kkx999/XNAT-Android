# XNAT Android v1.0.0

XNAT 官方原生 Android 客户端正式版。

## 功能

- XNAT 账户登录、2FA 与安全令牌存储
- 首页服务运行概览与余额
- VPS 服务列表与完整详情
- 开机、关机、重启
- NAT 端口映射添加与删除
- 系统重装
- 账务、订单、余额流水与充值记录
- 支持工单、新建、回复与关闭
- 套餐浏览、系统选择、优惠码试算、余额购买与自动开通
- 浅色 / 深色 / 跟随系统主题
- 低抖动页面缓存、下拉刷新、Bottom Sheet 与主题渐变交互

## 运行要求

- Android 8.0（API 26）及以上
- XNAT Panel v1.3.1
- Mobile API v1，需包含服务、账务、工单、端口、重装和购买接口
- Host Agent v1.1.0 / Agent API v1
- 正式客户端要求 HTTPS Panel

## 构建参数

- Application ID: `com.xnat.mobile`
- Version Name: `1.0.0`
- Version Code: `10014`
- Min SDK: 26
- Target / Compile SDK: 36
- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- JDK: 17

## GitHub Actions 正式构建

本仓库不保存签名私钥。请在 GitHub 仓库：

`Settings → Secrets and variables → Actions → New repository secret`

添加以下四个 Secrets：

- `XNAT_KEYSTORE_BASE64`
- `XNAT_KEYSTORE_PASSWORD`
- `XNAT_KEY_ALIAS`
- `XNAT_KEY_PASSWORD`

随后进入 `Actions → Build XNAT Android v1.0.0 → Run workflow`，或直接提交到 `main`。

构建完成后在 Artifacts 下载：

- Artifact: `XNAT-Android-v1.0.0`
- APK: `XNAT-Android-v1.0.0.apk`
- SHA-256: `XNAT-Android-v1.0.0.apk.sha256`

正式版继续使用现有测试版本同一签名证书，并将 Version Code 提升到 `10014`，因此可以直接覆盖安装现有测试版并保留本地数据。
