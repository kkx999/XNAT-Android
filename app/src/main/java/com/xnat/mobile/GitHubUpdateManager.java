package com.xnat.mobile;

import android.content.Context;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

final class GitHubUpdateManager {
    private GitHubUpdateManager() {}

    static final class UpdateInfo {
        final String tagName;
        final String versionName;
        final String title;
        final String body;
        final String apkName;
        final String apkUrl;
        final String sha256Url;
        final String htmlUrl;

        UpdateInfo(String tagName, String versionName, String title, String body,
                   String apkName, String apkUrl, String sha256Url, String htmlUrl) {
            this.tagName = tagName;
            this.versionName = versionName;
            this.title = title;
            this.body = body;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.sha256Url = sha256Url;
            this.htmlUrl = htmlUrl;
        }
    }

    interface ProgressCallback {
        void onProgress(long downloaded, long total);
    }

    static UpdateInfo fetchLatest() throws Exception {
        String repo = BuildConfig.GITHUB_REPO == null ? "" : BuildConfig.GITHUB_REPO.trim();
        if (repo.isEmpty() || !repo.contains("/")) throw new Exception("GitHub 更新仓库未配置");
        URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
        HttpURLConnection conn = open(url);
        try {
            int code = conn.getResponseCode();
            String text = readText(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                if (code == 404) throw new Exception("GitHub 暂无正式 Release");
                throw new Exception("GitHub 更新检查失败（HTTP " + code + "）");
            }
            JSONObject json = new JSONObject(text);
            String tag = json.optString("tag_name", "").trim();
            String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
            String title = json.optString("name", tag);
            String body = json.optString("body", "");
            String html = json.optString("html_url", "");
            String apkName = "";
            String apkUrl = "";
            String shaUrl = "";
            JSONArray assets = json.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.optJSONObject(i);
                    if (a == null) continue;
                    String name = a.optString("name", "");
                    String download = a.optString("browser_download_url", "");
                    String lower = name.toLowerCase(Locale.US);
                    if (lower.endsWith(".apk") && apkUrl.isEmpty()) {
                        apkName = name;
                        apkUrl = download;
                    }
                }
                if (!apkName.isEmpty()) {
                    String exact = (apkName + ".sha256").toLowerCase(Locale.US);
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a == null) continue;
                        String name = a.optString("name", "");
                        String lower = name.toLowerCase(Locale.US);
                        if (lower.equals(exact) || (lower.endsWith(".sha256") && lower.contains("apk"))) {
                            shaUrl = a.optString("browser_download_url", "");
                            break;
                        }
                    }
                }
            }
            if (tag.isEmpty()) throw new Exception("Release 缺少版本号");
            if (apkUrl.isEmpty()) throw new Exception("Release 中没有找到 APK 资产");
            if (shaUrl.isEmpty()) throw new Exception("Release 中没有找到 APK SHA-256 校验文件");
            return new UpdateInfo(tag, version, title, body, apkName, apkUrl, shaUrl, html);
        } finally {
            conn.disconnect();
        }
    }

    static boolean isNewerThanCurrent(UpdateInfo info) {
        return compareVersions(info.versionName, BuildConfig.VERSION_NAME) > 0;
    }

    static boolean sameCoreStableUpgrade(UpdateInfo info) {
        String current = BuildConfig.VERSION_NAME == null ? "" : BuildConfig.VERSION_NAME;
        return current.contains("-") && !info.versionName.contains("-")
                && coreVersion(current).equals(coreVersion(info.versionName));
    }

    private static int compareVersions(String a, String b) {
        int[] av = parseCore(a);
        int[] bv = parseCore(b);
        for (int i = 0; i < 3; i++) {
            if (av[i] != bv[i]) return Integer.compare(av[i], bv[i]);
        }
        boolean aPre = a != null && a.contains("-");
        boolean bPre = b != null && b.contains("-");
        if (aPre != bPre) return aPre ? -1 : 1;
        return 0;
    }

    private static String coreVersion(String value) {
        int[] v = parseCore(value);
        return v[0] + "." + v[1] + "." + v[2];
    }

    private static int[] parseCore(String value) {
        int[] out = new int[] {0, 0, 0};
        if (value == null) return out;
        String s = value.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int dash = s.indexOf('-');
        if (dash >= 0) s = s.substring(0, dash);
        String[] parts = s.split("\\.");
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try { out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", "")); }
            catch (Exception ignored) { out[i] = 0; }
        }
        return out;
    }

    static File downloadAndVerify(Context context, UpdateInfo info, ProgressCallback callback) throws Exception {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = context.getFilesDir();
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("无法创建下载目录");
        File apk = new File(dir, info.apkName.isEmpty() ? "XNAT-update.apk" : info.apkName);
        File part = new File(apk.getAbsolutePath() + ".part");
        if (part.exists()) part.delete();
        downloadFile(info.apkUrl, part, callback);
        String checksumText = getText(info.sha256Url);
        String expected = extractSha256(checksumText);
        if (expected.isEmpty()) {
            part.delete();
            throw new Exception("SHA-256 校验文件格式错误");
        }
        String actual = sha256(part);
        if (!expected.equalsIgnoreCase(actual)) {
            part.delete();
            throw new Exception("APK SHA-256 校验失败，已取消安装");
        }
        if (apk.exists()) apk.delete();
        if (!part.renameTo(apk)) {
            copyFile(part, apk);
            part.delete();
        }
        return apk;
    }

    private static HttpURLConnection open(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        conn.setRequestProperty("User-Agent", "XNAT-Android/" + BuildConfig.VERSION_NAME);
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);
        return conn;
    }

    private static String getText(String url) throws Exception {
        HttpURLConnection conn = open(new URL(url));
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("下载校验文件失败（HTTP " + code + "）");
            return readText(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private static void downloadFile(String url, File target, ProgressCallback callback) throws Exception {
        HttpURLConnection conn = open(new URL(url));
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("APK 下载失败（HTTP " + code + "）");
            long total = conn.getContentLengthLong();
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n == 0) continue;
                    out.write(buf, 0, n);
                    done += n;
                    if (callback != null) callback.onProgress(done, total);
                }
                out.getFD().sync();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String extractSha256(String text) {
        if (text == null) return "";
        String[] tokens = text.trim().split("\\s+");
        for (String token : tokens) {
            if (token.matches("(?i)[0-9a-f]{64}")) return token.toLowerCase(Locale.US);
        }
        return "";
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) if (n > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    private static void copyFile(File from, File to) throws Exception {
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) if (n > 0) out.write(buf, 0, n);
            out.getFD().sync();
        }
    }

    private static String readText(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
