# Kill Team 骰子模拟器 — 拟物控制台深度美化设计

日期：2026-09-19
状态：已获用户批准（2026-09-19）

## 背景

项目为 Warhammer 40K Kill Team 桌游骰子模拟器（单 Activity，包名 `com.example.helloworld`）。UI 为纯 XML/Views，3D 物理骰子在 WebView 中渲染（`@3d-dice/dice-box` + ammo.js）。现有暗金/血红主题概念良好，但组件风格不统一（Material2 默认控件与自定义主题冲突）、无字体系统、无系统栏适配，并存在若干 bug 与死代码。

## 目标

将应用视觉升级为「帝国军务部拟物控制台」风格，达到深度美化、酷炫的效果；同时修复已知问题。

## 范围

### 做

- 全面视觉重做（保持 XML/Views，不迁移 Compose）
- 修复重投统计 bug、启动器图标引用
- 清理死代码与未用依赖
- Edge-to-edge 系统栏适配

### 不做（YAGNI）

- 不迁移 Compose / Material3
- 不新增功能
- 不改动 WebView 3D 骰子引擎（`app/src/main/assets/`）
- 不改包名

## 设计

### 1. 视觉系统

**配色**（扩展 `res/values/colors.xml`）：

| 角色 | 色值（近似） | 用途 |
|---|---|---|
| 暗影黑 | `#0A0908` | 背景 |
| 炮铜金属 | `#2A241C`–`#3D352A` | 面板、按钮基底 |
| 帝国金 | `#C89B3C` / `#E8C573` | 主强调、描边、标题 |
| 血猩红 | `#A01818` / `#C21F1F` | ROLL 按钮、危险状态 |
| 翠绿 | `#3FA34D` | 状态灯、成功数值 |
| 冷灰钢 | `#8A8D91` | 次要文字 |

**字体**：

- 标题：打包一款 OFL 许可哥特/军事风字体（首选 Cinzel Decorative 或 Pirata One，放入 `res/font/`）
- 正文/数值：系统 serif bold + letterSpacing 模拟蚀刻感；统计数字用等宽粗体

**面板分层拟物**（纯 XML drawable：layer-list + gradient + stroke，不引入位图资源）：

- 外框：深色金属板 + 四角铆钉（layer-list 叠加小圆点渐变）
- 内卡：拉丝金属感（细渐变条纹）+ 1px 内阴影/顶部高光
- 所有硬编码颜色收拢到 `colors.xml`，字符串收拢到 `strings.xml`

**状态灯**：控制面板角落的圆形指示灯——就绪时翠绿呼吸闪烁（alpha 动画），掷骰进行中变琥珀色。

### 2. 组件重做

- **掷骰类型选择**（HIT/WOUND/SAVE/CUSTOM）：裸 TextView → 金属分段开关。选中段：内凹阴影 + 金色描边；未选中：拉丝金属 + 灰钢文字。
- **三组开关**（D6/D3、目标值 2+…6+、重投 NONE/1s/FAIL/ALL）：Material2 默认样式 → 与掷骰类型一致的自定义拟物分段控件，统一消除视觉冲突。实现方式：自定义 style + 选中态 drawable，仍基于现有 `MaterialButtonToggleGroup` 以减少逻辑改动。
- **ROLL 按钮**：大型黄铜浮雕按钮（径向渐变高光 + 描边 + 底部阴影）。按下：scale 0.96 + 阴影收浅的物理下沉动画，配合现有触觉反馈。
- **结果统计**：四个「仪表窗」——铆钉边框 + 凹槽背景，数值变化时数字滚动/翻转动画（ValueAnimator 插值）。
- **骰子数量 SeekBar**：自定义 thumb（黄铜圆钮）+ track（凹槽轨道）。

### 3. 布局与系统适配

- 保持单屏 + 全屏 WebView 3D 骰子结构。
- 控制区移入底部可拖拽面板（底部固定，提供收起/展开手柄），让 3D 骰子舞台充分露出。
- Edge-to-edge：透明状态栏/导航栏，`WindowInsets` 处理 padding，移除 `themes.xml` 中硬编码 `statusBarColor`。

### 4. Bug 修复与清理

- **重投统计 bug**：`MainActivity.performReroll()` 当前记录重投骰子的索引；`DiceWebInterface.onRollComplete` 改为按索引将重投结果合并回 `currentValues`，而不是整体替换，统计基于合并后的完整结果计算。
- **启动器图标**：`AndroidManifest.xml` 的 `android:icon`/`roundIcon` 改为 `@mipmap/ic_launcher`（已有 adaptive icon）。
- **JSON 解析**：`DiceWebInterface.parseResults()` 改用 `org.json`（platform 内置，无需新依赖）替换手写数字扫描。
- **死代码清理**：删除 `DiceView.kt`、`DiceResultAdapter.kt`、`DiceRoller.kt` 中的死逻辑（保留 `DiceType`/`RerollMode` 枚举）、`res/layout/item_dice.xml`、`res/drawable/bg_dice.xml`，以及未使用的 `constraintlayout`、`recyclerview` 依赖。
- 删除已提交的 `.DS_Store` 文件。

## 错误处理

- WebView JS 未就绪（`diceReady == false`）时 ROLL 按钮禁用并显示提示，不再静默无响应。
- JSON 解析失败时记录日志并保留上次统计，不崩溃。

## 测试

- 手动验证清单：各掷骰类型/开关组合 → ROLL → 统计正确；REROLL 后统计为合并结果；RESET 恢复初始；旋转屏幕状态保留现状（`configChanges` 已处理）；面板拖拽/收起；深色系统栏下对比度。
- 构建验证：`./gradlew assembleDebug` 通过，无新增警告。

## 关键文件

- `app/src/main/res/layout/activity_main.xml` — 单屏布局重做
- `app/src/main/res/values/{colors,strings,styles,themes}.xml` — 视觉系统
- `app/src/main/res/drawable/bg_*.xml` — 拟物 drawable（新增/重做）
- `app/src/main/res/font/` — 新增字体
- `app/src/main/java/com/example/helloworld/MainActivity.kt` — 面板动画、状态灯、统计动画、重投合并逻辑
- `app/src/main/java/com/example/helloworld/DiceWebInterface.kt` — JSON 解析修复
- `app/src/main/AndroidManifest.xml` — 图标修复
- `app/build.gradle.kts` — 移除未用依赖
