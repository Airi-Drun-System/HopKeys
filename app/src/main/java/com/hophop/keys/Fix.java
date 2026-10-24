package com.hophop.keys;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Автозамена «как в Gboard»: учитываем, куда именно попал палец. Промах по соседней клавише стоит дёшево,
 * по дальней — дорого, лишняя/пропущенная буква и перестановка — средне. Плюс частота слова.
 */
final class Fix {
    static final class Result { String best; List<String> top = new ArrayList<>(); boolean fix; }

    private static float[] center(Map<Character, float[]> c, char ch) { return c.get(Dict.norm(ch)); }

    /** Цена несовпадения нажатия с буквой: квадрат расстояния до центра клавиши. */
    private static double sub(float[] tap, char typed, char want, Map<Character, float[]> c, float keyW) {
        if (Dict.norm(typed) == Dict.norm(want)) {
            if (tap == null) return 0;
            float[] p = center(c, want);
            if (p == null) return 0;
            double dx = (tap[0] - p[0]) / keyW, dy = (tap[1] - p[1]) / (keyW * 1.35);
            return Math.max(0, (dx * dx + dy * dy) / (2 * 0.45 * 0.45) - 0.6);
        }
        float[] p = center(c, want);
        if (p == null) return 4;
        float[] from = tap != null ? tap : center(c, typed);
        if (from == null) return 4;
        double dx = (from[0] - p[0]) / keyW, dy = (from[1] - p[1]) / (keyW * 1.35); // ряды выше, чем клавиши широки
        return Math.min(6, (dx * dx + dy * dy) / (2 * 0.45 * 0.45));
    }

    /** Стоимость превратить набранное в слово (выравнивание с учётом касаний). */
    static double cost(String typed, float[][] taps, String w, Map<Character, float[]> c, float keyW) {
        int n = typed.length(), m = w.length();
        double[][] dp = new double[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][0] = i * 3.2;
        for (int j = 0; j <= m; j++) dp[0][j] = j * 3.2;
        for (int i = 1; i <= n; i++) for (int j = 1; j <= m; j++) {
            double v = dp[i - 1][j - 1] + sub(taps[i - 1], typed.charAt(i - 1), w.charAt(j - 1), c, keyW);
            v = Math.min(v, dp[i - 1][j] + 3.2);   // лишняя буква
            v = Math.min(v, dp[i][j - 1] + 3.2);   // пропущенная буква
            if (i > 1 && j > 1 && Dict.norm(typed.charAt(i - 1)) == Dict.norm(w.charAt(j - 2)) && Dict.norm(typed.charAt(i - 2)) == Dict.norm(w.charAt(j - 1)))
                v = Math.min(v, dp[i - 2][j - 2] + 2.4); // перестановка соседних
            dp[i][j] = v;
        }
        return dp[n][m];
    }

    static Result run(Dict d, String typed, float[][] taps, Map<Character, float[]> c, float keyW, int max) {
        Result r = new Result();
        String w = typed.toLowerCase();
        int n = w.length();
        Set<String> cand = new HashSet<>();
        String alpha = d.alphabet;
        // соседи для каждой позиции: буквы рядом с местом касания
        List<List<Character>> nb = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Character> l = new ArrayList<>();
            float[] tp = taps[i] != null ? taps[i] : center(c, w.charAt(i));
            for (int a = 0; a < alpha.length(); a++) {
                char ch = alpha.charAt(a); float[] p = center(c, ch);
                if (tp == null || p == null) continue;
                double dx = (tp[0] - p[0]) / keyW, dy = (tp[1] - p[1]) / (keyW * 1.35);
                if (dx * dx + dy * dy < 1.9 * 1.9 && ch != w.charAt(i)) l.add(ch);
            }
            nb.add(l);
        }
        cand.add(w);
        for (int i = 0; i < n; i++) cand.add(w.substring(0, i) + w.substring(i + 1));
        for (int i = 0; i < n - 1; i++) cand.add(w.substring(0, i) + w.charAt(i + 1) + w.charAt(i) + w.substring(i + 2));
        for (int i = 0; i <= n; i++) for (int a = 0; a < alpha.length(); a++) cand.add(w.substring(0, i) + alpha.charAt(a) + w.substring(i));
        for (int i = 0; i < n; i++) for (char ch : nb.get(i)) {
            String s1 = w.substring(0, i) + ch + w.substring(i + 1);
            cand.add(s1);
            if (n >= 4) for (int j = i + 1; j < n; j++) for (char ch2 : nb.get(j)) cand.add(s1.substring(0, j) + ch2 + s1.substring(j + 1));
        }
        // ё вместо е
        if (w.indexOf('е') >= 0) for (int i = 0; i < n; i++) if (w.charAt(i) == 'е') cand.add(w.substring(0, i) + 'ё' + w.substring(i + 1));
        String[] best = new String[max]; double[] bs = new double[max];
        java.util.Arrays.fill(bs, Double.MAX_VALUE);
        double typedScore = Double.MAX_VALUE;
        for (String s : cand) {
            if (!d.known(s)) continue;
            double sc = cost(w, taps, s, c, keyW) - 0.55 * Math.log(1 + d.score(s));
            if (s.equals(w)) { sc -= 2.2; typedScore = sc; } // своё слово в словаре — сильный довод не трогать
            for (int k = 0; k < max; k++) if (sc < bs[k]) {
                for (int j = max - 1; j > k; j--) { best[j] = best[j - 1]; bs[j] = bs[j - 1]; }
                best[k] = s; bs[k] = sc; break;
            }
        }
        for (String s : best) if (s != null) r.top.add(s);
        if (!r.top.isEmpty()) {
            r.best = r.top.get(0);
            r.fix = !r.best.equals(w) && n >= 2 && (typedScore == Double.MAX_VALUE || bs[0] < typedScore - 1.2);
        }
        return r;
    }
}
