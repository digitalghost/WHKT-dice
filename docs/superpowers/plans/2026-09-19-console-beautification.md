# Kill Team 骰子模拟器拟物控制台美化 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将单屏骰子应用 UI 全面升级为战锤40K 拟物控制台风格（金属面板、铆钉、哥特字体、状态灯、可拖拽底部面板），并修复重投统计 bug、启动器图标、清理死代码。

**Architecture:** 保持纯 XML/Views + 单 Activity + WebView 3D 骰子引擎不变。视觉全部通过 XML drawable（layer-list/gradient/stroke）+ 颜色/样式资源实现，不引入位图。逻辑修复抽取为纯 Kotlin 对象（`DiceResultParser`、`RollLogic`）以便 JVM 单元测试。

**Tech Stack:** Kotlin 2.0.0, AGP 8.5.2, AppCompat, Material Components 1.12.0, JUnit 4.13.2（仅测试）。minSdk 24 / targetSdk 34。

**参考设计文档:** `docs/superpowers/specs/2026-09-19-console-beautification-design.md`

## Global Constraints

- 不迁移 Compose；不改动 `app/src/main/assets/`（3D 骰子引擎）；不改包名。
- 不新增运行时依赖；仅允许新增测试依赖 `junit:junit:4.13.2`。
- minSdk 24：layer-list item 的 `android:gravity`/`width`/`height` 属性要求 API 23+，可用。
- 所有颜色引用 `@color/...`，所有用户可见字符串引用 `@string/...`，禁止硬编码。
- 每个任务结束必须 `./gradlew assembleDebug` 编译通过（涉及单元测试的任务另需 `./gradlew :app:testDebugUnitTest` 通过）。
- 任务末尾的 commit 步骤仅当用户明确要求 git 提交时执行，否则跳过。

---

### Task 1: 清理死代码 + 修复启动器图标 + 测试基架

**Files:**
- Delete: `app/src/main/java/com/example/helloworld/DiceView.kt`
- Delete: `app/src/main/java/com/example/helloworld/DiceResultAdapter.kt`
- Delete: `app/src/main/res/layout/item_dice.xml`
- Delete: `app/src/main/res/drawable/bg_dice.xml`
- Delete: `app/src/main/assets/dice-box/.DS_Store`, `app/libs/.DS_Store`, `app/src/main/java/com/example/helloworld/.DS_Store`
- Modify: `app/src/main/java/com/example/helloworld/DiceRoller.kt`（全文替换）
- Modify: `app/src/main/java/com/example/helloworld/DiceWebInterface.kt`（删除未用 import）
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: 无
- Produces: `enum class DiceType(val sides: Int)`（D6/D3）、`enum class RerollMode`（NONE/ONES/FAILURES/ALL）—— 后续任务与 MainActivity 均依赖这两个枚举，签名不变。

- [ ] **Step 1: 删除死代码文件**

```bash
rm app/src/main/java/com/example/helloworld/DiceView.kt \
   app/src/main/java/com/example/helloworld/DiceResultAdapter.kt \
   app/src/main/res/layout/item_dice.xml \
   app/src/main/res/drawable/bg_dice.xml \
   app/src/main/assets/dice-box/.DS_Store \
   app/libs/.DS_Store \
   app/src/main/java/com/example/helloworld/.DS_Store
```

- [ ] **Step 2: 将 DiceRoller.kt 裁剪为仅保留枚举**

全文替换为：

```kotlin
package com.example.helloworld

enum class DiceType(val sides: Int) {
    D6(6),
    D3(3)
}

enum class RerollMode {
    NONE,
    ONES,
    FAILURES,
    ALL
}
```

- [ ] **Step 3: 删除 DiceWebInterface.kt 中未使用的 import**

删除第 7 行：

```kotlin
import androidx.recyclerview.widget.RecyclerView
```

- [ ] **Step 4: 更新 app/build.gradle.kts 依赖**

移除 constraintlayout 与 recyclerview，新增 JUnit 测试依赖。dependencies 块替换为：

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 5: 修复 AndroidManifest.xml 启动器图标**

将 `android:icon="@drawable/ic_launcher_foreground"` 替换为两行：

```xml
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
```

- [ ] **Step 6: 验证构建与测试基架**

```bash
./gradlew assembleDebug && ./gradlew :app:testDebugUnitTest
```

预期：BUILD SUCCESSFUL（此时没有测试，`testDebugUnitTest` 报 "no tests found" 属正常）。

- [ ] **Step 7: Commit（可选，见 Global Constraints）**

```bash
git add -A
git commit -m "chore: remove dead 2D-dice code, fix launcher icon, add junit"
```

---

### Task 2: DiceResultParser — 健壮的掷骰结果解析（TDD）

`DiceWebInterface.parseResults()` 目前用手写数字扫描，且无法单测（私有方法 + 依赖 Android）。抽取为纯 Kotlin 单例并用正则解析；**不用 org.json**，因为 org.json 在 JVM 单元测试中是抛出异常的 stub。

**Files:**
- Create: `app/src/main/java/com/example/helloworld/DiceResultParser.kt`
- Create: `app/src/test/java/com/example/helloworld/DiceResultParserTest.kt`
- Modify: `app/src/main/java/com/example/helloworld/DiceWebInterface.kt`

**Interfaces:**
- Consumes: 无
- Produces: `DiceResultParser.parse(json: String): List<Int>` — 非数组输入返回 emptyList；DiceWebInterface 在 Task 2 末尾改用它。

- [ ] **Step 1: 编写失败的单元测试**

创建 `app/src/test/java/com/example/helloworld/DiceResultParserTest.kt`：

```kotlin
package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Test

class DiceResultParserTest {

    @Test
    fun `parses normal array`() {
        assertEquals(listOf(3, 1, 6, 4), DiceResultParser.parse("[3, 1, 6, 4]"))
    }

    @Test
    fun `parses empty array`() {
        assertEquals(emptyList<Int>(), DiceResultParser.parse("[]"))
    }

    @Test
    fun `tolerates whitespace and newlines`() {
        assertEquals(listOf(2, 5), DiceResultParser.parse(" [ 2,\n 5 ] "))
    }

    @Test
    fun `non-array input returns empty`() {
        assertEquals(emptyList<Int>(), DiceResultParser.parse("garbage"))
        assertEquals(emptyList<Int>(), DiceResultParser.parse("{\"value\":3}"))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.helloworld.DiceResultParserTest"
```

预期：编译失败，`Unresolved reference: DiceResultParser`。

- [ ] **Step 3: 实现 DiceResultParser**

创建 `app/src/main/java/com/example/helloworld/DiceResultParser.kt`：

```kotlin
package com.example.helloworld

object DiceResultParser {
    private val numberPattern = Regex("-?\\d+")

    fun parse(json: String): List<Int> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        return numberPattern.findAll(trimmed).map { it.value.toInt() }.toList()
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.helloworld.DiceResultParserTest"
```

预期：4 个测试全部 PASS。

- [ ] **Step 5: DiceWebInterface 改用 DiceResultParser**

在 `DiceWebInterface.kt` 中，删除私有方法 `parseResults`（第 35–60 行整体），并将 `onRollComplete` 中的调用改为：

```kotlin
    @JavascriptInterface
    fun onRollComplete(resultsJson: String) {
        try {
            val values = DiceResultParser.parse(resultsJson)
            handler.post { callback.onRollComplete(values) }
        } catch (e: Exception) {
            Log.e("DiceWebInterface", "Parse error: ${e.message}", e)
        }
    }
```

- [ ] **Step 6: 验证构建 + 全部测试**

```bash
./gradlew assembleDebug && ./gradlew :app:testDebugUnitTest
```

预期：BUILD SUCCESSFUL，4 个测试 PASS。

- [ ] **Step 7: Commit（可选）**

```bash
git add -A
git commit -m "refactor: extract DiceResultParser with regex-based parsing and unit tests"
```

---

### Task 3: 重投统计 bug 修复 — RollLogic + 按索引合并（TDD）

现状 bug：`performReroll()` 只对匹配骰子发起重投，但 `onRollComplete(values)` 收到的只是重投子集，却直接 `currentValues = values`，导致统计总数变小。修复：记录被重投骰子的索引，回调时按索引合并。

**Files:**
- Create: `app/src/main/java/com/example/helloworld/RollLogic.kt`
- Create: `app/src/test/java/com/example/helloworld/RollLogicTest.kt`
- Modify: `app/src/main/java/com/example/helloworld/MainActivity.kt`

**Interfaces:**
- Consumes: `RerollMode`、`DiceType`（Task 1 保留）
- Produces:
  - `RollLogic.rerollIndices(values: List<Int>, mode: RerollMode, target: Int): List<Int>`
  - `RollLogic.mergeRerollResults(current: List<Int>, indices: List<Int>, newValues: List<Int>): List<Int>` — indices/newValues 数量不一致时原样返回 current。

- [ ] **Step 1: 编写失败的单元测试**

创建 `app/src/test/java/com/example/helloworld/RollLogicTest.kt`：

```kotlin
package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Test

class RollLogicTest {

    @Test
    fun `rerollIndices ONES picks only 1s`() {
        assertEquals(
            listOf(1, 3),
            RollLogic.rerollIndices(listOf(4, 1, 6, 1), RerollMode.ONES, target = 3)
        )
    }

    @Test
    fun `rerollIndices FAILURES picks values below target`() {
        assertEquals(
            listOf(0, 2),
            RollLogic.rerollIndices(listOf(2, 5, 1, 4), RerollMode.FAILURES, target = 3)
        )
    }

    @Test
    fun `rerollIndices ALL picks everything, NONE picks nothing`() {
        assertEquals(listOf(0, 1, 2), RollLogic.rerollIndices(listOf(1, 2, 3), RerollMode.ALL, 3))
        assertEquals(emptyList<Int>(), RollLogic.rerollIndices(listOf(1, 2, 3), RerollMode.NONE, 3))
    }

    @Test
    fun `mergeRerollResults replaces values at indices`() {
        assertEquals(
            listOf(3, 5, 4, 6),
            RollLogic.mergeRerollResults(listOf(3, 1, 2, 6), listOf(1, 2), listOf(5, 4))
        )
    }

    @Test
    fun `mergeRerollResults size mismatch returns current unchanged`() {
        val current = listOf(3, 1, 2)
        assertEquals(current, RollLogic.mergeRerollResults(current, listOf(0, 1), listOf(6)))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.helloworld.RollLogicTest"
```

预期：编译失败，`Unresolved reference: RollLogic`。

- [ ] **Step 3: 实现 RollLogic**

创建 `app/src/main/java/com/example/helloworld/RollLogic.kt`：

```kotlin
package com.example.helloworld

object RollLogic {

    fun rerollIndices(values: List<Int>, mode: RerollMode, target: Int): List<Int> =
        values.mapIndexedNotNull { index, value ->
            val reroll = when (mode) {
                RerollMode.ONES -> value == 1
                RerollMode.FAILURES -> value < target
                RerollMode.ALL -> true
                RerollMode.NONE -> false
            }
            if (reroll) index else null
        }

    fun mergeRerollResults(
        current: List<Int>,
        indices: List<Int>,
        newValues: List<Int>
    ): List<Int> {
        if (indices.size != newValues.size) return current
        val merged = current.toMutableList()
        indices.forEachIndexed { i, dieIndex ->
            if (dieIndex in merged.indices) merged[dieIndex] = newValues[i]
        }
        return merged
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.helloworld.RollLogicTest"
```

预期：5 个测试全部 PASS。

- [ ] **Step 5: MainActivity 接入合并逻辑**

5a. 在字段区（`private var hasRolled = false` 附近）新增：

```kotlin
    private var pendingRerollIndices: List<Int>? = null
```

5b. `performReroll()` 整体替换为：

```kotlin
    private fun performReroll() {
        if (currentValues.isEmpty() || !hasRolled) return
        val mode = getRerollMode()
        if (mode == RerollMode.NONE) return

        vibrate(30)
        btnReroll.isEnabled = false

        val indices = RollLogic.rerollIndices(currentValues, mode, getTargetValue())
        if (indices.isEmpty()) {
            btnReroll.isEnabled = true
            return
        }

        pendingRerollIndices = indices
        val notation = "${indices.size}d${getDiceType().sides}"
        diceWebView.evaluateJavascript("doRoll('$notation');", null)
    }
```

5c. `onRollComplete()` 中，将 `currentValues = values` 替换为：

```kotlin
        val pending = pendingRerollIndices
        currentValues = if (pending != null) {
            pendingRerollIndices = null
            RollLogic.mergeRerollResults(currentValues, pending, values)
        } else {
            values
        }
```

注意：替换后方法内后续的统计代码必须改为基于 `currentValues` 而非参数 `values`（把 `values.count` / `values.size` 全部改为 `currentValues.count` / `currentValues.size`）。

5d. `performRoll()` 中 `diceWebView.evaluateJavascript(...)` 之前新增一行：

```kotlin
        pendingRerollIndices = null
```

5e. `resetResults()` 中 `currentValues = emptyList()` 之后新增一行：

```kotlin
        pendingRerollIndices = null
```

- [ ] **Step 6: 验证构建 + 全部测试**

```bash
./gradlew assembleDebug && ./gradlew :app:testDebugUnitTest
```

预期：BUILD SUCCESSFUL，累计 9 个测试 PASS。

- [ ] **Step 7: Commit（可选）**

```bash
git add -A
git commit -m "fix: merge reroll results by index so summary stays correct"
```

---

### Task 4: 视觉基础 — 颜色、字符串、字体、主题、分段控件样式

**Files:**
- Create: `app/src/main/res/font/pirata_one_regular.ttf`（下载）、`app/src/main/res/font/OFL.txt`（下载）
- Create: `app/src/main/res/color/segment_background.xml`、`app/src/main/res/color/segment_stroke.xml`、`app/src/main/res/color/segment_text.xml`、`app/src/main/res/color/roll_button_text.xml`
- Create: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/values/colors.xml`（追加）
- Modify: `app/src/main/res/values/strings.xml`（追加，暂不删旧字符串）
- Modify: `app/src/main/res/values/themes.xml`（全文替换）

**Interfaces:**
- Consumes: 无
- Produces（后续任务引用的资源名）:
  - 颜色：`metal_dark/metal_mid/metal_light/rivet/rivet_highlight/lamp_green/lamp_amber/lamp_dim/text_steel`
  - 字符串：`dice_d6/dice_d3/target_2..target_6/reroll_none/reroll_ones/reroll_failures/reroll_all/dice_count_initial/successes_label/failures_label/criticals_label/total_label/status_lamp_desc/panel_handle_desc`
  - 样式：`ConsoleSegmentButton`
  - 字体：`@font/pirata_one_regular`
  - 颜色选择器：`@color/segment_background`、`@color/segment_stroke`、`@color/segment_text`、`@color/roll_button_text`

- [ ] **Step 1: 下载 Pirata One 字体（OFL 许可）**

```bash
mkdir -p app/src/main/res/font
curl -L -o app/src/main/res/font/pirata_one_regular.ttf \
  https://github.com/google/fonts/raw/main/ofl/pirataone/PirataOne-Regular.ttf
curl -L -o app/src/main/res/font/OFL.txt \
  https://github.com/google/fonts/raw/main/ofl/pirataone/OFL.txt
file app/src/main/res/font/pirata_one_regular.ttf
```

预期：`file` 输出包含 "TrueType"（若下载到 HTML 错误页则重试或检查网络）。

- [ ] **Step 2: colors.xml 追加新色板**

在 `</resources>` 前追加：

```xml
    <!-- Gunmetal console -->
    <color name="metal_dark">#221D16</color>
    <color name="metal_mid">#363026</color>
    <color name="metal_light">#4A4234</color>
    <color name="rivet">#6B5D43</color>
    <color name="rivet_highlight">#9A8A66</color>

    <!-- Status lamps -->
    <color name="lamp_green">#3FA34D</color>
    <color name="lamp_amber">#D98E2B</color>
    <color name="lamp_dim">#4A4438</color>

    <!-- Steel text -->
    <color name="text_steel">#8A8D91</color>
```

- [ ] **Step 3: strings.xml 追加新字符串**

在 `</resources>` 前追加（旧的 `successes_format` 等保留，Task 7 再删）：

```xml
    <string name="dice_d6">D6</string>
    <string name="dice_d3">D3</string>
    <string name="target_2">2+</string>
    <string name="target_3">3+</string>
    <string name="target_4">4+</string>
    <string name="target_5">5+</string>
    <string name="target_6">6+</string>
    <string name="reroll_none">NONE</string>
    <string name="reroll_ones">1s</string>
    <string name="reroll_failures">FAIL</string>
    <string name="reroll_all">ALL</string>
    <string name="dice_count_initial">5</string>
    <string name="successes_label">SUCCESSES</string>
    <string name="failures_label">FAILURES</string>
    <string name="criticals_label">CRITS</string>
    <string name="total_label">TOTAL</string>
    <string name="status_lamp_desc">Status lamp</string>
    <string name="panel_handle_desc">Drag to expand or collapse the console</string>
```

- [ ] **Step 4: 颜色选择器（4 个文件）**

`app/src/main/res/color/segment_background.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_checked="true" android:color="@color/primary_dark"/>
    <item android:color="@color/metal_mid"/>
</selector>
```

`app/src/main/res/color/segment_stroke.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_checked="true" android:color="@color/primary_light"/>
    <item android:color="@color/border_dim"/>
</selector>
```

`app/src/main/res/color/segment_text.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_checked="true" android:color="@color/primary_light"/>
    <item android:color="@color/text_steel"/>
</selector>
```

`app/src/main/res/color/roll_button_text.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false" android:color="#6E6858"/>
    <item android:color="#3A0D0D"/>
</selector>
```

- [ ] **Step 5: styles.xml — 拟物分段按钮样式**

创建 `app/src/main/res/values/styles.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="ConsoleSegmentButton" parent="Widget.MaterialComponents.Button.OutlinedButton">
        <item name="android:textColor">@color/segment_text</item>
        <item name="backgroundTint">@color/segment_background</item>
        <item name="strokeColor">@color/segment_stroke</item>
        <item name="strokeWidth">1dp</item>
        <item name="cornerRadius">4dp</item>
        <item name="rippleColor">@color/border_gold_strong</item>
        <item name="android:textSize">12sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:letterSpacing">0.08</item>
        <item name="android:minWidth">0dp</item>
        <item name="android:padding">0dp</item>
        <item name="android:insetTop">0dp</item>
        <item name="android:insetBottom">0dp</item>
    </style>
</resources>
```

- [ ] **Step 6: themes.xml — Edge-to-edge**

全文替换 `app/src/main/res/values/themes.xml`：

```xml
<resources>
    <style name="Theme.WH40KDice" parent="Theme.MaterialComponents.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryVariant">@color/primary_dark</item>
        <item name="colorOnPrimary">@color/background</item>
        <item name="colorSecondary">@color/accent</item>
        <item name="colorSecondaryVariant">@color/accent_dark</item>
        <item name="colorOnSecondary">@color/text_primary</item>
        <item name="android:colorBackground">@color/background</item>
        <item name="android:textColorPrimary">@color/text_primary</item>
        <item name="android:textColorSecondary">@color/text_secondary</item>
        <item name="android:windowBackground">@color/background</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
```

（真正的 insets 避让在 Task 7 代码中处理；本任务后状态栏暂时透明、内容可能伸到其下，属中间态。）

- [ ] **Step 7: 验证构建**

```bash
./gradlew assembleDebug
```

预期：BUILD SUCCESSFUL（新资源暂未被布局引用，仅校验编译）。

- [ ] **Step 8: Commit（可选）**

```bash
git add -A
git commit -m "feat: console visual foundation (colors, strings, Pirata One font, segment styles, edge-to-edge theme)"
```

---

### Task 5: 拟物 drawable 资源集

全部为 XML drawable，无位图。本任务只新增，不删除旧 drawable（旧文件在 Task 6 布局替换后删除）。

**Files:**
- Create: `app/src/main/res/drawable/bg_panel_metal.xml`
- Create: `app/src/main/res/drawable/bg_panel_inset.xml`
- Create: `app/src/main/res/drawable/bg_panel_handle.xml`
- Create: `app/src/main/res/drawable/bg_header_etched.xml`
- Create: `app/src/main/res/drawable/bg_segment_selected.xml`
- Create: `app/src/main/res/drawable/bg_segment_normal.xml`
- Create: `app/src/main/res/drawable/bg_gauge.xml`
- Create: `app/src/main/res/drawable/sel_roll_button.xml`
- Create: `app/src/main/res/drawable/bg_roll_button_brass.xml`
- Create: `app/src/main/res/drawable/bg_roll_button_brass_pressed.xml`
- Create: `app/src/main/res/drawable/bg_roll_button_disabled.xml`
- Create: `app/src/main/res/drawable/bg_button_metal.xml`
- Create: `app/src/main/res/drawable/seekbar_track.xml`
- Create: `app/src/main/res/drawable/thumb_brass.xml`
- Create: `app/src/main/res/drawable/lamp_green.xml`
- Create: `app/src/main/res/drawable/lamp_amber.xml`
- Create: `app/src/main/res/drawable/lamp_dim.xml`

**Interfaces:**
- Consumes: Task 4 的颜色资源
- Produces: 上述 drawable 名，Task 6 布局引用

- [ ] **Step 1: 控制台金属面板 bg_panel_metal.xml（含四角铆钉）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="12dp"/>
            <gradient
                android:angle="270"
                android:startColor="@color/metal_light"
                android:centerColor="@color/metal_mid"
                android:endColor="@color/metal_dark"/>
            <stroke android:width="1dp" android:color="@color/border_gold_strong"/>
        </shape>
    </item>
    <item android:width="7dp" android:height="7dp" android:gravity="top|start" android:left="9dp" android:top="9dp">
        <shape android:shape="oval">
            <gradient android:type="radial" android:gradientRadius="5dp"
                android:startColor="@color/rivet_highlight" android:endColor="@color/rivet"/>
        </shape>
    </item>
    <item android:width="7dp" android:height="7dp" android:gravity="top|end" android:right="9dp" android:top="9dp">
        <shape android:shape="oval">
            <gradient android:type="radial" android:gradientRadius="5dp"
                android:startColor="@color/rivet_highlight" android:endColor="@color/rivet"/>
        </shape>
    </item>
    <item android:width="7dp" android:height="7dp" android:gravity="bottom|start" android:left="9dp" android:bottom="9dp">
        <shape android:shape="oval">
            <gradient android:type="radial" android:gradientRadius="5dp"
                android:startColor="@color/rivet_highlight" android:endColor="@color/rivet"/>
        </shape>
    </item>
    <item android:width="7dp" android:height="7dp" android:gravity="bottom|end" android:right="9dp" android:bottom="9dp">
        <shape android:shape="oval">
            <gradient android:type="radial" android:gradientRadius="5dp"
                android:startColor="@color/rivet_highlight" android:endColor="@color/rivet"/>
        </shape>
    </item>
</layer-list>
```

- [ ] **Step 2: 内凹卡片 bg_panel_inset.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="8dp"/>
    <gradient android:angle="270" android:startColor="#100D09" android:endColor="#1C1814"/>
    <stroke android:width="1dp" android:color="#050403"/>
</shape>
```

- [ ] **Step 3: 面板手柄 bg_panel_handle.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="2.5dp"/>
    <gradient android:angle="270" android:startColor="@color/metal_light" android:endColor="@color/metal_dark"/>
    <stroke android:width="0.5dp" android:color="@color/border_gold"/>
</shape>
```

- [ ] **Step 4: 蚀刻标题栏 bg_header_etched.xml（渐变 + 底部金线）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <gradient android:angle="270" android:startColor="#F214110E" android:endColor="#0014110E"/>
        </shape>
    </item>
    <item android:gravity="bottom" android:height="1dp">
        <shape android:shape="rectangle">
            <solid android:color="@color/border_gold_strong"/>
        </shape>
    </item>
</layer-list>
```

- [ ] **Step 5: 掷骰类型分段 — bg_segment_selected.xml / bg_segment_normal.xml**

`bg_segment_selected.xml`（金色下沉内凹）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="6dp"/>
    <gradient android:angle="270" android:startColor="#2A230F" android:endColor="@color/primary_dark"/>
    <stroke android:width="1dp" android:color="@color/primary_light"/>
</shape>
```

`bg_segment_normal.xml`（拉丝金属）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="6dp"/>
    <gradient android:angle="270" android:startColor="@color/metal_light" android:endColor="@color/metal_dark"/>
    <stroke android:width="1dp" android:color="@color/border_dim"/>
</shape>
```

- [ ] **Step 6: 结果仪表窗 bg_gauge.xml（铆钉 + 凹槽）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="8dp"/>
            <gradient android:angle="270" android:startColor="@color/metal_mid" android:endColor="@color/metal_dark"/>
            <stroke android:width="1dp" android:color="@color/border_gold"/>
        </shape>
    </item>
    <item android:width="5dp" android:height="5dp" android:gravity="top|start" android:left="5dp" android:top="5dp">
        <shape android:shape="oval">
            <gradient android:type="radial" android:gradientRadius="4dp"
                android:startColor="@color/rivet_highlight" android:endColor="@color/rivet"/>
        </shape>
    </item>
    <item android:width="5dp" android:height="5dp" android:gravity="top|end" android:right="5dp" android:top="5dp">
        <shape android:shape="oval">
            <gradient android:type="radial" android:gradientRadius="4dp"
                android:startColor="@color/rivet_highlight" android:endColor="@color/rivet"/>
        </shape>
    </item>
    <item android:width="5dp" android:height="5dp" android:gravity="bottom|start" android:left="5dp" android:bottom="5dp">
        <shape android:shape="oval">
            <gradient android:type="radial" android:gradientRadius="4dp"
                android:startColor="@color/rivet_highlight" android:endColor="@color/rivet"/>
        </shape>
    </item>
    <item android:width="5dp" android:height="5dp" android:gravity="bottom|end" android:right="5dp" android:bottom="5dp">
        <shape android:shape="oval">
            <gradient android:type="radial" android:gradientRadius="4dp"
                android:startColor="@color/rivet_highlight" android:endColor="@color/rivet"/>
        </shape>
    </item>
    <item android:left="9dp" android:top="9dp" android:right="9dp" android:bottom="9dp">
        <shape android:shape="rectangle">
            <corners android:radius="5dp"/>
            <gradient android:angle="270" android:startColor="#0D0B08" android:endColor="#181410"/>
            <stroke android:width="1dp" android:color="#060503"/>
        </shape>
    </item>
</layer-list>
```

- [ ] **Step 7: 黄铜 ROLL 按钮（normal / pressed / disabled + selector）**

`bg_roll_button_brass.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:top="4dp">
        <shape android:shape="rectangle">
            <corners android:radius="10dp"/>
            <solid android:color="#66000000"/>
        </shape>
    </item>
    <item android:bottom="4dp">
        <layer-list>
            <item>
                <shape android:shape="rectangle">
                    <corners android:radius="10dp"/>
                    <gradient android:angle="270"
                        android:startColor="#F0D490"
                        android:centerColor="@color/primary"
                        android:endColor="#8A6A24"/>
                    <stroke android:width="1dp" android:color="#5C4A1A"/>
                </shape>
            </item>
            <item android:left="1.5dp" android:top="1.5dp" android:right="1.5dp" android:bottom="1.5dp">
                <shape android:shape="rectangle">
                    <corners android:radius="8.5dp"/>
                    <stroke android:width="1dp" android:color="#59FFFFFF"/>
                </shape>
            </item>
        </layer-list>
    </item>
</layer-list>
```

`bg_roll_button_brass_pressed.xml`（同结构，整体更深、阴影更浅）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:top="1dp">
        <shape android:shape="rectangle">
            <corners android:radius="10dp"/>
            <solid android:color="#66000000"/>
        </shape>
    </item>
    <item android:bottom="1dp">
        <shape android:shape="rectangle">
            <corners android:radius="10dp"/>
            <gradient android:angle="270"
                android:startColor="#D9BC6E"
                android:centerColor="#A8822F"
                android:endColor="#6E571C"/>
            <stroke android:width="1dp" android:color="#4A3C14"/>
        </shape>
    </item>
</layer-list>
```

`bg_roll_button_disabled.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="10dp"/>
    <gradient android:angle="270" android:startColor="@color/metal_mid" android:endColor="@color/metal_dark"/>
    <stroke android:width="1dp" android:color="@color/border_dim"/>
</shape>
```

`sel_roll_button.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false" android:drawable="@drawable/bg_roll_button_disabled"/>
    <item android:state_pressed="true" android:drawable="@drawable/bg_roll_button_brass_pressed"/>
    <item android:drawable="@drawable/bg_roll_button_brass"/>
</selector>
```

- [ ] **Step 8: REROLL/RESET 金属按钮 bg_button_metal.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <corners android:radius="8dp"/>
            <gradient android:angle="270" android:startColor="@color/metal_dark" android:endColor="@color/metal_mid"/>
            <stroke android:width="1dp" android:color="@color/border_gold_strong"/>
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="8dp"/>
            <gradient android:angle="270" android:startColor="@color/metal_light" android:endColor="@color/metal_mid"/>
            <stroke android:width="1dp" android:color="@color/border_gold"/>
        </shape>
    </item>
</selector>
```

- [ ] **Step 9: 黄铜滑杆 seekbar_track.xml + thumb_brass.xml**

`seekbar_track.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <corners android:radius="3dp"/>
            <gradient android:angle="270" android:startColor="#0C0A07" android:endColor="#17130E"/>
            <stroke android:width="1dp" android:color="#26211A"/>
            <size android:height="6dp"/>
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape android:shape="rectangle">
                <corners android:radius="3dp"/>
                <gradient android:angle="0" android:startColor="@color/primary_dark" android:endColor="@color/primary_light"/>
                <size android:height="6dp"/>
            </shape>
        </clip>
    </item>
</layer-list>
```

`thumb_brass.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <size android:width="22dp" android:height="22dp"/>
    <gradient android:type="radial" android:gradientRadius="11dp"
        android:startColor="#F4DCA0" android:centerColor="@color/primary" android:endColor="@color/primary_dark"/>
    <stroke android:width="1dp" android:color="#3A3013"/>
</shape>
```

- [ ] **Step 10: 状态灯 lamp_green / lamp_amber / lamp_dim**

`lamp_green.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:width="16dp" android:height="16dp" android:gravity="center">
        <shape android:shape="oval">
            <solid android:color="#553FA34D"/>
        </shape>
    </item>
    <item android:width="9dp" android:height="9dp" android:gravity="center">
        <shape android:shape="oval">
            <gradient android:type="radial" android:gradientRadius="5dp"
                android:startColor="#BFFFCB" android:centerColor="@color/lamp_green" android:endColor="#1E5A26"/>
            <stroke android:width="0.5dp" android:color="#0E2E12"/>
        </shape>
    </item>
</layer-list>
```

`lamp_amber.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:width="16dp" android:height="16dp" android:gravity="center">
        <shape android:shape="oval">
            <solid android:color="#55D98E2B"/>
        </shape>
    </item>
    <item android:width="9dp" android:height="9dp" android:gravity="center">
        <shape android:shape="oval">
            <gradient android:type="radial" android:gradientRadius="5dp"
                android:startColor="#FFE9C0" android:centerColor="@color/lamp_amber" android:endColor="#7A4E10"/>
            <stroke android:width="0.5dp" android:color="#3E280A"/>
        </shape>
    </item>
</layer-list>
```

`lamp_dim.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <gradient android:type="radial" android:gradientRadius="5dp"
        android:startColor="#6E6858" android:centerColor="@color/lamp_dim" android:endColor="#242019"/>
    <stroke android:width="0.5dp" android:color="#12100C"/>
</shape>
```

- [ ] **Step 11: 验证构建**

```bash
./gradlew assembleDebug
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 12: Commit（可选）**

```bash
git add -A
git commit -m "feat: skeuomorphic drawable set (metal panel, rivets, brass button, gauges, lamps, seekbar)"
```

---

### Task 6: 布局重构 — 底部控制台面板

重写 `activity_main.xml`：顶部蚀刻标题栏（含状态灯）+ 底部金属控制台面板（手柄 + ROLL 行 + 结果仪表 + 分段选择 + 控制卡）。**保留所有现有 view id**（MainActivity 引用不中断，本任务结束即可编译运行）。旧 drawable 删除。

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`（全文替换）
- Delete: `app/src/main/res/drawable/bg_roll_type_normal.xml`、`bg_roll_type_selected.xml`、`bg_summary_success.xml`、`bg_summary_critical.xml`、`bg_summary_failure.xml`、`bg_summary_total.xml`、`bg_card_section.xml`、`bg_roll_button.xml`、`bg_button_outline.xml`、`bg_edge_fade_bottom.xml`、`bg_header_gradient.xml`

**Interfaces:**
- Consumes: Task 4 资源（样式/字符串/字体/颜色选择器）、Task 5 drawable
- Produces: 新增 id `rootLayout`、`headerBar`、`statusLamp`、`consolePanel`、`handleZone`、`actionRow`、`panelContent`（Task 7 代码引用）；保留 id 见下。仪表窗内 `successCountText` 等四个 TextView 改为只显示数值（Task 7 代码改为直接设置数字）。

- [ ] **Step 1: 全文替换 activity_main.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/rootLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background">

    <!-- 3D Dice WebView (full screen stage) -->
    <WebView
        android:id="@+id/diceWebView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layerType="hardware" />

    <!-- Etched Header -->
    <FrameLayout
        android:id="@+id/headerBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="top"
        android:background="@drawable/bg_header_etched"
        android:paddingStart="20dp"
        android:paddingEnd="20dp"
        android:paddingTop="14dp"
        android:paddingBottom="12dp">

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:gravity="center"
            android:orientation="vertical">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:fontFamily="@font/pirata_one_regular"
                android:letterSpacing="0.2"
                android:text="@string/header_title"
                android:textColor="@color/primary"
                android:textSize="24sp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:letterSpacing="0.3"
                android:text="@string/header_subtitle"
                android:textColor="@color/text_steel"
                android:textSize="10sp"
                android:textStyle="bold" />
        </LinearLayout>

        <View
            android:id="@+id/statusLamp"
            android:layout_width="14dp"
            android:layout_height="14dp"
            android:layout_gravity="center_vertical|end"
            android:background="@drawable/lamp_dim"
            android:contentDescription="@string/status_lamp_desc" />
    </FrameLayout>

    <!-- Bottom Console Panel -->
    <LinearLayout
        android:id="@+id/consolePanel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:layout_marginStart="10dp"
        android:layout_marginEnd="10dp"
        android:background="@drawable/bg_panel_metal"
        android:elevation="12dp"
        android:orientation="vertical">

        <!-- Drag handle -->
        <FrameLayout
            android:id="@+id/handleZone"
            android:layout_width="match_parent"
            android:layout_height="30dp"
            android:contentDescription="@string/panel_handle_desc">

            <View
                android:id="@+id/panelHandle"
                android:layout_width="56dp"
                android:layout_height="5dp"
                android:layout_gravity="center"
                android:background="@drawable/bg_panel_handle" />
        </FrameLayout>

        <!-- Action row: ROLL -->
        <LinearLayout
            android:id="@+id/actionRow"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:paddingStart="16dp"
            android:paddingEnd="16dp"
            android:paddingBottom="10dp">

            <Button
                android:id="@+id/btnRoll"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="56dp"
                android:background="@drawable/sel_roll_button"
                android:fontFamily="@font/pirata_one_regular"
                android:letterSpacing="0.2"
                android:shadowColor="#73FFFFFF"
                android:shadowDy="-1"
                android:shadowRadius="1"
                android:text="@string/roll_button"
                android:textAllCaps="true"
                android:textColor="@color/roll_button_text"
                android:textSize="20sp" />
        </LinearLayout>

        <!-- Post Roll Buttons -->
        <LinearLayout
            android:id="@+id/postRollButtons"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:paddingStart="16dp"
            android:paddingEnd="16dp"
            android:paddingBottom="10dp"
            android:visibility="gone">

            <Button
                android:id="@+id/btnReroll"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="44dp"
                android:layout_marginEnd="6dp"
                android:background="@drawable/bg_button_metal"
                android:letterSpacing="0.1"
                android:text="@string/reroll_button"
                android:textColor="@color/primary_light"
                android:textSize="13sp"
                android:textStyle="bold" />

            <Button
                android:id="@+id/btnReset"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="44dp"
                android:layout_marginStart="6dp"
                android:background="@drawable/bg_button_metal"
                android:letterSpacing="0.1"
                android:text="@string/reset_button"
                android:textColor="@color/accent_light"
                android:textSize="13sp"
                android:textStyle="bold" />
        </LinearLayout>

        <!-- Collapsible content -->
        <androidx.core.widget.NestedScrollView
            android:id="@+id/panelContent"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:scrollbars="none">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:paddingStart="16dp"
                android:paddingEnd="16dp"
                android:paddingBottom="16dp">

                <!-- Results gauges -->
                <LinearLayout
                    android:id="@+id/summaryLayout"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:visibility="gone">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginBottom="6dp"
                        android:gravity="center"
                        android:letterSpacing="0.2"
                        android:text="@string/results_label"
                        android:textColor="@color/text_steel"
                        android:textSize="9sp"
                        android:textStyle="bold" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:weightSum="2">

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_weight="1"
                            android:layout_height="wrap_content"
                            android:layout_marginEnd="4dp"
                            android:background="@drawable/bg_gauge"
                            android:gravity="center"
                            android:orientation="vertical"
                            android:paddingTop="14dp"
                            android:paddingBottom="14dp">

                            <TextView
                                android:id="@+id/successCountText"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:fontFamily="monospace"
                                android:textColor="@color/success_light"
                                android:textSize="28sp"
                                android:textStyle="bold" />

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="2dp"
                                android:letterSpacing="0.15"
                                android:text="@string/successes_label"
                                android:textColor="@color/text_steel"
                                android:textSize="9sp"
                                android:textStyle="bold" />
                        </LinearLayout>

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_weight="1"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="4dp"
                            android:background="@drawable/bg_gauge"
                            android:gravity="center"
                            android:orientation="vertical"
                            android:paddingTop="14dp"
                            android:paddingBottom="14dp">

                            <TextView
                                android:id="@+id/criticalCountText"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:fontFamily="monospace"
                                android:textColor="@color/critical"
                                android:textSize="28sp"
                                android:textStyle="bold" />

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="2dp"
                                android:letterSpacing="0.15"
                                android:text="@string/criticals_label"
                                android:textColor="@color/text_steel"
                                android:textSize="9sp"
                                android:textStyle="bold" />
                        </LinearLayout>
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:orientation="horizontal"
                        android:weightSum="2">

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_weight="1"
                            android:layout_height="wrap_content"
                            android:layout_marginEnd="4dp"
                            android:background="@drawable/bg_gauge"
                            android:gravity="center"
                            android:orientation="vertical"
                            android:paddingTop="14dp"
                            android:paddingBottom="14dp">

                            <TextView
                                android:id="@+id/failureCountText"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:fontFamily="monospace"
                                android:textColor="@color/failure_light"
                                android:textSize="28sp"
                                android:textStyle="bold" />

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="2dp"
                                android:letterSpacing="0.15"
                                android:text="@string/failures_label"
                                android:textColor="@color/text_steel"
                                android:textSize="9sp"
                                android:textStyle="bold" />
                        </LinearLayout>

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_weight="1"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="4dp"
                            android:background="@drawable/bg_gauge"
                            android:gravity="center"
                            android:orientation="vertical"
                            android:paddingTop="14dp"
                            android:paddingBottom="14dp">

                            <TextView
                                android:id="@+id/totalText"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:fontFamily="monospace"
                                android:textColor="@color/primary_light"
                                android:textSize="28sp"
                                android:textStyle="bold" />

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="2dp"
                                android:letterSpacing="0.15"
                                android:text="@string/total_label"
                                android:textColor="@color/text_steel"
                                android:textSize="9sp"
                                android:textStyle="bold" />
                        </LinearLayout>
                    </LinearLayout>
                </LinearLayout>

                <!-- Roll Type Selector -->
                <LinearLayout
                    android:id="@+id/rollTypeContainer"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="10dp"
                    android:orientation="horizontal"
                    android:weightSum="4">

                    <TextView
                        android:id="@+id/rollTypeHit"
                        android:layout_width="0dp"
                        android:layout_weight="1"
                        android:layout_height="48dp"
                        android:layout_marginEnd="4dp"
                        android:background="@drawable/bg_segment_selected"
                        android:fontFamily="@font/pirata_one_regular"
                        android:foreground="?attr/selectableItemForeground"
                        android:gravity="center"
                        android:letterSpacing="0.08"
                        android:text="@string/roll_type_hit"
                        android:textColor="@color/primary_light"
                        android:textSize="14sp" />

                    <TextView
                        android:id="@+id/rollTypeWound"
                        android:layout_width="0dp"
                        android:layout_weight="1"
                        android:layout_height="48dp"
                        android:layout_marginStart="4dp"
                        android:layout_marginEnd="4dp"
                        android:background="@drawable/bg_segment_normal"
                        android:fontFamily="@font/pirata_one_regular"
                        android:foreground="?attr/selectableItemForeground"
                        android:gravity="center"
                        android:letterSpacing="0.08"
                        android:text="@string/roll_type_wound"
                        android:textColor="@color/text_steel"
                        android:textSize="14sp" />

                    <TextView
                        android:id="@+id/rollTypeSave"
                        android:layout_width="0dp"
                        android:layout_weight="1"
                        android:layout_height="48dp"
                        android:layout_marginStart="4dp"
                        android:layout_marginEnd="4dp"
                        android:background="@drawable/bg_segment_normal"
                        android:fontFamily="@font/pirata_one_regular"
                        android:foreground="?attr/selectableItemForeground"
                        android:gravity="center"
                        android:letterSpacing="0.08"
                        android:text="@string/roll_type_save"
                        android:textColor="@color/text_steel"
                        android:textSize="14sp" />

                    <TextView
                        android:id="@+id/rollTypeCustom"
                        android:layout_width="0dp"
                        android:layout_weight="1"
                        android:layout_height="48dp"
                        android:layout_marginStart="4dp"
                        android:background="@drawable/bg_segment_normal"
                        android:fontFamily="@font/pirata_one_regular"
                        android:foreground="?attr/selectableItemForeground"
                        android:gravity="center"
                        android:letterSpacing="0.08"
                        android:text="@string/roll_type_custom"
                        android:textColor="@color/text_steel"
                        android:textSize="14sp" />
                </LinearLayout>

                <!-- Controls inset card -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="10dp"
                    android:background="@drawable/bg_panel_inset"
                    android:orientation="vertical"
                    android:padding="14dp">

                    <!-- Dice Count -->
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:letterSpacing="0.15"
                        android:text="@string/dice_count_label"
                        android:textColor="@color/text_steel"
                        android:textSize="9sp"
                        android:textStyle="bold" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="6dp"
                        android:gravity="center_vertical"
                        android:orientation="horizontal">

                        <SeekBar
                            android:id="@+id/diceCountSeekbar"
                            android:layout_width="0dp"
                            android:layout_weight="1"
                            android:layout_height="wrap_content"
                            android:max="19"
                            android:progress="4"
                            android:progressDrawable="@drawable/seekbar_track"
                            android:thumb="@drawable/thumb_brass"
                            android:splitTrack="false" />

                        <TextView
                            android:id="@+id/diceCountText"
                            android:layout_width="48dp"
                            android:layout_height="wrap_content"
                            android:fontFamily="monospace"
                            android:gravity="center"
                            android:text="@string/dice_count_initial"
                            android:textColor="@color/primary_light"
                            android:textSize="26sp"
                            android:textStyle="bold" />
                    </LinearLayout>

                    <View
                        android:layout_width="match_parent"
                        android:layout_height="1.5dp"
                        android:layout_marginTop="12dp"
                        android:layout_marginBottom="12dp"
                        android:background="@drawable/bg_divider_gold" />

                    <!-- Dice Type + Target Row -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:weightSum="2">

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:layout_marginEnd="6dp"
                            android:orientation="vertical">

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginBottom="5dp"
                                android:letterSpacing="0.15"
                                android:text="@string/dice_type_label"
                                android:textColor="@color/text_steel"
                                android:textSize="9sp"
                                android:textStyle="bold" />

                            <com.google.android.material.button.MaterialButtonToggleGroup
                                android:id="@+id/diceTypeGroup"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                app:selectionRequired="true"
                                app:singleSelection="true">

                                <com.google.android.material.button.MaterialButton
                                    android:id="@+id/btnD6"
                                    style="@style/ConsoleSegmentButton"
                                    android:layout_width="0dp"
                                    android:layout_weight="1"
                                    android:layout_height="38dp"
                                    android:text="@string/dice_d6" />

                                <com.google.android.material.button.MaterialButton
                                    android:id="@+id/btnD3"
                                    style="@style/ConsoleSegmentButton"
                                    android:layout_width="0dp"
                                    android:layout_weight="1"
                                    android:layout_height="38dp"
                                    android:text="@string/dice_d3" />
                            </com.google.android.material.button.MaterialButtonToggleGroup>
                        </LinearLayout>

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:layout_marginStart="6dp"
                            android:orientation="vertical">

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginBottom="5dp"
                                android:letterSpacing="0.15"
                                android:text="@string/target_label"
                                android:textColor="@color/text_steel"
                                android:textSize="9sp"
                                android:textStyle="bold" />

                            <com.google.android.material.button.MaterialButtonToggleGroup
                                android:id="@+id/targetGroup"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                app:selectionRequired="true"
                                app:singleSelection="true">

                                <com.google.android.material.button.MaterialButton
                                    android:id="@+id/btnTarget2"
                                    style="@style/ConsoleSegmentButton"
                                    android:layout_width="0dp"
                                    android:layout_weight="1"
                                    android:layout_height="38dp"
                                    android:text="@string/target_2" />

                                <com.google.android.material.button.MaterialButton
                                    android:id="@+id/btnTarget3"
                                    style="@style/ConsoleSegmentButton"
                                    android:layout_width="0dp"
                                    android:layout_weight="1"
                                    android:layout_height="38dp"
                                    android:text="@string/target_3" />

                                <com.google.android.material.button.MaterialButton
                                    android:id="@+id/btnTarget4"
                                    style="@style/ConsoleSegmentButton"
                                    android:layout_width="0dp"
                                    android:layout_weight="1"
                                    android:layout_height="38dp"
                                    android:text="@string/target_4" />

                                <com.google.android.material.button.MaterialButton
                                    android:id="@+id/btnTarget5"
                                    style="@style/ConsoleSegmentButton"
                                    android:layout_width="0dp"
                                    android:layout_weight="1"
                                    android:layout_height="38dp"
                                    android:text="@string/target_5" />

                                <com.google.android.material.button.MaterialButton
                                    android:id="@+id/btnTarget6"
                                    style="@style/ConsoleSegmentButton"
                                    android:layout_width="0dp"
                                    android:layout_weight="1"
                                    android:layout_height="38dp"
                                    android:text="@string/target_6" />
                            </com.google.android.material.button.MaterialButtonToggleGroup>
                        </LinearLayout>
                    </LinearLayout>

                    <View
                        android:layout_width="match_parent"
                        android:layout_height="1.5dp"
                        android:layout_marginTop="12dp"
                        android:layout_marginBottom="12dp"
                        android:background="@drawable/bg_divider_gold" />

                    <!-- Reroll Mode -->
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginBottom="5dp"
                        android:letterSpacing="0.15"
                        android:text="@string/reroll_label"
                        android:textColor="@color/text_steel"
                        android:textSize="9sp"
                        android:textStyle="bold" />

                    <com.google.android.material.button.MaterialButtonToggleGroup
                        android:id="@+id/rerollGroup"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        app:selectionRequired="true"
                        app:singleSelection="true">

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnRerollNone"
                            style="@style/ConsoleSegmentButton"
                            android:layout_width="0dp"
                            android:layout_weight="1"
                            android:layout_height="38dp"
                            android:text="@string/reroll_none" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnReroll1s"
                            style="@style/ConsoleSegmentButton"
                            android:layout_width="0dp"
                            android:layout_weight="1"
                            android:layout_height="38dp"
                            android:text="@string/reroll_ones" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnRerollFail"
                            style="@style/ConsoleSegmentButton"
                            android:layout_width="0dp"
                            android:layout_weight="1"
                            android:layout_height="38dp"
                            android:text="@string/reroll_failures" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnRerollAll"
                            style="@style/ConsoleSegmentButton"
                            android:layout_width="0dp"
                            android:layout_weight="1"
                            android:layout_height="38dp"
                            android:text="@string/reroll_all" />
                    </com.google.android.material.button.MaterialButtonToggleGroup>
                </LinearLayout>
            </LinearLayout>
        </androidx.core.widget.NestedScrollView>
    </LinearLayout>

</FrameLayout>
```

- [ ] **Step 2: 删除被替换的旧 drawable**

```bash
rm app/src/main/res/drawable/bg_roll_type_normal.xml \
   app/src/main/res/drawable/bg_roll_type_selected.xml \
   app/src/main/res/drawable/bg_summary_success.xml \
   app/src/main/res/drawable/bg_summary_critical.xml \
   app/src/main/res/drawable/bg_summary_failure.xml \
   app/src/main/res/drawable/bg_summary_total.xml \
   app/src/main/res/drawable/bg_card_section.xml \
   app/src/main/res/drawable/bg_roll_button.xml \
   app/src/main/res/drawable/bg_button_outline.xml \
   app/src/main/res/drawable/bg_edge_fade_bottom.xml \
   app/src/main/res/drawable/bg_header_gradient.xml
```

- [ ] **Step 3: 修正 MainActivity 中对新资源的引用**

`updateRollTypeUI()` 中旧 drawable 引用改为新名：

```kotlin
                view.setBackgroundResource(R.drawable.bg_segment_selected)
```

与

```kotlin
                view.setBackgroundResource(R.drawable.bg_segment_normal)
```

未选中文字颜色由 `R.color.text_secondary` 改为 `R.color.text_steel`（与新布局一致）。

- [ ] **Step 4: 验证构建**

```bash
./gradlew assembleDebug
```

预期：BUILD SUCCESSFUL（`successes_format` 等旧字符串仍被 MainActivity 使用，暂保留）。

- [ ] **Step 5: Commit（可选）**

```bash
git add -A
git commit -m "feat: rebuild layout as bottom console panel with gauges and etched header"
```

---

### Task 7: MainActivity 交互接线 — insets、面板拖拽、状态灯、动画

**Files:**
- Modify: `app/src/main/java/com/example/helloworld/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`（删除 4 个 `*_format` 与 `no_results`）

**Interfaces:**
- Consumes: Task 6 新 id（`rootLayout/headerBar/statusLamp/consolePanel/handleZone/panelContent`）、Task 4 的 `lamp_*` drawable（Task 5）、`RerollMode` 等
- Produces: 无对外接口（终端任务）

- [ ] **Step 1: 新增 import**

```kotlin
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
```

- [ ] **Step 2: 新增字段**

```kotlin
    private lateinit var rootLayout: View
    private lateinit var headerBar: View
    private lateinit var statusLamp: View
    private lateinit var consolePanel: View
    private lateinit var handleZone: View
    private lateinit var panelContent: View

    private var lampBreathing: ObjectAnimator? = null
    private var panelCollapsedOffset = 0f
    private var statusBarInset = 0

    private var lastSuccesses = 0
    private var lastFailures = 0
    private var lastCriticals = 0
    private var lastTotal = 0
```

并在 `bindViews()` 末尾追加绑定：

```kotlin
        rootLayout = findViewById(R.id.rootLayout)
        headerBar = findViewById(R.id.headerBar)
        statusLamp = findViewById(R.id.statusLamp)
        consolePanel = findViewById(R.id.consolePanel)
        handleZone = findViewById(R.id.handleZone)
        panelContent = findViewById(R.id.panelContent)
```

- [ ] **Step 3: Edge-to-edge + insets**

在 `onCreate()` 的 `setContentView` 之前调用：

```kotlin
        WindowCompat.setDecorFitsSystemWindows(window, false)
```

新增方法并在 `onCreate()` 中 `bindViews()` 之后调用 `setupInsets()`：

```kotlin
    private fun setupInsets() {
        val baseHeaderPadding = (14 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarInset = bars.top
            headerBar.setPadding(
                headerBar.paddingLeft,
                baseHeaderPadding + bars.top,
                headerBar.paddingRight,
                headerBar.paddingBottom
            )
            consolePanel.setPadding(0, 0, 0, 0)
            (consolePanel.layoutParams as? android.widget.FrameLayout.LayoutParams)?.bottomMargin = bars.bottom
            insets
        }
    }
```

- [ ] **Step 4: 面板拖拽**

`onCreate()` 中调用 `setupPanelDrag()`。新增方法：

```kotlin
    private fun setupPanelDrag() {
        consolePanel.post {
            panelCollapsedOffset = panelContent.height.toFloat()
            // 限制展开高度，避免超过标题栏
            val maxContentHeight = rootLayout.height - headerBar.height - statusBarInset -
                handleZone.height - (120 * resources.displayMetrics.density).toInt()
            if (panelContent.height > maxContentHeight && maxContentHeight > 0) {
                panelContent.layoutParams.height = maxContentHeight
                panelContent.requestLayout()
                panelCollapsedOffset = maxContentHeight.toFloat()
            }
        }

        var downRawY = 0f
        var startTranslation = 0f
        var moved = false

        handleZone.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = event.rawY
                    startTranslation = consolePanel.translationY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downRawY
                    if (kotlin.math.abs(dy) > 8f) moved = true
                    consolePanel.translationY =
                        (startTranslation + dy).coerceIn(0f, panelCollapsedOffset)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        togglePanel()
                    } else {
                        val collapse = consolePanel.translationY > panelCollapsedOffset / 2
                        animatePanel(collapse)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun togglePanel() {
        animatePanel(collapse = consolePanel.translationY < panelCollapsedOffset / 2)
    }

    private fun animatePanel(collapse: Boolean) {
        val target = if (collapse) panelCollapsedOffset else 0f
        consolePanel.animate().translationY(target).setDuration(220).start()
    }
```

- [ ] **Step 5: 状态灯**

新增方法：

```kotlin
    private enum class LampState { DIM, READY, ROLLING }

    private fun setLamp(state: LampState) {
        lampBreathing?.cancel()
        lampBreathing = null
        when (state) {
            LampState.DIM -> {
                statusLamp.alpha = 1f
                statusLamp.setBackgroundResource(R.drawable.lamp_dim)
            }
            LampState.READY -> {
                statusLamp.setBackgroundResource(R.drawable.lamp_green)
                lampBreathing = ObjectAnimator.ofFloat(statusLamp, View.ALPHA, 1f, 0.35f).apply {
                    duration = 1200
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
            LampState.ROLLING -> {
                statusLamp.setBackgroundResource(R.drawable.lamp_amber)
                lampBreathing = ObjectAnimator.ofFloat(statusLamp, View.ALPHA, 1f, 0.5f).apply {
                    duration = 400
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
        }
    }
```

接线：
- `setupDefaults()` 末尾：`setLamp(LampState.DIM)` 和 `btnRoll.isEnabled = false`
- `onDiceReady()` 末尾：`btnRoll.isEnabled = true; setLamp(LampState.READY)`
- `performRoll()` 中 `evaluateJavascript` 之后：`setLamp(LampState.ROLLING)`
- `onRollComplete()` 开头：`setLamp(LampState.READY)`

- [ ] **Step 6: ROLL 按压动画**

`setupListeners()` 中追加：

```kotlin
        btnRoll.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
```

（返回 false，保留原有 click 监听。）

- [ ] **Step 7: 统计数值滚动动画 + 改为纯数字**

新增方法：

```kotlin
    private fun animateStat(view: TextView, from: Int, to: Int) {
        if (from == to) {
            view.text = to.toString()
            return
        }
        ValueAnimator.ofInt(from, to).apply {
            duration = 400
            addUpdateListener { view.text = (it.animatedValue as Int).toString() }
            start()
        }
    }
```

`onRollComplete()` 中原四处 `getString(R.string.xxx_format, ...)` 赋值替换为：

```kotlin
        animateStat(successCountText, lastSuccesses, successes)
        animateStat(failureCountText, lastFailures, failures)
        animateStat(totalText, lastTotal, total)
        animateStat(criticalCountText, lastCriticals, criticals)

        lastSuccesses = successes
        lastFailures = failures
        lastTotal = total
        lastCriticals = criticals
```

`resetResults()` 末尾追加：

```kotlin
        lastSuccesses = 0
        lastFailures = 0
        lastCriticals = 0
        lastTotal = 0
```

- [ ] **Step 8: 删除废弃字符串**

`strings.xml` 中删除 `successes_format`、`failures_format`、`total_format`、`criticals_format`、`no_results` 五行。

- [ ] **Step 9: 验证构建 + 全部测试**

```bash
./gradlew assembleDebug && ./gradlew :app:testDebugUnitTest
```

预期：BUILD SUCCESSFUL，9 个测试 PASS。

- [ ] **Step 10: Commit（可选）**

```bash
git add -A
git commit -m "feat: wire console interactions (insets, drag panel, status lamp, animations)"
```

---

### Task 8: 最终验证

**Files:** 无（只读验证）

- [ ] **Step 1: 全量构建 + 测试**

```bash
./gradlew clean assembleDebug && ./gradlew :app:testDebugUnitTest
```

预期：BUILD SUCCESSFUL，9 个测试 PASS，无新增 warning。

- [ ] **Step 2: 手动验证清单（需要连接设备/模拟器，如环境允许则执行 `./gradlew installDebug`）**

- 应用冷启动：状态灯灰色，ROLL 禁用；3D 引擎就绪后灯变绿呼吸、ROLL 可用
- ROLL：黄铜按钮按压下沉 + 震动；掷骰中灯变琥珀；结束后数字滚动更新、仪表窗出现
- 重投：选 FAIL 模式重投后，TOTAL 保持等于骰子总数（bug 修复验证）
- RESET：骰子清空、统计隐藏、计数归零
- 面板：拖拽手柄可收起/展开，轻点手柄切换；收起时仅见手柄 + ROLL 行
- 系统栏：内容不遮挡状态栏/导航栏
- 旋转屏幕：无崩溃（configChanges 已处理）
- 启动器图标：显示 adaptive icon

- [ ] **Step 3: Commit（可选）**

```bash
git add -A
git commit -m "chore: final verification for console beautification"
```
