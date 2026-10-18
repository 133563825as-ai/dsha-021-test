# 第二轮实施与追加发现

本页记录封存后的实现、验证、反证与新发现。原始台账和评分不回写。

## 状态

- 修复前审计已汇总，封存摘要由 audit-seal.json 记录。
- 目标技术债低于20，冻结基线49.25；已实施并完成多轮软件验证，追加A05/B05正在gate6复验，设备行为尚未全部闭环。当前独立工程复评仍高于20，不提前宣称达标。
- 当前正式release两份APK保持上一轮已验证字节；新候选通过对应门禁后才替换。

## 第一批实现与验证

- A01：维护锁/所有权/停机顺序进入纯 Java MaintenanceGate；Android MaintenanceCoordinator 组装实际端口，runtime 使用 RuntimeWorkPort 与只读 HostMaintenancePending。仍有 ConfigStore 读取与 ColdInstallDiagnostics 诊断依赖，不声称 runtime 完全不依赖 core。
- A03：生产 Controller 构造点收敛至 get；WebRun 按 generation 持有 launcher/bridge/auth/port，旧回调不能写新的记录。A04 和补充组合测试仍在继续。
- B01：插件卸载 format=3 持久事务，原件保留到领域记录；10项Python回归覆盖安装兼容及卸载中断/回滚/后续修改。B02 host/config 首包已实现惰性历史区、一次迁移核验与 active 复查；其它领域继续。
- B03：敏感一次性确认与可记忆确认共用 completeConfirmation 后置代次/revision核验；交叉审查与5项JUnit通过。关闭的是弹窗待确认期间撤销窗口，已进入执行的动作不能倒退撤回。
- C01：实际 pinned 应急 tar 的 HTML 未含前置补丁，正式 RuntimeTools 的安装后补丁不能作为应急证据。独立恢复 overlay 纳入新 recovery ID 和完整proof，HTML/主线程/PDF Worker修订从锁定原件生成。C04 双内核共用 BrowserUploadRequestState，保留URI、预算及内核适配。细节见 agent-c-fixes.md。
- C02：54项安装表、101项资产与启动输入由签名 managed-runtime-inputs.json 驱动 RuntimeTools、descriptor和Gradle；6项隔离变更测试通过。缺必需安装资产失败，不再静默跳过。纯 UI/APK版本改动不参与运行时身份。
- C03：release-acceptance.json 按基线到候选的实际变化选择必验设备行为，证据绑定合同与flavor。未测/缺项拒绝正式交付。新增 deliver-from 仅在本轮软件回执、源码快照、检查日志和APK字节均未变化时复用软件验证，补设备证据后交付，避免为了拷贝重复全轮构建。
- 首次完整 Gradle 两flavor单测各742项、失败/错误0、既有跳过1，4分26秒；见 first-unit-results.json。0/65/1000完成历史下5次稳定态 Host/Config门禁分别恒定55/60文件系统调用，宿主约3–4ms；这不是手机性能或耗电结论。
- 初次默认Gradle home尝试下载受沙箱网络限制，改用已存在F盘缓存仍遇锁文件权限；获自动批准后使用原离线工具链完成。没有重新下载工具链、生成调试APK或操作正式数据故障注入。

## R2-D02 · P3 · 所选运行方式缺少可达的只读诊断

设备验收准备中核对真实调用点：DiagnosticRepository.collect 原来仅 execAndReadWithProot 检查工具版本，ProotBootstrap.smokeTest 虽能测试配置所选运行方式，但生产 UI 没有调用者。不能把固定 proot 的通过称为 proroot 已验证，也不能用 shell UID 夹具冒充 app UID。补入既有 smokeTest 的生产诊断入口，只执行固定 `/bin/echo SMOKE_OK`，不读取用户文件；保持固定 proot 的 Node/npm/Python 工具核验，并明确两阶段时限。新报告展示所选运行方式与实际探针输出；Bounded Guest 生命周期由已有已审查实现承担，不增加审计后门或额外 APK。

## 集成门禁修正

- gate1（63817486…）：插件恢复跨包误调 context()，改为既有公开 rootfs 路径取得 files 目录，未扩大 Context 接口。
- gate2（6ee64f7f…）：两项运行时历史保护测试失败。归档现在要求新 v2 mapping 的 plan/mapping seal、固定顶层成员及前代字节；额外用户证据保留原位。旧 v1 日志保持原位并兼容完整预检后的显式回退，不伪造旧 seal。目标23项通过。
- gate3（16476a37…）：两版各767项JUnit通过，Low Lint发现 Comparator-only PriorityQueue 构造需要API24；改为等价的 `(11, comparator)` API23兼容重载，未关闭检查。
- gate4（97b8aa42…）：两版767项JUnit、Lint零错误、E7E3签名/ELF/资产/插件/应急等软件门禁通过。Standard/Low 尚有510/444条Lint warning，以未用资源、条件API分支、文案/静态上下文等类别为主，不宣称零warning。此回执对应R2-D02接入前候选，新增诊断后需重新绑定软件/设备证据。
- gate6（9bc6f8a7…）：两版各775项、0失败/0错误/1既有跳过，完整软件门禁通过。1,000条合成历史的11项选择（含1缺失）批量元数据扫描17,017次文件系统调用，10次逐项扫描170,170次；原件内容仍逐项读回核验。Low正式包同签覆盖后受管更新与隔离试运行回到READY，PTY与简易终端均有实际命令输出和关闭证据；这不是所有设备矩阵通过。
- gate7（f558c6f3…）：Standard完整779项仅新增预览清理测试失败，原因是宿主测试调用Android JSONObject的未实现stub（`has`），Low尚未执行。保留失败，不开启returnDefaultValues或跳过断言；将该小段真实结果校验改用现有已锁定Gson的纯逻辑边界后复验，避免测试依赖Android运行时。

## R2-D03 · 资源诊断缺少宿主自身计数

gate6 正式 Low 包的 app PID 已唯一核对，shell 读取其 `/proc/<pid>/fd` 被系统明确拒绝；不使用调试 APK、root 提权或放宽进程保护。现有诊断只报告设备内存和磁盘，缺少应用自身资源计数，无法区分主进程、浏览器与 guest 的资源样本。补充生产“自检与诊断”的只读资源卡片：仅由宿主读取自身 PSS、FD 数量、线程数和 PID，不读取 FD 指向、文件内容或其它进程，不启动后台采样。采样失败明确为 unavailable，不阻断其它诊断。它服务于正常性能排查；真机重复页面/终端操作后复测趋势，不将此主进程样本描述成所有子进程内存或耗电结论。

## R2-D04 · 最后一个启动中简易终端关闭后不能新建

C组先记录在 `agent-c-terminal-note.md`：新建会禁用按钮等待启动状态；如果启动中关闭最后标签，空列表分支跳过 `renderState()`，且旧会话回调正确地被身份检查挡掉，按钮因而可能一直禁用。此路径在旧源码也存在，不归为单owner引入的回归。gate9完成后，空列表渲染显式恢复新建按钮；点击仍经过 `newTerminal()` 原有维护门禁，不改变创建或关闭进程的权限。验证包括编译/Lint和正式包空列表→新建→关闭→再次新建；不把正常关闭行为冒充已制造真实慢启动竞态。

## R2-D05 · 保留清单缺少正式可见入口

在执行最终设备路线时，C组查到 `RetainedDataActivity` 在生产代码中只有类声明和非exported的Manifest注册；全app检索证实只有旧deviceAudit工具直接打开该Activity。现有 `NativeDataActivity` 文案会提到保留旧树，却没有导航调用。此为已确认的功能可达性缺陷，之前的软件测试不能代替生产入口。先在数据与备份/原生救援的普通模式加入“保留副本与旧树”只读导航，独立导入/导出表单保持专用范围。它不授予恢复、启用或删除权限，所有具体动作继续走既有预检和停止屏障。关闭条件是从正式包的正常设置路线进入该页，核对清单范围、分页/刷新和原件只读；不通过外部启动非exported Activity或额外审计APK伪造可达性。

## R2-D06 · P2 · 独立应急网页未接入真实 locale 服务

gate11 Low 同签正式包真机复验中，应急原生栏已为 English，但独立应急网页在完成声明和“稍后配置 API”后仍全中文；原始画面保存在忽略的私有 `low11-recovery-web-en-check.png`，不在公开报告放页面内容。锁定应急归档的 `@deepseek-ai/dsh-client-locale/lib/client.js` 是原始 rc2 字节；现有应急覆盖层仅处理 HTML、PDF 和资源 URL。正式根在 `RuntimeTools.patchClientLanguage()` 用 `language-patch.json` 与 `web-integration/language.js` 把 `window.__DSHA_LANGUAGE__` 接到实际 locale 服务，应急根没有此桥。仅在网页注入全局变量/事件无法保证 rc2 服务消费，因此这是已观察到的功能缺陷。

修复须从独立锁定应急 tar 的精确 locale 模块生成第四份受管覆盖字节，核对 rc2 版本、三个唯一源码锚点、补丁资产摘要、原始与结果 SHA，再并入独立 `recovery-runtime.json` 内容 ID 和最终逐文件证明；不得引入正式设备/插件桥或读取正式 rootfs。宿主夹具要调用锁定 locale 模块，验证原生→网页 EN/ZH 双向同步、网页选择反馈、取消监听和无效来源拒绝；双 flavor APK 要核对第四覆盖字节。真机关闭标准是正式应急页在 English/中文切换与旋转后实际文字随 locale 服务更新，且正式/应急不同来源不能互授语言消息。gate11 原回执只代表修复前候选，不回写成通过；改动后重新软件门禁并绑定新 APK 的设备证据。

实现进度：`recovery_runtime_overlay.py` 只从 pinned rc2 tar 的固定 `dsh-client-locale/lib/client.js` 取原字节，要求三个唯一锚点与 `language-patch.json` 的确切模块/版本及唯一 `web-integration/language.js` prepend，生成第四覆盖字节；独立描述符另绑定五份 HTML/PDF/语言输入资产 SHA。`RecoveryRuntime` 解压后先核原始文件和签名资产/最终覆盖摘要，再发布并验整树，新恢复 ID 为 `23b2cd039b2520608730136fb16dfbf7354f84031ba1753354102513f958de1e`；正式 rootfs、设备桥和插件桥没有作为应急依赖。Gradle 生成输入显式追踪两个语言资产，交付行为规则也将其单独变更列入双 flavor 应急语言验收。隔离生成、真实锁定 locale 模块的 EN/ZH 双向/无效值/监听取消与异源 relay 拒绝夹具通过；应急资产测试12项（1项宿主软链接条件跳过）、APK映射/输入摘要测试9项、官方 locale 行为3项、Web UI 来源宿主夹具和变更选择11项通过。Gradle 两版正式包和手机 English/中文实际渲染仍待主代理重建验收，不以这些宿主结果代替 D06 真机关闭。

gate12 软件门禁与 Low 正式包安装均通过，原生栏为 English，但应急网页声明、API 弹窗和首页仍为中文。独立私有 logcat `low12-locale-all-logcat.txt` 给出本轮唯一相关 Web Content 错误：`runtime.connectNative` 的应用名 `dsha-recovery-locale` 不满足 Gecko 的 `^\w+(\.\w+)*$`，报错位置为 `locale-relay.js:8`。Activity 树确认为 `org.mozilla.geckoview.GeckoView`。因此此轮失败发生在应急扩展建立 native port 之前，不能用 overlay 内容正确或首页可见推断语言消息已到达。将应急固定 native app 名改为合法、独立的 `dsha_recovery_locale`，保持扩展 ID 和 localhost 来源范围，递增内置扩展版本刷新缓存；宿主夹具须按真实 Gecko 语法拒绝原连字符名称并核对 Java/JS 完全一致。关闭仍须新正式包的应急真机 EN/ZH 页面文字证据，gate12 回执保留为修前失败证据。

gate14 Low 正式包已证实 Gecko 应急页面的英文声明、API 页、首页及横屏，以及网页 Settings 选中文后原生 `RecoveryWebActivity` 工具栏立即变中文、同一应急 session 仍存活。返回 `RecoveryActivity` 时标题与按钮已中文，但顶部 READY 状态整段仍是之前快照生成的英文（私有 `low14-recovery-native-zh-return.png`）；正常点击 Stop 后新发布的 STOPPED 状态立即显示中文（私有 `low14-emergency-stopped.png`），进一步限定为旧 READY 文案缓存。根因是 `RecoveryController.Snapshot.detail` 保存发布时的 `UiText.choose` 结果，`RecoveryActivity.refresh` 直接显示缓存文案且刷新 key 不含语言。仅在该状态显示边界按 `state` 和完整应用生成模板重渲染 READY/READY_READ_ONLY 及已知凭据提示；不翻译未知失败、原始进程输出或日志。补正常 READY、无凭据和只读状态在 EN/ZH 间切换回归，新正式包再核顶部状态随语言变化；gate14 的网页双向链证据保持有效。
