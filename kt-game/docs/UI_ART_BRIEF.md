# KT-Game UI 素材需求与生成提示词

> 本文档只服务 `kt-game/`（Godot 独立游戏），与仓库根目录 Android 辅助 App 及 `docs/UI设计方案/` 无关。

---

## 0. 风格定位

**一句话**：一张摆在 41 千年哥特军械库里的战棋桌。玩家是指挥官，面前是真实的桌面（黄铜、天鹅绒、亚克力、磨损的木头），眼前浮着一层半透明的战术数据玻璃。

游戏里已经有三样"实物"定下了调子，所有新素材都要和它们相配：

| 已有实物 | 材质 | 色 |
|---|---|---|
| 骰盒 | 黄铜框 + 深红/深绿天鹅绒 | `#B08A3C` 黄铜, `#5A1A1E` 红绒, `#1C3A24` 绿绒 |
| 立牌 token | 烟色亚克力 + 阵营色边框 | 帝国 `#4A80D6`, 纳垢 `#70A036` |
| 桌面 | 深棕哑光台布 + 黄铜镶边 | `#0F0D0C` 台布 |

**UI 层（HUD）风格 = "战术玻璃"（Tactical Glass）**
- 面板：深色半透明玻璃（`#07090E` @ 86%），1px 黄铜细边，四角 45° 斜切（chamfer 8–10px），不要圆角
- 高亮色只用两种：**黄铜金** `#D4A84A`（可操作 / 当前）、**警戒橙** `#FF6A1F`（危险 / 伤害 / 当前阶段）
- 阵营归属色：帝国蓝 `#4A80D6`、纳垢绿 `#70A036`；信息文字灰 `#A3A9B4`
- 纹理：面板内有极淡的横向扫描线（2px 间隔，alpha 4%），边缘有轻微磨损/刮痕，不要赛博霓虹发光
- 字体方向：标题用窄高无衬线（Teko 已在项目里），正文用系统黑体；素材图上**不要烧死任何文字**

**不要**：圆润卡通、鲜艳渐变、发光霓虹、羊皮纸/手绘风（那是辅助 App 的方案）、任何 GW 官方 logo / 字样。

---

## 1. 通用生成参数

所有提示词末尾统一追加（给生成模型的全局约束）：

```
, grimdark gothic sci-fi tabletop wargame UI, brushed brass and smoked glass, 
chamfered 45-degree corners, subtle scanlines, worn metal edges, dark charcoal background, 
flat orthographic view, no text, no letters, no watermark, no logo, clean vector-like edges, 
high contrast, game asset, 2D
```

负面提示（如果工具支持）：

```
text, letters, typography, watermark, logo, blurry, photo, 3d render perspective, 
rounded corners, neon glow, rainbow gradient, cartoon, anime, parchment, paper texture
```

**格式要求**
- 全部 **PNG，透明背景**（下面标 ✱ 的除外）。做不到透明底就用 **纯黑 #000000 底**，我来抠
- 图标：**256×256**，图形占画面 70–80%，单色或双色（主金 + 深色内阴影），线宽一致
- 九宫格面板：**512×512**，四角装饰集中在角上 **64px** 内，中间区域纯色可拉伸
- 无缝贴图 ✱：**1024×1024**，上下左右可平铺
- 卡框：**1024×1536**（2:3 竖版）
- 横幅：**2048×512**
- 标题主视觉 ✱：**2560×1440**

命名直接用下面表格里的文件名，交回时放在一个文件夹即可。

---

## 2. 素材清单

### A. 面板与容器（九宫格，512×512，透明底）

| 文件名 | 用途 | 提示词 |
|---|---|---|
| `panel_primary.png` | 主控制台（技能栏、战斗面板） | `UI panel frame, nine-slice, thick brushed brass border 6px with riveted chamfered corners, smoked dark glass fill semi-transparent, faint horizontal scanlines, corner ornaments: small gothic skull rivet at each 45-degree cut corner` |
| `panel_floating.png` | 浮层（英雄卡、提示框、顶栏） | `UI panel frame, nine-slice, thin brass hairline border 1px, chamfered corners, smoked glass fill, no ornaments, minimal` |
| `panel_tag.png` | 标签/药丸（无边框） | `UI pill background, nine-slice, dark charcoal glass, no border, soft inner shadow top edge, chamfered ends` |
| `panel_card_stock.png` | 计谋卡卡面底 | `playing card background, nine-slice, dark gunmetal with vertical brushed texture, brass trim line inset 4px, chamfered corners, worn edges` |
| `panel_glossary.png` | 规则词条弹卡 | `UI tooltip panel, nine-slice, dark glass with brass top rule 2px, small cog ornament top-left corner, chamfered` |
| `btn_normal.png` / `btn_hover.png` / `btn_pressed.png` | 按钮三态 | `UI button, nine-slice, dark gunmetal face with brass hairline border, chamfered corners, bottom edge 3px darker (bevel)` + 分别追加 `, dim` / `, brass border brighter, subtle inner gold glow` / `, face darker, pressed inset shadow` |
| `btn_danger.png` | 危险按钮（开火/结算） | `UI button, nine-slice, dark face with warning orange hairline border #FF6A1F, chamfered, hazard stripe 2px along bottom edge` |

### B. 技能槽图标（256×256，透明底，单色黄铜金 + 深内阴影）

统一前缀：`tactical hotbar icon, flat, single color brushed brass #D4A84A with dark inner shadow, bold 12px stroke, centered, no background, ` + 下面的主体描述

| 文件名 | 动作 | 主体描述 |
|---|---|---|
| `icon_reposition.png` | 转移 | `double chevron arrow pointing up, military movement symbol` |
| `icon_dash.png` | 冲刺 | `lightning bolt crossing a forward arrow` |
| `icon_charge.png` | 冲锋 | `upward arrow with a blade tip, speed lines behind` |
| `icon_fall_back.png` | 后撤 | `arrow curving back and down, dashed trail` |
| `icon_shoot.png` | 射击 | `crosshair reticle with four tick marks, small dot center` |
| `icon_fight.png` | 近战 | `two crossed chainswords` |
| `icon_secure.png` | 占领 | `planted banner flag on a short pole over a hex base` |
| `icon_unique.png` | 特技 | `eight-pointed chaos-neutral star burst inside a diamond` |
| `icon_ploy.png` | 计谋 | `fanned playing cards, three cards` |
| `icon_card.png` | 原卡 | `single rectangular data card with a corner notch` |
| `icon_end.png` | 结束激活 | `check mark inside an octagon` |
| `icon_aim.png` | 瞄准（狙击手） | `eye inside a scope ring` |
| `icon_overwatch.png` | 反应/反击 | `exclamation mark inside a shield` |

### C. 系统栏图标（256×256，透明底，单色灰白 `#A3A9B4`）

统一前缀：`minimal UI glyph icon, flat, single color light grey, 10px stroke, centered, no background, `

| 文件名 | 主体描述 |
|---|---|
| `sys_orbit_left.png` | `circular arrow counter-clockwise` |
| `sys_orbit_right.png` | `circular arrow clockwise` |
| `sys_cam_iso.png` | `isometric cube outline` |
| `sys_cam_low.png` | `camera at low angle, small horizon line` |
| `sys_undo.png` | `curved undo arrow` |
| `sys_log.png` | `three horizontal lines with a small scroll end` |
| `sys_help.png` | `question mark inside thin circle` |
| `sys_settings.png` | `cog gear, 8 teeth` |
| `sys_menu.png` | `three stacked bars` |
| `sys_close.png` | `x cross` |

### D. 状态与词条分类图标（256×256，透明底，双色：类别色 + 深色）

| 文件名 | 用途 | 提示词主体 | 色 |
|---|---|---|---|
| `status_conceal.png` | 隐匿命令 | `hooded figure silhouette inside a circle token` | 蓝 `#4A80D6` |
| `status_engage.png` | 交战命令 | `crossed rifle and blade inside a circle token` | 橙 `#FF6A1F` |
| `status_poison.png` | 毒素 | `three-lobed biohazard inside a drop` | 绿 `#70A036` |
| `status_wounded.png` | 受创 | `cracked heart with a jagged break` | 红 `#C83C3C` |
| `status_stun.png` | 晕眩 | `three small stars orbiting a point` | 紫 `#9A6AE0` |
| `status_vantage.png` | 制高点 | `triangle peak with upward arrow` | 金 |
| `status_cover.png` | 掩护 | `shield half covered by a wall block` | 金 |
| `status_obscured.png` | 遮挡 | `eye with a diagonal slash and fog dots` | 紫 |
| `kind_weapon.png` | 词条：武器规则 | `small wrench crossed with a bullet` | 黄铜 |
| `kind_faction.png` | 词条：阵营规则 | `laurel wreath half circle` | 金 |
| `kind_ability.png` | 词条：特工能力 | `four-pointed star` | 绿 |
| `kind_ploy.png` | 词条：计谋 | `single playing card with corner fold` | 蓝 |
| `kind_tactic.png` | 词条：战团战术 | `small shield with a chevron` | 蓝 |
| `kind_equipment.png` | 词条：装备 | `backpack / satchel outline` | 橙棕 |

### E. 桌面实物贴图（给 3D 用）

| 文件名 | 尺寸 | 透明 | 提示词 |
|---|---|---|---|
| `tex_velvet_red.png` ✱ | 1024², 无缝 | 否 | `seamless tileable deep crimson velvet fabric texture, fine nap, soft directional sheen, top-down, flat lighting` |
| `tex_velvet_green.png` ✱ | 1024², 无缝 | 否 | 同上，`deep forest green` |
| `tex_brass_brushed.png` ✱ | 1024², 无缝 | 否 | `seamless tileable brushed brass metal texture, fine horizontal grain, slight tarnish spots, warm gold` |
| `tex_table_felt.png` ✱ | 1024², 无缝 | 否 | `seamless tileable dark charcoal-brown wool felt table surface, matte, tiny fibers` |
| `tex_leather_dark.png` ✱ | 1024², 无缝 | 否 | `seamless tileable dark brown worn leather texture, fine grain, slight scuffs` |
| `tex_wood_dark.png` ✱ | 1024², 无缝 | 否 | `seamless tileable dark stained oak wood texture, straight grain, matte varnish` |
| `die_faces_imperial.png` | 1536×256（6 格横排） | 是 | `six dice face designs in a row, each 256x256, deep royal blue square with chamfered corners, ivory pips 1 through 5, sixth face shows a stylized winged-skull crest instead of pips, flat, top-down` |
| `die_faces_nurgle.png` | 1536×256 | 是 | 同上，`sickly olive green square, bone-white pips, sixth face a three-ringed plague symbol` |
| `die_faces_attack.png` | 1536×256 | 是 | 同上，`dark crimson square, ivory pips, sixth face an eight-pointed burst star` |
| `chip_cp_imperial.png` | 512², 透明 | 是 | `top-down poker chip token, royal blue enamel center, brass rim with 12 small notches, embossed single chevron in center, flat` |
| `chip_cp_nurgle.png` | 512², 透明 | 是 | 同上，`olive green enamel, embossed trefoil` |
| `marker_vp_imperial.png` | 512², 透明 | 是 | `top-down small cylindrical game marker, blue top with brass ring` |
| `marker_vp_nurgle.png` | 512², 透明 | 是 | 同上，`green top` |
| `mat_datacard.png` | 1024×768 | 否 | `top-down dark leather card mat with brass corner protectors and a thin brass inset line, empty center, slight wear` |
| `block_tp_faces.png` | 1024×256（4 格横排） | 否 | `four square wooden block faces in a row, dark oak, each engraved and gold-inlaid with a Roman numeral I, II, III, IIII respectively, top-down` |

### F. 阶段与结算演出

| 文件名 | 尺寸 | 用途 | 提示词 |
|---|---|---|---|
| `banner_phase.png` | 2048×512, 透明 | 阶段横幅底 | `wide horizontal banner for a phase announcement, dark glass center band fading to transparent at left and right ends, two brass rules top and bottom, small aquila-neutral winged skull emblem at center, no text` |
| `banner_victory_imperial.png` | 2048×512, 透明 | 胜利横幅 | 同上，`blue light leak from center, laurel branches at both ends` |
| `banner_victory_nurgle.png` | 2048×512, 透明 | 胜利横幅 | 同上，`green sickly light leak, thorny vines at both ends` |
| `vfx_hit_badge.png` | 256², 透明 | 命中数字背板 | `small octagonal badge, dark glass with brass edge, empty center` |
| `vfx_crit_burst.png` | 512², 透明 | 暴击闪光 | `radial eight-pointed light burst, gold core fading to transparent, soft rays` |
| `vfx_kill_skull.png` | 512², 透明 | 击杀标记 | `stylized skull emblem front view, brass, dark eye sockets, flat` |
| `vfx_shield_block.png` | 512², 透明 | 格挡/豁免 | `shield silhouette with a diagonal deflect spark at upper right, blue-white, flat` |
| `marker_casualty.png` | 512², 透明 | 阵亡地面标记 | `top-down round token, dark disc with brass ring and a skull glyph, flat` |

### G. 标题页与过场（✱ 不透明）

| 文件名 | 尺寸 | 提示词 |
|---|---|---|
| `title_keyart.png` | 2560×1440 | `wide cinematic keyart of a grimdark sci-fi wargaming table in a dim gothic armoury, a 30x22 inch battlefield with ruined stone buildings and two glowing objective markers, brass dice box and a stack of chips at the table edge, warm candle and cold blue lamp lighting, shallow depth of field, left third of the frame kept dark and empty for menu placement, no figures, no text` |
| `title_emblem.png` | 1024², 透明 | `heraldic emblem for a wargame title: a winged skull over two crossed blades on a hexagonal brass plate, chamfered, dark enamel inlay, no text` |
| `bg_deploy_transition.png` | 2560×1440 | `abstract dark tactical overlay, faint hex grid, sweeping diagonal brass light streak from lower left to upper right, mostly black, for a screen transition` |
| `splash_imperial.png` | 1024×1536 | `tall vertical splash art panel, abstract: deep blue armour plate with a brass winged-skull emblem, dramatic rim light, no figure, no text` |
| `splash_nurgle.png` | 1024×1536 | `tall vertical splash art panel, abstract: corroded olive-green armour plate with a brass three-ringed plague emblem, dripping texture, no figure, no text` |

---

## 3. 优先级

如果一次做不完，按这个顺序：

1. **A 面板九宫格 + B 技能图标**（替换掉程序画的矢量图，观感变化最大）
2. **D 状态/词条图标**（规则可视化已经在用，现在是 emoji）
3. **E 骰面 + 筹码 + 卡垫**（桌面实物的质感）
4. **G 标题主视觉**
5. C、F 其余

---

## 4. 交付后我会做什么

- 九宫格：导入为 `StyleBoxTexture`，替换 `KTTheme.panel()` 三档样式
- 图标：替换 `KTWidgets.draw_tactical_icon()` 的程序绘制，技能槽、系统栏、状态标签改为贴图
- 骰面：替换 `KTDice3D._get_face_mat()` 的程序生成贴图；筹码、VP 标记、卡垫、TP 块贴到 `KTTableProps3D`
- 横幅：阶段 toast 与胜利画面换底图
- 标题：`title_keyart` 作为主菜单背景，左侧菜单叠在预留的暗区上

需要我先出一张"目标效果拼贴"（用现有截图 + 示意框标出每个素材落位）的话说一声。
