# R2-A05：运行时配置与诊断反向边（实施前记录）

当前真实反向调用：`ContainerRuntime.Proroot` 构造器读 `core.ConfigStore.isProrootStaticLoader()`；`ProotBootstrap.applyProotEnv` 读 `isProotSeccompDisabled()`；`RuntimeTools.prepareResolver/applyEnvironment` 分别新建 `ConfigStore` 读 DNS 模式。一次命令的运行方式/环境变量可能跨多次读取设置。`ProotBootstrap` 的冷安装阶段与启动记录又直接调用 `core.ColdInstallDiagnostics.stage/record/failure`。由此 runtime 同时知道 UI 设置实现和宿主诊断持久化入口，且未初始化进程缺少明确失败契约。

实施限定：在应用 `DshaApp` 组合根安装唯一 `RuntimeHostPorts` provider。`ConfigStore` 一次生成不可变只读 `RuntimeConfigSnapshot`（DNS、proroot static loader、proot seccomp），runtime 通过 invocation scope 在一次命令/模式选择期间复用同一快照；下一次 invocation 重新读取当前配置。诊断 sink 由组合根绑定 `ColdInstallDiagnostics`，runtime 只发送阶段/结果/失败。未安装 provider 拒绝运行/诊断，不在 runtime 复制默认值或自行读 SharedPreferences。独立 `RecoveryRuntime` 不接这个正式配置端口。

把 `DiagnosticRepository` 真正以 marker/退出码判成功的网络修复和工具探测迁 `execAndReadWithProotResult` + `GuestCommandOutcome`；所选运行方式 smoke 明确使用 typed 结果与实际 `runtimeMode`，失败不显示“通过”。旧字符串 API 只留原兼容调用者，不复制命令组装。验证用纯 JVM 端口测试默认快照、设置切换、未初始化拒绝、嵌套调用一致性、取消/异常 scope 释放及诊断 sink 异常；最终两 flavor Gradle/设备由主代理统一执行。

此包只移除上述运行时→core 配置/诊断反向知识；Ubuntu/Node/DSH、双运行方式及独立应急根是产品必要复杂度，不能因其存在单独计债。

## 定向验证中新发现的可见性缺口

`DiagnosticRepository.run` 的新 typed smoke/tool 在 `collect()` 抛错后只 `report.postValue("诊断未完成…")`；`DiagnosticActivity` 的 `diagnostic_report` 布局默认 `gone`，结构化 `results` 仍保留上轮卡片，`busy=false` 时 headline 又按“有旧 results”显示“检查已完成”。因此真实探针失败会被隐藏成旧成功状态。最小修复是开始新检查就清空旧 results，并用明确的 READY/RUNNING/FAILED/SUCCEEDED 状态驱动 headline；失败同时发布脱敏的可见 Result，成功卡片显示本次实际 runtime 和 SMOKE_OK。保持现有报告复制/导出与卡片入口，不改 UI 结构。

## gate8 后空 workdir 兼容回归（实施前记录）

移除无调用的 `ProotBootstrap.flattenL2sChains` 后，`RuntimeHostPorts.Settings.workdir` 在生产已无读取者；构造器仍拒空串（`RUNTIME_WORKDIR`），`ConfigStore.runtimeSettingsFrom` 又从原偏好映像复制 `KEY_WORKDIR`。历史备份 `ConfigStore.importBackupSettings` 可保存空 workdir，旧 `getWorkdir` 也原样返回。结果是与本次 proot/DNS 启动无关的旧空值会令所有 runtime snapshot 提前失败。只从 `RuntimeHostPorts.Settings` 移除 workdir 字段/构造参数及 `runtimeSettingsFrom` 的复制读取，保留 ConfigStore 原工作目录设置和备份导入语义，不替用户改写持久值。回归测试要求空 workdir 的偏好映像仍生成正常 DNS/模式/loader/seccomp 快照。
