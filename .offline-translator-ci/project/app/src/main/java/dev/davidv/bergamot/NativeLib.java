package dev.davidv.bergamot;

public final class NativeLib {
    static { System.loadLibrary("bergamot-sys"); }
    public NativeLib() { initializeService(); }
    public native void loadModelIntoCache(String cfg, String key);
    public native String[] translateMultiple(String[] inputs, String key);
    private native void initializeService();
    public native void cleanup();
}
