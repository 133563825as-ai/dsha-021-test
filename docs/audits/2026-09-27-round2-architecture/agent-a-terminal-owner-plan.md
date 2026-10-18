# A01 终端会话 owner 可行性核对（只读）

`MaintenanceCoordinator.AndroidPorts.stopTerminals:75–77` 直接调用 `PtyTerminalFragment.shutdownAndWait` 和 `TerminalFragment.shutdownShellAndWait`，是目前 core→UI 的真实边。PTY 的底层 `PtySession.pending/closeAllForMaintenance/finishAndWait` 已经在无 View 的类中登记出生身份、持有 RuntimeTasks token，并在 launcher 退出后要求整个 session 为空；UI 静态 `TerminalTabs<PtySession>` 仍持标签 ID、显示编号和当前选择，维护关闭后才从标签表移除。简易终端的 `util.TerminalSession` 也无 View 依赖，`disposeAndWait` 会在失败时留住进程与控制线程；但 `TerminalFragment` 静态 `TerminalTabs<ShellTab>` 的 `ShellTab` 同时持 `TerminalSession`、输出缓冲、草稿/光标，回调经静态 `bound` 弱引用投到 Fragment。两个 Fragment 的用户单标签关闭都按 `tab.id` 查找、beginClose、核验退出后 remove，失败 closeFailed 保留编号；维护关闭目前直接遍历同一张表。

因此**可以重构，但不是一个小接口替换**。仅把 `stopTerminals()` 委托到一个新接口，或在底层另建“活动进程 registry”，会在 UI 标签表、`PtySession.pending` 与新表之间制造三个 owner；一旦关闭失败或旋转，很容易提前释放编号/工作锁，收益不足。仅先迁 PTY 也不能消除简易终端的 core→UI 停止边。

若后续设备验收表明值得做，最小真实包应是：

1. 建一个进程级、无 View 的 `TerminalSessionOwner`，**实际持有当前两张 `TerminalTabs` 实例**，而非复制快照。PTY tab value 继续是 `PtySession`；简易 tab value 改为无 Fragment 引用的 `SimpleTab`，保留原 `TerminalSession`、缓冲、草稿与光标。输出/状态由 owner 记录并向当前页面的可撤销 observer 投递；页面销毁时只解除 observer，不结束会话。
2. 两页的新建、选择、单标签关闭都调用 owner 的同一 ID 操作。owner 使用现有 `TerminalTabs.beginClose/closeFailed/remove`，PTY 仍调用 `PtySession.finishAndWait`，简易终端仍调用 `TerminalSession.disposeAndWait`；失败不 remove，编号占位。维护入口由 owner 的 `closeAllAndConfirm(timeout)` 先执行 `PtySession.closeAllForMaintenance`，再按两张同一标签表逐项核验并移除；`MaintenanceGate` 原有独占 lease 与 RuntimeTasks 围栏顺序不变。
3. 禁止把 `TerminalTabs` 重建成新的静态实例或按显示编号处理回调。永久递增的会话 ID、最小空缺显示编号、旧回调只更新本 ID、草稿/输出跨切页旋转、两个终端相互独立、close 失败仍占位及 guest 出生身份/退出判据均用现有测试加真实非调试 APK 场景回归。维护过程中取消或超时仍抛错并保留环境。

**现阶段建议止步。** gate6 已有 Low 正式候选在 PTY 执行固定 echo 并进入受管更新 READY；这支持当前关闭链在该设备路径可用，Standard 与更多生命周期场景仍需主代理完成。尚未有因 Fragment 静态集合导致的实际漏关/编号错配证据。上述迁移横跨两个 UI、一个底层会话包装及维护入口，风险显著高于删掉一条 import；在当前交付候选冻结阶段不宜为依赖图整洁实施。若后续发现具体关闭失败或维护状态漂移，再以同一 owner 迁移作为一包，而不是临时加第二份注册表。

## 主代理裁决与本次关闭测试（2026-09-28）

主代理裁决 A01 属于已证实可改善的状态所有权债，授权本轮实施上述完整有限纵切面；前段“现阶段建议止步”保留为实施前风险评估，不再是本轮执行决定。实施不扩展设备权限、插件、备份格式或底层进程信号策略。

关闭条件：生产代码中两张 `TerminalTabs` 只由一个无 View owner 实例持有，Fragment 不再有静态 session 列表；简易 `SimpleTab` 的 buffer/draft/cursor 与 `TerminalSession` 同属 owner。用户单标签关闭和维护 `closeAllAndConfirm` 只按同一永久 ID 查找并调用原 `finishAndWait`/`disposeAndWait`，失败始终保留 tab 与显示编号、可重试；PTY 的 `PtySession.pending` 只作为现有低层进程保护，不变成第二张 UI tab registry。owner 的观察者可解除、旧回调不改新 tab，UI 更新在主线程；切页、旋转、语言切换不停止会话。纯 JVM 测试覆盖编号最小空缺复用、关闭失败/竞态占位、同 ID 维护清理、listener detach 与旧代回调；主代理再跑两 flavor 全量、Release Lint、同签正式包的 PTY/简易终端关闭和维护屏障验收。身份读取未知时仍保持原环境。

实施中 B 组独立复核指出主线程 guard 的条件性回归：旧两 Fragment 的维护等待入口在任何终端关闭前检查 `Looper`，新 owner 本身无 Android Looper。`MaintenanceCoordinator.AndroidPorts.stopTerminals` 现于调用 owner 前恢复该拒绝；多标签的逐项 5 秒超时不能意外阻塞 UI 线程。此项须在完整软件门禁中保持。
