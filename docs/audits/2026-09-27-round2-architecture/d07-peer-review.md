# D07 跟随系统语言：有限只读交叉复核

范围仅 [follow-system-observation.md](follow-system-observation.md)、`SystemLanguage`、`DshaApp.onCreate`、`UiLanguagePreferenceTest` 与主代理所述交付行为规则；未改源码、运行 Gradle 或操作设备。

已证实的设备触发是 gate16 Standard 上全局 configuration 首项 `zh-CN`、同包 per-app locales `[en]`，DSHA 却显示「Follow system · English」。这三个读数共同确立产品错误；`persist.sys.locale` 或 Files 文案单独不能做到这一点。新实现的意图正确：`DshaApp.onCreate()` 在 `LanguageController.apply()` 前用 Application 调 `SystemLanguage.initialize(this)`；`localeManagerSystemTag` 先从系统服务取 `getSystemLocales().get(0)`，不读本应用 `getApplicationLocales()`，缺服务/低 API 才回退原资源与默认 Locale。对本机 SDK 37 `android.jar` 用 `javap` 实核 `LocaleManager.getSystemLocales(): LocaleList`、`Context.getSystemService(Class)` 和 `LocaleList.get(int): Locale` 均存在。`SystemLanguage` 无 Android import，反射 `ClassNotFoundException` 等被限定捕获，API23–32 保留原路径。Binder/反射读取发生在进入 `initializeFromSystemTag` 的类监视器之前，未见此链新增持锁 Binder 死锁。顺序上的 Provider 暂值→Application 权威值→后续 apply 不覆盖由新增 JVM 断言覆盖；主代理的行为规则可把下一候选的 `native-language-follow-system` 缺证列为交付阻断，规则通过不等于正式设备渲染通过。

**发现一个并发发布窗口，建议在 gate17 前最小修正。** `tag()` 在 `cached == null` 时先调用 `detect()`，最后无条件写 `cached = detected`；`initializeFromSystemTag` 虽同步并以 `initialized` 阻止重复初始化，却不与 `tag()` 的最后写入同步。可达交错是：早期 Provider/其它线程的 `tag()` 读到 null 并进入慢探测；Application 线程从 LocaleManager 取得全局 `zh-CN`，写 `cached=zh-CN; initialized=true`；早期线程随后把 app-en 的探测值写回 `cached`。此时“权威只覆盖一次”被过期暂值反覆写，后续 `LanguageController.apply()` 仍可能得到英文。当前测试只覆盖**顺序**的早期读取，不能证明这个交错安全；没有设备日志表明它就是 gate16 的实际触发分支，因此分类为源码已证的条件性竞态，而非声称已动态复现。有限关闭方式是在 `tag()` 探测后与初始化共用同一短锁，发布前重读 `initialized/cached`；或等价的原子状态判据。用闩锁把早期探测暂停在写入前，先完成 global-zh 初始化，再放行旧 app-en，断言最终 `tag()` 仍为 zh。不得让锁覆盖 LocaleManager Binder 或资源探测，也不要改 zh/en/system 的产品映射。

最终结论须以修复后的新双 flavor 软件回执及同签正式候选的实际系统中文/per-app 英文→选择 Follow system 显示中文、语言切换不停止 Web/终端为准。gate16 的旧候选设备错误和已经完成的 14 行为不能替代新 APK 的第 15 项合同；Android6/7 等未测矩阵仍单列。

## 并发窗口修正后的定向复核

C 组已将 `tag()` 的探测交给 `tagWithDetector`：探测/反射在类锁外执行，进入短锁后先检查 `initialized`，再复读 `cached`，只有尚无权威值且无别的暂值时才发布本次暂值。`initializeFromSystemTag` 也先在锁外完成可能较慢的旧来源探测，在同一短锁内再次检查 `initialized` 后写 `cached` 和 `initialized=true`。因此“早期 app-en 探测暂停→Application 发布 global-zh→旧探测恢复”的旧值**不能再覆盖**权威缓存；JVM 新增闩锁测试实际强制这一顺序并核返回和最终 `tag()` 都是 `zh-CN`。C 组报告该定向 JUnit 共 12 项通过，本次只读复核没有重跑。

锁顺序未引入 Binder/资源探测持锁：反射及 `LocaleManager` 查询均在监视器外，监视器内只有读写字符串/布尔状态。`cached`、`initialized` 为 volatile，权威发布在同一锁内且 `tagWithDetector` 进锁后复读，未发现同一竞态的剩余永久覆盖路径。极窄情形下，已有暂值的 `tag()` 可在权威发布前读出并返回旧语言；这是初始化完成前已经准入的读取，不会回写缓存，`DshaApp` 先初始化再 `LanguageController.apply` 的主链不受该瞬时返回影响。仍须 gate17 编译/全量测试及新正式 APK 的第 15 项设备行为证明，不能用本复核替代交付。

## 阶段 2C：Application 期平台应用语言写入

gate17 的受限冷启动已把 `SystemLanguage` 锁存纠正为全局中文，但同包平台 per-app locale 仍 `[en]`，XML 英文；真实 AppCompat 1.6.1 字节码在没有 Activity delegate 时返回且不保存待写值，故阶段 1 的纯策略正确仍不足以在 Application 期改平台 locale。此次限定改动使 `LanguageController.apply()` 仍从 `ConfigStore.getUiLanguage()` 取得**已解析**的 zh/en，继续更新 `UiText`/Application 资源；`AppLocaleDispatch` 在 API33+ 直接调 `LocaleManager.setApplicationLocales(LocaleList(resolved))`，API23–32 保持 AppCompat 原路径。`system` 偏好仍存在配置中，不作为空列表/第三种语言传给平台。`DshaApp` 先 `SystemLanguage.initialize(this)` 再 `LanguageController.apply(this)`，所以 API33 写入的是 LocaleManager **system** 来源决定的实效值，不从当前 per-app `[en]` 倒推。纯分发器无 Android import，3 项测试覆盖无 Activity delegate 时平台先写、低 API 兼容路径及非法实效语言拒绝。

本机 SDK37 `android.jar` 的 `javap` 实核 `LocaleManager.setApplicationLocales(LocaleList)` 与 `LocaleList(Locale...)`，API33 专用引用置于 `Api33` 内部类，仅被 SDK 门禁的 platform writer 调用；未见低 API 主路径会触发该类。`getSystemService(LocaleManager.class)` 为 API33 正常服务；若它异常返回 null，当前代码明确抛 `LOCALE_MANAGER_UNAVAILABLE`，不会静默声称平台语言已写入，但该异常情形仍需以设备结果判断。未发现这几行新增死锁或把 `getApplicationLocales` 混成系统来源的路径。**静态复核无阻断结论**；gate17 旧包混语与已做的 14 项设备行为不能替代 gate18 新正式包的冷启动 global-zh/per-app-旧-en→平台 zh、XML/动态文案同语及第 15 项合同。
