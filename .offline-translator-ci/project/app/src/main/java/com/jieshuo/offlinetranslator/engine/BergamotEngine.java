package com.jieshuo.offlinetranslator.engine;

import android.content.Context;
import dev.davidv.bergamot.NativeLib;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public final class BergamotEngine implements TranslationEngine {
    private final TextSegmenter segmenter = new TextSegmenter();
    private final TranslationCache cache = new TranslationCache(1536, 2000000);
    private NativeLib lib;
    private boolean loaded;

    public synchronized void load(Context context) throws Exception {
        if (loaded) return;
        ModelInstaller.InstalledModel f = new ModelInstaller().ensureInstalled(context);
        NativeLib n = new NativeLib();
        try {
            n.loadModelIntoCache(config(f), "enar");
            lib = n;
            loaded = true;
        } catch (Throwable t) {
            try { n.cleanup(); } catch (Throwable ignored) {}
            throw t;
        }
    }

    public synchronized boolean isLoaded() { return loaded && lib != null; }

    public synchronized String translate(String text) throws Exception {
        if (!isLoaded()) throw new IllegalStateException("Engine is not loaded");
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        List<TextSegmenter.Piece> pieces = segmenter.split(text);
        List<String> src = segmenter.translatableTexts(pieces);
        if (src.isEmpty()) return text;
        String[] out = new String[src.size()];
        LinkedHashMap<String,List<Integer>> misses = new LinkedHashMap<>();
        for (int i = 0; i < src.size(); i++) {
            String s = src.get(i);
            String hit = cache.get(s);
            if (hit != null) out[i] = hit;
            else misses.computeIfAbsent(s, k -> new ArrayList<>()).add(i);
        }
        List<String> unique = new ArrayList<>(misses.keySet());
        for (int start = 0; start < unique.size(); start += 16) {
            int end = Math.min(unique.size(), start + 16);
            String[] batch = unique.subList(start, end).toArray(new String[0]);
            String[] tr = lib.translateMultiple(batch, "enar");
            if (tr == null || tr.length != batch.length) throw new IllegalStateException("Bad translation result");
            for (int j = 0; j < batch.length; j++) {
                String v = tr[j] == null ? "" : tr[j];
                cache.put(batch[j], v);
                for (int idx : misses.get(batch[j])) out[idx] = v;
            }
        }
        return segmenter.reassemble(pieces, Arrays.asList(out));
    }

    public synchronized void close() {
        loaded = false;
        cache.clear();
        NativeLib n = lib;
        lib = null;
        if (n != null) try { n.cleanup(); } catch (Throwable ignored) {}
    }

    private static String config(ModelInstaller.InstalledModel f) {
        int threads = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors()));
        return "models:\n  - " + f.model.getAbsolutePath() +
               "\nvocabs:\n  - " + f.vocab.getAbsolutePath() +
               "\n  - " + f.vocab.getAbsolutePath() +
               "\nbeam-size: 1\nnormalize: 1.0\nword-penalty: 0\nmax-length-break: 128" +
               "\nmini-batch-words: 1024\nmax-length-factor: 2.0\nskip-cost: true" +
               "\ncpu-threads: " + threads +
               "\nquiet: true\nquiet-translation: true\ngemm-precision: int8shiftAlphaAll\nalignment: soft\n";
    }
}
