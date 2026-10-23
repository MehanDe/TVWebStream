package com.emon.tvstream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String PREFS = "tvstream";
    private static final String KEY_URL = "url";
    private static final String KEY_CURSOR = "cursor";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private FrameLayout root;
    private WebView webView;
    private CursorView cursorView;
    private WebChromeClient chromeClient;

    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    private AlertDialog menuDialog;

    private boolean cursorMode = true;
    private float cx, cy;
    private float density = 1f;
    private long lastBack = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        cursorMode = prefs.getBoolean(KEY_CURSOR, true);
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

        cursorView = new CursorView(this);
        root.addView(cursorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cursorView.setVisibility(cursorMode ? View.VISIBLE : View.GONE);

        String url = prefs.getString(KEY_URL, null);
        if (url == null) {
            promptForUrl(true);
        } else {
            webView.loadUrl(url);
        }
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

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        webView.setWebViewClient(new WebViewClient() {
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
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                if (customCallback != null) customCallback.onCustomViewHidden();
                customCallback = null;
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

        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (down && e.getRepeatCount() == 0)
                    videoJs("if(v.paused){v.play();}else{v.pause();}");
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (down) videoJs("v.play();");
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (down) videoJs("v.pause();");
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (down) videoJs("v.currentTime=v.currentTime+10;");
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) videoJs("v.currentTime=Math.max(0,v.currentTime-10);");
                return true;
            default:
                break;
        }

        if (cursorMode) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) moveCursor(code, e.getRepeatCount());
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    if (down && e.getRepeatCount() == 0) clickAtCursor();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            chromeClient.onHideCustomView();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastBack < 2000) {
            finish();
        } else {
            lastBack = now;
            Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).show();
        }
    }

    private void videoJs(String body) {
        String js = "(function(){var vs=document.getElementsByTagName('video');var v=null;"
                + "for(var i=0;i<vs.length;i++){if(!vs[i].paused){v=vs[i];break;}}"
                + "if(!v)v=vs[0];if(!v)return;" + body + "})();";
        webView.evaluateJavascript(js, null);
    }

    // -------------------------------------------------------------- cursor

    private void moveCursor(int keyCode, int repeat) {
        float step = (10 + Math.min(repeat, 25) * 3) * density;
        int scrollStep = (int) (step * 2);
        float edge = 48 * density;
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

        // Near a screen edge: also scroll the page in that direction.
        if (dy < 0 && ny < edge) webView.scrollBy(0, -scrollStep);
        if (dy > 0 && ny > h - edge) webView.scrollBy(0, scrollStep);
        if (dx < 0 && nx < edge) webView.scrollBy(-scrollStep, 0);
        if (dx > 0 && nx > w - edge) webView.scrollBy(scrollStep, 0);

        cx = Math.max(0, Math.min(w - 1, nx));
        cy = Math.max(0, Math.min(h - 1, ny));
        cursorView.invalidate();
    }

    private void clickAtCursor() {
        final View target = (customView != null) ? customView : webView;
        final float x = cx;
        final float y = cy;
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
        }, 60);
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

    private void showMenu() {
        if (menuDialog != null && menuDialog.isShowing()) return;
        String[] items = {
                "Change website",
                "Go to home page",
                "Reload page",
                "Clear cache & cookies",
                "Cursor mode: " + (cursorMode ? "ON (tap to turn off)" : "OFF (tap to turn on)"),
                "Exit app"
        };
        menuDialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("TV Stream menu")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            promptForUrl(false);
                            break;
                        case 1:
                            String home = prefs.getString(KEY_URL, null);
                            if (home != null) webView.loadUrl(home);
                            break;
                        case 2:
                            webView.reload();
                            break;
                        case 3:
                            webView.clearCache(true);
                            webView.clearHistory();
                            WebStorage.getInstance().deleteAllData();
                            CookieManager.getInstance().removeAllCookies(null);
                            CookieManager.getInstance().flush();
                            Toast.makeText(this, "Cache and cookies cleared", Toast.LENGTH_SHORT).show();
                            break;
                        case 4:
                            cursorMode = !cursorMode;
                            prefs.edit().putBoolean(KEY_CURSOR, cursorMode).apply();
                            cursorView.setVisibility(cursorMode ? View.VISIBLE : View.GONE);
                            break;
                        case 5:
                            finish();
                            break;
                        default:
                            break;
                    }
                })
                .create();
        menuDialog.show();
    }

    private void promptForUrl(final boolean firstRun) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint("https://example.com");
        String current = prefs.getString(KEY_URL, "");
        input.setText(current);
        input.setSelection(input.getText().length());

        final AlertDialog dlg = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Enter website link")
                .setView(input)
                .setPositiveButton("Save & open", (d, w) -> {
                    String u = normalizeUrl(input.getText().toString());
                    if (u == null) {
                        Toast.makeText(this, "Invalid link, try again", Toast.LENGTH_LONG).show();
                        promptForUrl(firstRun);
                    } else {
                        prefs.edit().putString(KEY_URL, u).apply();
                        webView.loadUrl(u);
                    }
                })
                .setNegativeButton(firstRun ? "Exit" : "Cancel", (d, w) -> {
                    if (firstRun) finish();
                })
                .setCancelable(!firstRun)
                .create();

        input.setOnEditorActionListener((tv, actionId, ev) -> {
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
        webView.onResume();
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
