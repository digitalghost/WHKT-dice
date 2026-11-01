# 杀戮小队规则接入 APP 全流程（操作手册）

> 适用对象：后续接手的人或 Agent。读完本文就能把任意一支杀戮小队从“官方 PDF”一路做到“在 APP 里可编成、可对局、规则可查”。
> 最后更新：2026-10-26。
> 项目根目录：`/Users/alanliu-15air/Projects/anroid1st`（下文路径均相对于此）。
> 所有对用户的说明用中文；代码、命令、文件名保持原样。

---

## 0. 一页速览

### 0.1 APP 的定位（决定了“接入”要做到什么程度）

- APP 是**记录式、规则引导**的对局台：玩家在桌上用真模型对战，APP 负责引导**核心规则**流程（先手、战略、交战、射击、近战、CP、VP）并做校验与记录。
- **小队规则不追求自动化**。APP 不会“知道”阿斯塔特可以射两次；而是提供足够自由的通用工具（规则依据、状态标记、修饰、改判骰子、免费行动、跳过激活、授予武器……），让玩家自己点出来并留下清楚记录。
- 因此**新增一支小队 = 只补资料（JSON + 卡图），不写 Kotlin**。唯一例外是小队标记图标（见 §11，待改为数据驱动）。
- APP **不追踪距离、位置、可见性、地形**，这些永远由玩家判断，不算规则缺口。

### 0.2 一支小队从零到上线的流水线

| 阶段 | 做什么 | 主要工具 / 产物 | 人工量 |
|---|---|---|---|
| A | 下载官方中/英文 PDF | `tools/download_kill_team_rules.py` → `docs/最新杀戮小队规则更新/` | 无 |
| B | 切卡图、抽卡面文字 | `tools/extract_kill_team_cards.py` → `docs/<小队>-卡片提取/` | 抽查 |
| C | 生成整理资料文档 | `tools/build_team_doc.py` → `docs/<小队>-完整规则资料.md` | 抽查 |
| D | 登记小队 ID | `tools/team_registry.py` | 新队时核对 |
| E | 生成 APP 数据骨架（draft） | `tools/gen_team_skeleton.py`、`tools/gen_team_rules_skeleton.py` | 无 |
| F | **对照卡图校对骨架**（核心工作） | `docs/小队数据校对任务说明.md` | **约 50 分钟/队** |
| G | 卡图转 WebP、删 PNG | 见 §8 命令 | 无 |
| H | 跑校验脚本 | `tools/check_team_data.py`、`tools/check_team_rules.py` | 修到通过 |
| I | 编译、单测、装模拟器实测 | Gradle + adb | 抽测 |
| J | 小队标记图标 | `tools/extract_team_mark.py` + `TeamMarks.kt`（待数据化） | 少量 |

### 0.3 当前进度

详见 `docs/小队数据接入进度.md`（随每批更新）。2026-10-26 节点：8 支内置 + 12 支数据驱动已上线，28 支为草稿。

查看最新状态的命令：

```bash
cd /Users/alanliu-15air/Projects/anroid1st
grep -l '"draft": true' app/src/main/assets/teams/*.json | wc -l      # 待校对的 teams JSON 数
grep -L '"draft": true' app/src/main/assets/teams/*.json              # 已上线的数据驱动小队
python3 -I tools/check_team_data.py --all | grep -E '^\[|^结果'
python3 -I tools/check_team_rules.py | tail -1
```

> 注意：`check_team_data.py` 目前**不会**因为 `draft` 或能力为空而判失败，所以骨架也可能显示“[通过]”。判断是否完成以 `draft` 字段为准（见 §13 已知问题）。

---

## 1. 文件地图

### 1.1 资料（docs/，只读来源）

| 路径 | 内容 | 是否入库 |
|---|---|---|
| `docs/最新杀戮小队规则更新/*.pdf` | 48 份官方中文小队规则 PDF | 否（GW 版权，`.gitignore` 忽略） |
| `docs/最新杀戮小队规则更新/manifest.json` | 下载清单：URL、官方更新日期、SHA256 | 是 |
| `docs/最新杀戮小队规则更新_EN/*.pdf` | 同批英文 PDF，仅用于中英对照 | 否 |
| `docs/<小队>-卡片提取/*.png` | 每张卡的独立卡图（200 DPI），**唯一权威来源** | 视情况 |
| `docs/<小队>-卡片提取/manifest.json` | 卡片清单：分类、标题、卡面文字层 `text`、英文 `text_en`、`weapon_types` | 是 |
| `docs/<小队>-完整规则资料.md` | 15 章整理资料（第 4 节阵营规则、第 6 节特工能力、第 8–10 节计谋/装备、第 13 节更新日志与勘误） | 是 |
| `docs/杀戮小队三版官方规则-v11.5-全文提取.md` | 核心规则书全文（按 PDF 页） | 是 |
| `docs/杀戮小队三版 官中修订版 v11.5.pdf` | 核心规则书 PDF | 否 |

### 1.2 APP 数据（app/src/main/assets/）

| 路径 | 内容 |
|---|---|
| `teams/<TEAM_ID>.json` | **数据驱动小队**：特工、属性、武器、专属能力、专属武器规则、原型。格式见 `docs/小队数据JSON格式.md` |
| `rules/<TEAM_ID>.json` | 小队规则书：阵营规则、战略计谋、交战计谋、阵营装备、编成、标识指南、勘误与问答（8 支内置队也用它） |
| `team_rules/<TEAM_ID>--<原卡图文件名>.webp` | 卡图（WebP，质量 88）。JSON 里仍写 `.png` 名，运行时自动对应 |
| `equipment/` | 旧版装备卡图（部分内置队和通用装备） |
| `core_rules.json` | 核心规则主题（射击、近战、受创、计谋、武器规则…），缩进为 **1 个空格**，改写时保持 `indent=1` |

### 1.3 工具（tools/）

| 脚本 | 作用 |
|---|---|
| `download_kill_team_rules.py` | 从 Warhammer Community 下载中文小队规则 PDF，幂等、自愈。详见 `docs/最新杀戮小队规则自动下载说明.md` |
| `detect_card_frames.py` | 识别 PDF 页面上的卡片裁切框 |
| `extract_kill_team_cards.py` | 切卡图、抽文字、生成 `manifest.json` 和 `README.md` |
| `merge_en_reference.py` | 把英文 PDF 的对应文字并入中文 manifest |
| `extract_weapon_icons.py` | 识别武器图标（枪/刀）→ `weapon_types` |
| `verify_team_extract.py` | 卡片提取验收 |
| `build_team_doc.py` | 生成 `<小队>-完整规则资料.md` |
| `ktpdf.py` | PDF 读取辅助 |
| `team_registry.py` | 48 支小队登记表：ID、中英文名、PDF 路径、卡片目录、资料路径 |
| `gen_team_skeleton.py` | 生成 `teams/<ID>.json` 骨架并复制卡图 |
| `gen_team_rules_skeleton.py` | 生成 `rules/<ID>.json` 骨架 |
| `check_team_data.py` | 校验 teams JSON（对照 manifest 文字层核对数值） |
| `check_team_rules.py` | 校验 rules JSON（每张规则卡都有条目、正文非空、卡图存在） |
| `extract_team_mark.py` | 从卡片提取小队标记图标 |
| `seed_emulator_rosters.rb` | 往空模拟器里种两支演示小队（死亡天使、瘟疫战士） |
| `emulator_ui.rb` | 模拟器 UI 点按/读屏小工具 |

### 1.4 相关说明文档

- `docs/小队数据JSON格式.md`：teams JSON 字段规范（必读）。
- `docs/小队数据校对任务说明.md`：给校对代理的逐条要求（必读）。
- `docs/杀戮小队中英数据整理工作流.md`：A–C 阶段的详细工作流，含“10.5 小队规则 JSON 提取规范”。**注意：此文件被 `.gitignore` 第 23 行忽略，不进版本库**；如需保留请调整 `.gitignore`。
- `docs/最新杀戮小队规则自动下载说明.md`：A 阶段详解。

---

## 2. 阶段 A：下载官方 PDF

```bash
cd /Users/alanliu-15air/Projects/anroid1st
python3 tools/download_kill_team_rules.py --list   # 只看会下载/排除什么
python3 tools/download_kill_team_rules.py          # 下载或增量更新（约 8 秒增量检查）
```

- 只下载官方分类为“小队规则”的中文文档；任务包、核心规则、通用装备一律排除。
- 结束后检查 `docs/最新杀戮小队规则更新/manifest.json` 的 `counts`、`skipped`。
- 英文版放 `docs/最新杀戮小队规则更新_EN/`，用于对照（同批次版面与中文一致）。
- **核心规则书不在下载范围**。若官方发布新版核心规则，需要手工替换 PDF 并重建 `core_rules.json`（见 §12.3）。
- 官方小队 PDF 更新后（manifest 中 `last_updated` 变化），需要对该队重新走 B → H。

---

## 3. 阶段 B：切卡图、抽文字

```bash
python3 tools/extract_kill_team_cards.py "docs/最新杀戮小队规则更新/<中文名> <English> 杀戮小队规则.pdf" \
    --out-dir "docs/<中文名>-卡片提取" \
    --team-id <TEAM_ID> --team-name-zh <中文名> --team-name-en "<English>"
python3 tools/merge_en_reference.py "docs/<中文名>-卡片提取/manifest.json" "docs/最新杀戮小队规则更新_EN/<English> 杀戮小队规则.pdf"
python3 tools/verify_team_extract.py "docs/<中文名>-卡片提取"
```

产物 `manifest.json` 的关键字段：

| 字段 | 说明 |
|---|---|
| `cards[].category` | `operative`（数据卡）、`operative-ability`（能力卡）、`team-selection`（小队选择/编成）、`faction-rule`、`strategic-ploy`、`firefight-ploy`、`faction-equipment`、`other`（标识指南等） |
| `cards[].title_zh` | 卡片标题，形如 `裔兵指挥官-数据卡`。**注意：有的队多张卡标题相同**（如 XV26 六张都叫 `XV26-数据卡`） |
| `cards[].image` | 卡图文件名，形如 `01-01-特工-裔兵指挥官-数据卡.png`（两段编号）或 `13-小队选择-xx.png`（一段编号，旧格式） |
| `cards[].text` | 卡面文字层。**两栏/三栏经常混排在同一行，约一半数据卡的能力文字缺失**，只能作辅助 |
| `cards[].weapon_types` | `{武器名: "远程"/"近战"}`，来自武器图标识别 |
| `cards[].is_back` | 背面续栏 |

---

## 4. 阶段 C：整理资料文档

```bash
python3 tools/build_team_doc.py --card-dir "docs/<中文名>-卡片提取" \
    --pdf-zh "docs/最新杀戮小队规则更新/<…>.pdf" --pdf-en "docs/最新杀戮小队规则更新_EN/<…>.pdf" \
    --out "docs/<中文名>-完整规则资料.md" \
    --team-name-zh <中文名> --team-name-en "<English>" --official-date <DD/MM/YYYY> --team-id <TEAM_ID>
```

用途与可信度：

- 第 8–10 节（计谋、装备）的 **“中文全文”段落基本完整**，是 rules JSON 骨架正文的来源。
- 第 6 节（特工能力）**两栏混排严重**，不能直接用，只能对照卡图转录。
- 第 13 节（更新日志/勘误）也常混排；勘误最好看卡图或 PDF 勘误页渲染图。
- 带 `*` 的专属武器规则，名字在武器表里，**定义原文在第 6.2 节或卡片文字层里**（48 队共约 60 条，58 条原文都在，只是混排；真正无原文的只有洁天使“反灵能者”、胡蜂“中子轰炸”）。

---

## 5. 阶段 D：小队 ID

`tools/team_registry.py` 是唯一的 ID 来源：

- 已在 APP 中的沿用原 ID：`IMP-AOD`、`CHAOS-PM`、`CHAOS-WC`、`CHAOS-FELL`、`IMP-CI`、`IMP-WS`、`NEC-CAN`、`TYR-RAV`、`WYRM`。
- 其余：`"KT-" + slug.upper().replace("-", "_")`，例如 `KT-BROOD_BROTHERS`（**下划线，不是连字符**）。
- ID 一旦上线不要改：存档的小队与对局用它关联。
- 查询：`python3 -I tools/team_registry.py`。

---

## 6. 阶段 E：生成 APP 数据骨架

```bash
python3 -I tools/gen_team_skeleton.py <中文名或TEAM_ID>        # → assets/teams/<ID>.json + 复制卡图
python3 -I tools/gen_team_rules_skeleton.py <中文名或TEAM_ID>  # → assets/rules/<ID>.json
# 已存在不覆盖；重建加 --force（会丢掉人工校对内容，慎用）
```

骨架生成器做了什么、没做什么：

| 自动完成 | 必须人工补 |
|---|---|
| 每张数据卡 → 一个特工；APL/移动/豁免/耐伤 | `role`、`baseSizeMm`、`subtitle`、`archetypes` |
| 每行武器 → 攻击/命中/伤害/规则；远程/近战取 `weapon_types` | 同名卡导致的重名特工（`XX（2）`）要按卡图改名或合并 |
| 能力卡挂到前一个特工的 `cards` | `abilities` 全部（骨架为空列表） |
| 带 `*` 的规则 → `weaponRules` 占位（text 为空） | `weaponRules[].text`；误标 `*` 的核心规则要删 |
| 卡图复制到 `team_rules/<ID>--<原名>.png` | 默认武器（骨架默认“全部武器”） |
| 规则条目：每张规则卡一条，跨两张卡（`-1/-2`）合并 | 正文混排/断句修正、勘误并入 |
| 文件写入 `"draft": true` | 校对完成后删除 `draft` |

`"draft": true` 的文件 **APP 不加载**（`TeamDataLoader` 与 `TeamRuleBook` 都会跳过），所以骨架可以放心提交到工作区，不会让半成品出现在 APP 里。

> 已知：`gen_team_skeleton.py` 复制的是 **PNG**。生成后必须执行 §8 的转 WebP，否则安装包会暴涨。

---

## 7. 阶段 F：校对（核心人工环节）

完整要求见 `docs/小队数据校对任务说明.md`。要点重述：

### 7.1 资料优先级

1. **卡图 PNG**（`docs/<小队>-卡片提取/*.png`，用 Read 工具直接看图）——唯一权威。
2. 资料文档第 13 节 / PDF 勘误页——勘误必须并入正文。
3. manifest 文字层、资料文档其他章节——只作辅助。

### 7.2 teams JSON 校对清单（逐个特工）

- [ ] 名称与卡面一致（重名卡按卡面真实名称区分，如“XV26 标记者”）。
- [ ] APL / 移动 / 豁免 / 耐伤 与卡图一致。
- [ ] 每件武器：名称、攻击、命中、普通/暴击伤害、全部武器规则（含数值，如 `致命 5+`、`范围 8"`）。
- [ ] `kind`：卡面图标枪=远程 `ranged`，刀=近战 `melee`。
- [ ] 同名不同档案拆成多条，括号区分：`等离子手枪（标准）`/`（过载）`。
- [ ] `role`：卡面页脚最后一个关键词（指挥官 / 炮手 / 医务兵…）。
- [ ] `keywords`：页脚中文关键词，按卡面顺序。
- [ ] `defaultWeaponIds`：按编成卡第一种装备选项；编成卡没提到的（装备类武器）不放默认；拿不准就全选并在汇报中说明。
- [ ] `abilities`：卡上每个专属能力和独特行动都要有：`{"name","ap","text"}`。
  - `ap`：独特行动填整数 AP，被动能力填 `null`；卡面写“1/2AP”时 `ap` 填最低值，另加 `apLabel` 存原文。
  - 独特行动的效果（▶）与限制（◆）用 `\n` 分段；多段条目用 `• ` 开头分行；表格改写成“条件：效果”逐行文字。
  - 两栏混排必须理顺为连贯段落，**不概括、不漏字**；勘误并入正文。
- [ ] `cards`：数据卡在前、能力卡在后，文件名带 `<TEAM_ID>--` 前缀（写 `.png` 名）。
- [ ] 若文字层与卡图不一致而以卡图为准：特工上加 `"verifiedAgainstImage": true`，并在汇报里说明。

小队级：

- [ ] `subtitle`：如 `泰伦虫族 · 基因窃取者教派 · 15 种特工`。
- [ ] `archetypes`：编成卡“原型：”一行，如 `["渗透","安全保护"]`（APP 用它显示副标题、筛选战术行动卡）。
- [ ] `weaponRules`：卡面带 `*` 的专属规则全文；不带 `*` 的核心规则（撕裂、穿刺、致命…）不得出现在这里。
- [ ] 删除 `"draft": true`。

### 7.3 rules JSON 校对清单

- [ ] 每条正文与卡图逐字核对（骨架来自资料文档，可能断句、漏行、混排）。
- [ ] 跨两张卡的条目包含两张卡的全部内容。
- [ ] 第 13 节勘误并入正文（可在正文末尾注明“已并入 2026-xx 勘误”）。
- [ ] 标题与卡图文件名一致（计谋标题会作为 APP 快速选择的预制值）。勘误改名的条目：标题用新名，`check_team_rules.py` 里有改名对照表（`renamed`），新增改名时同步补表。
- [ ] 删除 `"draft": true`。

### 7.4 汇报内容（批量时每支队都要交）

条目数（特工/武器/能力/专属规则/规则条目）、两个校验脚本最后几行输出、起止时间、拿不准的转录（标卡图文件名）、对任务说明的改进建议。

### 7.5 耗时参考

| 小队 | 耗时 | 说明 |
|---|---|---|
| 族群裔兵 | ~6 小时墙钟（纯工作 1.5–2 h） | 第一支，摸索流程 |
| XV26 隐形战斗服 | ~51 分钟，约 24 万 token | 用现成骨架 + 任务说明 |

看卡图转录约占 70% 时间。批量时按 XV26 估算：每队约 50 分钟、约 24 万 token。

---

## 8. 阶段 G：卡图转 WebP

APP 内只放 WebP（质量 88，体积约 PNG 的 1/7，文字清晰）。`docs/` 下的 PNG 原图保留不动。

```bash
cd /Users/alanliu-15air/Projects/anroid1st
python3 - <<'EOF'
import os
from PIL import Image
d = 'app/src/main/assets/team_rules'
for f in sorted(os.listdir(d)):
    if not f.endswith('.png'): continue
    p = os.path.join(d, f); w = p[:-4] + '.webp'
    if not os.path.exists(w):
        im = Image.open(p)
        im = im.convert('RGBA') if im.mode in ('RGBA', 'LA', 'P') else im.convert('RGB')
        im.save(w, 'WEBP', quality=88, method=6)
EOF
# 确认每张 PNG 都有 WebP 后再删 PNG：
cd app/src/main/assets/team_rules
for f in *.png; do [ -s "${f%.png}.webp" ] || echo "缺 WebP: $f"; done
find . -maxdepth 1 -name '*.png' -type f -delete
```

- JSON 与代码里**继续写 `.png` 文件名**。运行时由 `CardAssets`（`app/src/main/java/com/example/helloworld/CardAssets.kt`）自动解析到 `.webp`；校验脚本也同时接受两种扩展名。
- 1448 张卡图转换后：664 MB → 85 MB。APK 当前约 183 MB（其中 `team_rules` ~87 MB、`res/drawable-nodpi` ~30 MB）。
- 删除文件前务必先确认 WebP 存在；删除操作需要用户同意（自动模式会拦截不可逆删除）。

---

## 9. 阶段 H：校验

```bash
python3 -I tools/check_team_data.py <TEAM_ID>      # 或 --all
python3 -I tools/check_team_rules.py               # 全部小队
```

### 9.1 check_team_data.py 检查项

1. 结构：必填字段与类型；特工 id、武器 id 在队内唯一；`kind` 合法；`defaultWeaponIds` 指向本特工武器；`ap` 为整数或 null。
2. manifest 中每张数据卡都被某个特工覆盖（按名称或 `cards` 引用卡图判定，支持同名卡）。
3. 属性与卡面文字层一致（`verifiedAgainstImage` 可豁免）。
4. 每件武器名称/攻击/命中/伤害出现在卡面文字层同一行（`verifiedAgainstImage` 可豁免）；`kind` 与 `weapon_types` 一致。
5. `profile` 可解析：`攻击A · 命中+ · 普通/暴击[ · 规则、规则]`。
6. 每个武器规则名要么是核心规则，要么在 `weaponRules` 定义（核心规则名单 + 别名：关键穿刺、穿刺暴击、眩晕、均衡、追踪轻型；带距离前缀的 `2" 毁灭 1` 会被识别为毁灭）；`weaponRules` 每条都被引用、text 非空。
7. `cards` 指向的卡图存在（.png 或 .webp），且以 `<TEAM_ID>--` 开头；每张能力卡都被引用。

### 9.2 check_team_rules.py 检查项

- 每张阵营规则/战略计谋/交战计谋/阵营装备/小队选择卡（含 `equipment/` 目录下的装备卡）都有对应条目，标题做规范化比较（去括注、标点、空格），支持勘误改名对照。
- 条目正文非空，引用的卡图存在。
- 卡图文件名正则已支持 `--NN-` 与 `--NN-NN-` 两种编号格式、.png/.webp 两种扩展名。

---

## 10. 阶段 I：编译、测试、模拟器实测

### 10.1 环境

```bash
cd /Users/alanliu-15air/Projects/anroid1st
export JAVA_HOME=$PWD/.jdk/jdk-17.0.20.1+1/Contents/Home   # 系统没有全局 Java
./gradlew -q compileDebugKotlin
./gradlew -q testDebugUnitTest assembleDebug
# 统计单测结果：
python3 -c "
import glob,re
t=f=0
for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    s=open(p).read(); m=re.search(r'tests=\"(\d+)\".*?failures=\"(\d+)\"',s); t+=int(m[1]); f+=int(m[2])
print(t,f)"
```

当前单测 156 个全部通过。

### 10.2 安装与模拟器

```bash
A=android-sdk/platform-tools/adb          # adb 不在 PATH 里
$A devices -l                              # emulator-5554 = 平板 2560x1600；emulator-5556 = 手机 1080x1920
for s in emulator-5554 emulator-5556; do $A -s $s install -r app/build/outputs/apk/debug/app-debug.apk; done
$A -s emulator-5556 shell am start -W -n com.example.helloworld/.BattleHubActivity
```

### 10.3 测试数据

- 旧对局保存了开局时的武器快照，旧小队也可能与新数据不兼容。**测试前可以删掉旧对局和旧小队，新建后再测**（用户已同意此做法）。
- 只删 `battle_records.xml`、`battle_view_state.xml`、`saved_rosters.xml`；保留 `dice_history.xml`、`ui_preferences.xml`、`files/operative_photos/`。删除前先备份：

```bash
for f in battle_records battle_view_state saved_rosters; do
  $A -s emulator-5556 exec-out run-as com.example.helloworld cat shared_prefs/$f.xml > /tmp/backup-$f.xml
done
$A -s emulator-5556 shell run-as com.example.helloworld rm shared_prefs/battle_records.xml shared_prefs/battle_view_state.xml shared_prefs/saved_rosters.xml
ruby tools/seed_emulator_rosters.rb emulator-5556   # 可选：种两支演示小队（仅在小队为空时生效）
```

### 10.4 新小队上线后的实测清单

- [ ] 我的小队 → 新建 → 列表里出现该队，副标题显示原型和成员数。
- [ ] 添加成员：特工属性、武器、关键字链接、能力原文、人物卡图都正常。
- [ ] 小队规则：规则/战略/战术/装备各节有正文和卡图。
- [ ] 新建对局 → 装备选择显示该队阵营装备。
- [ ] 战略阶段“使用计谋”列出本队战略计谋；交战阶段点 CP“−”列出交战计谋。
- [ ] 对局中特工行显示 `APL · 移动 · 豁免`，菜单里能看到武器。
- [ ] 至少打一次射击、一次近战，专属武器规则点开能看到原文。

### 10.5 模拟器操作的坑

- `adb shell input text` 不能输入中文和 `·`，测试时用英文/ASCII（手填武器档案可用空格分隔：`4A 2+ 4/5`）。
- 按 BACK 收键盘时若键盘没弹出，会直接退出当前页面；改用 `input keyevent 111`（ESC）。
- `uiautomator dump` 偶尔返回旧画面，必要时截图确认。
- 平板截图被缩放到 2000 宽显示，坐标要乘 1.28 才是真实坐标。

---

## 11. 阶段 J：小队标记图标（目前仍需改代码）

- `TeamMarks.kt` 只为 8 支内置队映射了 `R.drawable.team_mark_*`，其他队回退成死亡天使标记。
- 提取标记：`tools/extract_team_mark.py`（详见《杀戮小队中英数据整理工作流》4.7.1）。
- **待办（建议）**：把标记也数据化——提取到 `assets/team_marks/<TEAM_ID>.webp`，在 teams JSON 增加 `mark` 字段或按 ID 约定查找，修改 `TeamMarks.resourceOrNull` / `CombatKit.emblem` 支持 asset。完成后新增小队就完全不用改代码。

---

## 12. APP 端是怎么接入这些数据的（给改代码的人）

### 12.1 加载链路

| 组件 | 文件 | 作用 |
|---|---|---|
| `KillTeamApp` | `KillTeamApp.kt`（Manifest `android:name=".KillTeamApp"`） | 启动时调用 `TeamDataLoader.ensureLoaded` |
| `TeamDataLoader` | `TeamDataLoader.kt` | 扫描 `assets/teams/*.json`，跳过 `draft`；解析成 `KillTeamCatalog`；登记能力、专属武器规则、近战武器 id、原型 |
| `RosterCatalog` | `RosterModels.kt` | `teams` = 内置 8 队 + 注册的数据队（同 ID 以内置为准）；`operative()` 先查本队再查全部队（支持征用） |
| `OperativeTemplate.cardAsset / abilityAssets` | `RosterModels.kt` | 数据队的人物卡/能力卡来自 assets；`cardRes` 是占位图标 |
| `AbilityCatalog.forOperative` | `RuleGlossary.kt` | 依次查 Fellgor、Imported、`TeamDataLoader.abilities` |
| `RuleGlossary.definitionFor` | `RuleGlossary.kt` | 武器关键字说明，含 `TeamDataLoader.weaponRule` |
| `WeaponActions.legacyKind` | `WeaponActions.kt` | 近战判定含 `TeamDataLoader.isMelee` |
| `TeamArchetypes` | `TeamArchetypes.kt` | 内置表 + 数据队注册的原型 |
| `TeamRuleBook` | `TeamRuleBook.kt` | 读 `rules/<ID>.json`（`available` = 文件存在；`draft` 返回空） |
| `TeamPloys` | `TeamPloys.kt` | 计谋预制值：优先 rules JSON 正文，缺失时退回卡图文件名 |
| `EquipmentCards.cards` | `EquipmentCards.kt` | 阵营装备优先读 rules JSON 的“阵营装备”条目；旧名通过 `RENAMED` 映射 |
| `CardAssets` | `CardAssets.kt` | `.png` 名自动解析到 `.webp`；所有读卡图的地方都经过它 |
| `CoreRules` | `CombatKit.kt` | 读 `core_rules.json` |

### 12.2 内置 8 支队的数据在哪里

- 特工与武器：`RosterModels.kt`（死亡天使、瘟疫战士、蛇虫）、`WarpCovenCatalog.kt`、`FellgorCatalog.kt`、`ImportedTeamCatalog.kt`（冥工之环、狼侦察、洁天使隐伏者，由旧版 `tools/import_prepared_teams.py` 生成）。
- 规则书：`assets/rules/<ID>.json`（已按 2026-08 官方中文卡面文本重建）。
- 武器 id 保持不变，以免存档小队丢失武器选择。
- **长期建议**：把这 8 支也迁移到 `assets/teams/*.json`，删除 Kotlin 硬编码，统一走 §6–§9。迁移时保持特工/武器 id 不变。

### 12.3 更新内置队 / 核心规则时的做法（2026-10-25 的经验）

- 对比方法：临时写一个单测把 `RosterCatalog.teams` 导出成 TSV（Android 单测里 `org.json` 是空桩，不能用 `JSONObject`，要手写字符串），再用脚本解析 manifest 数据卡文字层逐项 diff，**所有差异都要看卡图确认**再改 Kotlin。用完删除临时单测。
- 规则 JSON：用资料文档的“中文全文”覆盖正文，按标题匹配；改名的条目保留原 id、更新标题；不在卡上的脚注规则（如洁天使“反灵能者”）保留。
- `core_rules.json` 只覆盖部分主题；曾经漏提取第 20 页“受创”定义，已补。重建时注意页码范围，写回时 `indent=1`。

---

## 13. 已知问题与坑

| 问题 | 影响 | 处理 |
|---|---|---|
| `check_team_data.py` 对草稿只提示不判失败 | 骨架也可能显示“通过” | 输出里会标“草稿（draft）”并在末尾列出草稿清单；能力全空会警告 |
| manifest 同名卡（XV26 等） | 骨架生成重名特工 | 校对时按卡图命名；校验已改为按卡图引用判定 |
| 文字层两栏混排、约半数能力文字缺失 | 骨架能力为空 | 只能看卡图转录 |
| 骨架生成器复制 PNG | 安装包暴涨 | 每次生成后执行 §8 |
| 编成规则未做硬校验 | 玩家可编出不合法小队 | 规则写在 rules JSON“编成与武器选择”供参照 |
| 内置 8 队仍在 Kotlin | 更新要改代码 | 见 §12.2 迁移建议 |
| 小队标记未数据化 | 新队显示死亡天使标记 | 见 §11 |
| `.gitignore` 忽略 `docs/杀戮小队中英数据整理工作流.md` 和 `docs/*-基础信息-角色与武器.md` | 这些文档不入库 | 需要时调整 `.gitignore` |
| 曾有其他 Agent（MiniMax Code）同时改仓库，删除了 52+ 个已跟踪文件（`docs/images`、`docs/licenses`、`assets/ruler-backgrounds/` 等） | 工作区有大量 `D` 状态 | 尚未恢复；恢复前确认没有其他进程在改仓库，可 `git checkout -- <路径>` |
| Kotlin KDoc 里写 `assets/teams/*.json` | `/*` 开启嵌套注释导致编译失败 | 注释里不要出现 `/*` |
| Python heredoc 改 Kotlin 时 `\"` 被反转义 | 字符串引号出错 | 写完立即编译 |
| 旧对局保存武器快照 | 数据更新后旧对局仍显示旧值 | 测试前删旧数据（§10.3） |

---

## 14. 对局中小队规则怎么处理（规则工具一览）

APP 的原则：核心规则自动引导和校验；小队规则交给玩家用通用工具记录，**所有越过核心规则的操作都要求填“依据”并写入日志**。

### 14.1 已有的通用工具

| 工具 | 入口 | 典型用途 |
|---|---|---|
| 规则例外记录（依据必填，写入行动记录） | 行动被核心规则拦截时弹出 | 重复射击、隐匿冲锋、反应第二个行动（亚空间残响）、移动后冲锋等 |
| 跳过本次激活 | 交战阶段底栏「规则：顺序…」 | 恶兆、耐心埋伏、C.A.T. |
| 指定对方下一次必须激活谁 | 同上 | 阴魂不散的注视 |
| 禁止激活 | 状态标记名 `不能激活` | 磨难耳语 |
| 连续激活 / 联合激活（两名特工同时激活、可切换） | 激活面板「AP · 规则…」 | 控制律令、变种兽群、紧急精神链接 |
| APL ±1、返还 1AP、额外消耗 1AP | 同上 | 免费行动、APL 修正 |
| 获得武器（借用本队数据卡武器 / 手填档案）、移除授予武器 | 特工菜单「规则：武器 / 数据卡 / 控制…」 | 私掠者舰炮、爆破炸药、风暴军匕首、灵能阴谋团 |
| 更换数据卡（可保留已损失耐伤） | 同上 | 混沌教派变异 |
| 被对方临时控制执行一个免费行动 / 改命令 | 同上 | 心灵控制 |
| 由规则新增特工（就绪/待机、是否计入击杀） | 队伍栏「＋ 特工」 | 变异害虫、召唤、增援 |
| 装备上限 +N | 装备选择器「规则允许额外装备」 | 贸易许可 |
| 从其他小队征用特工 | 添加成员页底部 | 审判庭征用命令 |
| 状态标记（名称、数值、7 种持续时间，到期自动移除） | 特工菜单「状态标记…」 | 中毒、标记、各种持续效果 |
| 小队计数器（可每转折点重置） | 队伍栏「计数器」 | 节点、预感点数、资源 |
| 战略/交战计谋预制 + 自由填写，可改 CP | 战略阶段「使用计谋」/ 交战阶段 CP“−” | 所有计谋 |
| 结算修饰（增减关键字、攻击/命中/伤害/豁免/防御骰数） | 射击/近战准备步骤「＋ 修饰」 | 大多数加成类规则 |
| 不掷骰预留（掷骰前，攻防都有） | 射击/近战准备、防御骰前 | 埋伏、冲动决心、变色孢子迷彩 |
| 骰子改判 / 改点数 / 其他规则重投 | 点骰子 | 各种重投、改判规则 |
| 可选武器规则手动应用（重击、撕裂、严重） | 保留攻击骰步骤 | 核心武器规则 |
| 伤害逐次调整、耐伤调整、舍弃对手成功、改由对方结算 | 结算步骤 | 减伤、额外伤害、决斗类规则 |
| 爆炸/洪流多目标（排除已射击目标；爆炸含己方） | 射击结果页「射击下一目标」 | 核心规则 |

### 14.2 尚未做的（48 队覆盖度审阅结论，优先级从高到低）

审阅共发现 199 个缺口：14 个“无法记录”（**已全部完成**，即 14.1 中新增的工具），其余为“很繁琐/易出错”，计划按通用机制补：

1. **结构化状态标记**：标记附带效果（APL/移动/豁免/命中修正、武器规则、忽略受创、免疫震荡），在激活开始/结束、就绪步骤等时机提醒或一键“掷 XdY 扣伤”。覆盖 40+ 队（中毒、中子碎片、燃烧、圣勉、混沌印记…）。
2. **持续修饰**：修饰挂在计谋/标记/队伍选项上，在持续时间内自动预填进每次结算，可取消勾选。30+ 队。
3. **常驻规则豁免**：小队/特工声明“同名行动 N 次 / 任何命令可反应 / 隐匿可射击或冲锋”，校验自动放行并写依据。20+ 队。
4. **插入式免费行动**：激活外、对方回合中、战略阶段、残废后移除前做一个行动，不改变就绪状态。20+ 队。
5. **通用规则伤害/掷骰工具**：选多名特工（敌我），按 XdY+Z 或拼骰扣耐伤并记录来源。15+ 队。
6. **限次能力按钮**：数据里声明“每场/每转折点 N 次”，自动计次。
7. **结算流程扩展**：一次格挡挡 N 个、普通挡暴击、提前结束近战、任意规则开启多目标射击（乱射*）。
8. **零散项**：交战计谋改费用已支持；修饰可加全部核心关键字（含过热）、过热检定改点数、先手骰改点数。

原始逐队缺口清单曾存于 `/tmp/kt/gaps.tsv`、`/tmp/kt/coverage.json`（临时目录，可能已被清理）。**待办**：重新生成时保存到 `docs/小队规则覆盖度审阅.tsv` 入库。

> 设计原则：只做“通用、可复用于多队”的工具，不写某队专用代码；规则效果由玩家主动点选，APP 不隐式套用。

---

## 15. 批量处理剩余小队的做法

用户决定：**先跑 10 支，看质量后再决定剩余的**（截至本文撰写，这 10 支尚未开跑）。

### 15.1 步骤

1. 确认没有其他 Agent 在改仓库（`pgrep -fl "minimax-code|codex|claude"` 并检查工作目录）。
2. 选出要处理的小队（`grep -l '"draft": true' app/src/main/assets/teams/*.json`）。
3. 每支小队派一个子代理，并发不超过约 10 个。提示词模板：

```
请按 /Users/alanliu-15air/Projects/anroid1st/docs/小队数据校对任务说明.md 完成一支小队的校对。
你负责的小队：<中文名>，TEAM_ID = `<TEAM_ID>`。
（如有已知特殊情况在此补充，例如多张同名数据卡、三栏版面、图示卡。）
完成后汇报：特工/武器/能力/专属武器规则/规则条目数；两个校验脚本的最后几行输出；
开始和结束时间（用 date 命令）；拿不准的地方（标出卡图文件名）；对任务说明的改进建议。
```

4. 子代理只改自己的两个 JSON；不改 Kotlin、不删/改名文件、不提交 git。
5. 全部交回后由主流程：
   - 跑 `check_team_data.py --all`、`check_team_rules.py`；
   - 抽查每队 1–2 张数据卡（数值 + 一条能力原文对照卡图）；
   - 汇总每队“拿不准的转录”给用户确认；
   - 编译、单测、装模拟器，按 §10.4 抽测几支；
   - 把任务说明中暴露的新问题补进 `docs/小队数据校对任务说明.md`。
6. 用户确认质量后再处理剩余小队。

### 15.2 成本估算

每队约 50 分钟、约 24 万 token；38 支合计约 4–5 小时（10 并发）、约 900 万 token。

---

## 16. 最终验收清单（一支小队“完成”的定义）

- [ ] `teams/<ID>.json` 与 `rules/<ID>.json` 都已删除 `draft`。
- [ ] `check_team_data.py <ID>` 与 `check_team_rules.py` 均通过。
- [ ] 能力列表非空，且每个数据卡/能力卡上的能力都有条目。
- [ ] `team_rules/` 下该队卡图全部为 `.webp`，无 `.png`。
- [ ] APP 能新建该队小队、添加全部特工、查看小队规则与计谋、在对局中使用（§10.4）。
- [ ] 拿不准的转录已列出并经用户确认（或标注待确认）。
- [ ] （可选）小队标记已接入（§11）。
