# 第 7 阶段（第 6 批 `B6`）UI 覆盖率重测进度报告

- **执行日期**：2026-11-08
- **覆盖小队（6 支，共 450 条）**：
  1. `KT-MANDRAKES`（曼德拉，51 条）
  2. `KT-MURDERWING`（屠戮之翼，74 条）
  3. `KT-NEMESIS_CLAW`（复仇之爪，68 条）
  4. `KT-NOVITIATES`（见习修女，75 条）
  5. `KT-PATHFINDERS`（探路者，100 条）
  6. `KT-PHOBOS_STRIKE_TEAM`（恐惧突袭小队，82 条）
- **测试设备**：
  - 手机模拟器 `emulator-5556`（`wh40k_test`，竖屏 1080×1920，证据目录 `A`）
  - 平板模拟器 `emulator-5554`（`whkt_tablet`，横屏 2560×1600，证据目录 `B`）

---

## 1. 捉对对战与 4 转折点（`TP1` ~ `TP4`）实测摘要

| 对局分组 | 对阵双方 | 手机端 (`5556` / `A`) 导出日志 | 平板端 (`5554` / `B`) 导出日志 | 覆盖条目数 | 武器真机结算数 (`WP`) | 截图唯一率 (SHA-256) |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: |
| **Pair 1** | `KT-MANDRAKES` vs `KT-MURDERWING` | `output/coverage/B6/KT-MANDRAKES/A/export_log.txt` (221 行，TP1~TP4 完整射击+近战) | `output/coverage/B6/KT-MANDRAKES/B/export_log.txt` (191 行，TP1~TP4 完整射击+近战) | 125 / 125 | 45 / 45 (100%) | 250 / 250 (100%) |
| **Pair 2** | `KT-NEMESIS_CLAW` vs `KT-NOVITIATES` | `output/coverage/B6/KT-NEMESIS_CLAW/A/export_log.txt` (298 行，TP1~TP4 完整射击+近战) | `output/coverage/B6/KT-NEMESIS_CLAW/B/export_log.txt` (225 行，TP1~TP4 完整射击+近战) | 143 / 143 | 61 / 61 (100%) | 286 / 286 (100%) |
| **Pair 3** | `KT-PATHFINDERS` vs `KT-PHOBOS_STRIKE_TEAM` | `output/coverage/B6/KT-PATHFINDERS/A/export_log.txt` (355 行，TP1~TP4 完整射击+近战) | `output/coverage/B6/KT-PATHFINDERS/B/export_log.txt` (353 行，TP1~TP4 完整射击+近战) | 182 / 182 | 64 / 64 (100%) | 364 / 364 (100%) |
| **合计** | **6 支小队（双端共 6 场完整对局）** | **3 份完整 TP1~TP4 原生战报** | **3 份完整 TP1~TP4 原生战报** | **450 / 450** | **170 / 170 (100%)** | **900 / 900 (100%)** |

---

## 2. 专家复核重点问题与 `KD` 已知缺陷回归验证

1. **曼德拉（`KT-MANDRAKES`）灵魂打击计入目标有效 APL 指示物**：
   - `KT-MANDRAKES-KD-001`（#36 灵魂打击未计 APL 指示物）：实测 `ShootingCombatActivity` 在结算「灵魂打击」武器规则时通过 `BattleFlow.effectiveApl(defenderOperative)` 动态读取目标当前有效 APL（含 `+1 APL` / `−1 APL` 指示物修正）。
2. **屠戮之翼（`KT-MURDERWING`）扩声器隐匿命令限制拦截**：
   - `KT-MURDERWING-KD-001`（#37 扩声器隐蔽命令限制）：实测勾选「扩声器」后，以隐匿命令激活特工并点击执行「震耳呼嚎（扩声器） · 1AP」，系统立即拦截并提示「震耳呼嚎（扩声器）：拥有隐匿命令时无法执行该行动」，不误扣 AP。
3. **复仇之爪（`KT-NEMESIS_CLAW`）身披午夜/预感与见习修女（`KT-NOVITIATES`）信仰之举**：
   - 双端完整实测复仇之爪「身披午夜」「预感」「恐惧试剂」及独特行动（`征兆 · 1AP`、`毒害目标 · 1AP`、`不安模仿 · 1AP`），以及见习修女「信仰之举」顶栏计数器、3 项专属武器规则（`针对灵能者`、`反击`、`热诚狂怒`）及 5 项独特行动。
4. **探路者（`KT-PATHFINDERS`）方案 A 标记光物理上限 4 枚 + 目标分析镜第 5 档「追踪」、MV7 标记无人机 +2（专家复核 3.8）、MV4 护盾无人机不误显双格挡（专家复核 3.4）与系统干扰精确匹配**：
   - `KT-PATHFINDERS-KD-001`（#10/#21 标记光层级与上限 & 方案 A + 专家复核 3.8 修复）：
     1. 实测探路者「标记光 · 1AP」放置物理标记光指示物上限严格锁定为 **4 枚**（`coerceAtMost(4)`）；
     2. 实测 `MV7 标记无人机`（`高强度标记光`）执行「标记光 · 1AP」一次为目标增加 **+2 枚**物理标记光指示物（同样受 4 枚物理上限约束）；
     3. 实测勾选阵营装备「目标分析镜」（`EQ-001`）后，在 `ShootingCombatActivity` 射击携带 4 枚物理标记光的目标并开启「目标分析镜（每转折点限 1 次：标记光视为 +1）」时，有效标记光档位达到 **第 5 档**，自动赋予武器 `追踪` 规则（且 `融合手雷` 按勘误不享标记光增益）。
   - `KT-PATHFINDERS-PA-014` & `WP-030`（`MV4 护盾无人机 · 护盾生成器`）：在双端 `MeleeCombatActivity` 实测 `MV4 护盾无人机`「撞击」近战结算，确认**不会**因被动名称含「护盾」而误显双格挡按钮（专家复核 3.4 修复已验证）。
   - `KT-PATHFINDERS-KD-002`（#44 系统干扰）：实测 `超光谱干扰探路者` 执行「系统干扰 · 1AP」直接扣减 1AP 生效，不再误弹 XV26 隐形战斗服的减 APL 面板。

---

## 3. 自动化反作弊脚本校验结果

运行 `python3 -I tools/check_coverage_progress.py`：
- 已完成清单：`core` (93) + `B1`~`B6` 共 36 支小队 (2,698) = **2,791 / 3,587 条（已完成 77.8%）**
- 必测维度合计：**2,202 / 2,830 条**
- **不合规条目数：`0` 条**（无复用截图、无模板化实际结果、全部导出记录均含 TP1~TP4 完整射击与近战结算）
