# R2-A02 独立进程夹具：一次设备观察

统一手机操作者在 `/data/local/tmp` 的全新隔离目录执行了 C 组准备的最小夹具。本文件只列规范化结果，不包含设备序列号、token、私有目录路径或用户数据。夹具 `fixture.tar.gz` 的 SHA-256 为 `3f0320ecf1458a5b2ea0df2d9401f7870c043d68ac084e2b20831e7a9b40ce4b`；native ELF 源于同版本 E7E3 Standard APK（APK SHA-256 `4840b234a065e2d05971ffde53eed8f4560e4f1943ed171087c349973895f5e4`），操作者已核对设备现装 APK 摘要一致。客体仅有锁定 rootfs 的 Bash、sleep、glibc 加载器/库、独立空目录和合成哨兵；无 DSH、正式 profile、用户 workspace 或应用私有数据。

`proroot` 一次运行中，前台 launcher 正常返回 `0`，宿主 `/proc/uptime` 为 `253066.42`；后台 guest 在自身六秒计时后写下 `AFTER 253072.43`，即结果时刻比 launcher 返回晚约 **6.01 秒**。随后对该客体出生身份所记录旧 PID 的 `/proc` 核对显示进程已不存在。整个夹具没有强杀或裸 PID 清理，guest 按脚本自行退出。这证明在该 shell UID/设备环境中，普通 proroot launcher 正常退出后，合成后台 guest 可以继续运行到自退；它是机制证据，不预认定 DSHA 正式 PTY 或维护实现有缺陷。

一次 `proot --kill-on-exit` 对照在 shell 域因 `execve("/usr/bin/bash"): Permission denied` 失败，launcher 返回 `1` 且无客体结果。该对照**不可用于**证明 proot 一定会回收或不会产生 guest。无第二次重试或其它设备实验。

夹具在 `adb shell` 的 shell UID/SELinux 域执行；应用 UID、私有 rootfs 的执行/挂载权限、PTY launcher 和维护停止屏障可能不同。当前结果不能代替应用同 UID 的真机矩阵，也不支持对正式数据做故障注入。操作者确认本轮没有修改正式应用数据或安装额外 APK；原始过程记录仅保留在忽略的 `app/build/round2-audit/c-process/`。

## 候选 native session 监督器的有限跟进

首次尝试用命名 FIFO 驱动候选 native 的三个独立 case，均在创建 FIFO 时遇到 shell 域 `Permission denied`，退出码 125；候选监督器**没有启动**，不得把这批结果解释为监督器失败或通过。随后只准备并执行一版不同夹具：API 23 AArch64 C driver 用匿名 `pipe()`、`fork()`、`execv()`，保持自己是候选 native 的直接父进程，模拟应用传入 stdin 的握手。归档 `app/build/round2-audit/c-session/session-fixture.tar.gz` SHA-256 为 `6b20e4de11c91f12299b09fca096ae74bf097e82cf5fb9cd2514e41a52c4afb0`；候选 `libdsha-session.so` SHA-256 `0ab7a3c3a944d7237bfd5107a9fd670a2b5708c4dd50490e049b697e1d23034d`，隔离测试 driver SHA-256 `25b78624dfed5ea9f0125dfc9098a102b4ae65fea6af67fd6fd073b618128f03`。三例各在全新 `/data/local/tmp` 目录执行一次，未重试，也没有改 APK 或正式数据。

| Case | 前台状态 | Driver 结果 | 最终进程核验 | Guest 结果 | Case |
|---|---|---|---|---|---|
| 身份双核后关闭本次组 | 0 | `VERIFIED_GROUP_CLOSE_REQUESTED` | live 0 / zombie 0 / unreadable 0 | 无 | PASS，退出 0 |
| 子 shell 写状态并 STOP 后父正常退出 | 0 | `PARENT_LEFT_AFTER_CHILD_STOP` | live 0 / zombie 0 / unreadable 0 | 无 | PASS，退出 0 |
| 父在送握手前退出 | 无前台状态 | `PARENT_LEFT_BEFORE_HANDSHAKE` | live 0 / zombie 0 / unreadable 0 | 未出现启动或结果哨兵 | PASS，退出 0 |

三个 case 的 `driver-exit` 均为 0，身份错误标记均未出现；成功判据还要求六秒 guest 的 `guest-result` 不存在，避免“客体先自行完成、最终 live 也为 0”的伪通过。第一例只有两次核对同一 native leader 的 PID、出生时间、PPID 与 `pgrp=session=pid` 后才对该组发信号；第二例由候选的父死亡处理接管；第三例未送启动 token。僵尸与活进程分开计数，未按裸 PID 猜杀。此结果支持候选监督器在该设备的 **shell UID** 隔离环境中处理这三个受控窗口，不能替代 app UID、真实冷安装/PTY 和不同厂商 `/proc` 可见性的验收。原始输出保存在忽略的 `app/build/round2-audit/c-session/`，公开记录不含目录随机后缀或设备标识。
