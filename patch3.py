def rw(p, f):
    s = open(p).read(); s = f(s); open(p, 'w').write(s)

def hook(s):
    assert 'ResourcesImpl' not in s
    s = s.replace('    public static Context portContext;\n', '    public static Context portContext;\n    static int fallbackLogs = 0;\n', 1)
    anchor = '        new Thread(() -> {\n            try { setup('
    assert anchor in s
    block = '''        try {
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
'''
    s = s.replace(anchor, block + anchor, 1)
    print('hook patched'); return s

def ctx(s):
    assert 'createConfigurationContext' not in s
    s = s.replace('import android.content.res.AssetManager;', 'import android.content.res.AssetManager;\nimport android.content.res.Configuration;', 1)
    s = s.replace('import android.view.LayoutInflater;', 'import android.view.Display;\nimport android.view.LayoutInflater;', 1)
    s = s.replace('    @Override public Resources getResources()', '    @Override public Context createConfigurationContext(Configuration c) { return this; }\n    @Override public Context createDisplayContext(Display d) { return this; }\n    @Override public Resources getResources()', 1)
    print('ctx patched', 'createDisplayContext' in s); return s

d = 'src/port/sysui/'
rw(d + 'Hook.java', hook); rw(d + 'PortContext.java', ctx)
