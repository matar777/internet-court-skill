package com.jieshuo.offlinetranslator.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextSegmenter {
    public static final int MAX_ENGINE_PIECE_CHARS = 1200;
    private static final int MIN_BACKTRACK = 700;

    private static final Pattern PROTECTED = Pattern.compile(
            "(?iu)" +
            "(?:https?://|www\\.)[^\\s<>\"']+" +
            "|[\\p{L}\\p{N}._%+\\-]+@[\\p{L}\\p{N}.\\-]+\\.[A-Z]{2,}" +
            "|(?:[A-Z]:\\\\|/)(?:[^\\s<>\"']+[/\\\\])*[^\\s<>\"']*" +
            "|(?<![\\p{L}\\p{N}_])[\\p{L}\\p{N}_\\-]+(?:\\.[\\p{L}\\p{N}_\\-]+)+(?![\\p{L}\\p{N}_])" +
            "|[@#][A-Z0-9_][A-Z0-9_.\\-]*" +
            "|(?:[A-Z_][A-Z0-9_]*::)+[A-Z_][A-Z0-9_]*" +
            "|0x[0-9A-F]+" +
            "|(?<![\\p{L}\\p{N}_])[+\\-]?\\d(?:[\\d.,:/%+\\-]*\\d)?%?(?![\\p{L}\\p{N}_])" +
            "|[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF]+(?:[ \\t]+[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF]+)*" +
            "|[\\r\\n]+" +
            "|[^\\p{L}\\p{N}\\s'’]+"
    );

    public static final class Piece {
        public final String value; public final boolean translate;
        Piece(String value, boolean translate) { this.value = value; this.translate = translate; }
    }

    public List<Piece> split(String input) {
        if (input == null || input.isEmpty()) return Collections.singletonList(new Piece(input == null ? "" : input, false));
        List<Piece> coarse = new ArrayList<>();
        Matcher m = PROTECTED.matcher(input);
        int pos = 0;
        while (m.find()) {
            if (m.start() > pos) addCandidate(coarse, input.substring(pos, m.start()));
            coarse.add(new Piece(m.group(), false));
            pos = m.end();
        }
        if (pos < input.length()) addCandidate(coarse, input.substring(pos));
        List<Piece> out = new ArrayList<>();
        for (Piece p : coarse) {
            if (!p.translate || p.value.length() <= MAX_ENGINE_PIECE_CHARS) appendMerged(out, p);
            else splitLong(out, p.value);
        }
        return out;
    }

    public String reassemble(List<Piece> pieces, List<String> translatedPieces) {
        StringBuilder out = new StringBuilder();
        int ti = 0;
        for (Piece piece : pieces) {
            if (piece.translate) out.append(translatedPieces.get(ti++));
            else out.append(piece.value);
        }
        if (ti != translatedPieces.size()) throw new IllegalStateException("Translation piece count mismatch");
        return out.toString();
    }

    public List<String> translatableTexts(List<Piece> pieces) {
        List<String> texts = new ArrayList<>();
        for (Piece p : pieces) if (p.translate) texts.add(p.value);
        return texts;
    }

    private void addCandidate(List<Piece> out, String s) {
        if (s.isEmpty()) return;
        int first = 0;
        while (first < s.length() && Character.isWhitespace(s.charAt(first))) first++;
        int last = s.length();
        while (last > first && Character.isWhitespace(s.charAt(last - 1))) last--;
        if (first > 0) appendMerged(out, new Piece(s.substring(0, first), false));
        if (last > first) {
            String core = s.substring(first, last);
            appendMerged(out, new Piece(core, containsLatinLetter(core)));
        }
        if (last < s.length()) appendMerged(out, new Piece(s.substring(last), false));
    }

    private void splitLong(List<Piece> out, String s) {
        int pos = 0;
        while (s.length() - pos > MAX_ENGINE_PIECE_CHARS) {
            int hard = pos + MAX_ENGINE_PIECE_CHARS;
            int min = Math.min(hard, pos + MIN_BACKTRACK);
            int cut = findBreak(s, min, hard);
            if (cut <= pos) cut = hard;
            appendMerged(out, new Piece(s.substring(pos, cut), true));
            pos = cut;
        }
        if (pos < s.length()) appendMerged(out, new Piece(s.substring(pos), true));
    }

    private int findBreak(String s, int min, int hard) {
        for (int i = hard; i > min; i--) if (Character.isWhitespace(s.charAt(i - 1))) return i;
        return hard;
    }

    private static boolean containsLatinLetter(String s) {
        for (int i = 0; i < s.length();) {
            int cp = s.codePointAt(i);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.LATIN && Character.isLetter(cp)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    private static void appendMerged(List<Piece> out, Piece p) {
        if (p.value.isEmpty()) return;
        if (!out.isEmpty()) {
            Piece prev = out.get(out.size() - 1);
            if (!p.translate && !prev.translate) {
                out.set(out.size() - 1, new Piece(prev.value + p.value, false));
                return;
            }
        }
        out.add(p);
    }
}
