package site.dulotv.app;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.LruCache;
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
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // Alamat situs. Kalau domain berganti, cukup ubah dua baris ini.
    private static final String BASE_URL = "https://dulotv.site";
    private static final String[] ALLOWED_HOSTS = { "dulotv.site" };

    // Isi dengan API Key (v3) TMDB milik Anda, di antara tanda kutip. Dipakai untuk filter tahun.
    private static final String TMDB_API_KEY = "ISI_API_KEY_TMDB_DI_SINI";

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

    // Tab navigasi bawah: Film dan Serial. Tiap tab menampilkan daftar poster dengan filter tahun.
    private static final String[] TAB_LABELS = { "Film", "Serial" };
    private static final String[] TAB_TYPES = { "movie", "tv" };
    private static final int[] TAB_ICONS = { R.drawable.ic_nav_movie, R.drawable.ic_nav_tv };
    private static final int NAV_TABS = 2;

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

    private static class Item {
        int id;
        String type;   // "movie" atau "tv"
        String title;
        String poster; // path poster TMDB, bisa null
    }

    private WebView webView;
    private LinearLayout root;
    private LinearLayout navContainer;
    private final ImageView[] tabIcons = new ImageView[TAB_LABELS.length];
    private final TextView[] tabTexts = new TextView[TAB_LABELS.length];
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private WebChromeClient chromeClient;

    // Filter tahun
    private LinearLayout yearPanel;
    private GridView grid;
    private TextView statusText;
    private final Map<Integer, TextView> yearChips = new HashMap<>();
    private final List<Item> items = new ArrayList<>();
    private PosterAdapter adapter;
    private String selectedType = "movie";
    private int selectedYear = 0; // 0 = semua tahun
    private boolean clearHistoryPending = false;
    private int page = 0;
    private boolean loading = false;
    private boolean hasMore = true;
    private int generation = 0;
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> imageCache =
        new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
            @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
        };

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---------- Bar navigasi bawah ----------

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
            text.setSingleLine(true);
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
        String type = TAB_TYPES[index];
        boolean panelVisible = yearPanel.getVisibility() == View.VISIBLE;
        if (panelVisible && type.equals(selectedType)) {
            // Ketuk tab yang sama: coba muat ulang jika kosong, atau gulir ke atas
            if (items.isEmpty() && !loading) applyFilter();
            else grid.smoothScrollToPosition(0);
            return;
        }
        boolean typeChanged = !type.equals(selectedType);
        selectedType = type;
        showYearPanel(true);
        setActiveTab(index);
        if (typeChanged || (items.isEmpty() && !loading)) applyFilter();
    }

    // Tandai tab yang aktif (putih dan tebal), tab lain abu-abu. -1 berarti tidak ada yang aktif.
    private void setActiveTab(int active) {
        for (int i = 0; i < NAV_TABS; i++) {
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
        if (path.startsWith("/movie/") || path.startsWith("/movies") || path.startsWith("/genre/movie")) return 0;
        if (path.startsWith("/tv/") || path.startsWith("/shows") || path.startsWith("/genre/tv")) return 1;
        return -1;
    }

    // ---------- Filter tahun ----------

    private TextView makeChip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(14);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        chip.setLayoutParams(lp);
        styleChip(chip, false);
        return chip;
    }

    private void styleChip(TextView chip, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(selected ? Color.WHITE : Color.rgb(38, 38, 44));
        chip.setBackground(bg);
        chip.setTextColor(selected ? Color.BLACK : Color.WHITE);
        chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void refreshChips() {
        for (Map.Entry<Integer, TextView> e : yearChips.entrySet()) {
            styleChip(e.getValue(), e.getKey() == selectedYear);
        }
    }

    private View buildYearPanel() {
        yearPanel = new LinearLayout(this);
        yearPanel.setOrientation(LinearLayout.VERTICAL);
        yearPanel.setBackgroundColor(Color.BLACK);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout yearRow = new LinearLayout(this);
        yearRow.setOrientation(LinearLayout.HORIZONTAL);
        yearRow.setPadding(dp(8), dp(8), dp(8), dp(8));
        TextView allChip = makeChip("Semua");
        allChip.setOnClickListener(v -> { selectedYear = 0; applyFilter(); });
        yearRow.addView(allChip);
        yearChips.put(0, allChip);
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int y = currentYear; y >= 1960; y--) {
            final int year = y;
            TextView chip = makeChip(String.valueOf(y));
            chip.setOnClickListener(v -> { selectedYear = year; applyFilter(); });
            yearRow.addView(chip);
            yearChips.put(y, chip);
        }
        scroll.addView(yearRow);

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(200, 200, 205));
        statusText.setTextSize(13);
        statusText.setPadding(dp(16), dp(8), dp(16), dp(8));
        statusText.setVisibility(View.GONE);

        adapter = new PosterAdapter();
        grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(dp(8));
        grid.setVerticalSpacing(dp(8));
        grid.setPadding(dp(8), 0, dp(8), dp(8));
        grid.setClipToPadding(false);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> openItem(items.get(position)));
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {}
            @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 6) loadPage();
            }
        });

        TextView credit = new TextView(this);
        credit.setText("Data film: TMDB");
        credit.setTextColor(INACTIVE_COLOR);
        credit.setTextSize(10);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(4), 0, dp(4));

        yearPanel.addView(scroll);
        yearPanel.addView(statusText);
        yearPanel.addView(grid, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        yearPanel.addView(credit);
        refreshChips();
        return yearPanel;
    }

    private void showYearPanel(boolean show) {
        boolean visible = yearPanel.getVisibility() == View.VISIBLE;
        if (show == visible) return;
        yearPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        webView.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show) webView.onPause(); else webView.onResume();
    }

    private void showStatus(String message) {
        if (message == null) {
            statusText.setVisibility(View.GONE);
        } else {
            statusText.setText(message);
            statusText.setVisibility(View.VISIBLE);
        }
    }

    // Dipanggil saat jenis (Film/Serial) atau tahun berubah: kosongkan daftar lalu muat dari awal
    private void applyFilter() {
        generation++;
        items.clear();
        page = 0;
        hasMore = true;
        loading = false;
        adapter.notifyDataSetChanged();
        refreshChips();
        if (TMDB_API_KEY.startsWith("ISI_")) {
            showStatus("API key TMDB belum diisi. Buka MainActivity.java, isi TMDB_API_KEY, lalu build ulang.");
            return;
        }
        showStatus("Memuat...");
        loadPage();
    }

    private void loadPage() {
        if (loading || !hasMore || TMDB_API_KEY.startsWith("ISI_")) return;
        loading = true;
        final int reqPage = page + 1;
        final String type = selectedType;
        final int year = selectedYear;
        final int gen = generation;
        io.execute(() -> {
            try {
                String yearParam = type.equals("movie") ? "primary_release_year" : "first_air_date_year";
                String url = "https://api.themoviedb.org/3/discover/" + type
                    + "?api_key=" + TMDB_API_KEY
                    + (year != 0 ? "&" + yearParam + "=" + year : "")
                    + "&sort_by=popularity.desc&include_adult=false&vote_count.gte=20&page=" + reqPage;
                JSONObject json = new JSONObject(httpGet(url));
                JSONArray results = json.getJSONArray("results");
                final int totalPages = json.optInt("total_pages", 1);
                final List<Item> fresh = new ArrayList<>();
                for (int i = 0; i < results.length(); i++) {
                    JSONObject o = results.getJSONObject(i);
                    Item it = new Item();
                    it.id = o.getInt("id");
                    it.type = type;
                    it.title = o.optString(type.equals("movie") ? "title" : "name", "");
                    it.poster = o.isNull("poster_path") ? null : o.optString("poster_path", null);
                    fresh.add(it);
                }
                runOnUiThread(() -> {
                    if (gen != generation) return;
                    items.addAll(fresh);
                    page = reqPage;
                    hasMore = reqPage < Math.min(totalPages, 500);
                    loading = false;
                    adapter.notifyDataSetChanged();
                    showStatus(items.isEmpty() ? "Tidak ada hasil." : null);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (gen != generation) return;
                    loading = false;
                    hasMore = false;
                    if (items.isEmpty()) {
                        showStatus("Gagal memuat. Periksa koneksi internet dan API key TMDB, lalu ketuk tab Film/Serial lagi.");
                    }
                });
            }
        });
    }

    private String httpGet(String urlStr) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private void loadPoster(final ImageView view, final String path) {
        view.setTag(path);
        Bitmap cached = imageCache.get(path);
        if (cached != null) { view.setImageBitmap(cached); return; }
        view.setImageBitmap(null);
        io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection)
                    new URL("https://image.tmdb.org/t/p/w342" + path).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                final Bitmap bmp = BitmapFactory.decodeStream(c.getInputStream());
                c.disconnect();
                if (bmp != null) {
                    imageCache.put(path, bmp);
                    runOnUiThread(() -> { if (path.equals(view.getTag())) view.setImageBitmap(bmp); });
                }
            } catch (Exception ignored) {
            }
        });
    }

    // Buka halaman film/serial di situs
    private void openItem(Item it) {
        showYearPanel(false);
        clearHistoryPending = true; // riwayat halaman direset supaya tombol Back kembali ke daftar
        webView.loadUrl(BASE_URL + "/" + it.type + "/" + it.id);
    }

    private class PosterAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            FrameLayout cell = (FrameLayout) convertView;
            ImageView img;
            TextView label;
            if (cell == null) {
                cell = new FrameLayout(MainActivity.this);
                cell.setBackgroundColor(Color.rgb(30, 30, 36));
                img = new ImageView(MainActivity.this);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                label = new TextView(MainActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(12);
                label.setGravity(Gravity.CENTER);
                label.setPadding(dp(6), dp(6), dp(6), dp(6));
                cell.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                cell.addView(label, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                img = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            }
            int gridWidth = parent.getWidth() > 0 ? parent.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
            int w = (gridWidth - dp(32)) / 3;
            cell.setLayoutParams(new AbsListView.LayoutParams(w, w * 3 / 2));

            Item it = items.get(position);
            label.setText(it.title);
            if (it.poster != null) {
                label.setVisibility(View.GONE);
                loadPoster(img, it.poster);
            } else {
                label.setVisibility(View.VISIBLE);
                img.setTag(null);
                img.setImageBitmap(null);
            }
            return cell;
        }
    }

    // ---------- Activity ----------

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BAR_COLOR);
        getWindow().setNavigationBarColor(BAR_COLOR);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setVisibility(View.GONE); // baru tampil saat poster diketuk

        navContainer = new LinearLayout(this);
        navContainer.setOrientation(LinearLayout.VERTICAL);
        navContainer.setBackgroundColor(BAR_COLOR);
        navContainer.addView(buildNavBar());

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BAR_COLOR);
        root.addView(webView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildYearPanel(), new LinearLayout.LayoutParams(
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

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (clearHistoryPending) { clearHistoryPending = false; view.clearHistory(); }
            }

            // Dipanggil setiap alamat halaman berubah, termasuk perpindahan halaman di dalam situs
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                if (yearPanel != null && yearPanel.getVisibility() == View.VISIBLE) return;
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
        applyFilter();
    }

    @Override public void onBackPressed() {
        if (fullscreenView != null) { chromeClient.onHideCustomView(); return; }
        boolean panelVisible = yearPanel != null && yearPanel.getVisibility() == View.VISIBLE;
        if (!panelVisible) {
            if (webView != null && webView.canGoBack()) { webView.goBack(); return; }
            // Dari halaman film kembali ke daftar poster
            showYearPanel(true);
            setActiveTab(selectedType.equals("movie") ? 0 : 1);
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
