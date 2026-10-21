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
    private static final int PICK_BG = 41, PICK_FONT = 42;
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
        if (rc != RESULT_OK || d == null || d.getData() == null) return;
        final Uri u = d.getData();
        if (rq == PICK_FONT) { saveFont(u); return; }
        if (rq != PICK_BG) return;
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

    /** Свой шрифт: копируем .ttf/.otf к себе и проверяем, что Android его читает. */
    private void saveFont(final Uri u) {
        new Thread(() -> {
            boolean ok = false;
            File tmp = new File(getFilesDir(), "font.tmp"), dst = new File(getFilesDir(), "font.ttf");
            try (InputStream in = getContentResolver().openInputStream(u); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] b = new byte[8192]; int n; long total = 0;
                while ((n = in.read(b)) > 0) { out.write(b, 0, n); total += n; if (total > 8 * 1024 * 1024) throw new Exception("big"); }
            } catch (Exception e) { tmp.delete(); }
            try {
                if (tmp.exists()) {
                    android.graphics.Typeface tf = android.graphics.Typeface.createFromFile(tmp);
                    if (tf != null && tmp.renameTo(dst)) { Theme.prefs(this).edit().putString("fontFace", "custom").apply(); ok = true; }
                }
            } catch (Exception ignored) { }
            tmp.delete();
            js("onFont(" + ok + ")");
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
                    .put("hasBg", new File(getFilesDir(), "bg.img").exists()).put("hasFont", new File(getFilesDir(), "font.ttf").exists()).toString();
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

        @JavascriptInterface public void pickFont() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "font/*"});
                startActivityForResult(i, PICK_FONT);
            });
        }

        /** Шрифт для превью на странице настроек. */
        @JavascriptInterface public String fontData() {
            File f = new File(getFilesDir(), "font.ttf");
            if (!f.exists()) return "";
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()]; int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
                return "data:font/ttf;base64," + android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface public void resetKeys(String csv) {
            SharedPreferences.Editor e = Theme.prefs(SettingsActivity.this).edit();
            for (String k : csv.split(",")) if (!k.isEmpty()) e.remove(k);
            e.apply();
        }

        @JavascriptInterface public String lookKeys() { return new JSONArray(java.util.Arrays.asList(Theme.LOOK_KEYS)).toString(); }

        @JavascriptInterface public boolean copy(String text) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) return false;
            cm.setPrimaryClip(android.content.ClipData.newPlainText("HopKeys", text));
            return true;
        }

        @JavascriptInterface public String paste() {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            try { return String.valueOf(cm.getPrimaryClip().getItemAt(0).coerceToText(SettingsActivity.this)); } catch (Exception e) { return ""; }
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
