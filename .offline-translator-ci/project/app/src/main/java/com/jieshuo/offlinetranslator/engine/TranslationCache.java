package com.jieshuo.offlinetranslator.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TranslationCache {
    private final int maxEntries;
    private final int maxChars;
    private int chars;
    private final LinkedHashMap<String, String> map = new LinkedHashMap<>(64, 0.75f, true);

    public TranslationCache(int maxEntries, int maxChars) {
        this.maxEntries = Math.max(1, maxEntries);
        this.maxChars = Math.max(1024, maxChars);
    }

    public synchronized String get(String source) { return map.get(source); }

    public synchronized void put(String source, String translated) {
        if (source == null || translated == null) return;
        if (source.length() + translated.length() > maxChars / 2) return;
        String old = map.remove(source);
        if (old != null) chars -= source.length() + old.length();
        map.put(source, translated);
        chars += source.length() + translated.length();
        trim();
    }

    public synchronized void clear() { map.clear(); chars = 0; }
    public synchronized int size() { return map.size(); }

    private void trim() {
        while (map.size() > maxEntries || chars > maxChars) {
            Map.Entry<String, String> eldest = map.entrySet().iterator().next();
            chars -= eldest.getKey().length() + eldest.getValue().length();
            map.remove(eldest.getKey());
        }
    }
}
