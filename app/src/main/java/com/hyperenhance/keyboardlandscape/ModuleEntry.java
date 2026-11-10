package com.hyperenhance.keyboardlandscape;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 小米「全面屏键盘优化」的横屏开关（Vector / libxposed 102 模块）。
 *
 * <p>键盘底部的功能条（剪贴板 / 切换输入法 / 小爱）由小米系统组件
 * {@code com.miui.inputmethod.InputMethodBottomManager} 绘制。该组件位于
 * MIUIFrequentPhrase.apk，由 {@code InputMethodServiceInjector} 通过
 * {@code InputMethodModuleManager.loadDex()} 加载进当前输入法进程。
 *
 * <p>其 {@code addMiuiBottomView(...)} 在 {@code Configuration.orientation ==
 * ORIENTATION_LANDSCAPE} 时直接 return：不创建底栏、不注册观察者，并把
 * {@code InputMethodUtil.sCanShowMiuiBottom} 置为 false。因此横屏下功能完全失效。
 *
 * <p>本模块不触碰任何输入法自己的类。它只 hook 小米框架里的
 * {@code InputMethodServiceInjector#reflectModule}，在它即将反射调用
 * {@code addMiuiBottomView} 时，把一个 Configuration 被覆盖为竖屏的 Context
 * 换进参数。原方法随后的竖屏分支即为完整可用的实现。
 */
public final class ModuleEntry extends XposedModule {

    private static final String TAG = "HyperEnhanceKeyboardLandscape";
    private static final String INJECTOR_CLASS =
            "android.inputmethodservice.InputMethodServiceInjector";
    private static final String BOTTOM_MANAGER_CLASS =
            "com.miui.inputmethod.InputMethodBottomManager";
    private static final String ADD_BOTTOM_VIEW = "addMiuiBottomView";
    private static final String REFLECT_MODULE = "reflectModule";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!param.isFirstPackage() || !INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            ClassLoader loader = param.getDefaultClassLoader();
            Class<?> injector = Class.forName(INJECTOR_CLASS, false, loader);
            Method target = findReflectModule(injector);
            if (target == null) {
                log(Log.WARN, TAG, REFLECT_MODULE + " not found in " + INJECTOR_CLASS);
                return;
            }
            hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();
                        if (forcePortraitContext(args)) {
                            return chain.proceed(args);
                        }
                        return chain.proceed();
                    });
            log(Log.INFO, TAG, "hooked " + INJECTOR_CLASS + "#" + REFLECT_MODULE);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "inactive: " + t, t);
        }
    }

    private static Method findReflectModule(Class<?> injector) {
        for (Method method : injector.getDeclaredMethods()) {
            if (!REFLECT_MODULE.equals(method.getName())) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 4
                    && types[0] == String.class
                    && types[1] == String.class
                    && types[2] == Class[].class
                    && types[3] == Object[].class) {
                return method;
            }
        }
        return null;
    }

    /**
     * {@code reflectModule(className, methodName, parameterTypes, values...)}:
     * values[0] 是传给 {@code addMiuiBottomView} 的 Context（com.miui.phrase 的
     * package context）。换成竖屏配置的 context 后，原方法走完整的竖屏实现。
     *
     * @return true when the argument array was changed and must be proceeded with
     */
    private boolean forcePortraitContext(Object[] args) {
        try {
            if (args.length != 4
                    || !BOTTOM_MANAGER_CLASS.equals(args[0])
                    || !ADD_BOTTOM_VIEW.equals(args[1])
                    || !(args[3] instanceof Object[])) {
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
}
