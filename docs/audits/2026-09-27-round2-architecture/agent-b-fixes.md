# 第二轮 B 组封存后修复与新发现

本文件在 `audit-seal.json` 封存后记录实施和新发现，不回写 `agent-b-audit.md`。正式数据没有故障注入；本阶段不运行 Gradle 或操作设备。

## R2-B01 · 插件卸载持久事务

`plugin-manager.py` 的卸载改用 `plugin-transactions.py` 的 format 3 持久计划。四个插件原件及 sources/manifest 的前后摘要在移动前登记；未提交由同域 `PluginInstallJournals` 门禁与原生维护恢复入口回切，已提交日志不回滚后来数据，变更或不可验证的原件留在现场。卸载成功后的旧源码、链接、禁用标记和历史仍保存在事务目录。`tools/test-plugin-transactions.py` 10 项通过，覆盖八个切换/提交中断点、回滚中断、回滚失败和修改过的原件。尚待两版完整 Java/构建门禁与显式保留清单的删除原件展示验收。

## R2-B02 · 历史与活动事务分离（实施中）

宿主备份及配置快照的第一纵切面已实现：旧 completed 首次核验终态、记录 ID 和重复后生成绑定 files 与历史目录身份的证明；普通 pending 此后只读 active，活跃记录仍逐次查标记及与 completed 同 ID 冲突。证明丢失、目录身份变化时重新核验，未知/损坏/未提交原件原位保留并阻塞。`NativeBackupJobs.loadLastState()` 以 last-task 指针和 active 记录替代每次初始化扫描 completed；旧记录首次读取后才建立指针。相关 JUnit 已增加 0/65/1,000 条完成历史的文件系统调用计数/耗时及 active/pending/broken/duplicate 场景，并通过第一次完整 Gradle。

后续三个领域的普通 `pending()` 保持**只读**：仅扫描 active、验证旧 completed 的一次性迁移证明。`ManagedRuntimeTransaction.trimOlder` 完成逐根复核后、兼容回退完成后，运行时域才显式归档终态；基础重建先做 `sealRetired/cleanupRetired`，调用方最后访问 work 后才归档；插件 Python 在同一个 `operation_lock` 的 workspace 退出时归档，Java 仅在独占维护中确认 guest 恢复进程成功退出后归档。`plugin-lifecycle.py` 原先 workspace 先于锁，已调换顺序；`register_plugin` 也取得同一锁。旧终态尚未归档时保留在 active，下一次安全维护/插件操作再迁移，不能为了普通门禁首轮常数成本移动正在使用的日志。此收紧来自提交 marker 后到 caller 最后访问之间的并发窗口；Java/Python 夹具增加了 commit 后暂停、并发 pending、继续 cleanup/恢复检查。

第一次主代理完整 Gradle 结果：Standard/Low 各 742 项，0 失败、0 错误、1 项既有跳过；host/config 0/65/1,000 条完成历史的稳定态五次扫描分别恒定 55/60 次文件系统调用，约 3–4 ms。详见 `first-unit-results.json`。后续三域和分页代码尚待下一轮完整 Gradle，不把此结果套用到新改动。

第二包首次完整门禁出现两条受管运行时 JUnit 失败：一条沿用“完成日志仍在 active 根”的旧断言，另一条揭示归档只看终态时会移动后来被加文件/改映射的原件。已将新日志映射格式升为 v2，在候选准备结束、切换开始前写 `archive-seal.json` 锁定 mapping 与 plan；归档和自动健康计数须重核 seal、固定顶层成员及前代逐根字节。修改过或有额外文件的日志留在 active，不能计健康或自动删除。旧 v1 不补造可信 seal、不自动搬迁，但保持按 ID 显式读取，并继续严格核验 plan、前代树、健康回执、数据格式兼容和真实试运行。新旧正负夹具已加，待目标测试复验；首次失败不能当成已关闭。

## R2-B04 · P2 · 封存后新增：保留清单的 256 条上限与应急代次历史耦合

**代码证据。** `RetainedCatalogue.java:26-36` 显式扫描环境、运行时和插件事务目录；`scan()` 在 40 行对所有非 BACKUP 类别直接以目录成员数 `>256` 抛 `RETAINED_ENTRY_LIMIT`。这些事务的成功历史按保护要求长期保留，成员数可正常增长超过 256。`RecoveryRepairBroker.java:158-170,223-234` 的代次和维护预览也逐条读取 `host-runtime-operations`、`host-environment-operations` 等目录，并在 256 条后抛 `REPAIR_JOURNAL_LIMIT`；当前仅备份/配置的 completed 子目录被从计数中排除。由此，用户正常积累成功记录后，显式保留清单可能整体打不开，应急候选代次/预览可能被历史总数阻塞。

**性质与边界。** 这是源码可证的容量/诊断缺陷；没有本轮真机或 257 次实际维护复现。它独立于上轮已修的“第 65/129 次新作业无法开始”。现有 `RetainedCatalogue` 非备份显示限制不应通过删历史或默默截断来规避。

**修复和关闭标准。** 各域先把经过终态验证的日志移到本域 inert completed，保留旧路径查找兼容；`RetainedCatalogue` 显式枚举 active 和 completed，提供分页或按类型读取并返回总数/下一页游标，异常原件仍可见。`RecoveryRepairBroker` 的数据代次只跟踪活动事务及固定异常摘要，completed 仅在用户显式历史预览时按页读取；代次确认仍在停止屏障内复核源文件。用 0/65/257/1,000 条合成历史、未提交/重复/损坏/目录修改和跨页读取验证：普通门禁耗时不随完成数量增长，用户能找回任一原件，显示不会无提示丢项，应急确认不被纯历史数永久阻断。

实施现状：`RetainedCatalogue.page(index, size)` 显式遍历历史，以容量 `offset + size` 的有界堆保留最新记录，再按原先“新记录优先”的 modified 时间排序并以 key 稳定断平；内存复杂度为 **O(offset + size)**，不是与页号无关的常数。它返回总数和 `hasNext()`；`RetainedDataActivity` 每次只创建 50 条卡片并显示当前范围/总数，跨页保留选择。`resolve(key)` 改为只保留命中的一项，操作前仍重新扫描/验证实物。运行时、环境、插件的 active/completed 均列入显式清单，B01 卸载暂存的 `delete-*` 也可见；`RecoveryRepairBroker` 普通代次忽略 inert completed，对尚未安全归档的旧 terminal 逐项确认后跳过总量计数。257 条插件历史跨六页、分页顺序与完整清单一致、极端页号不溢出的测试已加，待完整 Gradle 执行。

## 后续域迁移约束（首包验证后实施）

| 域 | 活动与完成路径 | 必须适配的显式查找 |
|---|---|---|
| 受管运行时 | `host-runtime-operations/<id>` → 同根 `completed/<id>`；只移动 `finalized`/`rolled-back` 已证终态，未关闭或身份不明的现场留在 active | `ManagedRuntimeTransaction.open` 按 ID 查两处且拒绝重复；`rollbackOptions`、`prepareRollback`、`trimOlder` 对完成历史逐项实物复核，不从迁移证明认定可回退/可清理；`RetainedCatalogue` 两处可见 |
| 基础重建 | `host-environment-operations/<id>` → 同根 `completed/<id>`；在重建调用方完成 `sealRetired/cleanupRetired` 后的下一次安全扫描再归档 | `EnvironmentRebuildTransaction.open`、`cleanupCompleted` 查两处并保持原目录对象在提交期间有效；`RetainedCatalogue` 两处可见 |
| 插件 | `<DSH_HOME>/plugin-install-operations/<id>` → 同根 `completed/<id>`；安装/卸载持久日志的 `committed`/`rolled-back` 均经标记和固定 plan 核验后移入 | Python `Transactions.pending/workspace/recover_all` 仅扫 active，显式日志查看可从 completed 查原件；`PluginInstallJournals` 原生门禁同步，`RetainedCatalogue` 展示包括 B01 的 `delete-source/link/marker/history` |

以上移动只改变日志目录，不删除数据或把完成历史当成当前健康证明。旧已完成记录先一次完整核验；同 ID 两处出现、未知或损坏记录必须留下且明确阻断新写入。`RecoveryRepairBroker` 的 completed 判据需与上述三域同时变更，不能产生一轮窗口中 256 条历史重新阻断应急确认。

## 本机证明与 v5 用户归档的只读契约核对

`host-backup-completed-v1.json`、`host-runtime-completed-v1.json`、`host-environment-completed-v1.json`、`startup-config-completed-v1.json` 和 `host-backup-last-task-v1.json` 均在 `filesDir` 顶层，`NativeDataLocations.java:45,63-70` 的正常 v5 来源只遍历所选 `.dsh` 数据根，故这些宿主指针不会随正常用户归档进入。即使外来档案伪造逻辑根，`NativeRestoreTargets.java:49-81` 也只接受固定逻辑类型，不提供这些 filesDir 顶层目标的直接恢复映射。

插件 `.completed-proof-v1.json` 位于 `.dsh/plugin-install-operations`。当前 `DataRootPolicy.machine()` **未列** `plugin-install-operations`，所以 `NativeDataLocations` 的普通 v5 应用/设置归档可以把这棵本机日志及证明作为 `dsh-child` 读入。这是需要留意的来源范围；但 `NativeRestoreTargets.java:63-70` 对非直接用户数据一律送到本次 `plugin-imports/<任务>/declarations/plugin-install-operations` 隔离区，`PluginRestoreGraph` 只重建审阅后的包/链接，不把该证明复制回活动 `.dsh`。`UserDataLayout.java:62-67` 同时将活动 `plugin-install-operations` 标为私有 DocumentsProvider 路径。因此本轮未发现**用户 v5 导入后证明直接放行活动事务**的路径；不能把外来档案里的证明当本机授权，显式从隔离区审阅/导出时仍须按真实原件重新核验。后续可单独把该本机日志从普通用户归档范围排除，减少无用副本；这不是本轮已证实的直接授权缺陷。

## R2-B05 · 有界的保留清单批量读取与游标分页（实施前证据）

`RetainedDataActivity.inspect()` 对每个选中 key 调 `RetainedCatalogue.resolve()`；当前 `resolve()` 每次重新 `collect()` 全部历史，且随后 `sources(entry)` 再次 `resolve()`。K 条多选、N 条历史时只读元数据扫描可接近 O(N×K)，即使每个实际内容仍应逐项验证。`RetainedCatalogue.page(index,size)` 用容量 `offset+size` 的堆保留最新条目，深页内存随页号增长，当前 `RetainedDataActivity.Model.entries` 本身仅保留一页且 View 数有界。这里是代码复杂度证据，尚无真机迟滞结论。

实施约束：提供 `resolveAll(Set<String>)` 一次显式扫描，逐 key 报告 missing、duplicate、changed/unreadable；UI 保存选择时的展示身份并在检查前与本次结果比较。批量检查可消费这一轮新鲜结果，仍逐项读取/核验源字节；真实导出/恢复始终走自己的按 key 复核，批量结果不作授权缓存。分页改为带 `modified + key` 边界与本轮目录可见状态指纹的展示游标：每页只保留 O(pageSize) 条最新候选，扫描时返回总数；若历史在翻页间变化，明确要求刷新首页，不静默跳过或重复。保持旧“新记录优先”和上下页。用 1,000 条历史及多选计数验证 O(N+K) 元数据扫描而非 O(N×K)，并覆盖目录变化、同 key 双份、深页、极端游标、返回上一页及实际操作重新核验。

实现边界补记：候选堆与保留的 `Entry` 为 O(pageSize)，但 `BackupFileSystem.list()` 每根目录仍返回全部名字，因此一次显式页面扫描的峰值内存为 **O(最大单根目录名字数 + pageSize)**，不能称整条路径严格 O(pageSize)。Android/JVM 实现均已排序且返回可迭代列表；`RetainedCatalogue.scan()` 去掉了第二份全量 `ArrayList` 和重复排序，直接迭代并跳过保留名称。本轮不扩写文件系统接口。重复 logical key 的两份物理原件仍分别展示，标明活动/完成位置和冲突状态；勾选及动作在冲突状态禁用，显式 `resolveAll` 继续拒绝重复，绝不删或隐藏其中之一。列表加载在主线程按请求序号同步提交页、游标和 ViewModel 后才解除忙状态，避免旧页按钮抢在新页渲染前推进页码。

实现状态：`resolveAll` 一次遍历将选中 key 分为新鲜命中、缺失、同 ID 已变化、不可读和重复；UI 另比较选择时的展示身份，随后逐项读取/核验实际内容。`inspectionSources` 只消费本次批量结果；正式导出/恢复仍调用独立 `resolve`/预检，不使用它作授权缓存。游标记录上一页最后一项的 `modified + key + 物理目录` 和整轮可见清单指纹；若下一页扫描的边界或指纹变化，明确重置首页。每页候选堆最多 `pageSize` 条，仍需全量遍历目录名字以给出总数。active/completed 重复 ID 各输出一条 `DUPLICATE` 物理记录并禁动作，即使存在 `verified.json` 也不调用会因重复而抛错的备份副本检查；旧选择在 UI 冲突行出现时移除。1,000 条/多选文件系统调用、257 条跨页、游标变化及重复备份的测试已加，待主代理目标和完整门禁执行；本页不将其写作已通过。

第一次 R2-B05 定向 Gradle 实际执行 Standard 全量 774 项，只有 `thousandHistoryBatchLookupScansOnceAndReportsPerKeyProblems` 在旧源码快照中失败：active/completed 重复 ID 被缩成 `record` 后，旧选中 `delete-source` key 返回 `CHANGED`。其后代码已用 `duplicateOwners` 按 kind:id: 把旧 part 的结果明确改为 `RETAINED_SOURCE_DUPLICATE`，并补备份重复记录夹具；断言未放松。Low 与最新源码的全量复验尚未执行，本次失败保留在记录中。

## 最后一项限域共享探测（实施前证据与方案）

`HostMaintenancePending.inspect()` 与 `EnvironmentMaintenance.ensureSingleRecovery()` 目前各自列出相同七个可恢复事务域。前者按顺序返回首个 domain/ID 或 unreadable；后者另写一份计数表达式并在总数 >1 时抛 `MULTIPLE_TRANSACTIONS`。`bounded-guest` 只在前者作为启动/停止屏障，不能算成可恢复事务。双清单的风险是将来新增域时两处漏改，或多域同时挂起时普通提示与恢复判据漂移；目前未证实由此发生写入绕过。

拟在 `HostMaintenancePending` 内建立**一条固定顺序、只读、仅供门禁/恢复计数的七域 probe 序列**：普通 `inspect` 在 `bounded-guest` 先行检查后取第一个；恢复 `moreThanOneRecoverable` 只累积到 2 即停止。保持旧权重：host-data 与 managed-runtime 按各自待恢复记录数计；plugin-install 整个域无论候选数按 1；legacy-maintenance、legacy-runtime、startup-configuration、environment-rebuild 各按 0/1。任一已经探测的域不可读仍抛 IOException 给恢复入口，普通入口仍返回带脱敏错误的 blocked；早停已足以判多事务时返回 true，仍由调用方抛原 `MULTIPLE_TRANSACTIONS`。不把 `bounded-guest` 纳入可恢复计数，维护独占的进程确认/回收不变。`EnvironmentMaintenance.ensureSingleRecovery` 只委托这一窄判据，不触碰其后实际 recover 执行顺序。隔离测试用可注入的只读 probe 验证首项、host/managed 多记录、plugin 多候选仍计一、异常阻断和有界早停；不造通用事务框架、不更改原件。

实施状态：`HostMaintenancePending` 的同一七域固定序列现分别供普通 `inspect` 与恢复入口的 `moreThanOneRecoverable` 使用；后者对逐记录域至多加 2，再在跨域累计 >1 时短路。`EnvironmentMaintenance.ensureSingleRecovery` 只换成该只读判据，仍抛 `MULTIPLE_TRANSACTIONS`，实际恢复执行器与顺序未变。这里的“有界”仅指**计数与跨域探测短路**；各 `domain.pending()` 仍按原有文件系统规则完整检查本域活动现场，不能据此宣称底层目录枚举 O(1)。`bounded-guest` 仍先作为普通启动/停止屏障检查，未纳入可恢复事务数。`HostMaintenancePendingTest` 已加七域权重、首阻断、插件多候选、host/managed 多条、不可读与早停；待 C 等价审查及主代理合并测试，尚不记为动态通过。
