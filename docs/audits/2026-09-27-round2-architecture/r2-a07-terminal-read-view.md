# R2-A07：终端标签单 owner 的只读展示边界

实施前证据：`TerminalSessionOwner` 内唯一的 PTY/简易 `TerminalTabs` 表负责永久 ID、显示编号、关闭中及失败占位；但 `ptyTabs()/simpleTabs()` 直接返回这两张可变表。`PtyTerminalFragment` 与 `TerminalFragment` 目前只调用 `wasInitialized/find` 并交给 `TerminalTabBar.render(snapshot/current)`，没有已证实的生产绕行。接口类型仍允许 UI 或未来调用方直接 `remove/select/beginClose`，跳过 owner 的退出确认与通知。这是单 owner 迁移后剩余的可实施封装债，不声称现行漏关。

有限方案：在 `TerminalTabs` 上提供独立运行时类型的只读 view，只暴露 `wasInitialized/find/current/snapshot`；内部由同一原表提供数据，不复制第二 registry。owner 的两个公开表入口改为该 view；`TerminalTabBar` 只接受只读类型，两 Fragment 原查询不改行为。view 对象不能通过运行时 cast 回原 `TerminalTabs`，snapshot 仍是脱离表的不可变列表；实际增删、选择、关闭仍仅由 owner 处理。

关闭条件：纯 JUnit 验证 view 不可回转、初始化/选择/关闭失败状态与编号实时可见、snapshot 不可修改且不随后来新增而变；静态边界守卫拒绝 owner 再暴露可变表、UI 对表直接增删或关闭。既有单调 ID、最小空缺显示号、失败占位、旧页 observer 身份与维护关闭语义不变。主代理统一跑双 flavor Gradle 与正式包无害终端操作；本包不运行 Gradle/设备，不新增文案或复制表。

实施：`TerminalTabs.ReadOnly` 是独立接口，由仅持原表引用的私有 `View` 实现；`TerminalTabs` 自身不实现该接口，所以返回对象运行时不能 cast 回可变表。它只提供四个查询，沿原表同步方法读取实时状态，`snapshot()` 继续返回不可修改且已脱离后续新增的列表。`TerminalSessionOwner` 的 `ptyTabs/simpleTabs` 现在只返回 view，`TerminalTabBar.render` 参数也收窄；两 Fragment 原有查询和 owner 的增删/关闭路径无需改动。没有第二张表、额外选择状态或新进程关闭判据。

验证：新增 `TerminalTabsTest` 的不可 cast/实时关闭失败/快照断言，并在 `TerminalSessionOwnerTest` 确认两个公开入口不可 cast。`tools/test-architecture-boundaries.py` 增加 owner 返回类型、UI 不经 owner 操作标签和标签栏只读参数守卫。该静态脚本已运行通过；独立 `javac --release 17` 编译并运行纯 view 夹具通过，`git diff --check` 无差异格式错误。双 flavor Gradle、正式 APK 终端操作仍交主代理，未在本包执行。
