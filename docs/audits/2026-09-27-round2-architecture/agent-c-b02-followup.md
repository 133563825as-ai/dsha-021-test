# C 组只读复核：B02 第二包

审查当前 `ManagedRuntimeTransaction`、`EnvironmentRebuildTransaction`、`PluginInstallJournals`、`plugin-transactions.py`、`EnvironmentMaintenance`、`RetainedCatalogue`/`RetainedDataActivity`、`RecoveryRepairBroker` 和 `RecoveryRepairPlan`。未编辑这些产品文件，未运行 Gradle 或设备。**未发现本包新的具体阻断。**

运行时与环境历史：普通 `pending()` 只核对 completed proof、遍历 active 和识别未完成切换，不调用 `archiveCompleted()`（`ManagedRuntimeTransaction.java:102-108`、`EnvironmentRebuildTransaction.java:108-115`）。后者分别在 `EnvironmentMaintenance.rollbackRuntime()` 已提交且仍持有维护 owner 后、受管更新 `trimOlder()` 的末尾、基础重建提交与旧树清理/保留判定之后调用（`EnvironmentMaintenance.java:21-34,52-69,108-121`）。`trimOlder()` 先对仍符合摘要的旧受管树作保留数量判定，再归档日志（`ManagedRuntimeTransaction.java:233-249`），不会让搬走的目录使本轮清理失去路径。`open(id)` 与 `directory()` 均能在 active 或 completed 中定位，双份则拒绝（`ManagedRuntimeTransaction.java:47-57`、`EnvironmentRebuildTransaction.java:21-29`）；重建失败信息使用可重定位的 `transaction.directory()`。

插件历史：原生 `PluginInstallJournals.pending()` 检查 completed proof 与活动 UUID，但不移动目录；实际归档由明确维护恢复入口在 guest 命令完成后调用，或由 `plugin-transactions.py` 的 `workspace()` 进入/退出在 `operation_lock` 内调用（`PluginInstallJournals.java:12-28,54-94`；`plugin-transactions.py:108-123,415-419`；`plugin-manager.py:341,541-564`）。Python 终态移动保留目录并 `fsync` 两侧父目录，未完成计划继续可见。按 ID 的插件原件由 `RetainedCatalogue` 显式扫描 active 与 completed，操作时再按 key 重新定位源，未见移动后丢失路径。

保留清单：`RetainedCatalogue.page(index,50)` 每次真实扫描指定根，回报实际 `total` 和 `hasNext`，页内只保留最多 50 个 `Entry`；`RetainedDataActivity` 在页码超出更新后总数时回退到最后一页（`RetainedCatalogue.java:23-50`，`RetainedDataActivity.java:46-62`）。`resolve(key)` 再扫描当前目录，`sources()` 先重新 resolve，避免用户在旧页选中的物理路径直接复用。已有 257 个 completed 插件删除原件跨六页的测试源码覆盖总数和最终再定位。边界：每页仍会列举所有相关目录名，且分页按固定根/UUID 扫描顺序，**不承诺**与原 `list()` 的全局修改时间倒序相同；动态新增/归档时跨页顺序可能变化。这个只读目录界面不将分页当作事务快照。

应急原生确认：`RecoveryRepairPlan` 将候选绑定 instanceId、generation、nonce、源 SHA 与数据代次，单次从 PENDING 转 APPLYING 时计入 `NativeRepairLease`。`RecoveryRepairBroker.confirm()` 在取得 lease 前检查本次 broker 开启与写权限，进入 `MaintenanceCoordinator.exclusive` 后再检查写权限、源摘要、数据代次和待恢复门禁（`RecoveryRepairBroker.java:259-284`）。网络路由仅有 diagnostics/targets/read/propose/result，没有 confirm（`:298-305`）；关闭 broker 只使未决提案 EXPIRED，已确认 lease 留到操作完成。`dataGeneration()` 对完成的 host/runtime/environment 记录在 active 和 completed 两边同样跳过，纯归档不会虚假令原生候选过期；未知或未完成目录继续纳入代次（`:151-177`）。这与修复 lease 穿越应急聊天停止的契约一致。

未覆盖边界：本轮只做代码交叉审查，未独立复跑第二包 JVM/Python 测试；最终通过应以主代理本轮门禁日志为准。completed proof 仍只证明首次迁移历史终态及目录身份，显式使用原件时必须按原接口重核真实字节。
