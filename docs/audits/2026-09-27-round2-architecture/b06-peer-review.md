# B06 独立只读交叉复核：DocumentsProvider 父路径竞态

对照 `r2-b06-documents-parent-race.md`、当前 `DshaDocumentsProvider` 三入口、既有 `AndroidBackupFileSystem` 与 `git HEAD` 中旧 File 路径操作。未改源码、运行 Gradle 或设备。**未发现这次有限迁移导致的正常 SAF 语义退化或新的写入绕行。**

`createDocument()` 仍先检查名称、父逻辑文档、目录类型和 1000 次同名候选，成功后 `changed(id)` 并返回相同不透明 ID（当前 `DshaDocumentsProvider.java:181-200`）。具体创建改为已有 FS 的逐组件 NOFOLLOW 父描述符 `mkdir` 或 O_EXCL 文件；只有错误链里内核明确 `EEXIST` 才尝试下一候选，父目录链接替换、拒读、空间不足等不会被误当“同名”并继续。新文件 0600/目录 0700 仍由应用 UID 的 Provider 与同 UID guest 访问，SAF 外部调用者继续经 Provider 授权，不增加任何公开权限。

`renameDocument()` 的原有 anchor 禁止、同名返回、目标占用检查、descendants ID 收集、子授权撤销和新旧 URI 通知顺序保持；实际切换改用 FS 的已固定两侧父目录描述符、同设备与目标缺失复核（`:211-221`，`AndroidBackupFileSystem.java:91-99`）。目标在预检后并发出现时拒绝覆盖，父路径被换成链接时拒绝跟随。`deleteDocument()` 仍先以逻辑 ID 枚举后代、倒序逐项删除并撤销该项授权，异常时仍通知原 id；FS 的 `Os.remove` 对末端链接只删链接本身，`resolve(child,false)` 保留既有不跟末端链接的契约（`:223-245`）。

`openDocument()`、文档 ID/树 grant 判断、打开后的 FD 位置检查与写截断时机、`RuntimeTasks` lease 生命周期没有被 B06 修改（`:66-79,156-180`）。三入口各自仍包在 `lease(false)` 内，父目录被另一个同 UID 写入者替换时，描述符型操作拒绝并转成 `FileNotFoundException`，未出现由新代码跨链接写到外部路径的逻辑。旧 File 创建、目录创建、改名、删除在 API33 shell UID 合成竞态确实重定向；仓库真实 FS/DocumentPaths 的隔离 DEX 对应拒绝，正常四种动作及删链接保留目标共 53 断言通过、FD 50→50（私有 `app/build/round2-audit/b06/android-result.txt`）。

验收边界：这个证据是 shell UID 的合成目录，不等于 app UID/所有 ROM/Android 6–7 的完整 SAF 矩阵。FS 移动或创建之后若父目录 `fsync` 报错，调用者可能收到失败而现场已有新项；应保留错误并刷新目录核实，不能自动重放。当前代码没有在未知结果后重试写入，属于 fail-closed 的剩余故障窗口。正式候选包的普通文件创建、重命名、删除与末端链接操作仍需主代理做非破坏性验收；这项只读复核不代替其完成证明。
