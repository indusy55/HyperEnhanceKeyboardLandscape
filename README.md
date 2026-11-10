# HyperEnhanceKeyboardLandscape

[English](README_EN.md) | 简体中文

在小米设备横屏下启用「全面屏键盘优化」底栏（剪贴板 / 切换输入法 / 光标 / 小爱等）。

模块只 hook 小米系统框架里的一个方法，不 hook 任何具体输入法。

## 问题

横屏时，设置里的「全面屏键盘优化」虽然显示为已开启，但键盘底部不会出现功能栏。

原因在 `MIUIFrequentPhrase.apk` 的 `com.miui.inputmethod.InputMethodBottomManager#addMiuiBottomView`：

```java
if (context.getResources().getConfiguration().orientation == 2) {
    sIsScreenLandscape = true;
    sNeedHandleComputeInsets = false;
    InputMethodUtil.sCanShowMiuiBottom = false;
    // 不创建底栏、不注册观察者
    return;
}
```

横屏直接返回，底栏从未被创建。

## 原理

`com.miui.phrase` 的这个类由框架 `android.inputmethodservice.InputMethodServiceInjector`
通过 `InputMethodModuleManager.loadDex()` 加载进当前输入法进程，然后反射调用：

```java
reflectModule("com.miui.inputmethod.InputMethodBottomManager",
              "addMiuiBottomView",
              new Class[]{ Context.class, ... },
              moduleContext, ...);
```

模块 hook 的正是 `InputMethodServiceInjector#reflectModule`。当目标方法是
`addMiuiBottomView` 且当前是横屏时，把第一个参数（`com.miui.phrase` 的 package
Context）替换为 `createConfigurationContext()` 生成的竖屏配置 Context。
原方法随后的竖屏分支就是完整实现：创建底栏、注册设置观察者、
把 `InputMethodUtil.sCanShowMiuiBottom` 置为 true。

因此：

- 不 hook 搜狗 / 百度 / 讯飞 / 小爱等任何输入法类
- 不修改 APK、不修改设置
- 输入法只在作用域里作为宿主进程存在

## 环境

- HyperOS 设备（在 OS4.0.6 上验证）
- 系统属性 `ro.miui.support_miui_ime_bottom=1`
- 全面屏手势导航
- 设置 → 更多设置 → 语言与输入法 → 全面屏键盘优化 **已开启**
- Vector（API 102）或兼容 libxposed API 的框架

## 安装

1. 构建后安装 `app/build/outputs/apk/debug/app-debug.apk`。
2. 在 Vector / LSPosed 管理器中启用模块。
3. 作用域勾选你正在使用的输入法进程（任意输入法都可以，模块不针对特定输入法）。
4. 强行停止该输入法，或重新打开键盘。

模块没有桌面图标，装好后只在模块管理器里出现。

## 横屏高度

底栏高度不是独立的横屏开关，而是由 `InputMethodBottomManager.changeViewForMiuiBottom`
测量 `miuiBottomArea` 后写入 `InputMethodUtil.sBottomAreaHeight`。如需横屏专属高度，
可额外 hook：

| 目标 | 用途 |
|---|---|
| `InputMethodBottomManager.changeViewForMiuiBottom(ViewGroup, Context)` | 测量后改 `LayoutParams.height` / `sBottomAreaHeight` |
| `InputMethodUtil.getNavigationBarHeight(Context)` | 改手势条高度 / 底部 margin 来源 |
| `InputMethodBottomManager.setMiuiBottomMargin(Context)` | 改底栏 bottomMargin |
| `MiuiBottomConfig.getLayoutForDevice()` | 按设备选布局，无 `-land` 版本，横竖屏固有高度相同 |

## 构建

需要 JDK 17 和 Android SDK（`local.properties` 或 `ANDROID_HOME`）。

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

发布版本使用 `./gradlew :app:assembleRelease`，产物未签名，需要自行配置签名。

## 已知限制

- 依赖小米 `MIUIFrequentPhrase.apk` 的私有实现，系统组件升级后方法可能变化。
- 仅处理横屏 `addMiuiBottomView` 被跳过的问题；其他 OEM 限制未处理。
- 需要框架把模块注入输入法进程，作用域必须包含实际使用的输入法。

## License

[MIT](LICENSE)
