# Maintained Source

本目录是小组标注、维护和优化的唯一工作代码树。除本说明和 Gradle 构建配置外，初始文件来自 `../original/`；`../original/` 保持只读，用于基线对照。

原上游提交采用旧式 Android 项目布局，只包含 `AndroidManifest.xml`、`src/` 和 `res/`，未提供 Gradle、Maven 或 `Android.mk`。本目录补充最小 Gradle Wrapper 和构建配置，不移动或改写原始源码与资源。

## 构建

要求：JDK 17 或更高版本、Android SDK Platform 34。第一次构建需要联网下载 Gradle 和 Android Gradle Plugin。

Windows PowerShell：

```powershell
$env:ANDROID_HOME = "你的 Android SDK 路径"
.\gradlew.bat assembleDebug
```

macOS / Linux：

```bash
export ANDROID_HOME="你的 Android SDK 路径"
./gradlew assembleDebug
```

输出 APK 位于 `build/outputs/apk/debug/`。不要提交 `.gradle/`、`build/` 或 `local.properties`。

`compat-stubs/` 只包含现代 Android SDK 已删除的旧 API 编译签名。Gradle 会先将其生成临时 JAR 并放到 javac 的启动类路径；它不会被打包进 APK。这样可以保持上游 Java 源码不因仓库初始化而改写。
