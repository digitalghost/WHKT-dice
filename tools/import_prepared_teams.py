#!/usr/bin/env python3
"""Generate the three 2026-09-24 team catalogs from reviewed docs manifests.

No network/OCR/translation: current Chinese text, IDs and numbers stay traceable
back to manifest.json. Original card images are copied without modification.
"""
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main'
NAMES = ['冥工之环', '狼侦察', '洁天使隐伏者']
CATEGORY = {'faction-rule': '阵营完整规则', 'strategic-ploy': '战略计谋',
            'firefight-ploy': '交战计谋', 'faction-equipment': '阵营装备'}


def quote(s):
    return json.dumps(str(s), ensure_ascii=False).replace('$', '\\$')


def ident(s):
    return s.lower().replace('-', '_')


def strings(items):
    return 'listOf(' + ', '.join(quote(x) for x in items) + ')'


def chinese_section(markdown, heading):
    start = markdown.index(heading)
    text = markdown[start:].split('\n', 1)[1]
    text = re.split(r'\n#{2,3} ', text, maxsplit=1)[0]
    text = re.split(r'\n(?:英文(?:来源)?全文：|网站 composition (?:原文|全文)：)', text, maxsplit=1)[0]
    return text.strip()


def clean_keywords(profile):
    # The manifest appends the English source in full-width parentheses.
    text = profile['rules_zh'].split('（', 1)[0].strip()
    if text in ('', '-', '—', '无'):
        return ''
    return text.replace('*', '').replace(', ', '、').replace(',', '、')


def generate():
    teams, ability_maps, melee, cards_map, equipment_maps, art_map = [], [], [], [], [], []
    special_rules = {}
    for name in NAMES:
        folder = ROOT / 'docs' / (name + '-卡片提取')
        data = json.loads((folder / 'manifest.json').read_text())
        api = json.loads((folder / 'ktdash-current.json').read_text())['data']
        markdown = (ROOT / 'docs' / (name + '-完整规则资料.md')).read_text()
        team_id = data['team_id']
        rules = {r['id']: r for r in data['rule_records']}
        cards = data['cards']
        target_cards = MAIN / 'assets/team_rules'
        target_cards.mkdir(parents=True, exist_ok=True)
        filenames = {}
        for card in cards:
            filename = team_id + '--' + card['image']
            shutil.copyfile(folder / card['image'], target_cards / filename)
            filenames[card['id']] = filename
        def linked(rule):
            return list(dict.fromkeys(filenames[src['card_id']] for src in rule.get('translation_source', [])))
        # Verify all current numerical fields against the saved API, not the old PDF images.
        assert len(data['operatives']) == data['counts']['roles']
        api_ops = {o['opTypeId']: o for o in api['opTypes']}
        ops = []
        for op in data['operatives']:
            source = api_ops[op['opTypeId']]
            for field in ('APL', 'MOVE', 'SAVE', 'WOUNDS', 'basesize', 'keywords'):
                assert op[field] == source[field], (team_id, op['opTypeId'], field)
            op_id = ident(op['opTypeId'])
            data_card = next(c for c in cards if c['category'] == 'operative' and op['opTypeId'] in c['ktdash_ids'])
            resource = 'roster_' + op_id
            shutil.copyfile(folder / data_card['image'], MAIN / 'res/drawable-nodpi' / (resource + '.png'))
            if not ops:
                art_map.append(f'{quote(team_id)} -> R.drawable.{resource}')
            source_weapons = {w['wepId']: w for w in source['weapons']}
            weapons, defaults = [], []
            for weapon in op['weapons']:
                source_profiles = {p['wepprofileId']: p for p in source_weapons[weapon['wepId']]['profiles']}
                assert weapon['wepType'] == source_weapons[weapon['wepId']]['wepType']
                for profile in weapon['profiles']:
                    for field in ('ATK', 'HIT', 'DMG', 'WR'):
                        assert profile[field] == source_profiles[profile['wepprofileId']][field]
                    profile_id = ident(profile['wepprofileId'])
                    title = weapon['name_zh']
                    if len(weapon['profiles']) > 1:
                        title += '（' + profile['name_zh'] + '）'
                    elif sum(w['name_zh'] == weapon['name_zh'] for w in op['weapons']) > 1:
                        title += '（' + ('近战' if weapon['wepType'] == 'M' else '远程') + '）'
                    keywords = clean_keywords(profile)
                    stats = f"{profile['ATK']}A · {profile['HIT']} · {profile['DMG']}" + (' · ' + keywords if keywords else '')
                    weapons.append(f'WeaponOption({quote(profile_id)}, {quote(title)}, {quote(stats)})')
                    if weapon['isDefault']:
                        defaults.append(profile_id)
                    if weapon['wepType'] == 'M':
                        melee.append(profile_id)
            ops.append(f'''OperativeTemplate({quote(op_id)}, {quote(op['name_zh'])}, {quote(op['role_zh'])}, {op['APL']}, {quote(op['MOVE'])}, {quote(op['SAVE'])}, {op['WOUNDS']}, R.drawable.{resource},
                listOf({', '.join(weapons)}), {strings(defaults)},
                baseSizeMm={op['basesize']}, keywords={strings(k.strip() for k in op['keywords'].split(','))})''')
            abilities = []
            for ability in op['abilities']:
                rule = rules[ability['abilityId']]
                ap = 'null' if ability['AP'] is None else str(ability['AP'])
                shared = 'true' if ability['isFactionRule'] else 'false'
                abilities.append(f'AbilityDefinition({quote(rule["id"])}, {quote(rule["title_zh"])}, {quote(rule["current_text_zh"])}, {ap}, {shared})')
                if rule['title_zh'] in ['维度放逐', '护盾', '洪流 0"']:
                    special_rules[rule['title_zh']] = rule['current_text_zh']
            ability_maps.append(f'{quote(op_id)} to listOf({", ".join(abilities)})')
            cards_map.append(f'{quote(op_id)} to {strings(filenames[c["id"]] for c in cards if c["category"] == "operative-ability" and op["opTypeId"] in c["ktdash_ids"])}')
        description = chinese_section(markdown, '### 2.1')
        teams.append(f'KillTeamCatalog({quote(team_id)}, {quote(name)}, {quote(description)}, listOf({", ".join(ops)}))')
        entries, shared_seen = [], set()
        for rule in data['rule_records']:
            category = CATEGORY.get(rule['category'])
            if not category:
                continue
            identity = (rule['title_zh'], rule['current_text_zh'])
            if category == '阵营完整规则':
                if identity in shared_seen:
                    continue
                shared_seen.add(identity)
            body = rule['current_text_zh']
            if category.endswith('计谋'):
                body = rule['cost'] + '\n\n' + body
            entries.append(dict(id=rule['id'], category=category, title=rule['title_zh'], body=body,
                                cards=linked(rule), source_date=data['extracted_at']))
        composition = chinese_section(markdown, '### 2.2')
        entries.append(dict(id=team_id+'-composition', category='编成与武器选择', title='小队编成与配装', body=composition,
                            cards=[filenames[c['id']] for c in cards if c['category']=='team-selection']))
        for card in cards:
            if card['category']=='other' and '标识' in card['title_zh']:
                section = next((h for h in re.findall(r'^### .+$', markdown, re.M) if card['id'] in h), None)
                body = chinese_section(markdown, section) if section else '标识与指示物见原卡。'
                body = re.sub(r'网站独立ID：.*?\n', '', body).strip()
                entries.append(dict(id=card['id'],category='标识指南',title=card['title_zh'],body=body or '标识与指示物见原卡。',cards=[filenames[card['id']]]))
        for rule in data.get('supplementary_pdf_only_rules', []):
            entries.append(dict(id=rule['id'],category='勘误与问答',title=rule['title_zh'],body=rule['current_text_zh'],cards=[]))
        if team_id == 'IMP-CI':
            anti = chinese_section(markdown, '### 反灵能者')
            anti = anti.split('网站武器档案')[0].strip()
            special_rules['反灵能者'] = anti
            card = next(c for c in cards if '反灵能者' in c['title_zh'])
            entries.insert(4,dict(id=team_id+'-anti-psyker',category='阵营完整规则',title='反灵能者',body=anti,cards=[filenames[card['id']]]))
        output = dict(team_id=team_id, name=name, source_date=data['extracted_at'], entries=entries)
        (MAIN/'assets/rules'/f'{team_id}.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n')
        equipment = []
        for rule in data['rule_records']:
            if rule['category'] != 'faction-equipment':
                continue
            files=linked(rule)
            equipment.append(f'EquipmentCard({quote(rule["title_zh"])}, "阵营装备", {quote(rule["current_text_zh"])}, {quote("team_rules/"+files[0])}, currentText=true)')
        equipment_maps.append(f'{quote(team_id)} to listOf({", ".join(equipment)})')
        print(f'{team_id}: {len(ops)} operatives, {sum(len(w["profiles"]) for o in data["operatives"] for w in o["weapons"])} profiles, {len(cards)} original cards')
    result = '''package com.example.helloworld

// Generated by tools/import_prepared_teams.py from reviewed docs manifests (2026-09-24).
object ImportedTeamCatalog {
'''
    result += '    val teams = listOf(\n        ' + ',\n        '.join(teams) + '\n    )\n'
    result += '    val abilities = mapOf(\n        ' + ',\n        '.join(ability_maps) + '\n    )\n'
    result += '    val meleeWeaponIds = ' + strings(melee).replace('listOf(', 'setOf(',1) + '\n'
    result += '    val abilityCards = mapOf(\n        ' + ',\n        '.join(cards_map) + '\n    )\n'
    result += '    val equipment = mapOf(\n        ' + ',\n        '.join(equipment_maps) + '\n    )\n'
    result += '    fun teamArt(id: String): Int? = when(id) {\n        ' + '\n        '.join(art_map) + '\n        else -> null\n    }\n'
    result += '    val weaponRules = listOf(\n        ' + ',\n        '.join(f'RuleDefinition({quote("imported_"+str(i))}, {quote(k)}, {quote(v)})' for i,(k,v) in enumerate(special_rules.items())) + '\n    )\n}\n'
    (MAIN/'java/com/example/helloworld/ImportedTeamCatalog.kt').write_text(result)


if __name__=='__main__':
    generate()
