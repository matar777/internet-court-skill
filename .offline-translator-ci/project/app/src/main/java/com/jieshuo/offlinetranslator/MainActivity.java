package com.jieshuo.offlinetranslator;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.jieshuo.offlinetranslator.engine.ModelInstaller;

public final class MainActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.START);
        TextView title = new TextView(this);
        title.setText("مترجم جيشو دون اتصال");
        title.setTextSize(24);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, dp(16), 0, 0);
        status.setMovementMethod(new ScrollingMovementMethod());
        boolean assets = ModelInstaller.bundledAssetsPresent(this);
        status.setText("الحالة:\n• المحرك: Bergamot محلي بالكامل؛ التطبيق لا يطلب إذن INTERNET.\n• نموذج EN→AR داخل APK: " + (assets ? "موجود" : "غير مضاف بعد") + "\n• " + CallerVerifier.installedJieshuoStatus(this) + "\n• الخدمة: Messenger IPC + تحقق UID وتوقيع Jieshuo.");
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
