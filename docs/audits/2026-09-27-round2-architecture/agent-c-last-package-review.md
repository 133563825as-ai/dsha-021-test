# C 组最后 UI 窄包：A04 取消失败的安全出口

先核对了真实循环：`PluginFragment` 的预览负按钮和返回取消都调用 `discardPreview()`，弹窗自动关闭；若脚本清理失败，Repository 保留同一 preview 并发布非 busy 状态；先前的状态观察者立即据同一个值重开同一弹窗。再次返回会重复清理，用户难以“稍后处理”。`RetainedDataActivity` 使用相同保留 preview 机制，也需要可见的非模态后续入口。

只改两个现有 UI：取消前登记本次 `deferredPreviewId`，它只抑制同 id 的自动弹窗，**不授权确认、不删除候选、不绕过 Repository 的任务门禁**。成功丢弃后 Repository 的 `onSuccess` 清 preview，观察者清掉 defer；失败则 preview 保留、自动重显被抑制，原状态错误完整保留。`PluginFragment` 的现有状态行明确提示“清理未确认；预览已保留。点此重试或稍后处理”，点击可选择重新打开预览、显式重试清理或稍后处理；defer id 随 Fragment saved state 保留旋转。`RetainedDataActivity` 的现有报告行提供同样入口；原审阅弹窗另有“稍后处理”，返回只保留预览而不自动提交写入。显式重试仍保持 defer，连续失败不会重新弹窗循环。新 preview id 不受旧 defer 影响。

回调顺序仍由 Repository 决定：`finishTask` 主线程先发布完成状态，同一栈的成功 `onSuccess` 才清 preview；两个 UI 的非 busy 自动展示使用 `View.post()`，成功清理后看到 null，失败时看到保留值但 defer 抑制，用户从明确可点击状态手动处理。`git diff --check` 对两文件通过。未运行 Gradle、未操作设备；实际短屏/大字体及取消失败弹窗行为须由主代理整轮门禁与合适 UI 夹具核验。

新增按钮和失败提示未在 `tools/i18n/messages.json` 预先声明；最终实现直接用 `UiText.choose(zh,en)` 与既有 `t(zh,en)` 提供准确中英文，未让目录缺项落回生硬中文。初次取消后新候选清理失败会显示一次；再次取消时先登记同一 id 的 defer，从而退出自动弹窗循环。定向静态复核后 UI 包冻结。

附带只读复核：`DiagnosticRepository.hostResources()` 仅采样当前 app 进程的 PID、PSS、`/proc/self/fd` 与 `/proc/self/task` 数量；拒读为 `unavailable`，没有输出 FD 路径、文件内容、其它进程身份，也没有改变前置 selected runtime smoke/工具检查成功条件。`RetainedDataActivity.inspect()` 中间进度改为摘要，末尾仍保留逐项结果以便用户访问，成本 O(selected) 如实记录，不在本窄包添加截断或导出流程。
