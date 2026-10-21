package com.hophop.keys;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Словарь для подсказок и автозамены (Т9). Частоты слов взяты из открытого списка FrequencyWords
 * (Hermit Dave, MIT). Выученные слова пользователя добавляются поверх и хранятся только на телефоне.
 */
final class Dict {
    final String alphabet;
    private final Map<String, Integer> freq = new HashMap<>();
    private String[] sorted = new String[0];
    private final Map<String, Integer> user = new HashMap<>();

    Dict(InputStream in, String alphabet) {
        this.alphabet = alphabet;
        List<String> words = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t <= 0) continue;
                String w = line.substring(0, t);
                try { freq.put(w, Integer.parseInt(line.substring(t + 1))); words.add(w); } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) { }
        sorted = words.toArray(new String[0]);
        Arrays.sort(sorted);
    }

    void setUser(Map<String, Integer> u) { user.clear(); user.putAll(u); }

    /** Вес слова: частота из словаря плюс заметная прибавка, если ты сам его печатал. */
    long score(String w) {
        Integer f = freq.get(w);
        Integer u = user.get(w);
        long s = f == null ? 0 : f;
        if (u != null) s += 2000L * u + 5000;
        return s;
    }

    boolean known(String w) { return freq.containsKey(w) || user.containsKey(w); }

    /** До n слов, начинающихся с prefix, по убыванию веса. */
    List<String> complete(String prefix, int n) {
        List<String> out = new ArrayList<>();
        if (prefix.isEmpty()) return out;
        String[] best = new String[n];
        long[] bs = new long[n];
        int i = Arrays.binarySearch(sorted, prefix);
        if (i < 0) i = -i - 1;
        for (int scanned = 0; i < sorted.length && scanned < 4000; i++, scanned++) {
            String w = sorted[i];
            if (!w.startsWith(prefix)) break;
            if (w.equals(prefix)) continue;
            insert(best, bs, w, score(w));
        }
        for (Map.Entry<String, Integer> e : user.entrySet()) {
            String w = e.getKey();
            if (w.startsWith(prefix) && !w.equals(prefix) && !freq.containsKey(w)) insert(best, bs, w, score(w));
        }
        for (String b : best) if (b != null) out.add(b);
        return out;
    }

    private static void insert(String[] best, long[] bs, String w, long s) {
        for (int k = 0; k < best.length; k++) {
            if (best[k] != null && best[k].equals(w)) return;
        }
        for (int k = 0; k < best.length; k++) {
            if (best[k] == null || s > bs[k]) {
                for (int j = best.length - 1; j > k; j--) { best[j] = best[j - 1]; bs[j] = bs[j - 1]; }
                best[k] = w; bs[k] = s;
                return;
            }
        }
    }

    /** Исправление опечатки: слова на расстоянии одной правки (пропуск, лишняя, замена, перестановка). */
    String correct(String w) {
        if (w.length() < 3 || known(w)) return null;
        String best = null; long bs = 0;
        int n = w.length();
        List<String> cand = new ArrayList<>();
        for (int i = 0; i < n; i++) cand.add(w.substring(0, i) + w.substring(i + 1));
        for (int i = 0; i < n - 1; i++) cand.add(w.substring(0, i) + w.charAt(i + 1) + w.charAt(i) + w.substring(i + 2));
        for (int i = 0; i < n; i++) for (int a = 0; a < alphabet.length(); a++) {
            char c = alphabet.charAt(a);
            if (c != w.charAt(i)) cand.add(w.substring(0, i) + c + w.substring(i + 1));
        }
        for (int i = 0; i <= n; i++) for (int a = 0; a < alphabet.length(); a++) cand.add(w.substring(0, i) + alphabet.charAt(a) + w.substring(i));
        for (String c : cand) {
            long s = score(c);
            if (s <= 0) continue;
            if (near(w, c)) s *= 3; // соседняя клавиша — вероятнее промах пальцем
            if (s > bs) { bs = s; best = c; }
        }
        return bs >= 50 ? best : null;
    }

    private static final String[] ROWS = {"йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю", "qwertyuiop", "asdfghjkl", "zxcvbnm"};

    /** Отличаются одной заменой на соседнюю по раскладке букву. */
    static boolean near(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = -1;
        for (int i = 0; i < a.length(); i++) if (a.charAt(i) != b.charAt(i)) { if (diff >= 0) return false; diff = i; }
        if (diff < 0) return false;
        char x = a.charAt(diff), y = b.charAt(diff);
        for (String r : ROWS) {
            int ix = r.indexOf(x), iy = r.indexOf(y);
            if (ix >= 0 && iy >= 0 && Math.abs(ix - iy) == 1) return true;
        }
        return false;
    }
}
