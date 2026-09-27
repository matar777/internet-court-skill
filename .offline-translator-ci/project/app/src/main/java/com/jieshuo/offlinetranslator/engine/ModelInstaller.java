package com.jieshuo.offlinetranslator.engine;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ModelInstaller {
    public static final String ASSET_DIR = "models/enar";
    private static final String VERSION = "enar-base-memory-v2.2-2026-06";
    public static final Spec MODEL = new Spec("model.enar.intgemm.alphas.bin", 31_561_787L,
            "ae659d7045fc2e5d6ba50583586a5d94ee4358d92c019847e37b57a0e627faa8");
    public static final Spec VOCAB = new Spec("vocab.enar.spm", 863_591L,
            "8d93c54aa5e2044c416ec680b5ff9af0227bd698521666e8b1a1ea1b041fbae8");
    public static final Spec SHORTLIST = new Spec("lex.50.50.enar.s2t.bin", 3_139_692L,
            "2b7194817c5dd9225ca90c0d908cc92e1d6d1f45625781a5feb037ac568d6a61");

    public static final class Spec {
        public final String name; public final long size; public final String sha256;
        Spec(String name, long size, String sha256) { this.name = name; this.size = size; this.sha256 = sha256; }
    }
    public static final class InstalledModel {
        public final File model; public final File vocab; public final File shortlist;
        InstalledModel(File model, File vocab, File shortlist) { this.model = model; this.vocab = vocab; this.shortlist = shortlist; }
    }

    public InstalledModel ensureInstalled(Context context) throws Exception {
        File dir = new File(context.getFilesDir(), "models/" + VERSION);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create model directory");
        File marker = new File(dir, ".verified");
        File model = new File(dir, MODEL.name);
        File vocab = new File(dir, VOCAB.name);
        File shortlist = new File(dir, SHORTLIST.name);
        if (markerMatches(marker) && quickValid(model, MODEL) && quickValid(vocab, VOCAB) && quickValid(shortlist, SHORTLIST)) {
            return new InstalledModel(model, vocab, shortlist);
        }
        copyVerified(context.getAssets(), MODEL, model);
        copyVerified(context.getAssets(), VOCAB, vocab);
        copyVerified(context.getAssets(), SHORTLIST, shortlist);
        writeMarker(marker);
        return new InstalledModel(model, vocab, shortlist);
    }

    public static boolean bundledAssetsPresent(Context context) {
        AssetManager assets = context.getAssets();
        try {
            for (Spec spec : new Spec[]{MODEL, VOCAB, SHORTLIST}) {
                try (InputStream in = assets.open(ASSET_DIR + "/" + spec.name)) { if (in.read() < 0) return false; }
            }
            return true;
        } catch (IOException e) { return false; }
    }

    private void copyVerified(AssetManager assets, Spec spec, File destination) throws Exception {
        File tmp = new File(destination.getParentFile(), destination.getName() + ".tmp");
        if (tmp.exists() && !tmp.delete()) throw new IOException("Cannot clear temp file");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count = 0;
        try (InputStream raw = assets.open(ASSET_DIR + "/" + spec.name);
             BufferedInputStream in = new BufferedInputStream(raw, 1024 * 1024);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp), 1024 * 1024)) {
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue;
                out.write(buffer, 0, n); digest.update(buffer, 0, n); count += n;
            }
        }
        String hash = hex(digest.digest());
        if (count != spec.size || !spec.sha256.equals(hash)) { tmp.delete(); throw new IOException("Model asset verification failed: " + spec.name); }
        if (destination.exists() && !destination.delete()) throw new IOException("Cannot replace old model file: " + spec.name);
        if (!tmp.renameTo(destination)) throw new IOException("Atomic model install failed: " + spec.name);
    }

    private boolean markerMatches(File marker) {
        if (!marker.isFile()) return false;
        try (FileInputStream in = new FileInputStream(marker)) {
            byte[] bytes = new byte[(int) marker.length()];
            int n = in.read(bytes);
            return VERSION.equals(new String(bytes, 0, Math.max(0, n), StandardCharsets.UTF_8));
        } catch (Exception e) { return false; }
    }
    private boolean quickValid(File file, Spec spec) { return file.isFile() && file.length() == spec.size; }
    private void writeMarker(File marker) throws IOException {
        try (FileOutputStream out = new FileOutputStream(marker, false)) {
            out.write(VERSION.getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
        }
    }
    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
