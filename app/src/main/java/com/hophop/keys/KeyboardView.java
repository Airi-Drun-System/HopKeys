package com.hophop.keys;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Вся клавиатура рисуется вручную: так любой стиль (стекло, неон, iOS) — просто другая отрисовка. */
@SuppressLint("ViewConstructor")
final class KeyboardView extends View {
    final KeyboardService svc;
    Theme t;
    String mode = "ru";
    int shift = 0; // 0 выкл, 1 одна буква, 2 Caps Lock
    long lastShiftTap;
    String enterIcon = "enter";
    Bitmap bgImage;
    List<Layouts.Key> keys = new ArrayList<>();

    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler(Looper.getMainLooper());
    private final RectF r = new RectF();
    private float toolbarH, rowH, padB;
    private String toolDown;

    private static final class Ptr {
        Layouts.Key key; float downX, downY, lastX; boolean longDone, cursor, committed;
        String alts; int altSel; RectF altBox;
    }
    private final SparseArray<Ptr> ptrs = new SparseArray<>();
    private Ptr popupPtr;

    KeyboardView(KeyboardService s) {
        super(s);
        svc = s;
        d = s.getResources().getDisplayMetrics().density;
        tp.setTextAlign(Paint.Align.CENTER);
    }

    float dp(float v) { return v * d; }

    Typeface face = Typeface.DEFAULT;
    private float kx0, kx1; // границы клавиш (в режиме одной руки они уже экрана)

    void apply(Theme theme, Bitmap img, Typeface tf) {
        t = theme; bgImage = img;
        face = tf != null ? tf : Typeface.DEFAULT;
        tp.setTypeface(Typeface.create(face, t.bold ? Typeface.BOLD : Typeface.NORMAL));
        requestLayout(); relayout(); invalidate();
    }

    String[] rows() {
        switch (mode) {
            case "en": return Layouts.EN;
            case "sym": return Layouts.SYM;
            case "sym2": return Layouts.SYM2;
            case "num": return Layouts.NUM;
            case "phone": return Layouts.PHONE;
            default: return Layouts.RU;
        }
    }

    void setMode(String m) { mode = m; relayout(); requestLayout(); invalidate(); }

    private float toolbarHeight() { return t != null && t.toolbar ? dp(44) : 0; }

    private void relayout() {
        if (t == null) return;
        toolbarH = toolbarHeight(); rowH = dp(t.keyHeight); padB = dp(t.bottomPad);
        float w = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        float side = dp(t.sidePad);
        float avail = w - side * 2;
        float x0 = side;
        if (!"off".equals(t.oneHand)) {
            avail = (w - side * 2) * Math.max(0.5f, Math.min(0.95f, t.oneHandW));
            x0 = "left".equals(t.oneHand) ? side : w - side - avail;
        }
        kx0 = x0; kx1 = x0 + avail;
        keys = Layouts.build(rows(), t.numberRow, avail, rowH, toolbarH);
        for (Layouts.Key k : keys) k.x += x0;
    }

    @Override protected void onMeasure(int wm, int hm) {
        int w = MeasureSpec.getSize(wm);
        int rows = t == null ? 4 : Layouts.rows(rows(), t.numberRow);
        int hgt = (int) (toolbarHeight() + rows * dp(t == null ? 52 : t.keyHeight) + dp(t == null ? 6 : t.bottomPad));
        setMeasuredDimension(w, hgt);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { relayout(); }

    // ---------- отрисовка ----------

    @Override protected void onDraw(Canvas c) {
        if (t == null) return;
        drawBackground(c);
        if (t.toolbar) drawToolbar(c);
        if (!"off".equals(t.oneHand)) drawHandSwitch(c);
        for (Layouts.Key k : keys) drawKey(c, k, pressed(k));
        for (int i = 0; i < ptrs.size(); i++) {
            Ptr pr = ptrs.valueAt(i);
            if (pr.alts != null) drawAlts(c, pr);
            else if (t.preview && pr.key != null && !pr.key.special() && !pr.cursor) drawPreview(c, pr.key);
        }
    }

    private boolean pressed(Layouts.Key k) {
        for (int i = 0; i < ptrs.size(); i++) if (ptrs.valueAt(i).key == k) return true;
        return false;
    }

    private void drawBackground(Canvas c) {
        int w = getWidth(), hh = getHeight();
        if ("image".equals(t.bgMode) && bgImage != null) {
            float s = Math.max((float) w / bgImage.getWidth(), (float) hh / bgImage.getHeight());
            float bw = bgImage.getWidth() * s, bh = bgImage.getHeight() * s;
            p.setFilterBitmap(true);
            c.drawBitmap(bgImage, null, new RectF((w - bw) / 2, (hh - bh) / 2, (w + bw) / 2, (hh + bh) / 2), p);
            p.setColor(Color.argb(Math.round(255 * t.bgDim), 0, 0, 0));
            c.drawRect(0, 0, w, hh, p);
        } else if ("blur".equals(t.bgMode)) {
            p.setColor(Theme.mulAlpha(t.bg | 0xFF000000, 0.35f));
            c.drawRect(0, 0, w, hh, p);
        } else if ("gradient".equals(t.bgMode)) {
            double a = Math.toRadians(t.bgAngle);
            float cx = w / 2f, cy = hh / 2f, rr = (float) Math.hypot(w, hh) / 2;
            float dx = (float) Math.cos(a) * rr, dy = (float) Math.sin(a) * rr;
            p.setShader(new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, t.bg | 0xFF000000, t.bg2 | 0xFF000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, hh, p);
            p.setShader(null);
        } else {
            p.setColor(t.bg);
            c.drawRect(0, 0, w, hh, p);
        }
    }

    // Порядок кнопок верхней панели: слева буфер и вставка, справа настройки и «скрыть».
    private static final String[] TOOLS_L = {"clip", "paste"};
    private static final String[] TOOLS_R = {"down", "gear"};

    private void drawToolbar(Canvas c) {
        int col = Theme.mulAlpha(t.fnText, 0.8f);
        float s = dp(22), cy = toolbarH / 2;
        for (int i = 0; i < TOOLS_L.length; i++) {
            float cx = dp(26) + i * dp(48);
            if (TOOLS_L[i].equals(toolDown)) { p.setColor(Theme.mulAlpha(t.text, 0.12f)); c.drawCircle(cx, cy, dp(18), p); }
            Icons.draw(c, TOOLS_L[i], cx, cy, s, col, p);
        }
        for (int i = 0; i < TOOLS_R.length; i++) {
            float cx = getWidth() - dp(26) - i * dp(48);
            if (TOOLS_R[i].equals(toolDown)) { p.setColor(Theme.mulAlpha(t.text, 0.12f)); c.drawCircle(cx, cy, dp(18), p); }
            Icons.draw(c, TOOLS_R[i], cx, cy, s, col, p);
        }
        if (!t.langLabel) return;
        tp.setColor(t.hintText);
        tp.setTextSize(dp(12));
        String lang = mode.equals("en") ? "EN" : mode.equals("ru") ? "RU" : "";
        c.drawText(lang, getWidth() / 2f, cy + dp(4), tp);
    }

    /** Режим одной руки: в пустой полосе стрелка, нажатие переносит клавиатуру на другую сторону. */
    private void drawHandSwitch(Canvas c) {
        boolean left = "left".equals(t.oneHand);
        float cx = left ? (kx1 + getWidth()) / 2f : kx0 / 2f;
        float cy = toolbarH + (getHeight() - toolbarH - padB) / 2f;
        Icons.draw(c, left ? "next" : "back", cx, cy, dp(24), Theme.mulAlpha(t.text, 0.6f), p);
    }

    private void keyRect(Layouts.Key k) {
        float gx = dp(t.gapH) / 2, gy = dp(t.gapV) / 2;
        r.set(k.x + gx, k.y + gy, k.x + k.w - gx, k.y + k.h - gy);
    }

    private void drawKey(Canvas c, Layouts.Key k, boolean down) {
        keyRect(k);
        float rad = dp(t.radius);
        boolean enter = k.code.equals("{enter}");
        int fill = enter ? t.enterKey : k.fn ? t.fn : t.key;
        if (enter && t.look.equals("glass")) fill = Theme.mulAlpha(t.enterKey, 0.55f);
        if (enter && (t.look.equals("neon") || t.look.equals("minimal"))) fill = 0;
        if (down) {
            if (t.press != 0) fill = t.press;
            else fill = Color.alpha(fill) < 40 ? Theme.mulAlpha(t.text, 0.18f) : Theme.blend(fill, t.text, 0.18f) & 0x00FFFFFF | (Math.max(Color.alpha(fill), 120) << 24);
        }
        c.save();
        if (down && "scale".equals(t.pressAnim)) c.scale(0.93f, 0.93f, r.centerX(), r.centerY());
        if ("none".equals(t.keyStyle)) {
            if (down) { p.setColor(fill); c.drawRoundRect(r, rad, rad, p); }
        } else if ("outline".equals(t.keyStyle)) {
            if (down) { p.setColor(fill); c.drawRoundRect(r, rad, rad, p); }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(Math.max(1.2f, t.borderW)));
            p.setColor(enter ? t.enterKey : t.borderW > 0 ? t.border : Theme.mulAlpha(t.text, 0.5f));
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            if ("3d".equals(t.shadow)) {
                p.setColor(Theme.blend(t.bg | 0xFF000000, 0xFF000000, 0.35f));
                c.drawRoundRect(r.left, r.top + dp(1.5f), r.right, r.bottom + dp(1.5f), rad, rad, p);
            } else if ("soft".equals(t.shadow)) {
                p.setColor(0x26000000);
                c.drawRoundRect(r.left - dp(0.5f), r.top + dp(2), r.right + dp(0.5f), r.bottom + dp(3), rad + dp(1), rad + dp(1), p);
            }
            switch (t.look) {
                case "glass": {
                    p.setColor(fill); c.drawRoundRect(r, rad, rad, p);
                    p.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.6f, Color.argb(t.rim / 2, 255, 255, 255), 0, Shader.TileMode.CLAMP));
                    c.drawRoundRect(r, rad, rad, p);
                    p.setShader(null);
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1));
                    p.setShader(new LinearGradient(0, r.top, 0, r.bottom, Color.argb(t.rim, 255, 255, 255), Color.argb(t.rim / 4, 255, 255, 255), Shader.TileMode.CLAMP));
                    c.drawRoundRect(r, rad, rad, p);
                    p.setShader(null); p.setStyle(Paint.Style.FILL);
                    break;
                }
                case "neon": {
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(Theme.mulAlpha(t.accent, 0.25f)); p.setStrokeWidth(dp(5)); c.drawRoundRect(r, rad, rad, p);
                    p.setColor(down ? t.text : t.accent); p.setStrokeWidth(dp(1.4f)); c.drawRoundRect(r, rad, rad, p);
                    p.setStyle(Paint.Style.FILL);
                    if (down) { p.setColor(Theme.mulAlpha(t.accent, 0.25f)); c.drawRoundRect(r, rad, rad, p); }
                    break;
                }
                case "minimal": {
                    if (down) { p.setColor(Theme.mulAlpha(t.text, 0.14f)); c.drawRoundRect(r, rad, rad, p); }
                    if (enter) { p.setColor(t.enterKey); c.drawCircle(r.centerX(), r.centerY(), Math.min(r.width(), r.height()) * 0.42f, p); }
                    break;
                }
                default: {
                    p.setColor(fill); c.drawRoundRect(r, rad, rad, p);
                    if (t.rim > 0) {
                        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1));
                        p.setColor(Theme.mulAlpha(t.text, t.rim / 255f)); c.drawRoundRect(r, rad, rad, p);
                        p.setStyle(Paint.Style.FILL);
                    }
                }
            }
            if (t.borderW > 0) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(t.borderW)); p.setColor(t.border);
                c.drawRoundRect(r, rad, rad, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
        drawLabel(c, k, enter);
        c.restore();
    }

    private int labelColor(boolean enter, boolean fn) {
        if (!enter) return fn ? t.fnText : t.text;
        if (t.look.equals("neon") || "outline".equals(t.keyStyle) || "none".equals(t.keyStyle)) return t.enterKey | 0xFF000000;
        if (t.look.equals("minimal")) return t.bg | 0xFF000000;
        int a = t.enterKey;
        double lum = (0.299 * Color.red(a) + 0.587 * Color.green(a) + 0.114 * Color.blue(a)) / 255;
        return lum > 0.6 ? 0xFF111111 : 0xFFFFFFFF;
    }

    private void drawLabel(Canvas c, Layouts.Key k, boolean enter) {
        int col = labelColor(enter, k.fn);
        float cx = r.centerX(), cy = r.centerY();
        float icon = dp(22) * Math.min(1.2f, t.fontScale);
        switch (k.code) {
            case "{shift}": Icons.draw(c, shift == 2 ? "caps" : shift == 1 ? "shiftOn" : "shift", cx, cy, icon, col, p); return;
            case "{del}": Icons.draw(c, "del", cx, cy, icon, col, p); return;
            case "{enter}": Icons.draw(c, enterIcon, cx, cy, icon, col, p); return;
            case "{lang}": Icons.draw(c, "globe", cx, cy, icon * 0.9f, col, p); return;
            default: break;
        }
        String label;
        float size;
        switch (k.code) {
            case "{space}":
                label = "none".equals(t.spaceMode) ? "" : "custom".equals(t.spaceMode) ? t.spaceText
                        : mode.equals("en") ? "English" : mode.equals("ru") ? "Русский" : "";
                size = 13; col = Theme.mulAlpha(t.fnText, 0.6f); break;
            case "{sym}": label = "?123"; size = 15; break;
            case "{sym2}": label = "=\\<"; size = 15; break;
            case "{abc}": label = mode.equals("num") || mode.equals("phone") ? "АБВ" : (svc.lastLetters.equals("en") ? "ABC" : "АБВ"); size = 15; break;
            default:
                label = k.code;
                if (shift > 0 || t.upperLabels) label = label.toUpperCase();
                size = mode.equals("num") || mode.equals("phone") ? 26 : 23;
        }
        tp.setColor(col);
        tp.setTextSize(dp(size) * t.fontScale);
        Rect b = new Rect();
        tp.getTextBounds("Ж", 0, 1, b);
        c.drawText(label, cx, cy + b.height() / 2f, tp);
        if (t.hints && !k.special() && !k.alts().isEmpty()) {
            tp.setTextSize(dp(10) * t.fontScale * t.hintScale);
            tp.setColor(t.hintText);
            c.drawText(k.alts().substring(0, 1), r.right - dp(8), r.top + dp(13) * Math.max(0.8f, t.hintScale), tp);
        }
    }

    private void drawPreview(Canvas c, Layouts.Key k) {
        keyRect(k);
        float w = Math.max(r.width() + dp(10), dp(44)), hh = rowH * 1.15f;
        float left = Math.max(dp(2), Math.min(getWidth() - w - dp(2), r.centerX() - w / 2));
        float top = Math.max(0, r.top - hh - dp(4));
        RectF b = new RectF(left, top, left + w, top + hh);
        p.setColor(0x33000000);
        c.drawRoundRect(b.left, b.top + dp(2), b.right, b.bottom + dp(2), dp(12), dp(12), p);
        p.setColor(t.popup);
        c.drawRoundRect(b, dp(12), dp(12), p);
        tp.setColor(t.text);
        tp.setTextSize(dp(30) * t.fontScale);
        String l = k.code; if (shift > 0 || t.upperLabels) l = l.toUpperCase();
        c.drawText(l, b.centerX(), b.centerY() + dp(10), tp);
    }

    private void drawAlts(Canvas c, Ptr pr) {
        RectF b = pr.altBox;
        p.setColor(0x40000000);
        c.drawRoundRect(b.left, b.top + dp(3), b.right, b.bottom + dp(3), dp(12), dp(12), p);
        p.setColor(t.popup);
        c.drawRoundRect(b, dp(12), dp(12), p);
        float cw = b.width() / pr.alts.length();
        tp.setTextSize(dp(22) * t.fontScale);
        for (int i = 0; i < pr.alts.length(); i++) {
            float cx = b.left + cw * i + cw / 2;
            if (i == pr.altSel) {
                p.setColor(t.accent);
                c.drawRoundRect(b.left + cw * i + dp(3), b.top + dp(3), b.left + cw * (i + 1) - dp(3), b.bottom - dp(3), dp(9), dp(9), p);
                tp.setColor(labelColor(true, false));
            } else tp.setColor(t.text);
            String s = pr.alts.substring(i, i + 1);
            if (shift > 0) s = s.toUpperCase();
            c.drawText(s, cx, b.centerY() + dp(8), tp);
        }
    }

    // ---------- касания ----------

    private Layouts.Key keyAt(float x, float y) {
        Layouts.Key best = null; float bd = Float.MAX_VALUE;
        for (Layouts.Key k : keys) {
            if (x >= k.x && x < k.x + k.w && y >= k.y && y < k.y + k.h) return k;
            float dx = Math.max(Math.max(k.x - x, 0), x - (k.x + k.w)), dy = Math.max(Math.max(k.y - y, 0), y - (k.y + k.h));
            float dd = dx * dx + dy * dy;
            if (dd < bd) { bd = dd; best = k; }
        }
        return y >= toolbarH ? best : null;
    }

    private String toolAt(float x, float y) {
        if (y > toolbarH) return null;
        for (int i = 0; i < TOOLS_L.length; i++) if (Math.abs(x - (dp(26) + i * dp(48))) < dp(24)) return TOOLS_L[i];
        for (int i = 0; i < TOOLS_R.length; i++) if (Math.abs(x - (getWidth() - dp(26) - i * dp(48))) < dp(24)) return TOOLS_R[i];
        return null;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked(), idx = e.getActionIndex(), id = e.getPointerId(idx);
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = e.getX(idx), y = e.getY(idx);
                if (y < toolbarH) { toolDown = toolAt(x, y); invalidate(); return true; }
                // Быстрый набор: предыдущая буква засчитывается, как только нажата следующая.
                for (int i = 0; i < ptrs.size(); i++) {
                    Ptr o = ptrs.valueAt(i);
                    if (!o.committed && o.alts == null && !o.cursor && o.key != null && !o.key.special()) { press(o.key); o.committed = true; }
                }
                if (!"off".equals(t.oneHand) && (x < kx0 || x > kx1)) { svc.flipHand(); return true; }
                Layouts.Key k = keyAt(x, y);
                if (k == null) return true;
                Ptr pr = new Ptr();
                pr.key = k; pr.downX = pr.lastX = x; pr.downY = y;
                ptrs.put(id, pr);
                svc.feedback(k.code);
                if (k.code.equals("{del}")) { svc.deleteOne(); pr.committed = true; scheduleRepeat(pr); }
                else if (k.code.equals("{shift}")) { tapShift(); pr.committed = true; }
                else scheduleLong(pr);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    Ptr pr = ptrs.get(e.getPointerId(i));
                    if (pr == null) continue;
                    float x = e.getX(i), y = e.getY(i);
                    if (pr.alts != null) { pr.altSel = Math.max(0, Math.min(pr.alts.length() - 1, (int) ((x - pr.altBox.left) / (pr.altBox.width() / pr.alts.length())))); continue; }
                    if (pr.key.code.equals("{space}") && svc.swipeCursor() && (pr.cursor || Math.abs(x - pr.downX) > dp(16))) {
                        if (!pr.cursor) { pr.cursor = true; pr.lastX = x; h.removeCallbacksAndMessages(pr); }
                        float step = dp(11);
                        while (x - pr.lastX > step) { svc.moveCursor(1); pr.lastX += step; }
                        while (pr.lastX - x > step) { svc.moveCursor(-1); pr.lastX -= step; }
                        continue;
                    }
                    if (pr.key.code.equals("{del}") && !pr.cursor && pr.downX - x > dp(40)) { pr.cursor = true; h.removeCallbacksAndMessages(pr); svc.deleteWord(); continue; }
                    if (pr.committed || pr.longDone) continue;
                    Layouts.Key k = keyAt(x, y);
                    if (k != null && k != pr.key && !pr.key.special() && !k.special()) { pr.key = k; scheduleLong(pr); }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (toolDown != null) {
                    String tool = toolAt(e.getX(idx), e.getY(idx));
                    if (toolDown.equals(tool)) svc.tool(tool);
                    toolDown = null; invalidate();
                    if (ptrs.get(id) == null) return true;
                }
                Ptr pr = ptrs.get(id);
                ptrs.remove(id);
                if (pr == null) return true;
                h.removeCallbacksAndMessages(pr);
                if (pr.alts != null) svc.commitChar(pr.alts.substring(pr.altSel, pr.altSel + 1), shift > 0);
                else if (!pr.committed && !pr.cursor && !pr.longDone) press(pr.key);
                if (pr.alts != null) afterLetter();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < ptrs.size(); i++) h.removeCallbacksAndMessages(ptrs.valueAt(i));
                ptrs.clear(); toolDown = null; invalidate();
                return true;
            default: return true;
        }
    }

    private void scheduleLong(final Ptr pr) {
        h.removeCallbacksAndMessages(pr);
        h.postAtTime(() -> onLong(pr), pr, android.os.SystemClock.uptimeMillis() + svc.longDelay());
    }

    private void scheduleRepeat(final Ptr pr) {
        h.postAtTime(new Runnable() {
            int n = 0;
            @Override public void run() {
                if (ptrs.indexOfValue(pr) < 0) return;
                svc.deleteOne(); n++;
                pr.longDone = true;
                if (pr.cursor) return;
                h.postAtTime(this, pr, android.os.SystemClock.uptimeMillis() + (n > 15 ? svc.repeatMs() / 2 : svc.repeatMs()));
            }
        }, pr, android.os.SystemClock.uptimeMillis() + 380);
    }

    private void onLong(Ptr pr) {
        if (ptrs.indexOfValue(pr) < 0 || pr.committed) return;
        String code = pr.key.code;
        if (code.equals("{lang}")) { pr.longDone = true; svc.showPicker(); return; }
        String alts = pr.key.alts();
        if (pr.key.special() || alts.isEmpty()) return;
        pr.longDone = true;
        pr.alts = alts;
        keyRect(pr.key);
        float cw = Math.max(r.width(), dp(38)), w = cw * alts.length(), hh = rowH;
        float left = Math.max(dp(2), Math.min(getWidth() - w - dp(2), r.centerX() - cw / 2));
        float top = Math.max(0, r.top - hh - dp(6));
        pr.altBox = new RectF(left, top, left + w, top + hh);
        pr.altSel = 0;
        svc.feedback("long");
        invalidate();
    }

    private void press(Layouts.Key k) {
        switch (k.code) {
            case "{space}": svc.space(); break;
            case "{enter}": svc.enter(); break;
            case "{sym}": setMode("sym"); break;
            case "{sym2}": setMode("sym2"); break;
            case "{abc}": setMode(svc.lastLetters); break;
            case "{lang}": svc.switchLang(); break;
            default:
                if (!k.special()) { svc.commitChar(k.code, shift > 0); afterLetter(); }
        }
    }

    private void afterLetter() {
        if (shift == 1) { shift = 0; invalidate(); }
        svc.autoCaps();
    }

    private void tapShift() {
        long now = System.currentTimeMillis();
        if (shift == 0) shift = 1;
        else if (shift == 1 && now - lastShiftTap < 350) shift = 2;
        else shift = 0;
        lastShiftTap = now;
        invalidate();
    }

    void setAutoShift(boolean on) {
        if (shift == 2) return;
        int n = on ? 1 : 0;
        if (n != shift) { shift = n; invalidate(); }
    }
}
