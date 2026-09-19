package com.example.helloworld

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var modeBar: View
    private lateinit var consolePanel: View
    private lateinit var diceControlCell: View
    private lateinit var thresholdControlCell: View
    private lateinit var diceTrayView: DiceTrayView
    private lateinit var statusLamp: View
    private lateinit var statusText: TextView
    private lateinit var modeViews: List<TextView>
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
    private lateinit var summaryLayout: View
    private lateinit var criticalCountText: TextView
    private lateinit var normalCountText: TextView
    private lateinit var failureCountText: TextView
    private lateinit var btnHistory: TextView
    private lateinit var btnFocus: TextView
    private lateinit var gestureCoach: TextView
    private lateinit var gestureHintText: TextView
    private lateinit var presetAttack: TextView
    private lateinit var presetDefence: TextView
    private lateinit var btnDiceTheme: TextView
    private lateinit var historyEdgeHandle: View
    private lateinit var historyPanel: View
    private lateinit var historyHeader: View
    private lateinit var historyList: LinearLayout
    private lateinit var btnCloseHistory: View
    private lateinit var btnClearHistory: TextView
    private lateinit var historyStore: RollHistoryStore

    private val configs = mutableMapOf(
        RollMode.ATTACK to RollConfig(diceCount = 5, threshold = 3),
        RollMode.DEFENCE to RollConfig(diceCount = 3, threshold = 3),
        RollMode.FREE to RollConfig(diceCount = 5, threshold = 4)
    )

    private var currentMode = RollMode.ATTACK
    private var diceReady = true
    private var currentValues: List<Int> = emptyList()
    private var pendingRerollIndices: List<Int>? = null
    private var activeHistoryId: Long? = null
    private var lampAnimator: ObjectAnimator? = null
    private var focusMode = false
    private var historyClearArmedUntil = 0L
    private var currentDiceTheme = DiceTheme.ANGELS_OF_DEATH

    private val config: RollConfig
        get() = configs.getValue(currentMode)

    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersiveMode()
        setContentView(R.layout.activity_main)
        historyStore = RollHistoryStore(this)
        bindViews()
        restoreDiceTheme()
        applyConsoleSkin()
        setupListeners()
        renderConfig()
        renderIdleState()
        showFirstGestureCoach()
        setupBackNavigation()
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        mainContent = findViewById(R.id.mainContent)
        headerBar = findViewById(R.id.headerBar)
        diceStage = findViewById(R.id.diceStage)
        modeBar = findViewById(R.id.modeBar)
        consolePanel = findViewById(R.id.consolePanel)
        diceControlCell = findViewById(R.id.diceControlCell)
        thresholdControlCell = findViewById(R.id.thresholdControlCell)
        diceTrayView = findViewById(R.id.diceTrayOverlay)
        statusLamp = findViewById(R.id.statusLamp)
        statusText = findViewById(R.id.statusText)
        modeViews = listOf(
            findViewById(R.id.modeAttack),
            findViewById(R.id.modeDefence),
            findViewById(R.id.modeFree)
        )
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
        summaryLayout = findViewById(R.id.summaryLayout)
        criticalCountText = findViewById(R.id.criticalCountText)
        normalCountText = findViewById(R.id.normalCountText)
        failureCountText = findViewById(R.id.failureCountText)
        btnHistory = findViewById(R.id.btnHistory)
        btnFocus = findViewById(R.id.btnFocus)
        gestureCoach = findViewById(R.id.gestureCoach)
        gestureHintText = findViewById(R.id.gestureHintText)
        presetAttack = findViewById(R.id.presetAttack)
        presetDefence = findViewById(R.id.presetDefence)
        btnDiceTheme = findViewById(R.id.btnDiceTheme)
        historyEdgeHandle = findViewById(R.id.historyEdgeHandle)
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
        diceControlCell.background = GrimdarkSkins.button(this, ConsoleSurface.CELL)
        thresholdControlCell.background = GrimdarkSkins.button(this, ConsoleSurface.CELL)
        btnCritical.background = GrimdarkSkins.button(this, ConsoleSurface.CELL)
        summaryLayout.background = GrimdarkDrawable(this, ConsoleSurface.INSET)
        btnRoll.background = GrimdarkSkins.button(
            this,
            ConsoleSurface.GREEN,
            ConsoleSurface.GREEN_PRESSED
        )
        btnReroll.background = GrimdarkSkins.button(this, ConsoleSurface.CELL)
        btnReset.background = GrimdarkSkins.button(this, ConsoleSurface.CELL)
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
        modeViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                haptic(it)
                selectMode(RollMode.entries[index])
            }
        }
        btnDiceMinus.setOnClickListener { adjustDice(-1, it) }
        btnDicePlus.setOnClickListener { adjustDice(1, it) }
        btnThresholdMinus.setOnClickListener { adjustThreshold(-1, it) }
        btnThresholdPlus.setOnClickListener { adjustThreshold(1, it) }
        btnCritical.setOnClickListener {
            if (currentMode == RollMode.DEFENCE) return@setOnClickListener
            haptic(it)
            config.criticalThreshold = when (config.criticalThreshold) {
                6 -> 5
                5 -> 4
                else -> 6
            }
            invalidateRoll()
        }
        diceTrayView.setOnSelectionChangedListener { selectedCount ->
            updateRerollControl(selectedCount)
        }
        diceTrayView.setOnTrayGestureListener(
            onThrow = {
                if (currentValues.isNotEmpty() && diceTrayView.selectedIndices().isNotEmpty()) {
                    performReroll()
                } else {
                    performRoll()
                }
            },
            onReset = { clearRollWithUndo() }
        )
        btnRoll.setOnClickListener {
            haptic(it)
            performRoll()
        }
        btnReroll.setOnClickListener {
            haptic(it)
            performReroll()
        }
        btnReset.setOnClickListener {
            haptic(it)
            clearRollWithUndo()
        }
        presetAttack.setOnClickListener { applyPreset(RollMode.ATTACK, 5, 3, it) }
        presetDefence.setOnClickListener { applyPreset(RollMode.DEFENCE, 3, 3, it) }
        btnDiceTheme.setOnClickListener {
            haptic(it)
            showDiceThemeChooser()
        }
        btnHistory.setOnClickListener {
            haptic(it)
            openHistory()
        }
        btnCloseHistory.setOnClickListener { closeHistory() }
        btnClearHistory.setOnClickListener { confirmHistoryClear() }
        btnFocus.setOnClickListener {
            haptic(it)
            setFocusMode(!focusMode)
        }
        setupHistoryGestures()
        listOf(
            btnRoll,
            btnReroll,
            presetAttack,
            presetDefence,
            btnDiceTheme,
            btnHistory,
            btnFocus
        )
            .forEach(::addPressAnimation)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    historyPanel.visibility == View.VISIBLE -> closeHistory()
                    focusMode -> setFocusMode(false)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
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
        if (currentMode == mode) return
        currentMode = mode
        resetRoll()
        renderConfig()
    }

    private fun applyPreset(mode: RollMode, diceCount: Int, threshold: Int, view: View) {
        haptic(view)
        currentMode = mode
        configs[mode] = RollConfig(diceCount, threshold, 6)
        resetRoll()
        renderConfig()
        Snackbar.make(
            rootLayout,
            getString(R.string.preset_applied, modeDisplayName(mode)),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun restoreDiceTheme() {
        val savedTheme = getSharedPreferences("ui_preferences", MODE_PRIVATE)
            .getString("dice_theme", DiceTheme.ANGELS_OF_DEATH.name)
        currentDiceTheme = runCatching {
            DiceTheme.valueOf(savedTheme.orEmpty())
        }.getOrDefault(DiceTheme.ANGELS_OF_DEATH)
        diceTrayView.setDiceTheme(currentDiceTheme)
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
        diceTrayView.setDiceTheme(theme)
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
        btnDiceTheme.text = diceThemeDisplayName(currentDiceTheme)
        btnDiceTheme.setTextColor(
            getColor(
                when (currentDiceTheme) {
                    DiceTheme.ANGELS_OF_DEATH -> R.color.primary_light
                    DiceTheme.PLAGUE_MARINES -> R.color.normal_success
                    DiceTheme.ORK_KOMMANDOS -> R.color.theme_kommandos
                    DiceTheme.CORSAIR_VOIDSCARRED -> R.color.theme_corsair
                    DiceTheme.DEATH_KORPS -> R.color.theme_death_korps
                }
            )
        )
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
        haptic(view)
        config.diceCount = (config.diceCount + delta).coerceIn(1, 20)
        invalidateRoll()
    }

    private fun adjustThreshold(delta: Int, view: View) {
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
        modeViews.forEachIndexed { index, view ->
            val selected = index == currentMode.ordinal
            view.background = GrimdarkSkins.button(
                this,
                if (selected) ConsoleSurface.SELECTED else ConsoleSurface.CELL
            )
            view.setTextColor(getColor(if (selected) R.color.primary_light else R.color.text_steel))
        }
        diceCountLabel.text = getString(
            when (currentMode) {
                RollMode.ATTACK -> R.string.attack_dice_label
                RollMode.DEFENCE -> R.string.defence_dice_label
                RollMode.FREE -> R.string.dice_count_label
            }
        )
        thresholdLabel.text = getString(
            when (currentMode) {
                RollMode.ATTACK -> R.string.hit_label
                RollMode.DEFENCE -> R.string.save_label
                RollMode.FREE -> R.string.target_label
            }
        )
        diceCountText.text = config.diceCount.toString()
        thresholdText.text = getString(R.string.plus_value, config.threshold)
        val canChangeCritical = currentMode != RollMode.DEFENCE
        btnCritical.isEnabled = canChangeCritical
        btnCritical.alpha = if (canChangeCritical) 1f else 0.72f
        criticalText.text = when {
            currentMode == RollMode.DEFENCE -> getString(R.string.critical_save_six)
            config.criticalThreshold == 6 -> getString(R.string.critical_six)
            else -> getString(R.string.lethal_value, config.criticalThreshold)
        }
    }

    private fun performRoll() {
        if (!diceReady) return
        currentValues = emptyList()
        activeHistoryId = null
        diceTrayView.clearResults()
        pendingRerollIndices = null
        setRollingState()
        diceTrayView.roll(config.diceCount) { results -> completeRoll(results) }
        vibrate(35)
    }

    private fun performReroll() {
        if (currentValues.isEmpty()) return
        val indices = diceTrayView.selectedIndices()
        if (indices.isEmpty()) return
        pendingRerollIndices = indices
        setRollingState()
        diceTrayView.rerollSelected { results -> completeRoll(results) }
        vibrate(25)
    }

    private fun completeRoll(values: List<Int>) {
        val rerolledIndices = pendingRerollIndices
        currentValues = rerolledIndices?.let { indices ->
            RollLogic.mergeRerollResults(currentValues, indices, values)
        } ?: values
        pendingRerollIndices = null

        if (rerolledIndices == null) {
            activeHistoryId = historyStore.add(
                currentMode,
                config,
                currentDiceTheme,
                currentValues
            )
        } else {
            val historyId = activeHistoryId
            if (historyId == null) {
                activeHistoryId = historyStore.add(
                    currentMode,
                    config,
                    currentDiceTheme,
                    currentValues
                )
            } else {
                historyStore.appendReroll(historyId, currentValues)
            }
        }
        showSummary()
        vibrate(20)
    }

    private fun showSummary() {
        val summary = RollLogic.classify(
            currentValues,
            config.threshold,
            config.criticalThreshold
        )
        criticalCountText.text = getString(R.string.result_critical, summary.criticals)
        normalCountText.text = getString(R.string.result_normal, summary.normals)
        failureCountText.text = getString(R.string.result_failure, summary.failures)
        summaryLayout.visibility = View.VISIBLE
        postRollButtons.visibility = View.VISIBLE
        btnRoll.isEnabled = diceReady
        btnFocus.isEnabled = true
        btnRoll.setText(R.string.roll_again_button)
        btnReset.isEnabled = true
        updateRerollControl(diceTrayView.selectedIndices().size)
        statusText.setText(R.string.status_resolved)
        setLamp(LampState.READY)
    }

    private fun resetRoll() {
        currentValues = emptyList()
        activeHistoryId = null
        diceTrayView.clearResults()
        pendingRerollIndices = null
        summaryLayout.visibility = View.INVISIBLE
        postRollButtons.visibility = View.VISIBLE
        btnRoll.setText(R.string.roll_button)
        btnReset.isEnabled = false
        updateRerollControl(0)
        renderIdleState()
    }

    private fun clearRollWithUndo() {
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
        currentMode = state.mode
        configs[state.mode] = state.config.copy()
        applyDiceTheme(state.diceTheme, announce = false)
        currentValues = state.values
        activeHistoryId = state.historyId
        pendingRerollIndices = null
        renderConfig()
        diceTrayView.restoreResults(state.values)
        showSummary()
    }

    private fun updateRerollControl(selectedCount: Int) {
        val enabled = currentValues.isNotEmpty() && selectedCount > 0
        btnReroll.isEnabled = enabled
        btnReroll.alpha = if (enabled) 1f else 0.55f
        btnReroll.text = if (selectedCount > 0) {
            getString(R.string.reroll_count_button, selectedCount)
        } else {
            getString(R.string.select_dice_to_reroll)
        }
        gestureHintText.text = if (selectedCount > 0) {
            getString(R.string.gesture_selected, selectedCount)
        } else {
            getString(R.string.gesture_idle)
        }
    }

    private fun renderIdleState() {
        btnRoll.isEnabled = diceReady
        btnFocus.isEnabled = true
        statusText.setText(if (diceReady) R.string.status_ready else R.string.status_loading)
        setLamp(if (diceReady) LampState.READY else LampState.DIM)
    }

    private fun setRollingState() {
        btnRoll.isEnabled = false
        btnReroll.isEnabled = false
        btnFocus.isEnabled = false
        statusText.setText(R.string.status_rolling)
        setLamp(LampState.ROLLING)
    }

    private fun setFocusMode(enabled: Boolean) {
        focusMode = enabled
        consolePanel.visibility = if (enabled) View.GONE else View.VISIBLE
        if (!isTablet) modeBar.visibility = if (enabled) View.GONE else View.VISIBLE
        btnFocus.setText(if (enabled) R.string.exit_focus_mode else R.string.focus_mode)
        btnFocus.setTextColor(getColor(if (enabled) R.color.primary_light else R.color.text_steel))
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
        renderHistoryList()
        historyPanel.animate().cancel()
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
                entry.threshold,
                diceThemeDisplayName(entry.diceTheme)
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

    private fun restoreHistoryEntry(entry: RollHistoryEntry) {
        currentMode = entry.mode
        applyDiceTheme(entry.diceTheme, announce = false)
        configs[entry.mode] = RollConfig(
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
            RollMode.FREE -> R.string.mode_free_short
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            getSystemService(Vibrator::class.java)?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }

    override fun onDestroy() {
        lampAnimator?.cancel()
        super.onDestroy()
    }
}
