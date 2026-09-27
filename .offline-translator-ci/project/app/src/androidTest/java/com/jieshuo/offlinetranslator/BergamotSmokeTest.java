package com.jieshuo.offlinetranslator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.jieshuo.offlinetranslator.engine.BergamotEngine;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

/** End-to-end Android smoke tests for the bundled Bergamot JNI runtime and EN->AR model. */
@RunWith(AndroidJUnit4.class)
public final class BergamotSmokeTest {
    private static final Pattern ARABIC =
            Pattern.compile("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF]");

    @Test
    public void translatesEnglishToArabicThroughRealJniModel() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        BergamotEngine engine = new BergamotEngine();
        try {
            engine.load(context);
            assertTrue(engine.isLoaded());
            String source = "Hello world. This is an offline translation test.";
            String out = engine.translate(source);
            assertFalse(out.trim().isEmpty());
            assertFalse(source.equals(out));
            assertTrue("Expected Arabic script in output: " + out, ARABIC.matcher(out).find());
            System.out.println("BERGAMOT_ANDROID_SMOKE_TRANSLATION=" + out);
        } finally {
            engine.close();
        }
    }

    @Test
    public void preservesMixedProtectedContentExactly() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        BergamotEngine engine = new BergamotEngine();
        try {
            engine.load(context);
            String source =
                    "Open file.txt at https://example.com/a?x=1 and email mail@test.example at 12:30. مرحبا 😀";
            String out = engine.translate(source);
            assertTrue("URL changed: " + out, out.contains("https://example.com/a?x=1"));
            assertTrue("email changed: " + out, out.contains("mail@test.example"));
            assertTrue("filename changed: " + out, out.contains("file.txt"));
            assertTrue("number/time changed: " + out, out.contains("12:30"));
            assertTrue("existing Arabic changed: " + out, out.contains("مرحبا"));
            assertTrue("emoji changed: " + out, out.contains("😀"));
            assertTrue("Expected translated Arabic text: " + out, ARABIC.matcher(out).find());
            System.out.println("BERGAMOT_ANDROID_SMOKE_MIXED=" + out);
        } finally {
            engine.close();
        }
    }
}
