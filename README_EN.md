# HyperEnhanceKeyboardLandscape

English | [简体中文](README.md)

Enable Xiaomi's "Full-screen keyboard optimization" bottom bar (clipboard / input-method
switcher / cursor / XiaoAi, etc.) on devices in landscape.

The module hooks exactly one method in Xiaomi's system framework. It does not hook any
specific input method.

## The problem

In landscape, Settings shows "Full-screen keyboard optimization" as enabled,
but the function bar never appears below the keyboard.

The cause is in `MIUIFrequentPhrase.apk`,
`com.miui.inputmethod.InputMethodBottomManager#addMiuiBottomView`:

```java
if (context.getResources().getConfiguration().orientation == 2) {
    sIsScreenLandscape = true;
    sNeedHandleComputeInsets = false;
    InputMethodUtil.sCanShowMiuiBottom = false;
    // no bottom view, no observers
    return;
}
```

It returns immediately in landscape, so the bar is never created.

## How it works

The `com.miui.phrase` class is loaded into the current input-method process by
`android.inputmethodservice.InputMethodServiceInjector` through
`InputMethodModuleManager.loadDex()`, then invoked reflectively:

```java
reflectModule("com.miui.inputmethod.InputMethodBottomManager",
              "addMiuiBottomView",
              new Class[]{ Context.class, ... },
              moduleContext, ...);
```

The module hooks `InputMethodServiceInjector#reflectModule`. When the target is
`addMiuiBottomView` and the device is in landscape, it replaces the first argument (the
`com.miui.phrase` package Context) with a portrait-configured Context created by
`createConfigurationContext()`. The original portrait branch then runs: it creates the
bottom bar, registers settings observers and sets
`InputMethodUtil.sCanShowMiuiBottom = true`.

Therefore:

- No Sogou / Baidu / iFlytek / XiaoAi input-method classes are hooked
- No APK or setting is modified
- The input method is only the host process that the scoped module is injected into

## Requirements

- HyperOS device (verified on OS4.0.6)
- System property `ro.miui.support_miui_ime_bottom=1`
- Full-screen gesture navigation
- Settings → More settings → Language & input → Full-screen keyboard optimization **enabled**
- Vector (API 102) or another libxposed API compatible framework

## Install

1. Build, then install `app/build/outputs/apk/debug/app-debug.apk`.
2. Enable the module in the Vector / LSPosed manager.
3. Add the input-method process you use to the module scope (any input method works; the
   module is not specific to one).
4. Force-stop that input method, or reopen the keyboard.

The module has no launcher icon; it only appears in the module manager.

## Landscape height

The bar height is not a separate landscape switch. `InputMethodBottomManager.changeViewForMiuiBottom`
measures `miuiBottomArea` and writes `InputMethodUtil.sBottomAreaHeight`. For a
landscape-specific height, hook one of:

| Target | Purpose |
|---|---|
| `InputMethodBottomManager.changeViewForMiuiBottom(ViewGroup, Context)` | Override `LayoutParams.height` / `sBottomAreaHeight` after measurement |
| `InputMethodUtil.getNavigationBarHeight(Context)` | Change the gesture-bar height / bottom-margin source |
| `InputMethodBottomManager.setMiuiBottomMargin(Context)` | Change the bar's bottom margin |
| `MiuiBottomConfig.getLayoutForDevice()` | Device-based layout selection; no `-land` variant, so the intrinsic height is the same in both orientations |

## Build

Requires JDK 17 and the Android SDK (`local.properties` or `ANDROID_HOME`).

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

For distribution use `./gradlew :app:assembleRelease`; the release APK is unsigned and
needs your own signing configuration.

## Limitations

- Depends on Xiaomi's private `MIUIFrequentPhrase.apk` implementation; system updates may
  change the method.
- Only fixes the landscape early-return in `addMiuiBottomView`; other OEM restrictions are
  not handled.
- The framework must inject the module into the input-method process; the scope must
  include the input method actually in use.

## License

[MIT](LICENSE)
