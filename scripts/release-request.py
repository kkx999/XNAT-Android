from pathlib import Path
import os


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}: {old!r}")
    p.write_text(text.replace(old, new, 1))


# Version bump: v1.0.1 -> v1.0.2.
replace_once("app/build.gradle", "        versionCode 10208\n", "        versionCode 10209\n")
replace_once("app/build.gradle", "        versionName '1.0.1'\n", "        versionName '1.0.2'\n")

# Purchase must use the same /system-images payload as reinstall, because the
# v1.0.3 /catalog payload does not expose min_disk_gb for its embedded image list.
replace_once(
    "app/src/main/java/com/xnat/mobile/MainActivity.java",
    '''                JSONObject data = ApiClient.request(baseUrl, "/api/v1/catalog", "GET", token, null);\n                main.post(() -> {''',
    '''                JSONObject data = ApiClient.request(baseUrl, "/api/v1/catalog", "GET", token, null);\n                JSONObject imageData = ApiClient.request(baseUrl, "/api/v1/system-images", "GET", token, null);\n                JSONArray dynamicImages = imageData.optJSONArray("items");\n                if (dynamicImages != null) data.put("system_images", dynamicImages);\n                main.post(() -> {''',
)

# Remove Android-side distro guesses. Panel is the source of truth. For KVM,
# retain only the same 3 GiB technical floor used by Panel v1.0.3, and only
# when the Panel actually supplied a positive image requirement. Old Panels
# without min_disk_gb therefore do not get falsely blocked by the client.
old_method = '''    private double minimumImageDiskGb(JSONObject image, String virtualizationType) {\n        double minimum = image == null ? 0 : image.optDouble("min_disk_gb", 0);\n        String alias = image == null ? "" : image.optString("alias", "").trim().toLowerCase(java.util.Locale.US);\n        String family = image == null ? "" : image.optString("family", "").trim().toLowerCase(java.util.Locale.US);\n        if (minimum <= 0) {\n            if (alias.startsWith("images:alpine/") || "alpine".equals(family)) minimum = 1.0;\n            else if (alias.startsWith("images:ubuntu/") || alias.startsWith("images:debian/") || "apt".equals(family)) minimum = 2.0;\n            else minimum = 1.0;\n        }\n        if ("kvm".equalsIgnoreCase(virtualizationType)) minimum = Math.max(minimum, 4.0);\n        return minimum;\n    }\n'''
new_method = '''    private double minimumImageDiskGb(JSONObject image, String virtualizationType) {\n        double minimum = image == null ? 0 : image.optDouble("min_disk_gb", 0);\n        if (!Double.isFinite(minimum) || minimum < 0) minimum = 0;\n        if ("kvm".equalsIgnoreCase(virtualizationType) && minimum > 0) {\n            minimum = Math.max(minimum, 3.0);\n        }\n        return minimum;\n    }\n'''
replace_once("app/src/main/java/com/xnat/mobile/MainActivity.java", old_method, new_method)

# README current release metadata.
readme_path = Path("README.md")
readme = readme_path.read_text()
for old, new in [
    ("**当前正式版本：v1.0.1**", "**当前正式版本：v1.0.2**"),
    ("XNAT-Android-v1.0.1.apk", "XNAT-Android-v1.0.2.apk"),
    ("XNAT-Android-v1.0.1.apk.sha256", "XNAT-Android-v1.0.2.apk.sha256"),
]:
    if old not in readme:
        raise SystemExit(f"README.md missing expected token: {old}")
    readme = readme.replace(old, new)
readme_path.write_text(readme)

# Detailed project introduction and release history.
intro_path = Path("docs/INTRODUCTION.md")
intro = intro_path.read_text()
for old, new in [
    ("| XNAT Android | v1.0.1 |", "| XNAT Android | v1.0.2 |"),
    ("| XNAT Panel | v1.0.2 |", "| XNAT Panel | v1.0.3 |"),
    ("XNAT Panel v1.0.2\n     │", "XNAT Panel v1.0.3\n     │"),
    ("Android v1.0.1 的 `versionName` 为 `1.0.1`，内部 `versionCode` 为 `10208`。正式 Release 包含：",
     "Android v1.0.2 的 `versionName` 为 `1.0.2`，内部 `versionCode` 为 `10209`。正式 Release 包含："),
]:
    if old not in intro:
        raise SystemExit(f"docs/INTRODUCTION.md missing expected token: {old}")
    intro = intro.replace(old, new, 1)

old_description = '''v1.0.1 是兼容性体验更新。系统镜像选择会提前提示最低系统盘要求，不兼容镜像不可选择；现有 UI 架构、页面布局、视觉风格和主要交互保持不变。\n\nAndroid 继续使用 Mobile API v1，与当前 XNAT Panel v1.0.2 保持兼容。Host Agent API v2 属于 Panel ↔ Host 内部通信，Android 不直接接触 Host Agent。'''
new_description = '''v1.0.2 是系统镜像最低系统盘策略同步更新。购买和重装继续保留提前兼容提示，但最低磁盘不再由 Android 按发行版写死，而是直接读取 XNAT Panel v1.0.3 的 Mobile API v1 `/api/v1/system-images` 下发的 `min_disk_gb`。\n\nLXC 直接使用 Panel 配置；KVM 在 Panel 返回有效最低磁盘时仅保留与 Panel v1.0.3 一致的 3 GiB 技术底线。连接旧 Panel 且未返回 `min_disk_gb` 时，Android 不做本地误拦截，最终仍由 Panel 后端校验。Host Agent API v2 属于 Panel ↔ Host 内部通信，Android 不直接接触 Host Agent。现有 UI 架构、页面布局、视觉风格和主要交互保持不变。'''
if old_description not in intro:
    raise SystemExit("docs/INTRODUCTION.md current version description not found")
intro = intro.replace(old_description, new_description, 1)

marker = "## 更新记录\n\n### v1.0.1"
entry = '''## 更新记录\n\n### v1.0.2\n\n- 购买服务器与重装系统统一读取 Panel `/api/v1/system-images` 下发的 `min_disk_gb`。\n- 移除 Android 端 Alpine / Debian / Ubuntu 的固定最低磁盘规则。\n- LXC 直接使用 Panel 配置；KVM 仅保留与 Panel v1.0.3 一致的 3 GiB 技术底线。\n- 连接旧 Panel 且未返回 `min_disk_gb` 时不做本地误拦截，最终由 Panel 后端校验。\n- 不兼容镜像继续置灰并显示 `需 ≥xG`，购买与重装的现有交互保持不变。\n- Mobile API 保持 v1；UI、主题、页面布局和其他业务逻辑不变。\n\n### v1.0.1'''
if marker not in intro:
    raise SystemExit("docs/INTRODUCTION.md update-history marker not found")
intro = intro.replace(marker, entry, 1)

# Only current release asset examples are updated; the v1.0.1 historical
# section remains untouched.
release_section = intro.index("## 更新与发布")
head = intro[:release_section]
tail = intro[release_section:]
tail = tail.replace("XNAT-Android-v1.0.1.apk", "XNAT-Android-v1.0.2.apk")
tail = tail.replace("XNAT-Android-v1.0.1.apk.sha256", "XNAT-Android-v1.0.2.apk.sha256")
intro_path.write_text(head + tail)

# Release notes consumed by the publishing workflow after all checks pass.
runner_temp = os.environ.get("RUNNER_TEMP")
if not runner_temp:
    raise SystemExit("RUNNER_TEMP is not set")
notes = '''XNAT Android v1.0.2\n\n系统镜像最低系统盘策略同步更新：\n- 购买服务器和重装系统统一读取 Panel v1.0.3 / Mobile API v1 `/api/v1/system-images` 下发的 `min_disk_gb`。\n- 移除 Android 端 Alpine / Debian / Ubuntu 的固定最低磁盘规则。\n- LXC 直接使用 Panel 配置；KVM 在 Panel 返回有效最低磁盘时使用 `max(min_disk_gb, 3 GiB)`，与 Panel v1.0.3 一致。\n- 连接旧 Panel 且没有 `min_disk_gb` 时不做本地误拦截，由 Panel 后端最终校验。\n- 不兼容镜像仍置灰并显示 `需 ≥xG`。\n- UI、主题、页面布局和其他业务逻辑不变。\n\n版本：1.0.2（versionCode 10209）\n\n**由 NAMELESS 和 GPT 倾力打造**\n'''
Path(runner_temp, "xnat-release-notes.md").write_text(notes)

print("XNAT Android v1.0.2 release preparation complete")
