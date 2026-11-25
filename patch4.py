p = 'src/port/sysui/Hook.java'
s = open(p).read()
assert 'OplusDependencyImpl' not in s
anchor = '        new Thread(() -> {\n            try { setup('
assert anchor in s
block = '''        try {
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
'''
s = s.replace(anchor, block + anchor, 1)
open(p, 'w').write(s)
print('dependency hook added')
