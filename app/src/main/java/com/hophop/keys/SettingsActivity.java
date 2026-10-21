package com.hophop.keys;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;

/** Настройки: страница в WebView, все значения хранятся в SharedPreferences "kb" и читаются клавиатурой. */
public class SettingsActivity extends Activity {
    private static final int PICK_BG = 41;
    private WebView web;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(0xFF141218);
        setContentView(web);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        // Страница локальная, наружу никуда не ходим.
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
        });
        web.addJavascriptInterface(new Bridge(), "Kb");
        web.loadUrl("file:///android_asset/settings.html");
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.onState&&onState()", null);
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        // Выбор клавиатуры открывается поверх окна: после него обновляем статус.
        if (f && web != null) web.evaluateJavascript("window.onState&&onState()", null);
    }

    private void js(String s) { runOnUiThread(() -> web.evaluateJavascript(s, null)); }

    @Override protected void onActivityResult(int rq, int rc, Intent d) {
        super.onActivityResult(rq, rc, d);
        if (rq != PICK_BG || rc != RESULT_OK || d == null || d.getData() == null) return;
        final Uri u = d.getData();
        new Thread(() -> {
            boolean ok = false;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
                int s = 1; while (Math.max(o.outWidth, o.outHeight) / s > 1600) s *= 2;
                o = new BitmapFactory.Options(); o.inSampleSize = s;
                Bitmap bm;
                try (InputStream in = getContentResolver().openInputStream(u)) { bm = BitmapFactory.decodeStream(in, null, o); }
                if (bm != null) {
                    try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "bg.img"))) { bm.compress(Bitmap.CompressFormat.JPEG, 90, out); }
                    Theme.prefs(this).edit().putString("bgMode", "image").apply();
                    ok = true;
                }
            } catch (Exception ignored) { }
            js("onBg(" + ok + ")");
        }).start();
    }

    final class Bridge {
        @JavascriptInterface public String prefs() {
            JSONObject o = new JSONObject();
            try { for (Map.Entry<String, ?> e : Theme.prefs(SettingsActivity.this).getAll().entrySet()) o.put(e.getKey(), String.valueOf(e.getValue())); } catch (Exception ignored) { }
            return o.toString();
        }

        @JavascriptInterface public void set(String k, String v) {
            SharedPreferences.Editor e = Theme.prefs(SettingsActivity.this).edit();
            if (v == null) e.remove(k); else e.putString(k, v);
            e.apply();
        }

        @JavascriptInterface public void reset() {
            Theme.prefs(SettingsActivity.this).edit().clear().apply();
            new File(getFilesDir(), "bg.img").delete();
        }

        @JavascriptInterface public String presets() {
            try {
                JSONArray a = new JSONArray(Theme.PRESETS);
                for (int i = 0; i < a.length(); i++) if (a.getJSONObject(i).getString("id").equals("material")) Theme.dynamic(SettingsActivity.this, a.getJSONObject(i));
                return a.toString();
            } catch (Exception e) { return Theme.PRESETS; }
        }

        @JavascriptInterface public String state() {
            InputMethodManager m = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            boolean enabled = false;
            if (m != null) for (InputMethodInfo i : m.getEnabledInputMethodList()) if (i.getPackageName().equals(getPackageName())) enabled = true;
            String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            boolean current = cur != null && cur.startsWith(getPackageName() + "/");
            boolean blur = false;
            if (Build.VERSION.SDK_INT >= 31) {
                android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
                blur = wm != null && wm.isCrossWindowBlurEnabled();
            }
            try {
                return new JSONObject().put("enabled", enabled).put("current", current).put("blur", blur).put("sdk", Build.VERSION.SDK_INT)
                    .put("hasBg", new File(getFilesDir(), "bg.img").exists()).toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface public void openIme() { runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))); }

        @JavascriptInterface public void picker() {
            runOnUiThread(() -> {
                InputMethodManager m = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (m != null) m.showInputMethodPicker();
            });
        }

        @JavascriptInterface public void pickBg() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
                startActivityForResult(i, PICK_BG);
            });
        }

        @JavascriptInterface public String clips() { return ClipStore.get(SettingsActivity.this).sorted().toString(); }
        @JavascriptInterface public void clipDel(String id) { ClipStore.get(SettingsActivity.this).remove(Long.parseLong(id)); }
        @JavascriptInterface public void clipPin(String id) { ClipStore.get(SettingsActivity.this).pin(Long.parseLong(id)); }
        @JavascriptInterface public void clipClear() { ClipStore.get(SettingsActivity.this).clear(); }

        @JavascriptInterface public void bars(final String hex, final boolean dark) {
            runOnUiThread(() -> {
                int c = Color.parseColor(hex);
                getWindow().setStatusBarColor(c);
                getWindow().setNavigationBarColor(c);
                web.setBackgroundColor(c);
                getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            });
        }

        @JavascriptInterface public String version() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
        }
    }
}
