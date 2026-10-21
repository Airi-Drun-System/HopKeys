package com.hophop.keys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Простые значки линиями: рисуются в квадрате 24x24 и масштабируются под нужный размер. */
final class Icons {
    static void draw(Canvas c, String name, float cx, float cy, float size, int color, Paint p) {
        float k = size / 24f;
        c.save();
        c.translate(cx - 12 * k, cy - 12 * k);
        c.scale(k, k);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        Path s = new Path();
        switch (name) {
            case "shift": s.moveTo(12, 4); s.lineTo(4, 12); s.lineTo(8, 12); s.lineTo(8, 19); s.lineTo(16, 19); s.lineTo(16, 12); s.lineTo(20, 12); s.close(); break;
            case "shiftOn": p.setStyle(Paint.Style.FILL_AND_STROKE); s.moveTo(12, 4); s.lineTo(4, 12); s.lineTo(8, 12); s.lineTo(8, 19); s.lineTo(16, 19); s.lineTo(16, 12); s.lineTo(20, 12); s.close(); break;
            case "caps": p.setStyle(Paint.Style.FILL_AND_STROKE); s.moveTo(12, 3); s.lineTo(4, 11); s.lineTo(8, 11); s.lineTo(8, 16); s.lineTo(16, 16); s.lineTo(16, 11); s.lineTo(20, 11); s.close(); s.addRect(8, 19, 16, 21, Path.Direction.CW); break;
            case "del": s.moveTo(9, 6); s.lineTo(20, 6); s.lineTo(20, 18); s.lineTo(9, 18); s.lineTo(3, 12); s.close(); s.moveTo(12, 9.5f); s.lineTo(17, 14.5f); s.moveTo(17, 9.5f); s.lineTo(12, 14.5f); break;
            case "enter": s.moveTo(19, 5); s.lineTo(19, 13); s.lineTo(6, 13); s.moveTo(10, 9); s.lineTo(6, 13); s.lineTo(10, 17); break;
            case "search": s.addCircle(10.5f, 10.5f, 6, Path.Direction.CW); s.moveTo(15, 15); s.lineTo(20, 20); break;
            case "send": s.moveTo(4, 12); s.lineTo(20, 4); s.lineTo(14, 20); s.lineTo(11, 13); s.close(); break;
            case "next": s.moveTo(5, 12); s.lineTo(19, 12); s.moveTo(13, 6); s.lineTo(19, 12); s.lineTo(13, 18); break;
            case "done": s.moveTo(5, 12.5f); s.lineTo(10, 17.5f); s.lineTo(19, 7); break;
            case "globe": s.addCircle(12, 12, 8.5f, Path.Direction.CW); s.moveTo(3.5f, 12); s.lineTo(20.5f, 12); s.addOval(new RectF(8, 3.5f, 16, 20.5f), Path.Direction.CW); break;
            case "clip": s.addRoundRect(new RectF(5, 5, 19, 21), 2.5f, 2.5f, Path.Direction.CW); s.addRoundRect(new RectF(9, 3, 15, 7), 1.5f, 1.5f, Path.Direction.CW); s.moveTo(9, 12); s.lineTo(15, 12); s.moveTo(9, 16); s.lineTo(13, 16); break;
            case "gear": s.addCircle(12, 12, 3, Path.Direction.CW);
                for (int i = 0; i < 8; i++) { double a = Math.PI * i / 4; s.moveTo((float) (12 + Math.cos(a) * 6.5), (float) (12 + Math.sin(a) * 6.5)); s.lineTo((float) (12 + Math.cos(a) * 9), (float) (12 + Math.sin(a) * 9)); }
                s.addCircle(12, 12, 6.5f, Path.Direction.CW); break;
            case "down": s.moveTo(6, 9); s.lineTo(12, 15); s.lineTo(18, 9); break;
            case "back": s.moveTo(15, 5); s.lineTo(8, 12); s.lineTo(15, 19); break;
            case "pin": s.moveTo(9, 4); s.lineTo(15, 4); s.moveTo(10, 4); s.lineTo(9.5f, 11); s.lineTo(6, 14); s.lineTo(18, 14); s.lineTo(14.5f, 11); s.lineTo(14, 4); s.moveTo(12, 14); s.lineTo(12, 20); break;
            case "pinOn": p.setStyle(Paint.Style.FILL_AND_STROKE); s.moveTo(10, 4); s.lineTo(9.5f, 11); s.lineTo(6, 14); s.lineTo(18, 14); s.lineTo(14.5f, 11); s.lineTo(14, 4); s.close(); s.moveTo(12, 14); s.lineTo(12, 20); break;
            case "trash": s.moveTo(4, 7); s.lineTo(20, 7); s.moveTo(9, 7); s.lineTo(9, 4); s.lineTo(15, 4); s.lineTo(15, 7); s.moveTo(6, 7); s.lineTo(7, 20); s.lineTo(17, 20); s.lineTo(18, 7); break;
            case "paste": s.addRoundRect(new RectF(5, 5, 19, 21), 2.5f, 2.5f, Path.Direction.CW); s.moveTo(9, 3); s.lineTo(15, 3); s.moveTo(12, 9); s.lineTo(12, 17); s.moveTo(9, 14); s.lineTo(12, 17); s.lineTo(15, 14); break;
            case "translate": s.moveTo(3, 6); s.lineTo(12, 6); s.moveTo(7.5f, 4); s.lineTo(7.5f, 6); s.moveTo(10, 6); s.quadTo(9, 12, 4, 15); s.moveTo(6, 9.5f); s.quadTo(8, 13, 12, 15); s.moveTo(13, 20); s.lineTo(16.5f, 11); s.lineTo(20, 20); s.moveTo(14.2f, 17); s.lineTo(18.8f, 17); break;
            case "cursor": s.moveTo(4, 12); s.lineTo(20, 12); s.moveTo(7, 9); s.lineTo(4, 12); s.lineTo(7, 15); s.moveTo(17, 9); s.lineTo(20, 12); s.lineTo(17, 15); break;
            default: break;
        }
        c.drawPath(s, p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    /** Маленькая кнопка-значок для панелей. */
    static final class IconView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        String name; int color;
        IconView(Context c, String name, int color) { super(c); this.name = name; this.color = color; }
        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) * 0.55f;
            Icons.draw(c, name, getWidth() / 2f, getHeight() / 2f, s, color, p);
        }
    }
}
