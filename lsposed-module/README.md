# Leica Frame Patcher (LSPOSED)

Companion LSPOSED/Xposed module for the Nezha Leica Camera Enabler Magisk module.
The Magisk module sets the Leica system props; this module patches the app behaviour
that props alone cannot reach.

## Features

1. **Six Leica watermarks** — keep all gallery entries and allow only
   `徕卡_111`, `徕卡_88`, `徕卡_89`, `徕卡_90`, `徕卡_92`, `徕卡_91`
   when the original eligibility check rejects a Xiaomi photo on `nezha`.
   Other templates keep their original result. No EXIF data is fabricated.
2. **Camera watermark device name** — use DexKit to locate camera watermark
   provider methods and replace their manufacturer/model text. The set of
   provider methods is unchanged from the previously working module.
3. **Optional system Settings hook** — when `com.android.settings` is manually
   included in LSPosed scope, hide the unsupported Camera ring settings entry
   (`InputFeature.supportCameraMRFunction()`). The Photography Kit is unaffected.

The unavailable in-camera ring hook and obsolete gallery device-name hook
were removed. They were not working on the tested HyperOS 4 builds.

## Build

Open `lsposed-module/` in Android Studio, or from a shell (the Gradle wrapper is
committed):

```sh
cd lsposed-module
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed → installable).

This is a **modern Xposed module** (libxposed API 100+): it extends `XposedModule`,
hooks via the `hook(method).intercept(chain -> …)` interceptor API, and is configured
by `app/src/main/resources/META-INF/xposed/{module.prop,java_init.list,scope.list}`
(not the legacy manifest meta-data). **Requires an LSPOSED that supports API 100+.**

Dependencies: the API is `compileOnly 'io.github.libxposed:api:102.0.0'` (Maven
Central, provided by LSPOSED at runtime — not bundled); **DexKit**
(`org.luckypray:dexkit`) *is* bundled (native lib, ~4 MB APK) and powers the camera
watermark hook. libxposed provides no `XposedHelpers`, so reflection is done by hand.

## Install

1. Install the compiled LSPosed APK and enable the module.
2. Recommended static scope: `com.android.camera` and `com.miui.mediaeditor`.
   Do **not** select `com.hyper.gallery.plugin` (no Hook targets this package).
3. Optional: manually enable `com.android.settings` only if you want to hide
   the Camera ring entry in system Settings.
4. Force-stop affected apps or reboot. Test all six watermark templates with
   an original Xiaomi 17 Ultra photo.

**Important:** `scope.list` gives defaults; changing it does not forcibly
untick scopes already selected in LSPosed Manager. Remove unused selections
manually after installing the updated APK.

## If a HyperOS update breaks it

The verified editor check for version 2.10.43.0.6 is
`w60.x0.a(i70.e, String, pc.b, o80.f)`; verdicts are
`e70.a$a` (accepted) and `e70.a$b` (rejected). If a future editor update
changes this signature, the hook will fail to install and log the exception.
Do not enable broad unconditional bypasses.
