package port.sysui;

import dalvik.system.DexClassLoader;

public class PortLoader extends DexClassLoader {
    static final String[] PREFIX = {"com.oplus.posteffect.", "com.oplus.effectengine."};
    static final String[] EXACT = {
        "com.android.systemui.statusbar.notification.stack.ExpandableViewState",
        "com.android.systemui.statusbar.notification.stack.AnimationFilter"};

    public PortLoader(String dexPath, String opt, String lib, ClassLoader parent) {
        super(dexPath, opt, lib, parent);
    }

    static boolean shadow(String n) {
        for (String p : PREFIX) if (n.startsWith(p)) return true;
        for (String e : EXACT) if (n.equals(e) || n.startsWith(e + "$")) return true;
        return false;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (shadow(name)) {
            synchronized (this) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try { c = findClass(name); } catch (ClassNotFoundException ignored) { }
                }
                if (c != null) return c;
            }
        }
        return super.loadClass(name, resolve);
    }
}
