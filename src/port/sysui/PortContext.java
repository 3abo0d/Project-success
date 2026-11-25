package port.sysui;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.Display;
import android.view.LayoutInflater;

public class PortContext extends ContextWrapper {
    private final Resources res;
    private final Resources.Theme theme;
    private final ClassLoader loader;

    public PortContext(Context base, Resources res, ClassLoader loader, int themeId) {
        super(base);
        this.res = res;
        this.loader = loader;
        Resources.Theme t = res.newTheme();
        if (themeId != 0) t.applyStyle(themeId, true);
        this.theme = t;
    }

    @Override public Context getApplicationContext() { return this; }
    @Override public Context createConfigurationContext(Configuration c) { return this; }
    @Override public Context createDisplayContext(Display d) { return this; }
    @Override public Resources getResources() { return res; }
    @Override public AssetManager getAssets() { return res.getAssets(); }
    @Override public Resources.Theme getTheme() { return theme; }
    @Override public ClassLoader getClassLoader() { return loader; }

    @Override
    public Object getSystemService(String name) {
        if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
            return LayoutInflater.from(getBaseContext()).cloneInContext(this);
        }
        return super.getSystemService(name);
    }
}
