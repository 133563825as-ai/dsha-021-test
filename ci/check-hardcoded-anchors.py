#!/usr/bin/env python3
"""哨兵：Java 里硬编码的源码锚点，必须能在打好的运行时树里唯一命中。

为什么需要它：绝大多数适配补丁放在 app/src/main/assets/*-patch.json 里，可以逐个审计；
但 RuntimeTools.java 里有少量直接写死的字符串锚点。这类锚点不会被补丁清单审计覆盖，
上游一改结构就会在**真机上**才炸（曾经就炸过一次：
LAN 设置持久化的锚点在 0.2.1 从 dsh-client-ui-settings 搬到了 dsh-client-ui-settings-general）。

用法：在生成 dsh-runtime.bin 之后运行，参数是 npm 安装树里的 node_modules 路径。
"""
import sys
from pathlib import Path

# (模块相对路径, 锚点原文, 归属说明)
CHECKS = [
    (
        '@deepseek-ai/dsh-client-ui-settings-general/lib/client.js',
        'const documentController = ctx.remote.$host.isLoopback ? new SettingsDocumentStore(ctx, ctx.configForms.describe()) : void 0;',
        'RuntimeTools.prepareLanSettingsPersistence（局域网代理强制 host 持久化）',
    ),
]


def main(argv):
    if len(argv) != 1:
        raise SystemExit('用法: check-hardcoded-anchors.py <node_modules 路径>')
    modules = Path(argv[0]).resolve(strict=True)
    failures = []
    for relative, anchor, owner in CHECKS:
        target = modules / relative
        if not target.is_file():
            failures.append(f'{owner}: 模块不存在 {relative}')
            continue
        hits = target.read_text(encoding='utf-8', errors='replace').count(anchor)
        if hits != 1:
            failures.append(f'{owner}: 锚点在 {relative} 命中 {hits} 次（应为 1 次）')
        else:
            print(f'PASS {owner}')
    if failures:
        print('\n硬编码锚点与运行时结构不符：', file=sys.stderr)
        for line in failures:
            print('  - ' + line, file=sys.stderr)
        raise SystemExit(1)
    print(f'全部 {len(CHECKS)} 处硬编码锚点校验通过')


if __name__ == '__main__':
    main(sys.argv[1:])
