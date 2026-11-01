#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成全量覆盖测试用例清单（只读数据，写出 CSV）。

用法：python3 -I tools/gen_coverage_checklist.py
输出：docs/coverage/checklist_core.csv、docs/coverage/checklist_<TEAM_ID>.csv
每一行是一个必须核销的用例，执行人填写 结论 / 实际结果 / 证据 / 设备 / 执行人 / 日期 列。
已存在的 CSV 不会被覆盖（避免冲掉已填写的结果），需要重新生成时请先手动删除。
"""
import csv, glob, json, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
A = os.path.join(ROOT, 'app', 'src', 'main', 'assets')
OUT = os.path.join(ROOT, 'docs', 'coverage')
SRC = os.path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'example', 'helloworld')
COLS = ['编号', '维度', '对象', '细项', '依据', '期望结果', '结论', '实际结果', '证据', '设备', '执行人', '日期']

DIM_EXPECT = {
    'OP': '出场；人物卡、成员规则可打开；APL/移动/豁免/耐伤与最新卡面一致；受伤后移动 −2″',
    'WP': '武器在射击/近战页可选；攻击/命中/伤害/规则与最新卡面一致；结算数值符合规则',
    'UA': '「独特行动…」中出现；AP 扣减正确；效果按原文生效（APL 变化须在目标下一次激活时核对）',
    'PA': '已自动化的数值正确；未自动化的能用通用干预入口完整记录',
    'WR': '规则 Chip 可点开且正文完整；在结算中按原文生效',
    'FR': '「小队规则」中正文完整无截断；对局中按原文生效或可手工记录',
    'SP': '战略阶段可使用；CP 扣减正确；子选项与原文一致；重复使用有拦截',
    'FP': '交战阶段可使用；CP 扣减正确；效果按原文生效；可撤回',
    'EQ': '对局设置可勾选；对局中生成的行动/武器/修正与原文一致',
    'ER': '勘误内容已体现在数据卡/规则正文与结算中',
    'CO': '编成指南正文完整；编成要求提示与官方一致；记录不拦截情况',
    'KD': '按复核标注复现：修复前记录现象，修复后应符合期望',
}

def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)

def canonical():
    src = open(os.path.join(SRC, 'CanonicalRosters.kt'), encoding='utf-8').read()
    body = src.split('memberIdsByTeam', 1)[1]
    can = {}
    for m in re.finditer(r'"([A-Z][A-Z0-9_-]+)" to listOf\((.*?)\)', body, re.S):
        can[m.group(1)] = set(re.findall(r'"([^"]+)"', m.group(2)))
    return can

def known():
    p = os.path.join(OUT, 'known_defects.json')
    return load(p) if os.path.exists(p) else {}

def write(name, rows):
    path = os.path.join(OUT, name)
    if os.path.exists(path):
        print('跳过（已存在）', os.path.relpath(path, ROOT)); return 0
    with open(path, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.writer(f); w.writerow(COLS)
        for r in rows: w.writerow(r + [''] * (len(COLS) - len(r)))
    print('写出', os.path.relpath(path, ROOT), len(rows), '行'); return len(rows)

def team_rows(tid, team, rules, can, kd):
    rows, n = [], {}
    def add(dim, obj, detail, basis):
        n[dim] = n.get(dim, 0) + 1
        rows.append([f'{tid}-{dim}-{n[dim]:03d}', dim, obj, detail, basis, DIM_EXPECT[dim]])
    doc = f'docs/{team["name"]}-完整规则资料.md'
    for o in team['operatives']:
        tag = '标准编成' if o['id'] in can.get(tid, set()) else '副编成（场次 B）'
        add('OP', o['name'], f'{o["id"]} · {tag}', doc)
        for w in o['weapons']:
            add('WP', o['name'], f'{w["name"]}（{w["kind"]}）{w["profile"]}', doc)
        for a in o['abilities']:
            if a.get('ap') is not None:
                add('UA', o['name'], f'{a["name"]} · {a["ap"]}AP', doc)
            elif not a.get('factionRule'):
                add('PA', o['name'], a['name'], doc)
    for r in team.get('weaponRules', []):
        add('WR', r.get('keyword') or r.get('name', ''), '小队专属武器规则', doc)
    cat_dim = {'阵营完整规则': 'FR', '战略计谋': 'SP', '交战计谋': 'FP', '阵营装备': 'EQ', '勘误与问答': 'ER', '编成与武器选择': 'CO'}
    for e in rules.get('entries', []):
        d = cat_dim.get(e.get('category'))
        if d:
            add(d, e.get('title', ''), e.get('category'), doc)
    for item in kd.get(tid, []):
        add('KD', item['ref'], item['text'], 'docs/捉对对战测试方法与进度报告.md 第四节')
    return rows

def core_rows():
    core = load(os.path.join(A, 'core_rules.json'))
    rows = []
    for i, t in enumerate(core['topics'], 1):
        rows.append([f'CORE-TOPIC-{i:02d}', 'CORE', t['title'], f'规则速查 · {t["id"]}', f'v11.5 p{t.get("pages", "")}', '正文与页码正确；对应流程在 F 专项中验证'])
    for i, r in enumerate(core['weaponRules'], 1):
        rows.append([f'CORE-WR-{i:02d}', 'CORE', r['keyword'], '通用武器规则', f'v11.5 p{r.get("pages", "")}', 'Chip 可点开；在结算中按规则生效（列出验证所用武器）'])
    eq = re.findall(r'Card\("([^"]+)"', open(os.path.join(SRC, 'EquipmentCards.kt'), encoding='utf-8').read())
    for i, name in enumerate(eq, 1):
        rows.append([f'CORE-EQ-{i:02d}', 'CORE', name, '通用装备', 'v11.5 通用装备 p98', '可勾选；计入 4 件上限；对局中的效果/行动/武器正确'])
    ms = re.findall(r'MissionCard\("([^"]+)", "([^"]+)"', open(os.path.join(SRC, 'MissionCards.kt'), encoding='utf-8').read())
    for i, (name, kind) in enumerate(ms, 1):
        rows.append([f'CORE-MS-{i:02d}', 'CORE', name, kind, 'v11.5 认证行动任务包', '可选择；卡面详情正确；计分可在「NVP ›」完整记录'])
    for i in range(1, 15):
        rows.append([f'CORE-KILL-{i:02d}', 'CORE', f'起始 {i} 人击杀等级', '击杀行动', 'v11.5 p107', '每档阈值与官方表一致；升级自动 +1VP'])
    rows.append(['CORE-KILL-15', 'CORE', '起始 >14 人', '击杀行动', 'v11.5 p107', '按 14 人表计算'])
    for i in range(1, 13):
        rows.append([f'F-{i:02d}', 'F', f'专项 F-{i:02d}', '见测试方式文档第五节', '见文档', '专项内每个子项另行附截图与日志'])
    return rows

def main():
    os.makedirs(OUT, exist_ok=True)
    can, kd = canonical(), known()
    total = write('checklist_core.csv', core_rows())
    for p in sorted(glob.glob(os.path.join(A, 'teams', '*.json'))):
        team = load(p); tid = team['teamId']
        rp = os.path.join(A, 'rules', f'{tid}.json')
        rules = load(rp) if os.path.exists(rp) else {}
        total += write(f'checklist_{tid}.csv', team_rows(tid, team, rules, can, kd))
    print('合计新写出', total, '行')

if __name__ == '__main__':
    sys.exit(main())
