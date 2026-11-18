package site.dulotv.app;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayInputStream;

public class MainActivity extends Activity {
    // Alamat situs. Kalau domain berganti, cukup ubah dua baris ini.
    private static final String BASE_URL = "https://dulotv.site";
    private static final String[] ALLOWED_HOSTS = { "dulotv.site" };

    // Domain jaringan iklan yang permintaannya diblokir. Tambahkan domain lain di sini.
    private static final String[] BLOCKED_HOSTS = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "adsterra.com",
        "exoclick.com", "exosrv.com", "juicyads.com", "trafficjunky.com", "clickadu.com",
        "hilltopads.net", "hilltopads.com", "onclickads.net", "adcash.com", "mgid.com",
        "taboola.com", "outbrain.com", "revcontent.com", "adnxs.com", "adsco.re",
        "a-ads.com", "ad-maven.com", "admaven.com", "monetag.com", "richpush.co",
        "pushwoosh.com", "bidvertiser.com", "yllix.com", "ero-advertising.com",
        "profitableratecpmnetwork.com"
    };

    // Tab navigasi bawah: Beranda, Film, Serial, Anime, lalu tombol Muat ulang
    private static final String[] TAB_LABELS = { "Beranda", "Film", "Serial", "Anime", "Muat ulang" };
    private static final String[] TAB_PATHS = { "/", "/movies", "/shows", "/anime" };
    private static final int[] TAB_ICONS = {
        R.drawable.ic_nav_home, R.drawable.ic_nav_movie, R.drawable.ic_nav_tv,
        R.drawable.ic_nav_anime, R.drawable.ic_nav_refresh
    };
    private static final int BAR_COLOR = Color.rgb(10, 10, 15);
    private static final int ACTIVE_COLOR = Color.WHITE;
    private static final int INACTIVE_COLOR = Color.rgb(138, 138, 143);

    private static boolean hostMatches(String host, String[] list) {
        if (host == null) return false;
        host = host.toLowerCase();
        for (String d : list) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    private WebView webView;
    private LinearLayout root;
    private LinearLayout navContainer;
    private final ImageView[] tabIcons = new ImageView[TAB_LABELS.length];
    private final TextView[] tabTexts = new TextView[TAB_LABELS.length];
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private WebChromeClient chromeClient;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private View buildNavBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setClickable(true);
            tab.setLayoutParams(new LinearLayout.LayoutParams(0, dp(56), 1f));

            ImageView icon = new ImageView(this);
            icon.setImageResource(TAB_ICONS[i]);
            icon.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
            icon.setColorFilter(INACTIVE_COLOR);

            TextView text = new TextView(this);
            text.setText(TAB_LABELS[i]);
            text.setTextSize(10);
            text.setTextColor(INACTIVE_COLOR);
            text.setGravity(Gravity.CENTER);
            text.setPadding(0, dp(2), 0, 0);

            tab.addView(icon);
            tab.addView(text);
            tab.setOnClickListener(v -> onTabClicked(index));
            bar.addView(tab);
            tabIcons[i] = icon;
            tabTexts[i] = text;
        }
        return bar;
    }

    private void onTabClicked(int index) {
        if (webView == null) return;
        if (index < TAB_PATHS.length) {
            webView.loadUrl(BASE_URL + TAB_PATHS[index]);
            setActiveTab(index);
        } else {
            webView.reload();
        }
    }

    // Tandai tab yang aktif (putih dan tebal), tab lain abu-abu. -1 berarti tidak ada yang aktif.
    private void setActiveTab(int active) {
        for (int i = 0; i < TAB_PATHS.length; i++) {
            int color = (i == active) ? ACTIVE_COLOR : INACTIVE_COLOR;
            tabIcons[i].setColorFilter(color);
            tabTexts[i].setTextColor(color);
            tabTexts[i].setTypeface(null, i == active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private int tabForUrl(String url) {
        if (url == null) return -1;
        Uri uri = Uri.parse(url);
        if (!hostMatches(uri.getHost(), ALLOWED_HOSTS)) return -1;
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.isEmpty() || path.equals("/")) return 0;
        if (path.startsWith("/movies") || path.startsWith("/movie/") || path.startsWith("/genre/movie")) return 1;
        if (path.startsWith("/shows") || path.startsWith("/tv/") || path.startsWith("/genre/tv")) return 2;
        if (path.startsWith("/anime")) return 3;
        return -1;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BAR_COLOR);
        getWindow().setNavigationBarColor(BAR_COLOR);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);

        navContainer = new LinearLayout(this);
        navContainer.setOrientation(LinearLayout.VERTICAL);
        navContainer.setBackgroundColor(BAR_COLOR);
        navContainer.addView(buildNavBar());

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BAR_COLOR);
        root.addView(webView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(navContainer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // Jaga agar tampilan tidak tertutup status bar / tombol navigasi sistem (Android 15+)
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            navContainer.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        setActiveTab(0);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                if (scheme.equals("http") || scheme.equals("https")) {
                    // Blokir pengalihan halaman utama ke situs lain (biasanya iklan)
                    if (request.isForMainFrame()) return !hostMatches(uri.getHost(), ALLOWED_HOSTS);
                    return false;
                }
                // Blokir intent://, market://, dll. yang sering dipakai iklan untuk membuka aplikasi lain
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (hostMatches(request.getUrl().getHost(), BLOCKED_HOSTS)) {
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            // Dipanggil setiap alamat halaman berubah, termasuk perpindahan halaman di dalam situs
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                setActiveTab(tabForUrl(url));
            }
        });

        chromeClient = new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
                fullscreenView = view;
                fullscreenCallback = callback;
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                setContentView(view);
                // Paksa landscape saat video fullscreen, walau auto-rotate HP mati
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                // Sembunyikan status bar dan tombol navigasi (mode imersif)
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
            @Override public void onHideCustomView() {
                if (fullscreenView == null) return;
                setContentView(root);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                // Kembalikan orientasi ke pengaturan normal
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                fullscreenView = null;
                if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
            }
        };
        webView.setWebChromeClient(chromeClient);
        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(BASE_URL + "/");
    }

    @Override public void onBackPressed() {
        if (fullscreenView != null) { chromeClient.onHideCustomView(); return; }
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
