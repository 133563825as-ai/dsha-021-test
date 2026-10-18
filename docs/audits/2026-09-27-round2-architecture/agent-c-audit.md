# 第二轮 C 组初审：网页内核、界面生命周期与构建交付

范围是当前工作树的 Standard WebView、Low Gecko、独立应急网页、上传缓存、受管资产描述和稳定性门禁。先读 `README.md`、`AGENTS.md` 以及上一轮 `final-assessment.md`、`findings-before-fixes.md`、`fixes.md`。上一轮 UI-01—03、BUILD-01—06 均按已修基线处理；以下不重复列报。只做静态审查；没有操作设备、运行 Gradle、修改产品代码或依赖。当前 PowerShell 环境没有 `python` 命令，两个只读描述符检查未执行。

## R2-C01 · P2 · 缺陷 · 应急 Standard WebView 的旧内核兼容脚本无回退

生产链：`RecoveryWebActivity.onCreate()` 创建/复用 `RecoveryBrowserSurface` 并调用 `load()`（`app/src/main/java/com/deepseekharness/app/ui/RecoveryWebActivity.java:54-61`）；Standard 工厂返回 `RecoveryWebSurface`；该类在 `load()` 中仅当 `WebViewFeature.DOCUMENT_START_SCRIPT` 可用才调用 `addDocumentStartJavaScript(..., WebPageScripts.emergencyCompatibility(app), ...)`（`RecoveryWebSurface.java:60-72`）。`onPageFinished()` 只调用 `syncLanguage()`（同文件 `:30`）；`syncLanguage()` 的页面脚本只设置语言并派发事件（同文件 `:87-92`，`RecoveryLocalePolicy.java:45-51`）。因此缺该 WebView 能力时，`WebPageScripts.emergencyCompatibility()` 中的语言选择监听、ES 兼容与网页兼容代码从未执行。正式 WebView 已有页面完成回退（`WebPreviewActivity.java:370-381`），应急分支遗漏。其实际表现取决于旧内核网页所需 API；语言从网页切换无法通过 `DshaLanguage` 监听上报则可从调用链确定。上一轮 DEVICE-02 只验证了正常 Standard 应急页打开，未覆盖这个能力缺失窗口。

修复接口建议：给应急 surface 增加与正式 WebView 共用的 `WebPageScripts` 注入策略。文档起始可用时保持前置注入；不可用时在经过 `RecoveryLocalePolicy.sameOrigin` 核验的主帧完成回调中注入语言/兼容脚本并记录这一回退的能力限制。若锁定 rc2 页面要求脚本必须早于应用脚本运行，应在不支持文档起始的内核上明确进入内核更新/Gecko 选择提示，不能把完成后注入当作等价保证。保留 localhost 独立来源、应急 Cookie 与正式 127.0.0.1 隔离。关闭标准：模拟 `DOCUMENT_START_SCRIPT=false` 的行为测试核对脚本注入与来源拒绝；旧 WebView 真页重载、语言切换/旋转及早期 JS 能力单独验收。

## R2-C02 · P2 · 结构债 · 受管输入闭包仍由多份手写清单维护

生产链：`RuntimeTools.prepare()` 将运行时补丁、内置插件、`client-combo-cache` 安装或修订到 guest（`app/src/main/java/com/deepseekharness/app/runtime/RuntimeTools.java:30-79,128-154,475-494`）；`prepare-runtime-descriptor.py:6-35` 另列资产目录、特定插件文件和 12 个 Java 文件，生成 `runtimeId`；`app/build.gradle:157-175` 再声明 Gradle 输入；`test-runtime-descriptor-inputs.py:11-42` 用 AST 与 Gradle 字符串检查部分 Java 清单。当前已修复上一轮漏掉的四个 Gradle 输入，故**未证实本版描述符错误**。结构问题是生产安装列表、身份输入与 Gradle 追踪没有同一份可审查的事实来源；AST 测试只覆盖形如字面列表的 Java 文件路径，不验证 `RuntimeTools` 实际读取资产、动态 `prependAsset`、插件名单或目录变化。`client-combo-cache` 虽未直接列于描述符脚本，但它已进入 `build-dsh-runtime.py:39-50,631-636` 的归档配方和 `dsh-runtime.bin` 输入证明，不能误报为当前漏项。

有限实现包：由受管资产契约生成器输出机器可读 `managed-runtime-inputs.json`（路径、类别、安装目的地/修订用途），描述符生成器据此计算摘要，Gradle 据此配置文件输入；实际 `RuntimeTools` 安装表与这份契约作双向断言。Java 输入应按可解释的 launcher/数据协议边界显式列出，不能因为位于 `util/` 就声称完整闭包。关闭标准：每类实际读取源各做单文件变更夹具，验证 Gradle 重算、`runtimeId` 改变、候选树内容和健康回执一致；删改非运行资源不应无故触发 Ubuntu 重建。

## R2-C03 · P2 · 结构债 · 交付设备证据未按本版行为变化选择必验项

`verify-stability.py:39-64` 的外部设备证据只要求 `web-ready`、`existing-data-preserved`、`plugins-visible`、`recovery-ready`、`no-crash` 五项，`--deliver` 在 `:194-208` 核对它们与两版 APK 摘要后可替换同版本交付物。本版后加的麦克风授权、页面取消后旧系统回调、双内核实际请求和应急页面语言/旋转不在该检查集合；上一轮报告也明确这些未动态完成。这不是已证实的麦克风安全缺陷，也不否定上一轮已执行范围的 `PASS_FOR_EXECUTED_SCOPE`，但稳定性脚本的静态五项不能作为后续涉及这些能力的全面交付证据。

有限实现包：维护按改动选择的验收契约，构建报告记录所选行为、适用 flavor、结果和不可验证项；证据文件绑定每版 APK 摘要、证书、版本及设备安装身份。新增麦克风代码时要求 Standard/Low/应急来源与取消回调矩阵；改上传时要求两内核真实文件回调及旋转；改语言桥时要求两内核与应急旋转。关闭标准：夹具证明漏掉本次必验项会拒绝 `--deliver`，完整证据通过；无设备覆盖的结果明确保持未完成，不以静态 JUnit 替代。

## R2-C04 · P3 · 结构债 · 两内核上传回调所有权规则重复

正式 WebView `WebPreviewActivity.java:71-101,105-147,359-362,461-486` 和 Gecko `GeckoPreviewActivity.java:55-99,200-203,240-280,303-336` 各自实现 retained pending、导航代次、后台复制与主线程提交/取消；两者共用 `WebUploads.Session` 和 `WebUploadSessionBudget` 的 40 文件/512 MiB 配额。现有代码都核对原会话并对过期结果回退，**未找到可证明的新过期回调缺陷**。但相同状态机分散在 Activity 中，未来修复只覆盖单内核的机会较高，且回调与线程捕获的 Activity 字段不易独立测试。

有限实现包：只抽取 `BrowserUploadCoordinator` 的平台无关所有权状态（会话身份、导航代次、单次请求 ticket、`Batch` 接受/回退、关闭），两个 UI adapter 保留各自的 chooser/prompt 和 URI 交付形式；继续复用现有 `WebUploads.Session`，不重做文件访问。关闭标准：同一参数化夹具跑双 adapter 的选取、取消、导航、旋转、页面关闭、复制迟到与重复结果；真实 WebView `content://` 与 Gecko 文件回调各验证两份文件字节及缓存收敛。

## 明确没有升级为缺陷的边界

`BrowserMicrophone` 的取消保留系统请求槽直到旧结果排空（`BrowserMicrophone.java:63-72`、`MicrophoneRequestGate.java:8-20`），配合每次独立 ActivityResult 注册身份；这是有意的拒绝复用。正式 WebView/Gecko 的上传 `retained` 所有者与导航代次检查、会话预算及孤儿清理已存在；第一轮 UI-01—03 不重报。`prepare-recovery-assets.py` 的物理位置映射与内容身份分离符合应急归档契约，未发现新增交叉读取正式 rootfs 的证据。长会话手机堆、耗电、应急语言旋转和真实多文件回调仍需设备动态证据，静态审查不把它们写成已失败。
