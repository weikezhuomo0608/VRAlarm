# 多主播开播提醒 · 实现记录

> 状态：**已实现**（2026-09-10）。本文替换了 2026-08-16 的原始计划。原计划的方向可用，但其中 6 处会在真机上出问题，已在实现时修正；如需回顾差异见文末。

## 已确认的产品决定

- 提醒时段与铃声**全局共享**，每位主播只有独立的启用开关。
- 添加主播时**填入 B 站直播间号码，自动识别名称与头像**。
- 主播上限 16 位。

## 分层

改动顺着工程原有的“纯 Java 规则核心 + Android 外壳 + 离线 WebView 界面”三层落位，关键是**把多主播的判定与计时下沉到纯 Java**，这样最危险的部分能在没有 Android SDK 的机器上用 `python tools/test_core.py` 真正跑到。

| 文件 | 责任 |
| --- | --- |
| `Anchors.java`（新建，纯 Java） | 主播列表模型、写入校验 `validate`、读取修复 `sanitize`、`find`/`findIndex`/`firstId`/`fitName`/`validId` |
| `PollPlan.java`（新建，纯 Java） | 轮询计时：唤醒锁预算、限流退避、间隔折算 |
| `Prefs.java` | `anchors` 存取；`gates` 按主播 id 存场次状态；`snapshot.<id>`；`snoozeSession` 记为 `id\|session`；旧数据迁移 |
| `BiliApi.java` | 构造参数化 uid/room；`resolve(room)` 识别主播；`download(url,cap)` 取头像 |
| `AnchorArt.java`（新建） | 头像缓存到私有目录、MIME 嗅探、孤儿清理 |
| `GuardianService.java` | 逐主播轮询、独立去重状态、响铃互斥、整轮限流退避、通知文案参数化 |
| `MainActivity.java` | Bridge 增 `resolveAnchor`/`saveAnchor`/`deleteAnchor`；`state()` 输出 `anchors`；头像路由；备份含主播列表 |
| `ui-src/app.js` | 新增“主播”页与增删改流程；首页与响铃页改为读主播名 |
| `tools/test_core.py` | 加入 `Anchors`、`PollPlan` 及其测试 |

## 数据结构

- `anchors`：`[{id,name,uid,room,enabled}]`，`id ∈ [A-Za-z0-9_-]{1,80}`（同时也是头像文件名，故刻意限制字符集以杜绝路径穿越）。
- `gates`：`{<id>: {session,notified,logged,started,firstSeen,sourceStart,offlineSince,offlineSamples,baseline,unknownBaseline}}`
- `snapshot.<id>`：该主播最近一次检测结果。
- `snoozeSession`：`<id>|<session>`。
- 迁移：`gate` → `gates[<首位主播 id>]`；`snapshot` → `snapshot.<首位主播 id>`；无 `|` 的裸 `snoozeSession` 视为属于首位主播。

## 相对原计划修正的 6 处

1. **限流退避不再逐主播静默吞掉。** 一轮内任一主播收到 412/429 即按限流放慢整轮；全部主播失败时走原有 `failed()` 上报 `networkError`；部分失败时只在失败集合变化时记一次记录，避免每轮刷屏。
2. **唤醒锁与间隔随主播数调整。** 原 `acquire(25000)` 只够一次请求（8 秒连接 + 8 秒读取）。现按 `PollPlan.wakeLockMillis(主播数)` 申请；`PollPlan.gapSeconds` 把本轮耗时从等待中扣除，使“检测间隔”仍描述单主播间隔。单主播时唤醒锁仍是 25000，行为不变。
3. **补齐原计划引用但未定义的东西。** `logMissed`、`getAnchors`、`findIndex`、`lastLiveNames`、`alarmAnchorId` 等；实现里改用 `ring()` 记录错过的主播、`Anchors.findIndex`、`alarmAnchorId`/`alarmAnchorName` 真实写入。
4. **备份包含主播列表。** 导出写 `anchors`，恢复时一并替换（不可用则保留当前列表并提示）。
5. **删除旧单主播接口而非静默兜底。** 移除 `gate()`/`saveGate(State)` 重载，漏改的调用点会直接编译失败，而不是把状态写进错误的主播。
6. **界面按真实 API 重写。** 原计划用的 `bridge.anchors()`/`renderNav` 并不存在；实际使用 `api('state')`/`navigation()`，并新增 6 个界面场景做回归。

## 原计划另外两处会直接报错的地方

- 原计划的 `AnchorsTests.java` 用了 `String.repeat(25)`，而 `tools/test_core.py` 以 `-source 8 -target 8` 编译，Java 8 没有该方法；实现改用 `StringBuilder`。
- 原计划让 `Prefs.anchors()` 直接调用会抛异常的 `validate`；读取路径必须容错，故拆成写入用 `validate`、读取用 `sanitize`。

## 验证

```bash
python tools/test_core.py                      # 36038 + 12 + 13 + 46 + 72 条断言
python tools/check_java_balance.py             # 手改 Java 的括号/引号配平自检
node tools/build_ui.cjs                        # 界面转译到 WebView 60 语法
node tests/ui-regressions.cjs                  # 40 个界面与模拟通信场景
```

Android 端 Java 已用 Android SDK 35（platform-35_r02）+ build-tools 35.0.0 编译通过，D8 / zipalign / apksigner v2+v3 全部通过，产物为独立包名 `dev.hazel.livealarm.multi` 的 APK（可与原版并存）。工具链与自签名密钥都保存在仓库外的本地目录（路径不公开）——**该密钥需保留，换新密钥后无法覆盖升级本应用**。

注意 `tools/build_apk.py` 里两个常量不能合并：aapt2 的 `package` 必须是 Java 命名空间 `dev.hazel.livealarm`（否则 R 类生成到别的包，所有 `R.*` 引用会编译失败），应用 ID 由 `--rename-manifest-package` 单独改成 `dev.hazel.livealarm.multi`。

## 真机踩到的坑：Bridge 动作跑在 UI 线程上

`MainActivity.Bridge.request` 用 `runOnUiThread` 执行 `dispatch`，所以**每个新动作都在主线程上跑**。第一版 `resolveAnchor` 直接在 `dispatch` 里调 `BiliApi.resolve()` 和头像下载，真机上表现为「操作未完成，请重试」——`NetworkOnMainThreadException` 的消息是 **null**，于是落到 Bridge 的兜底文案，看起来像功能没实现。项目里所有网络/文件操作原本都走 `io` 单线程执行器（`refresh`、导出、导入、`GuardianService.check`），新增动作必须照做。

修法：`dispatch` 增加 `requestId` 参数并返回一个 `DEFERRED` 哨兵，表示「稍后从后台线程回复」；`resolveAnchor` 在 `io.execute` 里做完两三次请求后用 `runOnUiThread(() -> reply(...))` 回包。同时把 `AnchorArt.prune` 也移到 `io`（目录遍历属于文件操作）。Bridge 的兜底文案改为在消息为空时带上异常类名，否则这类失败无法诊断。界面上「识别主播信息」要多打两三次请求，故给该动作单独放宽到 40 秒超时并在按钮上显示「正在识别…」。

## 仍未在真机运行

多主播轮询、互斥响铃、头像下载与缓存、升级迁移、备份恢复、保活唤醒与看门狗都只经过编译与纯 Java 层测试。

## 1.1.0：全量复查发现的九个问题

真机反馈“整天没有提醒”之后，对整个应用做了一次从头到尾的复查（1.1.0）。按性质分三类：

**可靠性**

1. **检测循环没有唤醒能力。** 轮询靠 `Handler.postDelayed`，主线程消息队列本身唤不醒休眠的设备；`syncPower()` 只在“优先保证提醒 + 时段内”持有唤醒锁，时段外和关闭该选项时都会释放。新增 `KEEPALIVE` 系统闹钟（id 205，`AlarmScheduler.keepAlive`），在每轮 `scheduleNext` 时按“本轮延迟 × 3、下限 90 秒”重排；循环正常就每轮推后它，只有循环停住时才触发，届时 `ActionReceiver` 先重排再 `send("CHECK")`。停止守候时一并撤销。`PollPlan.keepAliveSeconds` 承载这条算式，因此可被 `PollPlanTests` 覆盖。
2. **`apply()` 的异常会整条终止轮询。** `io` 回调里 `busy=false` 之后直接 `apply(seen,spent)`，既没有 catch 也没有重新排期：一次异常要么以未捕获异常结束进程，要么让循环永久停住而界面仍显示“正在守候”。改为 `try { apply } catch { reportInternalFailure }`，后者按相同间隔重排并只在异常类名变化时记一条记录（避免每轮刷屏）。`busy` 在调用前就已复位，不会卡死。

**正确性**

3. **被互斥跳过的场次没有标记为已提醒。** `ring()` 里 `if(ringing){log("...本场不再重复响铃");return;}` 没有写 `state.notified`，于是第一位响铃一停，下一轮 `LiveGate` 就判为 `live_start` 并接着响——正是界面承诺不会出现的叠着响。改为把 `state.notified=state.session` 落盘，记录写一次（用 `state.logged` 去重）。
4. **新增主播/改房间号会继承旧编号的场次状态。** 主播的 `gate` 以 id 为键，换房间号后旧场次的 `session`/`notified` 仍生效，可能压掉新提醒；而新加入的 `<id>` 没有历史，`armedAt` 却是全局的“开启守候时刻”，于是加入时已在播的直播会被判成刚开播而立刻响铃。新增 `Prefs.armedAt(id)`（全局与按主播取较晚者）与 `Prefs.restartAnchor(id)`（清 gate、清 snapshot、把加入时间记为现在），`MainActivity.applyAnchors` 在“新主播或房间号/UID 变化”时调用；`GuardianService.apply` 改用 `prefs.armedAt(主播)`。
5. **身份校验同时比 UID。** `parse()` 原本要求 `uid` 与 `room_id` 都相等，而 `resolve()` 先取 `room_init` 的 uid、又用 `get_anchor_in_room` 的 uid 覆盖它——两个接口一旦不一致，该主播每次检测都会以“身份校验未通过”失败，永久不提醒，且日志里看不出是配置问题。改为只校验房间号（用户填的、两个接口都会回显的那个值），uid 只用于跳转个人空间；`resolve()` 不再覆盖 init 的 uid。
6. **测试响铃会吞掉真实开播。** 互斥分支把 `testing` 也一并挡住。改为真实开播先 `finishAlarm("dismiss",false)` 结束测试，再开始正式响铃。

**提示与版本**

7. **启动失败提示常驻。** `send()` 失败写 `serviceError`，而 `serviceError` 同时被“发现开播但通知被拒”复用；服务后来起来了也不会清（`onCreate` 只在首次启动时清），于是守候页会一直挂着“后台启动被系统限制”。拆出 `startError` 只记启动类失败，`onCreate` 成功与 `apply()` 有一次检测成功即清空；`serviceError` 在 `refreshNotices` 发现通知已可用时清空。界面按 `serviceError || startError || networkError` 显示。
8. **版本号写死三处。** 界面页脚 `state().version`、通知排查包 README、预览状态各写一份 1.0.5，改版本必漏。前两处改为 `getPackageManager().getPackageInfo(pkg,0).versionName`，从此不可能漂移；`versionCode=7`、`versionName=1.1.0` 只在 `app/build.gradle`、`package.json`、`tools/build_apk.py` 里维护。
9. **无效房间号的提示误导。** `request()` 对所有非零 code 统一抛“B 站接口返回 -400，等待重试”，填错房间号时用户会一直等。`resolve()` 捕获非限流的 `ApiException` 并改抛“没有找到这个直播间，请检查房间号”。

复查同时确认了没有问题的部分：`LiveGate` 的场次判定、`TimeRules` 的跨午夜与夏令时、唤醒锁预算随主播数增长、`Prefs` 的旧单主播数据迁移、备份/恢复路径、无障碍辅助的恢复限速。这九项都只经过编译与纯 Java/界面层测试，**尚未在真机确认**。