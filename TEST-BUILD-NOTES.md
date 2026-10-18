# 测试包说明：DSHA 0.1.7-rc2 → DSH 0.2.1-rc.2

这份文件是给测试同学和后续维护者的，不讲排场，只讲：改了什么、为什么这么改、验到哪一步、哪里还有坑。

## 0. 这个包是什么

| 项 | 值 |
|---|---|
| 基线源码 | DSHA `v0.1.7-rc2`（`ac276727`） |
| 运行时 | `@deepseek-ai/dsh 0.2.1-rc.2` |
| 变体 | `standardRelease`（`debuggable=false`） |
| 版本 | `versionCode 181` / `versionName 0.2.1-rc2-dsh0.2.1-rc.2-test` |
| 应用名 | **DSHA 测试包** |
| 签名 | 仓库内 `ci/test-signing.keystore`（测试签名） |
| ABI | arm64-v8a |
| 构建 | GitHub Actions，`.github/workflows/build.yml`（2026-10-18 全绿，20 步） |
| 产物 | 301 MB，`release` 标签下可直接下载（免登录） |
| 复现性 | CI 打出的 `dsh-runtime.bin` 与本机独立构建哈希一致：`1ccfdef0aa7c5436…` |

**这个包不能覆盖安装正式版**：正式版是历史 E7E3 证书签的，测试签名对不上，系统会拒绝覆盖。想在同一台机器上试，只能先卸载正式版（会丢数据，先备份）或者换一台设备。

## 1. 虚拟屏：根因和修复

**现象**：虚拟屏相关的 `android_vscreen_create / status / launch / see / tap / swipe / touch / type / key / tree / node / editor / close` 这些工具**根本不存在**，AI 侧看不到任何虚拟屏能力。

**根因**：`app/src/main/assets/builtin-plugins/dsh-tool-vscreen/cordis.patch.yml` 内容是一个空数组 `[]`。

内置插件靠这个清单往 Cordis Loader 里插一行：

```yaml
- insert:
    - id: dsh-tool-vscreen
      name: dsh-tool-vscreen
```

清单为空 = 没有这一行 = 插件从未被装载。七个内置插件里只有它是空的，其余六个都有真实内容。这个空清单从 `v0.1.7-rc2` 一直带到 `v0.2.0-rc2`、`v0.2.1-build150`，上游到最近才在 `main` 补上。

**修复**：按同目录其它插件的写法补齐 insert 行，并把它的 `peerDependencies` 对齐到 `0.2.1-rc.2`。修完用宿主自己的 `evaluatePluginCompatibility()` 复核，八个内置插件（含 `app-integration`）全部「兼容」。

**顺带修掉的两个虚拟屏缺陷**（都来自上游 issue）：

- **#100**：`VirtualScreenCore` 的 `--launch` 模式在握手成功后没有 `System.exit(0)`。部分 ROM（ColorOS / Android 16）在 `systemMain attach` 之后会留下非守护线程，JVM 不退出，启动命令永远等不到退出码，App 侧只能报 `VSCREEN_START_RESULT_UNKNOWN` —— 这些机型上虚拟屏等于全废。已确认常驻服务是 `launch()` 里 `ProcessBuilder` 拉起的**独立子进程**，父进程退出不影响它，所以补 `System.exit(0)` 是安全的。
- **#86 / #143**：读屏和虚拟屏树把「反序矩形」（`right < left` 或 `bottom < top`）的节点也算进 200 条配额。这些矩形没有任何一条边能用来定位，报出去就是错坐标，还会把真正有用的控件挤出配额。新增 `AccessibilityDumpBounds`，主屏 dump 和虚拟屏 tree 共用同一条不变量：允许零宽零高，禁止反序。

**第三个虚拟屏故障（真机实测才暴露）**：`VirtualScreenCore` 把包名写死成 `com.dsh.client`，用它反查自己的 APK 路径来比对 `java.class.path`。只要发行包被改名重签（例如 `com.dsh.clienu`），这一步必然抛 `SecurityException("INVALID_CLASSPATH")`，核心进程立刻退出，App 侧拿到的仍是 `VSCREEN_START_RESULT_UNKNOWN`——现象和 #100 一模一样，但根因完全不同。

现在的做法：包名由 App 在启动命令行里用 `--package` 传入（取 `context.getPackageName()`），核心进程只信这个值并用它查 PackageManager；不写死任何包名。同步改了三处校验（Java 的 `DeviceShellPolicy`、容器的 `device-shell-policy.py`、`DeviceShellExecutor` 的 APK 解析），并把设备脚本版本从 19 提到 20，保证覆盖安装后旧策略脚本会被重新注入。

> 注意：`app/build.gradle` 的 `applicationId` 仍然是 `com.dsh.client`。如果你想改包名安装，请整体改 `applicationId` 后重新构建；**不要只改 APK 清单再重签**——那会让 `BuildConfig.APPLICATION_ID` 与实际包名不一致。虚拟屏这一处已经改成运行时取值，但 `adb-pair.py` 的 `pm grant` 仍指向 `com.dsh.client`。

## 2. 运行时升级

### 2.1 依赖清单不是「改个版本号」

DSH 0.2.x 把依赖重构成了 bundle 形态：`dsh-base`、`dsh-web-app`、`dsh-tool-*`、`dsh-experimental-*-bundle`，客户端 UI 也从几个大包拆成约 60 个 `client-ui-*` 包。0.1.7 的 128 个扁平包和 0.2.1 的依赖树已经不是一回事。

所以清单按 0.2.1 的**真实依赖闭包**重建：

- 核心 305 个（闭包里每个包的实际版本，含 `dsh-client-modules`、`dsh-storage-json`、`dsh-session-format-v0…v4` 这些补丁要动的模块）；
- 保留 18 个 DSHA 额外需要、但 0.2.1 元包不再拉取的（`dsh-browser-use`、`dsh-ssh` 系列、`dsh-computer-use` 等）；
- `@deepseek-ai/cordis-plugin-group` 1.0.4 → 1.0.5；
- `dsh-invariants`、`dsh-subagent-in-process-driver` 在 0.2.1 已被上游移除，从清单里删掉（全仓库无引用）。

平台专用包（`*-darwin-arm64`、`*-win32-x64` 等）不进显式清单，由 npm 的 `os/cpu/libc` 规则处理。

### 2.2 八处源码级补丁全部重新锚定

`tools/build-dsh-runtime.py` 会在打包时改写运行时源码（这些改动直接关系数据安全，不能失效）：

| 补丁 | 作用 | 0.2.1 的适配 |
|---|---|---|
| `fs-local` / `session-persistence-jsonl` / `attachment-local` | 把 `link` 换成受管的排他发布 `dsha-runtime-fs`，避开 proroot/SELinux 下 link 变成悬空链接 | 原子发布导入结构未变，锚点原样命中 |
| `session-persistence-jsonl` | proroot 下新发布会话目录可能不出现在父目录枚举里，用受管 cwd 提示直达日志路径 | **改**：0.2.1 的 tracker 改为构造注入（字段里不再 `new JsonlBackendTracker`），提示字段改挂到 `generationFormat` 之后 |
| `storage-json` | proroot 的 `getdents d_type` 可能报 DT_UNKNOWN，改成直达已声明表目录 + 直接记录提示 | 锚点原样命中 |
| `client-modules` | 网页脚本拼接路径改写 | 8/8 命中 |
| `session-format-v2-to-v3` | 给 V2→V3 阶段包一层 rc.1 旧形状归一化 | 锚点命中 |
| `llm-deepseek` | DeepSeek Messages 序列化兼容 | 3/3 命中 |
| `settings` | rc1 设置迁移 | 命中 |

打包器每处锚点都要求「唯一命中」，不唯一就抛错——所以「能打出包」本身就是一次结构核验。

### 2.3 客户端补丁：16 个清单逐一核对

| 结果 | 清单 |
|---|---|
| 原样命中（12 个） | `client-combo` 8/8、`composer-enter` 5/5、`conversation-materialized` 1/1、`deepseek-messages` 3/3、`language` 3/3、`office-fonts` 1/1、`pdf-compat` 3/3、`persona` 1/1、`rc1-settings-migration` 1/1、`subagent-navigation` 4/4、`session-interaction` 7/7（实际目标是 `dsh-client-ui-workspace`，JSON 里的 `module` 字段写旧了，已修正为文档准确） |
| 按新结构重写（3 处） | `plugin-manager-navigation` #4（`installed:` 后面多了 `official:` 字段）、#12（`disabled:` 表达式新增 `availability/installTarget` 子句）、`plugin-manager-policy` #1（`node:fs` 导入新增 `globSync/realpathSync/statSync`） |
| 退役（1 个） | `lexical-claim`：0.2.1 把 composer 的 claim token 从「内联样式 + `splitText`」换成了 `CommandChipNode` 节点模型，`TOKEN_STYLE`、`activeToken`、`registerClaimDecoration` 全树都不存在了，原补丁没有可锚定的语义 |
| 本来就退役 | `agent-preset`、`models-navigation`（`dshVersion` 停在 0.1.7-rc.1，保持不生效，`retiredFor` 补上 0.2.1） |

### 2.4 内置插件

八个内置插件（`device-shell-guide`、`task-notifier`、`status-overlay`、`web-mobile`、`computer-use-android`、`auto-review`、`tool-vscreen`、`app-integration`）的 `peerDependencies` 对齐到 0.2.1，全部通过宿主兼容性判定。`dsh-web-mobile` 只改了 peer 元数据（去掉 0.2.x 里不存在的 `dsh-client-runtime`，放宽区间），**UI 代码没有动**。

## 3. 数据与迁移

- `runtime-descriptor.json` 的 `dataRead` 从 `[dsh-0.1.7-rc.1]` 扩到 `[0.1.7-rc.1, 0.1.7-rc.2, 0.2.0-rc.1, 0.2.0-rc.2]`：0.2.1 自带 v0→v4 会话格式迁移链，这个声明是站得住的。
- `tools/recovery-runtime/lock.json` 的 `dshVersion` 升到 0.2.1-rc.2；应急运行时和正式运行时是**同一份** runtime 的两个副本，不一起升的话，应急环境读不了新格式数据。两个归档哈希由 `ci/refresh-pins.py` 在构建时按实际产物刷新并打印差异（正式发布流程里应当由人审阅后提交，测试包自动刷新）。
- `managed-runtime-inputs.json` / `runtime-descriptor.json` 由 `--write` 重新生成，Gradle 的 `--check` 只做核验。

## 4. 顺手修的其它缺陷

| # | 现象 | 根因 | 本轮 |
|---|---|---|---|
| 100 | 虚拟屏在 ColorOS/A16 恒报 `START_RESULT_UNKNOWN` | `--launch` 父进程不退出 | 已修，1 行 |
| 115 | 多用户设备「结束应用」100% `POLICY_BLOCKED` | `pm` 输出里混入别的用户 `Error: ... user 666`，解析层整体拒绝 | 已修，跳过 `Error:` 噪声行 |
| 108 | 导出 `xxx.mp4` 落盘成 `xxx.mp4.gz`，相册不索引 | 非 txt/zip 一律 `application/gzip`，MediaStore 按 MIME 补后缀 | 已修，按扩展名给 MIME，未知一律 `octet-stream` |
| 86/143 | 读屏与虚拟屏树被反序矩形吃掉配额 | 只按「有文字/可点」筛选，不看矩形是否有序 | 已修，两处共用同一判据 |
| 98 | 局域网 401/408/503 共用一句「Request could not be processed」 | 失败文案不区分状态 | 已修，按状态给可自救的提示 |

## 5. 验证做到哪一步

**本地已做（轻量、无编译）**

- 16 个客户端补丁清单的锚点审计（含串联应用后的二次核验）；
- 八个内置插件对 0.2.1 的 `evaluatePluginCompatibility` 判定；
- 运行时钩子的宿主依赖核对：`SessionFormatUnsupportedMigrationError`、`node-addon-system/flock`、`koffi` 在 0.2.1 下都存在；
- 打包器对 0.2.1 安装树端到端跑通一次（文件数、arm64 二进制清单、产物哈希）。

**CI 已做**

- 工具链核对（Gradle 9.3.1 / AGP 9.1.1 / NDK 26.3.11579264 / platform android-37.0 / build-tools 36.0.0）；
- 从官方 APK 取 `offline-rootfs.bin` 与 `ubuntu-tools.bin` 并打印哈希；
- `npm ci`（linux/arm64/glibc）+ 生成 `dsh-runtime.bin`；
- 重新生成描述符与受管输入证明、裁剪 rootfs 内嵌新运行时、刷新应急运行时锁；
- `assembleStandardRelease` + APK 校验（`debuggable` 必须不存在、`versionCode 181`、只含 arm64-v8a、证书指纹比对）。

**CI 已实际验到的硬事实（都是构建期断言，不是推断）**

- APK 内 `assets/dsh-runtime.bin` 的 `dsh-runtime.version` = `0.2.1-rc.2`；
- APK 内 `assets/builtin-plugins/dsh-tool-vscreen/cordis.patch.yml` 已含 `insert` 行（不再是 `[]`）；
- `aapt2` 确认 `debuggable` 不存在、`versionCode 181`、`native-code` 仅 `arm64-v8a`；
- `apksigner` 确认签名证书指纹 = 仓库内测试证书；
- 宿主侧冒烟：`tools/prepare-test-runtime.py` + `test-recovery-agent.mjs` 在真实 0.2.1 运行时树上通过；
- 离线资产契约：`ubuntu-tools.bin` 归档内的 `SHA256SUMS` / `packages.txt` 与 `packages.lock.json` 逐项一致，
  rootfs 的 `var/lib/dpkg/status` 哈希与锁里的 `baseStatusSha256` 一致。

**没做（必须真机）**

- 装机运行：启动、Web 就绪、插件可见、虚拟屏工具真的出现在工具列表里；
- 从 0.1.7 / 0.2.0 覆盖升级后的会话与设置迁移；
- #100 在 ColorOS / Android 16 上的真实复现与修复确认；
- #86/#143 在真实反序矩形节点上的表现。

## 6. 风险与遗留

1. **UI 没有跟着升**（按你的要求「UI 不用管」）。内置的 `dsh-web-mobile` 还是 3.0.3，它的 CSS/JS 是针对 0.1.7 的 DOM 写的。0.2.1 的客户端结构变化很大，可能出现：某些手机档布局规则静默失效、个别弹层/返回行为不对。**这是本轮最大的体验风险**，元数据已经放开让它能加载，但适配本身没做。
2. **跨版本升级未在真机验证**。会话格式 v0→v4 迁移链、`rc1-migration`、`rc1-settings-migration` 都是按代码推断可用，没有真机数据证明。
3. **没修的缺陷**：`#89`（重启后 Web 停在旧页，涉及 WebView 生命周期，补丁已备但需真机）、`#82`（迁移 `IsADirectoryError` 报错不带路径，上游把 inventory/resolver 一起重写了，只补判据修不好）、`#123/#113/#147/#140/#142`（虚拟屏权限与输入后端，需要真机与平台改动）。
4. **测试签名**：不能覆盖安装正式版；`versionCode 181` 高于线上 173，同签名场景下才谈得上覆盖。
5. **应急运行时锁在 CI 里自动刷新**：测试包这样做没问题，正式发布前应改为人工审阅后提交。

## 7. 怎么复现 / 回滚

```bash
# 复现构建（在 Actions 里，本地不要跑，会烫手机）
Actions → build-test-apk → Run workflow
# 首次运行后把日志里的两个 sha256 填进输入，后续即强制校验

# 回滚：整棵树回到基线
git revert --no-commit <本次提交> && git commit -m "revert to v0.1.7-rc2 baseline"
```
