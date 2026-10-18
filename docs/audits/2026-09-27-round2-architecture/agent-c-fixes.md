# C 组封存后修复与反证

`agent-c-audit.md` 和 `agent-c-coverage.json` 保持 `audit-seal.json` 的原始摘要；本文记录封存后核验与改动。

## R2-C01：语言桥与独立应急根前置兼容已补

正式根的 `RuntimeTools.patchBrowserBootstrap()` 会在解压后将 ES 兼容插入 HTML，但应急 `RecoveryRuntime` 从独立锁定归档解压，不执行正式根的受管补丁。只读核验发现 `tools/recovery-runtime/archives/recovery-dsh-runtime.bin` 与正式 `app/src/main/assets/dsh-runtime.bin` 的 SHA-256 都是 `18ca9a502d3784cf907b2848a434c12139251bce97c4d7a24adeece75b975c3a`；锁定 tar 中唯一 `dsh-web-frontend/dist/index.html` 为 825 字节，缺 `DSHA_BROWSER_COMPAT_BEGIN` 标记，首个 `<script` 在 byte 456。相同归档字节不意味着解压后已应用正式补丁。PDF Worker 的正式运行时修订也不能从这份原始归档推断为应急已覆盖。

`RecoveryWebSurface.onPageFinished()` 对同源 localhost 页、且 WebView 缺 `DOCUMENT_START_SCRIPT` 时只补 `WebPageScripts.language()`；`RecoveryLocalePolicy.needsFinishedPageLanguageBridge()` 给出纯逻辑边界。前置兼容由另一条独立链处理：`tools/recovery_runtime_overlay.py` 从固定应急 tar 提取三个精确路径，按锁定锚点生成 HTML（兼容脚本早于应用脚本）、PDF 主线程及 Worker、资源 URL 的最终字节。`prepare-recovery-assets.py` 把原始与最终 SHA、最终文件大小纳入应急描述符和新的 `runtimeId`，并把三份覆盖字节放入 APK 资产。`RecoveryRuntime.prepare()` 先验证固定原始归档，再逐个校验目标原摘要与随包最终摘要、原子覆盖，最后对候选完整文件证明执行 `verifyRoot()`；旧 ID 胶囊不被覆盖。`verify-recovery-apk.py` 核对 overlay 与最终文件 proof。页面完成回调不承担早期兼容职责。

独立 `app/build/round2-audit/c/recoveryAssets` 生成成功，新的应急 ID 为 `234c710056c44b9b46a5e6911694762a8a13c8243cf33a9696db55dda6cb2d07`。`tools/test-recovery-browser-overlay.mjs` 对这份真实 pinned 产物通过 HTML 脚本时序、PDF 模块注册及 Worker 前导验证。仍需 Gradle 真包、APK 审计和缺文档起始能力的真实旧 WebView 验收，不能把宿主夹具扩写为手机覆盖。

## R2-C04：共享上传请求所有权

新增 `BrowserUploadRequestState`，正式 WebView 与 Gecko 的 retained 状态都为每次文件选择持有 ticket。新的 chooser 替换、导航、取消或页面关闭会使旧 ticket 失效；后台复制完成后必须同时核对 ticket、原浏览器和原缓存会话，才提交现有 `WebUploads.Batch`。平台各自的 chooser、WebView `content://` 回交、Gecko 文件回交及现有 40 文件/512 MiB 预算不变。`BrowserUploadRequestStateTest` 对两种 adapter 的替换、导航、旋转保留与关闭迟到结果作纯逻辑测试。真实两内核 Activity 文件回调仍待设备验收。

## A 组协同

按 A 组清单把 `AdbPairActivity`、`ConfigFragment`、`PtyTerminalFragment` 三处生产 UI 的 `new HarnessController(...)` 改为 `HarnessController.get(...)`；根包的 `DeviceBridgeService` 及 controller owner 由 A 组处理。

另将 `InstallFragment`、`EnvironmentUiStatus`、`MainActivity`、`TerminalFragment` 中六处 `BackupManager` 只读维护门禁改为 `MaintenanceCoordinator.pending/isExclusive/isEnvironmentTaskBusy`。`isEnvironmentTaskBusy` 仍表示独占维护或一般环境任务忙，未用于把普通启动/插件查询显示为维护横幅。

独立只读复核 `ScreenSessionGrant.completeConfirmation` 与 `HttpShellService.uiAuthorized`：弹窗前记录的运行 generation 和授权 revision 在返回后重新核对；敏感应用一次性确认不记住授权。弹窗期间撤销会增加 revision，使旧结果拒绝。直接 JUnit 5 项通过。确认返回与紧随其后的实际 UI/虚拟屏操作之间仍不是原子动作；极窄窗口内的随后撤销没有二次检查，需由主代理裁决是否纳入本轮加固。

## 本地验证

- Java 17 直接编译纯逻辑类及 JUnit：`BrowserUploadRequestStateTest`、`RecoveryLocalePolicyTest` 共 6 项通过，输出在忽略的 `app/build/round2-audit/c/`。未运行 Gradle。
- `node tools/test-web-ui-host-fixtures.mjs` 通过，覆盖现有上传预算与应急 Gecko 语言 relay 宿主夹具。
- `tools/test-recovery-assets.py` 12 项通过、1 项因宿主无软链接能力跳过；`tools/test-recovery-apk-assets.py` 8 项通过；真实 pinned 归档生成器及 Node overlay 行为夹具通过。
- `ScreenSessionGrantTest` 由 Java 17 直接运行。原命令在 PowerShell 中设置 `$cp='app/build/round2-audit/c;app/build/round2-audit/c/junit-4.13.2.jar;app/build/round2-audit/c/hamcrest-core-1.3.jar'`，执行 `F:\DSHA\_toolchains\jdk-17\bin\javac.exe -encoding UTF-8 -cp $cp -d app/build/round2-audit/c app/src/main/java/com/deepseekharness/app/util/ScreenSessionGrant.java app/src/test/java/com/deepseekharness/app/util/ScreenSessionGrantTest.java`，随后执行 `F:\DSHA\_toolchains\jdk-17\bin\java.exe -cp $cp org.junit.runner.JUnitCore com.deepseekharness.app.util.ScreenSessionGrantTest`。输出为 `JUnit version 4.13.2`、`OK (5 tests)`，退出码 0。
- 未操作设备、未生成或安装 APK；UI 和实际旧内核行为仍待完整门禁与设备检查。
