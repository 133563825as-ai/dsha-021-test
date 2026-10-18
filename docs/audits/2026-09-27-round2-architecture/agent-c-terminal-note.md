# 简易终端最后标签关闭时的 New 按钮状态

这是一个静态可达的旧 UI 问题，**不由 A01 新 TerminalSessionOwner 引入**。当前 `TerminalFragment.newTerminal()` 在新增并附着 SimpleTab 后将 `terminal_new` 强制设为 disabled，再调用 `session.ensureStarted()`。若会话仍处于 STARTING 就关闭最后标签，`closeTerminal()` 标记关闭，owner 在核验 `disposeAndWait()` 成功后移除标签，完成回调再调用 `attachSelected()`。此时 `tab==null`，`attachSelected()` 只设置输入提示并跳过 `renderState()`；启用 New 的唯一现有状态回调属于已移除会话，迟到回调也受 active tab 身份检查挡掉。因此按钮可保持 disabled，当前页面不能再新建终端；重新建 Fragment 时 XML 初始状态可能恢复。

对 `git show HEAD:app/src/main/java/com/deepseekharness/app/ui/TerminalFragment.java` 的对应 `newTerminal()`、`attachSelected()`、`renderState()` 只读核对：旧实现同样在创建后禁用 New，`tab==null` 分支同样不重新启用，故为既有边缘缺陷而非本次 owner 迁移回归。最小修复是在无选中 tab 的 `attachSelected()` 明确重新启用 New，点击时仍经现有维护门禁；以“STARTING 关闭最后一标签→关闭核验成功→新建按钮可用、显示编号回到1”作聚焦验证。当前 gate9 源码冻结，本页仅记录，不修改产品。
