# A 组修复后定向复评（2026-09-28）

本页仅复评架构边界、运行时编排和进程生命周期；不改源码，也不重新全库扫描。评分沿封存的 `architecture-before-fixes.md`：0 健康、5 债重，六维权重不变。证据采用当前源码一次定向引用核对、`app/build/stability-acceptance/4c0ca181-a3f0-4046-b8e5-865a23f527ab/manifest.json` 和 `agent-c-process-evidence.md`。软件回执为 `PASS_WITH_EXPLICIT_DEVICE_GAPS`：Standard/Low 各 767 单测、0 失败/0 错误/1 项既有跳过，双 Release Lint、E7E3 APK/ELF、受管资产和插件门禁通过。它不是完整设备矩阵。

## 本组闭环证据

| 项 | 修复后的实际边界与验证 | 结论边界 |
|---|---|---|
| R2-A01 维护权威 | `MaintenanceGate` 持有唯一归档锁、独占状态和 owner；`MaintenanceCoordinator` 接入 Android 的终端/Web 停止、`EnvironmentTaskGate`、`RuntimeTasks` 围栏与进程复核。生产写者由旧 `BackupManager` 维护入口迁至协调器，根包保留兼容委托；runtime 用 `RuntimeWorkPort` 和 `HostMaintenancePending` 读取窄契约。组合测试覆盖 detached guest 阻断、围栏内外来任务拒绝、取消和异常释放；架构负向检查通过。 | 维护策略不再由 runtime 反向查询根包；这不等于整个 Java 包图无环。 |
| R2-A02 bounded proroot | 原 API 33 shell UID 隔离夹具先证实 launcher 退出约 6 秒后 guest 仍可写。候选只对有界、非交互 proroot 使用独立会话监督：native leader 处理父死亡，握手前记录本次 leader PID/出生时刻/session；Java 在释放工作锁前核对初始进程组及整个 session，重启遇 live/未知记录阻断维护。匿名 pipe 设备夹具的“正常关闭、子 shell STOP 后父退、握手前父退”三例通过，且 guest 结果哨兵均未出现；原子写部分记录及未知成员的 JVM 回归已进入双 flavor 全量。 | 设备夹具在 shell UID/SELinux 域，不能证明 app UID、各 ROM 的 `/proc` 可见性或正式数据路径。native 父死信号只作用初始组；逃到其它组但仍在同 session 的成员依赖 Java 检查/持久记录保持阻断。未知身份不自动猜杀。 |
| R2-A03 Web 代次 | 生产直接构造点收敛至 `HarnessController.get()`；`WebRun` 按 generation 持有 launcher、bridge lease、鉴权 URL、实际端口和兼容模式。鉴权发布再次核对代次；旧 stdout 回调不能发布新端口/URL，相关单测已通过。 | 静态进程级队列仍存在，且 Android 真实慢启动/回调时序尚需设备证据。 |
| R2-A04 窄命令与资产 | `ManagedRuntimeAssets` 实际承担受管 staging、路径/别名清单、描述符与健康摘要，`ProotBootstrap` 保留兼容门面。主启动 rc1 prepare/finalize、内置注册、插件审阅写者、旧备份 RPC 和运行试验检查在解释正文前核对有类型退出码、超时与截断；旧字符串 API 由同一 typed 命令组装/执行适配。`GuestCommandOutcome` 独立测试覆盖成功标记后非零退出、超时和截断。 | A04 是有价值的纵切面，尚未完成全部 `ProotBootstrap` 职责分离或所有诊断入口迁移。 |
| R2-D01 资产流 | `HarnessController.readAssetText` 用 try-with-resources 管理输入；读取异常返回原兼容空串并写脱敏失败原因。异常流关闭与换行行为测试纳入全量。 | 无设备资源长期负载测量。 |

## 仍可安全推进的债与必要复杂度

1. `ProotBootstrap` 仍包含运行方式选取、冷安装、组补丁、迁移/插件脚本和部分命令执行。此前把 `flattenL2sChains` 列为“仍在运行的字符串成功判据”不准确：定向引用检查在 main/test 未发现调用者，普通启动按 AGENTS.md 也禁止遍历旧链接。它可作为死入口候选移除，历史 `flatten-l2s.py` 资产须保留。真实可达的 `PluginRepository.discard-preview` 取消链仍忽略脚本失败，需按本次 typed 退出与 JSON status 核对后才显示清理成功。`runtime → core` 的 DNS/proroot 配置与冷安装诊断边已有 A05 条件性收敛，不能再按未迁计分。
2. bounded proroot 的 durable record 可在 leader 或 session 身份不可读时阻止写入，却不保证自动救出所有残留 guest。后续应先在正式非调试 App UID 下做非破坏性诊断和停止/重启可见性验收，再考虑按本次出生身份提供受控恢复。不能以 PID、端口、名称或旧 session 号直接发信号；不能在正式个人数据上故障注入。
3. 对每次有界 proroot 命令的 session 核验会扫描 `/proc`；当前没有同场景设备耗时、FD/PSS 或长会话耗电样本。只有测到具体开销后才优化扫描；优化必须保持身份未知时的围栏。旧 `BackupManager` 兼容委托和历史数据格式亦须在迁移调用方后再删除，不宜为依赖图整洁一次性移除。

保留的复杂度包括正式与独立应急运行根、proot/proroot、WebView/Gecko、历史 v5/旧格式读取、原生 PID 出生身份及 Host 事务回切。它们分别满足离线/兼容/数据保护需求，不能仅凭文件数或类大小归为可删债。同 UID guest 并不是 OS 沙箱，应用层门禁的准确适用范围仍需说明。

## 六维建议分数

| 维度（权重） | 修复前 | 暂定修复后 | 依据与置信度 |
|---|---:|---:|---|
| 职责与依赖边界（20） | 2.50 | **1.75** | 维护权威、真实写者和受管资产证明已有独立 owner 与测试接口；仍有配置/诊断反向读取及兼容门面。**中高**。 |
| 状态、并发与生命周期（20） | 2.75 | **1.75** | WebRun 代次、真实任务围栏、bounded 会话关闭/持久记录覆盖主要已知窗口；App UID 与厂商进程身份矩阵未验。**中**；若该矩阵失败，应提高分数并单列阻断问题。 |
| 数据事务与故障恢复（20） | 3.00 | **1.75** | 本组停止围栏与未确认 guest 保留已收敛；全轮报告显示插件卸载及历史归档完成日志/异常路径有独立测试。旧用户档逐份恢复不在本轮证据内。**中低**（B 域只借交付报告，未重深读）。 |
| 补丁与兼容负担（15） | 2.00 | **1.50** | 单源运行输入、锁定应急前置兼容和 typed 命令主路径减少契约漂移；旧诊断字符串 API/Android 多内核适配仍在。**中**。 |
| 测试与交付可信度（15） | 1.75 | **1.50** | 767×2 单测、双 Lint、E7E3 签名/ELF/资产、插件与源码快照绑定通过；设备合同明确缺项且 Low 已有同签回读，Standard 与 App UID 等仍待验。**中**，不能将软件 PASS 写成完整发布完成。 |
| 性能与可诊断性（10） | 2.50 | **2.00** | pending 返回域/操作/错误，历史稳定态门禁 0/65/1000 完成记录约 55/60 次 FS 调用；长会话、手机堆/FD/PSS 和新 session 扫描耗时未实测。**低中**。 |
| **加权建议** | **49.25/100** | **34.0/100** | Σ(score/5×weight)，工程债估计，不是代码质量百分比或发布判定；总体**中等偏低置信度**。 |

设备后续结果应更新 R2-A02 和状态/测试维度：若 App UID 的 bounded 诊断无法建立可信监督器，或未知 guest 仍能越过维护围栏，则当前暂定分数不成立，需按实际失败重新定级；若通过，也只把对应设备/flavor 路径计为已验，不延伸到 Android 6/7、16 KiB 真机、全部外部模型和厂商 ROM。当前没有证据支持把整体债直接降到 20 以下。

## A05 追加纵切面（待主代理定向软件门禁）

`agent-a-next-plan.md` 记录了实施前调用链。现由 `DshaApp` 组合根绑定 `ConfigStore.runtimeSettingsSnapshot()` 和冷安装诊断 sink；`RuntimeHostPorts` 将 DNS、所选模式、static loader、seccomp、工作区组成一次只读设置映像。正式 Web 启动、bounded/固定 proot、试运行、冷安装和 PTY 的 argv/env 在各自 invocation scope 复用该映像；下次调用重新读取。`DiagnosticRepository` 的网络修复、工具探针和所选运行方式 smoke 在解释 marker 前先核对 typed 退出/超时/截断，并报告该次实际 mode。当前定向 `rg` 在 runtime Java 未再命中 `core.*` 或 `getSharedPreferences`；纯端口测试的默认样本、切换、嵌套、一旦取消或 sink 抛错时的释放/不遮蔽原错误 3 项通过。完整 Android 编译/设备表现仍待主代理门禁。

按同一冻结锚点，本次**若**定向两 flavor 编译和运行输入合同通过，职责与依赖边界建议由 **1.75 → 1.50**（唯一配置读取权威，runtime 无正式设置/诊断反向知识；但根包兼容门面与 A04 余项仍在），状态/并发/生命周期由 **1.75 → 1.50**（命令模式、DNS、PTY argv/env 同一快照；但 bounded guest 的 App UID 进程矩阵不因此自动通过）。其它四维不因 A05 的代码量改变，暂沿前表；条件性加权为 **32.0/100**，当前已验证回执仍是上一节的 **34.0/100**。两维置信度分别中/中低；若 Android 门禁或同次快照行为不符，保持原分并按失败重新定级。

剩余安全可实施项是旧诊断字符串命令调用逐项迁 typed、`RuntimeHostPorts` 诊断 sink 持久化失败时的受控可见性，以及应用 UID 对所选运行方式的真实探测。`ManagedRuntimeAssets`/`RuntimeTools` 仍必须处理签名 APK 资产、旧归档和 guest 兼容，不能因保留 Ubuntu/Node/proot/proroot 本身继续扣分；也不应为降低依赖图分数把独立应急恢复绑到正式设置。

## A01–A05 剩余项的可行性队列（gate6 源码冻结时只读核对）

| 优先顺序与性质 | 具体文件、真实链路和剩余后果 | 已有保护/证据 | 有限下一包或外部条件 |
|---|---|---|---|
| **1 · A02 待验证** | `ProotBootstrap.collectRootfs → IsolatedInstallProcess.close/boundedSessionEmpty → BoundedGuestSessions`；App UID 下若 `/proc` 会话成员不可读，会保留工作锁和磁盘记录，不能自动解除维护阻断。native 父死只结束初始进程组；其它组内的同 session 后台任务需 Java/记录确认。 | API33 shell UID 的旧 launcher 留 guest 机制已复现；候选匿名 pipe 三窗口 PASS，双 flavor JVM、签名ELF过关，未知时 fail-closed。 | 先在用户授权的正式非调试 APK 上做固定只读 bounded 命令、停止/重启和会话身份可见性验收。失败时按真实 errno/记录定级，不能在正式数据上故障注入或按裸 PID 猜杀。此项需设备外部条件，不能靠新增抽象关闭。 |
| **2 · A04 可实施的窄余项** | `PluginRepository:399–401,424–435` 的 `discard-preview` 是可达取消链：脚本结果未经退出码/JSON status 核对，仍可显示“已清理”或“已取消”。`ProotBootstrap.smokeTest` 是兼容文本门面，生产 `DiagnosticRepository` 已改 typed。`flattenL2sChains` 在 main/test 无调用，不能算当前误报路径。 | 主启动、插件审阅写者、旧备份 RPC 与受管试运行已先验 typed 状态；`GuestCommandOutcome` 测试覆盖成功 marker 后失败。 | 只迁真实 `discard-preview`：独立于已取消 `activeTask.check()` 核对 typed 完成与 `PLUGIN_RESULT.status=ok`，失败保留 preview/候选并显示脱敏原因。死 `flattenL2sChains` 方法可在确认无反射入口后删除，保留历史脚本资产；不为它新增执行路径或测试 APK。 |
| **3 · A01 可实施但收益需对准停机责任** | `MaintenanceCoordinator.AndroidPorts.stopTerminals:75–77` 仍直接调用两个 UI Fragment 的静态关闭入口；维护顺序由纯 gate 持有，但若标签实现变化，core 与 UI 的停止契约需双处同步。根包 `BackupManager:37–61` 兼容委托和归档业务仍引用 core；这些是历史入口，不构成第二把维护锁。 | `MaintenanceGate` 有唯一 archive lock/owner，真实 `RuntimeTaskRegistry` 并发/取消测试通过，设备合同列有终端关闭后重启。 | 只有在真机终端关闭验收稳定后，考虑让终端会话所有者提供一处 `closeAllAndConfirm` 窄端口，UI 只展示/委托；以出生身份和 guest 退出为完成判据。逐个移除无调用的旧委托需先列真实调用方，不能为包图好看重写归档引擎。 |
| **4 · A03 以设备证据决定** | `HarnessController` 仍保留进程级队列、`webRuns/currentWebRun`、`lastStopError`（约47–87、760–800）；这些跨 Activity 重建共享状态是必要的。当前未发现迟到 stdout 可写新 run 的源码路径；慢鉴权、前台服务重建与 ROM 拒信号仍依赖设备时序。 | 生产构造点已统一 `get()`；每代 launcher/lease/auth/port 有 owner，代次发布单测与停止身份门禁通过。 | 先跑本轮正式包慢启动/停止/切页场景。若出现资源滞留或旧回调错误，再针对真实代次重现补清理；仅因静态字段或类长度继续拆分无实质收益。 |
| **5 · A05 条件性结构债** | `RuntimeHostPorts.emit:51–57` 在冷安装诊断 sink 抛运行时异常时只在内存留异常类名；原 guest/安装错误仍传播，但页面可能缺持久诊断。`DshaApp → ConfigStore.runtimeSettingsSnapshot → RuntimeHostPorts` 现是唯一正式设置链，runtime 对 core/SharedPreferences 的定向扫描为零。 | 纯端口默认/切换/嵌套/异常测试通过；`DiagnosticRepository` typed 失败路径已发布可见卡片与状态。gate6 和设备行为仍待最终回执。 | 若存储/诊断 sink 故障的实测表明确实不可见，再把 `diagnosticFailure` 作为受限原生状态显示，不写用户命令或敏感值。当前先等待定向软件与 App UID 运行诊断；不让独立 Recovery 读取正式配置。 |

上述队列的“待验证”不按失败扣分，也不能算通过；“可实施”须有具体调用方和关闭测试，必要产品复杂度（独立应急、proot/proroot、双浏览器、历史归档、PID 出生身份与停止屏障）不是单独扣分项。A05 的条件性 32.0/100 仍取决于 gate6 与设备结果，不因列出余项再调整数字。

## gate8 后 A 组两维再校准（2026-09-28，只读）

本节覆盖前文 gate6 时点的条件性判断，不改其它四维，也不以目标总分倒推。gate8 `ede1e5ac-d931-4f21-b58e-66156ec29608/manifest.json` 为 `PASS_WITH_EXPLICIT_DEVICE_GAPS`：Standard/Low 各 781 单测，失败/错误均 0、既有跳过 1；Lint、E7E3 APK/ELF 与资产门禁通过。正式设备矩阵仍在主代理执行，以下是两维建议而非发布结论。

| 冻结维度 | 修复前 | gate8 后建议 | 可证的结构/行为变化与真正剩余项 |
|---|---:|---:|---|
| **职责与依赖边界（权重20）** | 2.50 | **1.25** | `MaintenanceGate` 仍是唯一停机/owner 围栏；新 `TerminalSessionOwner` 实际持有 PTY 与简易终端的两张原 `TerminalTabs`，简易 buffer/draft/cursor/session 同属无 View 的 `SimpleTab`，用户关闭与维护关闭调用同 ID、同退出判据，UI 仅订阅并渲染。`SimpleTerminalBackend` 把简易进程门禁与关闭规则从 Fragment 移出，`MaintenanceCoordinator` 不再静态调用 UI 终端类；独立复核补回了主线程先拒绝，双 flavor 781 项通过。A05 的 `ConfigStore` 单映像由 DshaApp 组合根注入，runtime 定向扫描无 `core.*`/SharedPreferences。A04 的受管资产证明与实际 typed 插件清理完成判据也有单一生产路径。**剩余**：根包 `BackupManager` 的兼容委托及历史归档业务仍引用 core；`ProotBootstrap` 仍承担多种真实启动/冷安装职责，旧文本 API 留给特定兼容调用者。它们是局部边界成本，当前没有第二把维护锁或可达未核对的 discard 成功文案。置信度**中高**。 |
| **状态、并发与生命周期（权重20）** | 2.75 | **1.50** | WebRun 按代次拥有 launcher/bridge/auth/port；MaintenanceGate 与 RuntimeTasks 实际组合测试覆盖 detached guest、竞争/取消/异常，PTY/简易终端迁 owner 后仍用原出生身份、`finishAndWait`/`disposeAndWait`，失败保留 tab 与编号，observer 可解除且旧页不能解绑新页。A05 的 mode/DNS/static-loader/seccomp/PTY argv-env 同次配置快照与下次切换、未初始化拒绝、sink 异常已有纯测试；typed 诊断失败现有可见卡片和明确状态。**剩余**：A02 在 app UID、Android 6/7、16 KiB 与不同 ROM 的 `/proc`/父死时序尚未形成完整矩阵；未知 session 继续保留磁盘记录并阻断维护，这是必要 fail-closed，不应为“不能自动猜杀”单独扣分。gate8 软件通过及当前 Low PTY 固定 echo/维护 READY 是局部设备证据，不替代完整矩阵。置信度**中**；若后续 app UID 证实越过围栏，须重新定级，不沿用本分。 |

前文 A04 队列中的 `discard-preview` 和死 `flattenL2sChains`、A01 的 core→UI 静态停机边、A05 “sink 失败仅内存不可见”均是**实施前时点**：现分别由严格 Gson 完成解析与可重试 preview、移除无调用 Java 方法且保留历史脚本、真实单 owner、以及 RootDiagnostics 的非空失败卡片/脱敏报告收敛，不能再作为当前未修债重复扣分。若没有新的可复现调用链问题，不因技术债分数仍高而发明包；正式/独立应急运行根、双运行方式、旧数据读取和出生身份保护都是产品必要复杂度。
