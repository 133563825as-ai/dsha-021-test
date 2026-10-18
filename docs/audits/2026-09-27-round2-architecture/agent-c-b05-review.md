# C 组独立只读复核：R2-B05 保留清单分页与批量检查

审查当前 `RetainedCatalogue.java`、`RetainedDataActivity.java`、`RetainedCatalogueTest.java` 及实际导出/恢复调用链；未改产品代码，未运行 Gradle或设备。结论：**未发现会把失效多选条目直接授权恢复的阻断**；有两项 P3 只读清单准确性/性能边界需要在 B05 结案时明确，若目标严格要求 O(page) 总内存则第二项尚未关闭。

已核对的主路径：`NEWEST_FIRST` 用 `modified` 降序，再以 logical key 和目录绝对路径打破平局；`PageSink` 的最大堆保留至多请求页大小的 `Entry`，`next` 来自本页最后一项，下一页按相同三元组过滤（`RetainedCatalogue.java:24-83`）。当前页总数与 `eligible>size` 决定下一页，不做 `index*size` 的整数乘法；100 项上限和 `Page.hasNext()` 避免旧偏移式边界。每页重新计算可见元数据的聚合指纹；目录插入/删除或状态变化后使用旧 cursor 会抛 `RETAINED_PAGE_CHANGED`，Activity 清游标回第一页并提示（`RetainedDataActivity.java:48-55`）。这是**展示游标**，没有把摘要冒充源字节健康证明。

多选存储原 `Entry`，`inspect()` 在一个只读扫描中 `resolveAll(selected.keySet())`，每项再用 `sameListing` 对 key、modified、状态、scope、目录及 source 路径作比较，报告缺失、重复与改变；加密副本重验文件摘要，树形来源 walk/open/verify（`RetainedDataActivity.java:112-131`）。`resolveAll()` 同 key 命中两次会删掉已找到项并标 `RETAINED_SOURCE_DUPLICATE`，同 owner 而 part 消失标 `RETAINED_SOURCE_CHANGED`（`RetainedCatalogue.java:197-230`）。检查不是恢复授权：`NativeDataActivity` 打开操作时重新 `resolve(key)`，`NativeBackupJobs` 在 worker 内又调用 `catalogue.sources(retainedTree)`；配置设置预览也在独占维护内重新 resolve。因此多选界面即使在检查后变化，实际导出/恢复仍不使用旧 `Entry` 路径直接写入。

**R2-B05-C1 · P3 · 首屏重复 key 可虚增总数。** `PageSink.add()` 每次直接 `total++` 并入堆（`RetainedCatalogue.java:46-67`），没有与 `resolveAll()` 相同的重复 key 判据。若同 UUID/part 同时出现在 active 与 completed（例如中断/异常搬动产生双份），`page(null,50)` 会显示同一 logical key 两行并将总数加二；用户勾选后 Map 仅保留一项，检查会在 `resolveAll()` 拒绝，不会越权恢复，但“总数/分页是真实唯一记录”不成立。现有 `RetainedCatalogueTest.java:132-134` 只覆盖 batch lookup 拒绝双份。建议首屏检测重复并显示冲突记录/错误，或按物理原件分别生成不可混淆的展示 key；关闭标准是 active+completed 双份夹具的 `page(null)`、cursor 后续页与 `resolveAll` 一致，原件均保留。

**R2-B05-C2 · P3 · 页内 Entry 有界，总内存仍随单目录历史增长。** `PageSink` 只留最多 100 个 `Entry`，但每个 `scan()` 先取得 `fs.list(parent)` 的整份列表，再 `new ArrayList<>(...)` 并排序（`RetainedCatalogue.java:94-99`）。因此峰值至少为 O(最大目录成员数)，还在 Java 中复制一份，不是严格 O(page) 内存；1000 条 completed 插件测试证明分页输出和一次扫描调用量，不能证明十万/百万历史目录的内存上限。建议先去掉额外复制/排序，若产品要求真正 O(page) 总内存，则让 `BackupFileSystem` 提供逐项目录迭代并在 Android 实现中确保句柄生命周期；指纹与 top-k 仍可单遍计算。关闭标准应测峰值 retained names/heap，且未知原件、分页顺序和总数保持一致。

次要快照边界：`sameListing()` 未比较 `displayName`/`protection`（`RetainedCatalogue.java:232-237`）。隔离包 `package.json` 原位改名可让目录 `modified` 与 source 路径不变，而检查转向新内容；这不授权写入，实际操作重新定位。若界面文案承诺“检查我勾选时看到的那个插件名称”，可把这两个展示字段纳入快照比较，并用同目录文件内容变化夹具验证。当前结论只要求界面准确报告来源已改变，不把它升级为数据安全阻断。

**R2-B05-C3 · P3 · 页状态提交与显示不是原子步骤。** `RetainedDataActivity.refresh()` 在后台调用 `model.entries.postValue(page)`，随即在 `finally` 把 `working=false`（`:48-55`）；`render(page)` 却从共享的 `model.page` 计算显示范围并决定上一页/下一页（`:61-68`）。`postValue` 的主线程投递尚未执行时，旧页按钮已可在 `working=false` 窗口再点，改变 `model.page/starts`。例如页0第一次 Next 已算出页1但未 render，旧页0的 Next 再次按下使 `model.page=2` 且把旧 cursor 放进 starts[2]；先到的页1结果被标为第3页，后续可重复页1或触发无谓刷新。没有数据写入，但真实分页的 UI 序号/按钮与条目可短暂错配。最小修复是在主线程以请求序号提交捕获的 page index/cursor，并使用同步 `entries.setValue(page)` 更新显示后才清 `working`；旧回调与旧按钮不能再改最新页索引。测试可用受控主线程队列延迟 LiveData 分发，连续两次 Next 验证没有重复页或错标。

限制：`collect()` 不是文件系统原子快照；记录在一次扫描期间从 active 移到 completed 时，当前页可短暂显示双份或随后用旧 cursor 触发刷新。持久操作仍由领域事务/锁决定，不依靠展示游标保证一致性。本次只做静态交叉审查，既有 257 条分页、120 条变更游标和 1000 条批量 lookup 测试源码已读，动态通过以主代理整轮门禁为准。

## B05 第一轮反馈后的定向复验

B 组已在 `scan()` 去掉额外整目录 `ArrayList` 与重复排序；文件系统 `list()` 本身仍返回整份名字列表，故如实保持 O(单根最大目录名单 + pageSize) 峰值，不扩展本轮 FS API。`refresh()` 捕获请求页/cursor 与 requestId，主线程同步 `entries.setValue(page)` 完成活动 observer 的 render 后才清 `working`，旧页按钮竞态已按建议收敛。插件 active/completed 双份现显示 `DUPLICATE` 与物理来源，并禁用复选框/详情动作，`resolveAll` 仍拒同 key。

仍需一次聚焦修正：`RetainedCatalogue.java:101-107` 先将双份备份置为 `DUPLICATE`，但若有 `verified.json` 仍调用 `VerifiedBackupCopy.inspect()`；它通过 active/completed `locate()` 拒双份并抛错，外层 catch 把该物理行改成 `UNREADABLE record`。这种双份未取得 DUPLICATE 标签，复选框只对 DUPLICATE 禁用；虽然 source 为 null 使导出/恢复动作不可见，检查也由 `resolveAll` 拒绝，展示承诺仍不一致。建议在 `status=DUPLICATE` 时跳过 verified copy inspect，保留双份物理目录的冲突行；测试补 active+completed 同 ID 且带 verified.json。禁用冲突框时也应移除 `model.selected` 的旧键，避免冲突后来消失后旧选择自动恢复。上述是 P3 UI/清单准确性，非绕过恢复授权。

## 第二次聚焦复验（进入正式门禁意见）

B 组已在检测到 active/completed 同 ID 时立即为**两个物理目录**各生成无 source 的 `DUPLICATE record`，跳过 `VerifiedBackupCopy.inspect()`（现 `RetainedCatalogue.java:101-107`）。`resolveAll()` 收集同 kind:id 的 `duplicateOwners`，即使用户持有冲突出现前的 `:encrypted` 或其它 part 旧 key，也返回 `RETAINED_SOURCE_DUPLICATE`（`:217-240`）。UI 的冲突行保留物理来源标签，复选框与导出/恢复动作均禁用；渲染时移除该 kind:id 前缀的旧选中键，不会在冲突消失后静默恢复旧选择（`RetainedDataActivity.java:64-86`）。

刷新链仍以捕获的请求页/cursor 与 requestId 计算，主线程 `entries.setValue()` 同步通知活动 observer 后才释放 `working`（`:48-63`），旧页按钮无法在待投递结果窗口改变页码。目录扫描已去掉第二份全量名字拷贝/排序；峰值仍如实为 O(单根名字数 + pageSize)，不是严格 O(pageSize)。**本次限定范围静态复验无剩余阻断，可进入正式门禁。** 若要把异常双份标签做到包括损坏 marker，可将重复位置判定移至 marker 内容读取前；现有外层 `UNREADABLE`、无 source 与动作再核验仍保持 fail-closed，这一边缘展示差异不影响本次门禁结论。聚焦测试尚待整轮执行。
