# C 组只读交叉复核：共享维护 pending probe

对照当前 `HostMaintenancePending.java`、`EnvironmentMaintenance.ensureSingleRecovery()`、`HostMaintenancePendingTest.java` 与 gate8 软件回执 `ede1e5ac-d931-4f21-b58e-66156ec29608/source-changes.zip` 内原源码。未修改产品/Gradle/设备，也未运行新门禁。**未发现共享 probe 的具体语义回退。**

普通 `inspect()` 仍先检查 bounded guest，再按旧顺序检查七域：legacy-maintenance、legacy-runtime、managed-runtime、startup-configuration、plugin-install、environment-rebuild、host-data；遇第一项 pending 或 IOException 即返回 blocked/unknown，不继续探测后续域（`HostMaintenancePending.java:37-75`）。Startup 配置探针的新构造与旧 `StartupRepairs.snapshots(context)` 使用同一 canonical files、AndroidBackupFileSystem、UserDataLayout.current()；其余域调用的原 pending 接口未换业务语义。

gate8 的 `ensureSingleRecovery()` 原算式为 host pending 数 + managed runtime pending 数 + 五个布尔域（startup、legacy runtime、environment、plugin、legacy maintenance）；新 `moreThanOneRecoverable()` 对 host 和 managed `countEach=true`，对其它五域包括插件的任意多候选 `countEach=false`，计数超过一即返回 true，`ensureSingleRecovery()` 仍抛 `MULTIPLE_TRANSACTIONS`（当前 `EnvironmentMaintenance.java:267-270`）。新顺序不同于旧算式，但求和结果不变；有两个已确认候选时提前停止，不再读取后续域。若后续域本身不可读，旧算式可能先报该域 IOException，新实现报 `MULTIPLE_TRANSACTIONS`；**两者都拒绝恢复写入**，这是一处有意的诊断优先级变化，不是放行。首个探针不可读仍向上传 IOException；普通 `inspect()` 将其化为 blocked/unknown。

bounded guest 没被误计入七域：它是独立进程停止屏障。`MaintenanceGate.exclusive()` 在终端/Web 停止、RuntimeTasks 围栏之后、领域恢复之前调用 `confirmOtherProcesses()`；Android 端转给 `BoundedGuestSessions.reapExited()`，身份或整会话未确认退出即抛错，阻止实际恢复（`MaintenanceGate.java:53-75`，`MaintenanceCoordinator.java:76-82`，`BoundedGuestSessions.java:91-119`）。普通启动 `inspect()` 仍先把未关闭 bounded 记录置为 blocked；本进程中正在运行且身份明确的记录由 RuntimeTasks/整会话检查挡住维护，不把历史记录数算作第二笔数据事务。

用源码片段归一化空白比较 gate8 与当前 `EnvironmentMaintenance.recover()` 主体，二者相同；替换的是入口的单次计数判定，不是领域恢复顺序、回切行为或候选选择。注入式 `HostMaintenancePendingTest` 锁定七域名称/权重、普通门禁首项短路、插件计一笔而 host/managed 逐笔、计数大于一早停及不可读 fail-closed。测试源码已审，最终动态通过仍以主代理本轮完整门禁为准。
