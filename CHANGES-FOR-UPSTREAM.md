# DSHA × DSH 0.2.1-rc.2 适配改动清单（给正式版同事）

这份文件是给维护正式版的同事看的：**改了哪些文件、为什么改、哪些能单独摘走**。
测试包与验证记录见同目录 `TEST-BUILD-NOTES.md`。

- 基线：DSHA `v0.1.7-rc2`（`ac276727`）
- 目标运行时：`@deepseek-ai/dsh 0.2.1-rc.2`
- 交付物：本目录（源码树）、`dsha-0.2.1-adaptation.patch.gz`（相对基线的补丁）、GitHub 仓库 `133563825as-ai/dsha-021-test`

一句话结论：**能只改版本号的地方几乎没有**。0.2.x 把依赖重构成 bundle 形态、客户端 UI 拆包，
运行时内部结构（会话后端、设置持久化、composer 节点模型）也都变了，下面每一类都是被真实故障逼出来的。

---

## 1. 运行时层（必须先做，否则起不来）

| 文件 | 改动 | 为什么 |
|---|---|---|
| `tools/dsh-runtime/package.json` | 128 包 → 323 包：0.2.1 真实依赖闭包的 305 个 `@deepseek-ai/*`，外加 18 个 DSHA 仍需要但 meta 不再拉取的（browser-use / ssh 系列 / computer-use 等） | 0.1.7 的扁平清单与 0.2.1 的树已经不是一回事；漏包会在运行期 `ERR_MODULE_NOT_FOUND` |
| 同上 | 删除 `@deepseek-ai/dsh-invariants`、`@deepseek-ai/dsh-subagent-in-process-driver` | 0.2.1 上游已移除，任何版本号都不存在（`npm 404`） |
| 同上 | `@deepseek-ai/cordis-plugin-group` 1.0.4 → 1.0.5；`cordis` 保持 4.0.5 | 1.0.5 的 peer 是 `cordis ~4.0.5`，配 0.2.1 |
| `tools/dsh-runtime/package-lock.json` | 重新生成 | 新增 `dsh-client-modules` / `dsh-storage-json` / `dsh-session-format-v0…v4` 等补丁要动的模块 |
| `tools/build-dsh-runtime.py` | 会话后端字段锚点重写：0.2.1 的 tracker 改成构造注入（不再 `new JsonlBackendTracker(this.name)`），`dshaSessionHints` 改挂到 `generationFormat` 之后；类文档注释锚点跟着改 | 原锚点匹配不到就抛错，打包直接失败 |
| 同上 | `lexical-claim` 构建期补丁退役 | 0.2.1 把 composer 的 claim token 从「内联样式 + `splitText`」换成 `CommandChipNode` 节点模型，`TOKEN_STYLE` / `registerClaimDecoration` 全树都不存在了 |
| `tools/prepare-runtime-descriptor.py` | `previous` 清单扩到 `0.1.7-rc.1/rc.2`、`0.2.0-rc.1/rc.2` | `dataRead` 决定能读哪些代际的数据；0.2.1 自带 v0→v4 会话迁移链，这个声明成立 |
| `tools/recovery-runtime/lock.json` | `dshVersion` → 0.2.1-rc.2 | 应急运行时和正式运行时是**同一份 runtime 的两个副本**，不一起升的话应急环境读不了新格式数据 |

> 注：`offline-rootfs.bin` / `ubuntu-tools.bin` 是按源码版本锁定的资产，必须取 `v0.1.7-rc2` 官方包里的，
> 跨版本取会被 `base_status_sha256`（rootfs 的 dpkg 状态）和脚本哈希挡下来。DSH 运行时层才是我们替换的部分。

## 2. 客户端补丁层（`app/src/main/assets/*-patch.json`）

16 个清单逐个核对后的结果：

| 结果 | 清单 | 说明 |
|---|---|---|
| 原样命中（12） | `client-combo` 8/8、`composer-enter` 5/5、`conversation-materialized` 1/1、`deepseek-messages` 3/3、`language` 3/3、`office-fonts` 1/1、`pdf-compat` 3/3、`persona` 1/1、`rc1-settings-migration` 1/1、`subagent-navigation` 4/4、`session-interaction` 7/7 | 只把 `dshVersion` 改成 `0.2.1-rc.2` |
| 按新结构重写（3 处） | `plugin-manager-navigation` #4（`installed:` 后多了 `official:`）、#12（`disabled:` 表达式多了 `availability/installTarget` 子句）、`plugin-manager-policy` #1（`node:fs` 导入多了 `globSync/realpathSync/statSync`） | 锚点变了，语义没变 |
| 退役（1） | `lexical-claim` | 见上，`retiredFor` 标注 |
| 保持退役（2） | `agent-preset`、`models-navigation` | `dshVersion` 停在 0.1.7-rc.1 才不会被重新套上，`retiredFor` 补了 0.2.1 |
| 文档修正 | `session-interaction` 的 `module` 字段 | 实际目标早在 Java 里写的是 `dsh-client-ui-workspace`，JSON 字段是旧的 |

另外修了一处**基线自带的脏数据**：`app/src/main/assets/web-integration/es-compat.inputs.json` 里
`compat.js` 的哈希是 `c0e53e…`，而 `v0.1.7-rc2` 里实际文件是 `dc935b…`（上游 main 已改成后者）。
不改这一条，`prepare-standard-assets.py` 一校验就抛「网页兼容代码与依赖锁不一致」。

## 3. 内置插件

| 文件 | 改动 |
|---|---|
| `builtin-plugins/*/package.json`（8 个） | `peerDependencies` 对齐 0.2.1；`cordis` → `^4.0.5`。全部通过宿主 `evaluatePluginCompatibility()` |
| `builtin-plugins/dsh-tool-vscreen/cordis.patch.yml` | **`[]` → 真实 `insert` 行**。这是本轮最关键的缺陷：清单为空 ⇒ Loader 里没有这一行 ⇒ 插件从未装载 ⇒ 所有 `android_vscreen_*` 工具根本不存在。该空清单从 `v0.1.7-rc2` 一直带到 `v0.2.1-build150` |
| `builtin-plugins/dsh-web-mobile/package.json` | 仅放开 peer（去掉 0.2.x 里不存在的 `dsh-client-runtime`，区间加 `>=0.2.0-rc.1 <0.3.0-0`）。**UI 代码没动**，仍是 3.0.3 |

## 4. Android 宿主代码

| 文件 | 改动 | 触发故障 |
|---|---|---|
| `util/Constants.java` | `DSH_VERSION` → `0.2.1-rc.2` | 所有运行时补丁按它做版本门控，不改则全部静默失效 |
| `runtime/RuntimeTools.java` | LAN 设置持久化补丁：模块 `dsh-client-ui-settings` → `dsh-client-ui-settings-general`，锚点改为 `documentController = … isLoopback ? new SettingsDocumentStore(...) : void 0` | 真机报 `LAN_SETTINGS_PATCH_MISMATCH`，环境维护直接失败 |
| 同上 | `applyEnvironment` 增加 `DSHA_SELF_PACKAGE = BuildConfig.APPLICATION_ID` | 容器内脚本需要知道宿主包名（改名包必须） |
| `bridge/AdbBridge.java` | 保活授权按 `BuildConfig.APPLICATION_ID` 传 `--package`；`SCRIPT_VERSION` 20 → 21 | 改名包会把 `WRITE_SECURE_SETTINGS` 授给错误的包 |
| `util/DeviceAppPolicy.java` | 自身保护名单用 `BuildConfig.APPLICATION_ID` | 改名包会漏保护自己 |
| `assets/adb-pair.py` | 新增 `--package`（默认取 `DSHA_SELF_PACKAGE`），`pm grant` 用该值；脚本版本 21 | 同上 |
| `assets/device-shell-policy.py` | 保护名单改为 `SELF_PACKAGE`（读 `DSHA_SELF_PACKAGE`），并**跳过 `pm` 的多用户 `Error:` 噪声行** | 多用户设备「结束应用」100% `POLICY_BLOCKED`（上游 #115） |
| `vscreen/VirtualScreenCore.java`、`VirtualScreenManager.java`、`DeviceShellExecutor.java`、`util/DeviceShellPolicy.java`、`assets/adb-*.sh/py` | 虚拟屏启动链改为携带 `--package`，核心只认传入值 | 改名重签包恒报 `VSCREEN_START_RESULT_UNKNOWN` |
| 同上 | `--launch` 握手成功后 `System.exit(0)` | ColorOS / Android 16 上父进程不退，启动拿到的是 `START_RESULT_UNKNOWN`（#100） |
| `util/AccessibilityDumpBounds.java`（新增）、`DshaAccessibilityService.java`、`vscreen/VirtualScreenAccessibility.java` | 读屏与虚拟屏树共用一条不变量：允许零宽零高，禁止反序矩形 | 反序矩形吃满 200 条配额，有效控件被挤掉（#86 / #143） |
| `data/DownloadsExport.java` | 按扩展名给 MIME，未知一律 `octet-stream` | `xxx.mp4` 被 MediaStore 改名 `xxx.mp4.gz`（#108） |
| `LanProxyService.java` | 401/408/503/400 分别给可自救的提示 | 四种状态共用一句「Request could not be processed」（#98） |
| `app/build.gradle` | `versionCode 183`；`applicationId` 改为 `-Pdsha.applicationId` 可注入（默认 `com.dsh.client`）；`DSHA_CI_TEST_SIGNING=true` 时放过测试证书（默认行为不变） | 测试包要与正式版共存 |
| `res/values/strings.xml` | `app_name` → `DSHA 测试包` | 桌面可辨识 |

## 5. 构建与 CI

| 文件 | 作用 |
|---|---|
| `.github/workflows/build.yml` | 22 步：工具链核对 → 取 `v0.1.7-rc2` 官方包的 rootfs → **用归档自带 `SHA256SUMS`/`packages.txt` 与锁逐项核对** → `npm ci` + 打运行时 → 证明重生成 → 裁剪 rootfs → 硬编码锚点哨兵 → 刷应急锁 → `assembleRelease` → APK 断言 → 发 Release |
| `ci/refresh-pins.py` | 按锁里声明的 `seed` 路径重算应急归档哈希并打印差异（正式发布应改为人工审阅后提交） |
| `ci/check-hardcoded-anchors.py` | 哨兵：Java 里硬编码的源码锚点必须在打好的运行时树里唯一命中。`LAN_SETTINGS_PATCH_MISMATCH` 就是这么漏出去的 |
| `ci/test-signing.keystore` | 测试签名（不可用于正式发布） |

## 6. 怎么摘（三种粒度）

1. **只想让 0.2.1 跑起来**：`tools/dsh-runtime/*` + `tools/build-dsh-runtime.py` + 全部 `assets/*-patch.json` + `Constants.java` + `prepare-runtime-descriptor.py` + `recovery-runtime/lock.json`。
2. **只想要缺陷修复**（不升级运行时）：`VirtualScreenCore`(exit 0) + `AccessibilityDumpBounds` 两处调用 + `DownloadsExport` + `DeviceAppPolicy`/`device-shell-policy.py` 的 `Error:` 跳过 + `LanProxyService` 文案。这几个彼此独立，可单独 cherry-pick。
3. **只想要「改包名也能用」**：`AdbBridge` + `DeviceAppPolicy` + `RuntimeTools.applyEnvironment` + `adb-pair.py` + `device-shell-policy.py` + vscreen 那条 `--package` 链。

## 7. 已知差距（别当成已完成）

- **UI 没升级**：`dsh-web-mobile` 仍是 3.0.3，CSS/JS 针对 0.1.7 的 DOM 写的，0.2.1 下可能有布局规则静默失效。这是本轮最大的体验风险。
- **跨版本升级未真机验证**：会话格式 v0→v4 迁移、设置迁移、应急运行时切换都只做了代码级核对。
- **未修的**：`#89`（重启后 Web 停在旧页，涉及 WebView 生命周期）、`#82`（迁移报错不带路径，上游把 inventory/resolver 一起重写了）、`#123/#113/#147/#140/#142`（虚拟屏权限与输入后端，需要真机与平台改动）。
- **`runtime-descriptor.json` 仓库里的副本是「构建前」状态**：它绑定构建产物的哈希，CI 每轮 `--write` 重算后再 `--check`。
- **应急运行时锁在 CI 里自动刷新**：测试包可以，正式发布必须人工审阅后提交。
