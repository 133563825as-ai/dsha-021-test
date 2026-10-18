# DSHA 0.1.7-rc2 修复前问题台账

基线见 `baseline.json`；逐文件审查层级见 `coverage.csv`。六个工作包与主代理交叉审查已完成，`baseline-revalidation.json` 证实清单中 1,306 个文件在审计期间未漂移。本页按固定基线收录 **22 条有代码证据的缺口：P1 2 条、P2 16 条、P3 4 条**；其中有些触发及影响仍需要隔离夹具或真机动态验证。另将证据不足的风险单列。历史报告不充当本轮通过证据。此页为修复前原始台账，后续新发现和修复状态另记，不回写抹除原始结论。

## 已证实的代码缺口

### SEC-01 · P1 · 虚拟屏原生授权可由通用设备命令绕过

`HttpShellService.java:834-843` 的 `/app/vscreen/*` 入口要求 `uiAuthorized`，但 `DeviceShellPolicy.java:108,127-137` 把 `app_process ... VirtualScreenCore --launch` 当作普通 `VIRTUAL_SCREEN` 计划；`HttpShellService.java:499-502,589-607` 的 `/device/execute` 可以将其交给 Root、Shizuku 或 ADB。调用者选择端口与 token 后，`VirtualScreenCore.java:35-42,94-119` 只按该 token 鉴权，创建和输入路由不再核对原生 ScreenSessionGrant。已持有桥 token 且设备通道可用的调用方因此可在未取得本次屏幕确认时启动独立 Core；它也不在 `VirtualScreenManager` 的撤销跟踪中。

修复应在通用设备命令入口拒绝此启动类型，并使受管启动及 Core 操作绑定原生授权和代次。先用纯逻辑拒绝测试验证，再在隔离设备验证未授权时无监听、无虚拟屏及授权撤销后失效。兼容风险是内部虚拟屏启动目前复用同一 `RootShell/ShizukuShell/AdbBridge` 通道，不能只删白名单而不迁移内部入口。

### SEC-02 · P1 · 虚拟屏 classpath 未绑定当前 APK

`DeviceShellPolicy.java:128-137` 与 `VirtualScreenCore.java:73-77` 仅要求 classpath 以 `/data/app/` 开头、以 `.apk` 结尾，没有拒绝 `..`、软链接或替代 APK，也没有核对本应用当前 `ApplicationInfo.sourceDir`。符合其余参数格式的遍历路径可进入 `DeviceShellExecutor` 的 `ProcessBuilder`。在用户已打开 Root 且 `su` 授权有效、目标 APK 可被特权进程读取并含同名主类的条件下，该类将以 Root 身份加载。没有创建或运行攻击 APK。

修复须同时限制通用入口与特权端，精确绑定签名 DSHA 当前 APK 的规范路径，并对遍历、替代 APK、链接和合法受管启动做回归。此结论针对 DSHA 桥承诺的设备命令边界；项目安全模型明确说同 UID 的任意插件代码不受该解析器强制隔离。

### SEC-03 · P2 · Root 原始文件读取绕过短信独立授权

`DeviceShellPolicy.java:27,57` 允许无读取路径范围的 `cat` 等查询；`RootShell.java:33-36` 只在计划种类为 `SENSITIVE_READ` 时核对短信授权。因此 Root 通道会把读取短信提供者数据库及关联文件视为普通 `READ`，`cp` 还允许从只读源复制到可写目的地。用户已开启 Root、获得 `su`，但短信预授权仍关闭时，与项目规定的“当前用户严格 content query”边界冲突。数据库实际路径因设备而异；未读取真实短信。

修复需要为 Root 读取建立能力范围，并在特权端复核源路径与当前用户，覆盖数据库、WAL/SHM 和复制源的合成测试；不在正式设备输出短信正文。

### DATA-01 · P2 · 恢复任务持久留存解密明文

`NativeBackupJobs.java:288,299-307,317-352` 把输入、`restore.dshdata` 和解包后的 payload 写入操作目录。`finally` 只清密码和任务控制；没有清除已完成恢复的明文。`PortableBackupCrypto.java:43-46` 在最终 GCM 标签验证前已向目标写出部分明文，因此错误密码或损坏归档也可能留有未认证内容。影响包含会话、附件、项目和可能的密钥副本，受应用私有目录保护但留存无期限。

修复要区分已提交事务、未确认退出及恢复现场；在安全终态清理明文输入与 payload。用合成哨兵数据覆盖成功、密码错误、取消和中断后的状态，不清除仍需恢复的事务证据。

### DATA-02 · P2 · 配置重置把解密 API Key 留在事务目录

`HarnessController.java:865-871` 在重置时取得可用的明文 API Key 并组成 `.env` 内容；`ProfileSettingsTransaction.java:131` 将该输入保存为 `host-backup-operations/<id>/environment-input`。已提交事务后文件仍保留，形成一份永久的明文密钥副本。需要同时核对 `previous/environment` 为回切保留的旧配置，不得盲删未完成事务。

用合成密钥调用生产重置链，验证提交后的敏感暂存清理与中断回切。重置范围还需和 `AGENTS.md` 描述的 `NativeConfigurationReset` 契约核对。

### DATA-03 · P2 · 备份事务 64 条历史形成永久容量悬崖

`BackupLimits.java:12-16` 把 `host-backup-operations` 总目录数限制为 64；`NativeBackupJobs.java:73,185,283` 的新作业入口达到上限即拒绝。`HostPendingTransactions.java:12,22` 在超过上限时抛错并报告 blocked，`BackupManager.java:116-124` 将其并入维护门禁。正常自动备份只轮换确认健康的自动副本；手动、未知和失败记录按规则保留，故长期使用可耗尽操作槽位。

修复应把活跃事务检查与历史保留解耦，保持手动、未知和部分记录可访问；用隔离文件系统创建 64、65 条历史及新作业，验证维护不会误报待恢复事务。

### DATA-04 · P3 · `.previous` 中断窗口缺少通用收敛

`BackupFileSystem.java:61-65` 的原子替换先将正式文件移动为 `.previous`，再发布新文件；在两步间进程中断，下一次同名写入会报 `UNFINISHED_RECORD_WRITE`。若目标是 `host-backup-catalogue/latest.json`，`NativeBackupJobs.java:85,231,239` 可能在外部归档已写出并核验后报告失败，之后的 catalogue 更新仍失败。现有个别调用方有恢复逻辑，但此固定记录缺少对应收敛。

使用隔离文件系统在两次 rename 之间模拟中断，验证重启后能鉴别有效版本、恢复目录并如实报告外部产物；不能只静默删除 `.previous`。

### RUN-01 · P2 · 受管运行时事务历史也有 64 条上限

`ManagedRuntimeTransaction.java:39,166` 的 `create()` 以 `host-runtime-operations` 所有目录计数，而成功的 `trimOlder()` 只清理旧 `previous` 树，不轮换事务目录。达到 64 条后新受管更新被持续拒绝；`EnvironmentMaintenance.java:47` 在 stage 或 prepare 尚未完成的失败窗口还可能保留占位目录。需要隔离夹具覆盖超过 64 次成功/失败交错更新、恢复与新建，不删除未知或未关闭现场。

### RUN-02 · P2 · 基础环境重建第八次后可能持续拒绝新建

`EnvironmentRebuildTransaction.java:15-17` 的 `create()` 对 `host-environment-operations` 目录总数设置 8 条上限；`cleanupCompleted()` 仅在有完整证明时清理 `previous-linux`，没有删除已完成事务目录。正常重建历史累积到八条后，新重建会报 `ENVIRONMENT_RETENTION_LIMIT`。修复须区分已封存历史与待恢复现场，让有证明的老事务可归档而不删改未知旧系统；用隔离夹具连续完成九次重建及中断回切。

### DATA-05 · P2 · 配置快照修复操作另有 64 条持久上限

`ConfigurationSnapshots.java:140,193` 在 `startup-config-operations` 达 64 条时拒绝创建和完整扫描。快照保留轮换只处理 `startup-config-snapshots` 的版本槽，未轮换已完成修复操作目录；长期修复后可能卡住后续启动恢复。应复用统一的已完成日志归档契约，并用 65 次合成创建/恢复验证，不删除异常或未封存现场。

### PLUGIN-01 · P2 · 恢复图改写包声明却保留旧依赖快照

`NativePluginGraph.java:141-149` 将第三方包与 `.dsha-dependencies.json` 一起备份。`PluginRestoreGraph.java:171-181` 在恢复时将依赖声明改写成本机 `link:`，另存原声明但未重建快照；`QuarantinedPluginReview.java:45-52` 又复制并重建候选。用户确认恢复启用时，`plugin-manager.py:320-325` 进入依赖准备，`plugin-dependencies.py:216-228` 看到旧快照就按旧 `manifestSha256`/依赖树核验并拒绝。这使含本地链接依赖的完整恢复插件留在隔离区、不能激活。修复须按实际恢复树与冻结锁重核后更新可信快照，或如实降为待审阅状态；不得重新解析最新依赖冒充原版本。测试用有快照和链接依赖的合成 v5 包走完整恢复、审阅、启用。

### PLUGIN-02 · P2 · 无版本第三方插件可启用并破坏 rc2 模型请求

`plugin-manager.py:234-256` 的准入只强制合法 `name` 与 `dsh.bundle.patch`；`plugin-lifecycle.py:105-121` 将缺失的 `version` 变成空串。用户审阅后仍可启用，而锁定 rc2 的 `dsh_plugin_packages` 在模型 HTTP 前枚举活动插件并要求有效版本，最终抛 `REQUEST_EXTENSION`。需在安装、恢复候选和再次启用路径统一要求有效 package version；用无版本合成包完成准入链，并调用真实 rc2 `deepseekLlmApiExtensions.prepare()` 验证拒绝发生在启用前。

### PLUGIN-03 · P2 · 插件安装事务日志达到 128 条后永久挡住后续安装

`plugin-transactions.py:70-78` 的 `workspace()` 保留每次成功、失败及未写完的事务目录，但按目录总数 128 拒绝新事务；`PluginInstallJournals.java` 负责扫描/恢复，不做已完成日志轮换。长期安装、更新、回退及恢复候选启用会耗尽槽位。修复要对已提交且字节仍符合证据的日志建立安全归档/容量策略；所有未完成、未知、被后来修改的原件继续保留。用 129 次成功/失败交错操作验证下一次仍可开始。

### PLUGIN-04 · P2 · 恢复插件和旧预设候选共享 64 条隔离上限

`QuarantinedPluginReview.java:15-16,43-44` 对 `dsha-native-plugin-reviews` 的总目录数设置 64 上限；候选原件按保护要求保留，未见安全轮换或逐项移出入口。累计 64 个候选后，正常恢复插件或旧预设无法继续进入原生审阅。修复需要保留源隔离原件、对已确认处理的候选建立可审计归档或按需分页读取，并验证第 65 个候选可审阅。

### UI-01 · P2 · 长驻预览的上传缓存没有会话总量上限

`WebUploads.java:32-73` 每次文件选择只限制当次 20 项/256 MiB，并在 `cache/web-uploads` 创建新的随机目录。`WebPreviewActivity.java:72` 与 `GeckoPreviewActivity.java:56` 把每次副本追加到 retained session，直到整个 ViewModel 清除才统一回收。长期打开网页并反复上传会持续增加缓存，最终可占满可用存储。修复须保留仍由浏览器引用的文件，并引入可解释的会话容量/回收策略；验证多次接近上限的上传、旋转与退出后的实际占用。

### UI-02 · P3 · 移动手势标记强引用已分离 DOM 节点

内置移动插件 `lib/client.js:96,139-181` 使用强 `Map` 登记手势目标及祖先，只在后续事件再次沿同节点路径经过时懒清理。动态列表卸载的节点不会再被访问，长会话反复手势可令旧 DOM 节点持续留在 JS 堆。修复应更新 `tools/apply-mobile-client-patches.mjs` 管理的源码差异，再生成受管插件资产；优先弱引用或有界定时清理，并用真实 rc2 行为测试和堆快照确认没有回归。

### UI-03 · P3 · 应急网页复用后界面语言不会重新同步

主浏览器恢复时同步 locale（`WebPreviewActivity.java:569`、`GeckoPreviewActivity.java:423`），而应急 `RecoveryWebSurface.java:55` 在 loaded 后跳过重新注入；`RecoveryWebActivity.java:63` 恢复只轮询服务，应急 Gecko surface 也没有 locale 更新。应急会话存活时切换中英文或重建 Activity，网页可能保持旧语言。修复应沿已有同源 locale bridge 在两个应急浏览器的恢复生命周期同步，并在真机切换语言、旋转后确认。

### BUILD-01 · P2 · 增量资产输出可保留已删除输入

`tools/prepare-standard-assets.py:95-107` 只向现有 `app/build/generated/standardAssets` 复制当次输入，没有删除上轮才存在的相对路径；`app/build.gradle:108-110,133-147` 把整个输出目录视为 APK 资产源。删除或改名补丁/资产后，旧文件仍可能被打入新包。`tools/prepare-recovery-assets.py:75-105` 对应输出只处理本轮固定归档及已知两种共享副本，也未全量收敛孤儿文件。修复应在经过绝对路径验证的专有生成目录进行新鲜输出或按完整清单安全清理，增加“先生成、删除输入、增量再生成、APK 不含旧字节”的集成回归。

### BUILD-02 · P2 · 当前稳定性门禁仍绑定旧 alpha 运行时

`tools/verify-stability.py:116-120` 固定读取 `app/build/locked-dsh-runtime-rc1/node_modules/@deepseek-ai/dsh/package.json`，再要求它等于当前 `tools/dsh-runtime/package.json` 锁定版本。当前被读取的实际版本为 `0.1.7-alpha.2`，本轮锁为 `0.1.7-rc.2`；脚本执行到该处必报 `Locked current DSH runtime is unavailable`，不能作为 rc2 的完整验收门禁。修复应从带输入摘要证明的当前测试运行时选择源，拒绝陈旧目录，并增加锁定版本变化时的输入契约测试。

### BUILD-03 · P2 · 受管运行时描述符的 Gradle 输入不完整

`tools/prepare-runtime-descriptor.py:33-38` 将 `backup/RuntimeTrialRecords.java`、`util/ManagedRuntimeLayout.java`、`util/ManagedAssetVersion.java` 以及当前 `tools/dsh-runtime/package.json` 纳入 runtimeId / DSH 版本；`app/build.gradle:156-162` 的 `prepareRuntimeDescriptor.inputs` 却未列这四个输入。仅这些文件变化时，增量构建可能继续沿用旧的受跟踪 `runtime-descriptor.json`，从而把错误运行时身份打入新 APK。修复要补齐全部真实输入，并验证每个遗漏输入单独变化都会触发重算且生成身份跟随变化。

### BUILD-04 · P2 · 单一稳定性脚本不足以证明当前发布门禁

`tools/verify-stability.py:79-121` 未调用当前统一插件门禁、应急 APK 核验与真实 rc2 移动 Modal 行为测试；`--device` 在解析参数后即拒绝，脚本无法取得真机覆盖证据，却仍有独立的 `--deliver` 拷贝入口。最新 rc2 记录显示这些检查曾由独立命令完成，因此这不是对现有 APK 的失败判定。修复应将当前必需的软件门禁串入同一可追溯验收清单，真机验收与候选 APK 摘要绑定后才允许本地交付；不能重新启用旧审计 APK 路径。

### BUILD-05 · P3 · ARM 构建脚本会持续改变共享 SDK 的 aidl

`build.sh:230-243` 的 ARM 兼容路径会重命名所选 SDK 的 `aidl` 并放入 shim，脚本退出后没有可靠恢复。这会污染共享 SDK，让后续构建行为依赖上次运行。修复应使用隔离 SDK 工具副本，或为所有正常/异常退出路径提供经过验证的恢复；不能在当前 Windows 构建结果上冒称 ARM 路径已动态验收。

## 尚待动态核验或判据裁决

- `WebProcessManager.java:193-203` 的全局 `/proc` 扫描在 owner 不可读、状态为 `DENIED` 时跳过。需结合已记录 PID 的严格检查和 Android hidepid 行为裁决；不能把所有其它应用的拒读 PID 当成本应用 Web，也不能漏掉已知同 UID 进程。
- `PtySession.java:157-185` 在 launcher 已退而同 session guest 仍活着时仅验空，不主动回收；是否发生及是否导致维护持续阻塞，需要隔离 PTY/proroot 夹具验证。
- `DshaDocumentsProvider` 的创建、改名、删除路径存在父目录链接替换竞态的可能；尚未用并发夹具证明越界。
- `HttpShellService` 的剪贴板入口和敏感应用判据需结合已公开的能力边界与设备实际行为复核。
- `plugin-manager.py` 删除第三方插件仍用临时目录暂存，没有持久插件事务日志；强杀窗口可能令源码、链接与清单局部切换，需隔离夹具验证后定级。

## 方法边界

第一批发现来自源码调用链，尚未等同于本轮测试通过。所有高风险修复都要由主代理复核、补真实边界测试，并在完整问题报告封存后才修改业务实现。
