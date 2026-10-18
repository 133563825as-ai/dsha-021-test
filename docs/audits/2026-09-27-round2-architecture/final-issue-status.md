# 最终问题台账（最终gate19已交付）

最终候选app\build\stability-acceptance\637b5b65-ec84-4fd0-bd0c-d2fdfe64d5a9\delivery-db0474b7-7e0b-4b44-9759-ed1101e0eecc.json，设备合同gate19-device-evidence.json（私有证据，SHA-256 06886d090059feadb6a4a03c33cca429ef1c456007425013b1df987145e9c6f1）。软件通过、真机行为与未测边界分别记录，不能将旧候选成功迁移到新APK。修复前严重级别、触发条件、根因和证据见封存findings-before-fixes.md；封存后新增项先记录于fixes.md/专项文件。本表只关闭最终候选已验证的实施项，历史失败候选不充当通过证据。

| ID | 实际改变 | 关闭依据/保留边界 |
|---|---|---|
| A01 | MaintenanceGate单owner和顺序，MaintenanceCoordinator平台适配；真实维护写者迁移 | 组合和失败顺序断言；正式维护、终端关闭、重启。身份不确定仍阻断。 |
| A02 | 有界proroot独立session监督、出生身份、持久active记录 | 旧launcher/guest分离隔离复现、API23原生构建及正式AppUID所选proroot探针。ROM矩阵未全验。 |
| A03 | 唯一HarnessController和每generation的WebRun | 旧回调拒绝覆盖新run；两正式包正常停止/重启。 |
| A04 | ManagedRuntimeAssets接管候选准备，GuestCommandOutcome区分退出/超时/截断/输出 | 真正调用者先验完成；预览取消严格单条PLUGIN_RESULT及失败保留。旧文本门面仍适配，不宣称整个bootstrap拆完。 |
| A05 | ConfigStore同次runtime快照，经RuntimeHostPorts注入所有相关执行入口 | 跨调用配置/工作区和argv/env回归；runtime→core/root选定显式依赖12/5→0是佐证而非全部无环证明。 |
| A06 | SimpleTerminal READY绑定PID+出生时刻，按身份逐成员停止 | 真实生产parser及会话测试，非调试正式包命令/关闭。未知退出保留标签和工作锁。 |
| A07 | Owner给UI独立只读View，不能cast回可变TerminalTabs | 一张原表、永久ID/显示号/失败占位不变，JUnit快照/实时状态+静态负向守卫+正式终端行为；Tab.value仍是原会话引用，非深冻结。 |
| B01 | 插件卸载format3持久计划与回滚，提交后不回滚后来修改 | 合成多窗口进程中断及回滚失败夹具；正式个人插件不故障注入，原件继续保留。 |
| B02 | active与有实物证明的completed分离；七域只读pending清单 | 0/65/1000历史稳定态不逐项扫全部完成记录；未知/修改过/v1无充分证明原件不自动移删。 |
| B03 | 原生确认返回后重核generation/revision | 软件敏感与普通确认竞态；正式页面允许→撤销→重问→拒绝，不外推全部特权渠道。 |
| B04 | RetainedCatalogue显式分页/总数/游标，普通UI导航可达 | 取消累计256限制但保留活动异常门禁；真机小数据页及只读检查，1000深页为宿主夹具。 |
| B05 | 多选resolveAll合并扫描，单页堆、主线程一次提交 | 1000记录/11选择17017FS调用对照170170；状态过期拒绝，显式全目录成员读取成本仍在。 |
| B05-C1 | 重复key保留两份DUPLICATE并拒绝模糊操作 | 不选一份猜恢复；单份物理救援身份方案需另定需求。 |
| B05-C2 | 去掉fs.list结果额外全量复制/排序 | 页候选O(pageSize)，总峰值仍含单根所有文件名，不虚报总O(pageSize)。 |
| B05-C3 | 请求游标/序号和ViewModel在主线程一致提交后解busy | 旧页面任务不会覆盖新请求；展示结果不能授权恢复。 |
| B06 | DocumentsProvider创建/改名/删除复用父fd相对NOFOLLOW操作；新增内部控制目录隐藏 | 真实Android隔离旧4操作可重定向、新4操作拒绝等53断言及FD50→50；两正式包合成空文件/目录创建改名删除。非空SAF往返未在本轮手机重新测。 |
| B07s | 敏感ADB计划在唯一发送前重新取得并比较精确对象；统一脚本标记19 | 旧实现4失败→新通过；ADB flow31+vscreen3软件门禁。已发出请求不自动重放或追溯撤销。 |
| B07v | 虚拟屏撤销调用线程立即推进epoch/撤销旧票据，旧cleanup不清新代 | 旧真实manager闩锁复现及新JUnit；不宣称检查后已准入的socket字节能原子追回。 |
| C01 | 独立应急归档生成前置HTML/PDF/Worker兼容，绑定独立内容身份 | 真实锁定模块与APK门禁、两内核应急实际入口。旧WebView缺失能力与PDF设备矩阵边界保留。 |
| C02 | 同一签名managed-runtime-inputs供安装、描述符、Gradle | 增删输入/版本/摘要负向夹具及最终两APK；未升级DSH/Ubuntu基础。 |
| C03 | 可信旧交付回执→源码变更→必验设备行为；所有证明绑定字节 | 缺行为/错源/错日志/错APK拒绝交付；最终实际设备合同全部通过后方写release。 |
| C04 | WebView/Gecko共享上传ticket、代次、缓存所有权 | 软件迟到复制/取消时序与真实2文件哈希/旋转/导航；不把即时小文件代表所有大文件竞态。 |
| C05 | baseline_from_receipt拒绝未交付/篡改snapshot作基线 | 正负验收合同用例，保留原已验证基线。 |
| C06 | 兼容脚本/locale/生成器等变化会选择应急语言设备合同 | 软件输入命中与最终所选设备行为；规则命中本身不是真机通过。 |
| D01 | readAssetText明确关闭流 | 异常关闭断言，原错误和兼容语义保留。 |
| D02 | 诊断实际执行用户所选运行方式固定SMOKE探针 | 两正式包AppUID proroot SMOKE_OK；Node/npm/Python仍固定proot检查，二者不混报。 |
| D03 | 宿主当前PID/PSS/FD/线程只读资源卡 | 两版本同路径多样本；不包含独立浏览器/guest，不等于长期功耗验收。 |
| D04 | 最后简易标签关闭后重新启用新建 | 空列表→新建→快速关闭正式行为及JUnit；未用普通关闭声称全部启动竞态。 |
| D05 | NativeDataActivity加入保留副本与旧树入口 | 两正式包从正常UI进入、只读摘要检查和旋转，未扩大恢复/删除权限。 |
| D06 | 应急真实locale模块覆盖、合法Gecko native app名、原生READY随语言重渲染 | 历史gate11/12/14失败保留；最终双内核英语/中文/旋转/正常停止。官方窄屏设置排版仍拥挤，横屏可用。 |
| D07 | API33真实systemLocale源、缓存发布权威；Application直接写平台locale避免空Activity delegate跳过 | globalzh/appEN冷启动和活会话English→FollowSystem需新合同；锁存/闩锁/分发软件用例；最终两包冷启动平台en→zh、XML/动态文本一致，活会话PID/Web代次保留；API23–32沿旧路径，当前手机不代表旧API真机。 |

以上实施项按表内限定关闭条件标为本轮关闭；不表示所列外部矩阵均已验证。最终两版软件及15/15设备行为通过，E7E3正式包已写入release，实物摘要再次一致。以下保留项不再混入已修缺陷：整体依赖环（有待后续真实变更评估的结构余债）、目录名规模成本（待测量驱动）、Tab.value浅只读（现有会话API边界）、在途请求（通信语义）、应急窄屏设置拥挤（已观察的非阻断界面限制）、设备/服务/长期性能矩阵（未验证）。当前没有留在队列中的可达严重数据安全或权限绕过问题。

剩余项不继续无限扩张：依赖图未整体无环、显式历史深页目录名成本、浅只读session引用、已在途请求、上游应急窄屏排版和兼容矩阵/长期性能未知如实保留。它们不以改名或“接受风险”自动换取降分；是否重构取决于后续真实变化/测量/产品需求。没有未经验证的高风险数据删除、权限放宽或假成功作为已关闭项。
