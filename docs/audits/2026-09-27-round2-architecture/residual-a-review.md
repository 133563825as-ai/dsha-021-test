# A 组剩余边界复核（2026-09-28，只读）

范围限 R2-A01–A05，沿 `architecture-before-fixes.md` 冻结的 0.25 级锚点；不改代码、不替主代理宣布最终分数。gate11 回执 `gate11-results.json` 为 `PASS_WITH_EXPLICIT_DEVICE_GAPS`，Standard/Low 各 786 单测、0 失败/错误、1 项既有跳过，Release 软件门禁通过。主代理的 Low 同签正式包还实测了简易终端空列表新建/快速关闭后再新建、PTY 关闭、Web 停止重启、App UID proroot 固定只读诊断三次 `SMOKE_OK`，本机 FD 281/279/279、线程 128 恒定。新发现 D06 应急 EN 网页同步属于 C 组，尚未闭环，不能借本页称整轮发布通过。

## 仍有证据且可安全实施

| 顺序 | 归属/性质 | 真实链路与剩余后果 | 可行的有限下一包和关闭证据 |
|---|---|---|---|
| 1 | **A01 进程身份风险；源码已证判据缺口，错误信号尚未动态复现** | `util/TerminalSession.java:269–274` 只把受控 READY marker 中的 `$$` 数字记为 `run.group`；`core/SimpleTerminalBackend.java:29–37` 停止时只检查 `group>1` 即 `Os.kill(-group,SIGKILL)`，没有保存该 guest 组长出生时刻，也未在发信号前核对同一实例。若原组长和全组退出、组号后被复用，而容器 launcher 仍存活，可能向后来无关的同 UID 组发信号。`ProcessTermination.awaitExit` 与 `Compat.destroy` 是在该组信号之后才执行，不能替代信号前身份核验。这是 A01 owner 迁移前就存在的简易终端路径，不是新 owner 导致；Low 的正常 echo/关闭未触发该窗口。 | 给简易终端 READY 交握增加本次 guest leader 出生时刻与会话/组身份，并在发送任何负 PGID 信号前由宿主按 UID、starttime、session/group 双次核验；读取未知、组长已退或身份变更则不发组信号、保持 `TerminalSession` FAILED/工作锁。不要在 Java 启动后单次临时认领裸组号。隔离 fake `/proc`/进程组夹具覆盖同一组、PID/组号复用、exec 空读、取消与超时；随后正式非调试 App UID 固定无害命令验证握手与失败阻断，不在正式数据上强杀/注入。实现前先确认受管 guest `setsid`/Bash 的真实身份记录时序；无法稳定握手则维持拒绝组杀，不可退回当前裸组号。 |
| 2 | **A01 局部结构债；无现行绕过证据** | `core/TerminalSessionOwner.java:32–33` 仍把可变的原 `TerminalTabs` 对象直接公开给两 UI 的 `TerminalTabBar.render`/查找。生产 Fragment 当前只读这张表，增删/关闭已走 owner，故不是现行漏关；但编译接口仍允许未来调用方绕开 `closePty/closeSimple` 直接 `remove`，使“唯一 owner”靠约定而非类型约束。`SimpleTab` 的草稿/光标仍由 UI 主线程写入，这是页面展示状态，未见并发错误。 | owner 提供只读的当前项/快照视图给 `TerminalTabBar`，UI 继续通过 owner 的 ID 操作选择/关闭；不要复制第二张表。静态边界测试禁止生产 UI 持可变 `TerminalTabs`，既有单调 ID/最小空缺编号/失败占位和双 flavor 单测、真实旋转/切页维持。收益是编译期封住一条旁路，优先级低于上项。 |

第 1 项使**状态/生命周期**仍高于“少量局部债且关键失败行为可证”的 1 分锚点；按冻结规则，信号前身份缺口贴近 **2.0** 的“关键验证缺口”。不把假设的误杀说成已发生，也不能仅以 gate11 通过或低风险无害 echo 抹掉源码判据。第 2 项使**职责边界**仍有局部状态封装债，可维持 **1.25** 的建议；根包 `BackupManager` 的薄兼容委托在生产 main 中已无调用，但 debug/androidTest 有大量历史夹具调用，迁夹具再删属于可安全清理的兼容成本，当前没有第二把锁或实际授权分叉，不能仅为包图降分而排到前面。

## 缺设备或外部条件的验证不确定性

- **A02**：Low App UID 的固定只读 proroot 三次 `SMOKE_OK` 与稳定 FD/线程支持正常有界执行；父死亡、leader 单独退出、ROM `/proc` 拒读以及 Android 6/7、16 KiB 真机仍无同范围证据。现有 native 父死收敛、握手前出生记录、session 空核验及未知时阻断是必要 fail-closed。除非出现真实记录无法收敛或身份误判，不应自动猜杀 guest、解除围栏或扩成新的进程框架。所缺矩阵降低结论置信度，本身不是失败分。
- **A03/A05**：WebRun 迟到回调在纯测试及 Low Web 停止重启路径无已知异常；ConfigStore 单映像/RuntimeHostPorts 的设置切换与未初始化契约有测试，RootDiagnostics 已把 sink 写入失败作为非空卡片和脱敏报告展示。其它 ROM 的慢启动、前台重建或切换语言时序尚未覆盖，先按真实设备问题定向复现，不凭静态字段存在再拆分。

## 当前不应重复计债的必要复杂度与风格项

`ProotBootstrap` 同时面对离线 Ubuntu、proot/proroot、冷安装和受管候选，`ManagedRuntimeAssets` 已抽出 staging/证明，主启动、备份与插件完成判据已走 typed 结果。当前剩余的 `execAndRead`/`runPluginManager` 文本入口主要是兼容门面或历史夹具，`smokeTest` 的生产诊断已改 typed；只有找到可达成功误报链才值得继续迁。类长、static 进程队列、rootfs/Node/双浏览器、独立应急根、历史备份格式和 PID 出生身份保护并非各自单独可删的债。低端 Android 和 16 KiB 未测不能反过来要求删除它们。

本页只给 A 组剩余项及两维的证据边界。若主代理验证或修复了第 1 项，应按同一锚点重评生命周期；若选择不实施，必须把负组信号的剩余风险明确留在交付限制中。其它维度与 D06 由各自责任人和最终设备证据裁决。
