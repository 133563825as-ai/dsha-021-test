# dsha-021-test

DSHA **测试包** 构建仓库：基线是 DSHA `v0.1.7-rc2` 源码，运行时升级到 `@deepseek-ai/dsh 0.2.1-rc.2`。

- 出包方式：GitHub Actions，工作流 [.github/workflows/build.yml](.github/workflows/build.yml)
- 签名：仓库内 `ci/test-signing.keystore`（**测试签名，不能覆盖安装正式版**）
- 变体：`standardRelease`（`debuggable=false`、arm64-v8a、`versionCode 181`）
- 离线大资产（`offline-rootfs.bin` / `ubuntu-tools.bin`）不进 git，构建时从官方 `v0.2.0-rc2` APK 中取出并打印 sha256

## 触发方式

Actions → `build-test-apk` → Run workflow。首次运行后，把日志里打印的两个 sha256 填进
`offline_rootfs_sha256` / `ubuntu_tools_sha256`，后续运行就会强制校验资产字节。

## 本轮改了什么（摘要）

| 面 | 改动 |
|---|---|
| 运行时 | 128 包钉版清单重建为 0.2.1 的真实依赖闭包（305 核心 + 18 个 DSHA 额外依赖），`cordis-plugin-group` → 1.0.5；`dsh-invariants`、`dsh-subagent-in-process-driver` 上游已删除，移出清单 |
| 运行时补丁 | 会话后端、JSON 存储 proroot 绕行、原子发布、client 拼接、V2→V3 迁移包装全部重新锚定到 0.2.1 |
| 客户端补丁 | 16 个 `*-patch.json` 逐一核对：12 个原样命中，3 处按新代码结构重写，`lexical-claim` 退役（上游换成 `CommandChipNode` 模型） |
| 内置插件 | 8 个插件 peer 声明对齐 0.2.1，全部通过宿主 `evaluatePluginCompatibility` |
| 虚拟屏 | `dsh-tool-vscreen/cordis.patch.yml` 原为 `[]`，插件从未装载；已补齐 insert 行 |
| 应急运行时 | `tools/recovery-runtime/lock.json` 升级到 0.2.1，哈希由 `ci/refresh-pins.py` 在构建时刷新 |
| UI | 保持内置 dsh-web-mobile 3.0.3，仅对齐 peer 元数据 |

详细说明见构建产物旁的 `TEST-BUILD-NOTES.md`（在源仓库里）。
