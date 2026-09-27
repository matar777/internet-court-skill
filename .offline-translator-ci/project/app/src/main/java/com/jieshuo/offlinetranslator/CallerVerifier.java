package com.jieshuo.offlinetranslator;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import java.security.MessageDigest;
import java.util.Arrays;

public final class CallerVerifier {
    public static final String JIESHUO_PACKAGE = "com.nirenr.talkman";
    private static final String CERT_A = "CCD370776742C35762BEE8C49D847EA9";
    private static final String CERT_B = "E20036D20890F787DF49F5FD0B4487B9";
    private final Context context;

    public CallerVerifier(Context context) { this.context = context.getApplicationContext(); }

    public boolean isAuthorizedUid(int uid) {
        if (uid < 0) return false;
        PackageManager pm = context.getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages == null || !Arrays.asList(packages).contains(JIESHUO_PACKAGE)) return false;
        return isExpectedJieshuoSignature(pm);
    }

    public static String installedJieshuoStatus(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(JIESHUO_PACKAGE, 0);
            return isExpectedJieshuoSignature(pm)
                    ? "Jieshuo موجود والتوقيع مطابق."
                    : "Jieshuo موجود لكن توقيعه لا يطابق النسخة المعتمدة.";
        } catch (PackageManager.NameNotFoundException e) {
            return "Jieshuo غير موجود في هذا الملف الشخصي.";
        }
    }

    private static boolean isExpectedJieshuoSignature(PackageManager pm) {
        try {
            PackageInfo info = pm.getPackageInfo(JIESHUO_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo signingInfo = info.signingInfo;
            if (signingInfo == null) return false;
            Signature[] signatures = signingInfo.hasMultipleSigners()
                    ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
            if (signatures == null) return false;
            String expected = CERT_A + CERT_B;
            for (Signature signature : signatures) {
                if (expected.equals(sha256Hex(signature.toByteArray()))) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02X", b & 0xff));
        return sb.toString();
    }
}
