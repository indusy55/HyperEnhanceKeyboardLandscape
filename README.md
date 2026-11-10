# HyperEnhanceKeyboardLandscape

[English](README_EN.md) | 简体中文

在 HyperOS 横屏下启用小米「全面屏键盘优化」底栏。

## 原理

底栏由 `com.miui.inputmethod.InputMethodBottomManager` 绘制（`MIUIFrequentPhrase.apk`），
由 `InputMethodServiceInjector` 通过 `InputMethodModuleManager.loadDex()` 加载进输入法进程。

其 `addMiuiBottomView(...)` 在横屏直接 return。模块 hook `InputMethodServiceInjector#reflectModule`，
在横屏时把传给 `addMiuiBottomView` 的 Context 换成竖屏 Configuration Context，让原方法走完整
竖屏实现。

开关状态通过 libxposed API 102 的远程偏好在模块 App 与输入法进程之间同步：

- `XposedService.getRemotePreferences("settings")` 写入
- 输入法进程内 `XposedModule.getRemotePreferences("settings")` 读取
- `changeViewForMiuiBottom` 之后按开关值设置底栏 `VISIBLE/GONE`

不需要 root。

## 环境

- HyperOS 设备（在 OS4.0.6 上验证）
- 系统属性 `ro.miui.support_miui_ime_bottom=1`
- 全面屏手势导航
- 设置 → 更多设置 → 语言与输入法 → 全面屏键盘优化 **已开启**
- Vector（API 102）

## 安装

1. 构建后安装 `app/build/outputs/apk/debug/app-debug.apk`。
2. 在 Vector 中启用模块。
3. 作用域勾选正在使用的输入法；`scope.list` 已提供常见输入法作为推荐项。
4. 模块更新后，按框架提示强行停止并重新打开作用域内的应用。

模块没有桌面图标，入口在 Vector 的模块设置页。

## 界面

- 模块状态：显示框架服务是否连接、当前输入法是否在作用域
- 启用横屏支持：即时显示/隐藏底栏
- 测试键盘输入：用于直接验证底栏
- 当前输入法：显示实际 IME 组件

## 构建

需要 JDK 17 和 Android SDK（`local.properties` 或 `ANDROID_HOME`）。

```bash
./gradlew :app:assembleDebug
```

发布版本：`./gradlew :app:assembleRelease`，需要在 `keystore.properties` 或环境变量中配置签名。

## License

[MIT](LICENSE)
