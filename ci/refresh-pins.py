#!/usr/bin/env python3
"""测试包专用：把本次实际生成的离线资产哈希写回应急运行时锁。

正式发布流程里这份锁应当由人审阅后提交（prepare-recovery-assets.py 只做 --
check）。测试包每次都要重新生成 dsh-runtime.bin，哈希必然变化，所以在 CI 里
显式刷新并打印前后差异，不静默。
"""
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / 'tools/recovery-runtime/lock.json'
VERSION = json.loads((ROOT / 'tools/dsh-runtime/package.json').read_text(encoding='utf-8'))['dependencies']['@deepseek-ai/dsh']


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b''):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    lock = json.loads(LOCK.read_text(encoding='utf-8'))
    changed = []
    for row in lock['archives']:
        # 锁里自己声明了种子路径：优先用它，避免构建脚本改了目录结构后这里悄悄算错。
        seed = ROOT / row['seed']
        if not seed.is_file():
            sys.exit('缺少应急归档的种子文件（先跑 :app:prepareStandardAssets）: ' + str(seed))
        actual = sha256(seed)
        if row['sha256'] != actual:
            changed.append((row['asset'], row['sha256'], actual))
            row['sha256'] = actual
    if lock.get('dshVersion') != VERSION:
        changed.append(('dshVersion', lock.get('dshVersion'), VERSION))
        lock['dshVersion'] = VERSION
    LOCK.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if not changed:
        print('应急运行时锁与本次生成物一致（无变化）')
    for name, before, after in changed:
        print(f'应急运行时锁更新 {name}: {before} -> {after}')


if __name__ == '__main__':
    main()
