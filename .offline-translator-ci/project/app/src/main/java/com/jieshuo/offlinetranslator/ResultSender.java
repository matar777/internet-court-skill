package com.jieshuo.offlinetranslator;

import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public final class ResultSender {
    private ResultSender() {}
    public static void sendResult(Messenger replyTo, String requestId, String text) throws RemoteException {
        if (text == null) text = "";
        int chunkSize = Protocol.MAX_CHARS_PER_IPC_CHUNK;
        int count = Math.max(1, (text.length() + chunkSize - 1) / chunkSize);
        if (count > Protocol.MAX_PARTS) {
            sendError(replyTo, requestId, Protocol.ERR_ENGINE, "Translated result exceeds IPC safety limit");
            return;
        }
        for (int i = 0; i < count; i++) {
            int start = i * chunkSize;
            int end = Math.min(text.length(), start + chunkSize);
            Bundle b = new Bundle();
            b.putInt(Protocol.KEY_VERSION, Protocol.VERSION);
            b.putString(Protocol.KEY_REQUEST_ID, requestId);
            b.putInt(Protocol.KEY_PART_INDEX, i);
            b.putInt(Protocol.KEY_PART_COUNT, count);
            b.putString(Protocol.KEY_TEXT, text.substring(start, end));
            Message m = Message.obtain(null, Protocol.MSG_RESULT_CHUNK);
            m.setData(b);
            replyTo.send(m);
        }
    }
    public static void sendError(Messenger replyTo, String requestId, String code, String message) {
        if (replyTo == null) return;
        try {
            Bundle b = new Bundle();
            b.putInt(Protocol.KEY_VERSION, Protocol.VERSION);
            b.putString(Protocol.KEY_REQUEST_ID, requestId == null ? "" : requestId);
            b.putString(Protocol.KEY_ERROR_CODE, code);
            b.putString(Protocol.KEY_ERROR_MESSAGE, message == null ? "" : message);
            Message m = Message.obtain(null, Protocol.MSG_ERROR);
            m.setData(b);
            replyTo.send(m);
        } catch (RemoteException ignored) {}
    }
    public static void sendStatus(Messenger replyTo, boolean ready) {
        if (replyTo == null) return;
        try {
            Bundle b = new Bundle();
            b.putInt(Protocol.KEY_VERSION, Protocol.VERSION);
            b.putBoolean(Protocol.KEY_ENGINE_READY, ready);
            Message m = Message.obtain(null, Protocol.MSG_STATUS);
            m.setData(b);
            replyTo.send(m);
        } catch (RemoteException ignored) {}
    }
}
