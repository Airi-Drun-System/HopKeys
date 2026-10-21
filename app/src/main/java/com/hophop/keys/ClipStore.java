package com.hophop.keys;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** История буфера обмена. Хранится только в памяти приложения на телефоне, ничего не отправляется. */
final class ClipStore {
    private final File file;
    private final Context ctx;
    JSONArray items = new JSONArray(); // {id, text, pin, t}, новые первыми

    private static ClipStore inst;
    /** Одна история на всё приложение: и клавиатура, и настройки видят одни и те же записи. */
    static synchronized ClipStore get(Context c) { if (inst == null) inst = new ClipStore(c.getApplicationContext()); return inst; }

    private ClipStore(Context c) {
        ctx = c;
        file = new File(c.getFilesDir(), "clips.json");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] b = new byte[(int) file.length()];
            int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
            items = new JSONArray(new String(b, 0, n, StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    synchronized void add(String text) {
        if (text == null) return;
        if (text.trim().isEmpty() || text.length() > 20000) return;
        try {
            boolean pinned = false;
            for (int i = items.length() - 1; i >= 0; i--) {
                JSONObject o = items.getJSONObject(i);
                if (o.getString("text").equals(text)) { pinned = o.optBoolean("pin"); items.remove(i); }
            }
            JSONArray n = new JSONArray();
            n.put(new JSONObject().put("id", System.currentTimeMillis()).put("text", text).put("pin", pinned).put("t", System.currentTimeMillis()));
            for (int i = 0; i < items.length(); i++) n.put(items.get(i));
            items = n;
            trim();
            save();
        } catch (Exception ignored) { }
    }

    private void trim() throws Exception {
        int max = (int) Theme.num(Theme.prefs(ctx), "clipMax", 1000);
        int unpinned = 0;
        for (int i = 0; i < items.length(); i++) if (!items.getJSONObject(i).optBoolean("pin")) unpinned++;
        for (int i = items.length() - 1; i >= 0 && unpinned > max; i--) {
            if (!items.getJSONObject(i).optBoolean("pin")) { items.remove(i); unpinned--; }
        }
    }

    synchronized void remove(long id) {
        try { for (int i = items.length() - 1; i >= 0; i--) if (items.getJSONObject(i).getLong("id") == id) items.remove(i); save(); } catch (Exception ignored) { }
    }

    synchronized void pin(long id) {
        try {
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                if (o.getLong("id") == id) o.put("pin", !o.optBoolean("pin"));
            }
            save();
        } catch (Exception ignored) { }
    }

    /** Очистить всё, кроме закреплённых. */
    synchronized void clear() {
        try {
            JSONArray n = new JSONArray();
            for (int i = 0; i < items.length(); i++) if (items.getJSONObject(i).optBoolean("pin")) n.put(items.get(i));
            items = n; save();
        } catch (Exception ignored) { }
    }

    /** Закреплённые сверху, дальше по времени. */
    synchronized JSONArray sorted() {
        JSONArray out = new JSONArray();
        try {
            for (int i = 0; i < items.length(); i++) if (items.getJSONObject(i).optBoolean("pin")) out.put(items.get(i));
            for (int i = 0; i < items.length(); i++) if (!items.getJSONObject(i).optBoolean("pin")) out.put(items.get(i));
        } catch (Exception ignored) { }
        return out;
    }

    private void save() {
        try (FileOutputStream o = new FileOutputStream(file)) { o.write(items.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) { }
    }
}
