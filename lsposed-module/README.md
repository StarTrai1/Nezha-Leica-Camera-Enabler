# Nezha Leica Enabler — LSPosed v1.3

专为 Xiaomi 17 Ultra 打造的徕卡影像增强模块，完善相机与相册的徕卡水印、相框及拍摄信息展示体验。

**作者：loong**。使用现代 libxposed API 102，不使用旧版 XposedBridge。

## 固定作用域

`staticScope=true`：`com.android.camera`、`com.miui.mediaeditor`、`com.android.settings`。

- 相机：DexKit 定位水印名称 Provider，保留原有已验证功能。
- 相册编辑器：仅对「徕卡_111 / 88 / 89 / 90 / 92 / 91」、仅在特定 Xiaomi 图片且原校验拒绝时返回通过；其他模板不受影响。
- 系统设置：拦截 `miui.hardware.input.InputFeature.supportCameraMRFunction()`，隐藏没有对应硬件的大师变焦环入口；摄影套装手柄入口不受影响。

没有 Hook `com.hyper.gallery.plugin`；失效的旧相机环/相册名称 Hook 已移除。不会伪造 ISO、快门、焦距等 EXIF。

## 构建与验证

```sh
./gradlew :app:assembleDebug
```

安装 APK、启用 LSPosed 模块并重启。测试六个水印、相机水印名称和系统设置的大师变焦环入口。
