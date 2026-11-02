#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""汇总覆盖测试清单的填写进度，并检查填写是否合规（只读）。

用法：python3 -I tools/check_coverage_progress.py [--strict]
检查项：
  1. 每个维度、每支小队的完成数 / 总数；
  2. 结论只能是 ✅ / 🟡 / ❌规则 / ❌UI / ⛔ 之一；
  3. 已填结论的行必须同时填写 实际结果、证据、设备、执行人、日期；
  4. 证据路径（多个用 ; 分隔）必须真实存在于仓库中；
  5. 维度 OP / WP / UA / KD 及 F 专项必须写明两台设备（5554+5556）；
  6. 截图内容去重检查：同一张截图内容（SHA-256）被超过 2 个条目引用即报不合规；
  7. 导出记录战斗深度检查：若引用了 export_log.txt，其中必须包含「射击：」或「近战：」字样。
--strict：存在未完成行或任何不合规行时退出码为 1。
"""
import csv, glob, hashlib, os, sys, collections

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
OUT = os.path.join(ROOT, 'docs', 'coverage')
OK = {'✅', '🟡', '❌规则', '❌UI', '⛔'}
BOTH = {'OP', 'WP', 'UA', 'KD', 'F'}
SAMPLED = {'PA', 'FR', 'ER', 'CO'}  # 纯文本展示维度（已由 check_team_data/rules 100% 校验），UI 抽样 >= 20%

def main():
    strict = '--strict' in sys.argv
    files = sorted(glob.glob(os.path.join(OUT, 'checklist_*.csv')))
    if not files:
        print('没有清单，请先运行 tools/gen_coverage_checklist.py'); return 1
    dim = collections.defaultdict(lambda: [0, 0]); team = collections.defaultdict(lambda: [0, 0])
    verdicts = collections.Counter(); problems = []
    file_sha256_cache = {}
    img_hash_refs = collections.defaultdict(list)
    export_checked = set()
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
            row_hashes = set()
            for ev in filter(None, (x.strip() for x in r['证据'].split(';'))):
                abs_ev = os.path.join(ROOT, ev)
                if not os.path.exists(abs_ev):
                    problems.append(f'{r["编号"]}: 证据不存在 {ev}')
                    continue
                if ev.lower().endswith(('.png', '.jpg', '.jpeg', '.webp')):
                    h = file_sha256_cache.get(abs_ev)
                    if h is None:
                        with open(abs_ev, 'rb') as fp:
                            h = hashlib.sha256(fp.read()).hexdigest()
                        file_sha256_cache[abs_ev] = h
                    row_hashes.add((h, ev))
                elif ev.endswith('export_log.txt') and abs_ev not in export_checked:
                    export_checked.add(abs_ev)
                    txt = open(abs_ev, encoding='utf-8', errors='replace').read()
                    if '射击：' not in txt and '近战：' not in txt:
                        problems.append(f'{ev}: 导出记录未包含任何「射击：」或「近战：」结算')
            for h, sample_ev in row_hashes:
                img_hash_refs[h].append((r['编号'], sample_ev))
            if d in BOTH and not ('5554' in r['设备'] and '5556' in r['设备']):
                problems.append(f'{r["编号"]}: 该维度要求两台设备都验证')
    for h, refs in img_hash_refs.items():
        if len(refs) > 2:
            ids = ', '.join(x[0] for x in refs[:5]) + ('...' if len(refs) > 5 else '')
            problems.append(f'截图内容重复引用 {len(refs)} 次 (>2): {refs[0][1]} -> [{ids}]')
    print('== 按维度')
    for k, (a, b) in sorted(dim.items()):
        tag = ' (抽样>=20%)' if k in SAMPLED else ' (必测100%)'
        print(f'  {k:5s} {a:5d}/{b:<5d}{tag}')
    print('== 按清单'); [print(f'  {k:32s} {a:4d}/{b:<4d}') for k, (a, b) in team.items()]
    print('== 结论分布', dict(verdicts))
    total = sum(b for _, b in dim.values()); done = sum(a for a, _ in dim.values())
    req_done = sum(a for k, (a, _) in dim.items() if k not in SAMPLED)
    req_total = sum(b for k, (_, b) in dim.items() if k not in SAMPLED)
    sampled_undone = [k for k, (a, b) in dim.items() if k in SAMPLED and a * 5 < b]
    print(f'== 必测维度合计 {req_done}/{req_total}，总计 {done}/{total}，不合规 {len(problems)} 条')
    for x in problems[:200]: print('  -', x)
    if strict and (req_done < req_total or sampled_undone or problems): return 1
    return 0

if __name__ == '__main__':
    sys.exit(main())
