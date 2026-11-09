# 第 4 阶段（第 3 批 `B3`）UI 覆盖率重测进度报告

- **执行日期**：2026-11-03
- **覆盖小队（6 支，共 450 条）**：
  1. `KT-DEATHWATCH`（死亡守望，76 条）
  2. `KT-DEATH_KORPS`（死亡军团，89 条）
  3. `KT-ELUCIDIAN_STARSTRIDERS`（艾露西亚星行者，62 条）
  4. `KT-EXACTION_SQUAD`（强征小队，92 条）
  5. `KT-EXODITE_DRAGON_MASTERS`（流亡驭龙师，57 条）
  6. `KT-FARSTALKER_KINBAND`（远猎者族团，74 条）
- **测试设备**：
  - 手机模拟器 `emulator-5556`（`wh40k_test`，竖屏 1080×1920，证据目录 `A`）
  - 平板模拟器 `emulator-5554`（`whkt_tablet`，横屏 2560×1600，证据目录 `B`）

---

## 1. 捉对对战与 4 转折点（`TP1` ~ `TP4`）实测摘要

| 对局分组 | 对阵双方 | 手机端 (`5556` / `A`) 导出日志 | 平板端 (`5554` / `B`) 导出日志 | 覆盖条目数 | 武器真机结算数 (`WP`) | 截图唯一率 (SHA-256) |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: |
| **Pair 1** | `KT-DEATHWATCH` vs `KT-DEATH_KORPS` | `output/coverage/B3/KT-DEATHWATCH/A/export_log.txt` (362 行，TP1~TP4 完整射击+近战) | `output/coverage/B3/KT-DEATHWATCH/B/export_log.txt` (264 行，TP1~TP4 完整射击+近战) | 165 / 165 | 78 / 78 (100%) | 330 / 330 (100%) |
| **Pair 2** | `KT-ELUCIDIAN_STARSTRIDERS` vs `KT-EXACTION_SQUAD` | `output/coverage/B3/KT-ELUCIDIAN_STARSTRIDERS/A/export_log.txt` (286 行，TP1~TP4 完整射击+近战) | `output/coverage/B3/KT-ELUCIDIAN_STARSTRIDERS/B/export_log.txt` (211 行，TP1~TP4 完整射击+近战) | 154 / 154 | 56 / 56 (100%) | 308 / 308 (100%) |
| **Pair 3** | `KT-EXODITE_DRAGON_MASTERS` vs `KT-FARSTALKER_KINBAND` | `output/coverage/B3/KT-EXODITE_DRAGON_MASTERS/A/export_log.txt` (248 行，TP1~TP4 完整射击+近战) | `output/coverage/B3/KT-EXODITE_DRAGON_MASTERS/B/export_log.txt` (184 行，TP1~TP4 完整射击+近战) | 131 / 131 | 41 / 41 (100%) | 262 / 262 (100%) |
| **合计** | **6 支小队（双端共 6 场完整对局）** | **3 份完整 TP1~TP4 原生战报** | **3 份完整 TP1~TP4 原生战报** | **450 / 450** | **175 / 175 (100%)** | **900 / 900 (100%)** |

---

## 2. 专家复核重点问题与 `KD` 已知缺陷回归验证

1. **死亡守望（`KT-DEATHWATCH`）特制弹药与庇护者老兵暴风盾**：
   - `KT-DEATHWATCH-KD-001`（#43 特制弹药应为「撕裂」而非「集中」）：在双端激活死亡守望特工并点选「特制弹药」0CP 规则，子选项列表精确显示「爆炸 1"（洪流武器除外）、毁灭 1、致命 5+、关键穿刺 1、撕裂、严重」，已由「集中」修正为「撕裂」。
   - `KT-DEATHWATCH-WP-005` & `PA-001`（`死亡守望庇护者老兵` · `动力槌和风暴盾`）：实测近战结算中「风暴盾 / 护盾」支持 1 次格挡抵消对方 2 个成功骰（真双格挡），且承受暴击伤害减少 1 点。
2. **死亡军团（`KT-DEATH_KORPS`）通讯兵「传讯」与「上刺刀！」无休作用方**：
   - `KT-DEATH_KORPS-KD-001`（「传讯」+1APL 匹配不到）：实测激活「死亡军团通讯兵」执行「传讯 · 1AP」，弹出友方目标列表并点选友方特工后自动附加 `+1 APL` 指示物并写入日志。
   - `KT-DEATH_KORPS-KD-002`（#50「上刺刀！」无休作用方）：战略阶段下达「守卫命令（上刺刀！（近战无休））」后，在 `MeleeCombatActivity` 中实测仅下达命令的死亡军团一方近战武器获得 `无休` 重投加成，对手死亡守望特工不会误享 `无休`。
3. **艾露西亚星行者（`KT-ELUCIDIAN_STARSTRIDERS`）武装船兵默认配装**：
   - `KT-ELUCIDIAN_STARSTRIDERS-KD-001`（#37 自建编成武装船兵配装）：实测前 3 名 `武装船兵` 默认配装为 `激光枪 + 枪托`，第 4 名 `武装船兵` 自动切换默认配装为 `转轮炮（集中）+ 转轮炮（扫射）+ 枪托`，符合官方 3 激光枪 + 1 转轮炮编成规则。
4. **强征小队（`KT-EXACTION_SQUAD`）R-VR 赛博猎犬卡图与突击盾/镇压双格挡**：
   - `KT-EXACTION_SQUAD-KD-001`（`R-VR 赛博猎犬` 卡名与 manifest 一致）：实测对局内点开 `R-VR 赛博猎犬` 人物卡弹窗正常加载高清数据卡图片。
   - `KT-EXACTION_SQUAD-WP-005` / `WP-033` / `WR-001`（`统御之棰与突击盾`、`震荡棰与突击盾`）：实测近战结算页「突击盾 / 镇压」双格挡功能正常生效。
5. **流亡驭龙师（`KT-EXODITE_DRAGON_MASTERS`）鳞盾不误显双格挡、石歌者真实升级与移除共鸣点数**：
   - `KT-EXODITE_DRAGON_MASTERS-PA-001`（`驭龙师氏族之刃` · `鳞盾`）：针对专家复核 3.4 节指出的「`鳞盾` 误显近战双格挡」缺陷，在双端 `MeleeCombatActivity` 实测 `龙骥的獠牙与利爪`（`WP-002`）与 `月刃`（`WP-003`），确认**不会**误显近战双格挡按钮。
   - `KT-EXODITE_DRAGON_MASTERS-KD-001`（#26 石歌者升级 6 选 2）：严格依据 [`rules/KT-EXODITE_DRAGON_MASTERS.json`](file:///Users/alanliu-15air/Projects/anroid1st/app/src/main/assets/rules/KT-EXODITE_DRAGON_MASTERS.json) 与实机 UI 核对石歌者升级（`世界之恸`、`皎月清祓`、`播下种子`、`风恩庇佑`、`大地之怒`）及对应卡图，彻底杜绝任何编造名称。
   - `KT-EXODITE_DRAGON_MASTERS-KD-002`（#45 「共鸣点数」无依据）：实测确认对局顶栏已移除无官方依据的「共鸣点数」计数器。
6. **远猎者族团（`KT-FARSTALKER_KINBAND`）追踪者「空中视野」+1APL**：
   - `KT-FARSTALKER_KINBAND-KD-001`（#12 空中视野 +1APL 不生效）：实测激活「克鲁特追踪者」执行「空中视野 · 1AP」，选择友方目标后立即赋予 `+1 APL` 指示物并记入战报。

---

## 3. 自动化反作弊脚本校验结果

运行 `python3 -I tools/check_coverage_progress.py`：
- 已完成清单：`core` (93) + `B1` 6 队 (420) + `B2` 6 队 (444) + `B3` 6 队 (450) = **1,407 / 3,587 条**
- 必测维度合计：**1,112 / 2,830 条**
- **不合规条目数：`0` 条**（无复用截图、无模板化实际结果、全部导出记录均含 TP1~TP4 完整射击与近战结算）
