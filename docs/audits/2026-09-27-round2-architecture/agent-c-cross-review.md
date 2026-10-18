# C 组只读交叉审查：B02 完成历史归档

审查当前工作树的 `HostOperationArchive`、`HostPendingTransactions`、`ConfigurationSnapshots`、`NativeBackupJobs` 最后任务指针及 `BackupFileSystem.atomic`。未编辑这些产品文件，未运行 Gradle、设备或测试 APK。本次结论：**未发现阻断 B02 集成的代码证据**；下列边界应保留在最终验收说明中。

`HostOperationArchive.verifyCompleted()` 首次逐条核对 completed UUID、终态与活动目录重复，然后以 files/history 目录身份写 proof；相同目录身份的普通启动不再遍历完整历史（`HostOperationArchive.java:19-44`）。`HostPendingTransactions.pending()` 仍逐项扫描 active，并在其与 completed 存在同 ID 时拒绝（`HostPendingTransactions.java:11-24`）。`locate()` 在按 ID 显式读取时再次拒绝 active/completed 双份（`HostOperationArchive.java:73-88`）；`activateReviewedProfile()` 只把仍为完整 preview、无切换或终态标记的历史项移回活动根，并通过 `reserve()` 先处理活动容量（同文件 `:133-149`）。这些路径没有把 completed 误作新待恢复事务。

旧作业迁移前，`archiveIfTerminal()` 对已确认终态的恢复明文调用 `RestoreStaging.clearSensitivePlaintext`，对已回滚配置明文调用专用清理；明文仍在时 `terminal()` 拒绝归档（同文件 `:90-110,151-211`）。移动后复核目录 inode、主日志摘要、加密产物 inode/大小，并核对活动源已消失；未知、忙碌或损坏记录留在 active，不能被容量整理静默删除。`ConfigurationSnapshots` 的 completed 迁移独立验证 `finalized` 与 `rolled-back` 互斥、plan 版本/ID、active 重名；移动前后复核目录身份和标记/plan 摘要（`ConfigurationSnapshots.java:31-82`）。它的 `pendingNative()` 继续扫描 active（`:253-263`）。

`NativeBackupJobs.update()` 在首次写作业 `operation.json` 后写 `host-backup-last-task-v1.json` 指针（`NativeBackupJobs.java:175-185`）。重启时指针通过 `locate()` 在 active/completed 两边解析，再扫描全部 active 以覆盖写作业后、写指针前的中断；没有指针的旧数据才扫描 completed 建新指针（`:44-87`）。若指针已损坏或指向缺失日志，显示 `FAILED_RETAINED/OPERATION_RECORD_UNREADABLE`，不猜测另一份任务；原件仍在。显式 `preview`、`VerifiedBackupCopy.inspect` 继续按 ID 找真实文件，不靠指针推断加密归档健康。

`BackupFileSystem.atomic()` 在 `target→.previous` 后中断时复制并恢复最后完整记录，发布后 `.previous` 未清时移入唯一 retained 名；它不会无证删除未知旧字节（`BackupFileSystem.java:58-112`）。`HostOperationArchive` 和配置历史的 proof 用这一原子写入口，意外 `.previous` 留存不改变活动事务的逐项扫描。

保留边界：completed proof 仅证明**首次迁移时**的历史终态和目录身份，不是每次启动对所有历史正文重新哈希。目录 inode 未变而子记录后来被外部改写时，普通启动不会重新遍历；历史作为惰性只读数据保留，显式打开加密副本会重新验其字节。这个设计与固定时间启动目标一致，但不得表述为历史内容一直未被修改。原始故障夹具源码覆盖了 65/129 次历史、旧恢复明文迁移、双份和中断移动、配置历史首扫/后续定量扫描；本次交叉审查未独立运行这些 JVM 测试，因此动态结果仍以主代理整轮门禁为准。
