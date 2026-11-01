# 方案一：KT24 战术竞技场（Spec-Ops Tactical HUD）

> **视觉定位**：严格对标《战锤 40K：杀戮小队》三版（KT2024）官方规则书、官方特种测量尺与切角塑料命令令牌（Order Tokens）的现代暗黑军工风格。彻底抛弃原应用的圆角表单设计，采用 **45° 斜切八边形几何、高对比警戒橙与战术冰青、分段式装甲耐伤格、单手拇指战术甲板**。

---

## 1. 装饰图片与视觉元素清单（位于 [`assets/`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/assets/)）

| 资源文件 | 用途说明 |
| :--- | :--- |
| [`hero_killzone_banner.jpg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/assets/hero_killzone_banner.jpg) | 杀戮战区哥特工业废墟 + 橙色全息激光网格横幅，用于首页与平板交火抬头背景 |
| [`emblem_specops_skull.jpg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/assets/emblem_specops_skull.jpg) | 杀戮小队钛钢骷髅与交叉战术匕首徽记，用于顶栏品牌与交火中心勋章 |
| [`texture_carbon_armor.jpg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/assets/texture_carbon_armor.jpg) | 暗黑陶钢与碳纤装甲板底纹（带微划痕与斜向警戒暗纹），用于全局背景层 |
| [`token_engage.svg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/assets/token_engage.svg) | KT24 切角高光信号橙「交战（Engage）」命令令牌图标 |
| [`token_conceal.svg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/assets/token_conceal.svg) | KT24 切角战术冰青「隐匿（Conceal）」命令令牌图标 |
| [`divider_specops.svg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/assets/divider_specops.svg) | 斜切警戒条纹 + `KT//24` 战术分割线 |

---

## 2. 手机版 vs 平板版双端架构差异

### 📱 手机版（[`phone.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/phone.html) · 单手竖屏特种战术终端）
- **人体工学拇指操作甲板（Bottom Thumb-Zone Deck）**：玩家左手拿实体棋子或量尺时，右手大拇指在屏幕底部即可一键点击 `移动 (1AP)`、`远程射击 (1AP)`、`近战格斗 (1AP)`、`任务行动 (1AP)`，无需满屏寻找入口。
- **分段式斜切装甲血条**：告别单调的数字加减，每位特工右侧配有斜切装甲格（每格代表耐伤阶段），跌破半血受创阈值时自动切换为猩红色并闪烁 `▲ 受创（命中恶化 1）` 芯片。
- **激光配对骰子池**：攻击保留骰与防御成功骰上下对峙，点击配对抵挡后实时展示承伤关口总计。

### 💻 平板版（[`tablet.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/tablet.html) · 横屏桌面三栏全景指挥台）
- **左/中/右三栏同屏零跳转架构**：
  - **左翼栏（橙色）**：A 方全体特工状态、受创进度、在场人数、CP/VP 实时遥测；
  - **中央交火竞技场**：顶部为战区实况横幅，中部为**攻防双骰盘同屏并列** + 中央伤害关口结算核，底部左半侧为带撤回删除线的**结构化战局时间线**，右半侧为**带 PDF 页码的规则原文速查窗**；
  - **右翼栏（冰青色）**：B 方全体特工状态、受击目标高亮与残废计分追踪。

---

## 3. 效果图预览

- 手机版对局主控台：[`previews/phone_battle.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/previews/phone_battle.png)
- 手机版攻防骰裁决台：[`previews/phone_combat.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/previews/phone_combat.png)
- 手机版战区总控首页：[`previews/phone_hub.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/previews/phone_hub.png)
- 平板版全景指挥台：[`previews/tablet_command.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案一_KT24战术竞技场/previews/tablet_command.png)
