# R2-A04 最后窄包方案（gate6 期间只读）

## 校正事实

`ProotBootstrap.flattenL2sChains()` 只在自身声明；定向 `rg` 在 main/test 无调用。AGENTS.md 已禁止普通启动遍历工作区做旧链接修复，因此它不是当前可达的字符串“成功”误报。可移除这段死入口和 `L2S_FLATTEN_SCRIPT` Java 常量，但保留 `app/src/main/assets/flatten-l2s.py` 作为历史/保留资产；删除前再核对反射/脚本入口，无证据时不重新接入启动链。

`PluginRepository` 有四个真实相关边界：`inspect:387–393`、`prepareUpdate:442–448`、`importArchive:464–483` 会先丢旧 preview；`receivePreview:396–404` 在当前 `PluginTask.requested()` 后丢新候选；`discardPreview:424–435` 显示“插件安装预览已取消”。现在两处内部丢弃调用 `proot.runPluginManager("discard-preview …")` 只返回文本且被忽略，Python `plugin-lifecycle.py:319–324` 则明确用 `PLUGIN_RESULT` 的 `status=ok` 表示删除完成。取消任务的 `PluginTask.check()` 在 `requested=true` 后必抛异常，不能复用 `runManager()` 作为补救清理入口。

## 有限实现

1. 在 `PluginRepository` 内建立一个 `discardPreviewConfirmed(proot, previewId)`：直接调用已经存在的 `ProotBootstrap.runPluginManagerResult("discard-preview " + ShellQuote.arg(id), "")`，**不携带已取消的 taskId，也不调用 `runManager/activeTask.check`**。先用 `GuestCommandOutcome.requireCompleted` 拒绝非零退出、超时与截断，再沿既有 `PluginOutput.resultJson`/`result` 解析末个独立 `PLUGIN_RESULT` 行，要求 `status=ok`；脚本的错误 message 经 `SensitiveData.redact` 告知用户。整个动作仍在当前 `submit` 持有的 EnvironmentTaskGate/RuntimeTasks 中，失败不重放写入。
2. 替换旧/新候选的内部丢弃：旧 preview 只有在脚本确认清理后才 `preview.postValue(null)`；失败保留旧引用和原件，终止后续新 inspect/update/import。已请求取消的新候选若清理成功，再报告“已取消解析并清理临时包”；若清理失败，把新 preview 的 id 留在可恢复的原生 preview 状态，报告“取消已请求、临时包清理未确认”，供用户稍后明确重试，不假称完成。不能因本次 task 已取消而跳过必要清理。
3. 用户点击“取消预览”时，把原来 submit `onAccepted` 的 `preview.setValue(null)` 移到确认成功后的 `onSuccess`；失败时 preview 留可见，成功文案只在双重核验后产生。`PluginTask.close()` 仍按原时序清理本次任务标记，不能因 UI 状态调整提前释放维护围栏。

## 验证与输入影响

- 用隔离插件管理夹具验证：正常 `status=ok` 才清 preview/显示取消成功；输出含伪成功 marker 但退出非零、`status=error`、缺 marker、超时、截断均保留可重试 preview 与错误；`requested=true` 时补救丢弃仍能调用脚本；新预览成功替换旧预览，旧预览清理失败不继续安装新候选。检查候选目录实际原件，不只测 UI 文案。
- `PluginRepository.java` 属原生协调层，修改不改变 guest 运行时资产安装表；若移除 `ProotBootstrap.flattenL2sChains`，`runtime/**` launcher tree 摘要和 runtimeId 会变化，需要主代理重算受管描述符并重跑资产/两 flavor 门禁。`flatten-l2s.py` 字节与历史用途保持，不删资产、不新增测试/调试 APK。主代理继续统一正式签名与设备验收。

gate6 未结束前本页仅为可评审方案，不修改产品源码。

## 封存后 UI 重试可见性补充（实施时发现）

原计划“失败保留 preview 即可重试”还缺一个显示边界：`PluginFragment:213–214,263–272` 只在 preview 值变化时尝试弹窗，busy 时直接返回；取消清理失败的新候选常在任务仍 busy 时 `postValue(candidate)`，`finishTask` 仅更新 state 而不重发相同 preview。`RetainedDataActivity:40–44` 原 preview observer 甚至可在 busy 时打开对话框，用户点击后 repository 因 working 拒绝，却自动关闭弹窗。保留原件若不可见，不能称“可重试”。

最小修正：两个入口都在任务转非 busy 后**延迟一个主线程消息**再次检查保留 preview，并以现有 `previewDialog/pluginDialog` 防重；Retained 还要求 `model.pluginAction` 且当前 repository 非 busy，避免其它插件页面候选提前弹出。延迟到 `finishTask` 同一回调的 `onSuccess` 清理之后，成功 discard 不会立刻重开旧 dialog；失败保留同一候选则再次可见。只改预览展示 observer，不动插件事务或完整 UI 导航。

## gate7 单测边界修正

gate7 的 `PluginRepositoryDiscardTest` 在 JVM Android stub 上调用 `JSONObject.has` 报“not mocked”，不代表设备版 parser 失败；不能放宽断言或开启 mock 默认值。生产 discard 完成判据改由 `util.PluginOutput.requireDiscardSuccess` 共用：沿用已有独立结果行识别，但此命令要求恰好一条 `PLUGIN_RESULT`；用项目已锁定的 Gson 2.13.1 `JsonReader` 严格解析完整 JSON 对象，拒字段重复、非字符串 status/message、缺失、坏 JSON、尾随字符、超长结果和 `status!=ok`，错误 message 只脱敏后抛出。`PluginRepository.requireDiscardResult` 仍先执行 `GuestCommandOutcome.requireCompleted` 核对进程退出/超时/截断，然后调用该纯 parser。原 `PluginOutput.resultJson` 保持不变供其它插件路径使用。

这没有新增依赖（`app/build.gradle` 原已锁 Gson 2.13.1）；新增的是 `PluginOutput.java` 的原生取消确认逻辑与纯 JUnit 用例，隔离 JDK17/Gson 的 `PluginOutputTest` 5/5 已通过。它不改变 guest asset 安装表或网络协议。`PluginOutput.java` 当前不在 managed runtime launcherSources，因只影响 Android 本机插件取消文案/核验，是否纳入运行时身份由主代理按 launcher 输入契约统一决定；不得为了让该测试通过把 backup 域 JSON 语义借到 core。
