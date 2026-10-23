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
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * Modern (libxposed API 100+) Xposed module for the Xiaomi 17 Ultra (nezha) Leica tweaks.
 *
 * <ul>
 *   <li><b>Frames</b> — retains all Leica watermark picker entries and relaxes
 *       eligibility of six legacy Leica templates on nezha images (HyperOS 4).</li>
 *   <li><b>Ring</b> — optionally hides the system Settings "Camera ring" entry;
 *       this requires manually adding Settings to the LSPosed scope.</li>
 *   <li><b>Watermark</b> — overrides the device name in the camera.</li>
 * </ul>
 *
 * Hooks use the interceptor-chain API: {@code hook(method).intercept(chain -> result)}.
 * Returning a value without calling {@code chain.proceed()} replaces the result and skips
 * the original. libxposed provides no XposedHelpers, so reflection is done by hand below.
 */
public class HookEntry extends XposedModule {

    static final String TAG = "LeicaFramePatcher";

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
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "loaded in " + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        ClassLoader cl = param.getDefaultClassLoader();
        if (cl == null) return;

        if (PKG_MEDIAEDITOR.equals(pkg)) {
            safe("legacy Leica eligibility", () -> hookAllowLegacyLeicaWatermarks(cl));
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
        }, "LeicaFP-DexKit").start();
    }

    // ----------------------------------------------------- legacy Leica (gallery) -----
    /**
     * Xiaomi Gallery Editor 2.10.43.0.6 (HyperOS 4):
     *   w60.x0.a(i70.e, String, pc.b, o80.f) -> e70.a
     *   e70.a$a.a = Support singleton, e70.a$b = UnSupport
     *
     * The obsolete hook filtered the picker Map in-place using old OS3 symbols
     * (xi.y / Di.a). Do not filter or remove any watermark in the picker here.
     * Instead, preserve the editor's normal check and override ONLY rejections
     * for the six verified templates (111/88/89/90/92/91) on a Xiaomi 17 Ultra photo.
     */
    private void hookAllowLegacyLeicaWatermarks(ClassLoader cl) throws ReflectiveOperationException {
        Class<?> templateCls = Class.forName("i70.e", false, cl);
        Class<?> exifCls = Class.forName("pc.b", false, cl);
        Class<?> photoTypeCls = Class.forName("o80.f", false, cl);
        Class<?> checkerCls = Class.forName("w60.x0", false, cl);
        Class<?> rejectedCls = Class.forName("e70.a$b", false, cl);
        Class<?> acceptedCls = Class.forName("e70.a$a", false, cl);

        // IMPORTANT: i70.e.d is NOT the asset/template ID! The editor maps
        // asset IDs and internal names can differ; use the logged name field.
        // i70.e.a contains the tested template names (e.g. '徕卡_111').
        Field templateNameField = templateCls.getDeclaredField("a");
        templateNameField.setAccessible(true);
        Field successField = acceptedCls.getDeclaredField("a");
        successField.setAccessible(true);
        final Object accepted = successField.get(null);
        Method exifRead = exifCls.getDeclaredMethod("f", String.class);
        exifRead.setAccessible(true);
        Method check = checkerCls.getDeclaredMethod(
                "a", templateCls, String.class, exifCls, photoTypeCls);
        check.setAccessible(true);

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
