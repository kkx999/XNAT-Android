#!/usr/bin/env python3
from pathlib import Path

R = Path(__file__).resolve().parents[1]

def rw(p): return (R/p).read_text(encoding='utf-8')
def ww(p,s): (R/p).write_text(s,encoding='utf-8')
def rep(s,a,b,n):
    if a not in s:
        if b in s: return s
        raise RuntimeError('missing '+n)
    return s.replace(a,b,1)

# version
g=rw('app/build.gradle')
g=rep(g,"        versionCode 10207\n        versionName '1.0.0'\n","        versionCode 10208\n        versionName '1.0.1'\n",'version')
ww('app/build.gradle',g)

p='app/src/main/java/com/xnat/mobile/MainActivity.java'
s=rw(p)

# purchase context + guard
s=rep(s,
'''        final int[] selectedIndex = {-1};
        final Button[] nextRef = new Button[1];
        for (int i = 0; i < images.length(); i++) {
''',
'''        final int[] selectedIndex = {-1};
        final Button[] nextRef = new Button[1];
        final double planDiskGb = plan.optDouble("disk_gb", 0);
        final String planVirtualizationType = plan.optString("virtualization_type", "lxc");
        for (int i = 0; i < images.length(); i++) {
''','purchase context')

s=rep(s,
'''            final String imageName = image.optString("name", "系统镜像");
            LinearLayout option = systemImageOption(imageName, image.optString("alias", ""), false, false);
            option.setTag("purchase-image-" + i);
            option.setOnClickListener(v -> {
                subtleHaptic(v);
                selectedId[0] = imageId;
''',
'''            final String imageName = image.optString("name", "系统镜像");
            final double requiredDiskGb = minimumImageDiskGb(image, planVirtualizationType);
            final boolean compatible = planDiskGb <= 0 || planDiskGb + 1e-9 >= requiredDiskGb;
            LinearLayout option = systemImageOption(imageName, image.optString("alias", ""), false, false, compatible, requiredDiskGb);
            option.setTag("purchase-image-" + i);
            option.setContentDescription(compatible ? "image-compatible" : "image-incompatible");
            option.setOnClickListener(v -> {
                subtleHaptic(v);
                if (!compatible) {
                    toast(imageName + " 至少需要 " + formatDiskGb(requiredDiskGb) + " GB 系统盘，当前套餐为 " + formatDiskGb(planDiskGb) + " GB");
                    return;
                }
                selectedId[0] = imageId;
''','purchase guard')

s=rep(s,
'''                    if (!(child instanceof LinearLayout)) continue;
                    boolean selected = ("purchase-image-" + selectedIndex[0]).equals(String.valueOf(child.getTag()));
''',
'''                    if (!(child instanceof LinearLayout)) continue;
                    if ("image-incompatible".contentEquals(child.getContentDescription())) continue;
                    boolean selected = ("purchase-image-" + selectedIndex[0]).equals(String.valueOf(child.getTag()));
''','purchase preserve')

# reinstall context + guard
s=rep(s,
'''                    managementActionInProgress = false;
                    buildReinstallSheet(serverId, serverName, server.optString("os_name", ""), images);
''',
'''                    managementActionInProgress = false;
                    buildReinstallSheet(serverId, serverName, server.optString("os_name", ""), server.optDouble("disk_gb", 0), server.optString("virtualization_type", "lxc"), images);
''','reinstall context')

s=rep(s,
'''    private void buildReinstallSheet(int serverId, String serverName, String currentOs, JSONArray images) {
''',
'''    private void buildReinstallSheet(int serverId, String serverName, String currentOs, double diskGb, String virtualizationType, JSONArray images) {
''','reinstall signature')

s=rep(s,
'''        current.addView(infoRow("当前系统", blankDash(currentOs)));
        sheet.addView(current, matchWrap());
''',
'''        current.addView(infoRow("当前系统", blankDash(currentOs)));
        if (diskGb > 0) { current.addView(thinDivider()); current.addView(infoRow("系统盘", formatDiskGb(diskGb) + " GB")); }
        sheet.addView(current, matchWrap());
''','reinstall disk row')

s=rep(s,
'''            final String imageName = image.optString("name", image.optString("alias", "系统镜像"));
            LinearLayout option = systemImageOption(imageName, image.optString("alias", ""), imageName.equalsIgnoreCase(currentOs), false);
            option.setTag("image-option-" + i);
            option.setOnClickListener(v -> {
                subtleHaptic(v);
                selectedIndex[0] = index;
''',
'''            final String imageName = image.optString("name", image.optString("alias", "系统镜像"));
            final double requiredDiskGb = minimumImageDiskGb(image, virtualizationType);
            final boolean compatible = diskGb <= 0 || diskGb + 1e-9 >= requiredDiskGb;
            LinearLayout option = systemImageOption(imageName, image.optString("alias", ""), imageName.equalsIgnoreCase(currentOs), false, compatible, requiredDiskGb);
            option.setTag("image-option-" + i);
            option.setContentDescription(compatible ? "image-compatible" : "image-incompatible");
            option.setOnClickListener(v -> {
                subtleHaptic(v);
                if (!compatible) {
                    toast(imageName + " 至少需要 " + formatDiskGb(requiredDiskGb) + " GB 系统盘，当前服务器为 " + formatDiskGb(diskGb) + " GB");
                    return;
                }
                selectedIndex[0] = index;
''','reinstall guard')

s=rep(s,
'''                    if (child instanceof LinearLayout) {
                        boolean chosen = ("image-option-" + selectedIndex[0]).equals(String.valueOf(child.getTag()));
''',
'''                    if (child instanceof LinearLayout) {
                        if ("image-incompatible".contentEquals(child.getContentDescription())) continue;
                        boolean chosen = ("image-option-" + selectedIndex[0]).equals(String.valueOf(child.getTag()));
''','reinstall preserve')

s=rep(s,
'''            buildReinstallConfirmSheet(dialog, serverId, serverName, currentOs, selectedId[0], selectedName[0], images);
''',
'''            buildReinstallConfirmSheet(dialog, serverId, serverName, currentOs, diskGb, virtualizationType, selectedId[0], selectedName[0], images);
''','confirm args')

s=rep(s,
'''    private void buildReinstallConfirmSheet(Dialog dialog, int serverId, String serverName, String currentOs, int imageId, String imageName, JSONArray images) {
''',
'''    private void buildReinstallConfirmSheet(Dialog dialog, int serverId, String serverName, String currentOs, double diskGb, String virtualizationType, int imageId, String imageName, JSONArray images) {
''','confirm signature')

s=rep(s,
'''            buildReinstallSheet(serverId, serverName, currentOs, images);
''',
'''            buildReinstallSheet(serverId, serverName, currentOs, diskGb, virtualizationType, images);
''','back args')

# Overload keeps the existing row builder and styling intact.
needle='''    private LinearLayout systemImageOption(String name, String alias, boolean current, boolean selected) {
'''
extra='''    private LinearLayout systemImageOption(String name, String alias, boolean current, boolean selected, boolean compatible, double requiredDiskGb) {
        LinearLayout option = systemImageOption(name, alias, current, selected);
        if (!compatible) {
            option.setAlpha(0.58f);
            TextView mark = option.findViewWithTag("image-mark");
            if (mark != null) {
                mark.setText("需 ≥" + formatDiskGb(requiredDiskGb) + "G");
                mark.setTextColor(AMBER);
                mark.setBackground(roundRect(AMBER_SOFT, dp(12), 0, 0));
            }
        }
        return option;
    }

'''+needle
s=rep(s,needle,extra,'option overload')

helper='''    private double minimumImageDiskGb(JSONObject image, String virtualizationType) {
        double minimum = image == null ? 0 : image.optDouble("min_disk_gb", 0);
        String alias = image == null ? "" : image.optString("alias", "").trim().toLowerCase(java.util.Locale.US);
        String family = image == null ? "" : image.optString("family", "").trim().toLowerCase(java.util.Locale.US);
        if (minimum <= 0) {
            if (alias.startsWith("images:alpine/") || "alpine".equals(family)) minimum = 1.0;
            else if (alias.startsWith("images:ubuntu/") || alias.startsWith("images:debian/") || "apt".equals(family)) minimum = 2.0;
            else minimum = 1.0;
        }
        if ("kvm".equalsIgnoreCase(virtualizationType)) minimum = Math.max(minimum, 4.0);
        return minimum;
    }

    private String formatDiskGb(double value) {
'''
s=rep(s,'    private String formatDiskGb(double value) {\n',helper,'disk policy helper')
ww(p,s)
print('XNAT Android v1.0.1 patch applied')
