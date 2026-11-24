package port.sysui;

import dalvik.system.DexClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class Hook implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    static String modulePath;
    public static ClassLoader loader;

    public void initZygote(IXposedHookZygoteInit.StartupParam s) { modulePath = s.modulePath; }

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam p) {
        if (!"com.android.systemui".equals(p.packageName)) return;
        new Thread(() -> {
            try { go(p); } catch (Throwable t) { XposedBridge.log("PORT169 fatal: " + t); }
        }).start();
    }

    static void go(XC_LoadPackage.LoadPackageParam p) throws Exception {
        XposedBridge.log("PORT169 start");
        File dir = new File(p.appInfo.dataDir, "code_cache/port169");
        dir.mkdirs();
        ZipFile z = new ZipFile(modulePath);
        List<String> paths = new ArrayList<>();
        for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements();) {
            ZipEntry en = e.nextElement();
            String n = en.getName();
            if (!n.startsWith("assets/dex/") || !n.endsWith(".dex")) continue;
            File out = new File(dir, n.substring(n.lastIndexOf('/') + 1));
            if (!out.exists() || out.length() != en.getSize()) {
                out.setWritable(true);
                out.delete();
                try (InputStream in = z.getInputStream(en); OutputStream o = new FileOutputStream(out)) {
                    byte[] buf = new byte[1 << 16]; int r;
                    while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
                }
                out.setReadOnly();
            }
            paths.add(out.getAbsolutePath());
        }
        loader = new DexClassLoader(android.text.TextUtils.join(":", paths), null, null, p.classLoader);
        BufferedReader br = new BufferedReader(new InputStreamReader(z.getInputStream(z.getEntry("assets/carry.txt"))));
        String line; int ok = 0, bad = 0;
        Map<String, Integer> kinds = new TreeMap<>();
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.startsWith("L") || !line.endsWith(";")) continue;
            String name = line.substring(1, line.length() - 1).replace('/', '.');
            try {
                Class<?> c = Class.forName(name, false, loader);
                c.getDeclaredFields(); c.getDeclaredMethods(); c.getDeclaredConstructors();
                ok++;
            } catch (Throwable t) {
                bad++;
                String k = t.getClass().getSimpleName();
                kinds.put(k, kinds.containsKey(k) ? kinds.get(k) + 1 : 1);
                if (bad <= 60) XposedBridge.log("PORT169 FAIL " + name + " : " + t);
            }
        }
        XposedBridge.log("PORT169 done ok=" + ok + " bad=" + bad + " " + kinds);
    }
}
