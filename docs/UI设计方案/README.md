# 《战锤 40K：杀戮小队》战术控制台 — 三套全新 UI 设计方案总览

本目录存放为应用全新设计的 **三套《战锤 40K：杀戮小队》主题 UI 备选方案**。每套方案均打破了原有应用的表单式设计，结合战棋桌面高频单手操作与双人同屏对局场景，分别提供了 **手机版（412×892 竖屏）** 与 **平板版（1280×800 横屏）** 两套截然不同的专属架构，并配套生成了战锤 40K 专属装饰图片与矢量徽记。

> 💡 **快速浏览入口**：可以直接在浏览器打开 [`index.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/index.html) 同屏对比三套方案的手机版与平板版，也可以直接查看下方各目录中的高清渲染图（`previews/*.png`）。

---

## 目录结构与方案导航

### 1. [`方案一_KT24战术竞技场/`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/README.md)（Spec-Ops Tactical HUD · 官方三版现代军工风）
- **核心设计**：45° 斜切八边形几何、高对比警戒橙（`#FF5500`）与战术冰青（`#00E0F0`）、分段式斜切装甲血条。
- **手机版**：[`phone.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/phone.html)（底部单手拇指 1AP 战术甲板 + 竖屏激光配对骰池）
  - 预览图：[`phone_battle.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/previews/phone_battle.png) ｜ [`phone_combat.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/previews/phone_combat.png) ｜ [`phone_hub.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/previews/phone_hub.png)
- **平板版**：[`tablet.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/tablet.html)（左/中/右三栏同屏全景指挥台：双军特工遥测 + 中央双骰盘同屏对撞 + 底部结构化时间线与 PDF 原文窗）
  - 预览图：[`tablet_command.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/previews/tablet_command.png)
- **专属装饰图素**：[`assets/`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/assets/)（含战区废墟全息横幅 `hero_killzone_banner.jpg`、钛钢骷髅匕首徽记 `emblem_specops_skull.jpg`、碳纤陶钢装甲纹理 `texture_carbon_armor.jpg`、交战/隐匿矢量令牌等）

---

### 2. [`方案二_机械教沉思者终端/`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/README.md)（Adeptus Mechanicus Auspex & Cogitator · 鸟卜仪与重装甲风）
- **核心设计**：做旧铆钉黄铜装甲板、真空辉光数码管（Nixie Tube）比分槽、内嵌阴极射线管（CRT）荧光绿雷达扫描屏与生命体征 `VITAL %` 读数。
- **手机版**：[`phone.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/phone.html)（掌上鸟卜仪生命监测仪 + 机械黄铜实体按键组）
  - 预览图：[`phone_battle.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/previews/phone_battle.png) ｜ [`phone_combat.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/previews/phone_combat.png) ｜ [`phone_hub.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/previews/phone_hub.png)
- **平板版**：[`tablet.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/tablet.html)（大贤者舰桥级三舱联控沉思者：左右双生物监测舱 + 中央鸟卜仪圆盘雷达与双通道攻防示波器）
  - 预览图：[`tablet_command.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/previews/tablet_command.png)
- **专属装饰图素**：[`assets/`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/assets/)（含鸟卜仪圆盘扫描仪 `auspex_radar_dial.jpg`、欧姆尼赛亚齿轮骷髅徽章 `emblem_cog_mechanicum.jpg`、伺服颅骨重装甲顶板 `header_cogitator_plate.jpg` 等）

---

### 3. [`方案三_帝国审判庭圣契/`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/README.md)（Inquisitorial Dataslate & Codex · 哥特圣物与羊皮纸卡册风）
- **核心设计**：黑曜石镶金哥特大教堂拱门、高对比度做旧羊皮纸实体数据卡面（复刻 `APL | 移动 | 豁免 | 耐伤` 经典四格属性窗）、猩红火漆纯度印章（Purity Seals）状态飘带挂载。
- **手机版**：[`phone.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/phone.html)（随身审判庭羊皮纸数据卡册 + 白刃/远程圣裁祭坛）
  - 预览图：[`phone_battle.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/previews/phone_battle.png) ｜ [`phone_combat.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/previews/phone_combat.png) ｜ [`phone_hub.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/previews/phone_hub.png)
- **平板版**：[`tablet.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/tablet.html)（对开本《战区圣典》左右羊皮纸书卷 + 中央黑曜石镶金天平圣裁祭坛）
  - 预览图：[`tablet_command.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/previews/tablet_command.png)
- **专属装饰图素**：[`assets/`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/assets/)（含鎏金帝国双头鹰浮雕横幅 `header_imperial_aquila.jpg`、猩红火漆纯度印章 `badge_purity_seal.jpg`、焦边做旧羊皮纸纹理 `texture_aged_parchment.jpg` 等）
