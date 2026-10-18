#!/usr/bin/env python3
"""输出/校验 CI 工具链版本。

契约与上游 DSH-APP/DSHA 的 tools/ci-toolchain.py 一致：版本的唯一真源是 ci/toolchain.lock.json，
并且必须与源码自身的声明（app/build.gradle、build.gradle、gradle-wrapper.properties）逐项一致，
任何漂移直接非零退出 —— 不允许 workflow 里的版本号和源码各说各话。

源码树还没推上来时（本仓库的第一个版本），只打印 lock 并给出 SKIP，方便 workflow 继续跑工具链部分。
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIELDS = ('java', 'node', 'python', 'gradle', 'agp', 'androidPlatform', 'buildTools', 'ndk')


def one(pattern, content, label):
    values = re.findall(pattern, content)
    if len(values) != 1:
        raise ValueError('CI_TOOLCHAIN_SOURCE: ' + label)
    return values[0]


def read_lock():
    lock = json.loads((ROOT / 'ci/toolchain.lock.json').read_text(encoding='utf-8'))
    if lock.get('schema') != 1 or set(lock) != {'schema', *FIELDS}:
        raise ValueError('CI_TOOLCHAIN_SCHEMA')
    for name in FIELDS:
        if not isinstance(lock[name], str) or not re.fullmatch(r'[A-Za-z0-9.-]+', lock[name]):
            raise ValueError('CI_TOOLCHAIN_VALUE: ' + name)
    return lock


def check_against_source(lock):
    app_path = ROOT / 'app/build.gradle'
    top_path = ROOT / 'build.gradle'
    wrapper_path = ROOT / 'gradle/wrapper/gradle-wrapper.properties'
    missing = [str(p.relative_to(ROOT)) for p in (app_path, top_path, wrapper_path) if not p.is_file()]
    if missing:
        return 'SKIP: 源码树未就位，缺少 ' + ', '.join(missing)

    app = app_path.read_text(encoding='utf-8')
    top = top_path.read_text(encoding='utf-8')
    wrapper = wrapper_path.read_text(encoding='utf-8')
    platform = one(r'version\s*=*\s*release\(\s*(\d+)\s*\)', app, 'compileSdk')
    expected = {
        'java': one(r'sourceCompatibility\s+JavaVersion.VERSION_(\d+)', app, 'java'),
        'gradle': one(r'distributionUrl=[^\n]+/gradle-([0-9.]+)-bin\.zip', wrapper, 'gradle'),
        'agp': one(r"id\s+'com.android.application'\s+version\s+'([0-9.]+)'", top, 'agp'),
        'ndk': one(r"ndkVersion\s+['\"]([0-9.]+)['\"]", app, 'ndk'),
        'androidPlatform': 'android-' + platform + '.0',
    }
    if one(r'targetSdk\s+(\d+)', app, 'targetSdk') != platform:
        raise ValueError('CI_TOOLCHAIN_TARGET_SDK')
    for name, value in expected.items():
        if lock[name] != value:
            raise ValueError(f'CI_TOOLCHAIN_DRIFT: {name} lock={lock[name]} source={value}')
    return 'PASS: lock 与源码声明一致'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--field', choices=FIELDS)
    parser.add_argument('--github-output', action='store_true')
    args = parser.parse_args()
    lock = read_lock()
    source_state = check_against_source(lock)
    if args.github_output:
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8', newline='\n') as output:
            for name in FIELDS:
                output.write(name + '=' + lock[name] + '\n')
            output.write('source_state=' + source_state.replace('\n', ' ') + '\n')
    if args.field:
        print(lock[args.field])
        return
    print(json.dumps(lock, sort_keys=True))
    print(source_state)
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a', encoding='utf-8', newline='\n') as handle:
            handle.write('### Toolchain\n\n```\n' + json.dumps(lock, sort_keys=True, indent=2) + '\n```\n\n' + source_state + '\n')


if __name__ == '__main__':
    main()
