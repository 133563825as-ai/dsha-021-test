# C 组只读复核：交付回执与受管输入契约

检查当前 `tools/release_acceptance.py`、`release-acceptance.json`、`verify-stability.py`、`runtime_input_contract.py`、`test-runtime-input-contract.py`、`test-runtime-descriptor-inputs.py`，未编辑这些文件，也未运行 Gradle 或设备。四个纯 Python 宿主测试分别通过：acceptance 5、release evidence 4、runtime input 6、descriptor input 契约脚本通过（54 安装项、101 资产、41 启动输入）。以下两项是代码路径可复现的交付可信度缺口，需在正式交付前裁决。

**R2-C05 · P2 · 输入 baseline 未与可信验收点绑定。** `verify-stability.py:138,163` 接收任意 `--baseline-sources` JSON，按该文件与当前源码的差异选择设备项目；`--deliver-from` 在 `:109-115` 只要求重新计算出的选择结果等于回执中的选择结果。`release_acceptance.requirements()` 拒绝空 baseline，但不校验其来自 `audit-seal.json` 或上一个已验收软件回执。将当前 `source_snapshot()` 原样误传为 baseline 时，`requirements(current,current)` 的 `changed=[]`、`rules=[]`，只剩固定五项，上传、麦克风、应急网页等本轮变更可从设备必验集合中消失。建议在门禁中绑定封存 baseline 的路径与 SHA，或绑定前一正式交付回执的已验证 `sourceSnapshot.sha256`；`acceptance.baselineSha256` 仅是自述摘要，不能独立证明来源。复测要覆盖“误传当前快照仍被拒绝”和“已确认上一交付快照可通过”。

**R2-C06 · P2 · 行为规则漏掉运行时代码与网页资产输入。** `release-acceptance.json` 的 browser/recovery 规则只列少数 Java UI 与 `tools/recovery_runtime_overlay.py`，没有 `app/src/main/assets/web-integration/es-compat.js`、`compat.js`、`pdf-compat-patch.json`、`tools/prepare-recovery-assets.py` 等能改变网页旧内核/应急 APK 实际行为的输入。纯函数复现：以 `es-compat.js` 从 `a*64` 变为 `b*64` 为唯一改变，`requirements()` 返回 `rules=[]` 且 Standard/Low 仍只有五个 base check。建议规则从实际网页兼容、应急生成器/补丁输入集合派生或至少完整列入这些目录及固定文件；验收夹具应逐类单独改动，证明双 flavor 的相关行为必验项被选中。别把每个无关文案资源一概映射为高成本设备矩阵。

回执复用已有重要正向保护：`validate_software_receipt()` 核对完整源码 snapshot、必需命令及其日志 hash 和双 flavor JUnit 计数；`deliver_from_receipt()` 重算验收选择、核对设备证据与两份 APK 当前字节后才写 release。保留边界是回执 JSON 自身为可编辑本地文件，`validate_software_receipt()` 未把 APK 行内签名/包名/版本与签名检查日志做不可变绑定；若回执行被人为改写并配上匹配的外部设备 JSON，`--deliver-from` 不重跑签名/manifest。它不是一个针对恶意修改本机所有证据文件的安全边界；建议交付时至少对复用的**确切 APK 字节**重新核对证书、包名、版本、非调试和 flavor，以降低手工指错回执/产物的风险。

受管输入方面，`RuntimeTools.installManagedAssets()` 从 `managed-runtime-inputs.json` 消费安装表，描述符与 Gradle 同读该表；`test-runtime-input-contract.py` 对单文件、目录新增/删除和目标路径改变验证身份变化，`test-runtime-descriptor-inputs.py` 对内置插件声明及 Gradle 消费关系做补充检查。当前静态检查未发现上一轮 BUILD-03 类型的具体漏项。该 manifest 不宣称包含整个 util 包；`launcherSources` 明确列出跨层运行时相关类，符合有限边界。
