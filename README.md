# XNAT Android v1.2.0-dev4

XNAT 官方原生 Android 客户端。v1.2.0-dev4 基于 v1.1.0 正式版做最小增量升级，对接 XNAT Panel v1.4.2 / Mobile API v1。

## v1.2.0-dev4

- 优化充值 Bottom Sheet 动效：创建订单后不再关闭旧弹层再重新打开，改为同一弹层内平滑切换，整体过渡由左右位移改为低幅度上浮 + 淡入，减少割裂感。
- 修复充值倒计时导致信息框周期性左右抖动：倒计时改为每秒原地更新单个文本，5 秒状态轮询不再重复重建整张订单页面；只有支付状态或可操作项真正变化时才刷新结构。
- 充值轮询与倒计时任务增加单实例清理，避免手动刷新、自动轮询或关闭弹层后产生重复任务。
- 修复 Android 15/16 edge-to-edge 启动页底部黑色系统导航条：启动遮罩改为 Activity 内全屏覆盖层，状态栏/导航栏统一透明并由 XNAT 背景延伸绘制。
- Bottom Sheet 同步适配 edge-to-edge 与导航栏 Insets，避免充值、更新、删除/重置确认等底部弹层出现同类黑边或遮挡。
- 深浅主题切换时系统栏保持透明，由页面背景自然过渡，避免状态栏/导航栏闪色。
- 自动更新 Release 资产选择更严格：优先匹配 `XNAT-Android-v版本.apk`，多 APK 时拒绝随机选择，并强制要求与 APK 同名的 `.sha256`。
- 悬浮提示位置同步计入系统导航栏 Insets，避免全面屏手势区域导致提示与 XNAT 底部导航重叠。

- 提示反馈 UI 收口：替换 Android 系统 Toast 为 XNAT 应用内悬浮胶囊，自动适配浅色/深色主题、成功/失败状态与底部导航，不再显示割裂的系统 App 图标 Toast。

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
- Version Name：`1.2.0-dev4`
- Version Code：`10204`
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

- Artifact：`XNAT-Android-v1.2.0-dev4`
- APK：`XNAT-Android-v1.2.0-dev4.apk`
- SHA-256：`XNAT-Android-v1.2.0-dev4.apk.sha256`

继续使用 v1.1.0 相同正式签名证书时，可直接覆盖升级并保留登录、主题和本地设置。

**由 NAMELESS 和 GPT 倾力打造。**
