package com.jieshuo.offlinetranslator;

public final class Protocol {
    private Protocol() {}
    public static final int VERSION = 1;
    public static final int MSG_TRANSLATE_CHUNK = 100;
    public static final int MSG_RESULT_CHUNK = 101;
    public static final int MSG_ERROR = 102;
    public static final int MSG_PING = 103;
    public static final int MSG_STATUS = 104;
    public static final String KEY_VERSION = "v";
    public static final String KEY_REQUEST_ID = "id";
    public static final String KEY_PART_INDEX = "i";
    public static final String KEY_PART_COUNT = "n";
    public static final String KEY_SOURCE = "src";
    public static final String KEY_TARGET = "dst";
    public static final String KEY_TEXT = "text";
    public static final String KEY_ERROR_CODE = "code";
    public static final String KEY_ERROR_MESSAGE = "message";
    public static final String KEY_ENGINE_READY = "ready";
    public static final int MAX_CHARS_PER_IPC_CHUNK = 16 * 1024;
    public static final int MAX_PARTS = 160;
    public static final int MAX_REQUEST_CHARS = 2 * 1024 * 1024;
    public static final int MAX_PENDING_REQUESTS = 4;
    public static final long REQUEST_TTL_MS = 300_000L;
    public static final String ERR_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERR_PROTOCOL = "PROTOCOL";
    public static final String ERR_UNSUPPORTED_LANGUAGE = "UNSUPPORTED_LANGUAGE";
    public static final String ERR_BUSY = "BUSY";
    public static final String ERR_MODEL = "MODEL";
    public static final String ERR_ENGINE = "ENGINE";
}
