def rw(p, f):
    s = open(p).read(); s = f(s); open(p, 'w').write(s)

def hook(s):
    a = s.count('" : " + c);'); b = s.count('FAIL : " + c);')
    s = s.replace('" : " + c);', '" : " + describe(c));').replace('FAIL : " + c);', 'FAIL : " + describe(c));')
    helper = '''    static String describe(Throwable c) {
        StringBuilder b = new StringBuilder(String.valueOf(c));
        StackTraceElement[] st = c.getStackTrace();
        for (int i = 0; i < st.length && i < 6; i++) b.append(" | ").append(st[i]);
        return b.toString();
    }

'''
    assert 'describe(Throwable' not in s
    s = s.replace('    static void selfTest(', helper + '    static void selfTest(', 1)
    print('hook edits', a, b); return s

def ctx(s):
    assert 'getApplicationContext' not in s
    s = s.replace('    @Override public Resources getResources()', '    @Override public Context getApplicationContext() { return this; }\n    @Override public Resources getResources()', 1)
    print('ctx edit', 'getApplicationContext' in s); return s

def loader(s):
    old = '''        "com.android.systemui.statusbar.notification.stack.AnimationFilter"};'''
    new = '''        "com.android.systemui.statusbar.notification.stack.AnimationFilter",
        "com.android.systemui.statusbar.notification.AnimatableProperty",
        "com.oplus.systemui.R", "com.android.systemui.R"};'''
    assert old in s
    print('loader edit'); return s.replace(old, new, 1)

d = 'src/port/sysui/'
rw(d + 'Hook.java', hook); rw(d + 'PortContext.java', ctx); rw(d + 'PortLoader.java', loader)
