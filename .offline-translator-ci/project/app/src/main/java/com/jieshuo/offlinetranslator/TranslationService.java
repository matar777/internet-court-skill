package com.jieshuo.offlinetranslator;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import com.jieshuo.offlinetranslator.engine.BergamotEngine;
import com.jieshuo.offlinetranslator.engine.TranslationEngine;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class TranslationService extends Service {
    private CallerVerifier callerVerifier;
    private final RequestAssembler assembler = new RequestAssembler();
    private final TranslationEngine engine = new BergamotEngine();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4),
            r -> { Thread t = new Thread(r, "JieshuoTranslatorEngine"); t.setPriority(Thread.NORM_PRIORITY); return t; });
    private Messenger messenger;

    @Override public void onCreate() {
        super.onCreate();
        callerVerifier = new CallerVerifier(this);
        messenger = new Messenger(new IncomingHandler(Looper.getMainLooper()));
    }
    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }
    @Override public void onDestroy() { worker.shutdownNow(); engine.close(); super.onDestroy(); }

    private final class IncomingHandler extends Handler {
        IncomingHandler(Looper looper) { super(looper); }
        @Override public void handleMessage(Message msg) {
            final int uid = msg.sendingUid;
            if (!callerVerifier.isAuthorizedUid(uid)) {
                ResultSender.sendError(msg.replyTo, requestId(msg), Protocol.ERR_UNAUTHORIZED, "Caller is not the approved Jieshuo build");
                return;
            }
            if (msg.what == Protocol.MSG_PING) { ResultSender.sendStatus(msg.replyTo, engine.isLoaded()); return; }
            if (msg.what != Protocol.MSG_TRANSLATE_CHUNK) {
                ResultSender.sendError(msg.replyTo, requestId(msg), Protocol.ERR_PROTOCOL, "Unknown message code");
                return;
            }
            try {
                RequestAssembler.CompletedRequest request = assembler.accept(uid, msg.getData(), msg.replyTo);
                if (request != null) submitTranslation(request);
            } catch (RequestAssembler.ProtocolException e) {
                String code = e.getMessage() != null && e.getMessage().contains("en -> ar")
                        ? Protocol.ERR_UNSUPPORTED_LANGUAGE : Protocol.ERR_PROTOCOL;
                ResultSender.sendError(msg.replyTo, requestId(msg), code, e.getMessage());
            }
        }
    }

    private void submitTranslation(RequestAssembler.CompletedRequest request) {
        try {
            worker.execute(() -> {
                try {
                    if (!engine.isLoaded()) engine.load(getApplicationContext());
                    ResultSender.sendResult(request.replyTo, request.requestId, engine.translate(request.text));
                } catch (java.io.IOException e) {
                    ResultSender.sendError(request.replyTo, request.requestId, Protocol.ERR_MODEL, e.getMessage());
                } catch (Throwable t) {
                    ResultSender.sendError(request.replyTo, request.requestId, Protocol.ERR_ENGINE,
                            t.getClass().getSimpleName() + ": " + safeMessage(t));
                }
            });
        } catch (RejectedExecutionException e) {
            ResultSender.sendError(request.replyTo, request.requestId, Protocol.ERR_BUSY, "Translation queue is full");
        }
    }
    private static String requestId(Message msg) {
        try {
            Bundle b = msg.getData();
            String id = b == null ? null : b.getString(Protocol.KEY_REQUEST_ID);
            return id == null ? "" : id;
        } catch (Exception e) { return ""; }
    }
    private static String safeMessage(Throwable t) { String m = t.getMessage(); return m == null ? "Unknown engine error" : m; }
}
