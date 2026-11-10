# HyperEnhanceKeyboardLandscape

English | [简体中文](README.md)

Enables Xiaomi's full-screen keyboard optimization bar in landscape on HyperOS.

## How it works

The bar is drawn by `com.miui.inputmethod.InputMethodBottomManager`
(`MIUIFrequentPhrase.apk`), loaded into the input-method process by
`InputMethodServiceInjector` through `InputMethodModuleManager.loadDex()`.

`addMiuiBottomView(...)` returns immediately in landscape. The module hooks
`InputMethodServiceInjector#reflectModule` and replaces the Context passed to
`addMiuiBottomView` with a portrait-configured Context, so the original portrait
implementation runs.

The toggle is synchronized through libxposed API 102 remote preferences:

- written with `XposedService.getRemotePreferences("settings")`
- read inside the input-method process with `XposedModule.getRemotePreferences("settings")`
- `changeViewForMiuiBottom` sets the bar `VISIBLE/GONE` from that value

No root is required.

## Requirements

- HyperOS device (verified on OS4.0.6)
- System property `ro.miui.support_miui_ime_bottom=1`
- Full-screen gesture navigation
- Settings → More settings → Language & input → Full-screen keyboard optimization **enabled**
- Vector (API 102)

## Install

1. Build, then install `app/build/outputs/apk/debug/app-debug.apk`.
2. Enable the module in Vector.
3. Add the input method you use to the scope; `scope.list` provides common IMEs as
   recommended entries.
4. After a module update, force-stop and reopen scoped apps as prompted by the framework.

The module has no launcher icon; it is opened from Vector's module settings.

## UI

- Module status: framework service and current input-method scope
- Enable landscape support: shows/hides the bar immediately
- Test keyboard input: verifies the bar directly
- Current input method: shows the actual IME component

## Build

Requires JDK 17 and the Android SDK (`local.properties` or `ANDROID_HOME`).

```bash
./gradlew :app:assembleDebug
```

Release: `./gradlew :app:assembleRelease`, with signing configured in
`keystore.properties` or environment variables.

## License

[MIT](LICENSE)
