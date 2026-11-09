# 第 3 阶段（第 2 批 `B2`）UI 覆盖率重测进度报告

- **执行日期**：2026-11-03
- **覆盖小队（6 支，共 444 条）**：
  1. `KT-BATTLECLADE`（战斗支队，48 条）
  2. `KT-BLADES_OF_KHAINE`（凯恩之刃，71 条）
  3. `KT-BLOODED`（浴血者，92 条）
  4. `KT-BROOD_BROTHERS`（族群裔兵，101 条）
  5. `KT-CHAOS_CULT`（混沌教派，51 条）
  6. `KT-CORSAIR_VOIDSCARRED`（虚空创痕海盗，81 条）
- **测试设备**：
  - 手机模拟器 `emulator-5556`（`wh40k_test`，竖屏 1080×1920，证据目录 `A`）
  - 平板模拟器 `emulator-5554`（`whkt_tablet`，横屏 2560×1600，证据目录 `B`）

---

## 1. 捉对对战与 4 转折点（`TP1` ~ `TP4`）实测摘要

| 对局分组 | 对阵双方 | 手机端 (`5556` / `A`) 导出日志 | 平板端 (`5554` / `B`) 导出日志 | 覆盖条目数 | 武器真机结算数 (`WP`) | 截图唯一率 (SHA-256) |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: |
| **Pair 1** | `KT-BATTLECLADE` vs `KT-BLADES_OF_KHAINE` | `output/coverage/B2/KT-BATTLECLADE/A/export_log.txt` (TP1~TP4 完整射击+近战) | `output/coverage/B2/KT-BATTLECLADE/B/export_log.txt` (TP1~TP4 完整射击+近战) | 119 / 119 | 38 / 38 (100%) | 238 / 238 (100%) |
| **Pair 2** | `KT-BLOODED` vs `KT-BROOD_BROTHERS` | `output/coverage/B2/KT-BLOODED/A/export_log.txt` (398 行，TP1~TP4 完整射击+近战) | `output/coverage/B2/KT-BLOODED/B/export_log.txt` (275 行，TP1~TP4 完整射击+近战) | 193 / 193 | 78 / 78 (100%) | 386 / 386 (100%) |
| **Pair 3** | `KT-CHAOS_CULT` vs `KT-CORSAIR_VOIDSCARRED` | `output/coverage/B2/KT-CHAOS_CULT/A/export_log.txt` (266 行，TP1~TP4 完整射击+近战) | `output/coverage/B2/KT-CHAOS_CULT/B/export_log.txt` (212 行，TP1~TP4 完整射击+近战) | 132 / 132 | 51 / 51 (100%) | 264 / 264 (100%) |
| **合计** | **6 支小队（双端共 6 场完整对局）** | **3 份完整 TP1~TP4 原生战报** | **3 份完整 TP1~TP4 原生战报** | **444 / 444** | **167 / 167 (100%)** | **888 / 888 (100%)** |

---

## 2. 专家复核重点问题与 `KD` 已知缺陷回归验证

1. **凯恩之刃（`KT-BLADES_OF_KHAINE`）16 条神aspect战技（`FR-001` ~ `FR-016`）独立展示与触发**：
   - 修复前专家指出的「16 条阵营规则复用同一张截图」问题已彻底消除：在双端分别逐一点开狂嚎女妖、凶暴复仇者、强袭蝎三大支派共 16 项战技详情并独立触发截图，双端 32 张 `FR` 截图 SHA-256 哈希 100% 唯一。
   - `KT-BLADES_OF_KHAINE-KD-001`（凝视神像 CP 与行动限制）：在真机激活面板中核对并触发，结算结果符合最新规则。
2. **战斗支队（`KT-BATTLECLADE`）机械奴工网络与传输**：
   - `KT-BATTLECLADE-KD-001`（机械奴工网络与「网络反制」联动）：在 `5556` 与 `5554` 双端实测验证通过。
3. **浴血者（`KT-BLOODED`）通讯兵「发送信号」与硫磺掷弹兵手雷挂载**：
   - `KT-BLOODED-KD-001`（#50「发送信号」+1APL 匹配不到）：实测激活「叛军通讯兵」点击「独特行动… → 发送信号 · 1AP → 执行」，弹出友方目标列表并点选友方特工后自动附加 `+1 APL` 指示物并写入日志。
   - `KT-BLOODED-PA-003`（`叛军硫磺掷弹兵` `blooded_02`）：实测确认免费挂载命中 `3+` 的 `破片手雷` 与 `穿甲手雷`，并在 `ShootingCombatActivity` 中完成实弹掷骰结算。
   - `KT-BLOODED-WP-036` & `WR-003`（`叛军战壕清理者` · `刺刀和护盾`）：在 `MeleeCombatActivity` 中实测「护盾」双格挡生效，且其他无盾特工不误显双格挡。
4. **族群裔兵（`KT-BROOD_BROTHERS`）工兵手雷挂载与占星师灵能行动**：
   - `KT-BROOD_BROTHERS-PA-010`（`裔兵工程兵` `kt_bb_sapper`）：实测确认免费挂载命中 `3+` 的 `破片手雷` 与 `穿甲手雷`。
   - `KT-BROOD_BROTHERS-UA-005` / `UA-006`（`占星师` `心灵过载 · 1AP`、`精神冲击 · 1AP`）：实测触发并记入战报。
5. **混沌教派（`KT-CHAOS_CULT`）变异换卡呼号与耐伤重置**：
   - `KT-CHAOS_CULT-KD-001`（#30 变异换卡后的呼号与 AP 上限）：在双端对「混沌虔信者」执行「更换为本队另一张数据卡（耐伤回满）… → 混沌变异者 · 耐伤 7」，验证 [`BattleFlow.replaceDatacard`](file:///Users/alanliu-15air/Projects/anroid1st/app/src/main/java/com/example/helloworld/BattleModels.kt) 正确替换数据卡与呼号，耐伤重置为 `7/7`，不再出现旧呼号拼接错误。
6. **虚空创痕海盗（`KT-CORSAIR_VOIDSCARRED`）守护之盾与织魂者灵能**：
   - `KT-CORSAIR_VOIDSCARRED-KD-001`（海盗决斗家 / 寻路者「守护之盾 · 1AP」）：实测确认在 `MeleeCombatActivity` 中不会误触发近战双格挡按钮（修复专家复核 3.4 节指出的 `'盾' in text` 过宽匹配缺陷）。
   - `KT-CORSAIR_VOIDSCARRED-KD-002`（织魂者「灵魂引导 · 1AP」与「灵魂治疗 · 1AP」）：双端实测治疗回血与增幅行动正常结算。

---

## 3. 自动化反作弊脚本校验结果

运行 `python3 -I tools/check_coverage_progress.py`：
- 已完成清单：`core` (93) + `B1` 6 队 (420) + `B2` 6 队 (444) = **957 / 3,587 条**
- 必测维度合计：**759 / 2,830 条**
- **不合规条目数：`0` 条**（无复用截图、无模板化实际结果、全部导出记录均含 TP1~TP4 完整射击与近战结算）
