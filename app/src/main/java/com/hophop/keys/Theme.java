package com.hophop.keys;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Оформление клавиатуры: готовый стиль плюс пользовательские настройки поверх него. */
final class Theme {
    // Готовые стили. Цвета #AARRGGBB, rim — яркость обводки 0..255, look: flat | glass | neon | ios | minimal.
    static final String PRESETS = "["
        + "{\"id\":\"ios_light\",\"n\":\"iOS светлый\",\"bg\":\"#FFD1D4DB\",\"key\":\"#FFFFFFFF\",\"fn\":\"#FFADB2BC\",\"text\":\"#FF000000\",\"accent\":\"#FF007AFF\",\"rim\":0,\"look\":\"ios\",\"radius\":6,\"gap\":6},"
        + "{\"id\":\"ios_dark\",\"n\":\"iOS тёмный\",\"bg\":\"#FF1E1E20\",\"key\":\"#FF6B6B6E\",\"fn\":\"#FF464649\",\"text\":\"#FFFFFFFF\",\"accent\":\"#FF0A84FF\",\"rim\":0,\"look\":\"ios\",\"radius\":6,\"gap\":6},"
        + "{\"id\":\"material\",\"n\":\"Material You\",\"bg\":\"#FF1D1B20\",\"key\":\"#FF36343B\",\"fn\":\"#FF4A4458\",\"text\":\"#FFE6E0E9\",\"accent\":\"#FFD0BCFF\",\"rim\":0,\"look\":\"flat\",\"radius\":12,\"gap\":5},"
        + "{\"id\":\"glass\",\"n\":\"Liquid Glass\",\"bg\":\"#66101018\",\"key\":\"#38FFFFFF\",\"fn\":\"#24FFFFFF\",\"text\":\"#FFFFFFFF\",\"accent\":\"#FF8AB4FF\",\"rim\":110,\"look\":\"glass\",\"radius\":16,\"gap\":6},"
        + "{\"id\":\"tinted\",\"n\":\"Tinted\",\"bg\":\"#8C0C0F1A\",\"key\":\"#405B8CFF\",\"fn\":\"#285B8CFF\",\"text\":\"#FFEAF0FF\",\"accent\":\"#FF5B8CFF\",\"rim\":80,\"look\":\"glass\",\"radius\":14,\"gap\":6},"
        + "{\"id\":\"neon\",\"n\":\"Неон\",\"bg\":\"#FF07070D\",\"key\":\"#00000000\",\"fn\":\"#00000000\",\"text\":\"#FFF3EFFF\",\"accent\":\"#FF00F0FF\",\"rim\":255,\"look\":\"neon\",\"radius\":10,\"gap\":7},"
        + "{\"id\":\"minimal\",\"n\":\"Минимализм\",\"bg\":\"#FF111113\",\"key\":\"#00000000\",\"fn\":\"#00000000\",\"text\":\"#FFF2F2F2\",\"accent\":\"#FFFFFFFF\",\"rim\":0,\"look\":\"minimal\",\"radius\":8,\"gap\":4},"
        + "{\"id\":\"paper\",\"n\":\"Бумага\",\"bg\":\"#FFEDE6D8\",\"key\":\"#FFFBF8F1\",\"fn\":\"#FFDDD3C0\",\"text\":\"#FF2E2A24\",\"accent\":\"#FFC2452D\",\"rim\":40,\"look\":\"flat\",\"radius\":10,\"gap\":6},"
        + "{\"id\":\"gboard_dark\",\"n\":\"Gboard тёмный\",\"bg\":\"#FF202124\",\"key\":\"#FF3C4043\",\"fn\":\"#FF5F6368\",\"text\":\"#FFE8EAED\",\"accent\":\"#FF8AB4F8\",\"rim\":0,\"look\":\"flat\",\"radius\":7,\"gap\":6},"
        + "{\"id\":\"gboard_light\",\"n\":\"Gboard светлый\",\"bg\":\"#FFE8EAED\",\"key\":\"#FFFFFFFF\",\"fn\":\"#FFC5C9D0\",\"text\":\"#FF202124\",\"accent\":\"#FF1A73E8\",\"rim\":0,\"look\":\"flat\",\"radius\":7,\"gap\":6},"
        + "{\"id\":\"graphite\",\"n\":\"Графит\",\"bg\":\"#FF313038\",\"key\":\"#FF4F4B5C\",\"fn\":\"#FF3C3946\",\"text\":\"#FFE6E0E9\",\"accent\":\"#FFE6E0E9\",\"rim\":0,\"look\":\"flat\",\"radius\":18,\"gap\":6}"
        + "]";

    int bg, key, fn, enterKey, text, fnText, hintText, accent, rim, popup, border, press, bg2;
    String look, bgMode, id, font, keyStyle, shadow, spaceMode, spaceText, oneHand, pressAnim;
    float radius, gapH, gapV, keyHeight, fontScale, keyAlpha, bgDim, blur, imgBlur, bgAngle, borderW, hintScale, sidePad, bottomPad, oneHandW;
    boolean bold, upperLabels, numberRow, preview, hints, toolbar, langLabel, suggest;

    /** Ключи настроек внешнего вида: их сохраняют «Мои темы» и сбрасывает «Сброс оформления». */
    static final String[] LOOK_KEYS = {"style", "accent", "c_bg", "c_key", "c_fn", "c_enter", "c_text", "c_fnText", "c_hint", "c_popup", "c_border", "c_press",
        "bgMode", "bgColor", "bgColor2", "bgAngle", "bgDim", "blur", "imgBlur", "keyAlpha", "radius", "gap", "gapH", "gapV", "height", "font", "fontFace",
        "bold", "upper", "hints", "hintScale", "preview", "pressAnim", "keyStyle", "shadow", "borderW", "spaceMode", "spaceText", "toolbar", "langLabel",
        "numRow", "oneHand", "oneHandW", "sidePad", "bottomPad"};

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("kb", Context.MODE_PRIVATE); }

    static int col(String s, int def) { try { return (int) Long.parseLong(s.replace("#", ""), 16); } catch (Exception e) { return def; } }
    static float num(SharedPreferences p, String k, float def) { try { return Float.parseFloat(p.getString(k, String.valueOf(def))); } catch (Exception e) { return def; } }
    static boolean flag(SharedPreferences p, String k, boolean def) { return "1".equals(p.getString(k, def ? "1" : "0")); }

    static JSONObject preset(String id) {
        try {
            JSONArray a = new JSONArray(PRESETS);
            for (int i = 0; i < a.length(); i++) if (a.getJSONObject(i).getString("id").equals(id)) return a.getJSONObject(i);
            return a.getJSONObject(0);
        } catch (Exception e) { return new JSONObject(); }
    }

    /** Material You: цвета из обоев (Android 12+). */
    static void dynamic(Context c, JSONObject p) {
        if (Build.VERSION.SDK_INT < 31) return;
        boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        try {
            int bg = c.getColor(night ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_50);
            int key = c.getColor(night ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_10);
            int fn = c.getColor(night ? android.R.color.system_accent2_700 : android.R.color.system_accent2_100);
            int text = c.getColor(night ? android.R.color.system_neutral1_50 : android.R.color.system_neutral1_900);
            int acc = c.getColor(night ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
            p.put("bg", hex(bg)).put("key", hex(key)).put("fn", hex(fn)).put("text", hex(text)).put("accent", hex(acc));
        } catch (Exception ignored) { }
    }

    static String hex(int c) { return String.format("#%08X", c); }

    static Theme load(Context c) {
        SharedPreferences p = prefs(c);
        Theme t = new Theme();
        t.id = p.getString("style", "glass");
        JSONObject s = preset(t.id);
        if (t.id.equals("material")) dynamic(c, s);
        t.look = s.optString("look", "flat");
        t.bg = col(s.optString("bg"), 0xFF1D1B20);
        t.key = col(s.optString("key"), 0xFF36343B);
        t.fn = col(s.optString("fn"), 0xFF4A4458);
        t.text = col(s.optString("text"), 0xFFFFFFFF);
        t.accent = col(s.optString("accent"), 0xFF8AB4FF);
        t.rim = s.optInt("rim", 0);
        String acc = p.getString("accent", "");
        if (!acc.isEmpty()) {
            t.accent = col(acc, t.accent);
            if (t.id.equals("tinted")) { // Tinted перекрашивается целиком под выбранный цвет
                t.key = (0x40 << 24) | (t.accent & 0xFFFFFF);
                t.fn = (0x28 << 24) | (t.accent & 0xFFFFFF);
            }
        }
        // Ручные цвета поверх стиля: пустая строка значит «как в стиле».
        t.bg = col(p.getString("c_bg", ""), t.bg);
        t.key = col(p.getString("c_key", ""), t.key);
        t.fn = col(p.getString("c_fn", ""), t.fn);
        t.text = col(p.getString("c_text", ""), t.text);
        t.fnText = col(p.getString("c_fnText", ""), t.text);
        t.hintText = col(p.getString("c_hint", ""), mulAlpha(t.text, 0.45f));
        t.enterKey = col(p.getString("c_enter", ""), t.accent);
        t.radius = num(p, "radius", s.optInt("radius", 10));
        float gap = num(p, "gap", s.optInt("gap", 6));
        t.gapH = num(p, "gapH", gap);
        t.gapV = num(p, "gapV", gap);
        t.keyHeight = num(p, "height", 52);
        t.fontScale = num(p, "font", 1f);
        t.keyAlpha = num(p, "keyAlpha", 1f);
        t.bgDim = num(p, "bgDim", 0.35f);
        t.blur = num(p, "blur", 40);
        t.imgBlur = num(p, "imgBlur", 0);
        t.bgMode = p.getString("bgMode", "theme"); // theme | color | gradient | image | blur
        t.bg2 = col(p.getString("bgColor2", ""), 0xFF5B3CC4);
        if (t.bgMode.equals("color") || t.bgMode.equals("gradient")) t.bg = col(p.getString("bgColor", ""), t.bg | 0xFF000000);
        t.bgAngle = num(p, "bgAngle", 90);
        t.font = p.getString("fontFace", "sans-serif");
        t.bold = flag(p, "bold", false);
        t.upperLabels = flag(p, "upper", false);
        t.numberRow = flag(p, "numRow", false);
        t.preview = flag(p, "preview", true);
        t.hints = flag(p, "hints", true);
        t.hintScale = num(p, "hintScale", 1f);
        t.pressAnim = p.getString("pressAnim", "scale");
        t.keyStyle = p.getString("keyStyle", "fill"); // fill | outline | none
        t.shadow = p.getString("shadow", t.look.equals("ios") ? "3d" : "none"); // none | soft | 3d
        t.borderW = num(p, "borderW", 0);
        t.border = col(p.getString("c_border", ""), mulAlpha(t.text, 0.35f));
        t.spaceMode = p.getString("spaceMode", "lang"); // lang | custom | none
        t.spaceText = p.getString("spaceText", "");
        t.toolbar = flag(p, "toolbar", true);
        t.suggest = flag(p, "suggest", true);
        t.langLabel = flag(p, "langLabel", true);
        t.oneHand = p.getString("oneHand", "off"); // off | left | right
        t.oneHandW = num(p, "oneHandW", 0.8f);
        t.sidePad = num(p, "sidePad", 2);
        t.bottomPad = num(p, "bottomPad", 6);
        t.key = mulAlpha(t.key, t.keyAlpha);
        t.fn = mulAlpha(t.fn, t.keyAlpha);
        t.popup = col(p.getString("c_popup", ""), t.look.equals("ios") ? t.key | 0xFF000000 : blend(t.bg | 0xFF000000, t.text, 0.12f));
        t.press = col(p.getString("c_press", ""), 0);
        return t;
    }

    static int mulAlpha(int c, float k) {
        int a = Math.max(0, Math.min(255, Math.round(Color.alpha(c) * k)));
        return (a << 24) | (c & 0xFFFFFF);
    }

    static int blend(int a, int b, float k) {
        return Color.argb(255,
            Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * k),
            Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * k),
            Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k));
    }
}
