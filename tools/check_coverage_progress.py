#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""汇总覆盖测试清单的填写进度，并检查填写是否合规（只读）。

用法：python3 -I tools/check_coverage_progress.py [--strict]
检查项：
  1. 每个维度、每支小队的完成数 / 总数；
  2. 结论只能是 ✅ / 🟡 / ❌规则 / ❌UI / ⛔ 之一；
  3. 已填结论的行必须同时填写 实际结果、证据、设备、执行人、日期；
  4. 证据路径（多个用 ; 分隔）必须真实存在于仓库中；
  5. 维度 OP / WP / UA / KD 及 F 专项必须写明两台设备（5554+5556）。
--strict：存在未完成行或任何不合规行时退出码为 1。
"""
import csv, glob, os, sys, collections

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
OUT = os.path.join(ROOT, 'docs', 'coverage')
OK = {'✅', '🟡', '❌规则', '❌UI', '⛔'}
BOTH = {'OP', 'WP', 'UA', 'KD', 'F'}

def main():
    strict = '--strict' in sys.argv
    files = sorted(glob.glob(os.path.join(OUT, 'checklist_*.csv')))
    if not files:
        print('没有清单，请先运行 tools/gen_coverage_checklist.py'); return 1
    dim = collections.defaultdict(lambda: [0, 0]); team = collections.defaultdict(lambda: [0, 0])
    verdicts = collections.Counter(); problems = []
    for p in files:
        name = os.path.basename(p)[len('checklist_'):-4]
        for r in csv.DictReader(open(p, encoding='utf-8-sig')):
            v = r['结论'].strip(); d = r['维度']
            dim[d][1] += 1; team[name][1] += 1
            if not v:
                continue
            dim[d][0] += 1; team[name][0] += 1; verdicts[v] += 1
            if v not in OK:
                problems.append(f'{r["编号"]}: 结论「{v}」不在允许值内')
            for k in ('实际结果', '证据', '设备', '执行人', '日期'):
                if not r[k].strip():
                    problems.append(f'{r["编号"]}: 缺少 {k}')
            for ev in filter(None, (x.strip() for x in r['证据'].split(';'))):
                if not os.path.exists(os.path.join(ROOT, ev)):
                    problems.append(f'{r["编号"]}: 证据不存在 {ev}')
            if d in BOTH and not ('5554' in r['设备'] and '5556' in r['设备']):
                problems.append(f'{r["编号"]}: 该维度要求两台设备都验证')
    print('== 按维度'); [print(f'  {k:5s} {a:5d}/{b:<5d}') for k, (a, b) in sorted(dim.items())]
    print('== 按清单'); [print(f'  {k:32s} {a:4d}/{b:<4d}') for k, (a, b) in team.items()]
    print('== 结论分布', dict(verdicts))
    total = sum(b for _, b in dim.values()); done = sum(a for a, _ in dim.values())
    print(f'== 合计 {done}/{total}，不合规 {len(problems)} 条')
    for x in problems[:200]: print('  -', x)
    if strict and (done < total or problems): return 1
    return 0

if __name__ == '__main__':
    sys.exit(main())
