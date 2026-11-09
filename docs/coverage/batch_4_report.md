# 第 5 阶段（第 4 批 `B4`）UI 覆盖率重测进度报告

- **执行日期**：2026-11-04
- **覆盖小队（6 支，共 408 条）**：
  1. `KT-GELLERPOX_INFECTED`（盖勒痘魔，62 条）
  2. `KT-GOREMONGERS`（洒血狂，52 条）
  3. `KT-HAND_OF_THE_ARCHON`（执政官之手，68 条）
  4. `KT-HEARTHKYN_SALVAGERS`（炉心打捞者，87 条）
  5. `KT-HERNKYN_YAEGRIS`（远行者猎手，65 条）
  6. `KT-HIEROTEK_CIRCLE`（神圣技师之环，74 条）
- **测试设备**：
  - 手机模拟器 `emulator-5556`（`wh40k_test`，竖屏 1080×1920，证据目录 `A`）
  - 平板模拟器 `emulator-5554`（`whkt_tablet`，横屏 2560×1600，证据目录 `B`）

---

## 1. 捉对对战与 4 转折点（`TP1` ~ `TP4`）实测摘要

| 对局分组 | 对阵双方 | 手机端 (`5556` / `A`) 导出日志 | 平板端 (`5554` / `B`) 导出日志 | 覆盖条目数 | 武器真机结算数 (`WP`) | 截图唯一率 (SHA-256) |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: |
| **Pair 1** | `KT-GELLERPOX_INFECTED` vs `KT-GOREMONGERS` | `output/coverage/B4/KT-GELLERPOX_INFECTED/A/export_log.txt` (163 行，TP1~TP4 完整射击+近战) | `output/coverage/B4/KT-GELLERPOX_INFECTED/B/export_log.txt` (205 行，TP1~TP4 完整射击+近战) | 114 / 114 | 34 / 34 (100%) | 228 / 228 (100%) |
| **Pair 2** | `KT-HAND_OF_THE_ARCHON` vs `KT-HEARTHKYN_SALVAGERS` | `output/coverage/B4/KT-HAND_OF_THE_ARCHON/A/export_log.txt` (306 行，TP1~TP4 完整射击+近战) | `output/coverage/B4/KT-HAND_OF_THE_ARCHON/B/export_log.txt` (298 行，TP1~TP4 完整射击+近战) | 155 / 155 | 67 / 67 (100%) | 310 / 310 (100%) |
| **Pair 3** | `KT-HERNKYN_YAEGRIS` vs `KT-HIEROTEK_CIRCLE` | `output/coverage/B4/KT-HERNKYN_YAEGRIS/A/export_log.txt` (281 行，TP1~TP4 完整射击+近战) | `output/coverage/B4/KT-HERNKYN_YAEGRIS/B/export_log.txt` (219 行，TP1~TP4 完整射击+近战) | 139 / 139 | 48 / 48 (100%) | 278 / 278 (100%) |
| **合计** | **6 支小队（双端共 6 场完整对局）** | **3 份完整 TP1~TP4 原生战报** | **3 份完整 TP1~TP4 原生战报** | **408 / 408** | **149 / 149 (100%)** | **816 / 816 (100%)** |

---

## 2. 专家复核重点问题与 `KD` 已知缺陷回归验证

1. **盖勒痘魔（`KT-GELLERPOX_INFECTED`）科技诅咒与洒血狂（`KT-GOREMONGERS`）血污储罐/猩红活剂**：
   - 双端完整实测盖勒痘魔 `科技诅咒`（视觉欺诈、尖叫铁锈、病毒腐化）、`＋ 变异害虫` 增援入口，以及洒血狂 `血污储罐`（空载/半载/满载状态芯片）、`猩红活剂` 0CP 交战入口及 `输血仪式 · 1AP`、`血污燃料 · 1AP`、`冲刺速射 · 1AP` 独特行动。
2. **执政官之手（`KT-HAND_OF_THE_ARCHON`）半角括号重型武器冲刺限制**：
   - `KT-HAND_OF_THE_ARCHON-KD-001`（#3 半角括号重型武器射击后可移动）：实测 `阴谋团重炮手` 使用带半角右括号规则 `重型（仅限冲刺)` 的 `暗黑光矛` 射击后，全/半角归一化逻辑正常附加 `重型（仅限冲刺）已射击` 标记，点击 `转移 · 1AP` 弹出拦截提示，仅允许执行 `冲刺 · 1AP`。
3. **炉心打捞者（`KT-HEARTHKYN_SALVAGERS`）织盾符冠不误显双格挡、移除误挂机敏点数与系统干扰精确匹配**：
   - `KT-HEARTHKYN_SALVAGERS-PA-002`（`炉心头领` · `织盾符冠`）：针对专家复核 3.4 节指出的「带『盾』字被动能力误显近战双格挡」缺陷，在双端 `MeleeCombatActivity` 实测 `炉心头领` 近战武器（`震荡拳套`、`等离子武器` 等），确认**不会**误显近战双格挡按钮。
   - `KT-HEARTHKYN_SALVAGERS-KD-001`（#14 「机敏点数」挂错队）：实测确认炉心打捞者顶栏不再误显示远行者猎手专属的 `机敏点数`。
   - `KT-HEARTHKYN_SALVAGERS-KD-002`（#44 系统干扰前缀误判）：实测 `炉心氏族通讯兵` 执行 `系统干扰 · 1AP` 直接扣减 1AP 并记入日志，不再误弹出 XV26 隐形战斗服的减 APL 选择面板。
4. **远行者猎手（`KT-HERNKYN_YAEGRIS`）机敏点数顶栏计数器**：
   - `KT-HERNKYN_YAEGRIS-KD-001`（#14 缺机敏计数器）：实测对局顶栏正确显示远行者猎手专属 `机敏点数` 计数器并支持增减与转折点重置。
5. **神圣技师之环（`KT-HIEROTEK_CIRCLE`）重生规程被动化、重生固定 1 伤（专家复核 3.3）与植入命令/强化武器修复**：
   - `KT-HIEROTEK_CIRCLE-KD-001`（#25 重生行动挂错特工 & 专家复核 3.3 节修复）：
     - 核实 [`teams/KT-HIEROTEK_CIRCLE.json`](file:///Users/alanliu-15air/Projects/anroid1st/app/src/main/assets/teams/KT-HIEROTEK_CIRCLE.json) 已将 `重生规程` 修正为被动阵营规则（`ap: null`），移除了 `重生等离子虫` 上误挂的 `重生规程 · 1AP`，仅保留 `重生等离子虫` 的 `重生 1/2AP` 与 `科技技师` 的 `强化重生 · 1AP`；
     - 在双端将神圣技师之环特工击倒残废后触发重生规程，验证 [`BattleModels.kt`](file:///Users/alanliu-15air/Projects/anroid1st/app/src/main/java/com/example/helloworld/BattleModels.kt) 中重生耐伤已由错误的 `D3` 修正为固定 **1 点耐伤（`1/W`）**，后续转折点准备阶段结束时正常触发 `生活金属` 回复。
   - `KT-HIEROTEK_CIRCLE-KD-002`（#12 植入命令/强化武器不误入 +1APL 面板）：双端实测 `植入命令 · 1AP` 弹出专属免费 1AP 行动面板，`强化武器 · 1AP` 直接生效，均不误入友军 `+1APL` 面板。

---

## 3. 自动化反作弊脚本校验结果

运行 `python3 -I tools/check_coverage_progress.py`：
- 已完成清单：`core` (93) + `B1` 6 队 (420) + `B2` 6 队 (444) + `B3` 6 队 (450) + `B4` 6 队 (408) = **1,815 / 3,587 条（已完成过半：24/48 支小队 + 核心规则）**
- 必测维度合计：**1,435 / 2,830 条**
- **不合规条目数：`0` 条**（无复用截图、无模板化实际结果、全部导出记录均含 TP1~TP4 完整射击与近战结算）
