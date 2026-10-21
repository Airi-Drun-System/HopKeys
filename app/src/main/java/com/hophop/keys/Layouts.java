package com.hophop.keys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Раскладки. Служебные клавиши в фигурных скобках, остальные — символы. */
final class Layouts {
    static final String[] RU = {
        "й ц у к е н г ш щ з х",
        "ф ы в а п р о л д ж э",
        "{shift} я ч с м и т ь б ю {del}",
        "{sym} {lang} , {space} . {enter}"};
    static final String[] EN = {
        "q w e r t y u i o p",
        "a s d f g h j k l",
        "{shift} z x c v b n m {del}",
        "{sym} {lang} , {space} . {enter}"};
    static final String[] SYM = {
        "1 2 3 4 5 6 7 8 9 0",
        "@ # ₽ _ & - + ( ) /",
        "{sym2} * \" ' : ; ! ? {del}",
        "{abc} {lang} , {space} . {enter}"};
    static final String[] SYM2 = {
        "~ ` | • √ π ÷ × ¶ ∆",
        "€ $ £ ¥ ^ ° = { } \\",
        "{sym} % © ® ™ ✓ [ ] {del}",
        "{abc} {lang} < {space} > {enter}"};
    static final String[] NUM = {
        "1 2 3 -",
        "4 5 6 ,",
        "7 8 9 {del}",
        "{abc} 0 . {enter}"};
    static final String[] PHONE = {
        "1 2 3 +",
        "4 5 6 *",
        "7 8 9 {del}",
        "# 0 ( {enter}"};
    static final String NUMROW = "1 2 3 4 5 6 7 8 9 0";

    /** Длинное нажатие: дополнительные символы. Для верхнего ряда первой идёт цифра. */
    static final Map<String, String> ALT = new HashMap<>();
    static {
        String[] ruTop = RU[0].split(" "), enTop = EN[0].split(" ");
        for (int i = 0; i < ruTop.length; i++) ALT.put(ruTop[i], i < 10 ? String.valueOf((i + 1) % 10) : "");
        for (int i = 0; i < enTop.length; i++) ALT.put(enTop[i], String.valueOf((i + 1) % 10));
        ALT.put("е", "6ё"); ALT.put("х", "ъ"); ALT.put("ь", "ъ");
        ALT.put("e", "3èéêëē"); ALT.put("u", "7ùúûü"); ALT.put("i", "8ìíîï"); ALT.put("o", "9òóôöõø");
        ALT.put("a", "àáâäãåæ"); ALT.put("s", "ßš"); ALT.put("c", "çć"); ALT.put("n", "ñń"); ALT.put("y", "6ýÿ");
        ALT.put(".", ",?!:;…"); ALT.put(",", "\"'«»"); ALT.put("-", "–—_"); ALT.put("₽", "$€£¥"); ALT.put("?", "¿"); ALT.put("!", "¡");
        ALT.put("0", "°"); ALT.put("(", "[{<"); ALT.put(")", "]}>"); ALT.put("\"", "«»„“”");
    }

    static final class Key {
        String code, label; float x, y, w, h; boolean fn;
        Key(String code) { this.code = code; }
        boolean special() { return isSpecial(code); }
        String alts() { String a = ALT.get(code); return a == null ? "" : a; }
    }

    /** Строит клавиши в координатах 0..width, высота ряда rowH. */
    static List<Key> build(String[] rows, boolean numRow, float width, float rowH, float top) {
        List<String> all = new ArrayList<>();
        if (numRow && rows != NUM && rows != PHONE && rows != SYM) all.add(NUMROW);
        for (String r : rows) all.add(r);
        int units = 0;
        for (String r : rows) if (!r.contains("{space}")) units = Math.max(units, letters(r));
        if (units == 0) units = 10;
        float unit = width / units;
        List<Key> keys = new ArrayList<>();
        float y = top;
        for (String r : all) {
            String[] parts = r.split(" ");
            int lc = letters(r);
            boolean small = rows == NUM || rows == PHONE;
            float[] ws = new float[parts.length];
            float used = 0; int flex = 0;
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                if (small) ws[i] = width / parts.length;
                else if (p.equals("{space}")) { ws[i] = -1; flex++; }
                else if (p.equals("{shift}") || p.equals("{del}") || p.equals("{sym2}") || (p.equals("{sym}") && !r.contains("{space}"))) {
                    ws[i] = Math.max(1f, (units - lc) / 2f) * unit;
                } else if (p.equals("{sym}") || p.equals("{abc}") || p.equals("{enter}")) ws[i] = 1.5f * unit;
                else ws[i] = unit;
                if (ws[i] > 0) used += ws[i];
            }
            for (int i = 0; i < ws.length; i++) if (ws[i] < 0) ws[i] = (width - used) / flex;
            float total = 0; for (float w : ws) total += w;
            float x = Math.max(0, (width - total) / 2);
            for (int i = 0; i < parts.length; i++) {
                Key k = new Key(parts[i]);
                k.x = x; k.y = y; k.w = ws[i]; k.h = rowH;
                k.fn = k.special() && !parts[i].equals("{space}");
                if (small && !k.special()) k.fn = false;
                keys.add(k);
                x += ws[i];
            }
            y += rowH;
        }
        return keys;
    }

    static boolean isSpecial(String p) { return p.length() > 1 && p.startsWith("{"); }
    static int letters(String r) { int n = 0; for (String p : r.split(" ")) if (!isSpecial(p)) n++; return n; }

    static int rows(String[] rows, boolean numRow) {
        return rows.length + (numRow && rows != NUM && rows != PHONE && rows != SYM ? 1 : 0);
    }
}
