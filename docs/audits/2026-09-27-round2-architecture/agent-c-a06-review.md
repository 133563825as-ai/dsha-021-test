# C 组独立只读复核：R2-A06 简易终端出生身份

范围为 `TerminalSession`、`TerminalReady`、`SimpleTerminalBackend`、`TerminalProcessCloser` 和对应纯逻辑测试。未编辑 A 组产品类，未运行 Gradle 或手机。静态调用链中未发现明确的新误杀或提前释放工作锁路径；真机非调试 `/proc` 拒读/身份窗口仍必须由主代理的正式包验收验证。

固定启动命令在新 setsid Bash 进程 `exec` 前用 shell 内建从自身 `/proc/$$/stat` 读取字段22，按本次随机 prefix 输出 `READY:<pid>:<starttime>`；`TerminalReady.parse()` 对 PID、时刻、长度/溢出和尾随内容拒绝。`TerminalSession.receive()` 只接受当前 Run 首次 READY，先登记 `run.group`，再调用生产 `Backend.onReady`；返回前 `run.ready` 仍为 false，不会 `pump()` 用户命令（`TerminalSession.java:154-177,259-291`）。生产后端对报告值调用 `TerminalProcessCloser.captureSimpleLeader`，实际读回同一出生时刻、同 UID、`session=group=pid` 和 JNI kernel sessionId，才保存 leader。它不是“解析一个数字即授权组信号”。

停止/取消/维护同走 `TerminalSession.stop → SimpleTerminalBackend.terminate → TerminalProcessCloser.closeSimple`。后端不再调用 `Os.kill(-group, SIGKILL)`；复核已存 leader 的出生身份后，closer 暂停组长、逐项核对当前 session/UID/成员出生身份并回收本次成员，最后结束组长。组长已自然消失时只查本次 session 空，不向可能复用的组号发信号。`read()` 对不完整或拒读的 `/proc` 有限等待并询问 JNI sessionId；仍属同 session 但无法读出生身份时抛错，不把空 stat 当退出。后端只有在 session 已空、launcher 真实退出且流关闭后才清 `reported/sessionLeader`；`TerminalSession.stop()` 只有这之后 `releaseLifetime()`。失败保持 FAILED、原 tab 与 RuntimeTasks lifetime；重试仍核同一次报告身份。伪 READY、不可读、组号复用和后台成员不明均是拒绝路径。

测试源码包括 `TerminalReadyTest` 的格式/溢出/尾随拒绝，`TerminalSessionTest` 的同组号不同出生、暂拒后重试、无 READY 退出仍保锁及正常中止。独立 JVM 和 Android `/proc` 的差别没有被这些测试消除；正式 Low/Standard 非调试包仍需至少一次正常 shell、取消和维护关闭，遇拒读不得称已退出或释放锁。

受管身份输入仅补 `managed-runtime-inputs.json` 的 `util/TerminalReady.java`：原清单已有 `core/SimpleTerminalBackend.java`、`util/TerminalSession.java`，`app/src/main/java/.../runtime` 树覆盖 `TerminalProcessCloser.java`，所以没有盲加整个 util 包。补项后 `test-runtime-descriptor-inputs.py` 报 54 个安装项、101 个资产、53 个 launcher 输入；`test-runtime-input-contract.py` 六项通过。当前受跟踪 `runtime-descriptor.json` 须由主代理按新输入重算、审阅并纳入正式双 flavor 门禁，本组未写它。
