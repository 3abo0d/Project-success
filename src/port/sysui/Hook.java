package port.sysui;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Hook implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    static final String PKG = "com.android.systemui";
    static String modulePath;
    public static ClassLoader loader;
    public static Resources res169;
    public static Context portContext;
    static int fallbackLogs = 0;

    public void initZygote(IXposedHookZygoteInit.StartupParam s) { modulePath = s.modulePath; }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!PKG.equals(p.packageName)) return;
        final String dataDir = p.appInfo.dataDir;
        final ClassLoader parent = p.classLoader;
        try {
            Class<?> k = XposedHelpers.findClass("com.oplus.systemui.keyguard.ui.view.OplusKeyguardBottomAreaView", parent);
            XposedBridge.hookAllConstructors(k, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final Object o = param.thisObject;
                    if (o instanceof FrameLayout) {
                        final FrameLayout area = (FrameLayout) o;
                        area.post(() -> attach(area, 0));
                    } else {
                        XposedBridge.log("PORT169 bottom area is " + o.getClass().getName() + ", not a FrameLayout");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("PORT169 bottom area hook failed: " + t);
        }
        try {
            Class<?> impl = XposedHelpers.findClass("android.content.res.ResourcesImpl", null);
            XposedBridge.hookAllMethods(impl, "getValue", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Throwable t = param.getThrowable();
                    if (!(t instanceof Resources.NotFoundException) || res169 == null) return;
                    if (param.args.length != 3 || !(param.args[0] instanceof Integer)) return;
                    try {
                        Object impl169 = XposedHelpers.getObjectField(res169, "mResourcesImpl");
                        if (impl169 == null || impl169 == param.thisObject) return;
                        XposedHelpers.callMethod(impl169, "getValue", param.args[0], param.args[1], param.args[2]);
                        param.setResult(null);
                        if (fallbackLogs++ < 25) {
                            XposedBridge.log("PORT169 fallback id=0x" + Integer.toHexString((Integer) param.args[0]));
                        }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("PORT169 resource fallback hook failed: " + t);
        }
        try {
            Class<?> depImpl = XposedHelpers.findClass("com.oplus.systemui.OplusDependencyImpl", parent);
            try { Wire.aospDep = XposedHelpers.findClass("com.android.systemui.Dependency", parent); } catch (Throwable ignored) { }
            XposedBridge.hookAllMethods(depImpl, "createDependency", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Wire.oplusDep = param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Class) {
                            Class<?> want = (Class<?>) param.args[0];
                            if (Wire.ours(want)) {
                                Object o = Wire.get(want);
                                if (o != null) param.setResult(o);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("PORT169 dep hook error: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("PORT169 dependency hook failed: " + t);
        }
        new Thread(() -> {
            try { setup(dataDir, parent); } catch (Throwable t) { XposedBridge.log("PORT169 fatal: " + t); }
        }, "port169").start();
    }

    static File extract(ZipFile z, String entry, File dir) throws IOException {
        ZipEntry en = z.getEntry(entry);
        if (en == null) throw new FileNotFoundException(entry);
        File out = new File(dir, entry.substring(entry.lastIndexOf('/') + 1));
        if (!out.exists() || out.length() != en.getSize()) {
            out.setWritable(true);
            out.delete();
            try (InputStream in = z.getInputStream(en); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[1 << 16];
                int r;
                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
            }
            out.setReadOnly();
        }
        return out;
    }

    static Context waitForApp() throws Exception {
        Class<?> at = XposedHelpers.findClass("android.app.ActivityThread", null);
        for (int i = 0; i < 120; i++) {
            Object app = XposedHelpers.callStaticMethod(at, "currentApplication");
            if (app instanceof Context) return (Context) app;
            Thread.sleep(500);
        }
        return null;
    }

    static void setup(String dataDir, ClassLoader parent) throws Exception {
        XposedBridge.log("PORT169 start");
        File dir = new File(dataDir, "code_cache/port169");
        dir.mkdirs();
        List<String> dex = new ArrayList<>();
        File resApk;
        try (ZipFile z = new ZipFile(modulePath)) {
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements();) {
                String n = e.nextElement().getName();
                if (n.startsWith("assets/dex/") && n.endsWith(".dex")) dex.add(extract(z, n, dir).getAbsolutePath());
            }
            resApk = extract(z, "assets/res169.apk", dir);
        }
        loader = new PortLoader(android.text.TextUtils.join(":", dex), null, null, parent);

        Context app = waitForApp();
        if (app == null) { XposedBridge.log("PORT169 no application context"); return; }

        AssetManager am = (AssetManager) XposedHelpers.newInstance(AssetManager.class);
        Object cookie = XposedHelpers.callMethod(am, "addAssetPath", resApk.getAbsolutePath());
        if (!(cookie instanceof Integer) || (Integer) cookie == 0) {
            XposedBridge.log("PORT169 addAssetPath failed: " + cookie);
            return;
        }
        Resources base = app.getResources();
        res169 = new Resources(am, base.getDisplayMetrics(), base.getConfiguration());

        int themeId = 0;
        String[] themes = {"Theme.SystemUI", "Theme.SystemUI.Dialog", "Theme.SystemUI.LightWallpaper"};
        for (String t : themes) {
            themeId = res169.getIdentifier(t, "style", PKG);
            if (themeId != 0) { XposedBridge.log("PORT169 theme " + t + " = 0x" + Integer.toHexString(themeId)); break; }
        }
        final Context pc = new PortContext(app, res169, loader, themeId);
        portContext = pc;
        new Handler(Looper.getMainLooper()).post(() -> selfTest(pc));
    }

    static String describe(Throwable c) {
        StringBuilder b = new StringBuilder(String.valueOf(c));
        StackTraceElement[] st = c.getStackTrace();
        for (int i = 0; i < st.length && i < 6; i++) b.append(" | ").append(st[i]);
        return b.toString();
    }

    static String nameOf(Resources r, int id) {
        try { return r.getResourceName(id); } catch (Throwable t) { return "MISSING"; }
    }

    static void selfTest(Context ctx) {
        try {
            Resources sys = ((android.content.ContextWrapper) ctx).getBaseContext().getResources();
            int[] ids = {0x7f060160, 0x7f0702b5, 0x7f0d0239, 0x7f060001, 0x7f070001};
            for (int id : ids) {
                XposedBridge.log("PORT169 ID 0x" + Integer.toHexString(id) + " ours=" + nameOf(res169, id)
                    + " systemui=" + nameOf(sys, id));
            }
            XposedBridge.log("PORT169 cfg ours=" + res169.getConfiguration() + " systemui=" + sys.getConfiguration());
        } catch (Throwable t) {
            XposedBridge.log("PORT169 diag fail: " + t);
        }
        String[] names = {"layout_keyguard_capsule_mask", "layout_keyguard_immersed_mask",
            "capsule_item_view", "oplus_capsule_and_immersive_card_container"};
        int ok = 0, bad = 0;
        for (String n : names) {
            try {
                int id = ctx.getResources().getIdentifier(n, "layout", PKG);
                if (id == 0) throw new IllegalStateException("layout id not found");
                FrameLayout root = new FrameLayout(ctx);
                LayoutInflater.from(ctx).inflate(id, root, true);
                XposedBridge.log("PORT169 INFLATE ok " + n + " id=0x" + Integer.toHexString(id)
                    + " children=" + root.getChildCount());
                ok++;
            } catch (Throwable t) {
                Throwable c = t;
                while (c.getCause() != null) c = c.getCause();
                XposedBridge.log("PORT169 INFLATE FAIL " + n + " : " + describe(c));
                bad++;
            }
        }
        XposedBridge.log("PORT169 selftest ok=" + ok + " bad=" + bad);
    }

    static void attach(final FrameLayout area, final int tries) {
        try {
            if (portContext == null) {
                if (tries < 60) area.postDelayed(() -> attach(area, tries + 1), 500);
                return;
            }
            if (area.findViewWithTag("port169") != null) return;
            float d = area.getResources().getDisplayMetrics().density;
            FrameLayout box = new FrameLayout(portContext);
            box.setTag("port169");
            box.setBackgroundColor(0x4400FF00);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) (300 * d), (int) (90 * d), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lp.bottomMargin = (int) (120 * d);
            area.addView(box, lp);
            XposedBridge.log("PORT169 box attached to lockscreen bottom area");
            try {
                int id = portContext.getResources().getIdentifier(
                    "oplus_capsule_and_immersive_card_container", "layout", PKG);
                LayoutInflater.from(portContext).inflate(id, box, true);
                XposedBridge.log("PORT169 capsule container inflated into box");
            } catch (Throwable t) {
                Throwable c = t;
                while (c.getCause() != null) c = c.getCause();
                XposedBridge.log("PORT169 container inflate FAIL : " + describe(c));
            }
        } catch (Throwable t) {
            XposedBridge.log("PORT169 attach fail: " + t);
        }
    }
}
