# R2-A06：简易终端进程组信号前的本次身份确认

## 修复前证据与范围

`TerminalSession.start` 从受管固定命令 `exec /usr/bin/setsid /bin/bash -c 'printf <随机前缀>READY:%s "$${...}"; exec /bin/bash'` 获取 READY；`TerminalSession.accept`（约269–274）仅解析 Bash `$$` 的数字组号，保存在 `Run.group`。用户关闭/维护关闭与取消重启最后都经 `TerminalSession.stop → SimpleTerminalBackend.terminate`；后者在 `group>1` 时直接 `Os.kill(-group,SIGKILL)`，信号之后才等待/回收 launcher。没有保存该组长出生时刻，也没有信号前比对 UID、session、group 与本次实例。若组长和全组已退出而 launcher 仍活，组号复用可能令旧关闭操作作用于新组。**目前没有真机误杀复现**；gate11 Low 的简易固定 echo、关闭与新建通过，只覆盖正常路径。这是旧简易终端路径的身份判据缺口，不归咎 A01 新 owner。

已有可复用边界：`ProcessIdentity.inSession` 可解析 `/proc/PID/stat` 的启动时刻/session/group；`TerminalProcessCloser` 对 PTY 已用出生身份、内核 sessionId、组长保持存活及未知时阻断；`RuntimeTasks`/`TerminalSession` 在停止失败时保留会话及工作锁。不可把裸 PID/PGID 或端口当停止权限，不能在正式个人数据上制造故障。

## 有限实现与判据

仅扩展简易终端 READY 交握：固定 Bash 在 `exec` 前用内建 `read` 从自身 `/proc/$$/stat` 取得 PID 与 starttime，以随机前缀发出 `READY:<pid>:<starttime>`；host 只接受正整数、有界长度，且仅在本 Run 首次 READY。Backend 的新默认身份登记 hook 让现有测试后端保持源码兼容；生产 `SimpleTerminalBackend` 在准许 READY/用户命令前按同 UID、starttime、`session=group=pid` 读取并复核本次组长，记录不可变身份。停止时调用 runtime 身份协作者；组长仍为该出生实例才允许组信号，必要时先暂停/再次核验以缩小退出复用窗口；组长已消失只核验本次 session 已空，不向可复用号码发信号。`/proc` 拒读、exec 窗口不完整、组号变化、来源 UID 不明或 session 未空均停止操作并让标签/RuntimeTasks 工作锁保持占用，不报告“已关闭”或自动新建。

隔离测试：真实固定 READY 文本解析、重复/旧 Run marker、恶意超长值；fake `/proc`/group 核验同一出生、组号复用、拒读/空 stat、组长已消失但同 session 后台成员仍在、正常退出与取消重试。不得在生产数据上发送合成 kill。完成后由主代理运行两 flavor 完整单测/Lint/正式同签 APK 的无害命令与维护关闭，Android 6/7/ROM/16KiB 缺口仍单列。尚未实施时本节是证据及关闭条件，不表示漏洞已动态复现。

## 本次实现与验证状态

`TerminalSession` 的固定启动脚本在独立 Bash 会话内读自身 `/proc/$$/stat`，以原随机前缀送出 `READY:<pid>:<starttime>`；`TerminalReady` 严格解析长度与正整数。生产 `SimpleTerminalBackend.onReady` 调用 `TerminalProcessCloser.captureSimpleLeader`：比对 READY 出生时刻、`session=group=pid`、实际 UID 和内核 sessionId 后才允许用户命令。取消、用户关闭和维护关闭仍共用 `TerminalSession.stop`；生产后端不再发送 `Os.kill(-group, SIGKILL)`，而复用 PTY 的逐成员出生身份回收，并在 launcher 回收与释放 lifetime 前确认会话已空。组长消失时只检查会话空，不发组信号；不明 READY、UID/stat/session 拒读、成员仍存活或身份变化会让停止失败，原标签及工作锁继续占用。后端仅在完整成功后清除所存身份，重试仍比对本次 marker。

`TerminalSessionTest` 已追加组号相同而出生时刻不同、身份暂拒后重试、无 READY 即退出保留锁的夹具；原测试中的过期回调、正常中止与维护争用仍覆盖对应状态。`TerminalReadyTest` 覆盖格式、溢出与尾随内容。独立 `javac --release 17` 编译并运行 `TerminalReadyCheck` 通过，`git diff --check` 通过。未按分工运行 Gradle 或手机；Android 的 `/proc`/JNI 会话访问、正式 APK 关闭实效待主代理门禁及非破坏性真机检查。新增受管 launcher 输入 `util/TerminalReady.java` 需纳描述符。
