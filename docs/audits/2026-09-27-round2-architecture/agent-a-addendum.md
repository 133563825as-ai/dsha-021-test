# A 组第二轮补充：R2-A02 条件性实证

此前 `agent-a-audit.md` 将 launcher 退出后 proroot guest 留存列为待验证风险。主代理在 API 33 设备、全新 `/data/local/tmp` 隔离 rootfs 使用当前正式 APK 同字节原生库和 proroot `--static-loader` 进行了不接触正式应用数据的夹具。记录见 `app/build/round2-audit/c-process/process-plan.md`、`manifest.json`、`device-proroot.txt`：前台 Bash 正常 `exit 0`，launcher 于 uptime `253066.42` 返回；自限时后台 guest 于 `253072.43` 写出 `AFTER`，之后按出生身份确认消失。proot 对照因 shell SELinux 域 `execve Permission denied` 无有效结论。

这**证实了 proroot 机制可在 launcher 正常返回后继续运行 guest**。当前 `ProotBootstrap.collectRootfs` 原实现只把 `RuntimeTasks` token 保留到 Java `Process.exitValue()` 确认，故在该条件下可能先放行维护；尚未证明相同现象在应用 UID、正式 rootfs/数据或全部 ROM 下发生。类型从“未复现风险”提升为“条件性已证实生命周期缺口”，不宣称用户数据已受损。未知身份、guest 未退出或无法读 `/proc` 继续保持保护。

限定修复对象为 `collectRootfs` 使用所选 **proroot 的有界非交互命令**。保留 `execRootfs` Web 长进程、PTY、独立应急与既有 proot 路径。该分支复用已有 `IsolatedInstallProcess` 的独立会话启动与已核验组长出生身份：`BoundedProcessRunner` 可因监督器写入状态而提前返回前台命令退出码，调用方必须在释放 token 前显式 `close()`，核验 supervisor 与整组消失；超时、取消或核验失败让原 token 继续保留，不重放命令，不按裸 PID/端口/名称发信号。若系统缺少可信会话 launcher，拒绝本次 bounded proroot 命令，不降回无监督路径。

关闭条件：隔离 Android 夹具分别覆盖正常前台退出但后台 guest 短时留存、超时、取消、身份不可读/组退出未知；确认 work token 在 guest 退出前不释放、失败保留维护围栏、状态码与输出仍准确。再用正式非调试 APK 的非破坏性路径验证可用性，不在用户数据上做故障注入。此修复不能替代 Android 6/7、16 KiB 或全部 ROM 的进程矩阵。

## 监督器父死亡窗口（实施前补充）

`IsolatedInstallProcess.start` 当前通过 `tools/native-session/session-launcher.c` 建会话；C 程序只 `setsid()` 后 `execvp()` 原 shell。其 Java `close()` 可在 App 活着时核验出生身份并回收组，但若 App 在握手后死亡，内存 token 与 watcher 一并消失，旧会话可能继续写入。把旧冷安装监督器复用到普通 bounded proroot 后，这个窗口从安装事务扩大到日常命令；仅靠 `close()` 不能称已封闭。

本轮限定采用两层保护：Bundled native session launcher 保持 session leader，安装父死亡信号、在 fork/exec 前后确认原父关系；收到父死亡信号时只结束自身创建的独立组。bounded proroot 同时在**发送启动握手前**写入应用私有的本次会话记录（leader PID、出生时刻、session ID）；确认整组退出后只清本次记录。重启的只读 pending 探测遇 live/未知记录即阻止正式 Web/维护；只有核验该出生身份及整组均已退出才可收敛，不按已复用的裸 PID 或端口推测。独立应急、PTY、正式 Web 和普通 proot 仍走原有边界。

此记录兜底覆盖 native leader 被单独 SIGKILL、App 随后死亡而父死亡 handler 无法再运行的情况，但**不承诺自动结束身份不可读的 guest**。其原件与维护写入保持阻塞，待明确退出证据再恢复。`/system/bin/setsid` 后备没有父死亡监督语义，不能用于本次 bounded proroot 新路径。
