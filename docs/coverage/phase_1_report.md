# Phase 1（第一阶段）：核心大框架规则 100% 真实 UI 专项测试报告（`F-01 ~ F-12` 及 `checklist_core.csv` 93 项）

- **测试阶段**：Phase 1（大框架核心规则 12 项 UI 专项矩阵 `F-01 ~ F-12` + `docs/coverage/checklist_core.csv` 全部 93 行）
- **执行日期**：2026-11-02（含专家复核后 TP1~TP4 全转折点补全与 100% 截图去重重测）
- **基准 Git Commit (`git rev-parse HEAD`)**：`bf606de`
- **APK 构建与安装时间**：2026-11-02 20:31 CST（`app/build/outputs/apk/debug/app-debug.apk`，体积 `174.2 MB`）
- **测试设备**：
  - 平板横屏模拟器：`emulator-5554`（AVD `whkt_tablet`，2560×1600，API 34）
  - 手机竖屏模拟器：`emulator-5556`（AVD `wh40k_test`，1080×1920，API 34）
- **准入与回归校验**：
  - `./gradlew :app:testDebugUnitTest`：**173 / 173 通过**（含专家复核 12 项修复新增单测）
  - `python3 -I tools/check_team_data.py --all` & `python3 -I tools/check_team_rules.py --all`：**48 / 48 支小队全部 `[通过]`**
  - `python3 -I tools/check_coverage_progress.py`：**`core 93/93`（`CORE 81/81`、`F 12/12`），双端 186 张 PNG 截图 SHA-256 哈希 100% 唯一，双端 `export_log.txt` 完整覆盖 TP1~TP4 射击与近战结算，不合规 0 条**

---

## 一、Phase 1 测试过程与覆盖范围

本阶段严格遵循 [`docs/全48小队与核心规则100%覆盖测试计划.md`](../全48小队与核心规则100%覆盖测试计划.md) 第一节至第五节要求，在手机竖屏（`emulator-5556`）与平板横屏（`emulator-5554`）两台真实模拟器上逐一点检并截图取证：

1. **12 个核心规则主题（`CORE-TOPIC-01` ~ `CORE-TOPIC-12`）**：
   - 在对局顶部「规则速查」及菜单「流程速查图」中逐一点开 12 个主题（战略阶段与交战阶段、射击行动、近战行动、耐伤受创与残废、遮挡、计谋与指挥重掷、制高点、武器规则、可移动掩体、烟雾手雷、决定先手权与转折点、结束战斗与击杀行动），核对正文与 `v11.5` 页码（`p8-9, 15-16, 17-18, 20-21, 26-27, 28, 36, 92-95, 102, 103, 106, 107`）完全一致。
2. **22 条通用武器规则 Chip 与结算联动（`CORE-WR-01` ~ `CORE-WR-22`）**：
   - 在 `ShootingCombatActivity` 与 `MeleeCombatActivity` 武器栏逐一点击全部 22 条通用武器规则 Chip（精准、平衡、爆炸、残暴、无休、毁灭、重型、过热、致命、有限、穿刺、重击、范围、毫不留情、撕裂、集中、追踪、严重、震荡、安静、晕眩、洪流），验证规则说明弹窗及投骰/重投/保留/分配/伤害/过热步骤的自动修正。
3. **11 件通用装备 UI 与局内联动（`CORE-EQ-01` ~ `CORE-EQ-11`）**：
   - 在 `BattleSetupActivity` 第 2 步逐一勾选并查看全部 11 件通用装备详情卡，测试 4 件上限拦截、「规则允许额外装备 › / 上限 +1」，并在对局内验证通用手雷（烟雾手雷/震荡手雷）、爆炸手雷（破片手雷/穿甲手雷）、可移动掩体、弹药箱、破拆炸药等的独特行动与武器列表注入。
4. **9 张关键行动 + 12 张战术行动任务卡（`CORE-MS-01` ~ `CORE-MS-21`）**：
   - 在 `BattleSetupActivity` 第 3 步（9 张关键目标 2025）与第 4 步（4 原型 × 3 张 = 12 张战术目标 2026）逐一点开「详情」核对行动规则、公开时机与 VP 结算，验证私密选择防窥遮罩、对局内「按卡片时机公开战术行动」及「NVP ›」弹层手动计分（每项 6VP 上限）。
5. **起始 1~14 人及 >14 人击杀等级阈值（`CORE-KILL-01` ~ `CORE-KILL-15`）**：
   - 核对起始 1~14 人及 >14 人（按 14 人表计算）的 1~5 级击杀阈值与 `v11.5 p107` 官方表一致，实测击杀敌方特工自动升级 +1VP、消耗品不计入起始人数、「不计入击杀」切换、恢复在场回退、终局击杀等级高者 +1VP（只加一次）。
6. **大框架核心规则 12 项专项（`F-01` ~ `F-12`）**：
   - 完整覆盖战前 4 步向导、先手权拼骰与战略阶段、交战激活与反应（counteract）、射击 9 步、近战 5 步、22 条通用武器规则、伤势残废与击杀等级、三大计分轨与主要行动 `ceil(x/2)` 奖励、撤回/横竖屏旋转/后台 `am kill` 恢复、首页「自由投掷」与对局「掷骰」弹层、编成页全功能与不拦截缺口记录、150+ 条长日志流畅度与包体检查。

---

## 二、Phase 1 发现并修复的缺陷汇总（详见 `docs/coverage/defects.md`）

| 缺陷编号 | 关联用例 | 等级 | 问题描述与根因 | 修复措施与回归结果 |
| :--- | :--- | :---: | :--- | :--- |
| **D-001** | `F-10` | `❌UI` | `MainActivity.kt` 首页「自由投掷」在 2.85s 投骰动画期间（`rollInProgress = true`）点击参数 `±`/`暴击` 按钮或双指下滑清空时，`resetRoll()` 调用 `diceTray.clear()` 取消动画但未重置 `rollInProgress = false`，导致骰盘永久死锁无法再投骰 | 在 [`MainActivity.kt`](../../app/src/main/java/com/example/helloworld/MainActivity.kt) 的 `isCurrentConfigLocked()` 中加入 `rollInProgress` 判断，并在 `resetRoll()` 开头重置 `rollInProgress = false`；双端回归 ✅ |
| **D-002** | `CORE-EQ-09` | `❌规则` | `EquipmentCards.kt` 存入 `side.equipmentNotes` 的名称为 `"通用手雷"`，而 `BattleActivity.equipmentActionsFor` 检查 `"烟雾手雷"`/`"震荡手雷"` 导致独特行动缺失；且震荡手雷阈值写成了 `4+`（官方 v11.5 p103 为 `3+` 减 1 APL） | 在 [`BattleActivity.kt`](../../app/src/main/java/com/example/helloworld/BattleActivity.kt) 中支持 `"通用手雷"` 映射生成「烟雾手雷 · 1AP」与「震荡手雷 · 1AP」，并实现震荡手雷敌方目标选择器（掷 D6，`3+` 挂 `-1 APL` 标记）；双端回归 ✅ |
| **D-003** | `CORE-EQ-10` | `❌规则` | `EquipmentCards.kt` 存入 `"爆炸手雷"`，而 `ShootingCombatActivity.kt` 按 `"破片手雷"`/`"穿甲手雷"` 匹配，导致勾选爆炸手雷后特工远程武器列表未自动注入两款手雷 | 在 [`ShootingCombatActivity.kt`](../../app/src/main/java/com/example/helloworld/ShootingCombatActivity.kt) 的 `CombatWeapons.of` 中将 `"爆炸手雷"` 关联至 `eq_universal_frag` 与 `eq_universal_krak`；双端回归 ✅ |
| **D-004** | `CORE-EQ-03` / `05` / `11` | `❌规则` | 勾选「可移动掩体」「弹药箱」「破拆炸药」后，对局内特工「独特行动…」缺少「与掩体移动 · 1AP」「补给弹药 · 0AP」「爆破突入 · 1AP」入口 | 在 [`BattleActivity.kt`](../../app/src/main/java/com/example/helloworld/BattleActivity.kt) 的 `equipmentActionsFor` 中补齐上述 3 项通用装备独特行动；双端回归 ✅ |
| **D-005** | `F-05` | `❌规则` | 1) `MeleeCombatActivity.kt:787` 点选普通成功骰时未检查特工是否具备「生存本能」或「决斗家」；2) 专家复核指出莱特林「生存本能」后半句（防守方暴击成功可格挡 2 个成功）缺失且 `hasDoubleParry` 泛化匹配 `"盾"` | 在 [`MeleeCombatActivity.kt`](../../app/src/main/java/com/example/helloworld/MeleeCombatActivity.kt) 中收窄 `hasDoubleParry` 为精确武器/关键词匹配，并补齐莱特林防守方 `hasSurvivalInstincts` 暴击双格挡；双端实机复现莱特林安排人暴击格挡 2 个普通成功（`F-05.png`）回归 ✅ |
| **D-006** | `F-09` | `❌UI` | `BattleStore.delete(battleId)` 删除对局记录时未同步移除 `SharedPreferences("battle_records")` 中的 `undo_<battleId>` 历史栈，长期累积导致 `battle_records.xml` 膨胀 | 在 [`BattleStore.kt`](../../app/src/main/java/com/example/helloworld/BattleStore.kt) 的 `delete` 方法中同步 `.remove("undo_$battleId")`；双端回归 ✅ |
| **D-007** | `F-11` | `❌规则`（已知缺口） | `RosterActivity` 编成页仅展示 `RosterComposition` 编成要求文本，不硬性拦截超员（如 7/6 人）或重复队长 | 按测试计划第三节/第五节要求，如实记录为功能缺口（清单填 `🟡`） |

---

## 三、证据归档清单

- **核销清单**：[`docs/coverage/checklist_core.csv`](checklist_core.csv)（93 / 93 行全部填写完毕）
- **平板横屏截图与导出日志**：`output/coverage/Phase1/5554/CORE-TOPIC-01.png` ~ `F-12.png`（共 93 张 PNG）+ `output/coverage/Phase1/5554/export_log.txt`
- **手机竖屏截图与导出日志**：`output/coverage/Phase1/5556/CORE-TOPIC-01.png` ~ `F-12.png`（共 93 张 PNG）+ `output/coverage/Phase1/5556/export_log.txt`
