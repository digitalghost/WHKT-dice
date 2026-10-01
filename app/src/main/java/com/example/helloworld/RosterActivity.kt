package com.example.helloworld

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.io.File
import kotlin.math.roundToInt

class RosterActivity : AppCompatActivity() {
    private var pendingAvatarResult: ((String) -> Unit)? = null
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null
    private val importPhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingAvatarResult?.invoke(uri.toString())
        }
        pendingAvatarResult = null
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            pendingAvatarResult?.invoke(uri.toString())
        } else {
            pendingCameraFile?.delete()
        }
        pendingAvatarResult = null
        pendingCameraUri = null
        pendingCameraFile = null
    }

    private fun choosePhoto(onChosen: (String) -> Unit) {
        pendingAvatarResult = onChosen
        importPhoto.launch(arrayOf("image/*"))
    }

    private fun capturePhoto(onCaptured: (String) -> Unit) {
        val directory = File(filesDir, "operative_photos").apply { mkdirs() }
        val output = File(directory, "operative-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", output)
        pendingAvatarResult = onCaptured
        pendingCameraFile = output
        pendingCameraUri = uri
        takePhoto.launch(uri)
    }
    private val referenceUi by lazy { RecordUi(this) }
    private lateinit var root: LinearLayout
    private lateinit var teamButton: TextView
    private lateinit var rosterNameInput: EditText
    private lateinit var saveButton: TextView
    private lateinit var saveStateText: TextView
    private lateinit var catalogContent: LinearLayout
    private lateinit var rosterContent: LinearLayout
    private lateinit var catalogCountText: TextView
    private lateinit var catalogRuleText: TextView
    private lateinit var rosterCountText: TextView
    private lateinit var catalogPanel: View
    private lateinit var rosterPanel: View
    private var catalogTab: TextView? = null
    private var rosterTab: TextView? = null

    private lateinit var store: RosterStore
    private lateinit var currentTeam: KillTeamCatalog
    private lateinit var currentRoster: SavedRoster
    private var dirty = false
    private var suppressNameChange = false
    private var showingRosterOnPhone = false

    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersiveMode()
        store = RosterStore(this)
        val requestedRoster = intent.getStringExtra(EXTRA_ROSTER_ID)?.let(store::load)
        val requestedTeam = intent.getStringExtra(EXTRA_CREATE_TEAM_ID)
        currentTeam = RosterCatalog.team(requestedRoster?.teamId ?: requestedTeam ?: store.selectedTeamId())
        currentRoster = requestedRoster ?: store.createDraft(currentTeam)
        buildScreen()
        renderAll()
        setupBackNavigation()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun enterImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GrimdarkDrawable(this@RosterActivity, ConsoleSurface.ROOT)
        }
        setContentView(root)
        root.addView(buildHeader())
        root.addView(buildTeamBar())
        if (isTablet) {
            root.addView(buildTabletBody())
        } else {
            root.addView(buildPhoneBody())
        }
    }

    private fun buildHeader(): View = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, adaptiveDp(64, 52))
        background = GrimdarkDrawable(this@RosterActivity, ConsoleSurface.HEADER)
        setPadding(adaptiveDp(14, 12), 0, adaptiveDp(14, 12), 0)

        addView(ImageButton(this@RosterActivity).apply {
            background = GrimdarkSkins.button(this@RosterActivity, ConsoleSurface.CELL)
            contentDescription = "返回小队列表"
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(color(R.color.primary_light))
            scaleType = ImageView.ScaleType.CENTER
            setPadding(0, 0, 0, 0)
            setOnClickListener { handleBack() }
        }, FrameLayout.LayoutParams(adaptiveDp(42, 34), adaptiveDp(42, 34), Gravity.START or Gravity.CENTER_VERTICAL))

        addView(LinearLayout(this@RosterActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(label("杀戮小队编成", adaptiveSp(24f, 20f), R.color.text_primary, Typeface.BOLD))
            addView(label("成员与武器清单", adaptiveSp(10f, 9f), R.color.text_secondary, Typeface.BOLD).apply {
                letterSpacing = 0.18f
            })
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))

        saveButton = label("保存", adaptiveSp(14f, 12f), R.color.text_primary, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = GrimdarkSkins.button(
                this@RosterActivity,
                ConsoleSurface.GREEN,
                ConsoleSurface.GREEN_PRESSED
            )
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_save, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(color(R.color.text_primary))
            compoundDrawablePadding = adaptiveDp(7, 5)
            setOnClickListener { saveRoster() }
        }
        addView(saveButton, FrameLayout.LayoutParams(adaptiveDp(106, 90), adaptiveDp(42, 34), Gravity.END or Gravity.CENTER_VERTICAL))
    }

    private fun buildTeamBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), adaptiveDp(7, 4), dp(10), adaptiveDp(7, 4))
        background = GrimdarkDrawable(this@RosterActivity, ConsoleSurface.PANEL)

        teamButton = label("", 13f, R.color.primary_light, Typeface.BOLD).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = GrimdarkSkins.button(this@RosterActivity, ConsoleSurface.CELL)
            setPadding(adaptiveDp(12, 10), 0, adaptiveDp(12, 10), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_roster, 0, R.drawable.ic_dropdown, 0)
            compoundDrawableTintList = ColorStateList.valueOf(color(R.color.primary_light))
            compoundDrawablePadding = adaptiveDp(8, 5)
            setOnClickListener { showTeamChooser() }
        }
        addView(teamButton, LinearLayout.LayoutParams(0, adaptiveDp(48, 38), if (isTablet) 0.78f else 0.9f).apply {
            marginEnd = dp(6)
        })

            addView(referenceUi.link("规则 ›") { BattleReference(referenceUi).faction(currentTeam.id, includeComposition = false) }, LinearLayout.LayoutParams(dp(60), adaptiveDp(48, 38)))
        rosterNameInput = EditText(this@RosterActivity).apply {
            setSingleLine(true)
            hint = "给你的小队命名"
            setHintTextColor(color(R.color.text_hint))
            setTextColor(color(R.color.text_primary))
            textSize = 13f
            typeface = appTypeface(Typeface.BOLD)
            background = panelBackground(Color.rgb(18, 16, 13), Color.rgb(104, 82, 42), 7f)
            setPadding(adaptiveDp(13, 10), 0, adaptiveDp(13, 10), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!suppressNameChange) markDirty()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        addView(rosterNameInput, LinearLayout.LayoutParams(0, adaptiveDp(48, 38), 1.1f).apply {
            marginEnd = dp(8)
        })

        saveStateText = label("", adaptiveSp(11f, 10f), R.color.text_secondary, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            if (!isTablet) visibility = View.GONE
        }
        addView(saveStateText, LinearLayout.LayoutParams(if (isTablet) dp(104) else 0, adaptiveDp(48, 38)))
    }

    private fun buildTabletBody(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(9), dp(3), dp(9), dp(9))
        catalogPanel = buildCatalogPanel()
        rosterPanel = buildRosterPanel()
        addView(catalogPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.25f).apply {
            marginEnd = dp(5)
        })
        addView(rosterPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.88f).apply {
            marginStart = dp(5)
        })
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    private fun buildPhoneBody(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(7), dp(2), dp(7), dp(7))

        addView(LinearLayout(this@RosterActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            catalogTab = createTab("成员卡库", false) { showPhonePanel(false) }
            rosterTab = createTab("我的小队", true) { showPhonePanel(true) }
            addView(catalogTab, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(3) })
            addView(rosterTab, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(3) })
        })

        addView(FrameLayout(this@RosterActivity).apply {
            catalogPanel = buildCatalogPanel()
            rosterPanel = buildRosterPanel()
            addView(catalogPanel)
            addView(rosterPanel)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(6)
        })
        showPhonePanel(false)
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    private fun buildCatalogPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GrimdarkDrawable(this@RosterActivity, ConsoleSurface.PANEL)
        setPadding(adaptiveDp(9, 7), adaptiveDp(8, 5), adaptiveDp(9, 7), adaptiveDp(9, 7))

        addView(LinearLayout(this@RosterActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("可选成员卡", adaptiveSp(18f, 15f), R.color.primary_light, Typeface.BOLD), LinearLayout.LayoutParams(0, adaptiveDp(40, 30), 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            })
            catalogCountText = label("", adaptiveSp(11f, 10f), R.color.text_secondary, Typeface.BOLD).apply { gravity = Gravity.CENTER }
            addView(catalogCountText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, adaptiveDp(40, 30)))
        })

        catalogRuleText = label("", adaptiveSp(11f, 10f), R.color.text_steel).apply {
            setPadding(0, 0, 0, adaptiveDp(7, 5))
        }
        addView(catalogRuleText)

        addView(ScrollView(this@RosterActivity).apply {
            isFillViewport = true
            catalogContent = LinearLayout(this@RosterActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(catalogContent)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }.also {
        it.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun buildRosterPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GrimdarkDrawable(this@RosterActivity, ConsoleSurface.PANEL)
        setPadding(adaptiveDp(9, 7), adaptiveDp(8, 5), adaptiveDp(9, 7), adaptiveDp(9, 7))

        addView(LinearLayout(this@RosterActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("当前小队", adaptiveSp(18f, 15f), R.color.primary_light, Typeface.BOLD), LinearLayout.LayoutParams(0, adaptiveDp(40, 30), 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            })
            rosterCountText = label("", adaptiveSp(11f, 10f), R.color.normal_success, Typeface.BOLD).apply { gravity = Gravity.CENTER }
            addView(rosterCountText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, adaptiveDp(40, 30)))
        })

        addView(ScrollView(this@RosterActivity).apply {
            isFillViewport = true
            rosterContent = LinearLayout(this@RosterActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(rosterContent)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }.also {
        it.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun renderAll() {
        teamButton.text = currentTeam.name
        suppressNameChange = true
        rosterNameInput.setText(currentRoster.name)
        suppressNameChange = false
        dirty = false
        renderCatalog()
        renderRoster()
        updateSaveState()
    }

    private fun renderCatalog() {
        catalogContent.removeAllViews()
        catalogCountText.text = "${currentTeam.operatives.size} 种 · 直接配置"
        catalogRuleText.text = RosterComposition.hint(currentTeam.id)

        val compositionCards = assets.list("team_rules").orEmpty()
            .filter { it.startsWith("${currentTeam.id}--") && it.contains("-小队选择-") }
            .sorted()
        if(compositionCards.isNotEmpty()) {
            catalogContent.addView(label("编成规则原卡", adaptiveSp(16f, 13f), R.color.primary_light, Typeface.BOLD).apply {
                setPadding(0, 0, 0, adaptiveDp(7, 5))
            })
            referenceUi.tiles(catalogContent, compositionCards) { tile, file ->
                val title = file.substringAfter("-小队选择-").removeSuffix(".png")
                tile.addView(referenceUi.text(title, 15f, referenceUi.accent, true), referenceUi.wrap())
                referenceUi.preview(tile, title, "team_rules/$file", expandable = false)
            }
            catalogContent.addView(label("可选成员原卡", adaptiveSp(16f, 13f), R.color.primary_light, Typeface.BOLD).apply {
                setPadding(0, adaptiveDp(5, 3), 0, adaptiveDp(7, 5))
            })
        }
        val columns = if (isTablet) 2 else 1
        currentTeam.operatives.chunked(columns).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEachIndexed { index, operative ->
                row.addView(createOperativeCard(operative), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = adaptiveDp(5, 4)
                    if (index < columns - 1) marginEnd = adaptiveDp(5, 4)
                })
            }
            if (chunk.size < columns) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = adaptiveDp(5, 4) })
            }
            catalogContent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = adaptiveDp(9, 6)
            })
        }
    }

    private fun createOperativeCard(operative: OperativeTemplate): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBackground()
        elevation = adaptiveDp(3, 2).toFloat()
        setPadding(adaptiveDp(7, 5), adaptiveDp(7, 5), adaptiveDp(7, 5), adaptiveDp(7, 5))

        addView(LinearLayout(this@RosterActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(operative.name, adaptiveSp(18f, 14f), R.color.text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, adaptiveDp(36, 28), 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            })
            addView(label(operative.role, adaptiveSp(10f, 9f), R.color.primary_light, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                background = pillBackground(Color.argb(130, 122, 101, 32), Color.rgb(177, 135, 50))
                setPadding(adaptiveDp(9, 7), 0, adaptiveDp(9, 7), 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, adaptiveDp(25, 21)))
        })

        addView(label(
            "APL ${operative.apl}   移动 ${operative.move}   豁免 ${operative.save}   耐伤 ${operative.wounds}",
            adaptiveSp(12f, 10f),
            R.color.normal_success,
            Typeface.BOLD
        ).apply { setPadding(0, 0, 0, adaptiveDp(5, 4)) })

        val previewNames = operative.weapons.take(3).joinToString(" · ") { it.name }
        val more = (operative.weapons.size - 3).takeIf { it > 0 }?.let { " · 另 $it 项" }.orEmpty()
        addView(label("武器  $previewNames$more", adaptiveSp(11f, 10f), R.color.text_steel).apply {
            maxLines = 2
            minHeight = adaptiveDp(34, 26)
        })

        referenceUi.preview(this, operative.name, resource = operative.cardRes, expandable = false)

        addView(LinearLayout(this@RosterActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(label("配置加入  +", adaptiveSp(12f, 10f), R.color.primary_light, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                background = GrimdarkSkins.button(this@RosterActivity, ConsoleSurface.SELECTED)
                setOnClickListener { showMemberEditor(operative, null) }
            }, LinearLayout.LayoutParams(0, adaptiveDp(36, 30), 1.28f).apply { marginStart = dp(3) })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, adaptiveDp(36, 30)).apply {
            topMargin = adaptiveDp(6, 4)
        })

    }

    private fun renderRoster() {
        rosterContent.removeAllViews()
        rosterCountText.text = "${currentRoster.members.size} 名成员"
        rosterTab?.text = "我的小队  ${currentRoster.members.size}"
        if (currentRoster.members.isEmpty()) {
            rosterContent.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(18), adaptiveDp(70, 45), dp(18), adaptiveDp(30, 20))
                addView(ImageView(this@RosterActivity).apply {
                    setImageResource(R.drawable.ic_roster)
                    imageTintList = ColorStateList.valueOf(color(R.color.text_hint))
                }, LinearLayout.LayoutParams(adaptiveDp(62, 46), adaptiveDp(62, 46)))
                addView(label("小队还是空的", adaptiveSp(20f, 16f), R.color.text_secondary, Typeface.BOLD).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(5))
                })
                addView(label("从成员卡库选择角色，配置武器后加入。\n同一成员可重复加入，合法性由玩家自行判断。", adaptiveSp(12f, 10f), R.color.text_hint).apply {
                    gravity = Gravity.CENTER
                })
                if (!isTablet) {
                    addView(label("去选择成员", 13f, R.color.primary_light, Typeface.BOLD).apply {
                        gravity = Gravity.CENTER
                        background = GrimdarkSkins.button(this@RosterActivity, ConsoleSurface.SELECTED)
                        setOnClickListener { showPhonePanel(false) }
                    }, LinearLayout.LayoutParams(dp(150), dp(42)).apply { topMargin = dp(18) })
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }

        currentRoster.members.forEachIndexed { index, member ->
            val operative = RosterCatalog.operative(currentTeam, member.operativeId) ?: return@forEachIndexed
            rosterContent.addView(createMemberCard(index, member, operative), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = adaptiveDp(8, 6) })
        }
    }

    private fun createMemberCard(index: Int, member: RosterMember, operative: OperativeTemplate): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground(strong = true)
            setPadding(adaptiveDp(7, 5), adaptiveDp(7, 5), adaptiveDp(7, 5), adaptiveDp(7, 5))
            elevation = adaptiveDp(3, 2).toFloat()

            addView(LinearLayout(this@RosterActivity).apply {
                gravity = Gravity.CENTER_VERTICAL

                addView(FrameLayout(this@RosterActivity).apply {
                    background = GrimdarkSkins.button(this@RosterActivity, ConsoleSurface.CELL)
                    setOnClickListener { showCardOverlay(operative) }
                    addView(referenceUi.memberPortrait(member.customAvatarUri, operative.cardRes, 72).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        alpha = if (member.customAvatarUri.isNullOrBlank()) .34f else 1f
                    })
                    addView(label("查看卡片", adaptiveSp(10f, 9f), R.color.primary_light, Typeface.BOLD).apply {
                        gravity = Gravity.CENTER
                        setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_card, 0, 0)
                        background = ColorDrawable(Color.argb(118, 0, 0, 0))
                        compoundDrawableTintList = ColorStateList.valueOf(color(R.color.primary_light))
                        compoundDrawablePadding = adaptiveDp(2, 1)
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    addView(label((index + 1).toString().padStart(2, '0'), adaptiveSp(11f, 9f), R.color.text_primary, Typeface.BOLD).apply {
                        gravity = Gravity.CENTER
                        background = pillBackground(Color.argb(220, 92, 0, 0), Color.rgb(190, 90, 50))
                    }, FrameLayout.LayoutParams(adaptiveDp(29, 24), adaptiveDp(24, 20), Gravity.START or Gravity.TOP))
                }, LinearLayout.LayoutParams(adaptiveDp(124, 88), adaptiveDp(72, 58)).apply { marginEnd = adaptiveDp(9, 6) })

                addView(LinearLayout(this@RosterActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(member.callsign.ifBlank { operative.name }, adaptiveSp(17f, 14f), R.color.text_primary, Typeface.BOLD))
                    addView(label(
                        if (member.callsign.isBlank()) operative.role else "${operative.name} · ${operative.role}",
                        adaptiveSp(11f, 10f),
                        R.color.primary_light,
                        Typeface.BOLD
                    ))
                    addView(label(
                        "APL ${operative.apl} · M ${operative.move} · S ${operative.save} · W ${operative.wounds}",
                        adaptiveSp(11f, 10f),
                        R.color.normal_success,
                        Typeface.BOLD
                    ).apply { setPadding(0, adaptiveDp(4, 2), 0, 0) })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })

            val selectedWeapons = operative.weapons
                .filter { it.id in member.weaponIds }
                .joinToString(" · ") { it.name }
                .ifBlank { "未选择武器" }
            addView(label("武器  $selectedWeapons", adaptiveSp(11f, 10f), R.color.text_steel).apply {
                setPadding(dp(2), adaptiveDp(7, 5), dp(2), adaptiveDp(7, 5))
                maxLines = 3
            })

            addView(LinearLayout(this@RosterActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(memberAction("编辑", R.color.primary_light) { showMemberEditor(operative, member) }, actionParams(1f, end = 3))
                addView(memberAction("复制", R.color.normal_success) { duplicateMember(index, member) }, actionParams(1f, start = 3, end = 3))
                addView(memberAction("删除", R.color.failure_result) { confirmDelete(member, operative) }, actionParams(1f, start = 3))
            })
        }

    private fun showMemberEditor(operative: OperativeTemplate, existing: RosterMember?) {
        val selected = (existing?.weaponIds ?: operative.defaultWeaponIds).toMutableSet()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(adaptiveDp(16, 12), adaptiveDp(8, 6), adaptiveDp(16, 12), adaptiveDp(4, 3))
        }
        content.addView(sectionTitle("成员原卡"))
        referenceUi.preview(content, operative.name, resource = operative.cardRes, expandable = false)
        content.addView(label(operative.name, adaptiveSp(21f, 18f), R.color.text_primary, Typeface.BOLD).apply {
            setPadding(0, adaptiveDp(10, 7), 0, 0)
        })
        content.addView(label(
            "${operative.role} · APL ${operative.apl} · 移动 ${operative.move} · 豁免 ${operative.save} · 耐伤 ${operative.wounds}",
            adaptiveSp(12f, 11f),
            R.color.normal_success,
            Typeface.BOLD
        ).apply { setPadding(0, 0, 0, adaptiveDp(10, 7)) })

        content.addView(sectionTitle("成员称号（可选）"))
        val callsignInput = EditText(this).apply {
            setSingleLine(true)
            hint = "例如：蓝盔、队长阿尔法"
            setText(existing?.callsign.orEmpty())
            setTextColor(color(R.color.text_primary))
            setHintTextColor(color(R.color.text_hint))
            textSize = adaptiveSp(14f, 13f)
            background = panelBackground(Color.rgb(18, 16, 13), Color.rgb(96, 76, 40), 6f)
            setPadding(adaptiveDp(12, 10), 0, adaptiveDp(12, 10), 0)
        }
        content.addView(callsignInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, adaptiveDp(46, 38)).apply {
            bottomMargin = adaptiveDp(10, 7)
        })

        content.addView(sectionTitle("棋子照片"))
        var avatarUri = existing?.customAvatarUri
        val avatarPreview = referenceUi.memberPortrait(avatarUri, operative.cardRes, 68).apply {
            layoutParams = LinearLayout.LayoutParams(adaptiveDp(68, 56), adaptiveDp(68, 56)).apply {
                marginEnd = adaptiveDp(10, 8)
            }
            contentDescription = "${operative.name}棋子照片"
        }
        val avatarStatus = label(
            if (avatarUri.isNullOrBlank()) "尚未设置，当前显示成员原图" else "已设置棋子照片",
            adaptiveSp(12f, 10f), R.color.text_steel, Typeface.BOLD
        )
        val removePhoto = label("移除", adaptiveSp(11f, 10f), R.color.failure_result, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = GrimdarkSkins.button(this@RosterActivity, ConsoleSurface.CELL)
        }
        fun showAvatar(value: String?) {
            avatarUri = value
            val loaded = value?.let { runCatching {
                avatarPreview.setImageURI(Uri.parse(it)); avatarPreview.drawable != null
            }.getOrDefault(false) } ?: false
            if (!loaded) avatarPreview.setImageResource(operative.cardRes)
            avatarStatus.text = if (loaded) "已设置棋子照片" else "尚未设置，当前显示成员原图"
            removePhoto.visibility = if (loaded) View.VISIBLE else View.GONE
        }
        removePhoto.setOnClickListener { showAvatar(null) }
        removePhoto.visibility = if (avatarUri.isNullOrBlank()) View.GONE else View.VISIBLE
        content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panelBackground(Color.rgb(15, 14, 12), Color.rgb(74, 61, 38), 6f)
            setPadding(adaptiveDp(10, 8), adaptiveDp(8, 6), adaptiveDp(10, 8), adaptiveDp(8, 6))
            addView(avatarPreview)
            addView(LinearLayout(this@RosterActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(avatarStatus, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = adaptiveDp(5, 3)
                })
                addView(LinearLayout(this@RosterActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(label("拍摄", adaptiveSp(12f, 10f), R.color.primary_light, Typeface.BOLD).apply {
                        gravity = Gravity.CENTER
                        background = GrimdarkSkins.button(this@RosterActivity, ConsoleSurface.SELECTED)
                        setOnClickListener { capturePhoto(::showAvatar) }
                    }, LinearLayout.LayoutParams(0, adaptiveDp(38, 32), 1f).apply { marginEnd = adaptiveDp(4, 3) })
                    addView(label("相册导入", adaptiveSp(12f, 10f), R.color.primary_light, Typeface.BOLD).apply {
                        gravity = Gravity.CENTER
                        background = GrimdarkSkins.button(this@RosterActivity, ConsoleSurface.CELL)
                        setOnClickListener { choosePhoto(::showAvatar) }
                    }, LinearLayout.LayoutParams(0, adaptiveDp(38, 32), 1f).apply { marginEnd = adaptiveDp(4, 3) })
                    addView(removePhoto, LinearLayout.LayoutParams(0, adaptiveDp(38, 32), .72f))
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = adaptiveDp(12, 8)
        })

        content.addView(sectionTitle("武器与档案（由玩家自行判断搭配）"))
        operative.weapons.forEach { weapon ->
            content.addView(LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                addView(CheckBox(this@RosterActivity).apply {
                    text = weapon.name
                    setTextColor(color(R.color.text_primary))
                    textSize = adaptiveSp(12f, 11f)
                    typeface = appTypeface(Typeface.BOLD)
                    buttonTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(color(R.color.primary_light), color(R.color.text_hint))
                    )
                    setPadding(0, adaptiveDp(3, 1), 0, 0)
                    isChecked = weapon.id in selected
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selected.add(weapon.id) else selected.remove(weapon.id)
                    }
                })
                addView(WeaponRulesUi.profile(
                    referenceUi,weapon.name,weapon.profile,weapon.stats.keywords,
                    adaptiveSp(12f,10f),color(R.color.text_steel)
                ).apply {
                    setPadding(adaptiveDp(48,40),0,0,adaptiveDp(5,3))
                })
            })
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "配置并加入小队" else "编辑成员")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("取消", null)
            .setPositiveButton(if (existing == null) "加入" else "保存", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updated = RosterMember(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    operativeId = operative.id,
                    callsign = callsignInput.text.toString().trim(),
                    weaponIds = operative.weapons.map { it.id }.filter { it in selected },
                    customAvatarUri = avatarUri
                )
                if (existing == null) {
                    currentRoster.members.add(updated)
                } else {
                    val index = currentRoster.members.indexOfFirst { it.id == existing.id }
                    if (index >= 0) currentRoster.members[index] = updated
                }
                markDirty()
                renderRoster()
                if (!isTablet) showPhonePanel(true)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showCardOverlay(operative: OperativeTemplate) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground(Color.rgb(18, 16, 13), Color.rgb(177, 135, 50), 12f)
            elevation = adaptiveDp(18, 12).toFloat()
            setPadding(adaptiveDp(10, 8), adaptiveDp(8, 6), adaptiveDp(10, 8), adaptiveDp(10, 8))

            addView(LinearLayout(this@RosterActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label("${operative.name} · ", adaptiveSp(17f, 15f), R.color.primary_light, Typeface.BOLD), LinearLayout.LayoutParams(0, adaptiveDp(42, 34), 1f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                })
                addView(label("×", adaptiveSp(24f, 20f), R.color.text_primary, Typeface.BOLD).apply {
                    gravity = Gravity.CENTER
                    background = GrimdarkSkins.button(this@RosterActivity, ConsoleSurface.CELL)
                    contentDescription = ""
                    setOnClickListener { dialog.dismiss() }
                }, LinearLayout.LayoutParams(adaptiveDp(46, 38), adaptiveDp(38, 32)))
            })

            addView(ImageView(this@RosterActivity).apply {
                setImageResource(operative.cardRes)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "${operative.name}"
                maxHeight = resources.displayMetrics.heightPixels - dp(if (isTablet) 130 else 210)
                background = ColorDrawable(Color.BLACK)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dialog.setContentView(panel)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.82f }
        }
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                if (isTablet) (resources.displayMetrics.widthPixels * 0.78f).roundToInt()
                else (resources.displayMetrics.widthPixels * 0.95f).roundToInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    private fun duplicateMember(index: Int, member: RosterMember) {
        val duplicate = member.copy(
            id = UUID.randomUUID().toString(),
            callsign = member.callsign.takeIf { it.isNotBlank() }?.let { "$it · 副本" }.orEmpty()
        )
        currentRoster.members.add(index + 1, duplicate)
        markDirty()
        renderRoster()
        Snackbar.make(root, "已复制成员，可继续编辑配置", Snackbar.LENGTH_SHORT).show()
    }

    private fun confirmDelete(member: RosterMember, operative: OperativeTemplate) {
        MaterialAlertDialogBuilder(this)
            .setTitle("移除成员？")
            .setMessage("将从当前小队移除“${member.callsign.ifBlank { operative.name }}”。")
            .setNegativeButton("取消", null)
            .setPositiveButton("移除") { _, _ ->
                currentRoster.members.removeAll { it.id == member.id }
                markDirty()
                renderRoster()
            }
            .show()
    }

    private fun showTeamChooser() {
        if (currentRoster.updatedAt > 0L) {
            Snackbar.make(root, "已保存的小队不能更换卡库；请新建另一支小队", Snackbar.LENGTH_LONG).show()
            return
        }
        val names = RosterCatalog.teams.map { "${it.name}  ·  ${it.operatives.size} 种成员" }.toTypedArray()
        val selectedIndex = RosterCatalog.teams.indexOfFirst { it.id == currentTeam.id }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("选择杀戮小队卡库")
            .setSingleChoiceItems(names, selectedIndex) { picker, index ->
                picker.dismiss()
                requestTeamSwitch(RosterCatalog.teams[index])
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
    }

    private fun requestTeamSwitch(target: KillTeamCatalog) {
        if (target.id == currentTeam.id) return
        if (!dirty) {
            switchTeam(target)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("当前修改尚未保存")
            .setMessage("切换卡库前，要保存“${rosterNameInput.text}”吗？")
            .setNeutralButton("取消", null)
            .setNegativeButton("放弃并切换") { _, _ -> switchTeam(target) }
            .setPositiveButton("保存并切换") { _, _ ->
                persistRoster(showMessage = false)
                switchTeam(target)
            }
            .show()
    }

    private fun switchTeam(target: KillTeamCatalog) {
        currentTeam = target
        store.setSelectedTeam(target.id)
        currentRoster = if (currentRoster.updatedAt > 0L) {
            store.createDraft(target)
        } else {
            currentRoster.copy(
                teamId = target.id,
                name = "我的${target.name}小队",
                members = mutableListOf()
            )
        }
        showingRosterOnPhone = false
        renderAll()
        if (!isTablet) showPhonePanel(false)
    }

    private fun saveRoster() = persistRoster(showMessage = true)

    private fun persistRoster(showMessage: Boolean) {
        currentRoster = store.save(currentRoster.copy(
            name = rosterNameInput.text.toString().trim().ifBlank { "我的${currentTeam.name}小队" },
            updatedAt = System.currentTimeMillis()
        ))
        dirty = false
        updateSaveState()
        if (showMessage) Snackbar.make(root, "小队已保存在此设备", Snackbar.LENGTH_SHORT).show()
    }

    private fun markDirty() {
        if (!dirty) {
            dirty = true
            updateSaveState()
        }
    }

    private fun updateSaveState() {
        saveStateText.text = if (dirty) {
            "● 有未保存修改"
        } else if (currentRoster.updatedAt > 0L) {
            val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(currentRoster.updatedAt))
            "✓ 已保存 $time"
        } else {
            "尚未保存"
        }
        saveStateText.setTextColor(color(if (dirty) R.color.critical_result else R.color.text_secondary))
        saveButton.alpha = if (dirty) 1f else 0.78f
    }

    private fun showPhonePanel(showRoster: Boolean) {
        if (isTablet || !::catalogPanel.isInitialized || !::rosterPanel.isInitialized) return
        showingRosterOnPhone = showRoster
        catalogPanel.visibility = if (showRoster) View.GONE else View.VISIBLE
        rosterPanel.visibility = if (showRoster) View.VISIBLE else View.GONE
        catalogTab?.background = GrimdarkSkins.button(
            this,
            if (!showRoster) ConsoleSurface.SELECTED else ConsoleSurface.CELL
        )
        rosterTab?.background = GrimdarkSkins.button(
            this,
            if (showRoster) ConsoleSurface.SELECTED else ConsoleSurface.CELL
        )
        catalogTab?.setTextColor(color(if (!showRoster) R.color.primary_light else R.color.text_steel))
        rosterTab?.setTextColor(color(if (showRoster) R.color.primary_light else R.color.text_steel))
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
    }

    private fun handleBack() {
        if (!isTablet && showingRosterOnPhone) {
            showPhonePanel(false)
            return
        }
        if (!dirty) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("保存小队修改？")
            .setMessage("返回骰盘前，可以先把当前名单保存在设备上。")
            .setNeutralButton("取消", null)
            .setNegativeButton("不保存") { _, _ -> finish() }
            .setPositiveButton("保存并返回") { _, _ ->
                persistRoster(showMessage = false)
                finish()
            }
            .show()
    }

    private fun createTab(text: String, selected: Boolean, action: () -> Unit): TextView =
        label(text, 13f, if (selected) R.color.primary_light else R.color.text_steel, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = GrimdarkSkins.button(
                this@RosterActivity,
                if (selected) ConsoleSurface.SELECTED else ConsoleSurface.CELL
            )
            setOnClickListener { action() }
        }

    private fun memberAction(text: String, colorRes: Int, action: () -> Unit): TextView =
        label(text, adaptiveSp(11f, 10f), colorRes, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = GrimdarkSkins.button(this@RosterActivity, ConsoleSurface.CELL)
            setOnClickListener { action() }
        }

    private fun actionParams(weight: Float, start: Int = 0, end: Int = 0) =
        LinearLayout.LayoutParams(0, adaptiveDp(38, 30), weight).apply {
            marginStart = dp(start)
            marginEnd = dp(end)
        }

    private fun sectionTitle(text: String): TextView =
        label(text, adaptiveSp(13f, 11f), R.color.primary_light, Typeface.BOLD).apply {
            setPadding(0, adaptiveDp(4, 3), 0, adaptiveDp(5, 4))
        }

    private fun label(
        text: String,
        size: Float,
        colorRes: Int,
        style: Int = Typeface.NORMAL
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color(colorRes))
        typeface = appTypeface(style)
        includeFontPadding = false
    }

    private fun appTypeface(style: Int): Typeface =
        Typeface.create(ResourcesCompat.getFont(this, R.font.teko_variable), style)

    private fun cardBackground(strong: Boolean = false): GradientDrawable = panelBackground(
        fill = if (strong) Color.rgb(24, 22, 18) else Color.rgb(20, 19, 16),
        stroke = if (strong) Color.rgb(145, 103, 40) else Color.rgb(91, 70, 38),
        radius = 8f
    )

    private fun pillBackground(fill: Int, stroke: Int): GradientDrawable =
        panelBackground(fill, stroke, 12f)

    private fun panelBackground(fill: Int, stroke: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(radius).toFloat()
        }

    private fun color(resource: Int): Int = ResourcesCompat.getColor(resources, resource, theme)

    /** Keeps phone touch targets unchanged while using a denser console layout on tablets. */
    private fun adaptiveDp(phone: Int, tablet: Int): Int = dp(if (isTablet) tablet else phone)

    private fun adaptiveSp(phone: Float, tablet: Float): Float = if (isTablet) tablet else phone

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_ROSTER_ID = "roster_id"
        const val EXTRA_CREATE_TEAM_ID = "create_team_id"
    }
}
