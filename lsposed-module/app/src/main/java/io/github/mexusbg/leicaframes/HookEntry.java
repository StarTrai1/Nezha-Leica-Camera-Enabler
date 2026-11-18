package io.github.mexusbg.leicaframes;

import android.os.Build;
import android.util.Log;
import android.util.SparseArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.AnnotationMatcher;
import org.luckypray.dexkit.query.matchers.AnnotationsMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * Modern (libxposed API 100+) Xposed module for the Xiaomi 17 Ultra (nezha) Leica tweaks.
 *
 * <ul>
 *   <li><b>Frames</b> — retains all Leica watermark picker entries and relaxes
 *       eligibility of six legacy Leica templates on nezha images (HyperOS 4).</li>
 *   <li><b>Ring</b> — hides the unsupported Camera ring entry in system Settings.</li>
 *   <li><b>Watermark</b> — overrides the device name in the camera.</li>
 * </ul>
 *
 * Hooks use the interceptor-chain API: {@code hook(method).intercept(chain -> result)}.
 * Returning a value without calling {@code chain.proceed()} replaces the result and skips
 * the original. libxposed provides no XposedHelpers, so reflection is done by hand below.
 */
public class HookEntry extends XposedModule {

    static final String TAG = "NezhaLeicaEnabler";

    private static final String PKG_MEDIAEDITOR = "com.miui.mediaeditor";
    private static final String PKG_CAMERA = "com.android.camera";
    private static final String PKG_SETTINGS = "com.android.settings";

    // System Settings "Camera ring" (camera_mr) gate. Real framework name.
    private static final String CLS_INPUT_FEATURE = "miui.hardware.input.InputFeature";


    // Watermark device name. The frame renders a brand line ("XIAOMI", logo font) ahead
    // of the device text, so the device slot omits the leading "Xiaomi".
    private static final String WATERMARK_MFR = "";
    private static final String WATERMARK_DEV = "17 Ultra by Leica";


    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        ClassLoader cl = param.getDefaultClassLoader();
        if (cl == null) return;

        if (PKG_MEDIAEDITOR.equals(pkg)) {
            String editorApk = param.getApplicationInfo() != null
                    ? param.getApplicationInfo().sourceDir : null;
            safe("legacy Leica eligibility", () -> hookAllowLegacyLeicaWatermarks(cl, editorApk));
        } else if (PKG_CAMERA.equals(pkg)) {
            String apk = param.getApplicationInfo() != null ? param.getApplicationInfo().sourceDir : null;
            if (apk != null) {
                final String apkPath = apk;
                safe("camera watermark-name", () -> hookCameraWatermarkName(cl, apkPath));
            }
        } else if (PKG_SETTINGS.equals(pkg)) {
            safe("camera-ring entry", () -> hookHideCameraRingEntry(cl));
        }
    }

    private interface HookInstall {
        void run() throws Throwable;
    }

    private void safe(String what, HookInstall install) {
        try {
            install.run();
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "failed to install " + what + " hook: " + t);
        }
    }

    // ------------------------------------------------------------- ring (settings) ------

    /**
     * Hide the system-Settings "Camera ring" (camera_mr) entry by forcing
     * {@code miui.hardware.input.InputFeature.supportCameraMRFunction()} to false.
     * Runs before SettingsFeatures' static init, so the header is dropped. Does not touch
     * the Photography-Kit "Camera Grip" (a separate feature).
     */
    private void hookHideCameraRingEntry(ClassLoader cl) {
        Class<?> inputFeature = findClass(CLS_INPUT_FEATURE, cl);
        if (inputFeature == null) {
            log(Log.WARN, TAG, CLS_INPUT_FEATURE + " not found — camera-ring entry hook skipped.");
            return;
        }
        Method m = findMethod(inputFeature, "supportCameraMRFunction", 0);
        if (m == null) {
            log(Log.WARN, TAG, "supportCameraMRFunction() not found — camera-ring entry hook skipped.");
            return;
        }
        hook(m).intercept(chain -> Boolean.FALSE);
        log(Log.INFO, TAG, "system-settings Camera ring entry hidden (supportCameraMRFunction=false).");
    }

    // -------------------------------------------------------- watermark name (camera) ---

    /**
     * Camera watermark device name. A family of provider classes (subclasses of C15705)
     * return a {@code SparseArray<String[]>} = [manufacturer, device] from {@code mo26211d()}.
     * Class + method names are non-ASCII obfuscated, so they are located at runtime with
     * DexKit (SparseArray-returning, zero-arg, with the SparseArray&lt;String[]&gt; signature
     * annotation) and forced to {@link #WATERMARK_MFR} / {@link #WATERMARK_DEV}.
     */
    private void hookCameraWatermarkName(final ClassLoader cl, final String apkPath) {
        new Thread(() -> {
            try {
                System.loadLibrary("dexkit");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "could not load libdexkit — camera watermark skipped: " + t);
                return;
            }
            try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
                MethodDataList providers = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .returnType(SparseArray.class)
                                .paramCount(0)
                                .annotations(AnnotationsMatcher.create()
                                        .add(AnnotationMatcher.create()
                                                .usingStrings("Ljava/lang/String;")))));
                int hooked = 0;
                for (MethodData md : providers) {
                    try {
                        Method m = md.getMethodInstance(cl);
                        hook(m).intercept(chain -> {
                            SparseArray<String[]> sa = new SparseArray<>(1);
                            sa.put(0, new String[]{WATERMARK_MFR, WATERMARK_DEV});
                            return sa;
                        });
                        hooked++;
                    } catch (Throwable t) {
                        log(Log.ERROR, TAG, "failed to hook watermark provider " + md + ": " + t);
                    }
                }
                log(Log.INFO, TAG, "camera watermark providers hooked = " + hooked
                        + " ('" + WATERMARK_MFR + "' / '" + WATERMARK_DEV + "').");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "DexKit camera watermark search failed: " + t);
            }
        }, "NezhaLeica-DexKit").start();
    }

    // ------------------------------------------------------ Leica watermark compatibility --
    // Verified signatures: Gallery Editor 2.10.43.0.6 and 2.10.43.1.4.
    private static final String[][] LEICA_GATE_SIGNATURES = {
            {"x60.c1", "j70.d", "qc.b", "p80.g", "f70.a"}, // 2.10.43.1.4
            {"w60.x0", "i70.e", "pc.b", "o80.f", "e70.a"}  // 2.10.43.0.6
    };
    private static final String LEICA_REJECTION_MARKER = "UnSupport(messageId=";
    private static final String LEICA_WATERMARK_ITEM =
            "com.miui.mediaeditor.photo.watermark.model.menu.WatermarkItem";

    private static final class LeicaGate {
        final Method check;
        final Field templateName;
        final Method exifRead;
        final Class<?> rejected;
        final Object accepted;

        LeicaGate(Method check, Field templateName, Method exifRead,
                  Class<?> rejected, Object accepted) {
            this.check = check;
            this.templateName = templateName;
            this.exifRead = exifRead;
            this.rejected = rejected;
            this.accepted = accepted;
        }
    }

    private static LeicaGate describeLeicaGate(Method check)
            throws ReflectiveOperationException {
        Class<?>[] args = check.getParameterTypes();
        Class<?> verdict = check.getReturnType();
        if (!java.lang.reflect.Modifier.isStatic(check.getModifiers())
                || args.length != 4 || args[1] != String.class
                || verdict.isPrimitive() || verdict.isArray()) {
            throw new NoSuchMethodException("Unexpected watermark gate signature: " + check);
        }

        // Both inspected versions retain these members. Fail closed if they change.
        Field templateName = args[0].getDeclaredField("a");
        Method exifRead = args[2].getDeclaredMethod("f", String.class);
        if (templateName.getType() != String.class
                || java.lang.reflect.Modifier.isStatic(templateName.getModifiers())
                || exifRead.getReturnType() != String.class
                || java.lang.reflect.Modifier.isStatic(exifRead.getModifiers())) {
            throw new NoSuchFieldException("Unexpected template/EXIF members: " + check);
        }

        // Identify the two verdict subclasses by their verified structure, not their names.
        Class<?> rejected = null;
        Field acceptedField = null;
        for (Class<?> nested : verdict.getDeclaredClasses()) {
            if (nested.getSuperclass() != verdict) continue;
            try {
                nested.getDeclaredConstructor(int.class);
                Field reason = nested.getDeclaredField("a");
                if (reason.getType() == int.class
                        && !java.lang.reflect.Modifier.isStatic(reason.getModifiers())) {
                    if (rejected != null) throw new IllegalStateException("Ambiguous rejection verdict");
                    rejected = nested;
                }
            } catch (NoSuchMethodException | NoSuchFieldException ignored) {
                // Not the rejection class.
            }
            for (Field field : nested.getDeclaredFields()) {
                if (field.getType() == nested
                        && java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                    if (acceptedField != null) throw new IllegalStateException("Ambiguous acceptance verdict");
                    acceptedField = field;
                }
            }
        }
        if (rejected == null || acceptedField == null
                || rejected == acceptedField.getDeclaringClass()) {
            throw new NoSuchFieldException("Missing acceptance/rejection verdicts: " + check);
        }
        check.setAccessible(true);
        templateName.setAccessible(true);
        exifRead.setAccessible(true);
        acceptedField.setAccessible(true);
        Object accepted = acceptedField.get(null);
        if (accepted == null || !verdict.isInstance(accepted)) {
            throw new NoSuchFieldException("Invalid acceptance singleton: " + check);
        }
        return new LeicaGate(check, templateName, exifRead, rejected, accepted);
    }

    private LeicaGate resolveLeicaGate(ClassLoader cl, String apkPath) throws Throwable {
        java.util.LinkedHashSet<Method> known = new java.util.LinkedHashSet<>();
        for (String[] sig : LEICA_GATE_SIGNATURES) {
            try {
                Class<?> checker = Class.forName(sig[0], false, cl);
                Method method = checker.getDeclaredMethod("a",
                        Class.forName(sig[1], false, cl), String.class,
                        Class.forName(sig[2], false, cl), Class.forName(sig[3], false, cl));
                if (method.getReturnType() == Class.forName(sig[4], false, cl)) known.add(method);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // This signature belongs to a different editor version.
            }
        }
        if (known.size() == 1) {
            try {
                return describeLeicaGate(known.iterator().next());
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // A familiar name with changed members is not sufficient.
            }
        }

        // Unknown build: locate the verdict type by its stable toString() literal.
        // The two inspected APKs use different XOR keys for gate strings, so do NOT
        // search encoded log messages. Require a unique static four-argument method
        // returning the verdict, with a template type referenced by WatermarkItem.
        if (apkPath == null || apkPath.isEmpty()) {
            throw new NoSuchMethodException("Unknown Leica gate and no editor APK path");
        }
        Class<?> watermarkItem = Class.forName(LEICA_WATERMARK_ITEM, false, cl);
        System.loadLibrary("dexkit");
        java.util.LinkedHashSet<Method> matches = new java.util.LinkedHashSet<>();
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            MethodDataList markers = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().paramCount(0).returnType(String.class)
                            .usingStrings(LEICA_REJECTION_MARKER)));
            for (MethodData marker : markers) {
                Method toString;
                try {
                    toString = marker.getMethodInstance(cl);
                } catch (Exception | LinkageError ignored) {
                    continue;
                }
                if (!"toString".equals(toString.getName())) continue;
                Class<?> rejected = toString.getDeclaringClass();
                Class<?> verdict = rejected.getSuperclass();
                if (verdict == null || verdict == Object.class) continue;
                MethodDataList gates = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create().paramCount(4).returnType(verdict)));
                for (MethodData data : gates) {
                    try {
                        Method method = data.getMethodInstance(cl);
                        Class<?>[] args = method.getParameterTypes();
                        if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())
                                || args.length != 4 || args[1] != String.class) continue;
                        boolean itemReferencesTemplate = false;
                        for (Field field : watermarkItem.getDeclaredFields()) {
                            if (field.getType() == args[0]
                                    && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                                itemReferencesTemplate = true;
                                break;
                            }
                        }
                        if (!itemReferencesTemplate) continue;
                        LeicaGate candidate = describeLeicaGate(method);
                        if (candidate.rejected == rejected) matches.add(method);
                    } catch (Exception | LinkageError ignored) {
                        // Fail closed on a changed or incomplete signature.
                    }
                }
            }
        }
        if (matches.size() != 1) {
            throw new NoSuchMethodException("Leica gate discovery found " + matches.size()
                    + " candidates; no hook installed");
        }
        return describeLeicaGate(matches.iterator().next());
    }

    private void hookAllowLegacyLeicaWatermarks(ClassLoader cl, String apkPath) throws Throwable {
        LeicaGate gate = resolveLeicaGate(cl, apkPath);
        final Method check = gate.check;
        final Field templateNameField = gate.templateName;
        final Method exifRead = gate.exifRead;
        final Class<?> rejectedCls = gate.rejected;
        final Object accepted = gate.accepted;
        hook(check).intercept(chain -> {
            Object result = chain.proceed(); // other templates use the original rules
            if (!rejectedCls.isInstance(result)) return result;
            if (!"leica".equals(chain.getArg(1))) return result;
            if (!"nezha".equalsIgnoreCase(Build.DEVICE)) return result;

            final Object template = chain.getArg(0);
            final Object exif = chain.getArg(2);
            if (template == null || exif == null) return result;
            final String name;
            try {
                name = (String) templateNameField.get(template);
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Cannot read Leica template name: " + t);
                return result;
            }
            if (!isLegacyLeicaTemplate(name)) return result;

            // Avoid unlocking unrelated pictures simply because they are being
            // edited on a nezha device. These tags are read from PHOTO EXIF.
            final String model = readExif(exifRead, exif, "Model");
            final String product = readExif(exifRead, exif, "XiaomiProduct");
            final String make = readExif(exifRead, exif, "Make");
            final String m = model.toLowerCase(java.util.Locale.ROOT);
            final String p = product.toLowerCase(java.util.Locale.ROOT);
            final String manufacturer = make.toLowerCase(java.util.Locale.ROOT);
            final boolean isXiaomiPhoto = m.contains("xiaomi") || m.contains("17 ultra")
                    || m.contains("leitzphone") || p.contains("nezha")
                    || p.contains("17 ultra") || manufacturer.contains("xiaomi");
            if (!isXiaomiPhoto) {
                return result;
            }

            return accepted;
        });
        log(Log.INFO, TAG, "HyperOS 4 legacy Leica eligibility hook installed: " + check);
    }

    private static boolean isLegacyLeicaTemplate(String name) {
        // The six rejected templates were confirmed in device testing.
        // e70.a$b = rejected; e70.a$a = accepted.
        return "徕卡_111".equals(name) || "徕卡_88".equals(name)
                || "徕卡_89".equals(name) || "徕卡_90".equals(name)
                || "徕卡_92".equals(name) || "徕卡_91".equals(name);
    }

    private static String readExif(Method getter, Object exif, String name) {
        try {
            Object value = getter.invoke(exif, name);
            return value == null ? "" : value.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    // ----------------------------------------------------------------- reflection -------

    private Class<?> findClass(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Method by name and parameter count (searches the class only). */
    private Method findMethod(Class<?> cls, String name, int paramCount) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == paramCount) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}
