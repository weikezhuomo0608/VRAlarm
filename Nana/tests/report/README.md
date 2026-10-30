# 通知排查包 JVM 测试

执行真实 NotificationReport.java；Android Context / PackageManager / 包信息使用此目录的测试替身。Prefs 替身仅提供 JSON 写入。不代表系统权限查询或小米 ROM 的实际行为。

测试依赖 org.json:json:20240303（仅在测试中使用，APK 中不打包）。可在 Java 8+ 环境下编译：

```bash
mkdir -p build/report-tests
javac -encoding UTF-8 -cp /path/json-20240303.jar -d build/report-tests \
  $(find tests/report -name '*.java') \
  app/src/main/java/dev/hazel/livealarm/NotificationReport.java
java -cp build/report-tests:/path/json-20240303.jar ReportTests
```

只有 JRE 时可用 java -jar /path/ecj-3.39.0.jar -1.8 替代 javac。16 个断言覆盖导出范围、诊断模式选择、APK 与分包内容、摘要、不可读取与非系统组件、限额及取消。
