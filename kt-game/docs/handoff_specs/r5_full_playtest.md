# R5：全量试玩测试（工作目录 = kt-game）

目标：像玩家一样把游戏从开局到终局走完若干局，找出真实游玩中暴露的问题，并写成可复跑的自动化测试。**不改规则逻辑**（发现问题只记录 + 写失败用例，修复留给后续任务，除非是明显的崩溃/空引用，那种可以直接修）。

允许修改：`tests/run_all.gd`（新增场景测试）、`tests/scenarios/*.gd`（新建）、`build/playtest/`（输出日志与截图，git 忽略）。
禁止修改：`src/core/*` 的规则行为（崩溃类修复除外，需在报告里单独说明）、`data/*`、`assets/*`、`tools/*`。不要 git commit。

## A. 引擎级全程对局（必须自动化，加入测试套件）
新增 `tests/scenario_playtest.gd`（`extends SceneTree`，作为独立可执行脚本，不并入 `run_all.gd`，避免拖慢单元测试），内容：
1. 对 6 张地图 × 2 种模式（`ai`/`hotseat`）各跑 20 局随机种子（共 240 局）：用 `KTAI` 驱动双方（AI 对 AI），一直推进到 `phase == "GAME_OVER"`；每步检查：
   - 不出现 `push_error`/`printerr`（捕获 stdout/stderr 里含 "SCRIPT ERROR" 的行并计入失败）；
   - `state` 里没有 NaN/INF 坐标，特工坐标始终在棋盘范围内或在 (-99,-99)；
   - `ap_left` 不为负；`cp` 不为负；双方 `vp_*` 不超过 6；`tp` 单调不减且 ≤ 4；
   - 状态机不死循环：每局最多 4000 次推进，超出即判失败并 dump 当前 state；
   - 终局能算出 `get_total_vp()` 且不抛错。
2. 覆盖各机制的关键路径（每项至少 1 局、用固定种子可复现）：烟雾 + 震荡 + 爆破手雷；携掩体移动；爬梯攀爬；铁丝网；弹药储备重掷；8 张新战术行动（各至少一局由对应阵营选中并至少触发一次计分）；先手权卡（含重投与 ±N）；全灭后继续打到 TP4；终局主要行动 op 加分。
3. 输出：`build/playtest/report.md`，表格列出「地图 × 模式 × 种子 → 结束 TP、双方 VP、是否通过、失败原因」。任一局失败即以非零退出码结束。

## B. 界面级试玩（半自动，用截图 + 日志模拟）
用 `--autotest-screenshots` 的方式扩展一个 `--playtest-ui` 参数：从标题页开始，自动完成「选图 → 选人 → 选装备 → 选战术行动 → 部署装备 → 部署特工 → 掷先手 → 战略 → 交战（自动 AI 代打人类方）→ 到终局」，每一步：
- 记录 `interaction_mode`、`engine.state["phase"]`、`_context_hb` 子节点数量；
- 若某一步连续 3 秒没有任何状态变化，视为卡死，dump 当前界面状态并以非零退出；
- 每 20 步截一张图到 `build/playtest/ui_*.png`。
人类侧用 `KTAI` 决策代替点击（调用与按钮相同的引擎接口），但**必须**走界面层的处理函数（例如 `_on_board_move_committed`、`_on_board_shoot_target_clicked`、`execute_unique_action` 对应的 UI 回调），以覆盖 UI 与引擎之间的接缝。

## C. 手动清单（不能自动化的部分，写成交给玩家的检查表）
在 `build/playtest/manual_checklist.md` 里给出 20 条以内的人工验收项，每条写明：操作步骤、期望结果、若不符可能的原因区域。必须覆盖：
- 空格键在点过按钮后仍能结束激活；
- Z 键撤销只在己方回合可用，且一次回到自己上一步；
- 数字键 8/9/0 与独特行动/计谋的对应；
- Esc 取消已武装的模式；F 复位相机；
- 部署阶段点左侧名册能拾起未部署特工，镜头不飞到黑暗处；
- 骰盘子菜单（平衡/指挥重掷/弹药储备）各自可用且互不串用；
- 携掩体移动后掩体跟随、离开时提示；
- 烟雾覆盖下射击的遮挡提示；
- 战术行动面板显示的进度与实际一致。

## D. 报告要求
1. 汇总：跑了多少局、通过率、平均每局步数、发现的问题数与分类（崩溃/规则/交互/显示）。
2. 每个问题：复现步骤（种子 + 操作序列）、实际结果、期望结果、怀疑位置（文件:函数）。
3. 已在测试套件里固化的回归用例清单。
4. 你直接修掉的问题（仅限崩溃/空引用）单独列出。

## 自测
```bash
/opt/homebrew/bin/godot --headless --path . -s res://tests/run_all.gd 2>&1 | tail -1                     # 仍须全绿
/opt/homebrew/bin/godot --headless --path . -s res://tests/scenario_playtest.gd 2>&1 | tail -20            # 全部通过
/opt/homebrew/bin/godot --path . -- --playtest-ui 2>&1 | tail -10                                          # 正常结束
```
