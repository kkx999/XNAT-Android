package com.xnat.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.animation.ArgbEvaluator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());

    // Motion system: short, low-amplitude transitions. The goal is to make
    // navigation feel alive without moving whole layouts far enough to look shaky.
    private final TimeInterpolator motionEnter = new PathInterpolator(0.20f, 0.0f, 0.0f, 1.0f);
    private final TimeInterpolator motionStandard = new PathInterpolator(0.40f, 0.0f, 0.20f, 1.0f);
    private long pausedAt = 0L;
    private boolean resumedOnce = false;

    private static final String DEFAULT_BASE_URL = "https://xnat.666101.xyz";

    private SharedPreferences prefs;
    private LinearLayout root;
    private String baseUrl = DEFAULT_BASE_URL;
    private String token = "";

    private EditText usernameInput;
    private EditText passwordInput;
    private EditText totpInput;
    private TextView passwordToggle;
    private Button loginButton;
    private ProgressBar loginBusy;
    private boolean insecureHttpAcknowledged = false;
    private boolean passwordVisible = false;

    private FrameLayout contentHost;
    private LinearLayout navBar;
    private int currentTab = 0;
    private int screenGeneration = 0;
    private boolean inDetail = false;
    private int currentDetailServerId = 0;
    private int currentTicketId = 0;
    private int detailParentTab = 1;
    private boolean actionInProgress = false;
    private boolean managementActionInProgress = false;
    private long lastBackPressAt = 0L;

    // Keep the last successful payload for each top-level screen.
    // Switching tabs now paints immediately from cache and refreshes quietly
    // in the background instead of replacing the whole page with a loader.
    private JSONObject cachedHome = null;
    private JSONArray cachedServices = null;
    private JSONObject cachedBilling = null;
    private JSONObject cachedSupport = null;
    private JSONObject cachedMe = null;
    private JSONObject cachedCatalog = null;
    private final Map<Integer, JSONObject> cachedServerDetails = new HashMap<>();
    private final Map<Integer, JSONObject> cachedTicketDetails = new HashMap<>();

    private static final int TAB_HOME = 0;
    private static final int TAB_SERVICES = 1;
    private static final int TAB_BILLING = 2;
    private static final int TAB_SUPPORT = 3;
    private static final int TAB_ME = 4;

    private static final String THEME_SYSTEM = "system";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";

    private String themeMode = THEME_SYSTEM;
    private boolean darkModeActive = false;
    // Theme motion: selector movement is local, while the actual palette
    // swap is cross-faded from a snapshot. No API request or Activity recreate.
    private boolean suppressNextMeNetworkRefresh = false;
    private boolean themeTransitionInProgress = false;
    private int themeTransitionGeneration = 0;

    // XNAT deep navy brand palette with a complete dark-mode palette.
    private int BG;
    private int CARD;
    private int INK;
    private int MUTED;
    private int BORDER;
    private int SOFT;
    private int SOFT_BLUE;
    private int BLUE;
    private int BLUE_SOFT;
    private int GREEN;
    private int GREEN_SOFT;
    private int RED;
    private int RED_SOFT;
    private int AMBER;
    private int AMBER_SOFT;
    private int NAVY;
    private int NAVY_2;
    private int RIPPLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("xnat", MODE_PRIVATE);
        baseUrl = ApiClient.normalizeBase(prefs.getString("base_url", DEFAULT_BASE_URL));
        if (!ApiClient.isValidBaseUrl(baseUrl) || (!isDebugBuild() && !baseUrl.startsWith("https://"))) {
            baseUrl = DEFAULT_BASE_URL;
            prefs.edit().putString("base_url", baseUrl).apply();
        }
        token = SecureTokenStore.load(prefs);
        applyThemePalette();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
        applySystemBarInsets();

        if (!baseUrl.isEmpty() && !token.isEmpty()) {
            showApp(TAB_HOME);
        } else {
            showLogin();
        }
    }

    private void showLogin() {
        inDetail = false;
        screenGeneration++;
        passwordVisible = false;
        root.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = column();
        page.setPadding(dp(22), dp(28), dp(22), dp(34));
        scroll.addView(page);
        root.addView(scroll, match());

        LinearLayout brand = horizontalRow();
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView brandMark = text("X", 20, Color.WHITE, true);
        brandMark.setGravity(Gravity.CENTER);
        brandMark.setBackground(gradientRoundRect(NAVY, NAVY_2, 14));
        brand.addView(brandMark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        gapH(brand, 12);
        LinearLayout brandCopy = column();
        TextView brandTitle = text("XNAT", 31, INK, true);
        brandCopy.addView(brandTitle);
        TextView brandSub = text("服务器管理客户端", 12, MUTED, false);
        brandSub.setPadding(0, dp(1), 0, 0);
        brandCopy.addView(brandSub);
        brand.addView(brandCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(brand, matchWrap());

        View.OnLongClickListener advancedEntry = v -> {
            subtleHaptic(v);
            showAdvancedServerSettings();
            return true;
        };
        brand.setOnLongClickListener(advancedEntry);
        brandMark.setOnLongClickListener(advancedEntry);
        brandTitle.setOnLongClickListener(advancedEntry);

        gap(page, 34);

        LinearLayout card = roundedBox(CARD, 30, 0, 0);
        card.setPadding(dp(22), dp(24), dp(22), dp(22));
        page.addView(card, matchWrap());

        LinearLayout titleCopy = column();
        titleCopy.addView(text("欢迎回来", 25, INK, true));
        TextView desc = text("登录后即可管理服务、账务与支持。", 12, MUTED, false);
        desc.setPadding(0, dp(4), 0, 0);
        titleCopy.addView(desc);
        card.addView(titleCopy, matchWrap());
        gap(card, 22);

        card.addView(fieldLabel("用户名"));
        gap(card, 7);
        usernameInput = loginInput("请输入用户名", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        usernameInput.setText(prefs.getString("username", ""));
        usernameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(usernameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        gap(card, 16);

        card.addView(fieldLabel("密码"));
        gap(card, 7);
        LinearLayout passwordShell = horizontalRow();
        passwordShell.setGravity(Gravity.CENTER_VERTICAL);
        passwordShell.setBackground(roundRect(SOFT, dp(18), BORDER, 1));
        passwordInput = loginBareInput("请输入密码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordShell.addView(passwordInput, new LinearLayout.LayoutParams(0, dp(56), 1));
        passwordToggle = text("显示", 12, BLUE, true);
        passwordToggle.setGravity(Gravity.CENTER);
        passwordToggle.setPadding(dp(12), 0, dp(12), 0);
        passwordToggle.setBackground(rippleRoundRect(Color.TRANSPARENT, 14, 0, RIPPLE));
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(dp(58), dp(44));
        toggleLp.rightMargin = dp(4);
        passwordShell.addView(passwordToggle, toggleLp);
        passwordShell.setOnClickListener(v -> {
            passwordInput.requestFocus();
            passwordInput.setSelection(passwordInput.length());
        });
        card.addView(passwordShell, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        totpInput = input("请输入两步验证码", InputType.TYPE_CLASS_NUMBER);
        totpInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        totpInput.setVisibility(View.GONE);
        LinearLayout.LayoutParams totpLp = matchWrap();
        totpLp.topMargin = dp(14);
        card.addView(totpInput, totpLp);

        usernameInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                passwordInput.requestFocus();
                return true;
            }
            return false;
        });
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                login();
                return true;
            }
            return false;
        });
        totpInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                login();
                return true;
            }
            return false;
        });

        gap(card, 20);
        loginButton = primaryButton("登录");
        loginButton.setMinHeight(dp(56));
        loginButton.setBackground(rippleRoundRect(BLUE, 18, 0, Color.argb(42, 255, 255, 255)));
        loginButton.setOnClickListener(v -> login());
        card.addView(loginButton, matchWrap());

        loginBusy = new ProgressBar(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) loginBusy.setIndeterminateTintList(ColorStateList.valueOf(BLUE));
        loginBusy.setVisibility(View.GONE);
        LinearLayout.LayoutParams busyLp = new LinearLayout.LayoutParams(dp(26), dp(26));
        busyLp.gravity = Gravity.CENTER_HORIZONTAL;
        busyLp.topMargin = dp(12);
        card.addView(loginBusy, busyLp);

        LinearLayout securityRow = horizontalRow();
        securityRow.setGravity(Gravity.CENTER);
        boolean secureTransport = baseUrl.startsWith("https://");
        View dot = new View(this);
        dot.setBackground(roundRect(secureTransport ? GREEN : AMBER, dp(99), 0, 0));
        securityRow.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        gapH(securityRow, 8);
        String securityText = secureTransport
                ? "HTTPS 安全连接 · 凭据由 Android Keystore 加密保存"
                : "HTTP 测试连接 · 仅建议临时调试使用";
        TextView securityTextView = text(securityText, 10, MUTED, false);
        securityTextView.setAlpha(0.86f);
        securityRow.addView(securityTextView);
        LinearLayout.LayoutParams secLp = matchWrap();
        secLp.topMargin = dp(18);
        page.addView(securityRow, secLp);

        TextView footer = text("XNAT Android v1.1.0", 10, MUTED, false);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setAlpha(0.72f);
        LinearLayout.LayoutParams footerLp = matchWrap();
        footerLp.topMargin = dp(16);
        page.addView(footer, footerLp);
    }

    private void login() {
        String url = ApiClient.normalizeBase(baseUrl);
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String totp = totpInput.getText().toString().trim();

        if (!ApiClient.isValidBaseUrl(url)) {
            toast("客户端连接地址无效，请打开高级连接设置");
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            toast("请输入用户名和密码");
            return;
        }
        if (url.startsWith("http://") && !insecureHttpAcknowledged) {
            new AlertDialog.Builder(this)
                    .setTitle("连接不安全")
                    .setMessage("当前高级连接设置使用 HTTP，用户名、密码和令牌在传输过程中可能被窃听。仅建议 Debug 测试使用。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("继续测试", (d, w) -> {
                        insecureHttpAcknowledged = true;
                        performLogin(url, username, password, totp);
                    })
                    .show();
            return;
        }
        performLogin(url, username, password, totp);
    }

    private void performLogin(String url, String username, String password, String totp) {
        setLoginBusy(true);
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);
                if (!totp.isEmpty()) body.put("totp_code", totp);

                JSONObject out = ApiClient.request(url, "/api/v1/auth/login", "POST", null, body);
                if (out.optBoolean("two_factor_required", false)) {
                    main.post(() -> {
                        setLoginBusy(false);
                        totpInput.setVisibility(View.VISIBLE);
                        totpInput.requestFocus();
                        toast("请输入两步验证码后再次登录");
                    });
                    return;
                }

                String newToken = out.optString("access_token", "");
                if (newToken.isEmpty()) throw new Exception("服务器未返回登录令牌");

                SecureTokenStore.save(prefs, newToken);
                baseUrl = url;
                token = newToken;
                prefs.edit().putString("base_url", baseUrl).putString("username", username).apply();
                main.post(() -> showApp(TAB_HOME));
            } catch (Exception e) {
                main.post(() -> {
                    setLoginBusy(false);
                    toast(message(e));
                });
            }
        });
    }

    private void setLoginBusy(boolean value) {
        loginButton.setEnabled(!value);
        loginButton.setText(value ? "登录中…" : "登录");
        loginButton.setAlpha(value ? 0.78f : 1f);
        loginBusy.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    private void showApp(int tab) {
        root.removeAllViews();
        inDetail = false;

        LinearLayout shell = column();
        contentHost = new FrameLayout(this);
        shell.addView(contentHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        navBar = buildBottomNav();
        shell.addView(navBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)));
        root.addView(shell, match());
        selectTab(tab);
    }

    private LinearLayout buildBottomNav() {
        LinearLayout wrap = column();
        wrap.setBackgroundColor(BG);
        // Keep the navigation visually separated by a hairline instead of a heavy
        // elevation shadow. This avoids the dirty grey edges visible on light themes.
        wrap.setPadding(dp(12), dp(4), dp(12), dp(8));

        LinearLayout row = horizontalRow();
        row.setTag("nav_row");
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(2), dp(2), dp(2));
        row.setBackground(roundRect(CARD, dp(21), BORDER, 1));
        row.setElevation(0f);
        row.addView(navItem("首页", TAB_HOME), navCellWeight());
        row.addView(navItem("服务", TAB_SERVICES), navCellWeight());
        row.addView(navItem("账务", TAB_BILLING), navCellWeight());
        row.addView(navItem("支持", TAB_SUPPORT), navCellWeight());
        row.addView(navItem("我的", TAB_ME), navCellWeight());
        wrap.addView(row, match());
        return wrap;
    }

    private LinearLayout navItem(String label, int tab) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.setTag(tab);
        item.setPadding(dp(3), dp(4), dp(3), dp(3));

        NavIconView icon = new NavIconView(tab);
        icon.setTag("icon");
        item.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));
        gap(item, 3);
        TextView txt = text(label, 10, MUTED, false);
        txt.setTag("label");
        txt.setGravity(Gravity.CENTER);
        item.addView(txt, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        item.setBackground(rippleRoundRect(Color.TRANSPARENT, 15, 0, RIPPLE));
        item.setOnClickListener(v -> { subtleHaptic(v); selectTab(tab); });
        return item;
    }

    private void updateBottomNav() {
        if (navBar == null) return;
        View found = navBar.findViewWithTag("nav_row");
        if (!(found instanceof LinearLayout)) return;
        LinearLayout row = (LinearLayout) found;
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout item = (LinearLayout) child;
            int tab = (int) item.getTag();
            boolean active = tab == currentTab;
            View iconView = item.findViewWithTag("icon");
            TextView label = item.findViewWithTag("label");
            if (iconView instanceof NavIconView) ((NavIconView) iconView).setActive(active);
            label.animate().cancel();
            label.setTextColor(active ? BLUE : MUTED);
            label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            label.setAlpha(active ? 1f : 0.78f);
            // The selected surface is intentionally compact and low-contrast; the
            // blue icon/label carry the state, not a large block of blue fill.
            item.setBackground(rippleRoundRect(active ? SOFT_BLUE : Color.TRANSPARENT, 14, 0, RIPPLE));
            item.animate().cancel();
            item.setScaleX(1f);
            item.setScaleY(1f);
            if (active) {
                item.setAlpha(0.84f);
                item.animate().alpha(1f).setDuration(160L).setInterpolator(motionEnter).start();
            } else {
                item.setAlpha(1f);
            }
        }
    }

    private void selectTab(int tab) {
        // Tapping the already-selected tab should not tear down and rebuild the page.
        if (!inDetail && tab == currentTab && contentHost != null && contentHost.getChildCount() > 0) {
            updateBottomNav();
            return;
        }
        int oldTab = currentTab;
        boolean returningFromDetail = inDetail;
        currentTab = tab;
        inDetail = false;
        screenGeneration++;
        updateBottomNav();
        if (navBar != null) navBar.setVisibility(View.VISIBLE);
        if (tab == TAB_HOME) showHomePage();
        else if (tab == TAB_SERVICES) showServicesPage();
        else if (tab == TAB_BILLING) showBillingPage();
        else if (tab == TAB_SUPPORT) showSupportPage();
        else showMePage();
        int direction = returningFromDetail ? -1 : Integer.compare(tab, oldTab);
        animateContentIn(direction);
    }

    private void reloadCurrentTab() {
        // Re-render directly without first clearing contentHost. This avoids the
        // one-frame white/empty flash that used to happen after power or management
        // actions while still issuing a fresh background request.
        inDetail = false;
        screenGeneration++;
        int tab = currentTab;
        if (tab == TAB_HOME) showHomePage();
        else if (tab == TAB_SERVICES) showServicesPage();
        else if (tab == TAB_BILLING) showBillingPage();
        else if (tab == TAB_SUPPORT) showSupportPage();
        else showMePage();
    }

    private void showHomePage() {
        int gen = screenGeneration;
        SwipeRefreshLayout swipe = swipeContainer();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = pageColumn();
        scroll.addView(page);
        swipe.addView(scroll, match());
        contentHost.removeAllViews();
        contentHost.addView(swipe, frameMatch());

        LinearLayout header = topHeader("概览", "XNAT · 服务器管理中心", "刷新", () -> {
            swipe.setRefreshing(true);
            loadHome(gen, swipe, page, false);
        });
        page.addView(header, matchWrap());
        gap(page, 18);
        boolean hasCache = cachedHome != null;
        if (hasCache) renderHome(page, cachedHome, gen, swipe);
        else showInlineLoading(page, "正在读取 XNAT 数据…");

        swipe.setOnRefreshListener(() -> loadHome(gen, swipe, page, false));
        loadHome(gen, swipe, page, !hasCache);
    }

    private void loadHome(int gen, SwipeRefreshLayout swipe, LinearLayout page, boolean first) {
        io.execute(() -> {
            try {
                JSONObject data = ApiClient.request(baseUrl, "/api/v1/dashboard", "GET", token, null);
                JSONObject list = ApiClient.request(baseUrl, "/api/v1/servers", "GET", token, null);
                data.put("servers", list.optJSONArray("items"));
                main.post(() -> {
                    if (!validScreen(gen, TAB_HOME)) return;
                    swipe.setRefreshing(false);
                    cachedHome = data;
                    renderHome(page, data, gen, swipe);
                    if (first) animateLoadedContent(page);
                });
            } catch (Exception e) {
                main.post(() -> handlePageError(gen, TAB_HOME, swipe, page, e, () -> loadHome(gen, swipe, page, false)));
            }
        });
    }

    private void renderHome(LinearLayout page, JSONObject data, int gen, SwipeRefreshLayout swipe) {
        int keepScrollY = beginStableRender(page);
        try {
            page.removeViews(1, page.getChildCount() - 1);
            gap(page, 18);
            JSONObject user = data.optJSONObject("user");
            JSONObject stats = data.optJSONObject("stats");
            JSONArray servers = data.optJSONArray("servers");
            if (user == null) user = new JSONObject();
            if (stats == null) stats = new JSONObject();

            LinearLayout hero = column();
            hero.setBackground(gradientRoundRect(NAVY, NAVY_2, 24));
            hero.setPadding(dp(20), dp(19), dp(20), dp(20));
            LinearLayout heroTop = horizontalRow();
            heroTop.setGravity(Gravity.CENTER_VERTICAL);
            heroTop.addView(text(greeting() + "，" + user.optString("username", "用户"), 21, Color.WHITE, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            heroTop.addView(pill("安全连接", Color.rgb(219, 234, 254), Color.argb(34, 255, 255, 255)));
            hero.addView(heroTop, matchWrap());
            TextView heroSub = text("集中查看服务状态、余额与常用操作", 12, Color.rgb(191, 203, 221), false);
            heroSub.setPadding(0, dp(5), 0, dp(18));
            hero.addView(heroSub);
            LinearLayout balanceRow = horizontalRow();
            balanceRow.setGravity(Gravity.BOTTOM);
            LinearLayout balanceBlock = column();
            balanceBlock.addView(text("账户余额", 11, Color.rgb(181, 197, 220), false));
            TextView balance = text("¥" + money(user.optLong("balance_cents", 0)), 27, Color.WHITE, true);
            balance.setPadding(0, dp(2), 0, 0);
            balanceBlock.addView(balance);
            balanceRow.addView(balanceBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            balanceRow.addView(text(stats.optInt("running_count", 0) + " 运行中 / " + stats.optInt("server_count", 0) + " 服务", 12, Color.rgb(219, 234, 254), true));
            hero.addView(balanceRow, matchWrap());
            page.addView(hero, matchWrap());
            gap(page, 14);

            LinearLayout row1 = horizontalRow();
            row1.addView(statCard("全部服务", stats.optInt("server_count", 0), BLUE), weighted());
            gapH(row1, 10);
            row1.addView(statCard("运行中", stats.optInt("running_count", 0), GREEN), weighted());
            page.addView(row1, matchWrap());
            gap(page, 10);
            LinearLayout row2 = horizontalRow();
            row2.addView(statCard("已关机", stats.optInt("stopped_count", 0), MUTED), weighted());
            gapH(row2, 10);
            row2.addView(statCard("7天内到期", stats.optInt("expiring_7d", 0), AMBER), weighted());
            page.addView(row2, matchWrap());
            gap(page, 18);
            page.addView(purchaseEntryCard(), matchWrap());
            gap(page, 26);

            page.addView(sectionHeader("我的服务", "查看状态、进入详情或执行电源操作", servers == null ? "0 台" : servers.length() + " 台"));
            gap(page, 12);
            if (servers == null || servers.length() == 0) {
                page.addView(emptyCard("暂无服务器", "当前账户暂时没有可管理的服务。"), matchWrap());
            } else {
                for (int i = 0; i < servers.length(); i++) {
                    JSONObject server = servers.optJSONObject(i);
                    if (server != null) page.addView(serverCard(server, true), matchWrap());
                    if (i < servers.length() - 1) gap(page, 12);
                }
            }
            TextView hint = text("下拉即可刷新服务状态", 11, MUTED, false);
            hint.setGravity(Gravity.CENTER_HORIZONTAL);
            hint.setPadding(0, dp(20), 0, 0);
            page.addView(hint, matchWrap());
        
        } finally {
            endStableRender(page, keepScrollY);
        }
    }

    private void showServicesPage() {
        int gen = screenGeneration;
        SwipeRefreshLayout swipe = swipeContainer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = pageColumn();
        scroll.addView(page);
        swipe.addView(scroll, match());
        contentHost.removeAllViews();
        contentHost.addView(swipe, frameMatch());
        page.addView(topHeader("服务", "全部 XNAT 实例", "刷新", () -> {
            swipe.setRefreshing(true);
            loadServices(gen, swipe, page, false);
        }), matchWrap());
        gap(page, 18);
        boolean hasCache = cachedServices != null;
        if (hasCache) renderServices(page, cachedServices);
        else showInlineLoading(page, "正在读取服务列表…");
        swipe.setOnRefreshListener(() -> loadServices(gen, swipe, page, false));
        loadServices(gen, swipe, page, !hasCache);
    }

    private void loadServices(int gen, SwipeRefreshLayout swipe, LinearLayout page, boolean first) {
        io.execute(() -> {
            try {
                JSONObject list = ApiClient.request(baseUrl, "/api/v1/servers", "GET", token, null);
                JSONArray items = list.optJSONArray("items");
                main.post(() -> {
                    if (!validScreen(gen, TAB_SERVICES)) return;
                    swipe.setRefreshing(false);
                    cachedServices = items;
                    renderServices(page, items);
                    if (first) animateLoadedContent(page);
                });
            } catch (Exception e) {
                main.post(() -> handlePageError(gen, TAB_SERVICES, swipe, page, e, () -> loadServices(gen, swipe, page, false)));
            }
        });
    }

    private void renderServices(LinearLayout page, JSONArray items) {
        int keepScrollY = beginStableRender(page);
        try {
            page.removeViews(1, page.getChildCount() - 1);
            gap(page, 18);
            int count = items == null ? 0 : items.length();
            int running = 0;
            if (items != null) for (int i = 0; i < items.length(); i++) if ("running".equals(items.optJSONObject(i).optString("status"))) running++;

            LinearLayout summary = flatAccentCard(BLUE_SOFT, 20);
            summary.setPadding(dp(18), dp(16), dp(18), dp(16));
            LinearLayout summaryRow = horizontalRow();
            LinearLayout block1 = column();
            block1.addView(text("全部服务", 11, MUTED, false));
            block1.addView(text(String.valueOf(count), 24, INK, true));
            summaryRow.addView(block1, weighted());
            LinearLayout block2 = column();
            block2.addView(text("运行中", 11, MUTED, false));
            block2.addView(text(String.valueOf(running), 24, GREEN, true));
            summaryRow.addView(block2, weighted());
            summary.addView(summaryRow, matchWrap());
            page.addView(summary, matchWrap());
            gap(page, 12);
            page.addView(purchaseEntryCard(), matchWrap());
            gap(page, 20);
            page.addView(sectionHeader("服务列表", "点击“查看详情”查看完整配置与端口", count + " 台"));
            gap(page, 12);
            if (count == 0) {
                page.addView(emptyCard("暂无服务器", "当前账户暂时没有可管理的服务。"), matchWrap());
                return;
            }
            for (int i = 0; i < count; i++) {
                JSONObject s = items.optJSONObject(i);
                if (s != null) page.addView(serverCard(s, true), matchWrap());
                if (i < count - 1) gap(page, 12);
            }
        
        } finally {
            endStableRender(page, keepScrollY);
        }
    }

    private void showServerDetail(int serverId) {
        inDetail = true;
        detailParentTab = TAB_SERVICES;
        currentTicketId = 0;
        currentDetailServerId = serverId;
        screenGeneration++;
        int gen = screenGeneration;
        if (navBar != null) navBar.setVisibility(View.GONE);

        SwipeRefreshLayout swipe = swipeContainer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = pageColumn();
        scroll.addView(page);
        swipe.addView(scroll, match());
        contentHost.removeAllViews();
        contentHost.addView(swipe, frameMatch());
        animateContentIn(1);

        LinearLayout top = horizontalRow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = compactButton("‹ 服务");
        back.setOnClickListener(v -> selectTab(TAB_SERVICES));
        top.addView(back);
        gapH(top, 12);
        LinearLayout title = column();
        title.addView(text("服务详情", 23, INK, true));
        title.addView(text("完整实例信息", 11, MUTED, false));
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(top, matchWrap());
        gap(page, 18);
        JSONObject cached = cachedServerDetails.get(serverId);
        boolean hasCache = cached != null;
        if (hasCache) renderServerDetail(page, cached);
        else showInlineLoading(page, "正在读取服务详情…");

        swipe.setOnRefreshListener(() -> loadServerDetail(gen, swipe, page, serverId, false));
        loadServerDetail(gen, swipe, page, serverId, !hasCache);
    }

    private void loadServerDetail(int gen, SwipeRefreshLayout swipe, LinearLayout page, int serverId, boolean first) {
        io.execute(() -> {
            try {
                JSONObject s = ApiClient.request(baseUrl, "/api/v1/servers/" + serverId, "GET", token, null);
                main.post(() -> {
                    if (!inDetail || gen != screenGeneration || currentDetailServerId != serverId) return;
                    swipe.setRefreshing(false);
                    cachedServerDetails.put(serverId, s);
                    renderServerDetail(page, s);
                    if (first) animateLoadedContent(page);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!inDetail || gen != screenGeneration) return;
                    swipe.setRefreshing(false);
                    if (handleUnauthorized(e)) return;
                    if (cachedServerDetails.containsKey(serverId)) {
                        toast("刷新失败：" + message(e));
                        return;
                    }
                    page.removeViews(1, page.getChildCount() - 1);
                    gap(page, 18);
                    page.addView(errorCard(message(e), () -> loadServerDetail(gen, swipe, page, serverId, false)), matchWrap());
                });
            }
        });
    }

    private void renderServerDetail(LinearLayout page, JSONObject s) {
        int keepScrollY = beginStableRender(page);
        try {
            page.removeViews(1, page.getChildCount() - 1);
            gap(page, 18);
            String status = s.optString("status", "unknown");

            LinearLayout hero = flatAccentCard(SOFT_BLUE, 22);
            hero.setPadding(dp(18), dp(18), dp(18), dp(18));
            LinearLayout titleRow = horizontalRow();
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout nameBlock = column();
            nameBlock.addView(text(s.optString("name", "VPS"), 24, INK, true));
            TextView ip = text(s.optString("public_ip", "-") + portSuffix(s.optInt("ssh_port", 0)), 13, MUTED, false);
            ip.setPadding(0, dp(5), 0, 0);
            nameBlock.addView(ip);
            titleRow.addView(nameBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            titleRow.addView(pill(statusLabel(status), statusColor(status), statusBackground(status)));
            hero.addView(titleRow, matchWrap());
            page.addView(hero, matchWrap());
            gap(page, 14);

            LinearLayout actionsCard = surfaceCard(18);
            actionsCard.setPadding(dp(8), dp(5), dp(8), dp(5));
            actionsCard.addView(powerActions(s), matchWrap());
            page.addView(actionsCard, matchWrap());
            gap(page, 22);

            page.addView(sectionHeader("资源配置", "实例规格与系统信息", ""));
            gap(page, 10);
            LinearLayout resource = surfaceCard(18);
            resource.setPadding(dp(16), dp(6), dp(16), dp(6));
            resource.addView(infoRow("CPU", s.optInt("cpu", 0) + " vCPU"));
            resource.addView(thinDivider());
            resource.addView(infoRow("内存", s.optInt("memory_mb", 0) + " MB"));
            resource.addView(thinDivider());
            resource.addView(infoRow("硬盘", s.optInt("disk_gb", 0) + " GB"));
            resource.addView(thinDivider());
            resource.addView(infoRow("带宽", s.optInt("bandwidth_mbps", 0) + " Mbps"));
            resource.addView(thinDivider());
            resource.addView(infoRow("系统", blankDash(s.optString("os_name", ""))));
            resource.addView(thinDivider());
            resource.addView(infoRow("虚拟化", blankDash(s.optString("virtualization_type", ""))));
            page.addView(resource, matchWrap());
            gap(page, 22);

            int portCount = s.optInt("port_count", s.optJSONArray("ports") == null ? 0 : s.optJSONArray("ports").length());
            int portLimit = s.optInt("port_limit", 0);
            page.addView(sectionHeader("网络", "公网、私网与 NAT 端口", portLimit > 0 ? (portCount + "/" + portLimit) : ""));
            gap(page, 10);
            LinearLayout network = surfaceCard(18);
            network.setPadding(dp(16), dp(6), dp(16), dp(6));
            network.addView(infoRow("公网 IP", blankDash(s.optString("public_ip", ""))));
            network.addView(thinDivider());
            network.addView(infoRow("私网 IP", blankDash(s.optString("private_ip", ""))));
            network.addView(thinDivider());
            network.addView(infoRow("SSH 端口", s.optInt("ssh_port", 0) > 0 ? String.valueOf(s.optInt("ssh_port")) : "-"));
            page.addView(network, matchWrap());
            gap(page, 10);
            page.addView(portManagementCard(s), matchWrap());
            gap(page, 22);

            page.addView(sectionHeader("流量与生命周期", "配额、到期与当前生命周期", ""));
            gap(page, 10);
            LinearLayout life = surfaceCard(18);
            life.setPadding(dp(16), dp(14), dp(16), dp(14));
            long used = s.optLong("traffic_used_bytes", 0);
            int quota = s.optInt("traffic_quota_gb", 0);
            life.addView(infoRow("已用流量", bytes(used) + " / " + quota + " GB"));
            ProgressBar traffic = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            traffic.setMax(1000);
            traffic.setProgress(trafficProgress(used, quota));
            traffic.setProgressTintList(ColorStateList.valueOf(s.optBoolean("traffic_throttled", false) ? AMBER : BLUE));
            traffic.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
            tlp.topMargin = dp(8);
            tlp.bottomMargin = dp(10);
            life.addView(traffic, tlp);
            life.addView(thinDivider());
            life.addView(infoRow("剩余流量", bytes(s.optLong("traffic_remaining_bytes", 0))));
            life.addView(thinDivider());
            life.addView(infoRow("到期时间", s.isNull("expires_at") ? "长期有效" : cleanDate(s.optString("expires_at"))));
            JSONObject lifecycle = s.optJSONObject("lifecycle");
            if (lifecycle != null) {
                life.addView(thinDivider());
                String code = lifecycle.optString("code", "active");
                life.addView(infoRow("生命周期", lifecycleLabel(code)));
            }
            page.addView(life, matchWrap());
            gap(page, 22);

            page.addView(sectionHeader("系统管理", "重装操作会清空系统盘，请谨慎执行", ""));
            gap(page, 10);
            LinearLayout systemCard = surfaceCard(18);
            systemCard.setPadding(dp(16), dp(14), dp(16), dp(14));
            systemCard.addView(infoRow("当前系统", blankDash(s.optString("os_name", ""))));
            systemCard.addView(thinDivider());
            TextView warning = text("重装系统会删除当前系统盘数据，并重新生成登录凭据。提交后任务会在后台执行。", 12, RED, false);
            warning.setPadding(0, dp(12), 0, dp(12));
            systemCard.addView(warning);
            Button reinstall = sheetButton("重装系统", RED, RED_SOFT, 0);
            reinstall.setOnClickListener(v -> showReinstallSheet(s));
            systemCard.addView(reinstall, matchWrap());
            page.addView(systemCard, matchWrap());
        
        } finally {
            endStableRender(page, keepScrollY);
        }
    }


    private LinearLayout purchaseEntryCard() {
        LinearLayout card = flatAccentCard(SOFT_BLUE, 19);
        card.setPadding(dp(17), dp(15), dp(14), dp(15));
        LinearLayout row = horizontalRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = column();
        labels.addView(text("购买服务器", 16, INK, true));
        TextView sub = text("查看在售套餐 · 选择系统 · 余额支付 · 自动开通", 11, MUTED, false);
        sub.setPadding(0, dp(4), 0, 0);
        labels.addView(sub);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button buy = compactButton("购买 ›");
        buy.setTextColor(BLUE);
        buy.setOnClickListener(v -> showCatalogPage());
        row.addView(buy);
        card.addView(row, matchWrap());
        card.setBackground(rippleRoundRect(SOFT_BLUE, 19, 0, RIPPLE));
        card.setOnClickListener(v -> { subtleHaptic(v); showCatalogPage(); });
        return card;
    }

    private void showCatalogPage() {
        detailParentTab = currentTab;
        inDetail = true;
        currentDetailServerId = 0;
        currentTicketId = 0;
        screenGeneration++;
        int gen = screenGeneration;
        if (navBar != null) navBar.setVisibility(View.GONE);

        SwipeRefreshLayout swipe = swipeContainer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = pageColumn();
        scroll.addView(page);
        swipe.addView(scroll, match());
        contentHost.removeAllViews();
        contentHost.addView(swipe, frameMatch());
        animateContentIn(1);

        LinearLayout top = horizontalRow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = compactButton(detailParentTab == TAB_SERVICES ? "‹ 服务" : "‹ 首页");
        back.setOnClickListener(v -> selectTab(detailParentTab));
        top.addView(back);
        gapH(top, 12);
        LinearLayout title = column();
        title.addView(text("购买服务器", 23, INK, true));
        title.addView(text("套餐、系统与余额支付", 11, MUTED, false));
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(top, matchWrap());
        gap(page, 18);

        boolean hasCache = cachedCatalog != null;
        if (hasCache) renderCatalog(page, cachedCatalog);
        else showInlineLoading(page, "正在读取在售套餐…");
        swipe.setOnRefreshListener(() -> loadCatalog(gen, swipe, page, false));
        loadCatalog(gen, swipe, page, !hasCache);
    }

    private void loadCatalog(int gen, SwipeRefreshLayout swipe, LinearLayout page, boolean first) {
        io.execute(() -> {
            try {
                JSONObject data = ApiClient.request(baseUrl, "/api/v1/catalog", "GET", token, null);
                main.post(() -> {
                    if (!inDetail || gen != screenGeneration || currentDetailServerId != 0 || currentTicketId != 0) return;
                    swipe.setRefreshing(false);
                    cachedCatalog = data;
                    renderCatalog(page, data);
                    if (first) animateLoadedContent(page);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!inDetail || gen != screenGeneration) return;
                    swipe.setRefreshing(false);
                    if (handleUnauthorized(e)) return;
                    if (cachedCatalog != null) {
                        toast("刷新失败：" + message(e));
                        return;
                    }
                    page.removeViews(1, page.getChildCount() - 1);
                    gap(page, 18);
                    page.addView(errorCard(mobileApiFeatureMessage(e, "购买服务器"), () -> loadCatalog(gen, swipe, page, false)), matchWrap());
                });
            }
        });
    }

    private void renderCatalog(LinearLayout page, JSONObject data) {
        int keepScrollY = beginStableRender(page);
        try {
            page.removeViews(1, page.getChildCount() - 1);
            gap(page, 18);
            long balance = data.optLong("balance_cents", 0);
            JSONArray plans = data.optJSONArray("plans");
            JSONArray images = data.optJSONArray("system_images");

            LinearLayout balanceCard = flatAccentCard(SOFT_BLUE, 20);
            balanceCard.setPadding(dp(18), dp(16), dp(18), dp(16));
            LinearLayout br = horizontalRow();
            br.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout bb = column();
            bb.addView(text("可用余额", 11, MUTED, false));
            bb.addView(text("¥" + money(balance), 24, INK, true));
            br.addView(bb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView note = text("余额支付", 11, BLUE, true);
            br.addView(note);
            balanceCard.addView(br, matchWrap());
            page.addView(balanceCard, matchWrap());
            gap(page, 22);

            int count = plans == null ? 0 : plans.length();
            page.addView(sectionHeader("在售套餐", "购买后自动进入开通队列", count + " 个"));
            gap(page, 12);
            if (count == 0) {
                page.addView(emptyCard("暂无在售套餐", "当前没有可以购买的套餐，请稍后再试。"), matchWrap());
                return;
            }
            if (images == null || images.length() == 0) {
                page.addView(errorCard("当前没有可用系统镜像，暂时无法购买。", () -> showCatalogPage()), matchWrap());
                return;
            }
            for (int i = 0; i < count; i++) {
                JSONObject plan = plans.optJSONObject(i);
                if (plan != null) page.addView(planCard(plan, data), matchWrap());
                if (i < count - 1) gap(page, 12);
            }
        } finally {
            endStableRender(page, keepScrollY);
        }
    }

    private LinearLayout planCard(JSONObject plan, JSONObject catalog) {
        LinearLayout card = surfaceCard(20);
        card.setPadding(dp(17), dp(16), dp(17), dp(16));
        JSONObject stock = plan.optJSONObject("stock");
        boolean soldOut = stock != null && stock.optBoolean("sold_out", false);

        LinearLayout top = horizontalRow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = column();
        LinearLayout nameRow = horizontalRow();
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.addView(text(plan.optString("name", "XNAT 套餐"), 18, INK, true));
        if (plan.optBoolean("is_recommended", false)) {
            gapH(nameRow, 8);
            nameRow.addView(pill(plan.optString("recommendation_label", "推荐"), BLUE, BLUE_SOFT));
        }
        names.addView(nameRow, matchWrap());
        String virt = plan.optString("virtualization_type", "lxc").toUpperCase();
        TextView meta = text(virt + " · " + plan.optInt("port_count", 0) + " 个 NAT 端口", 11, MUTED, false);
        meta.setPadding(0, dp(4), 0, 0);
        names.addView(meta);
        top.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (soldOut) top.addView(pill("已售罄", RED, RED_SOFT));
        else if (stock != null && !stock.isNull("available")) top.addView(pill("余 " + stock.optInt("available", 0), GREEN, GREEN_SOFT));
        else top.addView(pill("有货", GREEN, GREEN_SOFT));
        card.addView(top, matchWrap());
        gap(card, 14);

        LinearLayout spec = roundedBox(SOFT, 14, 0, 0);
        spec.setPadding(dp(13), dp(7), dp(13), dp(7));
        spec.addView(infoRow("配置", plan.optInt("cpu", 0) + " vCPU · " + plan.optInt("memory_mb", 0) + " MB · " + plan.optInt("disk_gb", 0) + " GB"));
        spec.addView(thinDivider());
        spec.addView(infoRow("网络", plan.optInt("bandwidth_mbps", 0) + " Mbps · " + plan.optInt("traffic_gb", 0) + " GB 流量"));
        card.addView(spec, matchWrap());
        gap(card, 14);

        LinearLayout bottom = horizontalRow();
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout price = column();
        price.addView(text("月付", 10, MUTED, false));
        price.addView(text("¥" + money(plan.optLong("monthly_price_cents", 0)), 22, INK, true));
        bottom.addView(price, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button choose = sheetButton(soldOut ? "已售罄" : "选择套餐", Color.WHITE, soldOut ? MUTED : BLUE, 0);
        setActionEnabled(choose, !soldOut);
        choose.setOnClickListener(v -> showPurchaseSheet(plan, catalog));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(126), ViewGroup.LayoutParams.WRAP_CONTENT);
        bottom.addView(choose, bp);
        card.addView(bottom, matchWrap());
        return card;
    }

    private void showPurchaseSheet(JSONObject plan, JSONObject catalog) {
        JSONArray images = catalog.optJSONArray("system_images");
        if (images == null || images.length() == 0) {
            toast("当前没有可用系统镜像");
            return;
        }
        Dialog dialog = bottomDialog();
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("配置新服务器", 22, INK, true));
        TextView desc = text(plan.optString("name", "套餐") + " · ¥" + money(plan.optLong("monthly_price_cents", 0)) + "/月", 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(14));
        sheet.addView(desc);

        LinearLayout summary = roundedBox(SOFT, 14, 0, 0);
        summary.setPadding(dp(13), dp(7), dp(13), dp(7));
        summary.addView(infoRow("CPU / 内存", plan.optInt("cpu", 0) + " vCPU / " + plan.optInt("memory_mb", 0) + " MB"));
        summary.addView(thinDivider());
        summary.addView(infoRow("硬盘 / 带宽", plan.optInt("disk_gb", 0) + " GB / " + plan.optInt("bandwidth_mbps", 0) + " Mbps"));
        summary.addView(thinDivider());
        summary.addView(infoRow("流量 / 端口", plan.optInt("traffic_gb", 0) + " GB / " + plan.optInt("port_count", 0) + " 个"));
        sheet.addView(summary, matchWrap());
        gap(sheet, 14);

        sheet.addView(text("选择系统", 12, MUTED, true));
        gap(sheet, 8);
        LinearLayout options = column();
        final int[] selectedId = {0};
        final String[] selectedName = {""};
        final int[] selectedIndex = {-1};
        final Button[] nextRef = new Button[1];
        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.optJSONObject(i);
            if (image == null) continue;
            final int idx = i;
            final int imageId = image.optInt("id", 0);
            final String imageName = image.optString("name", "系统镜像");
            LinearLayout option = systemImageOption(imageName, image.optString("alias", ""), false, false);
            option.setTag("purchase-image-" + i);
            option.setOnClickListener(v -> {
                subtleHaptic(v);
                selectedId[0] = imageId;
                selectedName[0] = imageName;
                selectedIndex[0] = idx;
                for (int x = 0; x < options.getChildCount(); x++) {
                    View child = options.getChildAt(x);
                    if (!(child instanceof LinearLayout)) continue;
                    boolean selected = ("purchase-image-" + selectedIndex[0]).equals(String.valueOf(child.getTag()));
                    child.setBackground(roundRect(selected ? BLUE_SOFT : SOFT, dp(18), selected ? BLUE : 0, selected ? 1 : 0));
                    TextView mark = child.findViewWithTag("image-mark");
                    if (mark != null) {
                        mark.setText(selected ? "✓ 已选择" : "选择");
                        mark.setTextColor(selected ? BLUE : MUTED);
                        mark.setBackground(roundRect(selected ? SOFT_BLUE : Color.TRANSPARENT, dp(12), 0, 0));
                    }
                }
                if (nextRef[0] != null) setActionEnabled(nextRef[0], true);
            });
            options.addView(option, matchWrap());
            if (i < images.length() - 1) gap(options, 8);
        }
        sheet.addView(options, matchWrap());
        gap(sheet, 14);

        sheet.addView(text("优惠码（可选）", 12, MUTED, true));
        gap(sheet, 7);
        EditText coupon = input("输入优惠码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        sheet.addView(coupon, matchWrap());
        gap(sheet, 18);

        LinearLayout buttons = horizontalRow();
        Button cancel = sheetButton("取消", INK, SOFT, BORDER);
        Button next = sheetButton("计算价格", Color.WHITE, BLUE, 0);
        setActionEnabled(next, false);
        nextRef[0] = next;
        buttons.addView(cancel, weighted());
        gapH(buttons, 10);
        buttons.addView(next, weighted());
        sheet.addView(buttons, matchWrap());
        cancel.setOnClickListener(v -> dialog.dismiss());
        next.setOnClickListener(v -> {
            if (selectedId[0] <= 0) {
                toast("请选择系统");
                return;
            }
            String code = coupon.getText().toString().trim().toUpperCase();
            runPurchaseQuote(dialog, next, plan, selectedId[0], selectedName[0], code);
        });
        showBottomDialog(dialog, sheet);
    }

    private void runPurchaseQuote(Dialog currentDialog, Button next, JSONObject plan, int imageId, String imageName, String couponCode) {
        if (managementActionInProgress) return;
        managementActionInProgress = true;
        next.setEnabled(false);
        next.setText("正在计算…");
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("plan_id", plan.optInt("id", 0));
                body.put("coupon_code", couponCode);
                JSONObject quote = ApiClient.request(baseUrl, "/api/v1/purchase/quote", "POST", token, body);
                main.post(() -> {
                    managementActionInProgress = false;
                    currentDialog.dismiss();
                    buildPurchaseConfirmSheet(currentDialog, plan, imageId, imageName, couponCode, quote);
                });
            } catch (Exception e) {
                main.post(() -> {
                    managementActionInProgress = false;
                    next.setText("计算价格");
                    next.setEnabled(true);
                    if (!handleUnauthorized(e)) toast("价格试算失败：" + message(e));
                });
            }
        });
    }

    private void buildPurchaseConfirmSheet(Dialog dialog, JSONObject plan, int imageId, String imageName, String couponCode, JSONObject quote) {
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("确认购买", 22, INK, true));
        TextView desc = text("确认配置和价格后，将直接从账户余额扣款并进入自动开通队列。", 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(14));
        sheet.addView(desc);

        LinearLayout config = roundedBox(SOFT, 14, 0, 0);
        config.setPadding(dp(13), dp(7), dp(13), dp(7));
        config.addView(infoRow("套餐", plan.optString("name", "-")));
        config.addView(thinDivider());
        config.addView(infoRow("系统", imageName));
        config.addView(thinDivider());
        config.addView(infoRow("虚拟化", plan.optString("virtualization_type", "lxc").toUpperCase()));
        sheet.addView(config, matchWrap());
        gap(sheet, 12);

        long listPrice = quote.optLong("list_price_cents", plan.optLong("monthly_price_cents", 0));
        long discount = quote.optLong("discount_cents", 0);
        long finalPrice = quote.optLong("final_price_cents", listPrice);
        long balance = quote.optLong("balance_cents", 0);
        boolean sufficient = quote.optBoolean("sufficient_balance", balance >= finalPrice);
        LinearLayout moneyBox = roundedBox(CARD, 14, BORDER, 1);
        moneyBox.setPadding(dp(13), dp(7), dp(13), dp(7));
        moneyBox.addView(infoRow("套餐价格", "¥" + money(listPrice)));
        if (discount > 0) {
            moneyBox.addView(thinDivider());
            moneyBox.addView(infoRow("优惠", "-¥" + money(discount)));
        }
        moneyBox.addView(thinDivider());
        moneyBox.addView(infoRow("本次支付", "¥" + money(finalPrice)));
        moneyBox.addView(thinDivider());
        moneyBox.addView(infoRow("当前余额", "¥" + money(balance)));
        moneyBox.addView(thinDivider());
        moneyBox.addView(infoRow("支付后余额", sufficient ? "¥" + money(balance - finalPrice) : "余额不足"));
        sheet.addView(moneyBox, matchWrap());
        if (!couponCode.isEmpty()) {
            TextView coupon = text("优惠码：" + couponCode + (discount > 0 ? " · 已生效" : ""), 11, discount > 0 ? GREEN : MUTED, true);
            coupon.setPadding(0, dp(9), 0, 0);
            sheet.addView(coupon);
        }
        gap(sheet, 18);

        LinearLayout buttons = horizontalRow();
        Button cancel = sheetButton("返回", INK, SOFT, BORDER);
        buttons.addView(cancel, weighted());
        gapH(buttons, 10);
        if (sufficient) {
            Button confirm = sheetButton("余额支付 ¥" + money(finalPrice), Color.WHITE, BLUE, 0);
            buttons.addView(confirm, weighted());
            final String requestId = UUID.randomUUID().toString();
            confirm.setOnClickListener(v -> runPurchase(dialog, confirm, plan.optInt("id", 0), imageId, couponCode, requestId));
        } else {
            Button recharge = sheetButton("去充值", Color.WHITE, BLUE, 0);
            buttons.addView(recharge, weighted());
            recharge.setOnClickListener(v -> {
                dialog.dismiss();
                openRechargePage();
            });
        }
        sheet.addView(buttons, matchWrap());
        cancel.setOnClickListener(v -> {
            dialog.dismiss();
            showPurchaseSheet(plan, cachedCatalog == null ? new JSONObject() : cachedCatalog);
        });
        swapBottomDialogContent(dialog, sheet, true);
    }

    private void runPurchase(Dialog dialog, Button confirm, int planId, int imageId, String couponCode, String requestId) {
        if (managementActionInProgress) return;
        managementActionInProgress = true;
        confirm.setEnabled(false);
        confirm.setText("正在提交…");
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("plan_id", planId);
                body.put("os_image_id", imageId);
                body.put("coupon_code", couponCode);
                body.put("request_id", requestId);
                JSONObject out = ApiClient.request(baseUrl, "/api/v1/purchase", "POST", token, body);
                main.post(() -> {
                    managementActionInProgress = false;
                    dialog.dismiss();
                    cachedHome = null;
                    cachedServices = null;
                    cachedBilling = null;
                    cachedCatalog = null;
                    cachedMe = null;
                    JSONObject server = out.optJSONObject("server");
                    JSONObject job = out.optJSONObject("job");
                    int serverId = server == null ? 0 : server.optInt("id", 0);
                    String serverName = server == null ? "新服务器" : server.optString("name", "新服务器");
                    int jobId = job == null ? 0 : job.optInt("id", 0);
                    showPurchaseSuccessSheet(serverId, serverName, jobId, out.optLong("balance_after_cents", 0));
                });
            } catch (Exception e) {
                main.post(() -> {
                    managementActionInProgress = false;
                    confirm.setEnabled(true);
                    confirm.setText("重新提交");
                    if (!handleUnauthorized(e)) toast("购买失败：" + message(e));
                });
            }
        });
    }

    private void showPurchaseSuccessSheet(int serverId, String serverName, int jobId, long balanceAfter) {
        Dialog dialog = bottomDialog();
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("已进入开通队列", 22, INK, true));
        TextView desc = text("购买已完成，XNAT 正在为你自动部署新服务器。通常数秒到数分钟完成。", 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(14));
        sheet.addView(desc);
        LinearLayout box = roundedBox(GREEN_SOFT, 14, 0, 0);
        box.setPadding(dp(13), dp(7), dp(13), dp(7));
        box.addView(infoRow("服务器", serverName));
        box.addView(thinDivider());
        box.addView(infoRow("开通任务", jobId > 0 ? "#" + jobId : "已提交"));
        box.addView(thinDivider());
        box.addView(infoRow("剩余余额", "¥" + money(balanceAfter)));
        sheet.addView(box, matchWrap());
        gap(sheet, 18);
        Button view = sheetButton("查看新服务器", Color.WHITE, BLUE, 0);
        sheet.addView(view, matchWrap());
        view.setOnClickListener(v -> {
            dialog.dismiss();
            if (serverId > 0) showServerDetail(serverId); else selectTab(TAB_SERVICES);
        });
        showBottomDialog(dialog, sheet);
    }

    private void openRechargePage() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(ApiClient.normalizeBase(baseUrl) + "/recharge"));
            startActivity(intent);
        } catch (Exception e) {
            toast("无法打开充值页面");
        }
    }

    private void showBillingPage() {
        int gen = screenGeneration;
        SwipeRefreshLayout swipe = swipeContainer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = pageColumn();
        scroll.addView(page);
        swipe.addView(scroll, match());
        contentHost.removeAllViews();
        contentHost.addView(swipe, frameMatch());
        page.addView(topHeader("账务", "余额、订单与充值记录", "刷新", () -> {
            swipe.setRefreshing(true);
            loadBilling(gen, swipe, page, false);
        }), matchWrap());
        gap(page, 18);
        boolean hasCache = cachedBilling != null;
        if (hasCache) renderBilling(page, cachedBilling);
        else showInlineLoading(page, "正在读取账务数据…");
        swipe.setOnRefreshListener(() -> loadBilling(gen, swipe, page, false));
        loadBilling(gen, swipe, page, !hasCache);
    }

    private void loadBilling(int gen, SwipeRefreshLayout swipe, LinearLayout page, boolean first) {
        io.execute(() -> {
            try {
                JSONObject data = ApiClient.request(baseUrl, "/api/v1/billing", "GET", token, null);
                main.post(() -> {
                    if (!validScreen(gen, TAB_BILLING)) return;
                    swipe.setRefreshing(false);
                    cachedBilling = data;
                    renderBilling(page, data);
                    if (first) animateLoadedContent(page);
                });
            } catch (Exception e) {
                main.post(() -> handlePageError(gen, TAB_BILLING, swipe, page, e, () -> loadBilling(gen, swipe, page, false)));
            }
        });
    }

    private void renderBilling(LinearLayout page, JSONObject data) {
        int keepScrollY = beginStableRender(page);
        try {
            page.removeViews(1, page.getChildCount() - 1);
            gap(page, 18);
            JSONObject summary = data.optJSONObject("summary");
            if (summary == null) summary = new JSONObject();

            LinearLayout hero = column();
            hero.setBackground(gradientRoundRect(NAVY, NAVY_2, 22));
            hero.setPadding(dp(20), dp(18), dp(20), dp(18));
            hero.addView(text("可用余额", 12, Color.rgb(191, 203, 221), false));
            TextView balance = text("¥" + money(summary.optLong("balance_cents", 0)), 30, Color.WHITE, true);
            balance.setPadding(0, dp(3), 0, dp(14));
            hero.addView(balance);
            LinearLayout heroRow = horizontalRow();
            heroRow.addView(miniMetric("累计消费", "¥" + money(summary.optLong("total_spend_cents", 0))), weighted());
            heroRow.addView(miniMetric("订单", String.valueOf(summary.optInt("order_count", 0))), weighted());
            heroRow.addView(miniMetric("充值", String.valueOf(summary.optInt("recharge_count", 0))), weighted());
            hero.addView(heroRow, matchWrap());
            page.addView(hero, matchWrap());
            gap(page, 24);

            JSONArray orders = data.optJSONArray("orders");
            page.addView(sectionHeader("订单", "最近的服务购买与续费记录", orders == null ? "0" : String.valueOf(orders.length())));
            gap(page, 10);
            if (orders == null || orders.length() == 0) page.addView(emptyCard("暂无订单", "当前账户还没有订单记录。"), matchWrap());
            else {
                for (int i = 0; i < Math.min(orders.length(), 20); i++) {
                    JSONObject o = orders.optJSONObject(i);
                    if (o != null) page.addView(orderCard(o), matchWrap());
                    if (i < Math.min(orders.length(), 20) - 1) gap(page, 9);
                }
            }
            gap(page, 24);

            JSONArray ledger = data.optJSONArray("ledger");
            page.addView(sectionHeader("余额流水", "最近的余额变动", ledger == null ? "0" : String.valueOf(ledger.length())));
            gap(page, 10);
            if (ledger == null || ledger.length() == 0) page.addView(emptyCard("暂无流水", "当前账户还没有余额流水。"), matchWrap());
            else {
                for (int i = 0; i < Math.min(ledger.length(), 20); i++) {
                    JSONObject row = ledger.optJSONObject(i);
                    if (row != null) page.addView(ledgerCard(row), matchWrap());
                    if (i < Math.min(ledger.length(), 20) - 1) gap(page, 9);
                }
            }
            gap(page, 24);

            JSONArray recharges = data.optJSONArray("recharges");
            page.addView(sectionHeader("充值记录", "最近的 USDT 充值订单", recharges == null ? "0" : String.valueOf(recharges.length())));
            gap(page, 10);
            if (recharges == null || recharges.length() == 0) page.addView(emptyCard("暂无充值", "当前账户还没有充值记录。"), matchWrap());
            else {
                for (int i = 0; i < Math.min(recharges.length(), 20); i++) {
                    JSONObject r = recharges.optJSONObject(i);
                    if (r != null) page.addView(rechargeCard(r), matchWrap());
                    if (i < Math.min(recharges.length(), 20) - 1) gap(page, 9);
                }
            }
        
        } finally {
            endStableRender(page, keepScrollY);
        }
    }


    private void showSupportPage() {
        int gen = screenGeneration;
        SwipeRefreshLayout swipe = swipeContainer();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = pageColumn();
        scroll.addView(page);
        swipe.addView(scroll, match());
        contentHost.removeAllViews();
        contentHost.addView(swipe, frameMatch());

        page.addView(topHeader("支持", "提交问题并跟踪 XNAT 工单", "新建", this::showNewTicketSheet), matchWrap());
        gap(page, 18);
        boolean hasCache = cachedSupport != null;
        if (hasCache) renderSupport(page, cachedSupport);
        else showInlineLoading(page, "正在读取工单…");
        swipe.setOnRefreshListener(() -> loadSupport(gen, swipe, page, false));
        loadSupport(gen, swipe, page, !hasCache);
    }

    private void loadSupport(int gen, SwipeRefreshLayout swipe, LinearLayout page, boolean first) {
        io.execute(() -> {
            try {
                JSONObject data = ApiClient.request(baseUrl, "/api/v1/tickets", "GET", token, null);
                main.post(() -> {
                    if (!validScreen(gen, TAB_SUPPORT)) return;
                    swipe.setRefreshing(false);
                    cachedSupport = data;
                    renderSupport(page, data);
                    if (first) animateLoadedContent(page);
                });
            } catch (Exception e) {
                main.post(() -> handlePageError(gen, TAB_SUPPORT, swipe, page, e, () -> loadSupport(gen, swipe, page, false)));
            }
        });
    }

    private void renderSupport(LinearLayout page, JSONObject data) {
        int keepScrollY = beginStableRender(page);
        try {
            page.removeViews(1, page.getChildCount() - 1);
            gap(page, 18);
            int count = data.optInt("count", 0);
            int openCount = data.optInt("open_count", 0);
            JSONArray items = data.optJSONArray("items");

            LinearLayout hero = flatAccentCard(SOFT_BLUE, 22);
            hero.setPadding(dp(18), dp(18), dp(18), dp(18));
            LinearLayout heroTop = horizontalRow();
            heroTop.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout heroTitle = column();
            heroTitle.addView(text("XNAT 支持中心", 22, INK, true));
            TextView heroSub = text("技术、服务与账务问题都可以在这里提交", 12, MUTED, false);
            heroSub.setPadding(0, dp(4), 0, 0);
            heroTitle.addView(heroSub);
            heroTop.addView(heroTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            heroTop.addView(pill(openCount > 0 ? openCount + " 个处理中" : "暂无待处理", openCount > 0 ? AMBER : GREEN, openCount > 0 ? AMBER_SOFT : GREEN_SOFT));
            hero.addView(heroTop, matchWrap());
            gap(hero, 16);
            Button create = sheetButton("＋ 新建工单", Color.WHITE, BLUE, 0);
            create.setOnClickListener(v -> showNewTicketSheet());
            hero.addView(create, matchWrap());
            page.addView(hero, matchWrap());
            gap(page, 24);

            page.addView(sectionHeader("我的工单", "点击工单查看回复记录", count + " 个"));
            gap(page, 10);
            if (items == null || items.length() == 0) {
                page.addView(emptyCard("还没有工单", "遇到问题时可以创建一个新工单，我们会在这里跟踪处理。"), matchWrap());
                return;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject ticket = items.optJSONObject(i);
                if (ticket != null) page.addView(ticketCard(ticket), matchWrap());
                if (i < items.length() - 1) gap(page, 10);
            }
        
        } finally {
            endStableRender(page, keepScrollY);
        }
    }

    private LinearLayout ticketCard(JSONObject ticket) {
        LinearLayout card = surfaceCard(18);
        card.setPadding(dp(17), dp(15), dp(17), dp(15));
        int ticketId = ticket.optInt("id", 0);
        String status = ticket.optString("status", "open");

        LinearLayout top = horizontalRow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView id = text("#" + ticketId, 12, MUTED, true);
        top.addView(id);
        gapH(top, 8);
        top.addView(pill(ticket.optString("priority_label", "普通") + "优先级", ticketPriorityColor(ticket.optString("priority", "normal")), ticketPriorityBackground(ticket.optString("priority", "normal"))));
        top.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));
        top.addView(pill(ticket.optString("status_label", ticketStatusLabel(status)), ticketStatusColor(status), ticketStatusBackground(status)));
        card.addView(top, matchWrap());

        TextView subject = text(ticket.optString("subject", "工单"), 17, INK, true);
        subject.setPadding(0, dp(11), 0, dp(5));
        card.addView(subject);
        card.addView(text("更新于 " + cleanDate(ticket.optString("updated_at", "")), 11, MUTED, false));
        card.setClickable(true);
        card.setBackground(rippleRoundRect(CARD, 18, 0, Color.argb(18, 37, 99, 235)));
        card.setOnClickListener(v -> showTicketDetail(ticketId));
        return card;
    }

    private void showNewTicketSheet() {
        Dialog dialog = bottomDialog();
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("新建工单", 22, INK, true));
        TextView desc = text("请描述遇到的问题。技术问题建议附上服务名称和具体错误现象。", 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(14));
        sheet.addView(desc);

        EditText subject = input("工单标题", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        sheet.addView(subject, matchWrap());
        gap(sheet, 10);
        EditText body = input("详细描述", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setSingleLine(false);
        body.setMinLines(4);
        body.setGravity(Gravity.TOP | Gravity.START);
        sheet.addView(body, matchWrap());
        gap(sheet, 14);

        sheet.addView(text("优先级", 12, MUTED, true));
        gap(sheet, 8);
        final String[] selectedPriority = {"normal"};
        LinearLayout priorities = horizontalRow();
        Button low = sheetButton("低", MUTED, SOFT, BORDER);
        Button normal = sheetButton("普通", BLUE, BLUE_SOFT, BLUE);
        Button high = sheetButton("高", RED, RED_SOFT, BORDER);
        priorities.addView(low, weighted());
        gapH(priorities, 8);
        priorities.addView(normal, weighted());
        gapH(priorities, 8);
        priorities.addView(high, weighted());
        sheet.addView(priorities, matchWrap());
        View.OnClickListener choosePriority = v -> {
            if (v == low) selectedPriority[0] = "low";
            else if (v == high) selectedPriority[0] = "high";
            else selectedPriority[0] = "normal";
            stylePriorityButtons(low, normal, high, selectedPriority[0]);
        };
        low.setOnClickListener(choosePriority);
        normal.setOnClickListener(choosePriority);
        high.setOnClickListener(choosePriority);
        gap(sheet, 18);

        LinearLayout buttons = horizontalRow();
        Button cancel = sheetButton("取消", INK, SOFT, BORDER);
        Button submit = sheetButton("提交工单", Color.WHITE, BLUE, 0);
        buttons.addView(cancel, weighted());
        gapH(buttons, 10);
        buttons.addView(submit, weighted());
        sheet.addView(buttons, matchWrap());
        cancel.setOnClickListener(v -> dialog.dismiss());
        submit.setOnClickListener(v -> {
            String title = subject.getText().toString().trim();
            String content = body.getText().toString().trim();
            if (title.isEmpty() || content.isEmpty()) {
                toast("请填写工单标题和详细描述");
                return;
            }
            dialog.dismiss();
            runCreateTicket(title, content, selectedPriority[0]);
        });
        showBottomDialog(dialog, sheet);
    }

    private void stylePriorityButtons(Button low, Button normal, Button high, String selected) {
        low.setTextColor("low".equals(selected) ? BLUE : MUTED);
        low.setBackground(roundRect("low".equals(selected) ? BLUE_SOFT : SOFT, dp(14), "low".equals(selected) ? BLUE : BORDER, 1));
        normal.setTextColor("normal".equals(selected) ? BLUE : MUTED);
        normal.setBackground(roundRect("normal".equals(selected) ? BLUE_SOFT : SOFT, dp(14), "normal".equals(selected) ? BLUE : BORDER, 1));
        high.setTextColor("high".equals(selected) ? RED : MUTED);
        high.setBackground(roundRect("high".equals(selected) ? RED_SOFT : SOFT, dp(14), "high".equals(selected) ? RED : BORDER, 1));
    }

    private void runCreateTicket(String subject, String body, String priority) {
        if (managementActionInProgress) return;
        managementActionInProgress = true;
        toast("正在提交工单…");
        io.execute(() -> {
            try {
                JSONObject payload = new JSONObject().put("subject", subject).put("body", body).put("priority", priority);
                JSONObject out = ApiClient.request(baseUrl, "/api/v1/tickets", "POST", token, payload);
                JSONObject ticket = out.optJSONObject("ticket");
                int id = ticket == null ? 0 : ticket.optInt("id", 0);
                main.post(() -> {
                    managementActionInProgress = false;
                    toast("工单已提交");
                    if (id > 0) showTicketDetail(id); else reloadCurrentTab();
                });
            } catch (Exception e) {
                main.post(() -> {
                    managementActionInProgress = false;
                    if (!handleUnauthorized(e)) toast(mobileApiFeatureMessage(e, "工单支持"));
                });
            }
        });
    }

    private void showTicketDetail(int ticketId) {
        inDetail = true;
        detailParentTab = TAB_SUPPORT;
        currentTicketId = ticketId;
        currentDetailServerId = 0;
        screenGeneration++;
        int gen = screenGeneration;
        if (navBar != null) navBar.setVisibility(View.GONE);

        SwipeRefreshLayout swipe = swipeContainer();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = pageColumn();
        scroll.addView(page);
        swipe.addView(scroll, match());
        contentHost.removeAllViews();
        contentHost.addView(swipe, frameMatch());
        animateContentIn(1);

        LinearLayout top = horizontalRow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = compactButton("‹ 支持");
        back.setOnClickListener(v -> selectTab(TAB_SUPPORT));
        top.addView(back);
        gapH(top, 12);
        LinearLayout title = column();
        title.addView(text("工单详情", 23, INK, true));
        title.addView(text("#" + ticketId, 11, MUTED, false));
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(top, matchWrap());
        gap(page, 18);
        JSONObject cached = cachedTicketDetails.get(ticketId);
        boolean hasCache = cached != null;
        if (hasCache) renderTicketDetail(page, cached);
        else showInlineLoading(page, "正在读取工单…");

        swipe.setOnRefreshListener(() -> loadTicketDetail(gen, swipe, page, ticketId, false));
        loadTicketDetail(gen, swipe, page, ticketId, !hasCache);
    }

    private void loadTicketDetail(int gen, SwipeRefreshLayout swipe, LinearLayout page, int ticketId, boolean first) {
        io.execute(() -> {
            try {
                JSONObject ticket = ApiClient.request(baseUrl, "/api/v1/tickets/" + ticketId, "GET", token, null);
                main.post(() -> {
                    if (!inDetail || detailParentTab != TAB_SUPPORT || gen != screenGeneration || currentTicketId != ticketId) return;
                    swipe.setRefreshing(false);
                    cachedTicketDetails.put(ticketId, ticket);
                    renderTicketDetail(page, ticket);
                    if (first) animateLoadedContent(page);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!inDetail || detailParentTab != TAB_SUPPORT || gen != screenGeneration) return;
                    swipe.setRefreshing(false);
                    if (handleUnauthorized(e)) return;
                    if (cachedTicketDetails.containsKey(ticketId)) {
                        toast("刷新失败：" + message(e));
                        return;
                    }
                    page.removeViews(1, page.getChildCount() - 1);
                    gap(page, 18);
                    page.addView(errorCard(message(e), () -> loadTicketDetail(gen, swipe, page, ticketId, false)), matchWrap());
                });
            }
        });
    }

    private void renderTicketDetail(LinearLayout page, JSONObject ticket) {
        int keepScrollY = beginStableRender(page);
        try {
            page.removeViews(1, page.getChildCount() - 1);
            gap(page, 18);
            int ticketId = ticket.optInt("id", 0);
            String status = ticket.optString("status", "open");

            LinearLayout summary = flatAccentCard(SOFT_BLUE, 22);
            summary.setPadding(dp(18), dp(17), dp(18), dp(17));
            LinearLayout statusRow = horizontalRow();
            statusRow.setGravity(Gravity.CENTER_VERTICAL);
            statusRow.addView(pill(ticket.optString("priority_label", "普通") + "优先级", ticketPriorityColor(ticket.optString("priority", "normal")), ticketPriorityBackground(ticket.optString("priority", "normal"))));
            statusRow.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));
            statusRow.addView(pill(ticket.optString("status_label", ticketStatusLabel(status)), ticketStatusColor(status), ticketStatusBackground(status)));
            summary.addView(statusRow, matchWrap());
            TextView subject = text(ticket.optString("subject", "工单"), 22, INK, true);
            subject.setPadding(0, dp(12), 0, dp(6));
            summary.addView(subject);
            summary.addView(text("创建于 " + cleanDate(ticket.optString("created_at", "")) + " · 更新于 " + cleanDate(ticket.optString("updated_at", "")), 11, MUTED, false));
            page.addView(summary, matchWrap());
            gap(page, 22);

            JSONArray messages = ticket.optJSONArray("messages");
            page.addView(sectionHeader("沟通记录", "管理员回复会以 XNAT 支持标识显示", messages == null ? "0" : String.valueOf(messages.length())));
            gap(page, 10);
            if (messages != null) {
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject message = messages.optJSONObject(i);
                    if (message != null) page.addView(ticketMessageCard(message), matchWrap());
                    if (i < messages.length() - 1) gap(page, 9);
                }
            }
            gap(page, 20);

            if (!"closed".equals(status)) {
                LinearLayout actions = horizontalRow();
                Button reply = sheetButton("回复工单", Color.WHITE, BLUE, 0);
                Button close = sheetButton("关闭工单", RED, RED_SOFT, 0);
                actions.addView(reply, weighted());
                gapH(actions, 10);
                actions.addView(close, weighted());
                reply.setOnClickListener(v -> showReplyTicketSheet(ticketId, ticket.optString("subject", "工单")));
                close.setOnClickListener(v -> showCloseTicketSheet(ticketId, ticket.optString("subject", "工单")));
                page.addView(actions, matchWrap());
            } else {
                page.addView(emptyCard("工单已关闭", "此工单不能继续回复。如有新问题，请创建新的工单。"), matchWrap());
            }
        
        } finally {
            endStableRender(page, keepScrollY);
        }
    }

    private LinearLayout ticketMessageCard(JSONObject message) {
        boolean admin = message.optBoolean("author_is_admin", false);
        LinearLayout card = roundedBox(admin ? BLUE_SOFT : CARD, 18, 0, 0);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout head = horizontalRow();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(message.optString("author_name", admin ? "XNAT 支持" : "我"), 12, admin ? BLUE : INK, true));
        head.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));
        head.addView(text(cleanDate(message.optString("created_at", "")), 10, MUTED, false));
        card.addView(head, matchWrap());
        TextView body = text(message.optString("body", ""), 14, INK, false);
        body.setLineSpacing(0, 1.12f);
        body.setPadding(0, dp(9), 0, 0);
        card.addView(body, matchWrap());
        return card;
    }

    private void showReplyTicketSheet(int ticketId, String subject) {
        Dialog dialog = bottomDialog();
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("回复工单", 22, INK, true));
        TextView desc = text("#" + ticketId + " · " + subject, 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(14));
        sheet.addView(desc);
        EditText body = input("输入回复内容", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setSingleLine(false);
        body.setMinLines(4);
        body.setGravity(Gravity.TOP | Gravity.START);
        sheet.addView(body, matchWrap());
        gap(sheet, 18);
        LinearLayout buttons = horizontalRow();
        Button cancel = sheetButton("取消", INK, SOFT, BORDER);
        Button send = sheetButton("发送回复", Color.WHITE, BLUE, 0);
        buttons.addView(cancel, weighted());
        gapH(buttons, 10);
        buttons.addView(send, weighted());
        sheet.addView(buttons, matchWrap());
        cancel.setOnClickListener(v -> dialog.dismiss());
        send.setOnClickListener(v -> {
            String value = body.getText().toString().trim();
            if (value.isEmpty()) { toast("请输入回复内容"); return; }
            dialog.dismiss();
            runReplyTicket(ticketId, value);
        });
        showBottomDialog(dialog, sheet);
    }

    private void runReplyTicket(int ticketId, String body) {
        if (managementActionInProgress) return;
        managementActionInProgress = true;
        toast("正在发送回复…");
        io.execute(() -> {
            try {
                ApiClient.request(baseUrl, "/api/v1/tickets/" + ticketId + "/reply", "POST", token, new JSONObject().put("body", body));
                main.post(() -> {
                    managementActionInProgress = false;
                    toast("回复已发送");
                    showTicketDetail(ticketId);
                });
            } catch (Exception e) {
                main.post(() -> {
                    managementActionInProgress = false;
                    if (!handleUnauthorized(e)) toast(mobileApiFeatureMessage(e, "工单支持"));
                });
            }
        });
    }

    private void showCloseTicketSheet(int ticketId, String subject) {
        Dialog dialog = bottomDialog();
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("关闭工单", 22, INK, true));
        TextView desc = text("#" + ticketId + " · " + subject, 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(12));
        sheet.addView(desc);
        LinearLayout note = roundedBox(RED_SOFT, 14, 0, 0);
        note.setPadding(dp(13), dp(11), dp(13), dp(11));
        note.addView(text("关闭后将不能继续回复。需要继续沟通时请创建新工单。", 12, RED, false));
        sheet.addView(note, matchWrap());
        gap(sheet, 18);
        LinearLayout buttons = horizontalRow();
        Button cancel = sheetButton("取消", INK, SOFT, BORDER);
        Button close = sheetButton("确认关闭", Color.WHITE, RED, 0);
        buttons.addView(cancel, weighted());
        gapH(buttons, 10);
        buttons.addView(close, weighted());
        sheet.addView(buttons, matchWrap());
        cancel.setOnClickListener(v -> dialog.dismiss());
        close.setOnClickListener(v -> { dialog.dismiss(); runCloseTicket(ticketId); });
        showBottomDialog(dialog, sheet);
    }

    private void runCloseTicket(int ticketId) {
        if (managementActionInProgress) return;
        managementActionInProgress = true;
        io.execute(() -> {
            try {
                ApiClient.request(baseUrl, "/api/v1/tickets/" + ticketId + "/close", "POST", token, new JSONObject());
                main.post(() -> {
                    managementActionInProgress = false;
                    toast("工单已关闭");
                    showTicketDetail(ticketId);
                });
            } catch (Exception e) {
                main.post(() -> {
                    managementActionInProgress = false;
                    if (!handleUnauthorized(e)) toast(mobileApiFeatureMessage(e, "工单支持"));
                });
            }
        });
    }

    private String ticketStatusLabel(String status) {
        if ("answered".equals(status)) return "已回复";
        if ("closed".equals(status)) return "已关闭";
        return "待处理";
    }

    private int ticketStatusColor(String status) {
        if ("answered".equals(status)) return GREEN;
        if ("closed".equals(status)) return MUTED;
        return AMBER;
    }

    private int ticketStatusBackground(String status) {
        if ("answered".equals(status)) return GREEN_SOFT;
        if ("closed".equals(status)) return SOFT;
        return AMBER_SOFT;
    }

    private int ticketPriorityColor(String priority) {
        if ("high".equals(priority)) return RED;
        if ("low".equals(priority)) return MUTED;
        return BLUE;
    }

    private int ticketPriorityBackground(String priority) {
        if ("high".equals(priority)) return RED_SOFT;
        if ("low".equals(priority)) return SOFT;
        return BLUE_SOFT;
    }

    private void showMePage() {
        int gen = screenGeneration;
        SwipeRefreshLayout swipe = swipeContainer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = pageColumn();
        scroll.addView(page);
        swipe.addView(scroll, match());
        contentHost.removeAllViews();
        contentHost.addView(swipe, frameMatch());
        page.addView(topHeader("账户", "个人信息与客户端设置", "刷新", () -> {
            swipe.setRefreshing(true);
            loadMe(gen, swipe, page, false);
        }), matchWrap());
        gap(page, 18);
        boolean hasCache = cachedMe != null;
        if (hasCache) renderMe(page, cachedMe);
        else showInlineLoading(page, "正在读取账户信息…");
        swipe.setOnRefreshListener(() -> loadMe(gen, swipe, page, false));
        boolean skipNetwork = suppressNextMeNetworkRefresh;
        suppressNextMeNetworkRefresh = false;
        if (!skipNetwork) {
            loadMe(gen, swipe, page, !hasCache);
        } else {
            swipe.setRefreshing(false);
        }
    }

    private void loadMe(int gen, SwipeRefreshLayout swipe, LinearLayout page, boolean first) {
        io.execute(() -> {
            try {
                JSONObject me = ApiClient.request(baseUrl, "/api/v1/me", "GET", token, null);
                main.post(() -> {
                    if (!validScreen(gen, TAB_ME)) return;
                    swipe.setRefreshing(false);
                    cachedMe = me;
                    renderMe(page, me);
                    if (first) animateLoadedContent(page);
                });
            } catch (Exception e) {
                main.post(() -> handlePageError(gen, TAB_ME, swipe, page, e, () -> loadMe(gen, swipe, page, false)));
            }
        });
    }

    private void renderMe(LinearLayout page, JSONObject me) {
        int keepScrollY = beginStableRender(page);
        try {
            page.removeViews(1, page.getChildCount() - 1);
            gap(page, 18);

            String username = me.optString("username", "用户");
            LinearLayout profile = column();
            profile.setBackground(gradientRoundRect(NAVY, NAVY_2, 24));
            profile.setPadding(dp(18), dp(18), dp(18), dp(18));
            LinearLayout row = horizontalRow();
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView avatar = text(profileInitial(username), 19, Color.WHITE, true);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(roundRect(Color.argb(38, 255, 255, 255), dp(18), 0, 0));
            row.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));
            gapH(row, 13);
            LinearLayout identity = column();
            identity.addView(text(username, 22, Color.WHITE, true));
            TextView email = text(blankDash(me.optString("email", "")), 12, Color.rgb(196, 210, 230), false);
            email.setPadding(0, dp(3), 0, 0);
            identity.addView(email);
            row.addView(identity, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(pill(me.optBoolean("is_admin", false) ? "管理员" : "用户", Color.rgb(225, 236, 255), Color.argb(34, 255, 255, 255)));
            profile.addView(row, matchWrap());
            gap(profile, 16);

            LinearLayout metricsBox = roundedBox(Color.argb(24, 255, 255, 255), 16, 0, 0);
            metricsBox.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout metrics = horizontalRow();
            metrics.addView(miniMetric("账户余额", "¥" + money(me.optLong("balance_cents", 0))), weighted());
            View split = new View(this);
            split.setBackgroundColor(Color.argb(40, 255, 255, 255));
            metrics.addView(split, new LinearLayout.LayoutParams(dp(1), dp(34)));
            gapH(metrics, 14);
            metrics.addView(miniMetric("未读通知", String.valueOf(me.optInt("unread_notifications", 0))), weighted());
            metricsBox.addView(metrics, matchWrap());
            profile.addView(metricsBox, matchWrap());
            page.addView(profile, matchWrap());
            gap(page, 24);

            page.addView(sectionHeader("安全与连接", "账户登录与客户端安全状态", ""));
            gap(page, 10);
            LinearLayout security = surfaceCard(18);
            security.setPadding(dp(16), dp(6), dp(16), dp(6));
            security.addView(infoRow("两步验证", me.optBoolean("totp_enabled", false) ? "已启用" : "未启用"));
            security.addView(thinDivider());
            security.addView(infoRow("账户类型", me.optBoolean("is_admin", false) ? "管理员" : "普通用户"));
            security.addView(thinDivider());
            security.addView(infoRow("传输安全", baseUrl.startsWith("https://") ? "HTTPS" : "HTTP（测试）"));
            security.addView(thinDivider());
            security.addView(infoRow("凭据存储", "Android Keystore"));
            page.addView(security, matchWrap());
            gap(page, 22);

            page.addView(sectionHeader("外观", "选择 XNAT 的显示模式", ""));
            gap(page, 10);
            LinearLayout appearance = surfaceCard(18);
            appearance.setPadding(dp(16), dp(14), dp(16), dp(14));
            appearance.addView(infoRow("当前主题", themeModeLabel()));
            gap(appearance, 12);
            appearance.addView(themeSelector(), matchWrap());
            page.addView(appearance, matchWrap());
            gap(page, 22);

            page.addView(sectionHeader("关于", "XNAT Android 客户端", ""));
            gap(page, 10);
            LinearLayout about = surfaceCard(18);
            about.setPadding(dp(16), dp(6), dp(16), dp(6));
            about.addView(infoRow("应用", "XNAT Android"));
            about.addView(thinDivider());
            LinearLayout versionRow = infoRow("版本", "1.1.0");
            versionRow.setOnLongClickListener(v -> {
                subtleHaptic(v);
                showAdvancedServerSettings();
                return true;
            });
            about.addView(versionRow);
            about.addView(thinDivider());
            about.addView(infoRow("Mobile API", "v1"));
            page.addView(about, matchWrap());
            gap(page, 20);

            Button logout = sheetButton("退出登录", RED, RED_SOFT, 0);
            logout.setOnClickListener(v -> confirmLogoutSheet());
            page.addView(logout, matchWrap());

        } finally {
            endStableRender(page, keepScrollY);
        }
    }

    private LinearLayout serverCard(JSONObject s, boolean showDetail) {
        LinearLayout c = surfaceCard(22);
        c.setPadding(dp(18), dp(17), dp(18), dp(13));
        String status = s.optString("status", "unknown");
        int id = s.optInt("id", 0);

        LinearLayout titleRow = horizontalRow();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout identity = column();
        identity.addView(text(s.optString("name", "VPS"), 18, INK, true));
        TextView address = text(blankDash(s.optString("public_ip", "")) + portSuffix(s.optInt("ssh_port", 0)), 13, MUTED, false);
        address.setPadding(0, dp(4), 0, 0);
        identity.addView(address);
        titleRow.addView(identity, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(pill(statusLabel(status), statusColor(status), statusBackground(status)));
        c.addView(titleRow, matchWrap());
        gap(c, 14);

        LinearLayout spec = roundedBox(SOFT_BLUE, 14, 0, 0);
        spec.setPadding(dp(13), dp(11), dp(13), dp(11));
        spec.addView(text("配置", 11, MUTED, true));
        TextView specValue = text(s.optInt("cpu", 0) + " vCPU · " + s.optInt("memory_mb", 0) + " MB · " + s.optInt("disk_gb", 0) + " GB · " + s.optInt("bandwidth_mbps", 0) + " Mbps", 13, INK, false);
        specValue.setPadding(0, dp(4), 0, 0);
        spec.addView(specValue);
        c.addView(spec, matchWrap());
        gap(c, 12);

        long used = s.optLong("traffic_used_bytes", 0);
        int quota = s.optInt("traffic_quota_gb", 0);
        c.addView(infoRow("流量", bytes(used) + " / " + quota + " GB"));
        ProgressBar traffic = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        traffic.setMax(1000);
        traffic.setProgress(trafficProgress(used, quota));
        traffic.setProgressTintList(ColorStateList.valueOf(BLUE));
        traffic.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        tlp.topMargin = dp(3);
        tlp.bottomMargin = dp(8);
        c.addView(traffic, tlp);
        c.addView(infoRow("到期时间", s.isNull("expires_at") ? "长期有效" : cleanDate(s.optString("expires_at"))));

        if (showDetail) {
            gap(c, 8);
            View div = thinDivider();
            c.addView(div);
            TextView detail = text("查看详情  ›", 12, BLUE, true);
            detail.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            detail.setPadding(dp(4), dp(10), dp(4), dp(8));
            detail.setBackground(rippleRoundRect(Color.TRANSPARENT, 10, 0, RIPPLE));
            detail.setOnClickListener(v -> showServerDetail(id));
            c.addView(detail, matchWrap());
        }

        c.addView(thinDivider());
        LinearLayout actions = powerActions(s);
        c.addView(actions, matchWrap());
        return c;
    }

    private LinearLayout portManagementCard(JSONObject s) {
        LinearLayout card = surfaceCard(18);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        int serverId = s.optInt("id", 0);
        String serverName = s.optString("name", "VPS");
        JSONArray ports = s.optJSONArray("ports");
        int count = ports == null ? 0 : ports.length();
        int limit = s.optInt("port_limit", 0);

        LinearLayout header = horizontalRow();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout title = column();
        title.addView(text("NAT 端口映射", 14, INK, true));
        TextView subtitle = text("公网端口由 XNAT 自动分配", 11, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, 0);
        title.addView(subtitle);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(pill(limit > 0 ? (count + " / " + limit) : String.valueOf(count), BLUE, BLUE_SOFT));
        card.addView(header, matchWrap());
        gap(card, 12);

        if (ports == null || ports.length() == 0) {
            LinearLayout empty = roundedBox(SOFT, 14, 0, 0);
            empty.setPadding(dp(13), dp(12), dp(13), dp(12));
            empty.addView(text("暂无自定义端口映射", 12, MUTED, false));
            card.addView(empty, matchWrap());
        } else {
            for (int i = 0; i < ports.length(); i++) {
                JSONObject mapping = ports.optJSONObject(i);
                if (mapping == null) continue;
                if (i > 0) card.addView(thinDivider());
                card.addView(portMappingRow(serverId, serverName, mapping), matchWrap());
            }
        }

        gap(card, 12);
        Button add = sheetButton("添加端口", BLUE, BLUE_SOFT, 0);
        boolean canAdd = limit > 0 && count < limit && !"reinstalling".equals(s.optString("status"));
        setActionEnabled(add, canAdd);
        add.setOnClickListener(v -> showAddPortSheet(serverId, serverName, count, limit));
        card.addView(add, matchWrap());
        if (limit <= 0) {
            TextView hint = text("当前实例没有可用的自定义 NAT 端口额度。", 11, MUTED, false);
            hint.setPadding(0, dp(8), 0, 0);
            card.addView(hint);
        }
        return card;
    }

    private LinearLayout portMappingRow(int serverId, String serverName, JSONObject mapping) {
        LinearLayout row = horizontalRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(11), 0, dp(11));
        LinearLayout info = column();
        String protocol = mapping.optString("protocol", "tcp").toUpperCase();
        info.addView(text(protocol + "  " + mapping.optInt("public_port") + " → " + mapping.optInt("private_port"), 13, INK, true));
        TextView sub = text("公网端口 → 内部端口", 10, MUTED, false);
        sub.setPadding(0, dp(3), 0, 0);
        info.addView(sub);
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView remove = text("删除", 12, RED, true);
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(dp(12), dp(8), dp(12), dp(8));
        remove.setBackground(rippleRoundRect(RED_SOFT, 12, 0, Color.argb(24, 220, 38, 38)));
        remove.setOnClickListener(v -> confirmDeletePort(serverId, serverName, mapping));
        row.addView(remove);
        return row;
    }

    private void showAddPortSheet(int serverId, String serverName, int used, int limit) {
        if (managementActionInProgress) {
            toast("已有管理操作正在执行，请稍候");
            return;
        }
        Dialog dialog = bottomDialog();
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("添加 NAT 端口", 21, INK, true));
        TextView desc = text("输入 VPS 内部端口。公网端口会从当前宿主机端口池自动分配。", 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(14));
        sheet.addView(desc);
        LinearLayout serverBox = roundedBox(SOFT, 14, BORDER, 1);
        serverBox.setPadding(dp(13), dp(8), dp(13), dp(8));
        serverBox.addView(infoRow("服务器", serverName));
        serverBox.addView(thinDivider());
        serverBox.addView(infoRow("端口额度", used + " / " + limit));
        sheet.addView(serverBox, matchWrap());
        gap(sheet, 12);

        EditText portInput = input("内部端口，例如 8080", InputType.TYPE_CLASS_NUMBER);
        sheet.addView(portInput, matchWrap());
        gap(sheet, 10);
        final String[] selectedProtocol = {"tcp"};
        LinearLayout protocol = horizontalRow();
        protocol.setPadding(dp(3), dp(3), dp(3), dp(3));
        protocol.setBackground(roundRect(SOFT, dp(13), BORDER, 1));
        Button tcp = compactSegmentButton("TCP", true);
        Button udp = compactSegmentButton("UDP", false);
        View.OnClickListener protocolChoice = v -> {
            boolean chooseUdp = v == udp;
            selectedProtocol[0] = chooseUdp ? "udp" : "tcp";
            styleCompactSegment(tcp, !chooseUdp);
            styleCompactSegment(udp, chooseUdp);
        };
        tcp.setOnClickListener(protocolChoice);
        udp.setOnClickListener(protocolChoice);
        protocol.addView(tcp, weighted());
        gapH(protocol, 4);
        protocol.addView(udp, weighted());
        sheet.addView(protocol, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        gap(sheet, 18);

        LinearLayout buttons = horizontalRow();
        Button cancel = sheetButton("取消", INK, SOFT, BORDER);
        Button confirm = sheetButton("添加端口", Color.WHITE, BLUE, 0);
        buttons.addView(cancel, weighted());
        gapH(buttons, 10);
        buttons.addView(confirm, weighted());
        sheet.addView(buttons, matchWrap());
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            String value = portInput.getText().toString().trim();
            int privatePort;
            try { privatePort = Integer.parseInt(value); } catch (Exception e) { privatePort = 0; }
            if (privatePort < 1 || privatePort > 65535) {
                toast("请输入 1–65535 的有效内部端口");
                return;
            }
            String proto = selectedProtocol[0];
            dialog.dismiss();
            runAddPort(serverId, privatePort, proto);
        });
        showBottomDialog(dialog, sheet);
    }

    private void runAddPort(int serverId, int privatePort, String protocol) {
        if (managementActionInProgress) return;
        managementActionInProgress = true;
        toast("正在添加端口…");
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("private_port", privatePort).put("protocol", protocol);
                JSONObject out = ApiClient.request(baseUrl, "/api/v1/servers/" + serverId + "/ports", "POST", token, body);
                JSONObject mapping = out.optJSONObject("mapping");
                main.post(() -> {
                    managementActionInProgress = false;
                    if (mapping != null) toast("已分配公网端口 " + mapping.optInt("public_port"));
                    else toast("端口已添加");
                    showServerDetail(serverId);
                });
            } catch (Exception e) {
                main.post(() -> {
                    managementActionInProgress = false;
                    if (!handleUnauthorized(e)) toast(mobileApiFeatureMessage(e, "端口管理"));
                });
            }
        });
    }

    private void confirmDeletePort(int serverId, String serverName, JSONObject mapping) {
        if (managementActionInProgress) {
            toast("已有管理操作正在执行，请稍候");
            return;
        }
        int mappingId = mapping.optInt("id", 0);
        Dialog dialog = bottomDialog();
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("删除端口映射", 21, INK, true));
        TextView desc = text("删除后，该公网端口将立即停止转发。", 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(14));
        sheet.addView(desc);
        LinearLayout box = roundedBox(RED_SOFT, 14, 0, 0);
        box.setPadding(dp(13), dp(9), dp(13), dp(9));
        box.addView(infoRow("服务器", serverName));
        box.addView(thinDivider());
        box.addView(infoRow("映射", mapping.optString("protocol", "tcp").toUpperCase() + "  " + mapping.optInt("public_port") + " → " + mapping.optInt("private_port")));
        sheet.addView(box, matchWrap());
        gap(sheet, 18);
        LinearLayout buttons = horizontalRow();
        Button cancel = sheetButton("取消", INK, SOFT, BORDER);
        Button confirm = sheetButton("删除", Color.WHITE, RED, 0);
        buttons.addView(cancel, weighted());
        gapH(buttons, 10);
        buttons.addView(confirm, weighted());
        sheet.addView(buttons, matchWrap());
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            runDeletePort(serverId, mappingId);
        });
        showBottomDialog(dialog, sheet);
    }

    private void runDeletePort(int serverId, int mappingId) {
        if (managementActionInProgress) return;
        managementActionInProgress = true;
        toast("正在删除端口…");
        io.execute(() -> {
            try {
                ApiClient.request(baseUrl, "/api/v1/servers/" + serverId + "/ports/" + mappingId, "DELETE", token, null);
                main.post(() -> {
                    managementActionInProgress = false;
                    toast("端口映射已删除");
                    showServerDetail(serverId);
                });
            } catch (Exception e) {
                main.post(() -> {
                    managementActionInProgress = false;
                    if (!handleUnauthorized(e)) toast(mobileApiFeatureMessage(e, "端口管理"));
                });
            }
        });
    }

    private void showReinstallSheet(JSONObject server) {
        if (managementActionInProgress || actionInProgress) {
            toast("已有服务器操作正在执行，请稍候");
            return;
        }
        final int serverId = server.optInt("id", 0);
        final String serverName = server.optString("name", "VPS");
        managementActionInProgress = true;
        toast("正在读取可用系统镜像…");
        io.execute(() -> {
            try {
                JSONObject out = ApiClient.request(baseUrl, "/api/v1/system-images", "GET", token, null);
                JSONArray images = out.optJSONArray("items");
                if (images == null || images.length() == 0) throw new Exception("当前没有可用的系统镜像");
                main.post(() -> {
                    managementActionInProgress = false;
                    buildReinstallSheet(serverId, serverName, server.optString("os_name", ""), images);
                });
            } catch (Exception e) {
                main.post(() -> {
                    managementActionInProgress = false;
                    if (!handleUnauthorized(e)) toast(mobileApiFeatureMessage(e, "系统重装"));
                });
            }
        });
    }

    private void buildReinstallSheet(int serverId, String serverName, String currentOs, JSONArray images) {
        Dialog dialog = bottomDialog();
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("选择重装系统", 22, INK, true));
        TextView desc = text("选择目标系统后，下一步会再次要求确认。重装将清空系统盘。", 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(12));
        sheet.addView(desc);

        LinearLayout danger = roundedBox(RED_SOFT, 14, 0, 0);
        danger.setPadding(dp(13), dp(11), dp(13), dp(11));
        danger.addView(text("不可撤销 · 请先备份 VPS 内的重要数据", 12, RED, true));
        sheet.addView(danger, matchWrap());
        gap(sheet, 12);

        LinearLayout current = roundedBox(SOFT, 14, 0, 0);
        current.setPadding(dp(13), dp(8), dp(13), dp(8));
        current.addView(infoRow("服务器", serverName));
        current.addView(thinDivider());
        current.addView(infoRow("当前系统", blankDash(currentOs)));
        sheet.addView(current, matchWrap());
        gap(sheet, 14);

        sheet.addView(text("可用系统", 12, MUTED, true));
        gap(sheet, 8);
        LinearLayout options = column();
        final int[] selectedIndex = {-1};
        final int[] selectedId = {0};
        final String[] selectedName = {""};
        final Button[] continueButton = new Button[1];

        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.optJSONObject(i);
            if (image == null) continue;
            final int index = i;
            final int imageId = image.optInt("id", 0);
            final String imageName = image.optString("name", image.optString("alias", "系统镜像"));
            LinearLayout option = systemImageOption(imageName, image.optString("alias", ""), imageName.equalsIgnoreCase(currentOs), false);
            option.setTag("image-option-" + i);
            option.setOnClickListener(v -> {
                subtleHaptic(v);
                selectedIndex[0] = index;
                selectedId[0] = imageId;
                selectedName[0] = imageName;
                for (int x = 0; x < options.getChildCount(); x++) {
                    View child = options.getChildAt(x);
                    if (child instanceof LinearLayout) {
                        boolean chosen = ("image-option-" + selectedIndex[0]).equals(String.valueOf(child.getTag()));
                        child.setBackground(roundRect(chosen ? BLUE_SOFT : SOFT, dp(18), chosen ? BLUE : 0, chosen ? 1 : 0));
                        TextView mark = child.findViewWithTag("image-mark");
                        if (mark != null) {
                            mark.setText(chosen ? "✓ 已选择" : "选择");
                            mark.setTextColor(chosen ? BLUE : MUTED);
                            mark.setBackground(roundRect(chosen ? SOFT_BLUE : Color.TRANSPARENT, dp(12), 0, 0));
                        }
                    }
                }
                if (continueButton[0] != null) {
                    continueButton[0].setEnabled(true);
                    continueButton[0].setAlpha(1f);
                }
            });
            options.addView(option, matchWrap());
            if (i < images.length() - 1) gap(options, 8);
        }
        sheet.addView(options, matchWrap());
        gap(sheet, 18);

        LinearLayout buttons = horizontalRow();
        Button cancel = sheetButton("取消", INK, SOFT, BORDER);
        Button next = sheetButton("继续", Color.WHITE, BLUE, 0);
        next.setEnabled(false);
        next.setAlpha(0.45f);
        continueButton[0] = next;
        buttons.addView(cancel, weighted());
        gapH(buttons, 10);
        buttons.addView(next, weighted());
        sheet.addView(buttons, matchWrap());
        cancel.setOnClickListener(v -> dialog.dismiss());
        next.setOnClickListener(v -> {
            if (selectedId[0] <= 0 || selectedName[0].isEmpty()) {
                toast("请选择要安装的系统");
                return;
            }
            buildReinstallConfirmSheet(dialog, serverId, serverName, currentOs, selectedId[0], selectedName[0], images);
        });
        showBottomDialog(dialog, sheet);
    }

    private LinearLayout systemImageOption(String name, String alias, boolean current, boolean selected) {
        LinearLayout option = horizontalRow();
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(dp(12), dp(11), dp(13), dp(11));
        // Use a soft filled surface instead of a hard grey outline on every row.
        // Selection still gets the brand border so the state remains unambiguous.
        option.setBackground(roundRect(selected ? BLUE_SOFT : SOFT, dp(18), selected ? BLUE : 0, selected ? 1 : 0));

        String iconIdentity = (name == null ? "" : name) + " " + (alias == null ? "" : alias);
        option.addView(new SystemIconView(iconIdentity), new LinearLayout.LayoutParams(dp(40), dp(40)));
        gapH(option, 12);

        LinearLayout labels = column();
        LinearLayout nameRow = horizontalRow();
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.addView(text(name, 15, INK, true));
        if (current) {
            gapH(nameRow, 8);
            nameRow.addView(pill("当前", BLUE, BLUE_SOFT));
        }
        labels.addView(nameRow, matchWrap());
        if (alias != null && !alias.trim().isEmpty() && !alias.equalsIgnoreCase(name)) {
            TextView aliasView = text(alias, 11, MUTED, false);
            aliasView.setPadding(0, dp(3), 0, 0);
            labels.addView(aliasView);
        }
        option.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView mark = text(selected ? "✓ 已选择" : "选择", 12, selected ? BLUE : MUTED, true);
        mark.setTag("image-mark");
        mark.setGravity(Gravity.CENTER);
        mark.setPadding(dp(10), dp(7), dp(10), dp(7));
        mark.setBackground(roundRect(selected ? SOFT_BLUE : Color.TRANSPARENT, dp(12), 0, 0));
        option.addView(mark);
        return option;
    }

    private void buildReinstallConfirmSheet(Dialog dialog, int serverId, String serverName, String currentOs, int imageId, String imageName, JSONArray images) {
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("确认重装", 22, INK, true));
        TextView desc = text("请核对目标系统并输入服务器名称完成最后确认。", 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(12));
        sheet.addView(desc);

        LinearLayout transition = roundedBox(SOFT_BLUE, 16, 0, 0);
        transition.setPadding(dp(14), dp(10), dp(14), dp(10));
        transition.addView(infoRow("服务器", serverName));
        transition.addView(thinDivider());
        transition.addView(infoRow("当前系统", blankDash(currentOs)));
        transition.addView(thinDivider());
        transition.addView(infoRow("目标系统", imageName));
        sheet.addView(transition, matchWrap());
        gap(sheet, 12);

        LinearLayout warning = roundedBox(RED_SOFT, 14, 0, 0);
        warning.setPadding(dp(13), dp(11), dp(13), dp(11));
        warning.addView(text("重装会清空当前系统盘，并重新生成登录凭据。此操作不可撤销。", 12, RED, true));
        sheet.addView(warning, matchWrap());
        gap(sheet, 12);

        EditText confirmName = input("输入服务器名称确认：" + serverName, InputType.TYPE_CLASS_TEXT);
        sheet.addView(confirmName, matchWrap());
        gap(sheet, 18);

        LinearLayout buttons = horizontalRow();
        Button back = sheetButton("返回选择", INK, SOFT, BORDER);
        Button confirm = sheetButton("确认重装", Color.WHITE, RED, 0);
        buttons.addView(back, weighted());
        gapH(buttons, 10);
        buttons.addView(confirm, weighted());
        sheet.addView(buttons, matchWrap());
        back.setOnClickListener(v -> {
            dialog.dismiss();
            buildReinstallSheet(serverId, serverName, currentOs, images);
        });
        confirm.setOnClickListener(v -> {
            if (!serverName.equals(confirmName.getText().toString().trim())) {
                toast("请输入完整服务器名称 “" + serverName + "” 进行确认");
                return;
            }
            dialog.dismiss();
            runReinstall(serverId, serverName, imageId, imageName);
        });
        swapBottomDialogContent(dialog, sheet, true);
    }

    private void runReinstall(int serverId, String serverName, int imageId, String imageName) {
        if (managementActionInProgress) return;
        managementActionInProgress = true;
        toast("正在提交重装任务…");
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("os_image_id", imageId).put("confirm_name", serverName);
                JSONObject out = ApiClient.request(baseUrl, "/api/v1/servers/" + serverId + "/reinstall", "POST", token, body);
                main.post(() -> {
                    managementActionInProgress = false;
                    toast("重装任务 #" + out.optInt("job_id", 0) + " 已提交 · " + imageName);
                    main.postDelayed(() -> showServerDetail(serverId), 800);
                });
            } catch (Exception e) {
                main.post(() -> {
                    managementActionInProgress = false;
                    if (!handleUnauthorized(e)) toast(mobileApiFeatureMessage(e, "系统重装"));
                });
            }
        });
    }

    private String mobileApiFeatureMessage(Exception e, String feature) {
        if (e instanceof ApiClient.ApiException && ((ApiClient.ApiException) e).status == 404) {
            return "当前 Panel 的 Mobile API 尚未提供“" + feature + "”接口，请更新到最新 Mobile API 后重试";
        }
        return message(e);
    }

    private LinearLayout powerActions(JSONObject s) {
        LinearLayout actions = horizontalRow();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(3), 0, 0);
        int id = s.optInt("id", 0);
        String name = s.optString("name", "VPS");
        String status = s.optString("status", "unknown");
        Button start = groupedActionButton("开机", BLUE);
        Button stop = groupedActionButton("关机", RED);
        Button reboot = groupedActionButton("重启", INK);
        setActionEnabled(start, !"running".equals(status));
        setActionEnabled(stop, !"stopped".equals(status));
        setActionEnabled(reboot, "running".equals(status));
        start.setOnClickListener(v -> confirmAction(id, name, status, "start", "开机"));
        stop.setOnClickListener(v -> confirmAction(id, name, status, "stop", "关机"));
        reboot.setOnClickListener(v -> confirmAction(id, name, status, "reboot", "重启"));
        actions.addView(start, weighted());
        actions.addView(actionDivider());
        actions.addView(stop, weighted());
        actions.addView(actionDivider());
        actions.addView(reboot, weighted());
        return actions;
    }

    private void confirmAction(int serverId, String name, String status, String action, String label) {
        if (actionInProgress) {
            toast("已有电源操作正在执行，请稍候");
            return;
        }
        int accent = GREEN;
        int accentSoft = GREEN_SOFT;
        String title = "确认开机";
        String description = "开机后状态同步通常需要几秒钟，请稍候刷新。";
        if ("stop".equals(action)) {
            accent = RED;
            accentSoft = RED_SOFT;
            title = "确认关机";
            description = "关机会立即中断当前服务连接，请确认没有正在进行的重要任务。";
        } else if ("reboot".equals(action)) {
            accent = AMBER;
            accentSoft = AMBER_SOFT;
            title = "确认重启";
            description = "服务器会短暂离线，恢复时间取决于系统启动速度。";
        }
        final int actionColor = accent;
        final int actionSoft = accentSoft;
        Dialog dialog = bottomDialog();
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text(title, 21, INK, true));
        TextView desc = text(description, 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(16));
        sheet.addView(desc);
        LinearLayout serverBox = roundedBox(SOFT, 16, BORDER, 1);
        serverBox.setPadding(dp(14), dp(8), dp(14), dp(8));
        serverBox.addView(infoRow("服务器", name));
        serverBox.addView(thinDivider());
        LinearLayout statusRow = horizontalRow();
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView statusKey = text("当前状态", 12, MUTED, false);
        statusKey.setPadding(0, dp(10), 0, dp(10));
        statusRow.addView(statusKey, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        statusRow.addView(pill(statusLabel(status), statusColor(status), statusBackground(status)));
        serverBox.addView(statusRow, matchWrap());
        sheet.addView(serverBox, matchWrap());
        if ("stop".equals(action) || "reboot".equals(action)) {
            gap(sheet, 12);
            LinearLayout caution = roundedBox(actionSoft, 14, 0, 0);
            caution.setPadding(dp(13), dp(11), dp(13), dp(11));
            caution.addView(text("stop".equals(action) ? "关机后，当前 SSH / 网络会话将立即断开。" : "重启过程中服务会暂时不可用，请等待状态恢复。", 12, actionColor, false));
            sheet.addView(caution, matchWrap());
        }
        gap(sheet, 20);
        LinearLayout buttons = horizontalRow();
        Button cancel = sheetButton("取消", INK, SOFT, BORDER);
        Button confirm = sheetButton(label, Color.WHITE, actionColor, 0);
        buttons.addView(cancel, weighted());
        gapH(buttons, 10);
        buttons.addView(confirm, weighted());
        sheet.addView(buttons, matchWrap());
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            runAction(serverId, action, label);
        });
        showBottomDialog(dialog, sheet);
    }

    private void runAction(int serverId, String action, String label) {
        if (actionInProgress) {
            toast("已有电源操作正在执行，请稍候");
            return;
        }
        actionInProgress = true;
        toast("正在执行" + label + "…");
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("action", action);
                ApiClient.request(baseUrl, "/api/v1/servers/" + serverId + "/action", "POST", token, body);
                main.post(() -> {
                    actionInProgress = false;
                    toast(label + "指令已提交");
                    main.postDelayed(() -> {
                        if (inDetail && currentDetailServerId == serverId) showServerDetail(serverId);
                        else reloadCurrentTab();
                    }, 900);
                });
            } catch (Exception e) {
                main.post(() -> {
                    actionInProgress = false;
                    if (!handleUnauthorized(e)) toast(message(e));
                });
            }
        });
    }

    private LinearLayout orderCard(JSONObject o) {
        LinearLayout c = surfaceCard(16);
        c.setPadding(dp(15), dp(13), dp(15), dp(13));
        LinearLayout top = horizontalRow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        String plan = o.optString("plan_name", "");
        if (plan.isEmpty() || "null".equals(plan)) plan = "订单 #" + o.optInt("id");
        top.addView(text(plan, 14, INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(text("¥" + money(o.optLong("amount_cents", 0)), 14, INK, true));
        c.addView(top, matchWrap());
        gap(c, 7);
        LinearLayout meta = horizontalRow();
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(pill(orderStatusLabel(o.optString("status")), orderStatusColor(o.optString("status")), orderStatusBg(o.optString("status"))));
        gapH(meta, 8);
        meta.addView(text(orderKindLabel(o.optString("kind")) + " · " + cleanDate(o.optString("created_at")), 11, MUTED, false));
        c.addView(meta, matchWrap());
        return c;
    }

    private LinearLayout ledgerCard(JSONObject row) {
        LinearLayout c = surfaceCard(16);
        c.setPadding(dp(15), dp(13), dp(15), dp(13));
        long delta = row.optLong("delta_cents", 0);
        LinearLayout top = horizontalRow();
        String note = row.optString("note", "");
        if (note.isEmpty() || "null".equals(note)) note = ledgerKindLabel(row.optString("kind"));
        top.addView(text(note, 13, INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(text((delta >= 0 ? "+¥" : "-¥") + money(Math.abs(delta)), 14, delta >= 0 ? GREEN : RED, true));
        c.addView(top, matchWrap());
        TextView sub = text(cleanDate(row.optString("created_at")) + " · 余额 ¥" + money(row.optLong("balance_after_cents", 0)), 11, MUTED, false);
        sub.setPadding(0, dp(6), 0, 0);
        c.addView(sub);
        return c;
    }

    private LinearLayout rechargeCard(JSONObject r) {
        LinearLayout c = surfaceCard(16);
        c.setPadding(dp(15), dp(13), dp(15), dp(13));
        LinearLayout top = horizontalRow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        String chain = r.optString("chain", "").toUpperCase();
        top.addView(text(chain + " 充值", 14, INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(pill(rechargeStatusLabel(r.optString("status")), rechargeStatusColor(r.optString("status")), rechargeStatusBg(r.optString("status"))));
        c.addView(top, matchWrap());
        TextView amount = text("¥" + money(r.optLong("requested_cny_cents", 0)) + " · " + usdt(r.optLong("expected_usdt_units", 0)) + " USDT", 12, INK, false);
        amount.setPadding(0, dp(7), 0, dp(4));
        c.addView(amount);
        c.addView(text(cleanDate(r.optString("created_at")), 11, MUTED, false));
        return c;
    }

    private void confirmLogoutSheet() {
        Dialog dialog = bottomDialog();
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("退出登录", 21, INK, true));
        TextView desc = text("退出后，本机保存的登录令牌会立即清除。", 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(18));
        sheet.addView(desc);
        LinearLayout buttons = horizontalRow();
        Button cancel = sheetButton("取消", INK, SOFT, BORDER);
        Button confirm = sheetButton("退出", Color.WHITE, RED, 0);
        buttons.addView(cancel, weighted());
        gapH(buttons, 10);
        buttons.addView(confirm, weighted());
        sheet.addView(buttons, matchWrap());
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            logout();
        });
        showBottomDialog(dialog, sheet);
    }

    private void logout() {
        String oldBaseUrl = baseUrl;
        String oldToken = token;
        clearLogin();
        showLogin();
        io.execute(() -> {
            try {
                ApiClient.request(oldBaseUrl, "/api/v1/auth/logout", "POST", oldToken, new JSONObject());
            } catch (Exception ignored) {
            }
        });
    }

    private void clearLogin() {
        token = "";
        SecureTokenStore.clear(prefs);
        clearUiCache();
    }

    private void clearUiCache() {
        cachedHome = null;
        cachedServices = null;
        cachedBilling = null;
        cachedSupport = null;
        cachedMe = null;
        cachedCatalog = null;
        cachedServerDetails.clear();
        cachedTicketDetails.clear();
    }

    private boolean hasCacheForTab(int tab) {
        if (tab == TAB_HOME) return cachedHome != null;
        if (tab == TAB_SERVICES) return cachedServices != null;
        if (tab == TAB_BILLING) return cachedBilling != null;
        if (tab == TAB_SUPPORT) return cachedSupport != null;
        if (tab == TAB_ME) return cachedMe != null;
        return false;
    }

    private boolean validScreen(int gen, int tab) {
        return !inDetail && gen == screenGeneration && currentTab == tab;
    }

    private void handlePageError(int gen, int tab, SwipeRefreshLayout swipe, LinearLayout page, Exception e, Runnable retry) {
        if (!validScreen(gen, tab)) return;
        swipe.setRefreshing(false);
        if (handleUnauthorized(e)) return;
        String detail = message(e);
        if (tab == TAB_BILLING && e instanceof ApiClient.ApiException && ((ApiClient.ApiException) e).status == 404) {
            detail = "当前 Panel 的 Mobile API v1 尚未提供账务接口。请先更新 Panel 后再刷新。";
        }
        if (tab == TAB_SUPPORT && e instanceof ApiClient.ApiException && ((ApiClient.ApiException) e).status == 404) {
            detail = "当前 Panel 的 Mobile API v1 尚未提供工单接口。请先更新 Panel 后再刷新。";
        }
        // If the screen already has good cached data, a refresh failure should not
        // destroy the whole page and replace it with an error card. Keep the UI in
        // place and surface a lightweight message instead.
        if (hasCacheForTab(tab)) {
            toast("刷新失败：" + detail);
            return;
        }
        page.removeViews(1, page.getChildCount() - 1);
        gap(page, 18);
        page.addView(errorCard(detail, retry), matchWrap());
    }

    private boolean handleUnauthorized(Exception e) {
        if (e instanceof ApiClient.ApiException && ((ApiClient.ApiException) e).status == 401) {
            clearLogin();
            toast("登录已失效，请重新登录");
            showLogin();
            return true;
        }
        return false;
    }

    private SwipeRefreshLayout swipeContainer() {
        SwipeRefreshLayout swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(BLUE);
        swipe.setProgressBackgroundColorSchemeColor(CARD);
        swipe.setDistanceToTriggerSync(dp(58));
        swipe.setSlingshotDistance(dp(64));
        swipe.setProgressViewOffset(false, dp(2), dp(54));
        return swipe;
    }

    private LinearLayout pageColumn() {
        LinearLayout page = column();
        page.setPadding(dp(18), dp(14), dp(18), dp(30));
        return page;
    }

    private LinearLayout topHeader(String title, String subtitle, String action, Runnable onAction) {
        LinearLayout top = horizontalRow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout block = column();
        block.addView(text(title, 27, INK, true));
        TextView sub = text(subtitle, 11, MUTED, false);
        sub.setPadding(0, dp(1), 0, 0);
        block.addView(sub);
        top.addView(block, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button button = compactButton(action);
        button.setOnClickListener(v -> onAction.run());
        top.addView(button);
        return top;
    }

    private LinearLayout sectionHeader(String title, String subtitle, String trailing) {
        LinearLayout row = horizontalRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout block = column();
        block.addView(text(title, 20, INK, true));
        if (!subtitle.isEmpty()) {
            TextView sub = text(subtitle, 11, MUTED, false);
            sub.setPadding(0, dp(2), 0, 0);
            block.addView(sub);
        }
        row.addView(block, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!trailing.isEmpty()) row.addView(text(trailing, 11, MUTED, true));
        return row;
    }

    private LinearLayout statCard(String label, int value, int accent) {
        LinearLayout c = roundedBox(CARD, 18, BORDER, 1);
        c.setPadding(dp(15), dp(14), dp(15), dp(14));
        c.setMinimumHeight(dp(88));
        LinearLayout head = horizontalRow();
        head.setGravity(Gravity.CENTER_VERTICAL);
        View marker = new View(this);
        marker.setBackground(roundRect(accent, dp(99), 0, 0));
        head.addView(marker, new LinearLayout.LayoutParams(dp(8), dp(8)));
        gapH(head, 7);
        head.addView(text(label, 12, MUTED, false));
        c.addView(head, matchWrap());
        TextView number = text(String.valueOf(value), 25, INK, true);
        number.setPadding(0, dp(8), 0, 0);
        c.addView(number);
        return c;
    }

    private LinearLayout infoRow(String key, String value) {
        LinearLayout row = horizontalRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView k = text(key, 12, MUTED, true);
        k.setPadding(0, dp(10), 0, dp(10));
        row.addView(k, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView v = text(value, 12, INK, false);
        v.setGravity(Gravity.RIGHT);
        row.addView(v);
        return row;
    }

    private LinearLayout miniMetric(String key, String value) {
        LinearLayout b = column();
        b.addView(text(key, 10, Color.rgb(181, 197, 220), false));
        TextView val = text(value, 13, Color.WHITE, true);
        val.setPadding(0, dp(3), 0, 0);
        b.addView(val);
        return b;
    }

    private LinearLayout profileMetric(String key, String value) {
        LinearLayout b = column();
        b.addView(text(key, 11, MUTED, false));
        TextView val = text(value, 17, INK, true);
        val.setPadding(0, dp(3), 0, 0);
        b.addView(val);
        return b;
    }

    private LinearLayout emptyCard(String title, String subtitle) {
        LinearLayout c = roundedBox(SOFT, 18, BORDER, 1);
        c.setPadding(dp(18), dp(22), dp(18), dp(22));
        TextView t = text(title, 15, INK, true);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(t);
        TextView s = text(subtitle, 12, MUTED, false);
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        s.setPadding(0, dp(5), 0, 0);
        c.addView(s);
        return c;
    }

    private LinearLayout errorCard(String detail, Runnable retry) {
        LinearLayout c = roundedBox(RED_SOFT, 18, 0, 0);
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        c.addView(text("加载失败", 16, RED, true));
        TextView d = text(detail, 12, MUTED, false);
        d.setPadding(0, dp(6), 0, dp(13));
        c.addView(d);
        Button b = outlineButton("重新加载", BLUE, BLUE_SOFT);
        b.setOnClickListener(v -> retry.run());
        c.addView(b, matchWrap());
        return c;
    }

    private void showInlineLoading(LinearLayout page, String label) {
        LinearLayout box = roundedBox(SOFT, 18, 0, 0);
        box.setPadding(dp(18), dp(22), dp(18), dp(22));
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        ProgressBar progress = new ProgressBar(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) progress.setIndeterminateTintList(ColorStateList.valueOf(BLUE));
        box.addView(progress, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView txt = text(label, 12, MUTED, false);
        txt.setPadding(0, dp(9), 0, 0);
        box.addView(txt);
        page.addView(box, matchWrap());
    }

    private LinearLayout surfaceCard(int radius) {
        return roundedBox(CARD, radius, BORDER, 1);
    }

    private LinearLayout flatAccentCard(int color, int radius) {
        return roundedBox(color, radius, 0, 0);
    }

    private View thinDivider() {
        View v = new View(this);
        v.setBackgroundColor(BORDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private Dialog bottomDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private LinearLayout bottomSheetBase() {
        LinearLayout sheet = column();
        sheet.setBackground(topRoundRect(CARD, 28));
        sheet.setPadding(dp(20), dp(10), dp(20), dp(24));
        View handle = new View(this);
        handle.setBackground(roundRect(BORDER, dp(99), 0, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, lp);
        gap(sheet, 18);
        return sheet;
    }

    private void showBottomDialog(Dialog dialog, LinearLayout sheet) {
        dialog.setContentView(sheet);
        dialog.show();
        configureBottomDialogWindow(dialog);
    }

    private void configureBottomDialogWindow(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(darkModeActive ? 0.58f : 0.42f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setWindowAnimations(com.xnat.mobile.R.style.XnatBottomSheetAnimation);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(true);
        }
    }

    private void swapBottomDialogContent(Dialog dialog, LinearLayout nextSheet, boolean forward) {
        if (dialog == null || !dialog.isShowing()) {
            showBottomDialog(dialog == null ? bottomDialog() : dialog, nextSheet);
            return;
        }
        Window window = dialog.getWindow();
        View oldSheet = null;
        if (window != null) {
            View content = window.findViewById(android.R.id.content);
            if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
                oldSheet = ((ViewGroup) content).getChildAt(0);
            }
        }
        final View outgoing = oldSheet;
        Runnable install = () -> {
            dialog.setContentView(nextSheet);
            configureBottomDialogWindow(dialog);
            nextSheet.setAlpha(0.94f);
            nextSheet.setTranslationX(dp(forward ? 10 : -10));
            nextSheet.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(180L)
                    .setInterpolator(motionEnter)
                    .start();
        };
        if (outgoing == null) {
            install.run();
            return;
        }
        outgoing.animate().cancel();
        outgoing.animate()
                .alpha(0.90f)
                .translationX(dp(forward ? -8 : 8))
                .setDuration(90L)
                .setInterpolator(motionStandard)
                .withEndAction(install)
                .start();
    }

    private TextView pill(String label, int color, int background) {
        TextView badge = text(label, 11, color, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(roundRect(background, dp(999), 0, 0));
        return badge;
    }

    private int statusBackground(String s) {
        if ("running".equals(s)) return GREEN_SOFT;
        if ("stopped".equals(s)) return SOFT;
        if ("provisioning".equals(s) || "reinstalling".equals(s)) return BLUE_SOFT;
        return RED_SOFT;
    }

    private RippleDrawable rippleRoundRect(int baseColor, int radiusDp, int strokeColor, int rippleColor) {
        GradientDrawable content = roundRect(baseColor, dp(radiusDp), strokeColor, strokeColor == 0 ? 0 : 1);
        GradientDrawable mask = roundRect(Color.WHITE, dp(radiusDp), 0, 0);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private GradientDrawable gradientRoundRect(int startColor, int endColor, int radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{startColor, endColor});
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable roundRect(int color, float radiusPx, int strokeColor, int strokeWidthDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        if (strokeWidthDp > 0) d.setStroke(dp(strokeWidthDp), strokeColor);
        return d;
    }

    private GradientDrawable topRoundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        float r = dp(radiusDp);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return d;
    }

    private LinearLayout roundedBox(int background, int radius, int strokeColor, int strokeWidthDp) {
        LinearLayout v = column();
        v.setBackground(roundRect(background, dp(radius), strokeColor, strokeWidthDp));
        return v;
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout horizontalRow() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
    }

    private TextView fieldLabel(String value) {
        TextView label = text(value, 12, INK, true);
        label.setAlpha(0.90f);
        return label;
    }

    private EditText loginInput(String hint, int inputType) {
        EditText e = loginBareInput(hint, inputType);
        e.setPadding(dp(16), 0, dp(16), 0);
        e.setBackground(roundRect(SOFT, dp(18), BORDER, 1));
        return e;
    }

    private EditText loginBareInput(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setIncludeFontPadding(false);
        e.setPadding(dp(16), 0, dp(6), 0);
        e.setMinHeight(dp(56));
        e.setBackgroundColor(Color.TRANSPARENT);
        return e;
    }

    private void togglePasswordVisibility() {
        if (passwordInput == null) return;
        int cursor = Math.max(0, passwordInput.getSelectionStart());
        passwordVisible = !passwordVisible;
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT |
                (passwordVisible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        passwordInput.setSelection(Math.min(cursor, passwordInput.length()));
        if (passwordToggle != null) passwordToggle.setText(passwordVisible ? "隐藏" : "显示");
    }

    private boolean isDebugBuild() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private void showAdvancedServerSettings() {
        Dialog dialog = bottomDialog();
        LinearLayout sheet = bottomSheetBase();
        sheet.addView(text("高级连接设置", 21, INK, true));
        TextView desc = text("仅用于维护或测试。普通登录无需配置服务器地址。", 12, MUTED, false);
        desc.setPadding(0, dp(5), 0, dp(16));
        sheet.addView(desc);

        EditText serverInput = input("https://panel.example.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverInput.setText(baseUrl.isEmpty() ? DEFAULT_BASE_URL : baseUrl);
        serverInput.setSelectAllOnFocus(false);
        serverInput.setSelection(serverInput.length());
        sheet.addView(serverInput, matchWrap());
        gap(sheet, 10);

        Button restore = outlineButton("恢复内置地址", BLUE, BLUE_SOFT);
        restore.setOnClickListener(v -> {
            serverInput.setText(DEFAULT_BASE_URL);
            serverInput.setSelection(serverInput.length());
        });
        sheet.addView(restore, matchWrap());
        gap(sheet, 10);

        LinearLayout actions = horizontalRow();
        Button cancel = sheetButton("取消", INK, SOFT, BORDER);
        Button save = sheetButton("保存", Color.WHITE, BLUE, 0);
        actions.addView(cancel, weighted());
        gapH(actions, 10);
        actions.addView(save, weighted());
        sheet.addView(actions, matchWrap());
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String value = ApiClient.normalizeBase(serverInput.getText().toString());
            if (!ApiClient.isValidBaseUrl(value)) {
                toast("请输入有效的服务器地址");
                return;
            }
            if (!isDebugBuild() && !value.startsWith("https://")) {
                toast("正式版仅支持 HTTPS");
                return;
            }
            boolean changed = !value.equals(baseUrl);
            baseUrl = value;
            insecureHttpAcknowledged = false;
            prefs.edit().putString("base_url", baseUrl).apply();
            dialog.dismiss();
            if (changed) {
                boolean wasLoggedIn = !token.isEmpty();
                if (wasLoggedIn) clearLogin();
                showLogin();
                toast(wasLoggedIn ? "连接地址已更新，请重新登录" : "连接地址已更新");
            } else {
                toast("连接地址已保存");
            }
        });
        showBottomDialog(dialog, sheet);
    }

    private String profileInitial(String value) {
        if (value == null || value.trim().isEmpty()) return "X";
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(1, trimmed.length())).toUpperCase();
    }

    private EditText input(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setMinHeight(dp(50));
        e.setBackground(roundRect(SOFT, dp(13), BORDER, 1));
        return e;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(52));
        b.setBackground(rippleRoundRect(BLUE, 14, 0, Color.argb(42, 255, 255, 255)));
        b.setStateListAnimator(null);
        return b;
    }

    private Button compactButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(INK);
        b.setTextSize(12);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(13), dp(8), dp(13), dp(8));
        b.setBackground(rippleRoundRect(CARD, 12, BORDER, RIPPLE));
        b.setStateListAnimator(null);
        return b;
    }

    private Button outlineButton(String label, int color, int background) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(46));
        b.setBackground(rippleRoundRect(background, 12, 0, RIPPLE));
        b.setStateListAnimator(null);
        return b;
    }

    private Button groupedActionButton(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(46));
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(8), dp(7), dp(8), dp(7));
        b.setBackground(rippleRoundRect(Color.TRANSPARENT, 10, 0, RIPPLE));
        b.setStateListAnimator(null);
        return b;
    }

    private Button sheetButton(String label, int textColor, int background, int strokeColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(50));
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setBackground(rippleRoundRect(background, 14, strokeColor, RIPPLE));
        b.setStateListAnimator(null);
        return b;
    }

    private View actionDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(24));
        lp.gravity = Gravity.CENTER_VERTICAL;
        divider.setLayoutParams(lp);
        return divider;
    }

    private void setActionEnabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.34f);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void applySystemBarInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(darkModeActive ? 0 : mask, mask);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            if (darkModeActive) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private void applyThemePalette() {
        themeMode = prefs == null ? THEME_SYSTEM : prefs.getString("theme_mode", THEME_SYSTEM);
        int nightMask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean systemDark = nightMask == Configuration.UI_MODE_NIGHT_YES;
        darkModeActive = THEME_DARK.equals(themeMode) || (THEME_SYSTEM.equals(themeMode) && systemDark);

        if (darkModeActive) {
            BG = Color.rgb(5, 15, 29);
            CARD = Color.rgb(10, 27, 49);
            INK = Color.rgb(244, 248, 255);
            MUTED = Color.rgb(148, 167, 194);
            BORDER = Color.rgb(29, 49, 76);
            SOFT = Color.rgb(14, 35, 58);
            SOFT_BLUE = Color.rgb(10, 39, 75);
            BLUE = Color.rgb(64, 142, 255);
            BLUE_SOFT = Color.rgb(14, 48, 90);
            GREEN = Color.rgb(52, 211, 153);
            GREEN_SOFT = Color.rgb(8, 48, 42);
            RED = Color.rgb(248, 113, 113);
            RED_SOFT = Color.rgb(65, 25, 32);
            AMBER = Color.rgb(251, 146, 60);
            AMBER_SOFT = Color.rgb(62, 39, 17);
            NAVY = Color.rgb(0, 19, 49);
            NAVY_2 = Color.rgb(7, 70, 150);
            RIPPLE = Color.argb(48, 118, 166, 230);
        } else {
            BG = Color.rgb(246, 249, 253);
            CARD = Color.WHITE;
            INK = Color.rgb(9, 27, 52);
            MUTED = Color.rgb(96, 116, 143);
            BORDER = Color.rgb(226, 233, 242);
            SOFT = Color.rgb(248, 250, 253);
            SOFT_BLUE = Color.rgb(241, 246, 255);
            BLUE = Color.rgb(22, 111, 255);
            BLUE_SOFT = Color.rgb(235, 244, 255);
            GREEN = Color.rgb(22, 163, 74);
            GREEN_SOFT = Color.rgb(237, 252, 244);
            RED = Color.rgb(220, 38, 38);
            RED_SOFT = Color.rgb(254, 241, 242);
            AMBER = Color.rgb(217, 119, 6);
            AMBER_SOFT = Color.rgb(255, 248, 235);
            NAVY = Color.rgb(0, 27, 66);
            NAVY_2 = Color.rgb(13, 73, 154);
            RIPPLE = Color.rgb(226, 235, 247);
        }
    }

    private String themeModeLabel() {
        if (THEME_LIGHT.equals(themeMode)) return "浅色";
        if (THEME_DARK.equals(themeMode)) return "深色";
        return "跟随系统";
    }

    private Button compactSegmentButton(String label, boolean selected) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinHeight(dp(44));
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setStateListAnimator(null);
        styleCompactSegment(b, selected);
        return b;
    }

    private void styleCompactSegment(Button b, boolean selected) {
        b.setTextColor(selected ? (darkModeActive ? Color.WHITE : BLUE) : MUTED);
        b.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        int bg = selected ? (darkModeActive ? Color.rgb(20, 61, 112) : CARD) : Color.TRANSPARENT;
        b.setBackground(rippleRoundRect(bg, 10, 0, RIPPLE));
    }

    private View themeSelector() {
        return new ThemeSegmentedControl();
    }

    private int themeModeIndex(String mode) {
        if (THEME_LIGHT.equals(mode)) return 1;
        if (THEME_DARK.equals(mode)) return 2;
        return 0;
    }

    private String themeModeForIndex(int index) {
        if (index == 1) return THEME_LIGHT;
        if (index == 2) return THEME_DARK;
        return THEME_SYSTEM;
    }

    private final class ThemeSegmentedControl extends FrameLayout {
        private final TextView[] labels = new TextView[3];
        private final View indicator;
        private int selectedIndex;
        private int segmentWidth;

        ThemeSegmentedControl() {
            super(MainActivity.this);
            selectedIndex = themeModeIndex(themeMode);
            setMinimumHeight(dp(50));
            setPadding(dp(3), dp(3), dp(3), dp(3));
            setClipChildren(false);
            setClipToPadding(false);
            setBackground(roundRect(SOFT, dp(15), BORDER, 1));

            indicator = new View(MainActivity.this);
            indicator.setBackground(roundRect(darkModeActive ? Color.rgb(20, 61, 112) : CARD, dp(12), 0, 0));
            indicator.setElevation(0f);
            addView(indicator, new FrameLayout.LayoutParams(0, dp(44)));

            LinearLayout row = horizontalRow();
            row.setGravity(Gravity.CENTER_VERTICAL);
            String[] names = new String[]{"跟随系统", "浅色", "深色"};
            for (int i = 0; i < names.length; i++) {
                final int index = i;
                boolean selected = index == selectedIndex;
                TextView label = text(names[i], 12,
                        selected ? (darkModeActive ? Color.WHITE : BLUE) : MUTED, selected);
                label.setGravity(Gravity.CENTER);
                label.setClickable(true);
                label.setFocusable(true);
                label.setAlpha(selected ? 1f : 0.82f);
                label.setBackground(rippleRoundRect(Color.TRANSPARENT, 12, 0, RIPPLE));
                label.setContentDescription(names[i] + (selected ? "，已选择" : ""));
                label.setOnClickListener(v -> select(index));
                labels[i] = label;
                row.addView(label, weighted());
            }
            FrameLayout.LayoutParams rowLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            rowLp.gravity = Gravity.CENTER_VERTICAL;
            addView(row, rowLp);
            post(() -> positionIndicator(false));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            int usable = Math.max(0, w - getPaddingLeft() - getPaddingRight());
            segmentWidth = usable / 3;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) indicator.getLayoutParams();
            lp.width = segmentWidth;
            lp.height = Math.max(dp(40), h - getPaddingTop() - getPaddingBottom());
            lp.gravity = Gravity.START | Gravity.TOP;
            indicator.setLayoutParams(lp);
            positionIndicator(false);
        }

        private void positionIndicator(boolean animated) {
            if (segmentWidth <= 0) return;
            float target = selectedIndex * segmentWidth;
            indicator.animate().cancel();
            if (!animated) {
                indicator.setTranslationX(target);
                return;
            }
            indicator.animate()
                    .translationX(target)
                    .setDuration(270L)
                    .setInterpolator(new OvershootInterpolator(0.42f))
                    .start();
        }

        private void select(int index) {
            if (index < 0 || index > 2 || index == selectedIndex || themeTransitionInProgress) return;
            subtleHaptic(this);
            selectedIndex = index;
            positionIndicator(true);
            animateLabels();

            final int transition = ++themeTransitionGeneration;
            final String targetMode = themeModeForIndex(index);
            // Let the capsule travel most of the way first. The palette cross-fade
            // then overlaps its final settling, which feels springy without delaying input.
            postDelayed(() -> {
                if (transition == themeTransitionGeneration) applyThemeMode(targetMode);
            }, 155L);
        }

        private void animateLabels() {
            for (int i = 0; i < labels.length; i++) {
                TextView label = labels[i];
                boolean selected = i == selectedIndex;
                int startColor = label.getCurrentTextColor();
                int endColor = selected ? (darkModeActive ? Color.WHITE : BLUE) : MUTED;
                ValueAnimator color = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, endColor);
                color.setDuration(170L);
                color.setInterpolator(motionStandard);
                color.addUpdateListener(a -> label.setTextColor((Integer) a.getAnimatedValue()));
                color.start();
                label.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
                label.animate().cancel();
                label.animate().alpha(selected ? 1f : 0.82f)
                        .setDuration(160L).setInterpolator(motionStandard).start();
                label.setContentDescription(label.getText() + (selected ? "，已选择" : ""));
            }
        }
    }

    private void applyThemeMode(String mode) {
        if (!THEME_SYSTEM.equals(mode) && !THEME_LIGHT.equals(mode) && !THEME_DARK.equals(mode)) return;
        if (mode.equals(themeMode) || themeTransitionInProgress) return;
        themeTransitionInProgress = true;

        Bitmap snapshot = captureRootSnapshot();
        int oldBackground = BG;
        prefs.edit().putString("theme_mode", mode).apply();
        applyThemePalette();
        int newBackground = BG;
        root.setBackgroundColor(BG);

        // The theme selector lives on the Me page. Repaint from the cached account
        // payload without issuing another /api/v1/me request just because colors changed.
        suppressNextMeNetworkRefresh = true;
        if (!token.isEmpty()) showApp(TAB_ME); else showLogin();
        applySystemBarInsets();

        animateSystemBarColors(oldBackground, newBackground);
        crossFadeThemeSnapshot(snapshot);
    }

    private Bitmap captureRootSnapshot() {
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            root.draw(canvas);
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void crossFadeThemeSnapshot(Bitmap snapshot) {
        if (snapshot == null || root == null || !(root.getParent() instanceof ViewGroup)) {
            themeTransitionInProgress = false;
            return;
        }
        ViewGroup parent = (ViewGroup) root.getParent();
        ImageView overlay = new ImageView(this);
        overlay.setImageBitmap(snapshot);
        overlay.setScaleType(ImageView.ScaleType.FIT_XY);
        // Block taps for the ~225 ms cross-fade so a touch cannot land on a control
        // that has just moved under the user between the two theme frames.
        overlay.setClickable(true);
        overlay.setFocusable(false);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        parent.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.bringToFront();

        root.setAlpha(0.94f);
        root.animate().cancel();
        root.animate().alpha(1f).setDuration(220L).setInterpolator(motionStandard).start();
        overlay.animate().alpha(0f).setDuration(225L).setInterpolator(motionStandard)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        try { parent.removeView(overlay); } catch (Exception ignored) {}
                        try { snapshot.recycle(); } catch (Exception ignored) {}
                        themeTransitionInProgress = false;
                    }
                }).start();
    }

    private void animateSystemBarColors(int from, int to) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        // applySystemBarInsets has already set icon brightness for the target theme.
        // Put the bar colors back at the old value and interpolate them to the target.
        getWindow().setStatusBarColor(from);
        getWindow().setNavigationBarColor(from);
        ValueAnimator bars = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        bars.setDuration(225L);
        bars.setInterpolator(motionStandard);
        bars.addUpdateListener(animation -> {
            int color = (Integer) animation.getAnimatedValue();
            getWindow().setStatusBarColor(color);
            getWindow().setNavigationBarColor(color);
        });
        bars.start();
    }

    private int beginStableRender(LinearLayout page) {
        int scrollY = 0;
        if (page != null && page.getParent() instanceof ScrollView) {
            scrollY = ((ScrollView) page.getParent()).getScrollY();
        }
        if (page != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) page.suppressLayout(true);
        return scrollY;
    }

    private void endStableRender(LinearLayout page, int scrollY) {
        if (page == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) page.suppressLayout(false);
        if (page.getParent() instanceof ScrollView) {
            ScrollView scroll = (ScrollView) page.getParent();
            int keep = Math.max(0, scrollY);
            if (keep > 0) scroll.post(() -> scroll.scrollTo(0, keep));
        }
    }

    private void animateLoadedContent(View view) {
        // Only first-load content gets a subtle stagger. Background refreshes never
        // animate the entire page, which keeps scrolling and controls completely still.
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        int animated = 0;
        for (int i = 1; i < group.getChildCount() && animated < 8; i++) {
            View child = group.getChildAt(i);
            if (child instanceof Space) continue;
            child.animate().cancel();
            child.setAlpha(0.72f);
            child.setTranslationY(dp(6));
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(animated * 24L)
                    .setDuration(190L)
                    .setInterpolator(motionEnter)
                    .start();
            animated++;
        }
    }

    private void animateContentIn(int direction) {
        // A tiny horizontal cue communicates navigation direction without moving the
        // page enough to read as a shake. No full-screen fade from zero is used.
        if (contentHost == null) return;
        contentHost.animate().cancel();
        float offset = direction == 0 ? 0f : dp(7) * (direction > 0 ? 1f : -1f);
        contentHost.setTranslationX(offset);
        contentHost.setTranslationY(0f);
        contentHost.setAlpha(0.965f);
        contentHost.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(175L)
                .setInterpolator(motionEnter)
                .start();
    }

    private void subtleHaptic(View view) {
        if (view == null) return;
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Exception ignored) {
        }
    }

    private final class SystemIconView extends View {
        private final String identity;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SystemIconView(String identity) {
            super(MainActivity.this);
            this.identity = identity == null ? "" : identity.trim().toLowerCase();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            int accent;
            if (identity.contains("ubuntu")) accent = Color.rgb(233, 84, 32);
            else if (identity.contains("debian")) accent = Color.rgb(215, 10, 83);
            else if (identity.contains("centos")) accent = Color.rgb(147, 75, 176);
            else if (identity.contains("rocky")) accent = Color.rgb(16, 142, 97);
            else if (identity.contains("alma")) accent = Color.rgb(0, 149, 166);
            else if (identity.contains("arch")) accent = Color.rgb(23, 147, 209);
            else accent = BLUE;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(darkModeActive ? 54 : 22, Color.red(accent), Color.green(accent), Color.blue(accent)));
            canvas.drawRoundRect(new RectF(dp(1), dp(1), w - dp(1), h - dp(1)), dp(12), dp(12), paint);

            paint.setColor(accent);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            if (identity.contains("ubuntu")) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2.2f));
                canvas.drawCircle(cx, cy, dp(7.0f), paint);
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 3; i++) {
                    double a = Math.toRadians(-90 + i * 120);
                    float px = cx + (float) Math.cos(a) * dp(8.4f);
                    float py = cy + (float) Math.sin(a) * dp(8.4f);
                    canvas.drawCircle(px, py, dp(2.25f), paint);
                    paint.setColor(darkModeActive ? CARD : Color.WHITE);
                    canvas.drawCircle(px, py, dp(0.85f), paint);
                    paint.setColor(accent);
                }
            } else if (identity.contains("debian")) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2.1f));
                RectF outer = new RectF(cx - dp(8.5f), cy - dp(8.2f), cx + dp(8.5f), cy + dp(8.2f));
                canvas.drawArc(outer, 205, 290, false, paint);
                paint.setStrokeWidth(dp(1.8f));
                RectF inner = new RectF(cx - dp(5.2f), cy - dp(5.0f), cx + dp(5.2f), cy + dp(5.0f));
                canvas.drawArc(inner, 250, 235, false, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx + dp(2.0f), cy - dp(0.6f), dp(1.0f), paint);
            } else if (identity.contains("arch")) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2.2f));
                canvas.drawLine(cx - dp(7), cy + dp(7), cx, cy - dp(8), paint);
                canvas.drawLine(cx, cy - dp(8), cx + dp(7), cy + dp(7), paint);
                canvas.drawLine(cx - dp(3.6f), cy + dp(1.0f), cx + dp(3.6f), cy + dp(1.0f), paint);
            } else {
                String letter = "L";
                if (identity.contains("centos")) letter = "C";
                else if (identity.contains("rocky")) letter = "R";
                else if (identity.contains("alma")) letter = "A";
                else if (!identity.isEmpty()) letter = identity.substring(0, 1).toUpperCase();
                paint.setStyle(Paint.Style.FILL);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(dp(15));
                Paint.FontMetrics fm = paint.getFontMetrics();
                canvas.drawText(letter, cx, cy - (fm.ascent + fm.descent) / 2f, paint);
            }
        }
    }

    private final class NavIconView extends View {
        private final int type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;

        NavIconView(int type) {
            super(MainActivity.this);
            this.type = type;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setActive(boolean value) {
            if (active == value) return;
            active = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float sw = dp(active ? 2 : 1.7f);
            paint.setStrokeWidth(sw);
            paint.setColor(active ? BLUE : MUTED);
            paint.setStyle(Paint.Style.STROKE);

            if (type == TAB_HOME) {
                canvas.drawLine(cx - dp(7), cy, cx, cy - dp(6), paint);
                canvas.drawLine(cx, cy - dp(6), cx + dp(7), cy, paint);
                canvas.drawRoundRect(new RectF(cx - dp(5.5f), cy - dp(1), cx + dp(5.5f), cy + dp(7)), dp(2), dp(2), paint);
            } else if (type == TAB_SERVICES) {
                canvas.drawRoundRect(new RectF(cx - dp(7), cy - dp(7), cx + dp(7), cy - dp(1)), dp(2), dp(2), paint);
                canvas.drawRoundRect(new RectF(cx - dp(7), cy + dp(1.5f), cx + dp(7), cy + dp(7.5f)), dp(2), dp(2), paint);
                canvas.drawCircle(cx + dp(4.5f), cy - dp(4), dp(0.9f), paint);
                canvas.drawCircle(cx + dp(4.5f), cy + dp(4.5f), dp(0.9f), paint);
            } else if (type == TAB_BILLING) {
                canvas.drawRoundRect(new RectF(cx - dp(7.5f), cy - dp(5.5f), cx + dp(7.5f), cy + dp(6.5f)), dp(2.5f), dp(2.5f), paint);
                canvas.drawLine(cx - dp(5), cy - dp(7.5f), cx + dp(4), cy - dp(7.5f), paint);
                canvas.drawCircle(cx + dp(4.5f), cy + dp(0.5f), dp(1), paint);
            } else if (type == TAB_SUPPORT) {
                canvas.drawRoundRect(new RectF(cx - dp(7.5f), cy - dp(6.5f), cx + dp(7.5f), cy + dp(4.5f)), dp(3), dp(3), paint);
                canvas.drawLine(cx - dp(2), cy + dp(4.5f), cx - dp(5), cy + dp(7.5f), paint);
                canvas.drawLine(cx - dp(3.5f), cy - dp(2), cx + dp(3.5f), cy - dp(2), paint);
                canvas.drawLine(cx - dp(3.5f), cy + dp(1), cx + dp(1.5f), cy + dp(1), paint);
            } else {
                canvas.drawCircle(cx, cy - dp(4.5f), dp(3.5f), paint);
                canvas.drawArc(new RectF(cx - dp(7), cy + dp(0.5f), cx + dp(7), cy + dp(10)), 200, 140, false, paint);
            }
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    public void onBackPressed() {
        if (inDetail) {
            selectTab(detailParentTab);
            return;
        }
        if (!token.isEmpty() && currentTab != TAB_HOME) {
            selectTab(TAB_HOME);
            return;
        }
        if (!token.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastBackPressAt < 1800) {
                finish();
            } else {
                lastBackPressAt = now;
                toast("再按一次返回退出 XNAT");
            }
            return;
        }
        super.onBackPressed();
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private FrameLayout.LayoutParams frameMatch() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams navWeight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
    }

    private LinearLayout.LayoutParams navCellWeight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        // More breathing room keeps the active capsule from filling an entire fifth
        // of the navigation rail and removes the chunky look from the first RC.
        lp.setMargins(dp(5), dp(4), dp(5), dp(4));
        return lp;
    }

    private void gap(LinearLayout parent, int value) {
        Space s = new Space(this);
        parent.addView(s, new LinearLayout.LayoutParams(1, dp(value)));
    }

    private void gapH(LinearLayout parent, int value) {
        Space s = new Space(this);
        parent.addView(s, new LinearLayout.LayoutParams(dp(value), 1));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private String message(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? "操作失败" : m;
    }

    private String money(long cents) {
        return new DecimalFormat("0.00").format(cents / 100.0);
    }

    private String usdt(long units) {
        return new DecimalFormat("0.######").format(units / 1_000_000.0);
    }

    private String compactEndpoint(String url) {
        if (url == null) return "";
        return url.replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 6) return "夜深了";
        if (hour < 12) return "早上好";
        if (hour < 18) return "下午好";
        return "晚上好";
    }

    private int trafficProgress(long usedBytes, int quotaGb) {
        if (quotaGb <= 0) return 0;
        double quota = quotaGb * 1024.0 * 1024.0 * 1024.0;
        return (int) Math.max(0, Math.min(1000, Math.round((usedBytes / quota) * 1000.0)));
    }

    private String statusLabel(String s) {
        if ("running".equals(s)) return "运行中";
        if ("stopped".equals(s)) return "已关机";
        if ("provisioning".equals(s)) return "开通中";
        if ("reinstalling".equals(s)) return "重装中";
        if ("deleting".equals(s)) return "删除中";
        return s == null || s.isEmpty() ? "未知" : s;
    }

    private int statusColor(String s) {
        if ("running".equals(s)) return GREEN;
        if ("stopped".equals(s)) return MUTED;
        if ("provisioning".equals(s) || "reinstalling".equals(s)) return BLUE;
        return RED;
    }

    private String cleanDate(String iso) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) return "-";
        String cleaned = iso.replace("T", " ").replace("Z", "");
        return cleaned.substring(0, Math.min(16, cleaned.length()));
    }

    private String bytes(long value) {
        double n = Math.max(0, value);
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (n >= 1024 && i < units.length - 1) {
            n /= 1024.0;
            i++;
        }
        return new DecimalFormat(i == 0 ? "0" : "0.0").format(n) + " " + units[i];
    }

    private String blankDash(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value) ? "-" : value;
    }

    private String portSuffix(int port) {
        return port > 0 ? ":" + port : "";
    }

    private String lifecycleLabel(String code) {
        if ("active".equals(code)) return "正常";
        if ("expired".equals(code)) return "已到期";
        if ("suspended".equals(code)) return "已暂停";
        if ("delete_queued".equals(code)) return "等待删除";
        if ("grace".equals(code)) return "宽限期";
        return code == null || code.isEmpty() ? "正常" : code;
    }

    private String orderStatusLabel(String s) {
        if ("completed".equals(s)) return "已完成";
        if ("pending".equals(s)) return "处理中";
        if ("cancelled".equals(s) || "canceled".equals(s)) return "已取消";
        if ("failed".equals(s)) return "失败";
        return blankDash(s);
    }

    private int orderStatusColor(String s) {
        if ("completed".equals(s)) return GREEN;
        if ("pending".equals(s)) return AMBER;
        if ("failed".equals(s) || "cancelled".equals(s) || "canceled".equals(s)) return RED;
        return MUTED;
    }

    private int orderStatusBg(String s) {
        if ("completed".equals(s)) return GREEN_SOFT;
        if ("pending".equals(s)) return AMBER_SOFT;
        if ("failed".equals(s) || "cancelled".equals(s) || "canceled".equals(s)) return RED_SOFT;
        return SOFT;
    }

    private String orderKindLabel(String s) {
        if ("purchase".equals(s)) return "购买";
        if ("renew".equals(s) || "renewal".equals(s)) return "续费";
        return blankDash(s);
    }

    private String ledgerKindLabel(String s) {
        if ("recharge".equals(s)) return "充值入账";
        if ("purchase".equals(s)) return "购买服务";
        if ("refund".equals(s)) return "退款";
        if ("admin".equals(s) || "adjustment".equals(s)) return "余额调整";
        return blankDash(s);
    }

    private String rechargeStatusLabel(String s) {
        if ("paid".equals(s) || "completed".equals(s)) return "已入账";
        if ("pending".equals(s)) return "等待支付";
        if ("detected".equals(s)) return "已检测";
        if ("manual".equals(s)) return "人工确认";
        if ("expired".equals(s)) return "已过期";
        if ("failed".equals(s)) return "失败";
        return blankDash(s);
    }

    private int rechargeStatusColor(String s) {
        if ("paid".equals(s) || "completed".equals(s)) return GREEN;
        if ("pending".equals(s) || "detected".equals(s) || "manual".equals(s)) return AMBER;
        if ("failed".equals(s) || "expired".equals(s)) return RED;
        return MUTED;
    }

    private int rechargeStatusBg(String s) {
        if ("paid".equals(s) || "completed".equals(s)) return GREEN_SOFT;
        if ("pending".equals(s) || "detected".equals(s) || "manual".equals(s)) return AMBER_SOFT;
        if ("failed".equals(s) || "expired".equals(s)) return RED_SOFT;
        return SOFT;
    }

    @Override
    protected void onPause() {
        pausedAt = System.currentTimeMillis();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!resumedOnce) {
            resumedOnce = true;
            return;
        }
        // Returning from browser recharge/payment or another external activity should
        // quietly reconcile balance/status without forcing the user to press Refresh.
        if (!token.isEmpty() && contentHost != null && System.currentTimeMillis() - pausedAt > 1200L) {
            main.postDelayed(() -> {
                if (inDetail && currentDetailServerId > 0) showServerDetail(currentDetailServerId);
                else if (inDetail && currentTicketId > 0) showTicketDetail(currentTicketId);
                else if (!inDetail) reloadCurrentTab();
            }, 180L);
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
