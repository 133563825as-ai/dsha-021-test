# R2-B07v：虚拟屏撤销立即失效

## 修复前证据与可重复窗口

`VirtualScreenManager.revoke()` 只把 `stopLocked()` 投递到单线程 `WORKER` 后立即返回。`stopLocked()` 才递增 `lifecycleEpoch`、取消 `ADB_LAUNCH` 票据。与此同时 `adbLaunchTicketFor → authorizeAdbLaunchPlan → commitAdbLaunch` 直接读取旧 `lifecycleEpoch`；`HttpShellService` 在唯一发送之前调用 commit。因此若 worker 正在执行先前的心跳/网络任务，撤销返回后、旧任务运行前，已登记且未发送的票据仍可 commit。这里是源码可达竞态，不声称已在真机利用；已经向外发出的请求不能追溯撤回。

拟用同包 JUnit 把真实 `WORKER` 前置任务停在闩锁，向真实 manager 的一次性票据登记假命令、取得 ticket 与授权计划；在 worker 仍阻塞时调用真实 `revoke()` 并立即测试 `commitAdbLaunch` 必须为 false。另令新代出现再释放 worker，确认旧 cleanup 不清新代，并覆盖普通停止、重复撤销及过期票据。夹具不得连 ADB、HTTP 或触发特权命令。

## 有限修复边界

撤销线程原子推进生命周期 epoch，并同步取消当代 ADB 票据；排队 worker 只负责可能阻塞的网络关闭/清 UI，执行时须确认仍是其捕获的代次。所有本地请求在发出前比较活动会话代次，已撤销的 token 不再作为授权来源。`start`、异步启动结果、心跳以及失败收尾统一读同一 epoch，不能以排队旧清理覆盖后来新代。撤销不等待现有心跳/网络 I/O；已经发出的远端请求只按既有连接超时和服务端授权收敛。

状态：先封存问题和测试判据，实施/门禁结果续记。

## 实施与当前验证

`VirtualScreenManager` 现以原子 epoch 表示启动/会话代次。`revoke()` 在调用线程立即推进 epoch，并同步取消前代 `OneShotLaunchAuthority` 票据，之后才将可能阻塞的 HTTP 关闭排队。ticket、plan、commit 都用当前 epoch；旧票据在撤销返回后不可再 commit。活动 token 记所属 `activeEpoch`，普通请求与已有会话的再次 create 在发出前拒绝已撤销代次。异步启动完成、心跳与失败收尾也核对 epoch。排队的撤销 cleanup 只在 epoch 仍为它捕获的撤销代次时执行；若后来已进入新代，旧任务不清新 token。

`VirtualScreenManagerRevocationTest` 对真实静态 manager/authority/worker 建立闩锁夹具：worker 被阻时完成假命令的 ticket+plan、调用 `revoke()`、断言旧 commit 立即失败；再模拟新的授权代次并释放 worker，断言旧 cleanup 不清掉新 token、新票据仍可 commit。测试不接通道、HTTP 或特权命令。当前仅完成源码静态检查与 `git diff --check`，没有运行 Gradle（按分工交主代理）；测试运行结果不能预先称通过。已发出的远端动作无法回滚，网络关闭仍受既有超时/worker 调度约束。

主代理已对 gate11 的真实已编译管理器类运行独立 JVM 复现：仅替换 Android 时钟为固定值，持有真实 worker 闩锁，`revoke` 返回后旧 `commitAdbLaunch` 确实仍为 true；无设备命令。输出与该旧 class 摘要保存于 `app/build/round2-audit/b07/previous-result.txt` 和 `previous-class-sha.json`。修后真实 manager JUnit 仍待双版 Gradle 执行。

交叉复核边界：普通 HTTP 的本地代次检查是本次请求的准入点；检查已通过而撤销随后发生的请求属于在途请求，检查与网络发送不是同一原子操作。一次性 ADB commit 同样不能追溯撤回已获准发送的请求。不能将本项“撤销返回后再次 commit 拒绝”扩大为“所有网络字节在撤销返回瞬间停止”。
