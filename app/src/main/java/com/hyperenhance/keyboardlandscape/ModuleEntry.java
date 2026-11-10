package com.hyperenhance.keyboardlandscape;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 小米「全面屏键盘优化」横屏开关（Vector / libxposed 102 模块）。
 *
 * <p>底栏由 {@code com.miui.inputmethod.InputMethodBottomManager} 绘制，该类位于
 * MIUIFrequentPhrase.apk，由 {@code InputMethodServiceInjector} 通过
 * {@code InputMethodModuleManager.loadDex()} 加载进输入法进程。
 *
 * <p>{@code addMiuiBottomView(...)} 在横屏直接 return。本模块把
 * {@code reflectModule} 的 Context 参数换成竖屏 Configuration Context，让原方法
 * 走完整竖屏实现。开关关闭时不做任何替换。
 */
public final class ModuleEntry extends XposedModule {

    private static final String TAG = "HyperEnhanceKeyboardLandscape";
    private static final String INJECTOR_CLASS =
            "android.inputmethodservice.InputMethodServiceInjector";
    private static final String BOTTOM_MANAGER_CLASS =
            "com.miui.inputmethod.InputMethodBottomManager";
    private static final String ADD_BOTTOM_VIEW = "addMiuiBottomView";
    private static final String REFLECT_MODULE = "reflectModule";
    private static final String CHANGE_BOTTOM_VIEW = "changeViewForMiuiBottom";

    private boolean installed;
    private boolean visibilityHookInstalled;
    private boolean landscapeEnabled = true;
    private SharedPreferences prefs;
    private Field helperField;
    private Field areaField;

    @Override
    public boolean onHotReloading(XposedModuleInterface.HotReloadingParam param) {
        return true;
    }

    @Override
    public void onHotReloaded(XposedModuleInterface.HotReloadedParam param) {
        // Keep the existing hooks: package callbacks are not replayed after hot reload.
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!param.isFirstPackage() || installed) {
            return;
        }
        installed = true;
        try {
            ClassLoader loader = param.getDefaultClassLoader();
            Class<?> injector = Class.forName(INJECTOR_CLASS, false, loader);
            Method target = findMethod(injector, REFLECT_MODULE,
                    String.class, String.class, Class[].class, Object[].class);
            if (target == null) {
                log(Log.WARN, TAG, REFLECT_MODULE + " not found in " + INJECTOR_CLASS);
                return;
            }
            hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();
                        boolean changed = forcePortraitContext(args);
                        Object result = changed ? chain.proceed(args) : chain.proceed();
                        if (isAddBottomView(args)) {
                            Context moduleContext = contextOf(args);
                            if (moduleContext != null) {
                                installVisibilityHook(moduleContext.getClassLoader());
                                applyBarVisibility();
                            }
                        }
                        return result;
                    });
            loadPreferences();
            log(Log.INFO, TAG, "hooked " + INJECTOR_CLASS + "#" + REFLECT_MODULE);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "inactive: " + t, t);
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Context contextOf(Object[] args) {
        if (args.length != 4 || !(args[3] instanceof Object[])) {
            return null;
        }
        Object[] values = (Object[]) args[3];
        return values.length > 0 && values[0] instanceof Context ? (Context) values[0] : null;
    }

    private static boolean isAddBottomView(Object[] args) {
        return args.length == 4
                && BOTTOM_MANAGER_CLASS.equals(args[0])
                && ADD_BOTTOM_VIEW.equals(args[1]);
    }

    private boolean forcePortraitContext(Object[] args) {
        try {
            if (!isAddBottomView(args)) {
                return false;
            }
            Object[] values = (Object[]) args[3];
            if (values.length == 0 || !(values[0] instanceof Context)) {
                return false;
            }
            Context context = (Context) values[0];
            Configuration configuration =
                    new Configuration(context.getResources().getConfiguration());
            if (configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                return false;
            }
            configuration.orientation = Configuration.ORIENTATION_PORTRAIT;
            Context portrait = context.createConfigurationContext(configuration);
            if (portrait == null) {
                return false;
            }
            Object[] newValues = values.clone();
            newValues[0] = portrait;
            args[3] = newValues;
            log(Log.INFO, TAG, "landscape context replaced for " + ADD_BOTTOM_VIEW);
            return true;
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "forcePortraitContext failed: " + t, t);
            return false;
        }
    }

    private boolean isLandscapeEnabled() {
        return prefs == null || prefs.getBoolean(SettingsKeys.LANDSCAPE_ENABLED, true);
    }

    private void installVisibilityHook(ClassLoader loader) {
        if (visibilityHookInstalled || loader == null) {
            return;
        }
        synchronized (this) {
            if (visibilityHookInstalled) {
                return;
            }
            try {
                Class<?> manager = Class.forName(BOTTOM_MANAGER_CLASS, true, loader);
                helperField = manager.getDeclaredField("sBottomViewHelper");
                helperField.setAccessible(true);
                areaField = helperField.getType().getDeclaredField("mMiuiBottomArea");
                areaField.setAccessible(true);
                Method target = findMethod(manager, CHANGE_BOTTOM_VIEW, ViewGroup.class, Context.class);
                if (target == null) {
                    log(Log.WARN, TAG, CHANGE_BOTTOM_VIEW + " not found");
                    return;
                }
                hook(target)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            applyBarVisibility();
                            return result;
                        });
                visibilityHookInstalled = true;
                log(Log.INFO, TAG, "hooked " + BOTTOM_MANAGER_CLASS + "#" + CHANGE_BOTTOM_VIEW);
            } catch (Throwable t) {
                log(Log.WARN, TAG, "visibility hook failed: " + t, t);
            }
        }
    }

    private void applyBarVisibility() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(this::applyBarVisibility);
            return;
        }
        try {
            if (helperField == null || areaField == null) {
                return;
            }
            Object helper = helperField.get(null);
            if (helper == null) {
                return;
            }
            ViewGroup area = (ViewGroup) areaField.get(helper);
            if (area == null) {
                return;
            }
            boolean enabled = isLandscapeEnabled();
            int target = enabled ? View.VISIBLE : View.GONE;
            if (area.getVisibility() != target) {
                area.setVisibility(target);
                area.requestLayout();
                log(Log.INFO, TAG, "bar " + (enabled ? "shown" : "hidden"));
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "applyBarVisibility failed: " + t, t);
        }
    }

    private void loadPreferences() {
        try {
            prefs = getRemotePreferences(SettingsKeys.PREFS_GROUP);
            landscapeEnabled = prefs.getBoolean(SettingsKeys.LANDSCAPE_ENABLED, true);
            prefs.registerOnSharedPreferenceChangeListener((p, key) -> {
                if (SettingsKeys.LANDSCAPE_ENABLED.equals(key)) {
                    landscapeEnabled = p.getBoolean(SettingsKeys.LANDSCAPE_ENABLED, true);
                    applyBarVisibility();
                }
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "remote preferences unavailable: " + t, t);
        }
    }
}
