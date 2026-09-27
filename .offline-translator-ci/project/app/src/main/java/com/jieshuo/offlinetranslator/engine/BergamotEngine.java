package com.jieshuo.offlinetranslator.engine;

import android.content.Context;

import dev.davidv.bergamot.NativeLib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** English -> Arabic Bergamot engine using the proven cgeo Android JNI runtime. */
public final class BergamotEngine implements TranslationEngine {
    private static final String MODEL_KEY = "enar";
    private static final int NATIVE_BATCH_SIZE = 16;
    private final TextSegmenter segmenter = new TextSegmenter();
    private final TranslationCache cache = new TranslationCache(1536, 2_000_000);
    private NativeLib nativeLib;
    private boolean loaded;

    @Override
    public synchronized void load(Context context) throws Exception {
        if (loaded) return;
        ModelInstaller.InstalledModel files = new ModelInstaller().ensureInstalled(context);
        NativeLib lib = null;
        try {
            lib = new NativeLib();
            lib.loadModelIntoCache(buildConfig(files), MODEL_KEY);
            nativeLib = lib;
            loaded = true;
        } catch (Throwable t) {
            if (lib != null) {
                try { lib.cleanup(); } catch (Throwable ignored) {}
            }
            throw t;
        }
    }

    @Override
    public synchronized boolean isLoaded() { return loaded && nativeLib != null; }

    @Override
    public synchronized String translate(String text) throws Exception {
        if (!isLoaded()) throw new IllegalStateException("Engine is not loaded");
        if (text == null || text.isEmpty()) return text == null ? "" : text;

        List<TextSegmenter.Piece> pieces = segmenter.split(text);
        List<String> sources = segmenter.translatableTexts(pieces);
        if (sources.isEmpty()) return text;

        String[] outputs = new String[sources.size()];
        LinkedHashMap<String, List<Integer>> misses = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            String source = sources.get(i);
            String hit = cache.get(source);
            if (hit != null) outputs[i] = hit;
            else misses.computeIfAbsent(source, k -> new ArrayList<>()).add(i);
        }

        List<String> unique = new ArrayList<>(misses.keySet());
        for (int start = 0; start < unique.size(); start += NATIVE_BATCH_SIZE) {
            int end = Math.min(unique.size(), start + NATIVE_BATCH_SIZE);
            String[] batch = unique.subList(start, end).toArray(new String[0]);
            String[] translated = nativeLib.translateMultiple(batch, MODEL_KEY);
            if (translated == null || translated.length != batch.length) {
                throw new IllegalStateException("Bergamot returned wrong result count");
            }
            for (int j = 0; j < batch.length; j++) {
                String target = translated[j] == null ? "" : translated[j];
                cache.put(batch[j], target);
                for (int index : misses.get(batch[j])) outputs[index] = target;
            }
        }

        return segmenter.reassemble(pieces, Arrays.asList(outputs));
    }

    @Override
    public synchronized void close() {
        loaded = false;
        cache.clear();
        NativeLib lib = nativeLib;
        nativeLib = null;
        if (lib != null) {
            try { lib.cleanup(); } catch (Throwable ignored) {}
        }
    }

    private static String buildConfig(ModelInstaller.InstalledModel f) {
        String model = yamlPath(f.model.getAbsolutePath());
        String vocab = yamlPath(f.vocab.getAbsolutePath());
        int cpus = Runtime.getRuntime().availableProcessors();
        int threads = Math.max(1, Math.min(2, cpus));
        return "models:\n" +
                "  - " + model + "\n" +
                "vocabs:\n" +
                "  - " + vocab + "\n" +
                "  - " + vocab + "\n" +
                "beam-size: 1\n" +
                "normalize: 1.0\n" +
                "word-penalty: 0\n" +
                "max-length-break: 128\n" +
                "mini-batch-words: 1024\n" +
                "max-length-factor: 2.0\n" +
                "skip-cost: true\n" +
                "cpu-threads: " + threads + "\n" +
                "quiet: true\n" +
                "quiet-translation: true\n" +
                "gemm-precision: int8shiftAlphaAll\n" +
                "alignment: soft\n";
    }

    private static String yamlPath(String path) {
        return "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
