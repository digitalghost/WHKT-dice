# 方案二：机械教沉思者终端（Adeptus Mechanicus Auspex & Cogitator）

> **视觉定位**：深度还原《战锤 40K》帝国机械教（Adeptus Mechanicus）的**掌上鸟卜仪（Auspex Scanner）与舰桥沉思者终端（Cogitator Console）**。采用厚重的做旧铆钉黄铜装甲外框、真空辉光数码管（Nixie Tube）比分读数、内嵌阴极射线管（CRT）荧光绿雷达屏与工业拨杆开关，让玩家的手机或平板在战棋桌面上化身为真正的 41 千年帝国战术圣物。

---

## 1. 装饰图片与视觉元素清单（位于 [`assets/`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/assets/)）

| 资源文件 | 用途说明 |
| :--- | :--- |
| [`auspex_radar_dial.jpg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/assets/auspex_radar_dial.jpg) | 机械教鸟卜仪 CRT 荧光绿圆盘雷达扫描仪（带做旧黄铜铆钉外圈与实体拨杆），用于火控锁定与圣物库总控 |
| [`emblem_cog_mechanicum.jpg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/assets/emblem_cog_mechanicum.jpg) | 欧姆尼赛亚机械齿轮骷髅圣徽（Opus Mechanicum），用于转折点中枢徽章 |
| [`header_cogitator_plate.jpg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/assets/header_cogitator_plate.jpg) | 重型铆钉青铜装甲顶板（镶嵌伺服颅骨浮雕与琥珀色真空指示灯），用于顶栏装甲框 |
| [`crt_reticle_overlay.svg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/assets/crt_reticle_overlay.svg) | 鸟卜仪特工头像锁定十字准星与同心圆波段叠加层 |
| [`divider_cogitator.svg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/assets/divider_cogitator.svg) | 铆钉与 `++ OMNISSIAH ++` 机魂铭文金属分割条 |

---

## 2. 手机版 vs 平板版双端架构差异

### 📱 手机版（[`phone.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/phone.html) · 掌上鸟卜仪生命与弹道扫描仪）
- **双色辉光数码管（Nixie Tube）计分舱**：顶部将双方 CP 与 VP 封装在深邃的琥珀黄与荧光绿辉光管槽内。
- **鸟卜仪圆形观察窗 + 生命体征百分比（VITAL %）**：每位特工头像外罩圆形黄铜舷窗与绿色雷达准星，耐伤除了显示 `04/14`，还同步换算为 `VITAL: 28%` 生命体征读数，受创时整行切换为琥珀/猩红报警态。
- **底部黄铜实体按键组**：采用凸起黄铜斜纹物理按键造型，一键触发行动与机魂结算。

### 💻 平板版（[`tablet.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/tablet.html) · 大贤者舰桥级三舱联控沉思者）
- **左/右双生物监测舱 + 中央雷达与双示波器火控台**：
  - 左右两舱分别实时监控 ALPHA 与 OMEGA 两支杀戮小队的生命体征与小队专属计数器（如方尖碑节点、风暴标记）；
  - 中央上方内嵌 196×196 高清鸟卜仪雷达盘与黄铜拨杆矩阵，中央下方为**攻击脉冲示波器 vs 防御护盾示波器**同屏对撞，极具战锤 40K 复古未来科技感。

---

## 3. 效果图预览

- 手机版鸟卜仪生命监测台：[`previews/phone_battle.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/previews/phone_battle.png)
- 手机版弹道沉思者结算：[`previews/phone_combat.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/previews/phone_combat.png)
- 手机版机魂圣物总控：[`previews/phone_hub.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/previews/phone_hub.png)
- 平板版舰桥双联沉思者控制台：[`previews/tablet_command.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案二_机械教沉思者终端/previews/tablet_command.png)
