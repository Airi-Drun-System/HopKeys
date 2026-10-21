package com.hophop.keys;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class KeyboardService extends InputMethodService {
    private KeyboardView kv;
    private FrameLayout root;
    private LinearLayout clipPanel;
    private ClipStore clips;
    private Theme theme;
    private ClipboardManager cm;
    private boolean passwordField;
    // Поле внутри самого HopKeys: синхронные вопросы к нему из клавиатуры зависают (один поток), поэтому их не задаём.
    private boolean selfEditor;
    private long lastSpace;
    private int clearArmed;
    String lastLetters = "ru";

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::grabClip;

    @Override public void onCreate() {
        super.onCreate();
        clips = ClipStore.get(this);
        lastLetters = Theme.prefs(this).getString("lang", "ru");
        cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.addPrimaryClipChangedListener(clipListener);
    }

    @Override public void onDestroy() {
        if (cm != null) cm.removePrimaryClipChangedListener(clipListener);
        super.onDestroy();
    }

    /** В историю попадает только текст. Пароли и всё, что Android пометил секретным, пропускаем. */
    private void grabClip() {
        try {
            if (!Theme.flag(Theme.prefs(this), "clipOn", true) || passwordField || cm == null || !cm.hasPrimaryClip()) return;
            ClipData c = cm.getPrimaryClip();
            if (c == null || c.getItemCount() == 0) return;
            ClipDescription desc = c.getDescription();
            if (desc != null && desc.getExtras() != null && desc.getExtras().getBoolean("android.content.extra.IS_SENSITIVE")) return;
            CharSequence s = c.getItemAt(0).getText();
            if (s != null) clips.add(s.toString());
        } catch (Exception ignored) { }
    }

    @Override public View onCreateInputView() {
        kv = new KeyboardView(this);
        root = new FrameLayout(this);
        root.addView(kv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        theme = Theme.load(this);
        kv.apply(theme, background(), typeface());
        closeClips();
        selfEditor = getPackageName().equals(info.packageName);
        int cls = info.inputType & InputType.TYPE_MASK_CLASS, var = info.inputType & InputType.TYPE_MASK_VARIATION;
        passwordField = (cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME) kv.setMode("num");
        else if (cls == InputType.TYPE_CLASS_PHONE) kv.setMode("phone");
        else kv.setMode(lastLetters);
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean multi = (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        kv.enterIcon = multi ? "enter" : action == EditorInfo.IME_ACTION_SEARCH ? "search" : action == EditorInfo.IME_ACTION_SEND ? "send"
                : action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_NEXT ? "next" : action == EditorInfo.IME_ACTION_DONE ? "done" : "enter";
        kv.shift = 0;
        autoCaps();
        styleWindow();
        grabClip();
    }

    @Override public void onFinishInputView(boolean finishing) {
        super.onFinishInputView(finishing);
        closeClips();
    }

    /** Окно клавиатуры прозрачное, чтобы стекло и размытие было видно. Настоящее размытие — Android 12+, если телефон его поддерживает. */
    private void styleWindow() {
        try {
            Window w = getWindow().getWindow();
            if (w == null) return;
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            boolean blur = "blur".equals(theme.bgMode);
            if (Build.VERSION.SDK_INT >= 31) {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                boolean can = wm != null && wm.isCrossWindowBlurEnabled();
                w.setBackgroundBlurRadius(blur && can ? (int) (theme.blur * getResources().getDisplayMetrics().density) : 0);
            }
            int nav = "theme".equals(theme.bgMode) ? (theme.bg | 0xFF000000) : 0xFF000000;
            if ("theme".equals(theme.bgMode) && Color.alpha(theme.bg) < 255) nav = Theme.blend(0xFF000000, theme.bg | 0xFF000000, Color.alpha(theme.bg) / 255f);
            w.setNavigationBarColor(nav);
        } catch (Exception ignored) { }
    }

    /** Фото фона; размытие делаем уменьшением и обратным растягиванием — дёшево и выглядит мягко. */
    private Bitmap background() {
        if (!"image".equals(theme.bgMode)) return null;
        File f = new File(getFilesDir(), "bg.img");
        if (!f.exists()) return null;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (b == null || theme.imgBlur < 1) return b;
        float k = 1f / (1 + theme.imgBlur);
        Bitmap small = Bitmap.createScaledBitmap(b, Math.max(4, (int) (b.getWidth() * k)), Math.max(4, (int) (b.getHeight() * k)), true);
        return Bitmap.createScaledBitmap(small, b.getWidth(), b.getHeight(), true);
    }

    private android.graphics.Typeface typeface() {
        try {
            if ("custom".equals(theme.font)) {
                File f = new File(getFilesDir(), "font.ttf");
                if (f.exists()) return android.graphics.Typeface.createFromFile(f);
            }
            return android.graphics.Typeface.create(theme.font, android.graphics.Typeface.NORMAL);
        } catch (Exception e) { return android.graphics.Typeface.DEFAULT; }
    }

    void flipHand() {
        String n = "left".equals(theme.oneHand) ? "right" : "left";
        Theme.prefs(this).edit().putString("oneHand", n).apply();
        theme = Theme.load(this);
        kv.apply(theme, background(), typeface());
    }

    /** Свайп влево по «удалить»: стереть слово перед курсором. */
    void deleteWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (selfEditor) { for (int i = 0; i < 6; i++) ic.deleteSurroundingText(1, 0); return; }
        CharSequence b = ic.getTextBeforeCursor(64, 0);
        if (b == null || b.length() == 0) return;
        int i = b.length();
        while (i > 0 && Character.isWhitespace(b.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(b.charAt(i - 1))) i--;
        ic.deleteSurroundingText(b.length() - i, 0);
        feedback("long");
        autoCaps();
    }

    long repeatMs() { return (long) Theme.num(Theme.prefs(this), "repeatMs", 55); }

    // ---------- ввод ----------

    void commitChar(String s, boolean upper) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.commitText(upper ? s.toUpperCase() : s, 1);
    }

    void deleteOne() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = selfEditor ? null : ic.getSelectedText(0);
        if (!TextUtils.isEmpty(sel)) ic.commitText("", 1);
        else if (Build.VERSION.SDK_INT >= 24) ic.deleteSurroundingTextInCodePoints(1, 0);
        else ic.deleteSurroundingText(1, 0);
        autoCaps();
    }

    void space() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long now = System.currentTimeMillis();
        CharSequence before = selfEditor ? null : ic.getTextBeforeCursor(2, 0);
        if (Theme.flag(Theme.prefs(this), "dotSpace", true) && now - lastSpace < 450 && before != null && before.length() == 2
                && before.charAt(1) == ' ' && Character.isLetterOrDigit(before.charAt(0))) {
            ic.deleteSurroundingText(1, 0);
            ic.commitText(". ", 1);
            lastSpace = 0;
        } else {
            ic.commitText(" ", 1);
            lastSpace = now;
        }
        autoCaps();
    }

    void enter() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo info = getCurrentInputEditorInfo();
        if (ic == null || info == null) return;
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean multi = (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        if (multi || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) ic.commitText("\n", 1);
        else ic.performEditorAction(action);
        autoCaps();
    }

    void moveCursor(int dir) {
        sendDownUpKeyEvents(dir > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
        feedback("tick");
    }

    void switchLang() {
        lastLetters = lastLetters.equals("ru") ? "en" : "ru";
        Theme.prefs(this).edit().putString("lang", lastLetters).apply();
        kv.setMode(lastLetters);
        autoCaps();
    }

    void showPicker() {
        InputMethodManager m = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (m != null) m.showInputMethodPicker();
    }

    void autoCaps() {
        if (kv == null || !(kv.mode.equals("ru") || kv.mode.equals("en"))) return;
        EditorInfo info = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        if (info == null || ic == null || !Theme.flag(Theme.prefs(this), "autoCap", true)) { kv.setAutoShift(false); return; }
        int cls = info.inputType & InputType.TYPE_MASK_CLASS, var = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (cls != InputType.TYPE_CLASS_TEXT || passwordField || var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_URI || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) { kv.setAutoShift(false); return; }
        if (selfEditor) { kv.setAutoShift(false); return; }
        CharSequence b = ic.getTextBeforeCursor(3, 0);
        boolean start = b == null || b.length() == 0 || b.toString().matches("(?s).*([.!?…]\\s+|\\n)$");
        kv.setAutoShift(start);
    }

    long longDelay() { return (long) Theme.num(Theme.prefs(this), "longMs", 350); }
    boolean swipeCursor() { return Theme.flag(Theme.prefs(this), "swipe", true); }

    /** Отдача: вибрация и звук по настройкам. */
    void feedback(String code) {
        SharedPreferences p = Theme.prefs(this);
        float vib = Theme.num(p, "vib", 0.4f), snd = Theme.num(p, "sound", 0f);
        if (vib > 0) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                int base = (int) Theme.num(p, "vibMs", 12);
                int ms = code.equals("long") ? base * 2 : code.equals("tick") ? Math.max(3, base / 2) : base;
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, Math.max(1, Math.min(255, (int) (vib * 255)))));
                else v.vibrate(ms);
            }
        }
        if (snd > 0 && !code.equals("tick")) {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                int fx = code.equals("{del}") ? AudioManager.FX_KEYPRESS_DELETE : code.equals("{space}") ? AudioManager.FX_KEYPRESS_SPACEBAR
                        : code.equals("{enter}") ? AudioManager.FX_KEYPRESS_RETURN : AudioManager.FX_KEYPRESS_STANDARD;
                am.playSoundEffect(fx, snd);
            }
        }
    }

    void tool(String name) {
        switch (name) {
            case "clip": openClips(); break;
            case "paste": {
                JSONArray a = clips.sorted();
                String last = null;
                try { for (int i = 0; i < a.length(); i++) if (!a.getJSONObject(i).optBoolean("pin")) { last = a.getJSONObject(i).getString("text"); break; } } catch (Exception ignored) { }
                if (last == null && a.length() > 0) try { last = a.getJSONObject(0).getString("text"); } catch (Exception ignored) { }
                if (last != null) commitChar(last, false);
                break;
            }
            case "gear": {
                Intent i = new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                break;
            }
            case "down": requestHideSelf(0); break;
            default: break;
        }
    }

    // ---------- панель буфера обмена ----------

    private int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    private void closeClips() {
        if (clipPanel != null && root != null) { root.removeView(clipPanel); clipPanel = null; }
        clearArmed = 0;
    }

    private void openClips() {
        closeClips();
        int h = kv.getHeight();
        clipPanel = new LinearLayout(this);
        clipPanel.setOrientation(LinearLayout.VERTICAL);
        clipPanel.setBackgroundColor(theme.bg | 0xFF000000);
        if (!"theme".equals(theme.bgMode) || Color.alpha(theme.bg) < 255) clipPanel.setBackgroundColor(Theme.blend(0xFF0E0E12, theme.bg | 0xFF000000, 0.5f));
        int text = theme.text | 0xFF000000;
        if (Color.alpha(theme.bg) < 200) text = 0xFFFFFFFF;

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Icons.IconView back = new Icons.IconView(this, "back", text);
        back.setOnClickListener(v -> closeClips());
        head.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));
        TextView title = new TextView(this);
        title.setText("Буфер обмена");
        title.setTextColor(text); title.setTextSize(16); title.setTypeface(null, android.graphics.Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView clear = new TextView(this);
        clear.setText("Очистить"); clear.setTextColor(theme.accent | 0xFF000000); clear.setTextSize(14);
        clear.setPadding(dp(16), dp(10), dp(16), dp(10));
        clear.setOnClickListener(v -> {
            if (clearArmed == 0) { clearArmed = 1; clear.setText("Точно? Нажми ещё раз"); return; }
            clips.clear(); openClips();
        });
        head.addView(clear);
        clipPanel.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), 0, dp(8), dp(8));
        JSONArray a = clips.sorted();
        if (a.length() == 0) {
            TextView e = new TextView(this);
            e.setText("Пока пусто. Скопируй любой текст, и он появится здесь. Закреплённые записи не удаляются при очистке.");
            e.setTextColor(Theme.mulAlpha(text, 0.6f)); e.setTextSize(14); e.setPadding(dp(12), dp(16), dp(12), dp(16));
            list.addView(e);
        }
        for (int i = 0; i < a.length(); i++) {
            try {
                JSONObject o = a.getJSONObject(i);
                final long id = o.getLong("id");
                final String s = o.getString("text");
                boolean pin = o.optBoolean("pin");
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                GradientDrawable bgd = new GradientDrawable();
                bgd.setCornerRadius(dp(Math.min(theme.radius + 4, 20)));
                bgd.setColor(Color.alpha(theme.key) < 30 ? Theme.mulAlpha(text, 0.08f) : (theme.key | 0xFF000000));
                row.setBackground(bgd);
                TextView tv = new TextView(this);
                tv.setText(s.replace('\n', ' '));
                tv.setMaxLines(2); tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setTextColor(text); tv.setTextSize(14);
                tv.setPadding(dp(14), dp(10), dp(6), dp(10));
                tv.setOnClickListener(v -> { commitChar(s, false); closeClips(); });
                row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                Icons.IconView pv = new Icons.IconView(this, pin ? "pinOn" : "pin", pin ? (theme.accent | 0xFF000000) : Theme.mulAlpha(text, 0.6f));
                pv.setOnClickListener(v -> { clips.pin(id); openClips(); });
                row.addView(pv, new LinearLayout.LayoutParams(dp(40), dp(40)));
                Icons.IconView dv = new Icons.IconView(this, "trash", Theme.mulAlpha(text, 0.6f));
                dv.setOnClickListener(v -> { clips.remove(id); openClips(); });
                row.addView(dv, new LinearLayout.LayoutParams(dp(40), dp(40)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(6);
                list.addView(row, lp);
            } catch (Exception ignored) { }
        }
        sv.addView(list);
        clipPanel.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(clipPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
    }
}
