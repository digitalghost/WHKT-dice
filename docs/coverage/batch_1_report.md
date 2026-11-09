# Phase 2（第二阶段 · Batch 1）：第 1 批 6 支小队 100% 真实 UI 覆盖测试报告

- **测试阶段**：Phase 2 / Batch 1（`CHAOS-FELL`、`CHAOS-PM`、`CHAOS-WC`、`IMP-AOD`、`IMP-CI`、`IMP-WS` 共 6 支小队，合计 **420** 条核销项）
- **执行日期**：2026-11-02（含专家复核后 TP1~TP4 全转折点实机重测与 100% 截图去重验证）
- **基准 Git Commit (`git rev-parse HEAD`)**：`bf606de`
- **测试设备**：
  - 手机竖屏模拟器：`emulator-5556`（AVD `wh40k_test`，1080×1920，承担场次 A 主编成实战 + 全 420 项手机端核销）
  - 平板横屏模拟器：`emulator-5554`（AVD `whkt_tablet`，2560×1600，承担场次 B 副编成/替补特工实战 + 全 420 项平板端核销）
- **准入与回归校验**：
  - `./gradlew :app:testDebugUnitTest`：**173 / 173 通过**
  - `python3 -I tools/check_team_data.py --all` & `python3 -I tools/check_team_rules.py --all`：**48 / 48 支小队全部 `[通过]`**
  - `python3 -I tools/check_coverage_progress.py`：**累计完成 `513 / 3587`（Phase 1 `93/93` + Batch 1 `420/420`），双端 840 张 PNG 截图 SHA-256 哈希 100% 唯一，全部 163 项武器（`WP`）实弹投骰结算，不合规 0 条**

---

## 一、Batch 1 对局场次与 6 支小队核销概览

| 序号 | 小队 ID | 小队名称 | 用例总数 | ✅ 通过 | 场次 A（手机 `5556`）对局与导出日志 | 场次 B（平板 `5554`）对局与导出日志 |
| :---: | :--- | :--- | :---: | :---: | :--- | :--- |
| 1 | `CHAOS-FELL` | 恶角兽掠夺者 | 76 | 76 | vs `IMP-AOD`（TP1~TP4 完赛，48 射 + 32 近）<br>`output/coverage/B1/CHAOS-FELL/A/export_log.txt` | vs `IMP-AOD`（TP1~TP4 完赛，44 射 + 37 近）<br>`output/coverage/B1/CHAOS-FELL/B/export_log.txt` |
| 2 | `CHAOS-PM` | 瘟疫战士 | 54 | 54 | vs `IMP-CI`（TP1~TP4 完赛，22 射 + 16 近）<br>`output/coverage/B1/CHAOS-PM/A/export_log.txt` | vs `IMP-CI`（TP1~TP4 完赛，24 射 + 18 近）<br>`output/coverage/B1/CHAOS-PM/B/export_log.txt` |
| 3 | `CHAOS-WC` | 次元密会 | 83 | 83 | vs `IMP-WS`（TP1~TP4 完赛，29 射 + 24 近）<br>`output/coverage/B1/CHAOS-WC/A/export_log.txt` | vs `IMP-WS`（TP1~TP4 完赛，35 射 + 30 近）<br>`output/coverage/B1/CHAOS-WC/B/export_log.txt` |
| 4 | `IMP-AOD` | 死亡天使 | 83 | 83 | vs `CHAOS-FELL`（TP1~TP4 完赛，48 射 + 32 近）<br>`output/coverage/B1/IMP-AOD/A/export_log.txt` | vs `CHAOS-FELL`（TP1~TP4 完赛，44 射 + 37 近）<br>`output/coverage/B1/IMP-AOD/B/export_log.txt` |
| 5 | `IMP-CI` | 洁天使隐伏者 | 65 | 65 | vs `CHAOS-PM`（TP1~TP4 完赛，22 射 + 16 近）<br>`output/coverage/B1/IMP-CI/A/export_log.txt` | vs `CHAOS-PM`（TP1~TP4 完赛，24 射 + 18 近）<br>`output/coverage/B1/IMP-CI/B/export_log.txt` |
| 6 | `IMP-WS` | 狼侦察 | 59 | 59 | vs `CHAOS-WC`（TP1~TP4 完赛，29 射 + 24 近）<br>`output/coverage/B1/IMP-WS/A/export_log.txt` | vs `CHAOS-WC`（TP1~TP4 完赛，35 射 + 30 近）<br>`output/coverage/B1/IMP-WS/B/export_log.txt` |
| **合计** | **6 支小队** | — | **420** | **420** | **420 张唯一哈希手机截图 + 6 份导出日志** | **420 张唯一哈希平板截图 + 6 份导出日志** |

---

## 二、Batch 1 重点小队专属机制与必回归已知缺陷（`KD-*`）验证结果

1. **恶角兽掠夺者（`CHAOS-FELL`）**：
   - `CHAOS-FELL-KD-001`（`#6 狂暴残废条件`）：实测恶角兽特工耐伤归零触发「狂暴」后锁定交战命令并计入击杀；已处于狂暴状态时，单次射击普通伤害或近战首次普通出击不会使其立即残废，仅在激活/反击结束、近战暴击出击、第二次普通出击或 2 枚以上未豁免射击攻击骰时残废，符合官方勘误。
   - `CHAOS-FELL-KD-002`（`#11 暴怒再生治疗对象`）：巫医「暴怒再生 · 1AP」弹出友方非狂暴特工目标选择器，支持 `2D3` 或被攻击过固定 `6` 点治疗。
   - `CHAOS-FELL-KD-003`（`#12 点燃怒火 +1APL`）：铜蹄「点燃怒火 · 1AP」弹出友方目标选择器（排除本人），赋予 `+1 APL` 标记且实时提升有效 APL。
2. **瘟疫战士（`CHAOS-PM`）**：
   - `CHAOS-PM-KD-001`（`#9 毒素挂标/扣血`）：瘟疫战士「毒素」与蛇虫「中毒」彻底分离；非瘟疫战士敌方获得「毒素」标记后，激活开始时自动扣除 1 点耐伤，且剧毒武器自动获得 `+1/+1` 伤害加成。
3. **次元密会（`CHAOS-WC`）**：
   - `CHAOS-WC-KD-001`（`#27 奸奇恩惠缺 4 项`）：「小队规则 -> 奸奇恩惠」完整展示全部 9 项恩惠（虚化视觉、时间行者、亚空间残响、亚空间强化、变异肢体、亚空间飞行、扭曲命运、星爆术、非物质界专家）。
   - `CHAOS-WC-KD-002`（`#13 附魔弹药`）：勾选阵营装备「附魔弹药」后，巫师地狱火爆矢手枪、红字战士地狱火爆矢枪、奸角兽自动手枪自动附加 `毁灭 1` 武器规则。
4. **死亡天使（`IMP-AOD`）**：
   - `IMP-AOD-KD-001`（`#16 战团战术子项名称`）：首测发现 `TeamPloys.kt:130` 仍残留旧版译名（登记为缺陷 **D-008**）；修复后点选「战团战术」或战略计谋「灵活战术」完整展示 2026 官方中文 PDF 的 8 项名称（`1. 猛攻`、`2. 决斗家`、`3. 坚决`、`4. 隐匿`、`5. 机动`、`6. 强健`、`7. 神射手`、`8. 攻城战专家`），双端回归 ✅。
5. **洁天使隐伏者（`IMP-CI`）**：
   - `IMP-CI-KD-001` & `IMP-CI-KD-002`（`#27 祷祝正文与 2026 更新 PDF 勘误条目`）：首测发现 `IMP-CI.json` 的「祷祝」缺少触发首句且未收录 2026 年 8 月「勘误与问答」条目（登记为缺陷 **D-009**）；修复后补齐「祷祝」首句、同步全部特工能力为官方 PDF 卡面原文，并增补「勘误与问答 · 更新日志（2026 年 8 月）」条目，双端回归 ✅。
6. **狼侦察（`IMP-WS`）**：
   - 完整测试「狩猎风暴」（雷暴区域标记与冰霜吐息、符文祭司借雷暴施法）、「野性本能」、「芬里斯战吼」、陷阱大师「埋设爆炸陷阱 · 1AP」、狼侦察猎犬与符文祭司灵能行动，双端 59 项全部核销。

---

## 三、Batch 1 发现并修复的缺陷汇总（详见 `docs/coverage/defects.md`）

| 缺陷编号 | 关联用例 | 等级 | 问题描述与根因 | 修复措施与回归结果 |
| :--- | :--- | :---: | :--- | :--- |
| **D-008** | `IMP-AOD-KD-001` | `❌规则` | `TeamPloys.kt:130` 中「战团战术」8 个子项芯片仍为旧版译名（`1. 侵略战士` 等），未对齐 2026 官方中文 PDF | 在 [`TeamPloys.kt`](../../app/src/main/java/com/example/helloworld/TeamPloys.kt) 与 [`ShootingCombatActivity.kt`](../../app/src/main/java/com/example/helloworld/ShootingCombatActivity.kt) 中更新「战团战术」8 项及「教义条令」「熟练技巧」「剧目」「守卫命令」官方子项与瞄准无休效果；双端回归 ✅ |
| **D-009** | `IMP-CI-FR-001`<br>`IMP-CI-KD-001`<br>`IMP-CI-KD-002` | `❌规则` | `rules/IMP-CI.json` 与 `teams/IMP-CI.json` 中「祷祝」缺少触发首句，部分特工能力为改写句式，且缺少 2026 年 8 月「勘误与问答」条目 | 在 [`app/src/main/assets/rules/IMP-CI.json`](../../app/src/main/assets/rules/IMP-CI.json) 与 [`app/src/main/assets/teams/IMP-CI.json`](../../app/src/main/assets/teams/IMP-CI.json) 中补齐「祷祝」首句、恢复卡面原文并新增 `IMP-CI-errata` 条目（同步补齐 `KT-XV26_STEALTH_BATTLESUITS.json` 勘误条目）；双端回归 ✅ |

---

## 四、证据归档清单

- **核销清单 CSV（420 / 420 行全部填写完毕）**：
  - [`docs/coverage/checklist_CHAOS-FELL.csv`](checklist_CHAOS-FELL.csv)（76 行）
  - [`docs/coverage/checklist_CHAOS-PM.csv`](checklist_CHAOS-PM.csv)（54 行）
  - [`docs/coverage/checklist_CHAOS-WC.csv`](checklist_CHAOS-WC.csv)（83 行）
  - [`docs/coverage/checklist_IMP-AOD.csv`](checklist_IMP-AOD.csv)（83 行）
  - [`docs/coverage/checklist_IMP-CI.csv`](checklist_IMP-CI.csv)（65 行）
  - [`docs/coverage/checklist_IMP-WS.csv`](checklist_IMP-WS.csv)（59 行）
- **截图与日志目录**：
  - 手机竖屏（`5556`）：`output/coverage/B1/<TEAM_ID>/A/*.png`（420 张）+ `output/coverage/B1/<TEAM_ID>/A/export_log.txt`（6 份）
  - 平板横屏（`5554`）：`output/coverage/B1/<TEAM_ID>/B/*.png`（420 张）+ `output/coverage/B1/<TEAM_ID>/B/export_log.txt`（6 份）
