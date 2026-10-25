package com.emon.tvstream;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String PREFS = "tvstream";
    private static final String KEY_URL = "url";
    private static final String KEY_CURSOR = "cursor";
    private static final String KEY_UA = "uamode";
    private static final String KEY_NAV = "nav";
    private static final String KEY_SITES = "sites";
    private static final String KEY_LAST = "last";
    private static final int[] PALETTE = {
            0xFF1E3A8A, 0xFF7C2D12, 0xFF14532D, 0xFF581C87,
            0xFF831843, 0xFF134E4A, 0xFF3F3F46, 0xFF78350F
    };
    private static final String KEY_ADBLOCK = "adblock";
    // Ad / tracker / pop-up networks that are blocked (speeds pages up a lot).
    private static final String[] BLOCKED_HOSTS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "googletagservices.com", "googletagmanager.com", "google-analytics.com",
            "adservice.google.com", "adnxs.com", "taboola.com", "outbrain.com",
            "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
            "adsterra.com", "juicyads.com", "trafficjunky.net", "clickadu.com",
            "hilltopads.net", "adcash.com", "mgid.com", "revcontent.com",
            "criteo.com", "criteo.net", "scorecardresearch.com", "quantserve.com",
            "hotjar.com", "amazon-adsystem.com", "histats.com", "statcounter.com",
            "adform.net", "rubiconproject.com", "pubmatic.com", "openx.net",
            "smartadserver.com", "onclickads.net", "a-ads.com", "admaven.com",
            "ad-maven.com", "richads.com", "connect.facebook.net",
            "adskeeper.com", "monetag.com", "profitablecpmrate.com"
    };
    private static final String UA_DESKTOP =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/130.0.0.0 Safari/537.36";
    private static final String UA_PHONE =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/130.0.0.0 Mobile Safari/537.36";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private FrameLayout root;
    private WebView webView;
    private CursorView cursorView;
    private TextView menuButton;
    private WebChromeClient chromeClient;

    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    private AlertDialog menuDialog;
    private String defaultUa = "";

    // 0 = Tile (highlight jumps between buttons), 1 = Cursor, 2 = Off
    private int navMode = 0;
    private boolean tileBusy = false;
    private boolean adBlock = true;

    // "My sites" home screen
    private List<String[]> sites = new ArrayList<>();
    private int currentSiteIndex = -1;
    private boolean homeShown = false;
    private FrameLayout homeView;
    private GridLayout homeGrid;
    private String tileJs = "";
    private float cx, cy;
    private float density = 1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        navMode = prefs.getInt(KEY_NAV, 0);
        adBlock = prefs.getBoolean(KEY_ADBLOCK, true);
        loadTileJs();
        density = getResources().getDisplayMetrics().density;
        cx = getResources().getDisplayMetrics().widthPixels / 2f;
        cy = getResources().getDisplayMetrics().heightPixels / 2f;

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setupWebView();

        menuButton = new TextView(this);
        menuButton.setText("\u2630  Menu");
        menuButton.setTextColor(Color.WHITE);
        menuButton.setTextSize(16);
        menuButton.setPadding((int) (14 * density), (int) (8 * density),
                (int) (14 * density), (int) (8 * density));
        menuButton.setBackgroundColor(0x99000000);
        menuButton.setClickable(true);
        menuButton.setOnClickListener(v -> showMenu());
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        mlp.setMargins(0, (int) (12 * density), (int) (12 * density), 0);
        root.addView(menuButton, mlp);

        cursorView = new CursorView(this);
        root.addView(cursorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildHomeView();
        refreshCursorVisibility();

        sites = loadSites();
        if (sites.isEmpty()) {
            promptForSite(-1, true);
        } else {
            String last = prefs.getString(KEY_LAST, null);
            if (last != null) {
                currentSiteIndex = indexForUrl(last);
                webView.loadUrl(last);
            } else {
                currentSiteIndex = 0;
                webView.loadUrl(sites.get(0)[1]);
            }
        }

        handler.postDelayed(() -> checkForUpdates(false), 4000);
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        String ua = s.getUserAgentString();
        defaultUa = (ua == null) ? "" : ua.replace("; wv", "");
        applyUserAgent();

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    prefs.edit().putString(KEY_LAST, url).apply();
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (adBlock) {
                    String host = request.getUrl().getHost();
                    if (host != null && isBlockedHost(host.toLowerCase())) {
                        return new WebResourceResponse("text/plain", "utf-8",
                                new ByteArrayInputStream(new byte[0]));
                    }
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString().toLowerCase();
                // Stay inside the app; block links that try to open other apps.
                return !(u.startsWith("http://") || u.startsWith("https://"));
            }
        });

        chromeClient = new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                if (cursorView != null) cursorView.bringToFront();
                refreshCursorVisibility();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                if (customCallback != null) customCallback.onCustomViewHidden();
                customCallback = null;
                refreshCursorVisibility();
            }
        };
        webView.setWebChromeClient(chromeClient);
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_MENU) {
            if (down && e.getRepeatCount() == 0) showMenu();
            return true;
        }

        if (homeShown) {
            return super.dispatchKeyEvent(e);
        }

        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (down && e.getRepeatCount() == 0) togglePlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (down) skipOrScroll(1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) skipOrScroll(-1);
                return true;
            default:
                break;
        }

        boolean isSelect = code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                || code == KeyEvent.KEYCODE_BUTTON_A;
        boolean isDpad = code == KeyEvent.KEYCODE_DPAD_UP
                || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT;

        if (navMode == 0) {
            if (isDpad) {
                if (down) tileMove(code);
                return true;
            }
            if (isSelect) {
                if (down && e.getRepeatCount() == 0) tileSelect();
                return true;
            }
        } else if (navMode == 1) {
            if (isDpad) {
                if (down) moveCursor(code, e.getRepeatCount());
                return true;
            }
            if (isSelect) {
                if (down && e.getRepeatCount() == 0) clickAtCursor();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (homeShown) {
            String cur = webView.getUrl();
            if (cur != null && !cur.startsWith("about:")) hideHome();
            else finish();
            return;
        }
        if (customView != null) {
            chromeClient.onHideCustomView();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        showHome();
    }

    // ------------------------------------------------------------ tile mode

    private static boolean isBlockedHost(String host) {
        for (String b : BLOCKED_HOSTS) {
            if (host.equals(b) || host.endsWith("." + b)) return true;
        }
        return false;
    }

    private void loadTileJs() {
        try (InputStream in = getAssets().open("tile.js")) {
            tileJs = readAll(in);
        } catch (IOException ex) {
            tileJs = "";
        }
    }

    // Runs a call into tile.js. The script is injected on first use (and again
    // after every full page load, because the page forgets it).
    private void runTile(final String call, final ValueCallback<String> cb) {
        final String guarded = "(window.__tvNav ? " + call + " : 'noinit')";
        webView.evaluateJavascript(guarded, value -> {
            if (value != null && value.contains("noinit") && !tileJs.isEmpty()) {
                webView.evaluateJavascript(tileJs, x ->
                        webView.evaluateJavascript(guarded, cb));
            } else {
                cb.onReceiveValue(value);
            }
        });
    }

    private void tileMove(int keyCode) {
        if (tileBusy) return; // ignore key repeats while the page is still busy
        String dir;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: dir = "up"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dir = "down"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = "left"; break;
            default: dir = "right"; break;
        }
        tileBusy = true;
        handler.postDelayed(() -> tileBusy = false, 1500); // safety reset
        runTile("window.__tvNav('" + dir + "'," + root.getWidth() + ")", v -> tileBusy = false);
    }

    private void tileSelect() {
        runTile("window.__tvSel(" + root.getWidth() + ")", value -> {
            if (value == null) return;
            String t = value.trim();
            if (t.equals("null")) {
                // Highlight is hidden. Full screen video: Select = play/pause.
                // Otherwise just bring the highlight back.
                if (customView != null) {
                    togglePlay();
                    return;
                }
                String js = "(function(){var vs=document.getElementsByTagName('video');"
                        + "for(var i=0;i<vs.length;i++){if(!vs[i].paused)return 1;}return 0;})();";
                webView.evaluateJavascript(js, pv -> {
                    if ("1".equals(pv)) togglePlay();
                    else runTile("window.__tvNav('start'," + root.getWidth() + ")", v -> { });
                });
                return;
            }
            if (!t.startsWith("[")) return;
            try {
                String[] p = t.substring(1, t.length() - 1).split(",");
                tapAt(Float.parseFloat(p[0]), Float.parseFloat(p[1]));
            } catch (Exception ignored) {
                // bad value, do nothing
            }
        });
    }

    // ------------------------------------------------------- player control

    private void refreshCursorVisibility() {
        if (cursorView == null) return;
        cursorView.setVisibility(navMode == 1 ? View.VISIBLE : View.GONE);
        cursorView.invalidate();
        if (menuButton != null) menuButton.setVisibility(navMode == 1 ? View.VISIBLE : View.GONE);
    }

    private void togglePlay() {
        videoJs("if(v.paused){v.play();}else{v.pause();}", KeyEvent.KEYCODE_SPACE);
    }

    // Fast-forward / Rewind: seek if a video is playing (or full screen),
    // otherwise scroll sideways (e.g. rows of posters).
    private void skipOrScroll(final int dir) {
        if (customView != null) {
            seek(dir * 10);
            return;
        }
        String js = "(function(){var vs=document.getElementsByTagName('video');"
                + "for(var i=0;i<vs.length;i++){if(!vs[i].paused){"
                + "vs[i].currentTime=Math.max(0,vs[i].currentTime+(" + (dir * 10) + "));return 1;}}"
                + "return 0;})();";
        webView.evaluateJavascript(js, value -> {
            if (!"1".equals(value)) {
                runTile("window.__tvPage('" + (dir > 0 ? "right" : "left") + "')", v -> { });
            }
        });
    }

    private void seek(int seconds) {
        videoJs("v.currentTime=Math.max(0,v.currentTime+(" + seconds + "));",
                seconds > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
    }

    // Runs on a <video> in the page. If the video lives inside another site's
    // iframe the page can't be reached, so a plain key press is sent instead.
    private void videoJs(String body, final int fallbackKey) {
        String js = "(function(){var vs=document.getElementsByTagName('video');var v=null;"
                + "for(var i=0;i<vs.length;i++){if(!vs[i].paused){v=vs[i];break;}}"
                + "if(!v)v=vs[0];if(!v)return 0;" + body + "return 1;})();";
        webView.evaluateJavascript(js, value -> {
            if (!"1".equals(value) && fallbackKey != 0) sendKeyToPage(fallbackKey);
        });
    }

    private void sendKeyToPage(int keyCode) {
        long t = SystemClock.uptimeMillis();
        webView.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0));
        webView.dispatchKeyEvent(new KeyEvent(t, t + 20, KeyEvent.ACTION_UP, keyCode, 0));
    }

    // -------------------------------------------------------------- cursor

    // Scrolls whatever is under the cursor (page or an inner scroll box).
    private void scrollPage(int dxPx, int dyPx) {
        String js = "(function(x,y,dx,dy,nw){var r=nw/window.innerWidth;dx=dx/r;dy=dy/r;"
                + "function can(el){var cs=getComputedStyle(el);"
                + "if(dy>0&&/(auto|scroll)/.test(cs.overflowY)&&el.scrollTop+el.clientHeight<el.scrollHeight-1)return true;"
                + "if(dy<0&&/(auto|scroll)/.test(cs.overflowY)&&el.scrollTop>0)return true;"
                + "if(dx>0&&/(auto|scroll)/.test(cs.overflowX)&&el.scrollLeft+el.clientWidth<el.scrollWidth-1)return true;"
                + "if(dx<0&&/(auto|scroll)/.test(cs.overflowX)&&el.scrollLeft>0)return true;"
                + "return false;}"
                + "var el=document.elementFromPoint(x/r,y/r);"
                + "while(el&&el!==document.documentElement){"
                + "if(can(el)){el.scrollBy(dx,dy);return;}el=el.parentElement;}"
                + "window.scrollBy(dx,dy);})("
                + (int) cx + "," + (int) cy + "," + dxPx + "," + dyPx + "," + root.getWidth() + ");";
        webView.evaluateJavascript(js, null);
    }

    private void moveCursor(int keyCode, int repeat) {
        float step = (14 + Math.min(repeat, 30) * 5) * density;
        int scrollStep = (int) (step * 2);
        float edge = 64 * density;
        int w = root.getWidth();
        int h = root.getHeight();
        if (w == 0 || h == 0) return;

        float dx = 0, dy = 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: dy = -step; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dy = step; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dx = -step; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dx = step; break;
            default: break;
        }

        float nx = cx + dx;
        float ny = cy + dy;
        cx = Math.max(0, Math.min(w - 1, nx));
        cy = Math.max(0, Math.min(h - 1, ny));
        cursorView.invalidate();

        // Near a screen edge: scroll the page too.
        if (dy < 0 && ny < edge) scrollPage(0, -scrollStep);
        else if (dy > 0 && ny > h - edge) scrollPage(0, scrollStep);
        else if (dx < 0 && nx < edge) scrollPage(-scrollStep, 0);
        else if (dx > 0 && nx > w - edge) scrollPage(scrollStep, 0);
    }

    private void clickAtCursor() {
        tapAt(cx, cy);
    }

    // A real finger tap at screen position (x, y), so the page treats it as a
    // genuine click (needed for fullscreen, the on-screen keyboard, etc).
    private void tapAt(final float x, final float y) {
        final View target = root;
        final long downTime = SystemClock.uptimeMillis();

        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(down);
        down.recycle();

        handler.postDelayed(() -> {
            long upTime = SystemClock.uptimeMillis();
            MotionEvent up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0);
            up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            target.dispatchTouchEvent(up);
            up.recycle();
        }, 30);
    }

    private class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

        CursorView(Context c) {
            super(c);
            setFocusable(false);
            setClickable(false);
            fill.setColor(Color.WHITE);
            fill.setStyle(Paint.Style.FILL);
            ring.setColor(Color.BLACK);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(3 * density);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = 10 * density;
            canvas.drawCircle(cx, cy, r, fill);
            canvas.drawCircle(cx, cy, r, ring);
        }
    }

    // ---------------------------------------------------------------- menu

    private String navName(int v) {
        if (v == 1) return "Cursor";
        if (v == 2) return "Off";
        return "Tile";
    }

    private void showMenu() {
        if (menuDialog != null && menuDialog.isShowing()) return;
        String[] items = {
                "My sites",
                "Go to this site's start page",
                "Edit this site",
                "Reload page",
                "Clear cache & cookies",
                "Remote mode: " + navName(navMode) + " (tap to switch)",
                "Browser mode: " + uaName(prefs.getInt(KEY_UA, 1)) + " (tap to switch)",
                "Ad & tracker blocker: " + (adBlock ? "ON (tap to turn off)" : "OFF (tap to turn on)"),
                "Check for updates (installed: build " + BuildConfig.VERSION_CODE + ")",
                "Exit app"
        };
        menuDialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("TV Stream menu")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showHome();
                            break;
                        case 1:
                            if (currentSiteIndex >= 0 && currentSiteIndex < sites.size()) {
                                webView.loadUrl(sites.get(currentSiteIndex)[1]);
                            } else {
                                showHome();
                            }
                            break;
                        case 2:
                            promptForSite(currentSiteIndex, false);
                            break;
                        case 3:
                            webView.reload();
                            break;
                        case 4:
                            webView.clearCache(true);
                            webView.clearHistory();
                            WebStorage.getInstance().deleteAllData();
                            CookieManager.getInstance().removeAllCookies(null);
                            CookieManager.getInstance().flush();
                            Toast.makeText(this, "Cache and cookies cleared", Toast.LENGTH_SHORT).show();
                            break;
                        case 5:
                            navMode = (navMode + 1) % 3;
                            prefs.edit().putInt(KEY_NAV, navMode).apply();
                            refreshCursorVisibility();
                            webView.evaluateJavascript("window.__tvOff&&window.__tvOff();", null);
                            Toast.makeText(this, "Remote mode: " + navName(navMode),
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case 6:
                            int next = (prefs.getInt(KEY_UA, 1) + 1) % 3;
                            prefs.edit().putInt(KEY_UA, next).apply();
                            applyUserAgent();
                            Toast.makeText(this, "Browser mode: " + uaName(next), Toast.LENGTH_SHORT).show();
                            webView.reload();
                            break;
                        case 7:
                            adBlock = !adBlock;
                            prefs.edit().putBoolean(KEY_ADBLOCK, adBlock).apply();
                            Toast.makeText(this, "Ad & tracker blocker: " + (adBlock ? "ON" : "OFF"),
                                    Toast.LENGTH_SHORT).show();
                            webView.reload();
                            break;
                        case 8:
                            checkForUpdates(true);
                            break;
                        case 9:
                            finish();
                            break;
                        default:
                            break;
                    }
                })
                .create();
        menuDialog.show();
    }

    // 0 = TV default, 1 = Desktop Chrome, 2 = Phone Chrome
    private void applyUserAgent() {
        int mode = prefs.getInt(KEY_UA, 1);
        WebSettings s = webView.getSettings();
        if (mode == 1) s.setUserAgentString(UA_DESKTOP);
        else if (mode == 2) s.setUserAgentString(UA_PHONE);
        else s.setUserAgentString(defaultUa);
    }

    private String uaName(int mode) {
        if (mode == 1) return "Desktop";
        if (mode == 2) return "Phone";
        return "TV default";
    }

    // ----------------------------------------------------------- my sites

    private List<String[]> loadSites() {
        List<String[]> list = new ArrayList<>();
        String raw = prefs.getString(KEY_SITES, null);
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    list.add(new String[]{o.optString("n"), o.optString("u")});
                }
            } catch (Exception ignored) {
                // corrupt data: start with an empty list
            }
        } else {
            // Upgrade from the single saved link of older versions.
            String old = prefs.getString("url", null);
            if (old != null) {
                list.add(new String[]{niceName(old), old});
                saveSites(list);
            }
        }
        return list;
    }

    private void saveSites(List<String[]> list) {
        JSONArray arr = new JSONArray();
        try {
            for (String[] s : list) {
                JSONObject o = new JSONObject();
                o.put("n", s[0]);
                o.put("u", s[1]);
                arr.put(o);
            }
        } catch (Exception ignored) {
            return;
        }
        prefs.edit().putString(KEY_SITES, arr.toString()).apply();
    }

    private String hostOf(String url) {
        try {
            String h = Uri.parse(url).getHost();
            return h == null ? "" : h.toLowerCase().replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return "";
        }
    }

    private String niceName(String url) {
        String h = hostOf(url);
        if (h.isEmpty()) return "Site";
        String base = h.contains(".") ? h.substring(0, h.indexOf('.')) : h;
        if (base.isEmpty()) return "Site";
        return Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    private int indexForUrl(String url) {
        String h = hostOf(url);
        for (int i = 0; i < sites.size(); i++) {
            if (hostOf(sites.get(i)[1]).equals(h)) return i;
        }
        return -1;
    }

    private void buildHomeView() {
        homeView = new FrameLayout(this);
        homeView.setBackgroundColor(0xFF0B0F1A);
        homeView.setClickable(true);
        homeView.setVisibility(View.GONE);

        int pad = (int) (40 * density);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("My sites");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, (int) (16 * density));
        col.addView(title);

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        homeGrid = new GridLayout(this);
        homeGrid.setClipChildren(false);
        homeGrid.setClipToPadding(false);
        scroll.addView(homeGrid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("Select = open     Hold Select = edit or remove     Back = return to the page");
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(14);
        hint.setPadding(0, (int) (12 * density), 0, 0);
        col.addView(hint);

        homeView.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(homeView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private Drawable tileBackground(int color) {
        float radius = 14 * density;
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(color);
        normal.setCornerRadius(radius);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(color);
        focused.setCornerRadius(radius);
        focused.setStroke((int) (4 * density), 0xFFFFD400);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        return sl;
    }

    private View makeTile(String name, String sub, int color) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        int p = (int) (12 * density);
        t.setPadding(p, p, p, p);
        t.setBackground(tileBackground(color));
        t.setFocusable(true);
        t.setClickable(true);

        TextView n = new TextView(this);
        n.setText(name);
        n.setTextColor(Color.WHITE);
        n.setTextSize(20);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(n);

        if (sub != null && !sub.isEmpty()) {
            TextView h = new TextView(this);
            h.setText(sub);
            h.setTextColor(0xB3FFFFFF);
            h.setTextSize(13);
            h.setGravity(Gravity.CENTER);
            h.setMaxLines(1);
            h.setEllipsize(TextUtils.TruncateAt.END);
            t.addView(h);
        }

        t.setOnFocusChangeListener((v, has) -> v.animate()
                .scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start());
        return t;
    }

    private void buildHome() {
        homeGrid.removeAllViews();
        int widthPx = getResources().getDisplayMetrics().widthPixels;
        int pad = (int) (40 * density);
        int margin = (int) (8 * density);
        int target = (int) (210 * density);
        int cols = Math.max(2, (widthPx - 2 * pad) / (target + 2 * margin));
        int tileW = (widthPx - 2 * pad) / cols - 2 * margin;
        int tileH = (int) (112 * density);
        homeGrid.setColumnCount(cols);

        View focusTarget = null;
        for (int i = 0; i < sites.size(); i++) {
            final int idx = i;
            String[] s = sites.get(i);
            View t = makeTile(s[0], hostOf(s[1]), PALETTE[Math.abs(s[0].hashCode()) % PALETTE.length]);
            t.setOnClickListener(v -> openSite(idx));
            t.setOnLongClickListener(v -> {
                siteOptions(idx);
                return true;
            });
            homeGrid.addView(t, tileParams(tileW, tileH, margin));
            if (focusTarget == null || i == currentSiteIndex) focusTarget = t;
        }

        View add = makeTile("+  Add site", "", 0xFF374151);
        add.setOnClickListener(v -> promptForSite(-1, false));
        homeGrid.addView(add, tileParams(tileW, tileH, margin));

        View menu = makeTile("\u2630  Menu", "", 0xFF374151);
        menu.setOnClickListener(v -> showMenu());
        homeGrid.addView(menu, tileParams(tileW, tileH, margin));

        if (focusTarget == null) focusTarget = add;
        final View ft = focusTarget;
        homeView.post(ft::requestFocus);
    }

    private GridLayout.LayoutParams tileParams(int w, int h, int margin) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = w;
        lp.height = h;
        lp.setMargins(margin, margin, margin, margin);
        return lp;
    }

    private void showHome() {
        if (homeShown) return;
        homeShown = true;
        buildHome();
        homeView.setVisibility(View.VISIBLE);
        homeView.bringToFront();
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        // Stop any sound from the page while the site list is open.
        webView.evaluateJavascript("(function(){var m=document.querySelectorAll('video,audio');"
                + "for(var i=0;i<m.length;i++){try{m[i].pause();}catch(e){}}})();", null);
        webView.onPause();
    }

    private void hideHome() {
        if (!homeShown) return;
        homeShown = false;
        homeView.setVisibility(View.GONE);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.onResume();
        webView.requestFocus();
    }

    private void openSite(int idx) {
        if (idx < 0 || idx >= sites.size()) return;
        String[] s = sites.get(idx);
        currentSiteIndex = idx;
        String cur = webView.getUrl();
        boolean sameSite = cur != null && !cur.startsWith("about:")
                && hostOf(cur).equals(hostOf(s[1]));
        hideHome();
        if (!sameSite) webView.loadUrl(s[1]); // same site: just go back to where you were
    }

    private void siteOptions(final int idx) {
        if (idx < 0 || idx >= sites.size()) return;
        String[] items = {"Open (start page)", "Edit name or link", "Remove"};
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(sites.get(idx)[0])
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        currentSiteIndex = idx;
                        hideHome();
                        webView.loadUrl(sites.get(idx)[1]);
                    } else if (which == 1) {
                        promptForSite(idx, false);
                    } else {
                        confirmRemove(idx);
                    }
                })
                .show();
    }

    private void confirmRemove(final int idx) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Remove " + sites.get(idx)[0] + "?")
                .setPositiveButton("Remove", (d, w) -> {
                    sites.remove(idx);
                    saveSites(sites);
                    if (currentSiteIndex == idx) currentSiteIndex = -1;
                    else if (currentSiteIndex > idx) currentSiteIndex--;
                    if (homeShown) buildHome();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Add (index = -1) or edit a saved site.
    private void promptForSite(final int index, final boolean firstRun) {
        final boolean editing = index >= 0 && index < sites.size();
        int p = (int) (20 * density);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(p, p / 2, p, 0);

        final EditText nameIn = new EditText(this);
        nameIn.setSingleLine(true);
        nameIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameIn.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        nameIn.setHint("Name (e.g. Movies)");
        form.addView(nameIn);

        final EditText linkIn = new EditText(this);
        linkIn.setSingleLine(true);
        linkIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        linkIn.setImeOptions(EditorInfo.IME_ACTION_DONE);
        linkIn.setHint("https://example.com");
        form.addView(linkIn);

        if (editing) {
            nameIn.setText(sites.get(index)[0]);
            linkIn.setText(sites.get(index)[1]);
            linkIn.setSelection(linkIn.getText().length());
        }

        final AlertDialog dlg = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(editing ? "Edit site" : "Add a site")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String u = normalizeUrl(linkIn.getText().toString());
                    if (u == null) {
                        Toast.makeText(this, "Invalid link, try again", Toast.LENGTH_LONG).show();
                        promptForSite(index, firstRun);
                        return;
                    }
                    String name = nameIn.getText().toString().trim();
                    if (name.isEmpty()) name = niceName(u);
                    int at;
                    if (editing) {
                        sites.set(index, new String[]{name, u});
                        at = index;
                    } else {
                        sites.add(new String[]{name, u});
                        at = sites.size() - 1;
                    }
                    saveSites(sites);
                    if (homeShown) {
                        buildHome();
                    } else {
                        currentSiteIndex = at;
                        webView.loadUrl(u);
                    }
                })
                .setNegativeButton(firstRun ? "Exit" : "Cancel", (d, w) -> {
                    if (firstRun) finish();
                })
                .setCancelable(!firstRun)
                .create();

        linkIn.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });

        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
    }

    private String normalizeUrl(String raw) {
        String u = raw == null ? "" : raw.trim();
        if (u.isEmpty() || u.contains(" ")) return null;
        String lower = u.toLowerCase();
        if (!lower.contains("://")) {
            u = "https://" + u;
        } else if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return null;
        }
        return u;
    }

    // ------------------------------------------------------------- updates

    private HttpURLConnection openConnection(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "TVStream-Updater");
        return c;
    }

    private String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private void checkForUpdates(final boolean manual) {
        if (BuildConfig.REPO.isEmpty()) {
            if (manual) {
                Toast.makeText(this, "Updates only work for builds made by GitHub", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (manual) Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                HttpURLConnection c = openConnection(
                        "https://api.github.com/repos/" + BuildConfig.REPO + "/releases?per_page=15");
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() != 200) {
                    throw new IOException("GitHub replied " + c.getResponseCode());
                }
                JSONArray releases = new JSONArray(readAll(c.getInputStream()));
                int bestBuild = -1;
                String bestUrl = null;
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject r = releases.getJSONObject(i);
                    if (r.optBoolean("draft")) continue;
                    String digits = r.optString("tag_name", "").replaceAll("[^0-9]", "");
                    if (digits.isEmpty() || digits.length() > 8) continue;
                    int n = Integer.parseInt(digits);
                    JSONArray assets = r.optJSONArray("assets");
                    if (assets == null) continue;
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject a = assets.getJSONObject(j);
                        if ("TVStream.apk".equals(a.optString("name")) && n > bestBuild) {
                            bestBuild = n;
                            bestUrl = a.optString("browser_download_url", null);
                        }
                    }
                }
                final int newBuild = bestBuild;
                final String newUrl = bestUrl;
                runOnUiThread(() -> {
                    if (newUrl != null && newBuild > BuildConfig.VERSION_CODE) {
                        showUpdateDialog(newBuild, newUrl);
                    } else if (manual) {
                        Toast.makeText(this, "You're up to date (build "
                                + BuildConfig.VERSION_CODE + ")", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                final String msg = String.valueOf(e.getMessage());
                if (manual) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Update check failed: " + msg, Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }

    private void showUpdateDialog(final int build, final String url) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Update available")
                .setMessage("New version: build " + build + "\nInstalled: build "
                        + BuildConfig.VERSION_CODE)
                .setPositiveButton("Install now", (d, w) -> downloadAndInstall(url))
                .setNegativeButton("Later", null)
                .show();
    }

    private void downloadAndInstall(final String url) {
        Toast.makeText(this, "Downloading update...", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "update");
                dir.mkdirs();
                final File out = new File(dir, "TVStream.apk");
                HttpURLConnection c = openConnection(url);
                if (c.getResponseCode() != 200) {
                    throw new IOException("Download replied " + c.getResponseCode());
                }
                InputStream in = c.getInputStream();
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                fos.close();
                in.close();
                runOnUiThread(() -> installApk(out));
            } catch (Exception e) {
                final String msg = String.valueOf(e.getMessage());
                runOnUiThread(() -> Toast.makeText(this,
                        "Download failed: " + msg, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void installApk(File apk) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this,
                        "Turn ON 'Install unknown apps' for TV Stream, press Back, then check for updates again",
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Install failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ----------------------------------------------------------- lifecycle

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!homeShown) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            root.removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }
}
