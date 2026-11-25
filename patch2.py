p = 'src/port/sysui/Hook.java'
s = open(p).read()
assert 'PORT169 ID ' not in s
old = '''    static void selfTest(Context ctx) {
'''
new = '''    static String nameOf(Resources r, int id) {
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
'''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w').write(s)
print('diag added')
