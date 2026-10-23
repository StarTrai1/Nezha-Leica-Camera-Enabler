# Nezha Leica Enabler

专为 Xiaomi 17 Ultra 打造的徕卡影像增强模块，完善相机与相册的徕卡水印、相框及拍摄信息展示体验。

适用：Xiaomi 17 Ultra（`nezha`）、HyperOS 4，需 Magisk/KernelSU 与支持 libxposed API 102 的 LSPosed。

## 功能

- 使用系统属性启用徕卡主题/相机相关功能（`module/system.prop`）。
- 优化相机徕卡水印的设备名称显示。
- 保留相册水印列表；针对实机验证的六款徕卡模板（111、88、89、90、92、91）放宽特定照片的参数资格校验，不伪造 EXIF。
- 在系统设置隐藏标准版没有的「大师变焦环 / Camera ring」设置入口，不影响摄影套装的相机手柄设置。

## LSPosed 固定作用域

`META-INF/xposed/module.prop` 使用 `staticScope=true`，只声明以下三个目标：

- `com.android.camera` — 相机水印
- `com.miui.mediaeditor` — 相册徕卡水印资格校验
- `com.android.settings` — 隐藏大师变焦环入口

不注入 `com.hyper.gallery.plugin`：当前模块没有针对该包的 Hook。

## 安装

1. 由 GitHub Actions 构建的模块 ZIP 在 Magisk、KernelSU 或 KSU Next 中安装。
2. 安装内置的 `NezhaLeicaEnabler.apk`（在 Android 环境刷入时会尝试自动安装），并在 LSPosed 启用 **Nezha Leica Enabler**。
3. 重启手机，测试设置入口及相机、相册水印。固定作用域由 APK 声明，不需要添加其他应用。

> CI 每次生成的 Debug APK 可能使用不同证书。若自动升级提示签名冲突，先卸载旧的 LSPosed 同伴 APK，再安装新版，并重新启用模块。保留 Android 包名 `io.github.mexusbg.leicaframes`，避免不必要的包名迁移。

## 构建

```sh
cd lsposed-module
./gradlew :app:assembleDebug
```

构建工作流位于 `.github/workflows/release.yml`。作者：**loong**，版本：**v1.3**。

感谢 Max Weinbach 对相关 Leica 属性的发现。项目与 Leica/Xiaomi 官方无隶属关系。
