# R4b：8 张新战术行动的界面与 3D 显示（依赖 R4a 已合并）

工作目录 = kt-game。引擎侧已实现（`kt_engine.gd` 的 TAC OPS 段），本任务只做显示与操作入口，让玩家看得见、点得到。

允许修改：`src/ui/main_game.gd`、`src/board/killzone_view.gd`、`src/board/kt_models_3d.gd`、`src/ui/kt_theme.gd`、`assets/tokens/`（仅新增）。
禁止修改：`src/core/*`、`tests/*`、`data/*`、`tools/*`。不要 git commit。

## 先读
`kt_engine.gd` 里 8 张卡的实现，确认这些接口的真实签名（名字可能略有出入，以代码为准）：
- 侧翼：`flank_definition()`、`flank_controller(flank)`；
- 行动：`get_unique_actions(op)` 里新增的 id（形如 `tac_plant_banner`、`tac_grab_banner`、`tac_plant_device`、`tac_search`、`tac_pickup_retrieval`、`tac_clear`、`tac_surveil`、`tac_pickup_intel`），以及 `needs_point` / `needs_target` 标志；
- 标识：`state["eq_markers"]` 里 kind 为 `banner` / `intel`，目标点状态在 `objectives_state[oid]["tac"]`（`device` / `sweep` / `cleared` / `searched`）；
- 携带物：特工 `carried` 数组；监控：特工 `surveilled_by`。

## 1. 选卡界面
`_build_tacop_choice` 现在只显示 `implemented == true` 的卡。把 `data/core/tac_ops.json` 里 8 张卡的 `implemented` 已在 R4a 改为 true，确认界面能显示 12 张（按原型分组、`HFlowContainer` 换行），卡片文字用 `scoring`，tooltip 用 `reveal + rule + scoring`。若 12 张放不下，把卡片宽度缩到 200、高度 108。

## 2. 棋盘显示（`killzone_view.gd`）
1. **侧翼**：己方（双方都显示）选到侧翼卡时，在分界线处画一条贯穿棋盘的虚线（用现有 `add_seg`），并给该侧的两个侧翼区域铺不同透明度底色；每帧/每次刷新显示 `flank_controller(flank)` 归属色（A 蓝 / B 绿 / 无 灰）。侧翼卡未选中时不画。
2. **旗帜 / 情报 / 回收物**：把 `eq_markers` 里 `kind` 为 `banner` / `intel` / `retrieval_token`（若有）的条目画成平放 token（用 `Models3D.add_flat_token`，`assets/tokens/banner_blue.png` / `banner_red.png`；情报用 `assets/tokens/crate_blue.png` / `crate_red.png`，这两张图需从 `external-assets/killzone_tokens_and_tools/objective_and_tacop_markers/` 裁边后放进 `assets/tokens/`）。缓存键与地雷同步函数一致。
3. **目标点装置 / 扫荡 / 清理**：在目标点旁边画小图标（平放 token）：装置用 `assets/tokens/bomb_blue.png`/`bomb_red.png`（同样从外部素材裁边导入），扫荡用 `assets/tokens/status_cross_blue.png`/`status_cross_red.png`，清理用 `assets/tokens/status_alert_blue.png`/`status_alert_red.png`。位置沿目标点圆周按数量散开，避免重叠。
4. **携带物与监控**：在特工头顶名牌的状态 token 序列里追加（`KTTheme.status_kinds_for` 里加）：携带回收/情报 → `"carried"`（用 `crate_*`），被监控 → `"surveilled"`（用 `assets/tokens/scan_blue.png`/`scan_red.png`，从外部素材导入）。
5. 所有新增 token 都要在 `KTTheme.status_token_tex` 里登记，A 方蓝、B 方红。

## 3. 操作入口
`main_game.gd`：
1. 独特行动槽已经通用（`get_unique_actions` 循环），确认新行动都会出现；`needs_point` 的走 `pick_point` 模式，`needs_target` 的走 `pick_enemy` 模式，其余直接 `execute_unique_action`。
2. `pick_point` 模式的引导文字与合法点提示要按行动 id 区分（插旗 / 搜刮 / 等等）：把 `start_pick_point_mode(uid)` 增加一个可选参数 `label: String`，界面显示「放置：<label>」。
3. 战术面板（顶栏或英雄面板）增加一行「战术行动：<卡名>」与当前进度文字，例如「侧翼：西 ✔ / 东 ✘」「情报携带 2 枚」，数据从引擎新接口读取（若引擎没有提供汇总接口，在引擎里加一个只读 `tac_status_text(side_id) -> String` 是允许的——这属于 R4a 的补充，可以做，但要保持纯只读）。
4. 未实装卡的置灰逻辑保留，用于以后扩展。

## 自测
```bash
/opt/homebrew/bin/godot --headless --path . -s res://tests/run_all.gd 2>&1 | tail -1
/opt/homebrew/bin/godot --path . -- --autotest-screenshots 2>&1 | grep -iE "script error|parse error" | head
```
在截图巡检里额外生成一张 `08_tacops_board.png`：让 A 方选中「侧翼」，棋盘上摆一枚旗帜、目标点上放一枚装置与一枚扫荡标记、一名特工携带 2 枚情报，然后截图。报告里说明新增了哪些 token、哪些显示位置。
