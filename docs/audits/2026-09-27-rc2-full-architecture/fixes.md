# DSHA rc2 审计修复与验证进度

修复前依据是 `audit-seal.json` 锁定的 `findings-before-fixes.md` 与 `architecture-before-fixes.md`。本文件记录封存后的代码改动、新发现、最终验收与仍未覆盖的矩阵；正式 APK 已按当前验收范围交付，未覆盖矩阵不得冒充通过。

| 问题 | 负责人 | 当前状态 | 验证仍需完成 |
|---|---|---|---|
| SEC-01 至 SEC-03 | 权限代理 | 代码、两版 JUnit、宿主 Python/AIDL 门禁通过；通用 `app_process` 被拒绝，Root 敏感读取复核 | Root/Shizuku/ADB 虚拟屏完整授权撤销的本机动态矩阵未逐通道执行 |
| DATA-01 至 DATA-05 | 数据代理 | 代码、隔离 JVM/JUnit 29 项、两版全量 JUnit 通过；手机同签覆盖保留安装时间和 7 个插件 | 全部历史归档及逐会话内容未逐项验收 |
| RUN-01、RUN-02 | 运行时代理 | 第 65 次受管更新、第 9 次环境重建等 JUnit 纳入两版全量通过 | 真机连续历史容量与未关闭现场矩阵未故障注入 |
| PLUGIN-01 至 PLUGIN-04 | 主代理 | 插件恢复快照、版本准入和历史容量的 JUnit/Python/真实 rc2 插件门禁通过 | 真机完整第三方插件恢复启用链未在正式个人数据上故障注入 |
| UI-01 至 UI-03 | 界面代理 | 两版 JUnit、真实 rc2 Modal/快捷键/手势宿主夹具及应急双内核网页可见 | 真机多批文件上传、缓存回收、语言切换与长会话堆测仍未执行 |
| BUILD-01 至 BUILD-04 | 主代理 | 当前运行时描述符、双版 JUnit/Lint/签名 APK/ELF/插件/应急门禁与设备摘要绑定全部通过 | 每个单独输入变化触发 Gradle rerun 的完整动态矩阵未逐项跑 |
| BUILD-05、BUILD-06 | 主代理 | 假 SDK 的 aidl 正常/中断/缺失窗口及恢复夹具通过，真实 AIDL 事务号核对 | ARM 主机 aidl shim 路径未动态构建 |
| DEVICE-01（封存后真机新发现） | 主代理 | 单次前台化与 worker 就绪门禁已入两版正式包；两版聚焦 JUnit、最终安装时段无新增应用崩溃 | 生产数据上不做后台前台服务强制故障注入，确切异常窗口未再次触发 |
| DEVICE-02（封存后真机新发现） | 主代理 | 两内核共用来源转换；两版 JUnit 与新 Standard 应急 WebView 真机网页通过 | 应急页面语言切换/旋转完整矩阵仍未执行 |

## 封存后新增的代码发现

- **BUILD-06 · P3 · 旧 aidl shim 的在位判据读取了错误行。** `build.sh` 原来用 `head -1` 查“由 build.sh 生成”的标记，但生成的 shim 第一行是 shebang、标记在第二行。若上次被异常中断、已有 `.x86_64.disabled` 备份，新运行会误认非 shim、移动旧备份并覆盖原件，可能使 SDK 中只留下 shim。已改为检查前两行、校验备份存在、拒绝未知状态并在退出时恢复；尚无 ARM 动态验收。
- **DEVICE-01 · P1 · 后台数据保护通知刷新引发正式包进程崩溃。** Low 候选同签覆盖安装后，于 2026-09-27 06:31:20（设备本地时间）记录 `ForegroundServiceStartNotAllowedException`，栈为 `DataProtectionService.refresh → show → publish → startForeground`。源码当时每 600 ms 都重复 `startForeground()`；首次成功后若界面退到后台，系统可拒绝下一次调用。已改为仅首次前台化，之后 `NotificationManager.notify()` 更新已有通知；所有手动宿主备份/维护 worker 等待实际前台化成功再处理数据，失败留记录。修复后的两版正式包已同签覆盖，最终安装时段 crash buffer 无新增应用崩溃；未在正式数据上强制复现异常窗口。此处是封存后动态发现，不回写修复前 22 条台账。
- **DEVICE-02 · P2 · Standard 应急 WebView 入口误判来源。** 同签正式 Standard 候选的独立应急 DSH 已就绪，但点「进入空白应急 Web」显示 `RECOVERY_ORIGIN_INVALID`；Low Gecko 入口能显示应急网页。`RecoveryWebSurface.load()` 直接用正式的 127.0.0.1 base 调 `localhostBase()`，而该函数只接受应急 localhost；Gecko 先转换，故两内核分叉。现由 `RecoveryLocalePolicy.launch()` 同时转换 base 与 auth 并校验同源，两内核共用；最终两版 JUnit 和 Standard/Low 应急网页真机复验通过。

## 修复前风险裁决

- `WebProcessManager` 的 owner 未知且 `/proc` 拒读条目不能单独证明属于本应用；全局扫描跳过这类未知进程是避免 Android hidepid 把其它应用永久锁进本应用维护屏障。已把候选判据提取并写夹具；旧 Web PID 记录仍由独立严格核验。原审计中该项保持“风险”而非升级为已证实缺陷。
- PTY launcher 退出后 proroot guest 留存的具体设备场景尚未安全复现；保留当前 fail-closed 维护门禁，不做无证据的进程信号改动，正式包验收仍须报告这一矩阵缺口。

## 本轮非破坏性证据

- 修复前源码清单 1,306 项 hash 复核没有漂移，现有两版正式 APK 的 SHA/签名/包名/版本码可核。
- JS 110、Python 80、JSON 46、XML 148、Shell 24、PowerShell 4 文件的修复前语法检查通过。
- 100 条合成聊天、6 次挂载的宿主浏览器基线见 `chat-render-baseline.json`；不是 Android 真机性能结论。
- `tools/test-generated-asset-directory.py` 三项通过，包含删除输入后的完整标准资产增量生成；随后两版真实 APK 资产及应急映射验包通过。
- `tools/test-aidl-shim.ps1` 在隔离假 SDK 中验证了正常退出、中断现场接管、替身文件缺失窗口、未知备份拒绝和原件恢复；未修改共享 SDK。
- `tools/test-runtime-descriptor-inputs.py` 检查 12 个 Java 启动器与当前 DSH 包版本的 Gradle 输入契约，通过。
- 无版本、空版本、非 SemVer 插件均在原生审阅/启用前被拒绝；有效 rc2 预发布版本仍通过。对应单项 `test-plugin-review.py` 通过。
- `tools/test-release-evidence.py` 的 3 项通过：旧摘要/缺 flavor/缺必要行为/首次安装时间未保持均拒绝。最终交付门禁实际绑定两版手机回读 APK SHA、E7E3、包名、版本码、原首次安装时间和五项非破坏性检查，返回 `PASS_FOR_EXECUTED_SCOPE`。
- 数据域隔离 JVM/JUnit 29 项通过，包括中断回切、65 条旧完成恢复目录的明文迁移、65/129 次新建、手动与自动副本保留差异；最终 Gradle 两 flavor 各 723 项单测、0 失败、1 项既有跳过。
- 受管依赖证明已用只读 `prepare-backup-assets.py --check` 核对当前离线归档，计 572 个包；最终 Gradle 以 `--check` 核对受跟踪证明和 `runtimeId=25e652f1d4007270fec6f2fef39a065ca0e482a7472c5b44a187f63ca98a02ae`，没有在构建中改写源码树。
- 正式手机现装 APK 的完整字节 SHA-256 与修复前 `release/dsha-0.1.7-rc2.apk` 一致，证书为 E7E3；启动页显示 rc2、3080 READY，插件管理显示 7 个插件。私有截图和原始安装包仅在忽略的 `app/build/audit-private/`，不进入公开报告或源码快照。
- `tools/run-unit-tests.py` 在当前 Windows 本地缓存上缺 AndroidX 编译 classpath，早期目标测试未完成。后续以完整 Gradle 两 flavor JUnit 为准；该辅助脚本本来定位于缺 AAPT2 的 ARM 主机。
- 最终整轮软件与设备交付报告：`app/build/stability-acceptance/9ebcb050-6a51-4ff2-963a-26a777d95996/manifest.json`，状态 `PASS_FOR_EXECUTED_SCOPE`。Standard `4840b234a065e2d05971ffde53eed8f4560e4f1943ed171087c349973895f5e4`；Low `e8e91578b5fad9dd13083acd887ab7d160ba7f518e678139d239add21b11c13e`。手机最终安装 Standard，与 `release` 字节一致；历史首次安装时间未变。原始截图与设备证据只在忽略的私有构建目录。
- 合成 100 条聊天、六次挂载复测见 `chat-render-after.json`：100 行与 41,740 HTML 字节保持，中位耗时约 33.1 ms，修复前约 33.1 ms。此测试不覆盖手机内核长会话、耗电、真实 16 KiB 设备或模型服务。
