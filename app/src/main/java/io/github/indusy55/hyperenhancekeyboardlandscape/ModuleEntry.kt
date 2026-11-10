package io.github.indusy55.hyperenhancekeyboardlandscape

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 小米「全面屏键盘优化」横屏开关（Vector / libxposed 102 模块）。
 *
 * 底栏由 [com.miui.inputmethod.InputMethodBottomManager] 绘制，该类位于
 * MIUIFrequentPhrase.apk，由 InputMethodServiceInjector 通过
 * InputMethodModuleManager.loadDex() 加载进输入法进程。
 *
 * addMiuiBottomView(...) 在横屏直接 return。本模块把 reflectModule 的 Context 参数
 * 换成竖屏 Configuration Context，让原方法走完整竖屏实现。开关关闭时只隐藏底栏。
 */
class ModuleEntry : XposedModule() {

    private companion object {
        const val TAG = "HyperEnhanceKeyboardLandscape"
        const val INJECTOR_CLASS = "android.inputmethodservice.InputMethodServiceInjector"
        const val BOTTOM_MANAGER_CLASS = "com.miui.inputmethod.InputMethodBottomManager"
        const val ADD_BOTTOM_VIEW = "addMiuiBottomView"
        const val REFLECT_MODULE = "reflectModule"
        const val CHANGE_BOTTOM_VIEW = "changeViewForMiuiBottom"
    }

    private var installed = false
    private var visibilityHookInstalled = false
    private var prefs: SharedPreferences? = null
    private var helperField: Field? = null
    private var areaField: Field? = null

    @Volatile
    private var nativeBarHeightPx = 0

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean = true

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        // Keep existing hooks: package callbacks are not replayed after hot reload.
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (!param.isFirstPackage || installed) return
        installed = true
        try {
            val loader = param.defaultClassLoader
            val injector = Class.forName(INJECTOR_CLASS, false, loader)
            val target = findMethod(
                injector,
                REFLECT_MODULE,
                String::class.java,
                String::class.java,
                arrayOfNulls<Class<*>>(0).javaClass,
                arrayOfNulls<Any>(0).javaClass,
            )
            if (target == null) {
                log(Log.WARN, TAG, "$REFLECT_MODULE not found in $INJECTOR_CLASS")
                return
            }
            hook(target)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val args = chain.args.toTypedArray()
                    val changed = forcePortraitContext(args)
                    val result = if (changed) chain.proceed(args) else chain.proceed()
                    if (isAddBottomView(args)) {
                        contextOf(args)?.let { context ->
                            installVisibilityHook(context.classLoader)
                            applyBarVisibility()
                        }
                    }
                    result
                }
            loadPreferences()
            log(Log.INFO, TAG, "hooked $INJECTOR_CLASS#$REFLECT_MODULE")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "inactive: $t", t)
        }
    }

    private fun findMethod(type: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? =
        try {
            type.getDeclaredMethod(name, *parameterTypes).also { it.isAccessible = true }
        } catch (_: Throwable) {
            null
        }

    @Suppress("UNCHECKED_CAST")
    private fun contextOf(args: Array<Any?>): Context? {
        val values = args.getOrNull(3) as? Array<Any?> ?: return null
        return values.getOrNull(0) as? Context
    }

    private fun isAddBottomView(args: Array<Any?>): Boolean =
        args.size == 4 && BOTTOM_MANAGER_CLASS == args[0] && ADD_BOTTOM_VIEW == args[1]

    @Suppress("UNCHECKED_CAST")
    private fun forcePortraitContext(args: Array<Any?>): Boolean {
        try {
            if (!isAddBottomView(args)) return false
            val values = args[3] as? Array<Any?> ?: return false
            val context = values.getOrNull(0) as? Context ?: return false
            val configuration = Configuration(context.resources.configuration)
            if (configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return false
            configuration.orientation = Configuration.ORIENTATION_PORTRAIT
            val portrait = context.createConfigurationContext(configuration) ?: return false
            val newValues = values.copyOf()
            newValues[0] = portrait
            args[3] = newValues
            log(Log.INFO, TAG, "landscape context replaced for $ADD_BOTTOM_VIEW")
            return true
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "forcePortraitContext failed: $t", t)
            return false
        }
    }

    private fun isLandscapeEnabled(): Boolean =
        prefs?.getBoolean(SettingsKeys.LANDSCAPE_ENABLED, true) ?: true

    private fun installVisibilityHook(loader: ClassLoader?) {
        if (visibilityHookInstalled || loader == null) return
        synchronized(this) {
            if (visibilityHookInstalled) return
            try {
                val manager = Class.forName(BOTTOM_MANAGER_CLASS, true, loader)
                helperField = manager.getDeclaredField("sBottomViewHelper").also {
                    it.isAccessible = true
                }
                areaField = helperField?.type?.getDeclaredField("mMiuiBottomArea")?.also {
                    it.isAccessible = true
                }
                val target = findMethod(manager, CHANGE_BOTTOM_VIEW, ViewGroup::class.java, Context::class.java)
                if (target == null) {
                    log(Log.WARN, TAG, "$CHANGE_BOTTOM_VIEW not found")
                    return
                }
                hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val args = chain.args.toTypedArray()
                        val area = args.getOrNull(0) as? ViewGroup
                        val context = args.getOrNull(1) as? Context
                        if (area != null && context != null) {
                            lockBottomAreaHeight(area, context)
                        }
                        val result = chain.proceed()
                        if (area != null && context != null) {
                            lockBottomAreaHeight(area, context)
                        }
                        applyBarVisibility()
                        result
                    }
                visibilityHookInstalled = true
                log(Log.INFO, TAG, "hooked $BOTTOM_MANAGER_CLASS#$CHANGE_BOTTOM_VIEW")
            } catch (t: Throwable) {
                log(Log.WARN, TAG, "visibility hook failed: $t", t)
            }
        }
    }

    private fun lockBottomAreaHeight(area: ViewGroup, context: Context) {
        try {
            val height = resolveNativeBarHeight(area, context)
            if (height <= 0) return
            val areaParams = area.layoutParams
            if (areaParams != null && areaParams.height != height) {
                areaParams.height = height
                area.setLayoutParams(areaParams)
            }
            val bar = area.getChildAt(0) ?: return
            val barParams = bar.layoutParams
            if (barParams != null && barParams.height != height) {
                barParams.height = height
                bar.setLayoutParams(barParams)
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "lockBottomAreaHeight failed: $t", t)
        }
    }

    private fun resolveNativeBarHeight(area: ViewGroup, context: Context): Int {
        nativeBarHeightPx.takeIf { it > 0 }?.let { return it }
        synchronized(this) {
            if (nativeBarHeightPx > 0) return nativeBarHeightPx
            val bar = area.getChildAt(0) ?: return 0
            val resources = bar.resources
            val packages = arrayOf("com.miui.phrase", context.packageName, "miuix.animation")
            for (pkg in packages.distinct()) {
                for (name in arrayOf("input_bottom_height", "input_bottom_low_height")) {
                    val id = try {
                        resources.getIdentifier(name, "dimen", pkg)
                    } catch (_: Throwable) {
                        0
                    }
                    if (id == 0) continue
                    val px = try {
                        resources.getDimensionPixelSize(id)
                    } catch (_: Throwable) {
                        0
                    }
                    if (px > 0) {
                        nativeBarHeightPx = px
                        log(Log.INFO, TAG, "native bar height ${px}px from $pkg:$name")
                        return px
                    }
                }
            }
            log(Log.WARN, TAG, "native bar height resource not found; keeping original height")
            return 0
        }
    }

    private fun applyBarVisibility() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { applyBarVisibility() }
            return
        }
        try {
            val helper = helperField?.get(null) ?: return
            val area = areaField?.get(helper) as? ViewGroup ?: return
            val enabled = isLandscapeEnabled()
            val target = if (enabled) View.VISIBLE else View.GONE
            if (area.visibility != target) {
                area.visibility = target
                area.requestLayout()
                log(Log.INFO, TAG, if (enabled) "bar shown" else "bar hidden")
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "applyBarVisibility failed: $t", t)
        }
    }

    private fun loadPreferences() {
        try {
            val remote = getRemotePreferences(SettingsKeys.PREFS_GROUP)
            prefs = remote
            remote.registerOnSharedPreferenceChangeListener { preferences, key ->
                if (SettingsKeys.LANDSCAPE_ENABLED == key) {
                    preferences.getBoolean(SettingsKeys.LANDSCAPE_ENABLED, true)
                    applyBarVisibility()
                }
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "remote preferences unavailable: $t", t)
        }
    }
}
