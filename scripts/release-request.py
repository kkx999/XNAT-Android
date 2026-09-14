from pathlib import Path
import os

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/xnat/mobile/MainActivity.java'
GRADLE = ROOT / 'app/build.gradle'
README = ROOT / 'README.md'
INTRO = ROOT / 'docs/INTRODUCTION.md'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, got {count}')
    return text.replace(old, new, 1)


src = MAIN.read_text(encoding='utf-8')

field_anchor = '    private long lastBackPressAt = 0L;\n'
field_insert = '''    private static final long LIVE_METRICS_INTERVAL_MS = 5000L;
    private static final String TAG_LIVE_CPU_VALUE = "xnat_live_cpu_value";
    private static final String TAG_LIVE_CPU_DETAIL = "xnat_live_cpu_detail";
    private static final String TAG_LIVE_CPU_BAR = "xnat_live_cpu_bar";
    private static final String TAG_LIVE_MEMORY_VALUE = "xnat_live_memory_value";
    private static final String TAG_LIVE_MEMORY_DETAIL = "xnat_live_memory_detail";
    private static final String TAG_LIVE_MEMORY_BAR = "xnat_live_memory_bar";
    private static final String TAG_LIVE_DISK_VALUE = "xnat_live_disk_value";
    private static final String TAG_LIVE_DISK_DETAIL = "xnat_live_disk_detail";
    private static final String TAG_LIVE_DISK_BAR = "xnat_live_disk_bar";
    private static final String TAG_LIVE_RX = "xnat_live_rx";
    private static final String TAG_LIVE_TX = "xnat_live_tx";
    private static final String TAG_LIVE_NETWORK_DETAIL = "xnat_live_network_detail";
    private Runnable liveMetricsPollTask = null;
    private LinearLayout liveMetricsPage = null;
    private int liveMetricsServerId = 0;
    private boolean appResumed = false;
'''
src = replace_once(src, field_anchor, field_anchor + field_insert, 'live metrics fields')

src = replace_once(
    src,
    '    private void showLogin() {\n        inDetail = false;\n',
    '    private void showLogin() {\n        stopLiveMetricsPolling();\n        inDetail = false;\n',
    'showLogin stop polling',
)
src = replace_once(
    src,
    '    private void showApp(int tab) {\n        root.removeAllViews();\n        inDetail = false;\n',
    '    private void showApp(int tab) {\n        stopLiveMetricsPolling();\n        root.removeAllViews();\n        inDetail = false;\n',
    'showApp stop polling',
)
src = replace_once(
    src,
    '        currentTab = tab;\n        inDetail = false;\n        screenGeneration++;\n',
    '        stopLiveMetricsPolling();\n        currentTab = tab;\n        inDetail = false;\n        screenGeneration++;\n',
    'selectTab stop polling',
)
src = replace_once(
    src,
    '    private void reloadCurrentTab() {\n',
    '    private void reloadCurrentTab() {\n        stopLiveMetricsPolling();\n',
    'reloadCurrentTab stop polling',
)
src = replace_once(
    src,
    '    private void showServerDetail(int serverId) {\n        inDetail = true;\n',
    '    private void showServerDetail(int serverId) {\n        stopLiveMetricsPolling();\n        inDetail = true;\n',
    'showServerDetail reset polling',
)

src = replace_once(
    src,
    '''    @Override
    protected void onPause() {
        pausedAt = System.currentTimeMillis();
        super.onPause();
    }
''',
    '''    @Override
    protected void onPause() {
        appResumed = false;
        if (liveMetricsPollTask != null) main.removeCallbacks(liveMetricsPollTask);
        pausedAt = System.currentTimeMillis();
        super.onPause();
    }
''',
    'onPause polling pause',
)
src = replace_once(
    src,
    '''    @Override
    protected void onResume() {
        super.onResume();
''',
    '''    @Override
    protected void onResume() {
        super.onResume();
        appResumed = true;
        maybeResumeLiveMetricsPolling();
''',
    'onResume polling resume',
)

methods_anchor = '    private void renderServerDetail(LinearLayout page, JSONObject s) {\n'
methods_block = r'''    private LinearLayout liveMetricCell(String label, String valueText, String detailText,
                                              String valueTag, String detailTag, String barTag) {
        LinearLayout cell = column();
        cell.setPadding(dp(4), dp(2), dp(4), dp(2));
        cell.addView(text(label, 11, MUTED, true));

        TextView value = text(valueText, 20, INK, true);
        value.setTag(valueTag);
        value.setPadding(0, dp(4), 0, 0);
        cell.addView(value, matchWrap());

        TextView detail = text(detailText, 10, MUTED, false);
        detail.setTag(detailTag);
        detail.setPadding(0, dp(3), 0, 0);
        cell.addView(detail, matchWrap());

        if (barTag != null) {
            gap(cell, 9);
            LinearLayout bar = horizontalRow();
            bar.setTag(barTag);
            bar.setBackground(roundRect(SOFT, dp(99), 0, 0));
            cell.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)));
            setLiveMetricBar(bar, 0.0);
        }
        return cell;
    }

    private LinearLayout liveNetworkCell() {
        LinearLayout cell = column();
        cell.setPadding(dp(4), dp(2), dp(4), dp(2));
        cell.addView(text("实时网络", 11, MUTED, true));

        TextView rx = text("↓ 采样中…", 17, INK, true);
        rx.setTag(TAG_LIVE_RX);
        rx.setPadding(0, dp(4), 0, 0);
        cell.addView(rx, matchWrap());

        TextView tx = text("↑ 采样中…", 14, INK, true);
        tx.setTag(TAG_LIVE_TX);
        tx.setPadding(0, dp(3), 0, 0);
        cell.addView(tx, matchWrap());

        TextView detail = text("当前实际下载 / 上传速率", 10, MUTED, false);
        detail.setTag(TAG_LIVE_NETWORK_DETAIL);
        detail.setPadding(0, dp(5), 0, 0);
        cell.addView(detail, matchWrap());
        return cell;
    }

    private void addLiveMetricsSection(LinearLayout page, int serverId) {
        int targetId = serverId > 0 ? serverId : currentDetailServerId;
        page.addView(sectionHeader("实时资源监控", "当前实例 CPU、内存、硬盘与网络速率", "5 秒刷新"), matchWrap());
        gap(page, 10);

        LinearLayout panel = surfaceCard(18);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout row1 = horizontalRow();
        row1.setGravity(Gravity.TOP);
        row1.addView(liveMetricCell("CPU 使用率", "采样中…", "正在计算瞬时使用率",
                TAG_LIVE_CPU_VALUE, TAG_LIVE_CPU_DETAIL, TAG_LIVE_CPU_BAR), weighted());
        gapH(row1, 14);
        row1.addView(liveMetricCell("内存使用", "采样中…", "正在读取实例内存",
                TAG_LIVE_MEMORY_VALUE, TAG_LIVE_MEMORY_DETAIL, TAG_LIVE_MEMORY_BAR), weighted());
        panel.addView(row1, matchWrap());

        gap(panel, 13);
        panel.addView(thinDivider());
        gap(panel, 13);

        LinearLayout row2 = horizontalRow();
        row2.setGravity(Gravity.TOP);
        row2.addView(liveMetricCell("硬盘使用", "采样中…", "正在读取系统盘",
                TAG_LIVE_DISK_VALUE, TAG_LIVE_DISK_DETAIL, TAG_LIVE_DISK_BAR), weighted());
        gapH(row2, 14);
        row2.addView(liveNetworkCell(), weighted());
        panel.addView(row2, matchWrap());

        page.addView(panel, matchWrap());
        startLiveMetricsPolling(page, targetId);
    }

    private TextView liveTaggedText(LinearLayout page, String tag) {
        if (page == null) return null;
        View view = page.findViewWithTag(tag);
        return view instanceof TextView ? (TextView) view : null;
    }

    private LinearLayout liveTaggedBar(LinearLayout page, String tag) {
        if (page == null) return null;
        View view = page.findViewWithTag(tag);
        return view instanceof LinearLayout ? (LinearLayout) view : null;
    }

    private void setLiveText(LinearLayout page, String tag, String value) {
        TextView view = liveTaggedText(page, tag);
        if (view != null) view.setText(value);
    }

    private double clampLivePercent(double percent) {
        if (Double.isNaN(percent) || Double.isInfinite(percent)) return Double.NaN;
        return Math.max(0.0, Math.min(100.0, percent));
    }

    private String livePercentText(double percent) {
        double value = clampLivePercent(percent);
        if (Double.isNaN(value)) return "采样中…";
        double rounded = Math.round(value * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) return ((int) Math.round(rounded)) + "%";
        return String.valueOf(rounded) + "%";
    }

    private int liveMetricColor(double percent) {
        if (Double.isNaN(percent)) return BLUE;
        if (percent >= 90.0) return RED;
        if (percent >= 70.0) return AMBER;
        return BLUE;
    }

    private void setLiveMetricBar(LinearLayout bar, double percent) {
        if (bar == null) return;
        bar.removeAllViews();
        double value = clampLivePercent(percent);
        if (Double.isNaN(value) || value <= 0.0) return;

        View fill = new View(this);
        fill.setBackground(roundRect(liveMetricColor(value), dp(99), 0, 0));
        float usedWeight = (float) Math.max(0.1, value);
        float restWeight = (float) Math.max(0.1, 100.0 - value);
        bar.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, usedWeight));
        if (value < 100.0) {
            View rest = new View(this);
            rest.setBackgroundColor(Color.TRANSPARENT);
            bar.addView(rest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, restWeight));
        }
    }

    private double livePercent(JSONObject data, String key, long used, long total) {
        if (data != null && data.has(key) && !data.isNull(key)) {
            return clampLivePercent(data.optDouble(key, Double.NaN));
        }
        if (total > 0) return clampLivePercent((double) used / (double) total * 100.0);
        return Double.NaN;
    }

    private void updateLivePercentMetric(LinearLayout page, String valueTag, String detailTag, String barTag,
                                         double percent, String detail) {
        setLiveText(page, valueTag, livePercentText(percent));
        setLiveText(page, detailTag, detail);
        setLiveMetricBar(liveTaggedBar(page, barTag), percent);
    }

    private String liveRate(long bytesPerSecond) {
        return bytes(Math.max(0L, bytesPerSecond)) + "/s";
    }

    private String liveUnavailableReason(String status) {
        String s = status == null ? "" : status.trim().toLowerCase();
        if ("stopped".equals(s) || "frozen".equals(s)) return "实例未运行";
        if ("provisioning".equals(s) || "reinstalling".equals(s)) return "实例正在准备中";
        return "实时数据暂不可用";
    }

    private void setLiveMetricsUnavailable(LinearLayout page, String reason) {
        String message = (reason == null || reason.trim().isEmpty()) ? "实时数据暂不可用" : reason.trim();
        setLiveText(page, TAG_LIVE_CPU_VALUE, "--");
        setLiveText(page, TAG_LIVE_CPU_DETAIL, message);
        setLiveMetricBar(liveTaggedBar(page, TAG_LIVE_CPU_BAR), 0.0);
        setLiveText(page, TAG_LIVE_MEMORY_VALUE, "--");
        setLiveText(page, TAG_LIVE_MEMORY_DETAIL, message);
        setLiveMetricBar(liveTaggedBar(page, TAG_LIVE_MEMORY_BAR), 0.0);
        setLiveText(page, TAG_LIVE_DISK_VALUE, "--");
        setLiveText(page, TAG_LIVE_DISK_DETAIL, message);
        setLiveMetricBar(liveTaggedBar(page, TAG_LIVE_DISK_BAR), 0.0);
        setLiveText(page, TAG_LIVE_RX, "↓ --");
        setLiveText(page, TAG_LIVE_TX, "↑ --");
        setLiveText(page, TAG_LIVE_NETWORK_DETAIL, message);
    }

    private void updateLiveMetrics(LinearLayout page, JSONObject data) {
        if (data == null) {
            setLiveMetricsUnavailable(page, "实时数据暂不可用");
            return;
        }
        if (!data.has("available")) {
            setLiveMetricsUnavailable(page, "当前 Panel 暂不支持实时监控");
            return;
        }
        if (!data.optBoolean("available", false)) {
            setLiveMetricsUnavailable(page, liveUnavailableReason(data.optString("status", "")));
            return;
        }

        double cpu = data.has("cpu_percent") && !data.isNull("cpu_percent")
                ? clampLivePercent(data.optDouble("cpu_percent", Double.NaN)) : Double.NaN;
        updateLivePercentMetric(page, TAG_LIVE_CPU_VALUE, TAG_LIVE_CPU_DETAIL, TAG_LIVE_CPU_BAR,
                cpu, Double.isNaN(cpu) ? "正在计算瞬时使用率" : "当前使用率");

        long memoryUsed = Math.max(0L, data.optLong("memory_used_bytes", 0L));
        long memoryTotal = Math.max(0L, data.optLong("memory_total_bytes", 0L));
        double memoryPercent = livePercent(data, "memory_percent", memoryUsed, memoryTotal);
        String memoryDetail = memoryTotal > 0 ? bytes(memoryUsed) + " / " + bytes(memoryTotal) : "正在读取实例内存";
        updateLivePercentMetric(page, TAG_LIVE_MEMORY_VALUE, TAG_LIVE_MEMORY_DETAIL, TAG_LIVE_MEMORY_BAR,
                memoryPercent, memoryDetail);

        long diskUsed = Math.max(0L, data.optLong("disk_used_bytes", 0L));
        long diskTotal = Math.max(0L, data.optLong("disk_total_bytes", 0L));
        double diskPercent = livePercent(data, "disk_percent", diskUsed, diskTotal);
        String diskDetail = diskTotal > 0 ? bytes(diskUsed) + " / " + bytes(diskTotal) : "正在读取系统盘";
        updateLivePercentMetric(page, TAG_LIVE_DISK_VALUE, TAG_LIVE_DISK_DETAIL, TAG_LIVE_DISK_BAR,
                diskPercent, diskDetail);

        boolean rxReady = data.has("network_rx_bps") && !data.isNull("network_rx_bps");
        boolean txReady = data.has("network_tx_bps") && !data.isNull("network_tx_bps");
        setLiveText(page, TAG_LIVE_RX, rxReady ? "↓ " + liveRate(data.optLong("network_rx_bps", 0L)) : "↓ 采样中…");
        setLiveText(page, TAG_LIVE_TX, txReady ? "↑ " + liveRate(data.optLong("network_tx_bps", 0L)) : "↑ 采样中…");
        setLiveText(page, TAG_LIVE_NETWORK_DETAIL,
                (rxReady && txReady) ? "当前实际下载 / 上传速率" : "正在计算瞬时网络速率");
    }

    private boolean liveMetricsContextValid(LinearLayout page, int serverId, int generation) {
        return appResumed
                && inDetail
                && generation == screenGeneration
                && currentDetailServerId == serverId
                && liveMetricsServerId == serverId
                && liveMetricsPage == page
                && liveMetricsPollTask != null;
    }

    private void startLiveMetricsPolling(LinearLayout page, int serverId) {
        stopLiveMetricsPolling();
        if (page == null || serverId <= 0) return;

        final int generation = screenGeneration;
        liveMetricsPage = page;
        liveMetricsServerId = serverId;
        liveMetricsPollTask = new Runnable() {
            @Override
            public void run() {
                if (!liveMetricsContextValid(page, serverId, generation) || liveMetricsPollTask != this) return;
                io.execute(() -> {
                    try {
                        JSONObject metrics = ApiClient.request(baseUrl, "/api/v1/servers/" + serverId + "?metrics=1", "GET", token, null);
                        main.post(() -> {
                            if (!liveMetricsContextValid(page, serverId, generation) || liveMetricsPollTask != this) return;
                            updateLiveMetrics(page, metrics);
                            main.postDelayed(this, LIVE_METRICS_INTERVAL_MS);
                        });
                    } catch (Exception e) {
                        main.post(() -> {
                            if (!liveMetricsContextValid(page, serverId, generation) || liveMetricsPollTask != this) return;
                            if (handleUnauthorized(e)) {
                                stopLiveMetricsPolling();
                                return;
                            }
                            setLiveMetricsUnavailable(page, "暂时无法获取实时数据");
                            main.postDelayed(this, LIVE_METRICS_INTERVAL_MS);
                        });
                    }
                });
            }
        };
        if (appResumed) main.post(liveMetricsPollTask);
    }

    private void stopLiveMetricsPolling() {
        if (liveMetricsPollTask != null) main.removeCallbacks(liveMetricsPollTask);
        liveMetricsPollTask = null;
        liveMetricsPage = null;
        liveMetricsServerId = 0;
    }

    private void maybeResumeLiveMetricsPolling() {
        if (!appResumed || liveMetricsPollTask == null || liveMetricsPage == null || liveMetricsServerId <= 0) return;
        main.removeCallbacks(liveMetricsPollTask);
        main.post(liveMetricsPollTask);
    }

'''
src = replace_once(src, methods_anchor, methods_block + methods_anchor, 'insert live metrics methods')

resource_anchor = '''            page.addView(resource, matchWrap());
            gap(page, 22);

            int portCount = s.optInt("port_count", s.optJSONArray("ports") == null ? 0 : s.optJSONArray("ports").length());
'''
resource_replacement = '''            page.addView(resource, matchWrap());
            gap(page, 22);

            addLiveMetricsSection(page, s.optInt("id", currentDetailServerId));
            gap(page, 22);

            int portCount = s.optInt("port_count", s.optJSONArray("ports") == null ? 0 : s.optJSONArray("ports").length());
'''
src = replace_once(src, resource_anchor, resource_replacement, 'place live metrics section')

MAIN.write_text(src, encoding='utf-8')

gradle = GRADLE.read_text(encoding='utf-8')
gradle = replace_once(gradle, '        versionCode 10209\n', '        versionCode 10210\n', 'versionCode')
gradle = replace_once(gradle, "        versionName '1.0.2'\n", "        versionName '1.0.3'\n", 'versionName')
GRADLE.write_text(gradle, encoding='utf-8')

readme = README.read_text(encoding='utf-8')
readme = readme.replace('当前正式版本：v1.0.2', '当前正式版本：v1.0.3')
readme = readme.replace('XNAT-Android-v1.0.2.apk.sha256', 'XNAT-Android-v1.0.3.apk.sha256')
readme = readme.replace('XNAT-Android-v1.0.2.apk', 'XNAT-Android-v1.0.3.apk')
README.write_text(readme, encoding='utf-8')

intro = INTRO.read_text(encoding='utf-8')
intro = intro.replace('| XNAT Android | v1.0.2 |', '| XNAT Android | v1.0.3 |')
intro = intro.replace('| XNAT Panel | v1.0.3 |', '| XNAT Panel | v1.0.5 |')
intro = replace_once(
    intro,
    '## 版本说明\n\nv1.0.2 是系统镜像最低系统盘策略同步更新。',
    '## 版本说明\n\nv1.0.3 新增服务器详情实时资源监控，通过 Mobile API v1 从 Panel 读取 CPU、内存、硬盘与实时下载 / 上传速率。监控每 5 秒刷新，离开详情页或 App 进入后台后停止轮询，不在客户端保存监控历史；连接旧 Panel 时安全降级为暂不可用。\n\nCPU、内存、硬盘采用与现有 Android 视觉体系一致的全圆角胶囊进度条，网络单独显示上下行瞬时速率。App 仍只连接 Panel，不直接访问 Host Agent。其他页面、系统镜像磁盘策略与现有主要交互保持不变。\n\nv1.0.2 是系统镜像最低系统盘策略同步更新。',
    'intro version summary',
)
intro = intro.replace('- **查看账户与服务器**：首页展示账户概况、服务器列表与主要资源信息。', '- **查看账户与服务器**：首页展示账户概况、服务器列表与主要资源信息。\n- **实时资源监控**：服务器详情显示 CPU、内存、硬盘与实时下载 / 上传速率，5 秒刷新并在后台自动停止。')
intro = intro.replace('XNAT Panel v1.0.3', 'XNAT Panel v1.0.5')
intro = replace_once(
    intro,
    '## 更新记录\n\n### v1.0.2',
    '''## 更新记录

### v1.0.3

- 服务器详情新增一个整体 2×2 实时资源监控区域，展示 CPU、内存、硬盘与实时网络速率。
- CPU、内存、硬盘使用全圆角胶囊进度条；网络分别显示下载 / 上传瞬时速率。
- 每 5 秒刷新；离开服务器详情或 App 进入后台后停止轮询。
- 不保存监控历史，不写本地监控数据库；旧 Panel 无实时指标时安全降级。
- 继续通过 Mobile API v1 连接 Panel，Android 不直接访问 Host Agent。
- 保留 v1.0.2 的 Panel 动态 `min_disk_gb` 镜像磁盘策略，不改其他主要页面和交互。

### v1.0.2''',
    'intro changelog',
)
intro = intro.replace('Android v1.0.2 的 `versionName` 为 `1.0.2`，内部 `versionCode` 为 `10209`。', 'Android v1.0.3 的 `versionName` 为 `1.0.3`，内部 `versionCode` 为 `10210`。')
intro = intro.replace('XNAT-Android-v1.0.2.apk.sha256', 'XNAT-Android-v1.0.3.apk.sha256')
intro = intro.replace('XNAT-Android-v1.0.2.apk', 'XNAT-Android-v1.0.3.apk')
INTRO.write_text(intro, encoding='utf-8')

notes = '''# XNAT Android v1.0.3

服务器实时资源监控更新。

- 服务器详情新增 CPU、内存、硬盘和实时下载 / 上传速率
- 采用整体 2×2 对称布局；CPU、内存、硬盘使用全圆角胶囊进度条
- 每 5 秒刷新，离开详情页或 App 进入后台后停止轮询
- 不保存监控历史；旧 Panel 无实时指标时安全降级
- 继续使用 Mobile API v1，仅连接 Panel，不直接访问 Host Agent
- 保留 v1.0.2 的动态 `min_disk_gb` 镜像磁盘策略
- 适配 XNAT Panel v1.0.5 / Host Agent v1.0.4

**由 𝐍𝐀𝐌𝐄𝐋𝐄𝐒𝐒 和 GPT 倾力打造**
'''
runner_temp = Path(os.environ.get('RUNNER_TEMP', '/tmp'))
runner_temp.mkdir(parents=True, exist_ok=True)
(runner_temp / 'xnat-release-notes.md').write_text(notes, encoding='utf-8')

print('Prepared XNAT Android v1.0.3 live metrics release')
