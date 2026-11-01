# 方案三：帝国审判庭圣契（Inquisitorial Dataslate & Codex）

> **视觉定位**：致敬《战锤 40K》最经典的**哥特帝国圣物、审判庭卷宗与实体羊皮纸数据卡（Datacard）**美学。采用**黑曜石镶金哥特拱门底板 + 高对比度做旧羊皮纸数据卡面 + 猩红火漆纯度印章（Purity Seals）**。不仅战锤史诗感拉满，而且浅色羊皮纸卡面在明亮战棋室灯光下具备极佳的阅读舒适度。

---

## 1. 装饰图片与视觉元素清单（位于 [`assets/`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/assets/)）

| 资源文件 | 用途说明 |
| :--- | :--- |
| [`header_imperial_aquila.jpg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/assets/header_imperial_aquila.jpg) | 帝国双头鹰（Imperial Aquila）与带翼骷髅黑曜石镶金哥特浮雕横幅，用于手机与平板圣契顶栏 |
| [`badge_purity_seal.jpg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/assets/badge_purity_seal.jpg) | 审判庭猩红火漆纯度印章与拉丁经文羊皮纸飘带立绘，用于状态挂载与圣裁核心勋章 |
| [`texture_aged_parchment.jpg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/assets/texture_aged_parchment.jpg) | 战损焦边做旧帝国羊皮纸底纹（带隐约哥特铭文水印），用于特工数据卡与圣典书页底纹 |
| [`aquila_crest_gold.svg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/assets/aquila_crest_gold.svg) | 矢量鎏金双头鹰扉页纹章 |
| [`seal_wax_ribbon.svg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/assets/seal_wax_ribbon.svg) | 矢量火漆纯度印章组件（用于挂载特工 Buff 与规则修饰） |
| [`divider_gothic_skull.svg`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/assets/divider_gothic_skull.svg) | 双线鎏金哥特骷髅分割线 |

---

## 2. 手机版 vs 平板版双端架构差异

### 📱 手机版（[`phone.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/phone.html) · 审判庭随身羊皮纸数据卡册）
- **原汁原味的实体数据卡四格属性窗**：在羊皮纸卡面上还原战棋玩家最熟悉的 `APL | 移动 | 豁免 | 耐伤` 独立四宫格，字体铿锵有力。
- **火漆纯度印章（Purity Seal）挂载机制**：所有临时武器修饰（如 `+严重 · 圣勉光环`）或特工状态不再是冷冰冰的文本框，而是像一枚盖在卡片下沿的**猩红火漆印章配羊皮纸飘带**。

### 💻 平板版（[`tablet.html`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/tablet.html) · 对开本《战区圣典》与哥特圣裁祭坛）
- **左卷 / 中央黑曜石圣裁祭坛 / 右卷 对开装帧架构**：
  - 左右两卷如同摊开的古老羊皮纸圣典，分别记载双方特遣队的在场特工与纯度印章；
  - 中央为黑曜石镶金的**白刃与远程圣裁祭坛（Altar of Judgment）**，下方并列羊皮纸《圣战编年史（撤回留痕卷宗）》与红字标注重点的《圣典律法原文》。

---

## 3. 效果图预览

- 手机版圣契数据卡册：[`previews/phone_battle.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/previews/phone_battle.png)
- 手机版审判裁决祭坛：[`previews/phone_combat.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/previews/phone_combat.png)
- 手机版战区圣典总目：[`previews/phone_hub.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/previews/phone_hub.png)
- 平板版对开本战区圣典：[`previews/tablet_command.png`](file:///Users/alanliu-15air/Projects/anroid1st/docs/UI设计方案/方案三_帝国审判庭圣契/previews/tablet_command.png)
