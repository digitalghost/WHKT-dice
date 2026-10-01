package com.example.helloworld

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private val referenceUi by lazy { RecordUi(this) }

    private data class UndoState(
        val mode: RollMode,
        val config: RollConfig,
        val values: List<Int>,
        val historyId: Long?,
        val diceTheme: DiceTheme
    )

    private lateinit var rootLayout: View
    private lateinit var mainContent: View
    private lateinit var headerBar: View
    private lateinit var diceStage: View
    private lateinit var consolePanel: View
    private lateinit var weaponInfoPanel: LinearLayout
    private lateinit var weaponInfoTitle: TextView
    private lateinit var weaponInfoText: TextView
    private lateinit var diceControlCell: View
    private lateinit var thresholdControlCell: View
    private lateinit var attackTrayPage: View
    private lateinit var defenceTrayPage: View
    private lateinit var attackDiceTray: DiceTrayView
    private lateinit var defenceDiceTray: DiceTrayView
    private lateinit var coverRetainedBadge: View
    private lateinit var statusLamp: View
    private lateinit var statusText: TextView
    private lateinit var diceCountLabel: TextView
    private lateinit var diceCountText: TextView
    private lateinit var thresholdLabel: TextView
    private lateinit var thresholdText: TextView
    private lateinit var criticalText: TextView
    private lateinit var btnDiceMinus: View
    private lateinit var btnDicePlus: View
    private lateinit var btnThresholdMinus: View
    private lateinit var btnThresholdPlus: View
    private lateinit var btnCritical: View
    private lateinit var btnRoll: TextView
    private lateinit var btnReroll: TextView
    private lateinit var btnReset: TextView
    private lateinit var postRollButtons: View
    private lateinit var resolutionPanel: View
    private lateinit var resolutionText: TextView
    private lateinit var summaryLayout: View
    private lateinit var criticalCountText: TextView
    private lateinit var normalCountText: TextView
    private lateinit var failureCountText: TextView
    private var defenceSummaryLayout: View? = null
    private var defenceCriticalCountText: TextView? = null
    private var defenceNormalCountText: TextView? = null
    private var defenceFailureCountText: TextView? = null
    private lateinit var btnHistory: ImageButton
    private lateinit var gestureCoach: TextView
    private lateinit var gestureHintText: TextView
    private lateinit var btnRulesHelp: ImageButton
    private lateinit var rulesHelpOverlay: View
    private lateinit var rulesHelpPanel: View
    private lateinit var rulesHelpContent: TextView
    private var helpWeaponName: String? = null
    private var helpWeaponKeywords: List<String> = emptyList()
    private lateinit var btnCloseRulesHelp: View
    private lateinit var btnDiceTheme: ImageButton
 private lateinit var historyEdgeHandle: View
 private lateinit var historyScrim: View
    private lateinit var historyPanel: View
    private lateinit var historyHeader: View
    private lateinit var historyList: LinearLayout
    private lateinit var btnCloseHistory: View
    private lateinit var btnClearHistory: TextView
    private lateinit var historyStore: RollHistoryStore
    private lateinit var diceSoundPlayer: DiceSoundPlayer

    private val configs = mutableMapOf(
        RollMode.ATTACK to RollConfig(diceCount = 5, threshold = 3),
        RollMode.DEFENCE to RollConfig(diceCount = 3, threshold = 3)
    )

    private var currentMode = RollMode.ATTACK
    private var visibleTrayMode = RollMode.ATTACK
    private var diceReady = true
    private val valuesByMode = mutableMapOf(
        RollMode.ATTACK to emptyList<Int>(),
        RollMode.DEFENCE to emptyList()
    )
    private val stagesByMode = mutableMapOf(
        RollMode.ATTACK to mutableListOf<List<Int>>(),
        RollMode.DEFENCE to mutableListOf()
    )
    private var pendingRerollIndices: List<Int>? = null
    private var activeHistoryId: Long? = null
    private var lampAnimator: ObjectAnimator? = null
    private var historyClearArmedUntil = 0L
    private var currentDiceTheme = DiceTheme.ANGELS_OF_DEATH
    private var attackConfirmed = false
    private var encounterComplete = false
    private var defenceHasCover = false
    private var rollInProgress = false
    private var rulesHelpExpanded = false
    private var loadedWeaponHint: String? = null

    private val config: RollConfig
        get() = configs.getValue(currentMode)

    private var currentValues: List<Int>
        get() = valuesByMode.getValue(currentMode)
        set(value) {
            valuesByMode[currentMode] = value
        }

    private val diceTrayView: DiceTrayView
        get() = if (currentMode == RollMode.ATTACK) attackDiceTray else defenceDiceTray

    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersiveMode()
        setContentView(R.layout.activity_main)
        historyStore = RollHistoryStore(this)
        diceSoundPlayer = DiceSoundPlayer(this)
        bindViews()
        buildWeaponInfoPanel()
        restoreDiceTheme()
        applyConsoleSkin()
        setupVisibleNavigation()
        setupListeners()
        renderConfig()
        renderIdleState()
 applyWeaponIntent(intent)
        setupBackNavigation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyWeaponIntent(intent)
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        mainContent = findViewById(R.id.mainContent)
        headerBar = findViewById(R.id.headerBar)
        diceStage = findViewById(R.id.diceStage)
        consolePanel = findViewById(R.id.consolePanel)
        diceControlCell = findViewById(R.id.diceControlCell)
        thresholdControlCell = findViewById(R.id.thresholdControlCell)
        attackTrayPage = findViewById(R.id.attackTrayPage)
        defenceTrayPage = findViewById(R.id.defenceTrayPage)
        attackDiceTray = findViewById(R.id.attackDiceTray)
        defenceDiceTray = findViewById(R.id.defenceDiceTray)
        coverRetainedBadge = findViewById(R.id.coverRetainedBadge)
        statusLamp = findViewById(R.id.statusLamp)
        statusText = findViewById(R.id.statusText)
        diceCountLabel = findViewById(R.id.diceCountLabel)
        diceCountText = findViewById(R.id.diceCountText)
        thresholdLabel = findViewById(R.id.thresholdLabel)
        thresholdText = findViewById(R.id.thresholdText)
        criticalText = findViewById(R.id.criticalText)
        btnDiceMinus = findViewById(R.id.btnDiceMinus)
        btnDicePlus = findViewById(R.id.btnDicePlus)
        btnThresholdMinus = findViewById(R.id.btnThresholdMinus)
        btnThresholdPlus = findViewById(R.id.btnThresholdPlus)
        btnCritical = criticalText
        btnRoll = findViewById(R.id.btnRoll)
        btnReroll = findViewById(R.id.btnReroll)
        btnReset = findViewById(R.id.btnReset)
        postRollButtons = findViewById(R.id.postRollButtons)
        resolutionPanel = findViewById(R.id.resolutionPanel)
        resolutionText = findViewById(R.id.resolutionText)
        summaryLayout = findViewById(R.id.summaryLayout)
        criticalCountText = findViewById(R.id.criticalCountText)
        normalCountText = findViewById(R.id.normalCountText)
        failureCountText = findViewById(R.id.failureCountText)
        defenceSummaryLayout = findViewById(R.id.defenceSummaryLayout)
        defenceCriticalCountText = findViewById(R.id.defenceCriticalCountText)
        defenceNormalCountText = findViewById(R.id.defenceNormalCountText)
        defenceFailureCountText = findViewById(R.id.defenceFailureCountText)
        btnHistory = findViewById(R.id.btnHistory)
        gestureCoach = findViewById(R.id.gestureCoach)
        gestureHintText = findViewById(R.id.gestureHintText)
        btnRulesHelp = findViewById(R.id.btnRulesHelp)
        rulesHelpOverlay = findViewById(R.id.rulesHelpOverlay)
        rulesHelpPanel = findViewById(R.id.rulesHelpPanel)
        rulesHelpContent = findViewById(R.id.rulesHelpContent)
        btnCloseRulesHelp = findViewById(R.id.btnCloseRulesHelp)
        rulesHelpContent.movementMethod = ScrollingMovementMethod.getInstance()
        formatRulesHelpContent()
        btnDiceTheme = findViewById(R.id.btnDiceTheme)
 historyEdgeHandle = findViewById(R.id.historyEdgeHandle)
 historyScrim = findViewById(R.id.historyScrim)
        historyPanel = findViewById(R.id.historyPanel)
        historyHeader = findViewById(R.id.historyHeader)
        historyList = findViewById(R.id.historyList)
        btnCloseHistory = findViewById(R.id.btnCloseHistory)
        btnClearHistory = findViewById(R.id.btnClearHistory)
    }

    private fun applyConsoleSkin() {
        rootLayout.background = GrimdarkDrawable(this, ConsoleSurface.ROOT)
        headerBar.background = GrimdarkDrawable(this, ConsoleSurface.HEADER)
        diceStage.background = GrimdarkDrawable(this, ConsoleSurface.FRAME)
        consolePanel.background = GrimdarkDrawable(this, ConsoleSurface.PANEL)
        historyPanel.background = GrimdarkDrawable(this, ConsoleSurface.PANEL)
        rulesHelpPanel.background = GrimdarkDrawable(this, ConsoleSurface.PANEL)
        diceControlCell.background = GrimdarkSkins.button(this, ConsoleSurface.CELL)
        thresholdControlCell.background = GrimdarkSkins.button(this, ConsoleSurface.CELL)
        btnCritical.background = GrimdarkSkins.button(this, ConsoleSurface.CELL)
        if (!isTablet) {
            summaryLayout.background = GrimdarkDrawable(this, ConsoleSurface.INSET)
        }
        btnRoll.background = GrimdarkSkins.button(
            this,
            ConsoleSurface.GREEN,
            ConsoleSurface.GREEN_PRESSED
        )
        btnReroll.background = GrimdarkSkins.button(this, ConsoleSurface.CELL)
        btnReset.background = GrimdarkSkins.button(this, ConsoleSurface.CELL)
    }

    private fun buildWeaponInfoPanel() {
        weaponInfoTitle = TextView(this).apply {
            setTextColor(getColor(R.color.primary_light))
            textSize = 13f
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.teko_variable)
        }
        weaponInfoText = TextView(this).apply {
            setTextColor(getColor(R.color.text_primary))
            textSize = 12f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        weaponInfoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GrimdarkDrawable(this@MainActivity, ConsoleSurface.INSET)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
            addView(weaponInfoTitle)
            addView(weaponInfoText)
        }
        (mainContent as LinearLayout).addView(
            weaponInfoPanel,
            1,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(8), dp(6), dp(8), 0)
            }
        )
    }

    private fun enterImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun setupListeners() {
        btnDiceMinus.setOnClickListener { adjustDice(-1, it) }
        btnDicePlus.setOnClickListener { adjustDice(1, it) }
        btnThresholdMinus.setOnClickListener { adjustThreshold(-1, it) }
        btnThresholdPlus.setOnClickListener { adjustThreshold(1, it) }
        btnCritical.setOnClickListener {
            haptic(it)
            if (currentMode == RollMode.DEFENCE) {
                if (encounterComplete || currentValues.isNotEmpty()) return@setOnClickListener
                defenceHasCover = !defenceHasCover
                coverRetainedBadge.visibility = if (defenceHasCover) View.VISIBLE else View.GONE
            } else {
                if (attackConfirmed) return@setOnClickListener
                config.criticalThreshold = when (config.criticalThreshold) {
                    6 -> 5
                    5 -> 4
                    else -> 6
                }
            }
            invalidateRoll()
        }
        setupTrayListeners(RollMode.ATTACK, attackDiceTray)
        setupTrayListeners(RollMode.DEFENCE, defenceDiceTray)
        btnRoll.setOnClickListener {
            haptic(it)
            handlePrimaryAction()
        }
        btnReroll.setOnClickListener {
            haptic(it)
            performReroll()
        }
        btnReset.setOnClickListener {
            haptic(it)
            clearRollWithUndo()
        }
        btnDiceTheme.setOnClickListener {
            haptic(it)
            showDiceThemeChooser()
        }
        btnRulesHelp.setOnClickListener {
            haptic(it)
            setRulesHelpExpanded(!rulesHelpExpanded)
        }
        btnCloseRulesHelp.setOnClickListener {
            haptic(it)
            setRulesHelpExpanded(false)
        }
        rulesHelpOverlay.setOnClickListener { setRulesHelpExpanded(false) }
        rulesHelpPanel.setOnClickListener { /* Consume taps inside the floating panel. */ }
 btnHistory.setOnClickListener {
 haptic(it)
 openHistory()
 }
 historyScrim.setOnClickListener { closeHistory() }
        btnCloseHistory.setOnClickListener { closeHistory() }
        btnClearHistory.setOnClickListener { confirmHistoryClear() }
        setupHistoryGestures()
        listOf(
            btnRoll,
            btnReroll,
            btnRulesHelp,
            btnCloseRulesHelp,
            btnDiceTheme,
            btnHistory
        )
            .forEach(::addPressAnimation)
    }

    private fun setupTrayListeners(mode: RollMode, tray: DiceTrayView) {
        tray.setOnSelectionChangedListener { selectedCount ->
            if (currentMode == mode && visibleTrayMode == mode) {
                updateRerollControl(selectedCount)
            }
        }
        tray.setOnTrayGestureListener(
            onThrow = {
                if (rollInProgress) return@setOnTrayGestureListener
                if (mode != currentMode) {
                    Snackbar.make(rootLayout, R.string.read_only_tray, Snackbar.LENGTH_SHORT).show()
                } else if (currentValues.isNotEmpty() && tray.selectedIndices().isNotEmpty()) {
                    performReroll()
                } else if (currentValues.isEmpty()) {
                    performRoll()
                }
            },
            onReset = {
                if (mode == currentMode) clearRollWithUndo()
            }
        )
        tray.setOnHorizontalSwipeListener { direction ->
            if (isTablet || !attackConfirmed || rollInProgress) {
                return@setOnHorizontalSwipeListener
            }
            val target = if (direction > 0) RollMode.DEFENCE else RollMode.ATTACK
            showTrayPage(visibleTrayMode, target)
            renderConfig()
            renderCurrentRollState()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    historyPanel.visibility == View.VISIBLE -> closeHistory()
                    rulesHelpExpanded -> setRulesHelpExpanded(false)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun setupVisibleNavigation() {
        // The dice screen is immersive, so the system back gesture is not a
        // discoverable navigation affordance. Keep a labelled exit on screen.
        val battleId = intent.getStringExtra(BattleActivity.EXTRA_BATTLE_ID)
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = GrimdarkDrawable(this@MainActivity, ConsoleSurface.HEADER)
        }
        fun navigationButton(title: String, click: () -> Unit) = TextView(this).apply {
            text = title
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(ResourcesCompat.getColor(resources, R.color.primary_light, theme))
            setPadding(dp(14), 0, dp(14), 0)
            background = GrimdarkSkins.button(this@MainActivity, ConsoleSurface.CELL)
            setOnClickListener { click() }
        }
        bar.addView(navigationButton(if (battleId == null) "‹ 返回" else "‹ 返回对局") {
            if (battleId == null) finish() else {
                startActivity(Intent(this, BattleActivity::class.java).apply {
                    putExtra(BattleActivity.EXTRA_BATTLE_ID, battleId)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                finish()
            }
        }, LinearLayout.LayoutParams(-2, dp(48)))
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        findViewById<LinearLayout>(R.id.mainContent).addView(bar, 0, LinearLayout.LayoutParams(-1, dp(56)))
    }

    private fun setRulesHelpExpanded(expanded: Boolean) {
        rulesHelpExpanded = expanded
        rulesHelpOverlay.visibility = if (expanded) View.VISIBLE else View.GONE
        if (expanded) {
            formatRulesHelpContent()
            if (historyPanel.visibility == View.VISIBLE) closeHistory()
            rulesHelpOverlay.bringToFront()
            rulesHelpContent.post { rulesHelpContent.scrollTo(0, 0) }
        }
    }

    private fun formatRulesHelpContent() {
        val lines = getString(R.string.rules_help_content)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val formatted = SpannableStringBuilder()
        val keywordColor = ResourcesCompat.getColor(resources, R.color.primary_light, theme)
        val badgeTextColor = ResourcesCompat.getColor(resources, R.color.text_primary, theme)
        val badgeBackground = ResourcesCompat.getColor(resources, R.color.accent_dark, theme)

        helpWeaponName?.let { name ->
            val start = formatted.length
            formatted.append("当前武器 · ").append(name).append('\n')
            formatted.setSpan(StyleSpan(Typeface.BOLD), start, formatted.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            formatted.setSpan(ForegroundColorSpan(keywordColor), start, formatted.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (helpWeaponKeywords.isEmpty()) formatted.append("此武器没有特殊关键词。\n")
            helpWeaponKeywords.distinct().forEach { keyword ->
                val from = formatted.length
                formatted.append("◆ ").append(keyword).append("\n")
                formatted.setSpan(StyleSpan(Typeface.BOLD), from, formatted.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                formatted.setSpan(ForegroundColorSpan(keywordColor), from, formatted.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                formatted.append(WeaponRuleFocus.definition(keyword)?.description ?: "暂无此关键词说明，请查看小队原卡。").append("\n\n")
            }
            formatted.append("全部规则 · ◆ 为当前武器相关条目\n\n")
        }

        lines.forEachIndexed { index, line ->
            val closeBracket = line.indexOf('】')
            val isRule = line.startsWith("【") && closeBracket > 1
            val label = if (isRule) line.substring(1, closeBracket) else line.substringBefore('：')
            val description = if (isRule) {
                line.substring(closeBracket + 1).trim()
            } else {
                line.substringAfter('：', missingDelimiterValue = "").trim()
            }

            val lineStart = formatted.length
            val focused = helpWeaponName != null && WeaponRuleFocus.matches(label, helpWeaponKeywords)
            if (focused) formatted.append("◆ ")
            val badgeStart = formatted.length
            formatted.append(' ').append(rulePinyinInitial(label)).append(' ')
            val badgeEnd = formatted.length
            formatted.append("  ")
            val labelStart = formatted.length
            formatted.append(label)
            val labelEnd = formatted.length
            if (description.isNotEmpty()) {
                formatted.append("：").append(description)
            }
            val lineEnd = formatted.length
            if (focused) {
                formatted.setSpan(BackgroundColorSpan(0xff43251a.toInt()), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                formatted.setSpan(ForegroundColorSpan(0xffffe1cb.toInt()), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            formatted.setSpan(
                ForegroundColorSpan(badgeTextColor),
                badgeStart,
                badgeEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            formatted.setSpan(
                BackgroundColorSpan(badgeBackground),
                badgeStart,
                badgeEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            formatted.setSpan(
                StyleSpan(Typeface.BOLD),
                badgeStart,
                badgeEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            formatted.setSpan(
                ForegroundColorSpan(keywordColor),
                labelStart,
                labelEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            formatted.setSpan(
                StyleSpan(Typeface.BOLD),
                labelStart,
                labelEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            formatted.setSpan(
                RelativeSizeSpan(1.04f),
                labelStart,
                labelEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            formatted.setSpan(
                LeadingMarginSpan.Standard(0, dp(18)),
                lineStart,
                lineEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (index != lines.lastIndex) formatted.append('\n')
        }

        rulesHelpContent.text = formatted
    }

    private fun rulePinyinInitial(label: String): String {
        return when (label.substringBefore(' ').substringBefore('／')) {
            "通则" -> "T"
            "安静" -> "A"
            "爆炸" -> "B"
            "残暴", "穿刺" -> "C"
            "范围" -> "F"
            "过热" -> "G"
            "毫不留情", "洪流", "毁灭" -> "H"
            "集中", "精准" -> "J"
            "平衡" -> "P"
            "撕裂" -> "S"
            "无休" -> "W"
            "严重", "有限", "晕眩" -> "Y"
            "震荡", "致命", "重击", "重型", "追踪" -> "Z"
            else -> "·"
        }
    }

    private fun setupHistoryGestures() {
        var edgeStartX = 0f
        var edgeStartY = 0f
        historyEdgeHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    edgeStartX = event.x
                    edgeStartY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val shouldOpen = if (isTablet) {
                        edgeStartX - event.x > dp(32)
                    } else {
                        edgeStartY - event.y > dp(32)
                    }
                    if (shouldOpen) openHistory()
                    true
                }
                else -> true
            }
        }

        var panelStartX = 0f
        var panelStartY = 0f
        historyHeader.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    panelStartX = event.x
                    panelStartY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val shouldClose = if (isTablet) {
                        event.x - panelStartX > dp(44)
                    } else {
                        event.y - panelStartY > dp(44)
                    }
                    if (shouldClose) closeHistory()
                    true
                }
                else -> true
            }
        }
    }

    private fun selectMode(mode: RollMode) {
        if (rollInProgress || currentMode == mode) return
        currentMode = mode
        showTrayPage(visibleTrayMode, mode)
        renderConfig()
        renderCurrentRollState()
    }

    private fun showTrayPage(previousMode: RollMode, mode: RollMode) {
        visibleTrayMode = mode
        if (isTablet) {
            attackTrayPage.visibility = View.VISIBLE
            defenceTrayPage.visibility = View.VISIBLE
            attackTrayPage.translationX = 0f
            defenceTrayPage.translationX = 0f
            return
        }
        if (previousMode == mode) {
            attackTrayPage.visibility = if (mode == RollMode.ATTACK) View.VISIBLE else View.INVISIBLE
            defenceTrayPage.visibility = if (mode == RollMode.DEFENCE) View.VISIBLE else View.INVISIBLE
            return
        }
        val outgoing = if (previousMode == RollMode.ATTACK) attackTrayPage else defenceTrayPage
        val incoming = if (mode == RollMode.ATTACK) attackTrayPage else defenceTrayPage
        val direction = if (mode == RollMode.DEFENCE) 1f else -1f
        val distance = diceStage.width.toFloat().coerceAtLeast(1f)
        outgoing.animate().cancel()
        incoming.animate().cancel()
        incoming.visibility = View.VISIBLE
        incoming.translationX = direction * distance
        incoming.animate().translationX(0f).setDuration(220).start()
        outgoing.animate()
            .translationX(-direction * distance)
            .setDuration(220)
            .withEndAction {
                outgoing.visibility = View.INVISIBLE
                outgoing.translationX = 0f
            }
            .start()
    }

    private fun restoreDiceTheme() {
        val savedTheme = getSharedPreferences("ui_preferences", MODE_PRIVATE)
            .getString("dice_theme", DiceTheme.ANGELS_OF_DEATH.name)
        currentDiceTheme = runCatching {
            DiceTheme.valueOf(savedTheme.orEmpty())
        }.getOrDefault(DiceTheme.ANGELS_OF_DEATH)
        attackDiceTray.setDiceTheme(currentDiceTheme)
        defenceDiceTray.setDiceTheme(currentDiceTheme)
        renderDiceThemeButton()
    }

    private fun showDiceThemeChooser() {
        val themes = DiceTheme.entries.toTypedArray()
        val labels = arrayOf(
            getString(R.string.theme_angels_option),
            getString(R.string.theme_plague_option),
            getString(R.string.theme_kommandos_option),
            getString(R.string.theme_corsair_option),
            getString(R.string.theme_death_korps_option)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dice_theme)
            .setSingleChoiceItems(labels, currentDiceTheme.ordinal) { dialog, index ->
                applyDiceTheme(themes[index], announce = true)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyDiceTheme(theme: DiceTheme, announce: Boolean) {
        currentDiceTheme = theme
        attackDiceTray.setDiceTheme(theme)
        defenceDiceTray.setDiceTheme(theme)
        getSharedPreferences("ui_preferences", MODE_PRIVATE)
            .edit()
            .putString("dice_theme", theme.name)
            .apply()
        renderDiceThemeButton()
        if (announce) {
            Snackbar.make(
                rootLayout,
                getString(R.string.theme_changed, diceThemeDisplayName(theme)),
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun renderDiceThemeButton() {
        btnDiceTheme.imageTintList =
            ColorStateList.valueOf(getColor(R.color.primary_light))
    }

    private fun diceThemeDisplayName(theme: DiceTheme): String = getString(
        when (theme) {
            DiceTheme.ANGELS_OF_DEATH -> R.string.theme_angels_short
            DiceTheme.PLAGUE_MARINES -> R.string.theme_plague_short
            DiceTheme.ORK_KOMMANDOS -> R.string.theme_kommandos_short
            DiceTheme.CORSAIR_VOIDSCARRED -> R.string.theme_corsair_short
            DiceTheme.DEATH_KORPS -> R.string.theme_death_korps_short
        }
    )

    private fun adjustDice(delta: Int, view: View) {
        if (isCurrentConfigLocked()) return
        haptic(view)
        config.diceCount = (config.diceCount + delta).coerceIn(1, 20)
        invalidateRoll()
    }

    private fun adjustThreshold(delta: Int, view: View) {
        if (isCurrentConfigLocked()) return
        haptic(view)
        config.threshold = (config.threshold + delta).coerceIn(2, 6)
        if (config.criticalThreshold < config.threshold) {
            config.criticalThreshold = config.threshold
        }
        invalidateRoll()
    }

    private fun invalidateRoll() {
        resetRoll()
        renderConfig()
    }

    private fun renderConfig() {
        attackDiceTray.setDiceSelectionEnabled(
            currentMode == RollMode.ATTACK && !attackConfirmed
        )
        defenceDiceTray.setDiceSelectionEnabled(
            currentMode == RollMode.DEFENCE && attackConfirmed
        )
        diceCountLabel.text = getString(
            when (currentMode) {
                RollMode.ATTACK -> R.string.attack_dice_label
                RollMode.DEFENCE -> R.string.defence_dice_label
            }
        )
        thresholdLabel.text = getString(
            when (currentMode) {
                RollMode.ATTACK -> R.string.hit_label
                RollMode.DEFENCE -> R.string.save_label
            }
        )
        diceCountText.text = config.diceCount.toString()
        thresholdText.text = getString(R.string.plus_value, config.threshold)
        val controlsLocked = isCurrentConfigLocked()
        listOf(
            btnDiceMinus,
            btnDicePlus,
            btnThresholdMinus,
            btnThresholdPlus
        ).forEach {
            it.isEnabled = !controlsLocked
            it.alpha = if (controlsLocked) 0.55f else 1f
        }
        btnCritical.isEnabled = !controlsLocked
        btnCritical.alpha = if (controlsLocked) 0.55f else 1f
        criticalText.text = when {
            currentMode == RollMode.DEFENCE && defenceHasCover -> getString(R.string.cover_on)
            currentMode == RollMode.DEFENCE -> getString(R.string.cover_off)
            config.criticalThreshold == 6 -> getString(R.string.critical_six)
            else -> getString(R.string.lethal_value, config.criticalThreshold)
        }
        criticalText.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0,
            if (currentMode == RollMode.DEFENCE) R.drawable.ic_defence else R.drawable.ic_critical,
            0,
            0
        )
        val criticalColor = getColor(
            if (currentMode == RollMode.DEFENCE && defenceHasCover) {
                R.color.normal_success
            } else {
                R.color.critical_result
            }
        )
        criticalText.setTextColor(criticalColor)
        criticalText.compoundDrawableTintList = ColorStateList.valueOf(criticalColor)
    }

    private fun isCurrentConfigLocked(): Boolean =
        visibleTrayMode != currentMode || encounterComplete ||
            (currentMode == RollMode.ATTACK && attackConfirmed)

    private fun handlePrimaryAction() {
        when {
            encounterComplete -> startNewEncounter()
            currentMode == RollMode.ATTACK && currentValues.isNotEmpty() -> confirmAttack()
            currentMode == RollMode.DEFENCE && !attackConfirmed -> {
                Snackbar.make(rootLayout, R.string.finish_attack_first, Snackbar.LENGTH_SHORT).show()
            }
            else -> performRoll()
        }
    }

    private fun confirmAttack() {
        if (valuesByMode.getValue(RollMode.ATTACK).isEmpty()) return
        attackConfirmed = true
        renderConfig()
        selectMode(RollMode.DEFENCE)
    }

    private fun startNewEncounter() {
        attackDiceTray.clearResults()
        defenceDiceTray.clearResults()
        valuesByMode.keys.forEach { valuesByMode[it] = emptyList() }
        stagesByMode.values.forEach { it.clear() }
        pendingRerollIndices = null
        activeHistoryId = null
        attackConfirmed = false
        encounterComplete = false
        coverRetainedBadge.visibility = if (defenceHasCover) View.VISIBLE else View.GONE
        visibleTrayMode = RollMode.ATTACK
        if (currentMode != RollMode.ATTACK) {
            currentMode = RollMode.ATTACK
        }
        attackTrayPage.visibility = View.VISIBLE
        attackTrayPage.translationX = 0f
        defenceTrayPage.visibility = if (isTablet) View.VISIBLE else View.INVISIBLE
        defenceTrayPage.translationX = 0f
        renderConfig()
        renderCurrentRollState()
        statusText.setText(R.string.status_ready)
        setLamp(LampState.READY)
    }

    private fun performRoll() {
        if (!diceReady || rollInProgress || encounterComplete) return
        if (currentMode == RollMode.DEFENCE && !attackConfirmed) return
        currentValues = emptyList()
        stagesByMode.getValue(currentMode).clear()
        diceTrayView.clearResults()
        pendingRerollIndices = null
        setRollingState()
        val rollCount = if (currentMode == RollMode.DEFENCE && defenceHasCover) {
            (config.diceCount - 1).coerceAtLeast(0)
        } else {
            config.diceCount
        }
        if (rollCount > 0) diceSoundPlayer.play()
        diceTrayView.roll(rollCount) { results -> completeRoll(results) }
        vibrate(35)
    }

    private fun performReroll() {
        if (currentValues.isEmpty() || rollInProgress) return
        if (currentMode == RollMode.ATTACK && attackConfirmed) return
        val indices = diceTrayView.selectedIndices()
        if (indices.isEmpty()) return
        pendingRerollIndices = indices
        setRollingState()
        diceSoundPlayer.play()
        diceTrayView.rerollSelected { results -> completeRoll(results) }
        vibrate(25)
    }

    private fun completeRoll(values: List<Int>) {
        val rerolledIndices = pendingRerollIndices
        currentValues = rerolledIndices?.let { indices ->
            RollLogic.mergeRerollResults(currentValues, indices, values)
        } ?: values
        pendingRerollIndices = null
        stagesByMode.getValue(currentMode).add(currentValues.toList())
        rollInProgress = false

        if (currentMode == RollMode.DEFENCE) {
            val historyId = activeHistoryId
            if (historyId == null) {
                activeHistoryId = historyStore.addPair(
                    configs.getValue(RollMode.ATTACK),
                    stagesByMode.getValue(RollMode.ATTACK),
                    configs.getValue(RollMode.DEFENCE),
                    stagesByMode.getValue(RollMode.DEFENCE),
                    if (defenceHasCover) 1 else 0
                )
            } else if (rerolledIndices != null) {
                historyStore.appendReroll(historyId, RollMode.DEFENCE, currentValues)
            }
            encounterComplete = true
            intent.getStringExtra(BattleActivity.EXTRA_BATTLE_ID)?.let { battleId ->
                val store = BattleStore(this)
                store.load(battleId)?.takeUnless { it.completed }?.let { battle ->
                    val entry = BattleLogEntry(
                        id = "roll-${activeHistoryId}",
                        turningPoint = battle.turningPoint,
                        phase = battle.phase,
                        message = "${intent.getStringExtra("battle_roll_context").orEmpty()}；攻击骰 " +
                            stagesByMode.getValue(RollMode.ATTACK).joinToString(" → ") +
                            "；防御骰 " + stagesByMode.getValue(RollMode.DEFENCE).joinToString(" → ") +
                            if (defenceHasCover) "；掩护预留 1 普通成功" else ""
                    )
                    store.save(battle.copy(log = battle.log.filterNot { it.id == entry.id } + entry))
                }
            }
        }
        showSummary()
        renderConfig()
        vibrate(20)
    }

    private fun showSummary() {
        if (isTablet) {
            updateTabletSummaries()
        } else {
            val visibleValues = valuesByMode.getValue(visibleTrayMode)
            val summary = summaryFor(visibleTrayMode, visibleValues)
            criticalCountText.text = getString(R.string.result_critical, summary.criticals)
            normalCountText.text = getString(R.string.result_normal, summary.normals)
            failureCountText.text = getString(R.string.result_failure, summary.failures)
            summaryLayout.visibility = View.VISIBLE
        }
        postRollButtons.visibility = View.GONE
        btnRoll.isEnabled = diceReady
        btnReset.isEnabled = visibleTrayMode == currentMode
        updateRerollControl(
            if (visibleTrayMode == currentMode) diceTrayView.selectedIndices().size else 0
        )
        statusText.setText(
            when {
                currentMode == RollMode.ATTACK && !attackConfirmed -> R.string.status_attack_ready
                currentMode == RollMode.DEFENCE && currentValues.isEmpty() -> R.string.status_ready
                else -> R.string.status_resolved
            }
        )
        setLamp(LampState.READY)
        renderResolution()
        renderPrimaryAction()
    }

    private fun updateTabletSummaries() {
        if (!isTablet) return
        renderTabletSummary(
            mode = RollMode.ATTACK,
            layout = summaryLayout,
            normalText = normalCountText,
            criticalText = criticalCountText,
            failureText = failureCountText
        )
        renderTabletSummary(
            mode = RollMode.DEFENCE,
            layout = defenceSummaryLayout ?: return,
            normalText = defenceNormalCountText ?: return,
            criticalText = defenceCriticalCountText ?: return,
            failureText = defenceFailureCountText ?: return
        )
    }

    private fun renderTabletSummary(
        mode: RollMode,
        layout: View,
        normalText: TextView,
        criticalText: TextView,
        failureText: TextView
    ) {
        val values = valuesByMode.getValue(mode)
        if (values.isEmpty()) {
            layout.visibility = View.INVISIBLE
            layout.animate().cancel()
            return
        }
        val summary = summaryFor(mode, values)
        normalText.text = getString(R.string.result_normal, summary.normals)
        criticalText.text = getString(R.string.result_critical, summary.criticals)
        failureText.text = getString(R.string.result_failure, summary.failures)
        showResultOverlay(layout)
    }

    private fun summaryFor(mode: RollMode, values: List<Int>): RollSummary {
        val sideConfig = configs.getValue(mode)
        val base = RollLogic.classify(
            values,
            sideConfig.threshold,
            sideConfig.criticalThreshold
        )
        return if (mode == RollMode.DEFENCE && defenceHasCover) {
            base.copy(normals = base.normals + 1)
        } else {
            base
        }
    }

    private fun renderResolution() {
        val attackValues = valuesByMode.getValue(RollMode.ATTACK)
        val defenceValues = valuesByMode.getValue(RollMode.DEFENCE)
        if (!encounterComplete || attackValues.isEmpty() || defenceValues.isEmpty()) {
            resolutionPanel.animate().cancel()
            resolutionPanel.visibility = View.GONE
            return
        }

        val resolution = RollLogic.resolveShooting(
            attack = summaryFor(RollMode.ATTACK, attackValues),
            defence = summaryFor(RollMode.DEFENCE, defenceValues)
        )
        resolutionText.text = if (
            resolution.unblockedCriticals == 0 && resolution.unblockedNormals == 0
        ) {
            getString(R.string.resolution_cleared)
        } else {
            getString(
                R.string.resolution_result,
                resolution.unblockedCriticals,
                resolution.unblockedNormals
            )
        }
        if (isTablet) {
            showResultOverlay(resolutionPanel, offsetDp = -8f)
        } else {
            resolutionPanel.visibility = View.VISIBLE
        }
    }

    private fun showResultOverlay(view: View, offsetDp: Float = 8f) {
        if (view.visibility == View.VISIBLE) return
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = dp(offsetDp.toInt()).toFloat()
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .start()
    }

    private fun renderCurrentRollState() {
        renderResolution()
        if (valuesByMode.getValue(visibleTrayMode).isEmpty()) {
            if (isTablet) {
                updateTabletSummaries()
            } else {
                summaryLayout.visibility = View.INVISIBLE
            }
            btnReset.isEnabled = false
            updateRerollControl(0)
            statusText.setText(if (diceReady) R.string.status_ready else R.string.status_loading)
            setLamp(if (diceReady) LampState.READY else LampState.DIM)
        } else {
            showSummary()
        }
        renderPrimaryAction()
    }

    private fun renderPrimaryAction() {
        val shouldShow = encounterComplete ||
            (currentMode == RollMode.ATTACK && currentValues.isNotEmpty())
        val textRes = when {
            encounterComplete -> R.string.new_encounter_button
            currentMode == RollMode.ATTACK && currentValues.isNotEmpty() ->
                R.string.confirm_attack_button
            currentMode == RollMode.ATTACK -> R.string.roll_attack_button
            !attackConfirmed -> R.string.defence_locked_button
            else -> R.string.roll_defence_button
        }
        btnRoll.setText(textRes)
        btnRoll.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        btnRoll.visibility = if (shouldShow) View.VISIBLE else View.GONE
        btnRoll.isEnabled = diceReady && !rollInProgress &&
            (currentMode != RollMode.DEFENCE || attackConfirmed || encounterComplete)
        btnRoll.alpha = if (btnRoll.isEnabled) 1f else 0.55f
    }

    private fun resetRoll() {
        currentValues = emptyList()
        stagesByMode.getValue(currentMode).clear()
        diceTrayView.clearResults()
        pendingRerollIndices = null
        if (currentMode == RollMode.ATTACK) {
            attackConfirmed = false
            encounterComplete = false
            valuesByMode[RollMode.DEFENCE] = emptyList()
            stagesByMode.getValue(RollMode.DEFENCE).clear()
            defenceDiceTray.clearResults()
            activeHistoryId = null
        } else if (!encounterComplete) {
            activeHistoryId = null
        }
        if (isTablet) {
            updateTabletSummaries()
        } else {
            summaryLayout.visibility = View.INVISIBLE
        }
        renderResolution()
        postRollButtons.visibility = View.GONE
        btnReset.isEnabled = false
        updateRerollControl(0)
        renderIdleState()
    }

    private fun clearRollWithUndo() {
        if (encounterComplete) {
            startNewEncounter()
            return
        }
        if (currentValues.isEmpty()) {
            resetRoll()
            return
        }
        val undoState = UndoState(
            mode = currentMode,
            config = config.copy(),
            values = currentValues.toList(),
            historyId = activeHistoryId,
            diceTheme = currentDiceTheme
        )
        resetRoll()
        Snackbar.make(rootLayout, R.string.cleared_message, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { restoreUndoState(undoState) }
            .show()
    }

    private fun restoreUndoState(state: UndoState) {
        val previousMode = visibleTrayMode
        currentMode = state.mode
        configs[state.mode] = state.config.copy()
        applyDiceTheme(state.diceTheme, announce = false)
        currentValues = state.values
        stagesByMode.getValue(state.mode).apply {
            clear()
            add(state.values.toList())
        }
        activeHistoryId = state.historyId
        pendingRerollIndices = null
        showTrayPage(previousMode, state.mode)
        renderConfig()
        diceTrayView.restoreResults(state.values)
        showSummary()
    }

    private fun updateRerollControl(selectedCount: Int) {
        val viewingActiveTray = visibleTrayMode == currentMode
        val enabled = viewingActiveTray && currentValues.isNotEmpty() && selectedCount > 0 &&
            !(currentMode == RollMode.ATTACK && attackConfirmed) && !rollInProgress
        btnReroll.isEnabled = enabled
        btnReroll.alpha = if (enabled) 1f else 0.55f
        btnReroll.text = if (selectedCount > 0) {
            getString(R.string.reroll_count_button, selectedCount)
        } else {
            getString(R.string.select_dice_to_reroll)
        }
        gestureHintText.text = when {
            !viewingActiveTray -> getString(R.string.gesture_read_only)
            selectedCount > 0 -> getString(R.string.gesture_selected, selectedCount)
            currentMode == RollMode.ATTACK && currentValues.isEmpty() && loadedWeaponHint != null ->
                loadedWeaponHint
            else -> getString(R.string.gesture_idle)
        }
    }

    private fun applyWeaponIntent(source: Intent): Boolean {
        if (!source.hasExtra(EXTRA_ATTACK_DICE) || !source.hasExtra(EXTRA_HIT)) {
            helpWeaponName = null
            helpWeaponKeywords = emptyList()
            weaponInfoPanel.visibility = View.GONE
            formatRulesHelpContent()
            return false
        }

        val attacks = source.getIntExtra(EXTRA_ATTACK_DICE, 1).coerceIn(1, 20)
        val hit = source.getIntExtra(EXTRA_HIT, 6).coerceIn(2, 6)
        val critical = source.getIntExtra(EXTRA_CRITICAL, 6).coerceIn(2, 6)
        val normalDamage = source.getIntExtra(EXTRA_NORMAL_DAMAGE, 0).coerceAtLeast(0)
        val criticalDamage = source.getIntExtra(EXTRA_CRITICAL_DAMAGE, 0).coerceAtLeast(0)
        val rosterName = source.getStringExtra(EXTRA_ROSTER_NAME).orEmpty()
        val memberName = source.getStringExtra(EXTRA_MEMBER_NAME).orEmpty()
        val weaponName = source.getStringExtra(EXTRA_WEAPON_NAME).orEmpty()
        val keywords = source.getStringArrayListExtra(EXTRA_WEAPON_KEYWORDS).orEmpty()
        helpWeaponName = weaponName.ifBlank { "所选武器" }
        helpWeaponKeywords = keywords
        formatRulesHelpContent()

        attackDiceTray.clearResults()
        defenceDiceTray.clearResults()
        valuesByMode.keys.forEach { valuesByMode[it] = emptyList() }
        stagesByMode.values.forEach { it.clear() }
        pendingRerollIndices = null
        activeHistoryId = null
        attackConfirmed = false
        encounterComplete = false
        defenceHasCover = false
        rollInProgress = false
        coverRetainedBadge.visibility = View.GONE
        summaryLayout.visibility = View.INVISIBLE
        defenceSummaryLayout?.visibility = View.INVISIBLE
        resolutionPanel.visibility = View.GONE
        postRollButtons.visibility = View.GONE

        configs[RollMode.ATTACK] = RollConfig(attacks, hit, critical)
        if (source.hasExtra("battle_target_save")) {
            configs[RollMode.DEFENCE] = RollConfig(3, source.getIntExtra("battle_target_save", 6).coerceIn(2, 6), 6)
        }
        val previousMode = visibleTrayMode
        currentMode = RollMode.ATTACK
        showTrayPage(previousMode, RollMode.ATTACK)

        loadedWeaponHint = buildString {
            if (memberName.isNotBlank()) append(memberName).append(" · ")
            append(weaponName.ifBlank { "已选武器" })
            append(" · 伤害 ").append(normalDamage).append('/').append(criticalDamage)
            if (keywords.isNotEmpty()) append(" · ").append(keywords.joinToString("、"))
        }
        weaponInfoTitle.text = listOf(memberName, weaponName.ifBlank { "已选武器" })
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val weaponDetails = buildString {
            append("攻击 ").append(attacks)
            append(" · 命中 ").append(hit).append('+')
            append(" · 暴击 ").append(critical).append('+')
            append(" · 伤害 ").append(normalDamage).append('/').append(criticalDamage)
            if(keywords.isNotEmpty()) append("\n关键词：").append(keywords.joinToString("、"))
        }
        WeaponRulesUi.linkify(weaponInfoText,referenceUi,weaponName.ifBlank { "所选武器" },weaponDetails,keywords)
        weaponInfoPanel.visibility = View.VISIBLE

        renderConfig()
        renderCurrentRollState()
        updateRerollControl(0)
        statusText.text = if (rosterName.isBlank()) {
            getString(R.string.status_ready)
        } else {
            rosterName
        }
        return true
    }

    private fun renderIdleState() {
        renderResolution()
        statusText.setText(if (diceReady) R.string.status_ready else R.string.status_loading)
        setLamp(if (diceReady) LampState.READY else LampState.DIM)
        renderPrimaryAction()
    }

    private fun setRollingState() {
        rollInProgress = true
        btnRoll.isEnabled = false
        btnReroll.isEnabled = false
        statusText.setText(R.string.status_rolling)
        setLamp(LampState.ROLLING)
    }

    private fun showFirstGestureCoach() {
        val preferences = getSharedPreferences("ui_preferences", MODE_PRIVATE)
        if (preferences.getBoolean("gesture_coach_seen", false)) return
        preferences.edit().putBoolean("gesture_coach_seen", true).apply()
        gestureCoach.alpha = 0f
        gestureCoach.visibility = View.VISIBLE
        gestureCoach.animate().alpha(1f).setDuration(240).start()
        gestureCoach.postDelayed({
            if (!isFinishing) {
                gestureCoach.animate()
                    .alpha(0f)
                    .setDuration(420)
                    .withEndAction { gestureCoach.visibility = View.GONE }
                    .start()
            }
        }, 5200L)
    }

    private fun openHistory() {
        if (rulesHelpExpanded) setRulesHelpExpanded(false)
 renderHistoryList()
 historyPanel.animate().cancel()
 historyScrim.animate().cancel()
 historyScrim.alpha = 0f
 historyScrim.visibility = View.VISIBLE
 historyScrim.animate().alpha(1f).setDuration(180).start()
 historyPanel.visibility = View.VISIBLE
        historyPanel.alpha = 1f
        historyPanel.post {
            if (isTablet) {
                historyPanel.translationX = historyPanel.width.toFloat()
                historyPanel.translationY = 0f
                historyPanel.animate().translationX(0f).setDuration(240).start()
            } else {
                historyPanel.translationY = historyPanel.height.toFloat()
                historyPanel.translationX = 0f
                historyPanel.animate().translationY(0f).setDuration(240).start()
            }
            mainContent.animate().alpha(0.48f).setDuration(200).start()
        }
    }

    private fun closeHistory() {
 if (historyPanel.visibility != View.VISIBLE) return
 historyPanel.animate().cancel()
 historyScrim.animate().cancel()
 historyScrim.visibility = View.GONE
        val animator = if (isTablet) {
            historyPanel.animate().translationX(historyPanel.width.toFloat())
        } else {
            historyPanel.animate().translationY(historyPanel.height.toFloat())
        }
        animator.setDuration(210).withEndAction {
            historyPanel.visibility = View.GONE
            historyPanel.translationX = 0f
            historyPanel.translationY = 0f
        }.start()
        mainContent.animate().alpha(1f).setDuration(180).start()
    }

    private fun renderHistoryList() {
        historyList.removeAllViews()
        val entries = historyStore.entries()
        if (entries.isEmpty()) {
            historyList.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(120)
                )
                gravity = Gravity.CENTER
                text = getString(R.string.history_empty)
                setTextColor(getColor(R.color.text_steel))
                textSize = 14f
            })
            return
        }
        entries.forEach { entry -> historyList.addView(createHistoryRow(entry)) }
    }

    private fun createHistoryRow(entry: RollHistoryEntry): View {
        if (entry.isPaired) return createPairedHistoryRow(entry)

        val summary = RollLogic.classify(
            entry.latestValues,
            entry.threshold,
            entry.criticalThreshold
        )
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(R.drawable.bg_panel_inset)
            isClickable = true
            isFocusable = true
            setOnClickListener { restoreHistoryEntry(entry) }
            setOnLongClickListener {
                historyStore.delete(entry.id)
                if (activeHistoryId == entry.id) activeHistoryId = null
                renderHistoryList()
                Snackbar.make(rootLayout, R.string.history_deleted, Snackbar.LENGTH_SHORT).show()
                true
            }
        }
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(7) }

        row.addView(TextView(this).apply {
            text = getString(
                R.string.history_item_title,
                modeDisplayName(entry.mode),
                entry.diceCount,
                entry.threshold
            )
            setTextColor(getColor(R.color.primary_light))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = getString(
                R.string.history_result,
                entry.latestValues.joinToString(" · ")
            )
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            setPadding(0, dp(5), 0, 0)
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(2))
            addView(createHistoryStatText(
                getString(R.string.result_critical, summary.criticals),
                R.color.critical_result,
                R.font.pirata_one_regular
            ))
            addView(createHistoryStatText(
                getString(R.string.result_failure, summary.failures),
                R.color.failure_result,
                null
            ))
            addView(createHistoryStatText(
                getString(R.string.result_normal, summary.normals),
                R.color.normal_success,
                R.font.teko_variable
            ))
        })
        row.addView(TextView(this).apply {
            val time = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
                .format(Date(entry.timestamp))
            text = if (entry.rerollCount > 0) {
                "$time　${getString(R.string.history_rerolls, entry.rerollCount)}"
            } else {
                time
            }
            setTextColor(getColor(R.color.text_steel))
            textSize = 11f
            setPadding(0, dp(4), 0, 0)
        })
        return row
    }

    private fun createPairedHistoryRow(entry: RollHistoryEntry): View {
        val attack = requireNotNull(entry.attack)
        val defence = requireNotNull(entry.defence)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(R.drawable.bg_panel_inset)
            setOnClickListener { restoreHistoryEntry(entry) }
            setOnLongClickListener {
                historyStore.delete(entry.id)
                if (activeHistoryId == entry.id) activeHistoryId = null
                renderHistoryList()
                Snackbar.make(rootLayout, R.string.history_deleted, Snackbar.LENGTH_SHORT).show()
                true
            }
        }
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(7) }

        row.addView(TextView(this).apply {
            text = getString(R.string.history_pair_title, attack.diceCount, defence.diceCount)
            setTextColor(getColor(R.color.primary_light))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addHistorySide(row, RollMode.ATTACK, attack)
        addHistorySide(row, RollMode.DEFENCE, defence)

        row.addView(TextView(this).apply {
            val time = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
                .format(Date(entry.timestamp))
            text = if (entry.rerollCount > 0) {
                "$time  ${getString(R.string.history_rerolls, entry.rerollCount)}"
            } else {
                time
            }
            setTextColor(getColor(R.color.text_steel))
            textSize = 11f
            setPadding(0, dp(5), 0, 0)
        })
        return row
    }

    private fun addHistorySide(
        row: LinearLayout,
        mode: RollMode,
        side: RollSideHistory
    ) {
        val summary = RollLogic.classify(
            side.latestValues,
            side.threshold,
            side.criticalThreshold
        ).let { base ->
            if (side.retainedNormals > 0) {
                base.copy(normals = base.normals + side.retainedNormals)
            } else {
                base
            }
        }
        row.addView(TextView(this).apply {
            val coverSuffix = if (side.retainedNormals > 0) {
                getString(R.string.history_cover_suffix, side.retainedNormals)
            } else {
                ""
            }
            text = getString(
                R.string.history_side_title,
                modeDisplayName(mode),
                side.diceCount,
                side.threshold,
                coverSuffix
            )
            setTextColor(
                getColor(
                    if (mode == RollMode.ATTACK) R.color.critical_result
                    else R.color.normal_success
                )
            )
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(7), 0, 0)
        })
        row.addView(TextView(this).apply {
            text = getString(
                R.string.history_result,
                side.latestValues.joinToString(" · ")
            )
            setTextColor(getColor(R.color.text_primary))
            textSize = 12f
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(1))
            addView(createHistoryStatText(
                getString(R.string.result_critical, summary.criticals),
                R.color.critical_result,
                R.font.pirata_one_regular
            ))
            addView(createHistoryStatText(
                getString(R.string.result_failure, summary.failures),
                R.color.failure_result,
                null
            ))
            addView(createHistoryStatText(
                getString(R.string.result_normal, summary.normals),
                R.color.normal_success,
                R.font.teko_variable
            ))
        })
    }

    private fun createHistoryStatText(textValue: String, colorRes: Int, fontRes: Int?): TextView =
        TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f)
            gravity = Gravity.CENTER
            text = textValue
            setTextColor(getColor(colorRes))
            textSize = 18f
            typeface = if (fontRes == null) {
                android.graphics.Typeface.create(
                    android.graphics.Typeface.MONOSPACE,
                    android.graphics.Typeface.BOLD
                )
            } else {
                android.graphics.Typeface.create(
                    ResourcesCompat.getFont(this@MainActivity, fontRes),
                    android.graphics.Typeface.BOLD
                )
            }
        }

    private fun restoreHistoryEntry(entry: RollHistoryEntry) {
        helpWeaponName = null
        helpWeaponKeywords = emptyList()
        formatRulesHelpContent()
        // Historical rolls are independent of the battle that opened this screen.
        intent.removeExtra(BattleActivity.EXTRA_BATTLE_ID)
        if (entry.isPaired) {
            restorePairedHistoryEntry(entry)
            return
        }

        attackConfirmed = false
        encounterComplete = false
        defenceHasCover = false
        coverRetainedBadge.visibility = View.GONE
        valuesByMode.keys.forEach { valuesByMode[it] = emptyList() }
        stagesByMode.values.forEach { it.clear() }
        attackDiceTray.clearResults()
        defenceDiceTray.clearResults()
        currentMode = entry.mode
        visibleTrayMode = currentMode
        attackTrayPage.visibility = if (isTablet || currentMode == RollMode.ATTACK) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        defenceTrayPage.visibility = if (isTablet || currentMode == RollMode.DEFENCE) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        attackTrayPage.translationX = 0f
        defenceTrayPage.translationX = 0f
        configs[currentMode] = RollConfig(
            entry.diceCount,
            entry.threshold,
            entry.criticalThreshold
        )
        currentValues = entry.latestValues
        pendingRerollIndices = null
        activeHistoryId = entry.id
        renderConfig()
        diceTrayView.restoreResults(entry.latestValues)
        showSummary()
        closeHistory()
        Snackbar.make(rootLayout, R.string.history_restored, Snackbar.LENGTH_SHORT).show()
    }

    private fun restorePairedHistoryEntry(entry: RollHistoryEntry) {
        val attack = requireNotNull(entry.attack)
        val defence = requireNotNull(entry.defence)
        configs[RollMode.ATTACK] = RollConfig(
            attack.diceCount,
            attack.threshold,
            attack.criticalThreshold
        )
        configs[RollMode.DEFENCE] = RollConfig(
            defence.diceCount,
            defence.threshold,
            defence.criticalThreshold
        )
        valuesByMode[RollMode.ATTACK] = attack.latestValues
        valuesByMode[RollMode.DEFENCE] = defence.latestValues
        stagesByMode.getValue(RollMode.ATTACK).apply {
            clear()
            addAll(attack.stages.map { it.toList() })
        }
        stagesByMode.getValue(RollMode.DEFENCE).apply {
            clear()
            addAll(defence.stages.map { it.toList() })
        }
        defenceHasCover = defence.retainedNormals > 0
        coverRetainedBadge.visibility = if (defenceHasCover) View.VISIBLE else View.GONE
        attackConfirmed = true
        encounterComplete = true
        rollInProgress = false
        pendingRerollIndices = null
        activeHistoryId = entry.id
        attackDiceTray.restoreResults(attack.latestValues)
        defenceDiceTray.restoreResults(defence.latestValues)

        currentMode = RollMode.DEFENCE
        visibleTrayMode = RollMode.DEFENCE
        attackTrayPage.visibility = if (isTablet) View.VISIBLE else View.INVISIBLE
        attackTrayPage.translationX = 0f
        defenceTrayPage.visibility = View.VISIBLE
        defenceTrayPage.translationX = 0f
        renderConfig()
        renderCurrentRollState()
        closeHistory()
        Snackbar.make(rootLayout, R.string.history_restored, Snackbar.LENGTH_SHORT).show()
    }

    private fun confirmHistoryClear() {
        val now = System.currentTimeMillis()
        if (now > historyClearArmedUntil) {
            historyClearArmedUntil = now + 2600L
            btnClearHistory.setText(R.string.history_clear_confirm)
            val armedUntil = historyClearArmedUntil
            btnClearHistory.postDelayed({
                if (historyClearArmedUntil == armedUntil) {
                    historyClearArmedUntil = 0L
                    btnClearHistory.setText(R.string.history_clear)
                }
            }, 2700L)
            return
        }
        historyStore.clear()
        activeHistoryId = null
        historyClearArmedUntil = 0L
        btnClearHistory.setText(R.string.history_clear)
        renderHistoryList()
    }

    private fun modeDisplayName(mode: RollMode): String = getString(
        when (mode) {
            RollMode.ATTACK -> R.string.mode_attack_short
            RollMode.DEFENCE -> R.string.mode_defence_short
        }
    )

    private enum class LampState { DIM, READY, ROLLING, FAULT }

    private fun setLamp(state: LampState) {
        lampAnimator?.cancel()
        statusLamp.alpha = 1f
        statusLamp.setBackgroundResource(
            when (state) {
                LampState.DIM -> R.drawable.lamp_dim
                LampState.READY -> R.drawable.lamp_green
                LampState.ROLLING -> R.drawable.lamp_amber
                LampState.FAULT -> R.drawable.lamp_red
            }
        )
        if (state == LampState.READY || state == LampState.ROLLING) {
            lampAnimator = ObjectAnimator.ofFloat(statusLamp, View.ALPHA, 1f, 0.45f).apply {
                duration = if (state == LampState.READY) 1200 else 360
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }

    private fun addPressAnimation(view: View) {
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> target.animate()
                    .scaleX(0.97f).scaleY(0.97f).setDuration(70).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.animate()
                    .scaleX(1f).scaleY(1f).setDuration(110).start()
            }
            false
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun haptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(durationMs: Long) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                getSystemService(Vibrator::class.java)?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
            else -> getSystemService(Vibrator::class.java)?.vibrate(durationMs)
        }
    }

    override fun onDestroy() {
        lampAnimator?.cancel()
        diceSoundPlayer.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ROSTER_NAME = "roster_name"
        const val EXTRA_MEMBER_NAME = "member_name"
        const val EXTRA_WEAPON_NAME = "weapon_name"
        const val EXTRA_ATTACK_DICE = "attack_dice"
        const val EXTRA_HIT = "hit"
        const val EXTRA_CRITICAL = "critical"
        const val EXTRA_NORMAL_DAMAGE = "normal_damage"
        const val EXTRA_CRITICAL_DAMAGE = "critical_damage"
        const val EXTRA_WEAPON_KEYWORDS = "weapon_keywords"
    }
}
