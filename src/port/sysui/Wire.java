package port.sysui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public class Wire {
    static final Map<Class<?>, Object> cache = new HashMap<>();
    static final Set<Class<?>> building = new HashSet<>();
    public static Object oplusDep;
    public static Class<?> aospDep;
    static int logs = 0;

    static void log(String s) {
        if (logs++ < 60) XposedBridge.log("PORT169 WIRE " + s);
    }

    static boolean ours(Class<?> c) {
        return Hook.loader != null && c.getClassLoader() == Hook.loader;
    }

    public static synchronized Object get(Class<?> c) {
        return get(c, 0);
    }

    static Object get(Class<?> c, int depth) {
        Object hit = cache.get(c);
        if (hit != null) return hit;
        boolean mine = ours(c);
        Object o = mine ? build(c, depth) : system(c);
        if (o != null && mine) cache.put(c, o);
        return o;
    }

    static Object system(Class<?> c) {
        if (c == Context.class) return Hook.portContext;
        if (c == android.content.res.Resources.class) return Hook.res169;
        if (c == Looper.class) return Looper.getMainLooper();
        if (c == Handler.class) return new Handler(Looper.getMainLooper());
        if (c == Executor.class) {
            final Handler h = new Handler(Looper.getMainLooper());
            return (Executor) h::post;
        }
        if (oplusDep != null) {
            try {
                Object r = XposedHelpers.callMethod(oplusDep, "getDependency", c);
                if (r != null) return r;
            } catch (Throwable ignored) { }
        }
        if (aospDep != null) {
            try {
                Object r = XposedHelpers.callStaticMethod(aospDep, "get", c);
                if (r != null) return r;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    static Object zero(Class<?> t) {
        if (t == boolean.class) return Boolean.FALSE;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == char.class) return (char) 0;
        if (t == byte.class) return (byte) 0;
        if (t == short.class) return (short) 0;
        return null;
    }

    static Class<?> typeArg(Type g) {
        if (!(g instanceof ParameterizedType)) return null;
        Type a = ((ParameterizedType) g).getActualTypeArguments()[0];
        if (a instanceof WildcardType) {
            Type[] ub = ((WildcardType) a).getUpperBounds();
            a = ub.length > 0 ? ub[0] : null;
        }
        if (a instanceof ParameterizedType) a = ((ParameterizedType) a).getRawType();
        return a instanceof Class ? (Class<?>) a : null;
    }

    static Object resolve(Class<?> t, Type g, int depth) {
        String n = t.getName();
        if (n.equals("dagger.Lazy") || n.equals("javax.inject.Provider") || n.equals("kotlin.Lazy")) {
            final Class<?> target = typeArg(g);
            if (target == null) return null;
            return Proxy.newProxyInstance(t.getClassLoader(), new Class<?>[]{t}, (proxy, m, a) -> {
                String mn = m.getName();
                if (mn.equals("get") || mn.equals("getValue")) return Wire.get(target);
                if (mn.equals("isInitialized")) return Boolean.TRUE;
                if (mn.equals("hashCode")) return System.identityHashCode(proxy);
                if (mn.equals("equals")) return proxy == a[0];
                if (mn.equals("toString")) return "PortLazy(" + target.getName() + ")";
                return null;
            });
        }
        return get(t, depth);
    }

    static Object build(Class<?> c, int depth) {
        if (depth > 12) { log("too deep at " + c.getName()); return null; }
        if (!building.add(c)) { log("cycle at " + c.getName()); return null; }
        try {
            List<Constructor<?>> cs = new ArrayList<>();
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                if (k.isSynthetic()) continue;
                boolean marker = false;
                for (Class<?> p : k.getParameterTypes()) {
                    if (p.getName().equals("kotlin.jvm.internal.DefaultConstructorMarker")) marker = true;
                }
                if (!marker) cs.add(k);
            }
            cs.sort((a, b) -> b.getParameterTypes().length - a.getParameterTypes().length);
            List<String> missing = new ArrayList<>();
            for (Constructor<?> k : cs) {
                Class<?>[] pt = k.getParameterTypes();
                Type[] gt = k.getGenericParameterTypes();
                Object[] args = new Object[pt.length];
                boolean ok = true;
                for (int i = 0; i < pt.length; i++) {
                    Type g = gt.length == pt.length ? gt[i] : pt[i];
                    Object a = resolve(pt[i], g, depth + 1);
                    if (a == null && !pt[i].isPrimitive()) {
                        ok = false;
                        missing.add(pt[i].getName());
                        continue;
                    }
                    args[i] = a == null ? zero(pt[i]) : a;
                }
                if (!ok) continue;
                try {
                    k.setAccessible(true);
                    Object o = k.newInstance(args);
                    log("built " + c.getName());
                    return o;
                } catch (Throwable t) {
                    Throwable r = t;
                    while (r.getCause() != null) r = r.getCause();
                    log("ctor failed " + c.getName() + " : " + r);
                }
            }
            log("cannot build " + c.getName() + " missing=" + missing);
            return null;
        } finally {
            building.remove(c);
        }
    }
}
