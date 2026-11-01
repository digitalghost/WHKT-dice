# 小队数据 JSON 格式（数据驱动导入）

新小队不再生成 Kotlin 代码。每支小队两份资料文件 + 卡图：

| 文件 | 内容 |
|---|---|
| `app/src/main/assets/teams/<TEAM_ID>.json` | 小队、特工、武器、专属能力、专属武器规则（本文件定义） |
| `app/src/main/assets/rules/<TEAM_ID>.json` | 阵营规则、计谋、阵营装备、编成等（格式见《杀戮小队中英数据整理工作流》10.5 节） |
| `app/src/main/assets/team_rules/<TEAM_ID>--*.png` | 原卡图（特工数据卡、能力卡、规则卡） |

APP 启动时扫描 `assets/teams/*.json`，与代码内置的 8 支小队合并；同 ID 以代码内置为准。

## 结构

```json
{
  "teamId": "KT-BROOD_BROTHERS",
  "name": "族群裔兵",
  "nameEn": "Brood Brothers",
  "subtitle": "泰伦虫族 · 基因窃取者教派 · 15 种特工",
  "archetypes": ["渗透", "安全保护"],
  "source": "官方中文 PDF 26/08/2026（…杀戮小队规则.pdf），已并入 2026 年 8 月勘误",
  "operatives": [
    {
      "id": "kt_bb_commander",
      "name": "裔兵指挥官",
      "role": "指挥官",
      "apl": 2, "move": "6\"", "save": "5+", "wounds": 8,
      "baseSizeMm": 25,
      "keywords": ["族群裔兵", "基因窃取者教派", "泰伦虫族", "族群护卫", "队长", "指挥官"],
      "cards": ["KT-BROOD_BROTHERS--01-特工-裔兵指挥官-数据卡.png"],
      "weapons": [
        { "id": "kt_bb_cmd_bolt_pistol", "name": "爆矢手枪", "kind": "ranged",
          "profile": "4A · 3+ · 3/4 · 范围 8\"" },
        { "id": "kt_bb_cmd_chainsword", "name": "链锯剑与利爪", "kind": "melee",
          "profile": "4A · 3+ · 4/5 · 平衡、撕裂" }
      ],
      "defaultWeaponIds": ["kt_bb_cmd_bolt_pistol", "kt_bb_cmd_chainsword"],
      "abilities": [
        { "name": "协同", "ap": null, "text": "（卡面中文全文）" },
        { "name": "干扰", "ap": 1, "apLabel": "1/2AP", "text": "（卡面中文全文）" }
      ]
    }
  ],
  "weaponRules": [
    { "name": "隐蔽位置", "text": "（卡面中文全文，带 * 的专属武器规则）" }
  ]
}
```

## 约定

- **profile**：`攻击A · 命中+ · 普通/暴击 · 规则1、规则2`，与 APP 现有格式一致。无规则时省略最后一段。专属武器规则写名称不带星号（如 `中毒`），定义放到 `weaponRules`。
- **kind**：`ranged` / `melee`，取自卡面武器图标（枪 = 远程，刀 = 近战）。
- **同名不同档案**的武器（如“等离子手枪（标准）/（过载）”）分为两条，名称带括号区分。
- **teamId**：已有 APP ID 的沿用，其余**严格按 `tools/team_registry.py` 输出**：`"KT-" + slug.upper().replace("-", "_")`，例如 `KT-BROOD_BROTHERS`（下划线，不是连字符）。
- **id**：全小写、下划线；同一支小队内唯一（武器 id 也须全队唯一，建议 `kt_<队缩写>_<特工缩写>_<武器>`）。
- **abilities**：特工专属能力与独特行动，`ap` 为 AP 消耗（独特行动）或 `null`（被动能力）。`ap` 必须是整数；卡面写成可变消耗（如“1/2AP”）时 `ap` 填最低值，另加可选字段 `apLabel` 保存原样文字（加载器可忽略）。独特行动的效果（▶）与限制（◆）两部分用 `\n` 分段。文字取卡面中文原文，两栏混排要按段落理顺；勘误按“13. 更新日志”并入。
- **cards**：特工的数据卡与能力卡文件名（已复制到 `team_rules/`）。命名 `<TEAM_ID>--NN-<分类>-<标题>.png`，NN 为 manifest 中卡片的顺序号（01 起两位连续编号），`<分类>-<标题>` 沿用卡片提取目录里的文件名，例如 `01-07-特工能力-裔兵医务兵-能力卡.png` → `KT-BROOD_BROTHERS--07-特工能力-裔兵医务兵-能力卡.png`。数据卡放第一位（文件名含“数据卡”），能力卡随后（含“能力卡”）。
- **keywords**：卡面页脚中文关键词，按卡面顺序；勘误新增的关键词（如“泰伦虫族”）卡面已含则不重复。
- **draft**：`true` 表示骨架尚未校对，APP 不加载；校对完成并通过校验后删除该字段。
- **archetypes**：小队原型（渗透 / 侦察 / 安全保护 / 搜索与摧毁），取自“小队选择”卡的“原型：”一行；APP 用它显示小队副标题、筛选战术行动卡。
- **nameEn**（可选）：英文队名，取自 registry。
- **role**：特工定位，取卡面页脚最后一个关键词（即特工类型名，如 指挥官 / 炮手 / 医务兵 / 族长）；不自行概括。
- 编成规则（每种特工最多几名等）暂不做硬校验，写在 rules JSON 的“编成与武器选择”里供玩家参照。

## 校验

完成后运行：

```bash
python3 -I tools/check_team_data.py <TEAM_ID>
```

`--all` 校验 `assets/teams/` 下全部文件。检查：结构与 id 唯一性；每张特工数据卡都有对应 operative、每张能力卡都被引用；属性 / 武器与 `docs/<小队>-卡片提取/manifest.json` 中卡面文字一致；所有 profile 能被解析；武器 kind 与 manifest `weapon_types` 一致；武器规则要么是核心规则（`core_rules.json`），要么在 `weaponRules` 里有定义，且 `weaponRules` 每条都被引用；`cards` 指向的文件存在。
