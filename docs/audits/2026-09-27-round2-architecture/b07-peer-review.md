# B07s/B07v 最后有限只读交叉复核

范围是已记录的 `r2-b07-adb-sensitive-dispatch.md` 与 `r2-b07-vscreen-revocation.md` 两包。只读看生产发送链与对应夹具源码；未修改源码、运行 Gradle 或设备。**未发现会令这两包既定修复失效的具体回归**，但虚拟屏普通 HTTP 请求的极窄本地检查/发送窗口须与一次性 ADB 票据保证分开表述。

**B07s：SMS 预授权撤销后不发送。** `adb-shell.py.run_on_endpoint()` 在实际 `dev.connect()` 成功与总截止检查后进入 `policy.execute()`；READ 计划的 `shell()` 回调在唯一 `dev.shell()` 前调用现有 `request_device_plan(command, use_su)`，要求新计划仍为 `authorization=remembered`，且 version/kind/argv/capability/authorization 与原计划相同（`:232-290`）。短信当前用户与字段由原生 `SmsQuery.forUser()` 编入 `argv`（`HttpShellService.java:665-671`），故用户号/查询变化落入相等检查。`device-shell-policy.py:255-290` 对普通 READ 只调用一次 shell；`connect_with_retry()` 只捕获连接失败 `ConnectFail` 换地址，复核产生的 `policy.Blocked` 直接外抛，不会转到后续地址重放。复核网络不可用则在发送前失败，代码可能给出保守的结果未知文案，但不会假报已发送或实际发包。原始计划到真实 `dev.shell()` 之间仍有一次极短在途窗口；发送后的动作无法追溯撤回。四项改前失败/改后通过、ADB flow 31 项与受管虚拟屏桥 3 项通过为主代理记录；本次没有重跑或把假连接结果扩成真实短信设备验收。

内部 ADB wrapper 修订为 19：`adb-shell.py` header 与 `AdbBridge.SCRIPT_VERSION` 同值，`tools/test-adb-flow.py` 检查 setup/生成 wrapper/pair 与 Java 标记一致。它是受管脚本缓存失效版本，不改 APK/DSH/Ubuntu 产品版本。

**B07v：一次性启动票据在撤销返回时失效。** `VirtualScreenManager.revoke()` 在调用线程用 `AtomicLong.getAndIncrement()` 推进 epoch，并同步 `ADB_LAUNCH.cancel(previous)`，随后才把网络/界面关闭排到 WORKER（`:138-156`）。`adbLaunchTicketFor()`、授权计划与 `commitAdbLaunch()` 均以当前原子 epoch 调用同一 `OneShotLaunchAuthority`；旧票据在撤销返回后不能继续 commit（`:158-173`）。启动完成、心跳、失败收尾检查捕获 epoch；排队的旧撤销清理只在其捕获的 revokedEpoch 仍是当前代次时运行，不清后来代次。闩锁 JUnit 使用真实 manager/authority/worker 而无通道或特权命令，已写但本次未运行；不能写成通过。

边界：普通虚拟屏 HTTP 路径 `request()` 先判断 `activeEpoch == LIFECYCLE_EPOCH.get()`，再调用 `requestAt()`；`revoke()` 不等 `LOCK`，可在判断和实际连接之间推进 epoch 并返回。因此“撤销返回之后绝无任何先前已通过本地检查但尚未发出的 HTTP 请求开始发送”不是当前代码的线性化保证。受控 B07v 目标是 **ADB 启动票据** 撤销后不能 commit，这一目标不受该窗口影响；已发出/刚跨本地请求边界的远端动作继续由既有超时与服务端身份收敛。若最终文案扩大到所有普通虚拟屏操作即时拒绝，应另定义并验证该网络边界，不能拿票据测试替代。
