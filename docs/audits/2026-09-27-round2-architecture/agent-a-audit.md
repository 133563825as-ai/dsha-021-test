# 第二轮架构审计：A 组（运行时与进程生命周期）

范围：当前工作树的 Android 宿主架构、正式 Web/PTY/应急进程身份和维护并发。先读 `README.md`、`AGENTS.md`，再读上一轮 `final-assessment.md`、`findings-before-fixes.md`、`fixes.md`；本轮只读产品源码，未运行 Gradle、未接触手机、未改实现。以下行号对应 2026-09-27 当前工作树，历史修复项不重新登记为缺陷。

## 实际调用链与已有保护

- 正式启动：`HarnessController.requestStart`（240–309）检查环境、未完成维护与环境任务凭据；串行 worker 执行 `startWeb`（311–450），先让 `WebProcessManager.stop` 核验旧 Web，再准备运行时、插件、迁移和启动器。stdout drainer 在官方鉴权行后登记 PID 出生身份（453–485）；停止走哨兵、同一队列和 `WebProcessManager`（684–733）。`WebProcessManager.confirmStopped`（288–301）另核对已跟踪启动器及同 UID Web。对未知 PID/出生身份仍阻塞，不存在可据此建议按端口、名字或裸 PID 强杀的证据。
- 数据维护：`BackupTask.start`（104–147）持有 `EnvironmentTaskGate.Lease`，`BackupManager.runDataTask`（54–85）先关闭 PTY 与简易终端，再等待正式 Web 停止，随后取得 `RuntimeTasks.tryEnterMaintenance` 的原子围栏并执行事务。`RuntimeTaskRegistry`（24–45）在维护期间拒绝其它线程/脱离作用域的新工作，允许维护线程自身的同步嵌套任务。`RecoveryRepairBroker.confirm`（253–277）把已确认提案重新送入同一数据维护链。
- PTY：`PtySession.start`（89–109）在 fork 前登记 detached 工作锁，JNI 交握取得出生身份；`finishAndWait`（157–178）核验启动器退出后再要求整个会话为空；`TerminalProcessCloser`（23–68、70–107）在组长存活时核对会话与 UID 并回收成员，组长已退出时只查空，不给可能复用的会话号发信号。上一轮所述“launcher 退而 guest 留存”仍是待验证场景，不能定为已证实缺陷。
- 独立应急：`RecoveryController`（63–105、107–219）有独立 worker、代次和停止/格式化门禁；`RecoveryRuntime`（305–334、345–455）持久登记 launcher/guest 身份，停止要求两者退出。`READY_READ_ONLY` 不登记正式 Web 扫描豁免，原生写入仍须 `RecoveryRepairBroker` 的维护事务。这是应保留的安全边界。

## 本组问题与关闭条件

### R2-A01 · P2 · 结构债：运行、数据与根包协调形成双向依赖

**证据与链路。** 根包 `BackupManager.java:6,41–85` 导入并操作 `core.HarnessController`；`core/HarnessController.java:256,315–345` 又调用根包 `BackupManager`。`runtime/ProotBootstrap.java:135–170,1036–1103,1179–1182,1278–1285` 直接读取 `backup` 描述符、数据任务 owner 和 pending 状态及 `core.RuntimeTasks`；`core/EnvironmentMaintenance.java:18–68,70–123` 反向驱动 `ProotBootstrap`、`RuntimeTrial` 和多种 backup 事务。`RecoveryRepairBroker.java:253–277` 再跨入 core 与根包。显式导入只是依赖下界，代码中的全限定名调用还扩大了环。

**根因与影响。** 维护授权、运行时准备和事务提交的权威分散在包之间；例如 `ProotBootstrap` 同时能问“是否持有数据停止屏障”和执行 guest 命令，`BackupManager` 同时决定停机顺序和调用 runtime。更换一项维护门禁需要跨层查所有入口，难以给危险动作建立可枚举的唯一调用点。这是可证实的结构债，不代表当前门禁已被绕过。

**有限方案。** 先定义只包含 `runSnapshot`、`runExclusiveMaintenance` 与只读 `pending` 的宿主维护端口，由一个协调器持有 `EnvironmentTaskGate`、Web/终端停止证明和 `RuntimeTasks.Maintenance`；`ProotBootstrap` 只接收已取得的不可伪造作用域对象或保持当前调用方注入，不再反向查 `BackupManager`。首批只迁移 `EnvironmentMaintenance` 更新/重建与 `BackupTask` 入口，并保留旧 API 委托；不要一次性迁移所有备份与插件路径。

**关闭条件。** 静态依赖检查证明 `runtime` 不再引用根包 `BackupManager`、`core.RuntimeTasks`，新端口只有一个生产实现；隔离并发夹具覆盖 Web 启动与维护争用、PTY detached 工作锁、快照与独占维护的嵌套/抢占，确认未知进程仍阻塞。两 flavor 既有单测及最终正式包边界验收仍是后续实施门禁；本轮未运行。

**实际迁移责任与边界。** 第一纵切面应由 `core` 的进程级维护协调器持有 `EnvironmentTaskGate`、`restoring` 状态、PTY/简易终端关闭顺序、`WebProcessManager` 停止证明、`RuntimeTasks.Maintenance` 和归档互斥锁；`BackupManager` 只保留归档/URI 业务，旧入口暂作转发。必须在同一工作包迁移真实写者：`BackupTask.java:128,169,198`、`RecoveryRepairBroker.java:258`、`PluginRepository.java:314`、`PluginActivationHooks.java:49`、`NativeBackupJobs.java:223,339,385`、`ProfileSettingsTransaction.java:50,73`、`StorageMaintenance.java:46`。之后把 `isDataTaskOwner` 与 `hasPendingMaintenance` 的权威同移，使 `EnvironmentMaintenance`、`ProotBootstrap`、`RuntimeTrial` 等不再问根包 `BackupManager`。若只搬 `runDataTask` 的代码、仍由这些调用点经旧根包返回 core，则依赖环未减少，不算完成。`RuntimeDescriptor`、`BackupJson`、`UserDataLayout` 等中只有不依赖 Android/事务 IO 的数据契约才适合后续收敛为纯 contracts；单纯更换包名同样不计成效。

### R2-A02 · P2 · 风险：通用容器命令的工作锁寿命由 launcher 句柄定义

**证据与链路。** `ProotBootstrap.collectRootfs`（1011–1023）、`runRecoveryMaintenance`（1072–1103）、`execRootfsInteractive`（1137–1159）和冷安装命令（1416–1432）在未确认退出时把 `RuntimeTasks` token 交给 `Process`；`RuntimeTasks.waitForRetainedExit`（72–88）在该句柄的 `exitValue()` 成功后释放。相对地，隔离 `RuntimeTrial.CheckedExit`（149–156）会在 launcher 退出后额外调用 `WebProcessManager.confirmTrackedTrialStopped`。PTY 使用独立会话空检查（`PtySession.java:175–178`）。这说明各进程类别采用不同的“已退出”证明强度。

**根因与影响。** 通用执行器无法从普通 `Process` 句柄证明 proroot guest 全部退出。若 launcher 提前结束而 guest 留存，token 可能先释放；是否能在真实命令路径发生、guest 是否触碰待替换树，现有源码和上一轮真机证据均未证明。故本项是待验证风险，不能作为提前改信号或清锁的理由。

**有限方案。** 先用隔离的 proot/proroot 进程组夹具记录 launcher、guest 的 PID/出生时刻/会话与工作锁时间线，分别覆盖正常、超时、取消和 launcher 异常退出。仅在复现危险窗口后，为相关执行器引入与试运行相同强度的本次实例退出证明；核验未知时继续持锁并保留现场。不把 PTY 已退组长的旧会话号变成信号目标。

**关闭条件。** 夹具能证明每个返回/异常窗口的 guest 状态；若证实留存，则维护在 guest 退出前不得拿到围栏，重启后仍能基于持久身份核验并恢复。若无法复现，保留为风险与设备矩阵缺口，不宣称缺陷已修。

### R2-A03 · P3 · 结构债：正式 Web 的单次运行状态分散在静态字段和多个 Controller 实例

**证据与链路。** `HarnessController.java:35–79` 让 lifecycle、队列、启动器集合、桥 lease、鉴权 URL、端口、兼容模式和停止错误成为静态状态，构造器每次又把静态 `StartupDiagnostics.onHealthy` 改绑到本次 `this`。生产调用方确有直接 `new HarnessController`：`DeviceBridgeService.java:338`、`ConfigFragment.java:222`、`PtyTerminalFragment.java:129`，另有 `HarnessController.get` 单例（139–145）。`startWeb` 的启动、端口/认证、插件/迁移、桥与 LAN（311–450、453–580、628–659）和 `enqueueStop`（684–733）共同修改这些字段，依赖代次检查收敛。

**根因与影响。** 进程级状态和实例级协作者没有同一所有者，理解一轮 Web 的完整资源归属需跨多个方法与对象；新建 controller 会替换健康回调捕获对象。由于这些生产实例通常指向同一应用 Context/rootfs，当前没有证据证明回调已作用到错误数据；问题是结构性维护风险，不按“静态字段多”直接定功能缺陷。

**有限方案。** 先把“本轮 Web”表示为带 generation、启动器句柄、认证信息、端口、桥 lease 与退出证据的内部运行记录；由一个进程级 owner 创建/关闭，并让现有 Controller API 仅委托。先迁移记录与只读状态查询，再迁移启停流程；`WebProcessManager` 仍独占 PID 身份判据。禁止为消除 static 而复制第二套生命周期锁或提前取消慢鉴权。

**关闭条件。** 两个生产 Controller 交替请求启动/停止的隔离夹具证明同一代次只有一个 launcher、旧 drainer 不能覆盖新端口/URL、桥 lease 只按本代释放；健康回调绑定当前 owner；慢鉴权、自动重启、停止排队、proroot 一次兼容重试和未知 PID 屏障行为保持。

### R2-A04 · P3 · 结构债：`ProotBootstrap` 同时承担资产、策略和进程执行

**证据与链路。** 同一类既解读受管描述符/健康证明与 stage（125–246），又管理补丁、内置插件和 rc1 迁移（389–613），拼 guest 命令与环境（831–989），运行维护工具（1036–1112）、PTY 环境（1137–1203）及冷安装（1222–1530）。`execAndRead`（990–1008）还把异常编码成 `ERROR:` 字符串，调用方需要靠标记文本判断成功，例如 `HarnessController.java:351–371` 和 `EnvironmentMaintenance.java:153–175`。

**根因与影响。** 资产完整性、用户数据变更与进程执行错误被一个可广泛调用的对象承载，失败类型容易在字符串协议边界丢失。现有关键调用方有明确成功标记，本轮未发现它们把任意 `ERROR:` 当成功；这是有限重构候选而非重写理由。

**有限方案。** 只抽出有强契约的两块：受管资产 staging/证明与有类型结果的 bounded guest command；保留对外 `ProotBootstrap` 门面并逐入口迁移。先由夹具固定 rc1/插件脚本的原输出和错误语义，再替换调用方的字符串判据。独立应急 `RecoveryRuntime` 不并入正式 rootfs 服务。

**关闭条件。** 目标方法迁出后仍只使用当前锁定资产，冷安装和受管候选的依赖准备/摘要一致；超时、中断、非零退出和未确认 guest 退出各有不同结果并保持门禁；Android 正式/应急运行根完全分离。

## 建议工作包与可行性边界

1. **先做只读/隔离取证**：实现 R2-A02 的 launcher/guest 时间线夹具与 R2-A03 的双 Controller 代次夹具。若系统不允许读取必要身份，记录 `unknown` 并保持围栏；不在真实个人数据上故障注入。
2. **小步实现维护端口**：只迁移受管更新/重建及当前 `BackupTask` 调用；保持已有事务日志、停止哨兵与身份判据。此包的价值是让“谁能移动环境”有单一可测试入口，而非减少文件行数。
3. **收敛单轮 Web 状态与命令结果**：在行为夹具固定后迁移 R2-A03/A04 的窄接口。不要同时重写历史备份、插件安装、应急 DSH 或进程信号规则；它们涉及不同数据代次和设备内核边界。

可行性：纯逻辑围栏与代次可以 JVM 夹具验证；`/proc` 可见性、proroot guest 退出、非调试包和低版本 Android 行为需隔离环境/正式签名设备证据。本轮只封存判断，没有软件测试通过或设备验证结论。
