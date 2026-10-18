# C 组最后窄范围只读复核

范围为 A04 插件预览丢弃、B05 批量检查报告末尾，以及新增宿主进程资源卡片。未修改产品代码，未运行 Gradle 或设备。

**A04 结果：可冻结。** `PluginRepository.discardPreviewConfirmed()` 用无 taskId 的 `runPluginManagerResult` 执行补救清理，先要求 guest 正常完成、未截断，再解析末个结构化 `PLUGIN_RESULT` 并要求 `status=ok`（`PluginRepository.java:429-445`）。它在 `submit()` 的 EnvironmentTaskGate/RuntimeTasks 持有期间调用；取消后的 `PluginTask.check()` 不会阻断必要清理。`submit` 只有接受任务后才入队；`runTask` 只在 work 正常返回时传递 `onSuccess`，`finishTask` 先关闭 task、工作锁和 lease，再设置完成状态与执行成功回调（`:217-279,294-355`）。故丢弃失败不调用 `preview.setValue(null)`，也不显示成功文案；旧 preview 引用留存，取消后新候选清理失败也保存其 id 供重试。

UI 曾有一个真实重试可达性窗口：失败保留同一个 preview 值，已关闭的旧弹窗不会仅因状态改变再次出现；新候选也可能在 busy 期间通知 preview，弹窗操作立即被工作门禁拒绝。A 组已在 `PluginFragment.java:213-217,268-276` 和 `RetainedDataActivity.java:40-54` 增加“状态转非 busy 后排队重显”的窄修，后者的 preview observer 同时要求非 busy。复核 `finishTask` 的主线程顺序：`state.setValue(completed)` 同步让 observer `View.post()`，随后同一调用栈 `onSuccess` 清空已成功丢弃的 preview；排队重显读到 null，不会重开。失败无 `onSuccess`，排队重显读到保留 preview；两个现有 dialog 字段防重。现有确认安装路径的 `onAccepted` 提前清 preview 属其单次已授权安装流程，不被误用为丢弃成功判据。

**B05 结果：未发现新授权问题。** `RetainedDataActivity.inspect()` 每 25 项或 500ms 发布汇总进度，末尾仍保留每项 key 与结果供用户访问，最终报告内存/展示成本随选择数 O(selected) 增长（`:122-150`）。这是本包明确保留的详细可访问信息；先前 O(K²) 反复复制整串的中间进度问题已收敛，不因评分目标添加截断/导出新产品范围。10 万 key 是逻辑上限，不是已测的真机 TextView 性能承诺。

**宿主资源卡片：静态边界清楚。** `DiagnosticRepository.hostResources()` 只采当前 app 进程 PID、`Debug.getPss()`、`/proc/self/fd` 和 `/proc/self/task` 的计数；拒读显示 unavailable，不打开其它 PID、用户文件或完整日志（`DiagnosticRepository.java:149-171`）。卡片明确不包含 Ubuntu/独立浏览器子进程，建议同操作后比多个样本；它提升可诊断性，不能由一次数值推断内存泄漏或耗电。该卡片在 `collect()` 的所选 runtime smoke 和工具检查之后生成；若前面抛错，诊断转失败而不显示资源卡，这是当前可用性边界，不是资源计数伪通过。动态数值和多样本释放曲线仍待正式设备实际采集。
