# 第二轮 B 组审计：数据、插件、文件和设备授权

基线为 2026-09-27 的当前工作树；先读 README、AGENTS 和上一轮 `final-assessment`、`findings-before-fixes`、`fixes`。本页是**修复前初审**。只写审计文件与 `app/build/round2-audit/b/` 隔离夹具；未改产品源码，未运行 Gradle、操作手机或在正式数据中故障注入。上一轮 SEC-01—03、DATA-01—05、PLUGIN-01—04 的修复不重复报。

## R2-B01 · P2 · 已证实缺陷：插件卸载没有持久事务

**证据。** `app/src/main/assets/plugin-manager.py:535-588` 的 `cmd_delete` 在 `builtin.operation_lock` 内，把源码、实体链接、禁用标记和加载历史逐个 `os.replace` 到 `TemporaryDirectory(prefix=".plugin-delete-")`，然后依次改 `sources.json` 和 manifest。`except Exception` 只处理本进程捕获到的异常；`plugin-transactions.py:67-76` 的持久 workspace 仅供安装链，卸载没有 `plan.json`/终态标记。`PluginInstallJournals.java:11-21` 的普通门禁只读 `plugin-install-operations`，不识别 `.plugin-delete-*`。因此中断后可以出现“manifest 仍声明插件、源码已移入孤儿临时目录”的状态；下一次正常启动没有固定恢复入口。若异常回滚中的 `os.replace` 再失败，退出 `TemporaryDirectory` 时还会清掉被暂存的旧原件。

**隔离复现。** `app/build/round2-audit/b/repro_delete.py` 从实际 `plugin-manager.py` AST 提取并运行 `cmd_delete`，用合成 home 在第一次 `os.replace` 后终止子进程。夹具结果：`plugin-src/demo` 消失，原 `sentinel` 留在 `.plugin-delete-*/0/sentinel`，`manifest.json` 仍声明 `demo`；不存在安装事务日志。只验证第一移动窗口，未声称每个卸载阶段都已动态复现。正式数据未触碰。

**影响与根因。** 用户重启后可能遇到缺源码的插件、启动加载失败或卸载状态不一致；人工清理临时目录还可能误删唯一旧版。根因是卸载未复用插件领域的可恢复日志契约，而暂存原件的目录生命周期短于跨进程恢复需求。

**重构与关闭标准。** 为卸载单独增加同域持久 plan，固定四个目标与切换前摘要、候选状态和最终 manifest/sources 摘要；在维护屏障内逐步切换并 fsync 关键标记。普通启动/插件操作前只扫描活跃卸载日志，按记录核对当前字节后完成或回切；后来被修改、未知及失败候选原位保留。无需建立跨所有数据域的大事务框架。隔离夹具在每个 rename、两次配置发布与提交标记处杀进程，重启恢复须得到“原插件完整”或“卸载完整”，且旧源码、历史与异常现场可追溯。

## R2-B02 · P2 · 结构债：普通门禁仍线性扫描完成历史

**证据。** `BackupManager.java:116-123` 在启动、终端、插件、安装、应急修复及自动备份入口串行调用七类待恢复探测（例如 `HarnessController.java:256,316`、`TerminalFragment.java:187,192`、`AutomaticBackups.java:48`）。`HostPendingTransactions.java:11-20` 调 `HostOperationArchive.roots` 枚举 active **及 completed**，逐条 stat 三个标记；`ConfigurationSnapshots.java:222-233` 再独立枚举这两根并读标记。`ManagedRuntimeTransaction.java:96-105`、`EnvironmentRebuildTransaction.java:101-105`、`PluginInstallJournals.java:11-21` 也按历史目录逐条探测。`NativeBackupJobs.java:37-51` 的构造器同步调用 `loadLastState()`，同样枚举两根并解析每份历史的 `operation.json`；`verifiedCopies()`（60-68）在用户显式查看副本时逐条检查是合理成本，不应一并改成不验证。`BackupTask.java:75-85` 仅给显示轮询 1 秒缓存，写入口仍全扫。上轮修复消除了总数容量悬崖，但“历史随使用增长、每次门禁成本随之增长”仍存在。

**影响与根因。** 保留手动、未知及异常记录是正确要求；这些记录增加时，启动与命令入口的文件系统往返和标记读取随历史数量增长，慢闪存上可能引起显著延迟。当前是代码复杂度/成本证据，**没有真机耗时曲线，不能报成已观察到的卡顿**。另外各 `blocked()` 将具体 `IOException` 折为 `true`（如 `HostPendingTransactions.java:26`、`PluginInstallJournals.java:27-29`），执行层只见“待恢复”，难区分活跃事务、历史损坏和 I/O 错误；故障定位跨多个所有者。

**重构与关闭标准。** 各领域各自维护小型 active 索引/目录，终态完成时先验证再原子移入 retained 历史。已有 completed 需先做一次完整只读核验并生成摘要/目录身份证明；终态无法证明、重复、未知、损坏或后来修改的记录原位保留并进入异常索引，不能被迁移脚本“清理”成安全状态。普通门禁只读 active、异常索引和 retained 根的目录身份；若身份变化、索引缺失或证明不可信，保守阻塞并触发明确的深度审查，不凭缓存放行写操作。显式历史审阅/副本验证仍逐条检查 retained。`NativeBackupJobs` 的最近任务状态可在终态发布时写小型 last-state 指针，并在指针不可信时明确显示未知，避免 UI 构造器全扫。统一门禁返回领域、事务 ID、状态和错误码的只读结果，仍由各领域负责恢复，避免大一统事务实现。隔离文件系统放入 0/65/1,000 条已完成记录、活跃未提交、损坏/重复及修改过的历史，断言普通扫描次数不随已完成数量增长，异常不被静默吞掉，原件不删除；另在非调试设备量测入口延迟。

## R2-B03 · P2 · 已证实代码缺陷：敏感界面确认返回后未复核屏幕授权代次

**证据。** `HttpShellService.java:782-800` 在弹窗前记录 `generation` 与 `uiGrant.revision()`；非敏感界面在确认返回后核对停止状态、Web generation 和 `uiGrant.accept`（796-798），敏感界面却直接 `return ok`（800）。`revokeScreenGrant()` 在 757 行递增 revision，`hasScreenGrant()` 在 758-760 行核对当前 Web；`appVscreen()` 的授权通过后于 875-877 行执行动作。敏感弹窗等待期间 Web 停止、无障碍断开或用户撤销，若旧弹窗随后返回“同意”，该分支不会重新核对当前代次。Root/Shizuku/ADB 虚拟屏由 `VirtualScreenManager` 选通道，本轮未逐通道动态验证。

**影响与根因。** 一次旧确认可能批准撤销后的屏幕动作，破坏“停止/断连/撤销后失效”契约。此处为明确控制流缺口；真实设备时序尚未复现，不声称已观察到越权点击。根因是两类界面确认分支拥有不同的后置条件。

**重构与关闭标准。** 让所有正向确认共用一次后置核验：当前 Web generation、未停止、grant revision 与确认时一致、无障碍/虚拟屏会话仍有效；敏感应用仍逐次确认，不能用预授权替代。通过可控延迟确认夹具覆盖确认期间停止、撤销、无障碍断连、代次更新、正常同意；再对 Root/Shizuku/ADB 逐通道实测撤销后无新动作。不要在正式用户数据上注入设备故障。

## 未定性风险与待复现

- **DocumentsProvider 父目录替换竞态。** `DocumentPaths.java:65-91` 逐组件解析链接并返回 `File`；`DshaDocumentsProvider.java:180-218` 的创建、重命名、删除在后续 `mkdir/createNewFile/renameTo/delete` 时重新按路径访问。`openDocument` 已有 fd 位置核验且截断发生在核验后（155-172），不能把它与其它操作混为一谈。Provider 的 `synchronized` 只协调本实例方法，无法阻止 guest/其它文件写入者替换父目录。当前 Windows 环境未取得 Android `openat`/并发路径切换的有效隔离复现，**因此不登记已证实越界缺陷**。下一步在隔离 Android 私有测试树对父路径在校验与 syscall 之间反复替换，分别验证创建、改名、删除是否越过 base、误删其它私有根或仅安全失败；记录实际 inode/fd。若可复现，再立 R2-B 编号并按操作分别修复，优先目录 fd 相对路径与 `O_NOFOLLOW`/身份复核，保持旧 document ID 和 guest 链接语义。
- **ADB 短信撤销窗口。** `HttpShellService.java:655-672` 签发 plan 时检查 SMS 预授权；`adb-shell.py:466-541` 在取得 plan 后还会准备密钥、连线并发送。用户这段时间关闭预授权，旧 plan 的执行前没有重新检查。它可能属于已授权在途请求，是否违反“关闭后阻止新查询”的产品边界需按用户可见时序裁决。Root 在 `RootShell.java:31-37` 执行前另查授权；Shizuku 不执行敏感短信。可用可暂停 ADB 连接的隔离夹具先判断实际发送时点，再决定是否需要一次性授权票据/发送前提交。
- **虚拟屏撤销排队。** `VirtualScreenManager.java:130-140` 的 `revoke()` 将停止排入单线程 worker；真正清 token/代次在 `stopLocked()`。`HttpShellService` 的 UI grant 同步撤销可挡桥的新授权，但 native/UI 直调与已在途请求需实机裁决。修复前不得把异步排队直接等同已发生越权。

## 本组核查边界

宿主 v5 备份、自动计划、插件恢复图、`RecoveryRepairBroker` 与设备命令策略做了入口/风险模式阅读；本轮未运行两 flavor JUnit、Release Lint、设备覆盖或外部模型请求。上一轮的“完成”仅作为待复核前提，不冒充本轮动态通过。coverage 文件列出实际文件与检查层级。
