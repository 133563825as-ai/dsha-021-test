# R2-B06：文件提供器解析与变更之间的父目录变化

2026-09-28，封存后的有限复核工作包。原问题尚为风险，不回写封存台账。

`DshaDocumentsProvider.createDocument/renameDocument/deleteDocument` 先通过 `DocumentPaths.resolve` 检查逻辑 ID、映射和链接，随后使用 `File` 的路径操作。Provider 内的 synchronized 只能协调自身调用，不能约束其它同 UID 文件写入者。`openDocument` 已先打开描述符、核位置再截断，不把它混同为这三个入口。

验证仅使用 `/data/local/tmp` 下本轮唯一编号的合成树，在解析完成后、变更调用前替换合成父目录；比较原路径操作与仓库现有 `AndroidBackupFileSystem` 的逐组件 NOFOLLOW、父目录描述符操作。运行仓库真实 Java 类编译的独立 DEX 夹具，不生成、安装 APK，不访问应用私有数据，不使用 root，不改系统设置。

关闭条件：记录旧路径行为的实际结果；只有确认后才迁移生产调用；创建文件/目录、同目录改名、删除普通文件与删除链接本身保持正常，父目录变更时不沿新链接执行；外部合成哨兵内容不变；描述符关闭；最终软件回归与正式包正常文件入口检查分别记录。shell UID 的夹具不能冒称完整 App UID/所有 ROM 验收。

本问题关注解析后父路径被重定向。目录被原持有者整体移动、同 UID 任意代码的全局对抗、同名目标并发发布等不同语义不由这项测试自动得到证明。

状态：已确认并实施有限修复；双版软件回归与正式包文件入口验收待执行。

## 隔离结果与实现

实际 API 33 / shell UID 2000 执行仓库 `DocumentPaths`、`AndroidBackupFileSystem`、`BackupFileSystem`、`BackupLimits` 编译的 DEX：文件创建、目录创建、改名、删除四个旧路径调用均确认重定向；相应描述符操作全部拒绝，外部合成哨兵保持原样。正常文件创建/目录创建/同目录改名/删除及删除链接保留目标通过，共 53 项断言，FD 50→50。原始结果在 `app/build/round2-audit/b06/android-result.txt`；夹具代码与编译产物保留于该目录。只替换显示文案 `UiText` 为恒等夹具，不替换被测文件系统或路径实现。

生产三入口复用既有 `AndroidBackupFileSystem`：创建仅在内核明确 EEXIST 时选择下一名称，其余权限/父路径变化错误保留；改名与删除通过已固定的父目录描述符操作。文档 ID、映射、grant 判断、终态链接只删链接本身、运行任务 lease 生命周期保持原有合同。未修改 `openDocument`。

这是解析后父目录替换的机制与实际 Android 文件系统代码证据，未冒称完整 Provider/SAF App UID 并发矩阵已验。最终正常 SAF 行为仍需候选正式包证明。

## A02 集成补项：新进程现场目录的 SAF 隔离

在 gate12 正式包上打开系统 DocumentsUI 的 DSHA 根，实际列表出现本轮 A02 新增的 `bounded-guest-active`（私有截图 `provider-system-route.png`）。`UserDataLayout.PRIVATE` 已保护其它运行/事务现场，但遗漏该新目录。这里确认的是目录被枚举，未在正式数据上做记录修改或伪造进程现场。

该目录用于有界 guest 的待确认身份与退出围栏，属于内部控制记录。修复仅将其加入既有精确首层名称集合；通过同一 `resolve` 入口阻止目录本身与后代的读写/枚举，并保留类似前缀的普通用户目录。新增 JUnit 覆盖根、后代及前缀负例。未增加第二套权限策略，不删除任何现场。最终候选应在正常系统文件列表中确认它不再出现。
