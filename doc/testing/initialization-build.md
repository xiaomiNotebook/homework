# 仓库初始化构建记录

- 日期：2026-11-22
- 分支：`member-6`
- 上游基线：MiCode Notes `81a66024fe48eaa2206b854430856fe3875ff3ba`
- 环境：Windows、JDK 21.0.10、Android SDK Platform 34、Android Build Tools 34.0.0
- 构建入口：`src/maintained/`

## 基线完整性

- 下载的上游提交包含 147 个文件、39 个 Java 文件。
- `src/original/` 除本仓库额外的 `UPSTREAM.md` 外，147 个路径及 SHA-256 均与上游提交一致。
- `src/maintained/src/` 包含同样的 39 个原始 Java 文件。
- 其中 31 个文件与 `src/original/src/` 字节级一致；成员 6 先前提交的 8 个文件仅新增注释，共新增 479 行，没有删除或新增非注释代码行。
- `AndroidManifest.xml`、`res/`、`NOTICE`、原始 `README`、版权声明及来源记录均已复制到 `src/maintained/`。

## 构建兼容配置

上游提交采用旧式 Android 目录布局，没有 Gradle、Maven 或 `Android.mk`。初始化在 `src/maintained/` 增加最小 Gradle Wrapper 和构建配置；不修改 `src/original/`。

现代 Android SDK 已删除源码调用的 `Notification.setLatestEventInfo`。`compat-stubs/` 仅在编译启动类路径中补充该签名，不进入 APK，因此无需为初始化改写上游 Java 源码。

## 执行命令

```powershell
$env:ANDROID_HOME = "D:\androidsdk"
$env:ANDROID_SDK_ROOT = "D:\androidsdk"
.\gradlew.bat --offline clean assembleDebug
```

## 结果

- 结果：`BUILD SUCCESSFUL`
- Gradle 任务：33 个任务完成
- APK：`build/outputs/apk/debug/MiCodeNotes-debug.apk`
- APK 大小：1,050,838 字节
- APK SHA-256：`A2CBA7C8DA8D77D976D82C55473E2277506B733784A7C3BE0F335CFC535341D7`
- 包名：`net.micode.notes`
- `minSdkVersion`：14
- `targetSdkVersion`：28
- `compileSdkVersion`：34

构建产物和 Gradle 缓存由 `.gitignore` 排除，不提交到仓库。
