# XNAT Android v1.2.0-dev1

XNAT 官方原生 Android 客户端。v1.2.0-dev1 基于 v1.1.0 正式版做最小增量升级，对接 XNAT Panel v1.4.2 / Mobile API v1。

## v1.2.0-dev1

- 新增灵动启动遮罩：启动时并行恢复登录状态、准备首页与执行低频更新检查。
- 新增 GitHub Releases 自动更新：自动检测正式 Release、App 内下载 APK、SHA-256 校验、调用 Android 系统安装器覆盖升级；账户中心支持手动检查更新。
- 服务卡片升级：稳定编号、状态点、国家/地区国旗、系统、虚拟化、服务器地区、套餐名称与胶囊流量条。
- 服务详情新增国家/地区、服务器地区、区域代码、网络线路、NAT 端口与套餐信息。
- 新增删除服务器：使用稳定展示编号二次确认，复用 Mobile API 的 delete_server Job。
- 新增付费流量重置：展示重置价格、可用状态、原因、流量周期并调用 Mobile API 完成扣费与新周期创建。
- 套餐购买页新增服务器地区、网络线路、NAT 端口与流量重置价格。
- 原生 USDT 充值：TRON / Polygon、金额与汇率、精确 USDT、二维码、地址复制、状态轮询、取消订单和人工模式 TxHash 提交。
- 账务中心重构：余额卡 + 原生充值入口、订单 / 余额流水 / 充值记录分段、自然月份切换、日期精简为年月日。
- 统一服务器、订单、余额流水与充值状态的中文映射。
- 电源操作按服务器状态严格启用，避免开通中、重装中、删除中误触。
- 继续使用 Android Keystore 加密保存登录令牌，正式版继续强制 HTTPS。

## 兼容关系

- Panel：v1.4.2
- Mobile API：v1
- Host Agent：v1.1.1
- Agent API：v1
- Application ID：`com.xnat.mobile`
- Version Name：`1.2.0-dev1`
- Version Code：`10201`
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

- Artifact：`XNAT-Android-v1.2.0-dev1`
- APK：`XNAT-Android-v1.2.0-dev1.apk`
- SHA-256：`XNAT-Android-v1.2.0-dev1.apk.sha256`

继续使用 v1.1.0 相同正式签名证书时，可直接覆盖升级并保留登录、主题和本地设置。

**由 NAMELESS 和 GPT 倾力打造。**
