# 构建与测试

## 工程结构

| 路径 | 内容 |
| --- | --- |
| `app/src/main/java` | 原生检测、前台服务、权限、音频、时段与记录 |
| `app/src/main/assets` | 已编译的离线界面及静态资源 |
| `ui-src/app.js` | 可编辑的界面脚本源文件 |
| `tests` | Java 规则测试、模拟通信界面测试、原生测试 |
| `tools` | 界面、独立 APK 与原生测试 APK 构建脚本 |

Java 代码的包名为 `dev.hazel.livealarm`，安装后的应用包名为 `dev.hazel.livealarm.multi`（因此多主播版可与单主播版 1.0.5 并存）。当前 `versionName=1.1.0`、`versionCode=25`。应用运行不需要第三方 Android 依赖库；开发测试工具与运行时分开。

界面页脚和通知排查包里的版本号由应用读取已安装的包信息得到，源码中没有写死的版本常量；升级时仍需同步 `app/build.gradle`、`package.json`、`tools/build_apk.py` 的默认版本（它们决定 APK 里写入的版本）及发布说明。

## Android Studio / Gradle

使用 JDK 17、Gradle 8.11.1、Android Gradle Plugin 8.9.2、Android SDK Platform 35 和 Build Tools 35.0.0。版本匹配参考 [AGP 8.9 官方兼容表](https://developer.android.com/build/releases/agp-8-9-0-release-notes)。

仓库暂未包含 Gradle Wrapper。先安装上述 Gradle 版本，在 Android Studio 导入工程，或在工程根目录执行：

```bash
gradle :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/`。`assembleRelease` 默认不配置发行签名；发行时可在 Android Studio 选择自己的密钥签名。

Gradle 路径未在本次发布环境完整执行；现有发行 APK 使用下述独立 SDK 构建流程产出。请勿把版本兼容说明当作已执行的 CI 结果。

## 修改界面

已有 `app/src/main/assets/app.js` 可直接构建 APK。修改 `ui-src/app.js` 后执行：

```bash
npm install
npm run build:ui
```

脚本使用固定版本 esbuild 0.25.9，将界面转译到 Chrome/WebView 60 语法目标。提交界面修改时同时提交对应生成资源。

## 独立 SDK 构建（Linux）

需要 Python 3、JDK 17 和 Android SDK。自行准备签名密钥，并将密钥与密码文件保存在仓库外。

```bash
python3 tools/build_apk.py \
  --platform "$ANDROID_SDK_ROOT/platforms/android-35/android.jar" \
  --tools "$ANDROID_SDK_ROOT/build-tools/35.0.0" \
  --keystore /path/to/your-release.p12 \
  --password-file /path/to/your-password.txt \
  --alias your-key-alias \
  --output build/ManquAlarm-multi-1.1.0.apk
```

默认使用 `javac`；只有 JRE 时可附加 `--ecj /path/to/ecj-3.39.0.jar`。流程依次执行 AAPT2、Java 编译、D8、zipalign 和 apksigner。密钥别名必须与密钥库里实际的别名一致（用 `keytool -list -keystore <文件> -storetype PKCS12` 查看），否则签名会以 `does not contain a key` 失败。

原发布密钥不在仓库或公开发行附件中。自己生成的密钥不能用于覆盖原签名版本。修改版本时同步调整 `app/build.gradle`、`package.json`、`tools/build_apk.py` 的默认版本及发布说明。

## Java 规则测试

无需 Android 设备或 Android SDK，使用 JDK 17：

```bash
python3 tools/test_core.py
```

只有 JRE 的环境可执行：

```bash
python3 tools/test_core.py --ecj /path/to/ecj-3.39.0.jar
```

测试覆盖时区和时间段、场次去重、通知授权与声音同意策略、守候通知状态、主播列表校验与修复、多主播轮询计时与检测保活间隔、周表条目校验与修复。不覆盖 Android 服务、界面、真实网络与真机行为。通知导出测试见 [`tests/report/README.md`](../tests/report/README.md)。

改动 Android 端 Java 后，若本机没有 Android SDK 就无法编译验证，可先做一次结构自检（感知字符串与注释的括号、方括号、引号配平）：

```bash
python3 tools/check_java_balance.py
```

它只能发现手改造成的机械损伤（多一个或少一个括号），**不能代替编译器**：类型、符号和调用签名错误都发现不了。

## 界面测试

在装有 Node.js、Playwright 和 Chromium 的开发环境执行：

```bash
npm install
npm install --no-save --package-lock=false playwright
npx playwright install chromium
npm run test:ui
```

`HAZEL_TEST_CHROMIUM` 可指定已有 Chromium，`HAZEL_TEST_ASSETS` 可指定待测 APK 解出的 assets，`HAZEL_TEST_OUTPUT` 可指定结果和截图目录。测试模拟 Android 通信，不检测实际系统权限或手机音频。

若浏览器下载被网络阻断（`npx playwright install` 连接 `storage.googleapis.com` 超时），可直接指向本机已安装的 Chrome 或 Edge，无需下载：

```bash
HAZEL_TEST_CHROMIUM="/c/Program Files/Google/Chrome/Application/chrome.exe" node tests/ui-regressions.cjs
```

## 原生测试

`tests/native/` 与 `tools/build_native_tests.py` 是开发用测试工具，测试 APK 需与应用使用同一签名。在可重置的测试设备上安装主应用与测试 APK，再运行：

```bash
adb shell am instrument -w dev.hazel.livealarm.tests/dev.hazel.livealarm.tests.NativeTests
```

需按照测试类中的前置条件配置通知状态；测试会修改测试应用状态。普通用户不需要安装测试 APK 或使用 ADB。当前版本仅完成该测试 APK 的构建，没有把它计入真机测试通过项。
