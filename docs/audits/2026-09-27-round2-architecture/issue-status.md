> 本页为gate15阶段历史状态，最终权威状态已更新至[final-issue-status.md](final-issue-status.md)与[final-assessment.md](final-assessment.md)。下文待验措辞保留作过程记录。

# 第二轮问题统一状态（软件回执后、设备验收进行中）

本页汇合封存的 `findings-before-fixes.md`、封存后的 [fixes.md](fixes.md) 与各组复核，不回写原问题报告。最近完整软件回执是 [gate15 manifest](../../../app/build/stability-acceptance/f33ef597-f863-4eaf-8c20-0e74eb332471/manifest.json)：`PASS_WITH_EXPLICIT_DEVICE_GAPS`，Standard/Low 各 **794 项 JUnit、0 失败、0 错误、1 项既有跳过**，双版 Release Lint、E7E3 APK/ELF、资产、插件和软件合同门禁通过。**A07 在 gate15 之后改了源码，须由主代理给新候选重跑门禁并绑定新 APK；gate15 不能充当 A07 软件回执。**

gate15 对应的 Low 同签正式候选，主代理目前报告设备合同选中项 **13/14 已实测通过**，no-crash 观察尚未收尾；Standard 尚未完成设备验收。此前 gate10 的 Low 双文件 81/95 字节、gate11 的简易/PTY/Web 和 gate14 的应急语言画面是各自旧候选的真实证据，但不能替代 A07 之后下一候选的确切 APK/SHA 合同。此页不宣称 `release` 已替换或正式交付。

下表“gate15 软件通过”仅限该回执的源码；A07 单列为已实现、待新门禁。“设备待验”是最终源码/APK绑定证据未齐，**不等于功能失败**。正式用户数据上不做强杀、删 Bash、坏插件等故障注入。

| ID | 修复与软件状态 | 最终设备/关闭边界 |
|---|---|---|
| **R2-A01** 维护单 owner | **gate15 软件通过。** `MaintenanceGate` 唯一持锁、owner、停机顺序，Coordinator 绑定平台动作；`TerminalSessionOwner` 持原两表，用户和维护按相同 ID/退出判据关闭，失败保留标签/工作锁。A07 又收窄展示入口，见 [A07](r2-a07-terminal-read-view.md)。 | A07 后须新软件/设备回执；旧根包兼容委托不构成第二把锁。 |
| **R2-A02** 有界 proroot guest | **gate15 软件通过。** API33 shell UID 隔离证实 launcher 已退但 guest 可继续；有界非交互 proroot 改用独立会话监督、父死保护、出生身份/持久记录，未知退出保留围栏。 | Low 旧候选 App UID 固定只读 `SMOKE_OK` 有证；下一候选及其它 ROM/Android6–7 `/proc` 矩阵仍按合同，不能用 shell UID 推断全部正式数据路径。 |
| **R2-A03** Web owner/代次 | **gate15 软件通过。** 生产 Controller 统一 `get()`；WebRun 按 generation 持 launcher/bridge/auth/端口，迟到回调不能发布新代状态。 | Low 旧候选 Web 停止重启有实际证据；下一候选和 Standard 按设备合同，不把单设备扩写成 ROM 矩阵。 |
| **R2-A04** 执行/部署窄边界 | **gate15 软件通过。** `ManagedRuntimeAssets` 接管受管 staging/证明；关键 guest 命令先验退出码/超时/截断，预览丢弃还核严格单条 `PLUGIN_RESULT status=ok`，失败保留候选和“稍后处理”。 | 旧字符串接口仅兼容适配；完整拆分多运行方式不是关闭条件。最终候选插件 UI 与所选模式仍依合同取证。 |
| **R2-A05** 同次配置/工作区兼容 | **gate15 软件通过。** `ConfigStore` 唯一生成运行时设置快照，经 `RuntimeHostPorts` 给 Web、bounded/proot、试运行、冷安装、PTY 同次 argv/env；空旧 workdir 不阻断实际命令。 | 下一候选的模式/PTY 行为依合同；独立应急不读取正式设置。 |
| **R2-A06** 简易终端出生身份 | **gate15 软件通过。** 固定 READY 送 PID/出生时刻，宿主核 UID/session/group/starttime 才接受命令；停止复用逐成员身份回收，不再 `kill(-group)`，未知保留标签/工作锁。见 [A06](r2-a06-simple-terminal-identity.md) 与 [C 组交叉复核](agent-c-a06-review.md)。 | 非调试 App UID `/proc` 可见性、正常命令/取消/维护关闭仍要绑定下一正式候选；不在正式数据造 PID 复用故障。 |
| **R2-A07** 标签只读展示 | **已实现，晚于 gate15；新门禁待验。** owner 仍持唯一两表，公开独立类型 `TerminalTabs.ReadOnly`，UI 无法 runtime cast 回可变表；静态守卫与独立 Java17 夹具通过。见 [A07](r2-a07-terminal-read-view.md)。 | 主代理须新双版软件回执及正式包无害终端/编号/关闭验证；不能将 gate15 794 项直接记作 A07 通过。 |
| **R2-B01** 插件卸载中断 | **已实现、软件通过。** format 3 持久计划覆盖四个原件及 sources/manifest，原锁内回切；已提交不回滚后来修改。隔离进程边界/回滚失败/旧 ID 幂等测试通过。 | 正式个人插件不做强杀故障注入；正常插件列表/启停的最终设备显示可继续按合同核对。未知、修改原件保留。 |
| **R2-B02** 普通历史扫描 | **已实现、软件通过。** active 与经证明的 completed 分离；旧完成历史首轮验证、证明异常 fail-closed，普通门禁只查活动区。新运行时 v2 seal 绑定计划/映射，旧 v1 不补造证明且保留显式回退。宿主/配置 0/65/1,000 条历史稳定态调用量保持恒定；共享只读七域 probe 统一普通首项与恢复多事务计数。 | 宿主 3–4 ms 是隔离夹具，不是手机闪存结果。旧 v1、修改/额外文件现场留 active 是保护边界；需要设备延迟样本才评价实际性能。 |
| **R2-B03** 敏感屏幕确认 | **已实现、软件通过。** 敏感和可记忆确认共用返回后的 generation/revision 复核，弹窗期间撤销的旧结果拒绝。 | Root/Shizuku/ADB 屏幕授权撤销的最终设备矩阵仍待验；已执行动作不能倒退撤销。 |
| **R2-B04** 保留清单 256 悬崖 | **已实现、软件通过。** active/completed 显式可见，单页展示、总数、游标刷新及重复物理原件 fail-closed；应急代次不让纯完成历史形成 256 上限。 | 最终候选保留清单真实界面/深页行为仍待设备合同；不静默删改未知历史。 |
| **R2-B05** 批量检查/游标 | **已实现、软件通过。** 1,000 条历史/11 选择的批量元数据扫描为 17,017 次 FS 调用，对照逐项约 170,170；`resolveAll` 逐 key 区分缺失、变更、不可读和重复。展示游标只持有单页候选，修改后刷新；检查进度只发短计数，最终完整逐项报告一次。 | 显式深页仍遍历历史以核对总数/游标，整体内存仍含单根全量名字；真实手机延迟未量化。导出/恢复仍独立重核真实来源，展示结果不是写入授权。 |
| **R2-B06** DocumentsProvider 父路径竞态 | **gate15 软件通过。** 隔离 Android 合成现场证明旧 create/rename/delete 路径可被换父目录重定向；现沿既有 FS 的 NOFOLLOW 父 fd 相对操作，保留旧 document ID、正常 SAF 与末端链接删除语义。见 [复现与修复](r2-b06-documents-parent-race.md)、[独立交叉复核](b06-peer-review.md)。 | shell UID 隔离 53 断言/FD 50→50 不是 app UID/所有 ROM；最终正式包普通 SAF 创建、改名、删除按合同，fsync 后未知结果不自动重放。 |
| **R2-B07s** ADB 敏感发送前复核 | **gate15 软件通过。** 假 ADB 的修前四项失败/修后通过；已取得 SMS READ 计划在唯一 `dev.shell()` 前重取并比对当前用户、argv、能力与授权，不可用/变化则不发送也不换地址重放。受管脚本内部修订 19 同步 Java/wrapper。见 [B07s](r2-b07-adb-sensitive-dispatch.md)、[交叉复核](b07-peer-review.md)。 | 已发出查询不能追溯撤回；发送前最后极窄在途边界明确保留，未连接正式设备读取短信。 |
| **R2-B07v** 虚拟屏撤销 | **gate15 软件通过。** 修前真实旧 class 闩锁证明 worker 忙时撤销返回后旧票据可 commit；现原子 epoch 与同步取消使 ADB 票据立即失效，旧排队 cleanup 不清新代。见 [B07v](r2-b07-vscreen-revocation.md)、[交叉复核](b07-peer-review.md)。 | 普通 HTTP 本地准入检查已过而撤销随后发生属于在途请求，不宣称撤销后绝无网络字节；最终 Root/Shizuku/ADB 设备矩阵仍按合同。 |
| **R2-C01** 应急前置兼容 | **已实现、软件通过。** 独立锁定归档生成 HTML、PDF 主线程/Worker 前置覆盖，纳入应急内容身份与 APK 证明；页面完成时只处理同源语言桥。 | 最终候选旧 WebView 缺文档起始能力、PDF Worker 与应急语言旋转仍待对应设备/内核证据；Low Gecko 可打开不覆盖这些情形。 |
| **R2-C02** 受管输入重复 | **已实现、软件通过。** 签名输入表同供安装、descriptor 与 Gradle，增删输入及版本身份负向夹具通过。 | 软件资产/双版构建门禁足以关闭当前重复输入缺口；冷设备运行检查仍按总体合同，不从软件包摘要推断所有系统矩阵。 |
| **R2-C03** 交付行为选择 | **已实现、软件通过。** 设备合同按可信 baseline 到候选的源码变化选择必验行为，回执复用重核源码、日志、APK；缺行为证据拒绝交付。 | 当前设备合同仍未完成，**不得写 release 或宣称正式交付**；旧候选行为不能替代当前 SHA 绑定证据。 |
| **R2-C05** 可信验收 baseline（封存后） | **gate15 软件通过。** `baseline_from_receipt` 要求旧回执为 `PASS_FOR_EXECUTED_SCOPE`，核双 flavor E7E3/包名与 `sourceSnapshot` SHA；负向测试拒绝把当前 raw 快照冒充交付基线。 | A07 后新源码/确切 APK 仍须重新取得设备合同；可信 baseline 只决定必验集合。 |
| **R2-C06** 兼容行为输入覆盖（封存后） | **已实现、软件通过。** 合同测试逐个改变 `es-compat.js`、`compat.js`、`pdf-compat-patch.json` 与 `prepare-recovery-assets.py`，双 flavor 均选中 `recovery-language-rotation`；runtime snapshot、插件 preview 规则亦有测试。 | 选中行为仍须在最终候选上实际验证；规则命中本身不是旧 WebView/应急页面设备通过。 |
| **R2-C04** 双内核上传所有权 | **gate15 软件通过；旧 Low 候选两文件路径有实证。** 双内核共用 ticket/代次/缓存所有权，迟到复制不得交给新页面；原 URI、数量/大小策略保留。gate10 Low 真实 Activity 回调 81/95 字节，两份后端 SHA-256 匹配。 | Standard 与下一候选的实际两文件、导航取消、旋转/迟到矩阵按合同；旧 Low 正向证据不可直接搬到新 APK。 |
| **R2-D01** 资产流关闭 | **已实现、软件通过。** `readAssetText` 用 try-with-resources，失败脱敏记录且保留兼容返回语义，异常流关闭测试纳入全量。 | 无需用正式数据制造读异常；长期资源趋势另归 D03。 |
| **R2-D02** 所选运行方式诊断 | **已实现、软件通过。** 生产诊断实际调用固定只读 `smokeTest()` 报所选运行方式，同时固定 proot 继续检查 Node/npm/Python；进程结果先验退出/超时/截断。 | 最终候选所选模式的 App UID/真实运行诊断仍待设备证据；不能以 shell UID 或固定 proot 结果替代 proroot。 |
| **R2-D03** 宿主资源卡片 | **已实现、软件通过。** 只读当前 app PID、PSS、`/proc/self/fd` 与 task 数；拒读显示 unavailable，不读取 FD 目标或其它进程。 | 最终设备 FD/PSS 多样本采样仍在进行；主进程数字不代表 Gecko/guest 或耗电。 |
| **R2-D04** 最后启动中简易终端关闭 | **gate15 软件通过。** 空标签分支重新启用“新建”，仍经过维护门禁；旧源码已有的缺口不归咎于新 owner。 | Low 旧候选已实测空列表→新建→关闭→再新建；下一候选仍需绑定，普通关闭不冒充慢启动竞态复现。 |
| **R2-D05** 保留数据入口不可达 | **gate15 软件通过。** `NativeDataActivity` 已加正常 UI 的只读导航至 `RetainedDataActivity`，没有扩大恢复/删除权限。 | Low 旧候选已从普通界面进入 11 项保留清单并完成一份加密副本只读摘要核验/旋转；下一候选仍须按确切 SHA 合同，Standard 待验。 |
| **R2-D06** 独立应急语言 | **gate15 软件通过。** 修复分三层：锁定独立应急 tar 的 locale 模块受管前置覆盖与内容证明；Gecko native app 名改为合法且独立的 `dsha_recovery_locale`；原生 READY/只读/凭据状态在显示边界按当前语言重渲染。gate11/12/14 的真机失败和逐层根因保留在 [fixes.md](fixes.md)。 | gate14 Low 网页 English/中文与工具栏双向链已证，但旧 READY 缓存当时仍失败；gate15 修后设备合同 13/14 已通过、no-crash 仍在收尾，A07 后下一候选须重绑 APK/设备证据，Standard 待验。 |

封存后 B05 交叉复核子项的状态如下；它们与 C05/C06、D05 均不加入修复前 12 条计数，也不改写原台账。

| 子项 | 软件状态 | 保留边界 |
|---|---|---|
| **R2-B05-C1** 重复 key | **gate15 软件通过。** active/completed 双份各显示 `DUPLICATE` 物理记录，模糊逻辑 key 禁勾选、导出、恢复，`resolveAll` 明确拒绝。 | 原件均保留；单份物理导出须另定来源身份，不能把冲突记录自动启用。 |
| **R2-B05-C2** 页内存边界 | **gate15 软件通过。** 页候选堆 O(pageSize)，去掉 `fs.list()` 结果第二份全量拷贝/排序。 | `fs.list()` 仍返回单根全部名字，整体峰值不是 O(pageSize)；深页设备内存/耗时待测量。 |
| **R2-B05-C3** 页状态竞态 | **gate15 软件通过。** 请求序号与游标在主线程一致提交页/ViewModel 后才解除 busy。 | 下一候选的快速翻页/旋转仍按合同；展示游标不授权恢复。 |

**当前边界：** B06 父路径替换已在隔离 Android 现场复现并按父 fd 收口；B07s 未发送的敏感计划已在唯一发送前重核，B07v 旧 ADB 票据在撤销返回时失效。它们不代表已发出的请求可回滚，也不代表所有 Root/Shizuku/ADB/ROM/SAF 设备矩阵通过。A02 的 launcher/guest 分离有 shell UID 机制证据与 Low 旧候选 App UID 正常探针，但 Android 6/7、真实 16 KiB 页、全部外部模型及厂商 ROM 未由 gate15 自动覆盖。未知时的围栏、历史原件保留、独立应急根与双浏览器是必要保护。A07 后新软件回执和设备合同、Low no-crash 收尾及 Standard 验收仍由主代理完成；本页不宣布评分达标或 `release` 交付。
