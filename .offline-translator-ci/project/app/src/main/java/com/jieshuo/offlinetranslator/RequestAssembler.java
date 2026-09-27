package com.jieshuo.offlinetranslator;

import android.os.Bundle;
import android.os.Messenger;
import android.os.SystemClock;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reassembles chunked Messenger requests while enforcing strict size limits. */
public final class RequestAssembler {
    public static final class ProtocolException extends Exception {
        ProtocolException(String message) { super(message); }
    }

    public static final class CompletedRequest {
        public final int uid;
        public final String requestId;
        public final String source;
        public final String target;
        public final String text;
        public final Messenger replyTo;

        CompletedRequest(int uid, String requestId, String source, String target,
                         String text, Messenger replyTo) {
            this.uid = uid;
            this.requestId = requestId;
            this.source = source;
            this.target = target;
            this.text = text;
            this.replyTo = replyTo;
        }
    }

    private static final class Pending {
        final int uid;
        final String requestId;
        final int partCount;
        final String source;
        final String target;
        final String[] chunks;
        final Messenger replyTo;
        final long createdAt = SystemClock.elapsedRealtime();
        int received;
        int charCount;

        Pending(int uid, String requestId, int partCount, String source,
                String target, Messenger replyTo) {
            this.uid = uid;
            this.requestId = requestId;
            this.partCount = partCount;
            this.source = source;
            this.target = target;
            this.replyTo = replyTo;
            this.chunks = new String[partCount];
        }
    }

    private final LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();

    public synchronized CompletedRequest accept(int uid, Bundle b, Messenger replyTo)
            throws ProtocolException {
        expireOld();
        if (b == null) throw new ProtocolException("Missing Bundle");
        if (b.getInt(Protocol.KEY_VERSION, -1) != Protocol.VERSION) {
            throw new ProtocolException("Unsupported protocol version");
        }

        String id = safeString(b.getString(Protocol.KEY_REQUEST_ID));
        String src = safeString(b.getString(Protocol.KEY_SOURCE));
        String dst = safeString(b.getString(Protocol.KEY_TARGET));
        String chunk = b.getString(Protocol.KEY_TEXT);
        int index = b.getInt(Protocol.KEY_PART_INDEX, -1);
        int count = b.getInt(Protocol.KEY_PART_COUNT, -1);

        if (id.isEmpty() || id.length() > 64) throw new ProtocolException("Bad request id");
        if (!id.matches("[A-Za-z0-9._-]+")) throw new ProtocolException("Bad request id characters");
        if (replyTo == null) throw new ProtocolException("replyTo is required");
        if (count < 1 || count > Protocol.MAX_PARTS) throw new ProtocolException("Bad part count");
        if (index < 0 || index >= count) throw new ProtocolException("Bad part index");
        if (chunk == null) chunk = "";
        if (chunk.length() > Protocol.MAX_CHARS_PER_IPC_CHUNK) {
            throw new ProtocolException("Chunk too large");
        }
        if (!"en".equals(src) || !"ar".equals(dst)) {
            throw new ProtocolException("Only en -> ar is enabled in v1");
        }

        String key = uid + ":" + id;
        Pending p = pending.get(key);
        if (p == null) {
            if (pending.size() >= Protocol.MAX_PENDING_REQUESTS) {
                throw new ProtocolException("Too many pending requests");
            }
            p = new Pending(uid, id, count, src, dst, replyTo);
            pending.put(key, p);
        } else if (p.partCount != count || !p.source.equals(src) || !p.target.equals(dst)
                || !p.replyTo.getBinder().equals(replyTo.getBinder())) {
            pending.remove(key);
            throw new ProtocolException("Request metadata changed between chunks");
        }

        if (p.chunks[index] == null) {
            p.chunks[index] = chunk;
            p.received++;
            p.charCount += chunk.length();
            if (p.charCount > Protocol.MAX_REQUEST_CHARS) {
                pending.remove(key);
                throw new ProtocolException("Request too large");
            }
        } else if (!p.chunks[index].equals(chunk)) {
            pending.remove(key);
            throw new ProtocolException("Conflicting duplicate chunk");
        }

        if (p.received != p.partCount) return null;

        StringBuilder text = new StringBuilder(p.charCount);
        for (String part : p.chunks) {
            if (part == null) throw new ProtocolException("Missing chunk");
            text.append(part);
        }
        pending.remove(key);
        return new CompletedRequest(uid, id, src, dst, text.toString(), p.replyTo);
    }

    private void expireOld() {
        long now = SystemClock.elapsedRealtime();
        pending.entrySet().removeIf(e -> now - e.getValue().createdAt > Protocol.REQUEST_TTL_MS);
    }

    private static String safeString(String s) { return s == null ? "" : s; }
}
