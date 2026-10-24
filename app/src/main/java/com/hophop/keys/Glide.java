package com.hophop.keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Набор свайпом: ведёшь пальцем по буквам, а мы ищем слово, чей «идеальный» путь по центрам клавиш
 * ближе всего к нарисованному. Кандидаты берём по первой и последней букве, частые слова в приоритете.
 */
final class Glide {
    static final int N = 40;

    static List<String> decode(Dict d, Map<Character, float[]> centers, float keyW, float[] xs, float[] ys, int n, int max) {
        List<String> res = new ArrayList<>();
        if (d == null || n < 2 || centers.isEmpty()) return res;
        float[][] u = resample(xs, ys, n, N);
        float uLen = length(xs, ys, n);
        List<Character> first = near(centers, xs[0], ys[0], keyW), last = near(centers, xs[n - 1], ys[n - 1], keyW);
        String[] best = new String[max]; double[] bs = new double[max];
        java.util.Arrays.fill(bs, Double.MAX_VALUE);
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (char f : first) for (char l : last) {
            for (String w : d.byEnds(f, l, 350)) {
                if (!seen.add(w)) continue;
                double sc = score(w, centers, keyW, u, uLen, xs, ys, n);
                if (sc == Double.MAX_VALUE) continue;
                sc -= 0.08 * Math.log(1 + d.score(w));
                for (int k = 0; k < max; k++) if (sc < bs[k]) {
                    for (int j = max - 1; j > k; j--) { best[j] = best[j - 1]; bs[j] = bs[j - 1]; }
                    best[k] = w; bs[k] = sc; break;
                }
            }
        }
        for (String w : best) if (w != null) res.add(w);
        return res;
    }

    /** Ближайшие к точке клавиши: сама клавиша под пальцем и соседи, если палец у края. */
    static List<Character> near(Map<Character, float[]> c, float x, float y, float keyW) {
        List<Character> out = new ArrayList<>();
        char bestC = 0; float bd = Float.MAX_VALUE;
        for (Map.Entry<Character, float[]> e : c.entrySet()) {
            float dx = e.getValue()[0] - x, dy = e.getValue()[1] - y, dd = dx * dx + dy * dy;
            if (dd < bd) { bd = dd; bestC = e.getKey(); }
        }
        out.add(bestC);
        float lim = keyW * 0.85f; lim *= lim;
        for (Map.Entry<Character, float[]> e : c.entrySet()) {
            if (e.getKey() == bestC) continue;
            float dx = e.getValue()[0] - x, dy = e.getValue()[1] - y;
            if (dx * dx + dy * dy < lim && out.size() < 3) out.add(e.getKey());
        }
        return out;
    }

    private static double score(String w, Map<Character, float[]> centers, float keyW, float[][] u, float uLen, float[] xs, float[] ys, int n) {
        float[] px = new float[w.length()], py = new float[w.length()];
        int m = 0; char prev = 0;
        for (int i = 0; i < w.length(); i++) {
            char c = Dict.norm(w.charAt(i));
            if (c == prev) continue;          // двойные буквы на пути не видны
            float[] p = centers.get(c);
            if (p == null) continue;           // апостроф и т.п.
            px[m] = p[0]; py[m] = p[1]; m++; prev = c;
        }
        if (m == 0) return Double.MAX_VALUE;
        float iLen = length(px, py, m);
        if (uLen > keyW * 1.5f && (iLen < uLen * 0.45f || iLen > uLen * 2.2f + keyW)) return Double.MAX_VALUE;
        float[][] id = resample(px, py, m, N);
        double loc = 0;
        for (int i = 0; i < N; i++) loc += Math.hypot(u[0][i] - id[0][i], u[1][i] - id[1][i]);
        loc /= N * keyW;
        // каждая буква слова должна лежать рядом с путём, иначе это не то слово
        double miss = 0;
        for (int j = 1; j < m - 1; j++) {
            double md = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) md = Math.min(md, Math.hypot(xs[i] - px[j], ys[i] - py[j]));
            md /= keyW;
            if (md > 0.75) miss += (md - 0.75) * 1.6;
        }
        return loc + miss;
    }

    static float length(float[] xs, float[] ys, int n) {
        float s = 0;
        for (int i = 1; i < n; i++) s += (float) Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
        return s;
    }

    /** Равномерно по длине пути N точек. */
    static float[][] resample(float[] xs, float[] ys, int n, int cnt) {
        float[][] o = new float[2][cnt];
        float total = length(xs, ys, n);
        if (n == 1 || total < 1e-3f) { for (int i = 0; i < cnt; i++) { o[0][i] = xs[0]; o[1][i] = ys[0]; } return o; }
        float step = total / (cnt - 1), acc = 0; int j = 1; float cx = xs[0], cy = ys[0];
        o[0][0] = cx; o[1][0] = cy; int k = 1;
        while (k < cnt && j < n) {
            float seg = (float) Math.hypot(xs[j] - cx, ys[j] - cy);
            if (acc + seg >= step && seg > 0) {
                float t = (step - acc) / seg;
                cx = cx + (xs[j] - cx) * t; cy = cy + (ys[j] - cy) * t;
                o[0][k] = cx; o[1][k] = cy; k++; acc = 0;
            } else { acc += seg; cx = xs[j]; cy = ys[j]; j++; }
        }
        for (; k < cnt; k++) { o[0][k] = xs[n - 1]; o[1][k] = ys[n - 1]; }
        return o;
    }
}
