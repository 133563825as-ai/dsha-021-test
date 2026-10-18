# R2-A07 只读交叉复核

结论：在本次限定的只读 view 改动中，未发现阻断门禁的回归。此结论来自源码和测试断言复核；本复核未运行 Gradle、脚本或设备。

`TerminalTabs.ReadOnly<T>` 仅有 `snapshot/current/find/wasInitialized` 四个查询。返回对象是私有 `View<T>`，持有同一张原表引用，`TerminalTabs` 本身不实现该接口；因此普通运行时类型转换不能从 `ptyTabs()/simpleTabs()` 取得可变原表。owner 两个入口均返回该 view；标签栏也只接收 `ReadOnly`。两个 Fragment 读取初始化与按永久 ID 查找，增删、选择、开始关闭和退出确认仍调用 `TerminalSessionOwner`，没有新增表或按显示编号操作。`View` 每次委托原表同步查询，`snapshot()` 原有的不可修改列表和结构性拷贝行为保持；列表中的 `Tab` 对象本来就反映关闭状态，不能把结构快照误称为冻结的深拷贝。

关闭失败路径仍由 owner 的 `closePty/closeSimple` 在退出未确认时调用 `closeFailed(id)`，保留原会话、永久 ID 和占用的显示编号；成功后才 `remove(id)`。`TerminalTabsTest` 覆盖 view 不能 cast、初始化和关闭失败状态实时可见、旧 snapshot 不随新增扩容且不可修改；`TerminalSessionOwnerTest` 核公开入口不可 cast、失败占位及重试后编号复用。`tools/test-architecture-boundaries.py` 对两个 Fragment 的直接表修改、owner 可变表返回签名和标签栏参数设了静态守卫。它是针对已审接口的守卫，不把正则匹配当成所有 Java 行为的证明。

边界说明：`ReadOnly` 返回的 `Tab.value` 仍是现有会话对象，而 `snapshot()` 只保证列表结构不可修改；A07 的目标是收窄标签表的修改权限，未改会话操作 API 或此前的并发可见性规则。该现状不构成这次改动引入的回归。正式双 flavor 编译和终端设备行为仍由主代理的后续门禁与验收确认。
