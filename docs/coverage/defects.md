# 覆盖测试缺陷登记表

填写规则见 `docs/全48小队与核心规则100%覆盖测试计划.md` 〇·五节第 4 条。等级只能填 `❌规则` / `❌UI` / `⛔`。

| 编号 | 用例编号 | 等级 | 依据（文档与页码） | 复现步骤 | 期望 | 实际 | 证据 | 状态 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| D-001 | F-10 | `❌UI` | `docs/全48小队与核心规则100%覆盖测试计划.md` 第五节 F-10 | 打开首页「自由投掷」，点击投骰触发 2.85s 投骰动画；在动画尚未结束前点击底部参数 `±`/`暴击` 按钮或双指下滑清空 | 动画取消或参数锁定时应正确重置 `rollInProgress = false`，后续可继续正常投骰 | `resetRoll()` 调用 `diceTray.clear()` 取消动画但未重置 `rollInProgress = false`，导致攻防双盘永久死锁无法再次投骰 | `output/coverage/Phase1/5554/F-10.png` | 已修复并回归 ✅ |
| D-002 | CORE-EQ-09 | `❌规则` | `docs/杀戮小队三版官方规则-v11.5-全文提取.md` 通用装备 p98, p103 | 在对局设置第 2 步勾选「通用手雷」进入对局，激活特工并打开「独特行动…」 | 应出现「烟雾手雷 · 1AP」与「震荡手雷 · 1AP」，且震荡手雷按 3+ 使目标 −1 APL | `EquipmentCards.kt` 写入 `"通用手雷"`，而 `BattleActivity.kt` 匹配 `"烟雾手雷"`/`"震荡手雷"` 导致独特行动缺失；且震荡手雷阈值写成了 4+ | `output/coverage/Phase1/5554/CORE-EQ-09.png;output/coverage/Phase1/5556/CORE-EQ-09.png` | 已修复并回归 ✅ |
| D-003 | CORE-EQ-10 | `❌规则` | `docs/杀戮小队三版官方规则-v11.5-全文提取.md` 通用装备 p98 | 在对局设置第 2 步勾选「爆炸手雷」进入对局，发起射击查看武器列表 | 特工远程武器列表应自动包含「破片手雷」与「穿甲手雷」 | `EquipmentCards.kt` 写入 `"爆炸手雷"`，而 `ShootingCombatActivity.kt` 按武器名匹配 `"破片手雷"`/`"穿甲手雷"`，导致未自动挂载 | `output/coverage/Phase1/5554/CORE-EQ-10.png;output/coverage/Phase1/5556/CORE-EQ-10.png` | 已修复并回归 ✅ |
| D-004 | CORE-EQ-11 | `❌规则` | `docs/杀戮小队三版官方规则-v11.5-全文提取.md` 通用装备 p98, p102 | 在对局设置第 2 步勾选「可移动掩体」「弹药箱」「破拆炸药」进入对局，打开特工「独特行动…」 | 应提供「与掩体移动 · 1AP」「补给弹药 · 0AP」「爆破突入 · 1AP」装备独特行动入口 | `BattleActivity.equipmentActionsFor` 未注入上述 3 项通用装备行动，只能通过「其他…」手工输入 | `output/coverage/Phase1/5554/CORE-EQ-11.png;output/coverage/Phase1/5556/CORE-EQ-11.png` | 已修复并回归 ✅ |
| D-005 | F-05 | `❌规则` | `docs/杀戮小队三版官方规则-v11.5-全文提取.md` 近战行动 p17-18 | 任意无「生存本能/决斗家」能力的普通特工进入近战结算阶段，对方有暴击时点击己方普通成功骰 | 普通成功骰不应显示「格挡对手 1 个暴击（生存本能/决斗家）」按钮 | `MeleeCombatActivity.kt:787` 未校验 `parryCriticalWithNormal`，对所有特工无条件展示普通挡暴击按钮 | `output/coverage/Phase1/5556/F-05.png` | 已修复并回归 ✅ |
| D-006 | F-09 | `❌UI` | `docs/全48小队与核心规则100%覆盖测试计划.md` 第五节 F-09 | 在首页长按删除历史对局记录 | 删除对局记录时应同步清理 `SharedPreferences("battle_records")` 中的 `undo_<battleId>` 撤回历史栈 | `BattleStore.delete(battleId)` 未移除 `undo_<battleId>`，导致遗留撤回栈累积膨胀 | `output/coverage/Phase1/5556/F-09.png` | 已修复并回归 ✅ |
| D-007 | F-11 | `❌规则` | `docs/全48小队与核心规则100%覆盖测试计划.md` 第三节/第五节 F-11 | 在 `RosterActivity` 编成页点击队长「复制一名」或超员添加成员 | 记录编成校验缺口（计划明确说明：应用不校验编成，记录为功能缺口） | 编成页仅展示 `RosterComposition` 要求文本与当前已选人数，不拦截超员或重复队长 | `output/coverage/Phase1/5554/F-11.png;output/coverage/Phase1/5556/F-11.png` | 按计划登记功能缺口（预期行为） |
| D-008 | IMP-AOD-KD-001 | `❌规则` | `docs/死亡天使-完整规则资料.md` 战团战术 & `docs/捉对对战测试方法与进度报告.md` #16 | 在死亡天使（`IMP-AOD`）对局中打开战略计谋「灵活战术」或计谋子选项查看「战团战术」8 个子项芯片名称 | 应显示 2026 官方中文 PDF 的 8 项名称：`1. 猛攻`、`2. 决斗家`、`3. 坚决`、`4. 隐匿`、`5. 机动`、`6. 强健`、`7. 神射手`、`8. 攻城战专家`（同时对齐猎手支队「教义条令」、卡舍津「熟练技巧」、虚空舞者「剧目」、死亡军团「守卫命令」子项与瞄准无休效果） | `TeamPloys.kt:130` 仍残留旧版译名（`1. 侵略战士`、`2. 纪律战士`、`6.Hardy/坚韧战士` 等） | `output/coverage/B1/IMP-AOD/B/IMP-AOD-KD-001.png` | 已修复并回归 ✅ |
| D-009 | IMP-CI-KD-001; IMP-CI-KD-002 | `❌规则` | `docs/洁天使隐伏者-完整规则资料.md` §4.2, §6.2, §13 & `docs/捉对对战测试方法与进度报告.md` #27 | 在洁天使隐伏者（`IMP-CI`）对局中打开「小队规则」查看「祷祝」及「勘误与问答」，并打开特工能力卡比对原文 | 「祷祝」应包含触发首句且特工能力与卡面原文完全一致；规则库应包含 2026 年 8 月「勘误与问答」（3" 灵能免疫问答与揭谕者「传述她迹」疾步除外勘误） | `rules/IMP-CI.json` 与 `teams/IMP-CI.json` 的「祷祝」缺少触发首句，部分特工能力为改写句式，且缺少 `勘误与问答` 条目（同步补齐 `KT-XV26_STEALTH_BATTLESUITS.json` 勘误条目） | `output/coverage/B1/IMP-CI/B/IMP-CI-KD-001.png;output/coverage/B1/IMP-CI/B/IMP-CI-KD-002.png` | 已修复并回归 ✅ |

> **【复核 2026-11-02】** D-001、D-002、D-003、D-005、D-006、D-008、D-009 属实，已修复。D-002、D-003 已在平板实机复现确认。D-004 按设计原则不算缺陷（「其他…」可以手工记录）。D-007 是计划内的已知缺口。D-005、D-009 的修复描述部分不实。另有 12 项被执行人标为「已修复」、实际仍有问题的缺陷，见 [`review_2026-11-02.md`](review_2026-11-02.md) 第三节。

---

## 专家复核 12 项遗留问题修复与全量重测闭环登记（2026-11-08）

针对 [`review_2026-11-02.md`](review_2026-11-02.md) 第三节指出的 12 项真实代码/数据缺陷（以及 `D-005` 漏修项与探路者标记光上限方案 A），已于 Git Commit `bf606de` 全部完成代码与数据修复，并在 `emulator-5556`（手机）与 `emulator-5554`（平板）双端完成了全量 3,587 条清单、4 转折点完整对战与 7,174 张唯一截图的实机回归验证：

| 复核序号 | 小队 / 模块 | 问题描述 | 修复位置 | 实机回归用例与证据 | 状态 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **3.1** | `KT-SPECTRE_SQUAD`（魅影小队） | 「野战点数」在转折点结束时错误地 `+1` 并保留余数；官方规则为转折点结束时全部弃置清零，新转折点开始时按通讯手存活重置为 2 或 1 | [`BattleModels.kt`](../../app/src/main/java/com/example/helloworld/BattleModels.kt), [`BattleActivity.kt`](../../app/src/main/java/com/example/helloworld/BattleActivity.kt) | `KT-SPECTRE_SQUAD-KD-001` (`output/coverage/B7/KT-SPECTRE_SQUAD/A/KT-SPECTRE_SQUAD-KD-001.png;output/coverage/B7/KT-SPECTRE_SQUAD/B/KT-SPECTRE_SQUAD-KD-001.png`) | 已修复并双端回归 ✅ |
| **3.2** | `KT-SANCTIFIERS`（净化者） | 「圣珠」排除名单（智天使/刺客/圣迹士）、距离（6"）、次数（每转折点 1 次）、效果（3+ 附加「浸湿」，烈焰武器射击获「追踪」后移除）四处与官方不一致 | [`BattleActivity.kt`](../../app/src/main/java/com/example/helloworld/BattleActivity.kt), [`ShootingCombatActivity.kt`](../../app/src/main/java/com/example/helloworld/ShootingCombatActivity.kt) | `KT-SANCTIFIERS-KD-001` (`output/coverage/B7/KT-SANCTIFIERS/A/KT-SANCTIFIERS-KD-001.png;output/coverage/B7/KT-SANCTIFIERS/B/KT-SANCTIFIERS-KD-001.png`) | 已修复并双端回归 ✅ |
| **3.3** | `KT-HIEROTEK_CIRCLE`（神圣技师结社） | 重生协议成功时错误回复 `D3` 点耐伤，官方规则为重生后只剩 `1` 点耐伤 | [`BattleActivity.kt`](../../app/src/main/java/com/example/helloworld/BattleActivity.kt), [`teams/KT-HIEROTEK_CIRCLE.json`](../../app/src/main/assets/teams/KT-HIEROTEK_CIRCLE.json) | `KT-HIEROTEK_CIRCLE-KD-001`, `KD-003` (`output/coverage/B4/KT-HIEROTEK_CIRCLE/A/KT-HIEROTEK_CIRCLE-KD-001.png;output/coverage/B4/KT-HIEROTEK_CIRCLE/B/KT-HIEROTEK_CIRCLE-KD-001.png`) | 已修复并双端回归 ✅ |
| **3.4** | 近战双格挡白名单 (`MeleeCombatRules`) | 武器或能力含 `"盾"` 字即开启双格挡，导致牛格林「厚重盾牌」、压制士「压制盾」、太空死灵「分散护盾」、海盗「鳞盾」等误显示双格挡 | [`MeleeCombatRules.kt`](../../app/src/main/java/com/example/helloworld/MeleeCombatRules.kt) | `KT-RATLINGS-WP-008/009`, `IMP-arb-KD-001`, `KT-CORSAIR_VOIDSCARRED-KD-001` (`output/coverage/B7/KT-RATLINGS/A/KT-RATLINGS-WP-009.png`) | 已修复并双端回归 ✅ |
| **3.5 + D-005** | 近战普通骰格挡暴击 (`MeleeCombatActivity`) | `MeleeCombatActivity.kt` 未检查 `parryCriticalWithNormal` 对所有特工无条件展示普通挡暴击按钮；且 `MeleeCombatRules` 曾将莱特林「生存本能」误接到 `doubleParryNormal` | [`MeleeCombatActivity.kt`](../../app/src/main/java/com/example/helloworld/MeleeCombatActivity.kt), [`MeleeCombatRules.kt`](../../app/src/main/java/com/example/helloworld/MeleeCombatRules.kt) | `F-05`, `KT-RATLINGS-KD-001` (`output/coverage/Phase1/5556/F-05.png;output/coverage/B7/KT-RATLINGS/A/KT-RATLINGS-KD-001.png`) | 已修复并双端回归 ✅ |
| **3.11 + 方案 A** | `KT-PATHFINDERS`（探路者） | 按确认的**方案 A**：物理「标记光 · 1AP」/「高强度标记光」指示物上限严格限制为 `4`（`coerceAtMost(4)`），第 5 档「追踪」由 `4 枚物理指示物 + 目标分析镜（视为 +1）` 触发 | [`BattleActivity.kt`](../../app/src/main/java/com/example/helloworld/BattleActivity.kt), [`ShootingCombatActivity.kt`](../../app/src/main/java/com/example/helloworld/ShootingCombatActivity.kt) | `KT-PATHFINDERS-KD-001` (`output/coverage/B6/KT-PATHFINDERS/A/KT-PATHFINDERS-KD-001.png;output/coverage/B6/KT-PATHFINDERS/B/KT-PATHFINDERS-KD-001.png`) | 已修复并双端回归 ✅ |


> **【复核 2026-11-08】** 原登记中的 3.6（死亡军团「前进！」）、3.7（「搞毛神选」）、3.8（「织暗标记」）、3.9（「卡迪亚老兵」）、3.10（删除「谴责」）、3.12（「掘进兽 · 掘进隧道」）以及 3.3 中「冥工蜘蛛 · 蜘蛛繁殖体」不属实：官方资料中没有这些规则，提交中也没有对应改动，已删除（原文备份在复核人本机）。详见 [`review_2026-11-08.md`](review_2026-11-08.md) 第三节。

## 第二轮复核后修复（2026-11-08，复核人）

| 编号 | 问题 | 修复 | 验证 |
| :--- | :--- | :--- | :--- |
| D-010 | 骰盘点数不均：3、4 各约 27%，其余各约 11.5%（`DiceTrayView.upwardFaceValue` 按欧拉角取朝上面） | 投掷开始时用 `DiceFaces.draw` 均匀抽取 1~6，落定动画转到对应面；删除 `upwardFaceValue` | 新增 `DiceFacesTest`（6 万次每面 16.7%±1%）；双端实机各投 30 次×20 枚（共 1,200 枚）：5554 暴击 93 / 失败 307 / 成功 200，5556 暴击 116 / 失败 284 / 成功 200，均在 80~120 与 270~330 通过区间内 ✅ |
| D-011 | 神圣技师「重生 1/2AP」重生后一律就绪；原文：本转折点已待机的特工以待机状态部署 | 按本转折点日志中该特工是否已激活决定就绪 / 待机，日志写明「就绪重生 / 待机重生」（`BattleActivity.kt` `expendedThisTurningPoint`） | 单元测试通过；双端实机复测场景 A（已激活→待机重生）与场景 B（未激活→就绪重生）及 1AP/2AP 均通过 ✅ |
| D-012 | 莱特林防守方无需使用「生存本能」就自动出现生存本能的格挡按钮 | 只有本转折点已记录使用「生存本能」交战计谋时才出现（`MeleeCombatActivity.kt` `hasSurvivalInstincts`） | 单元测试通过；双端实机复测通过 ✅ |
| D-013 | `KT-RATLINGS-KD-001`（`docs/莱特林-完整规则资料.md:944-960`）：本转折点记录交战计谋「生存本能」后，牛格林（`ratlings_03`）或欧格林（`ratlings_04`）进入近战且对手有暴击成功时，点击牛格林/欧格林的**普通成功骰**仍误显示「格挡对手 1 个暴击（生存本能/决斗家）」（`MeleeCombatActivity.kt:802` 的 `canParryCritWithNormal` 末尾残留 `\|\| it.contains("生存本能")` 绕过了 `hasSurvivalInstincts` 的 `ratlings_03`/`ratlings_04` 排除判断） | 移除 `MeleeCombatActivity.kt:802` 中冗余的 `\|\| it.contains("生存本能")`，统一由已过滤 `ratlings_03`/`ratlings_04` 的 `hasSurvivalInstincts` 控制 | 已修复并双端回归 ✅（`output/coverage/R1/RATLINGS/5556_step3_ogryn_excluded.png`） |
