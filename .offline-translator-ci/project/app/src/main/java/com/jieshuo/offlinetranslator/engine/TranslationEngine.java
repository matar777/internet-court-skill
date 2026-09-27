package com.jieshuo.offlinetranslator.engine;

import android.content.Context;

public interface TranslationEngine extends AutoCloseable {
    void load(Context context) throws Exception;
    boolean isLoaded();
    String translate(String text) throws Exception;
    @Override void close();
}
