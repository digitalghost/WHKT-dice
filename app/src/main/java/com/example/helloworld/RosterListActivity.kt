package com.example.helloworld

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class RosterListActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var listContent: LinearLayout
    private lateinit var countText: TextView
    private lateinit var store: RosterStore

    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersiveMode()
        store = RosterStore(this)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        renderRosters()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun enterImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GrimdarkDrawable(this@RosterListActivity, ConsoleSurface.ROOT)
        }
        setContentView(root)
        root.addView(buildHeader())
        root.addView(buildBody(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun buildHeader(): View = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, adaptiveDp(64, 52))
        background = GrimdarkDrawable(this@RosterListActivity, ConsoleSurface.HEADER)
        setPadding(adaptiveDp(14, 12), 0, adaptiveDp(14, 12), 0)

        addView(ImageButton(this@RosterListActivity).apply {
            background = GrimdarkSkins.button(this@RosterListActivity, ConsoleSurface.CELL)
            contentDescription = "返回"
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(color(R.color.primary_light))
            scaleType = ImageView.ScaleType.CENTER
            setPadding(0, 0, 0, 0)
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(adaptiveDp(42, 34), adaptiveDp(42, 34), Gravity.START or Gravity.CENTER_VERTICAL))

        addView(LinearLayout(this@RosterListActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(label("我的小队", adaptiveSp(24f, 20f), R.color.text_primary, Typeface.BOLD))
            addView(label("保存双方可选编成", adaptiveSp(10f, 9f), R.color.text_secondary, Typeface.BOLD).apply {
                letterSpacing = 0.15f
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))

        addView(label("＋ 新建", adaptiveSp(14f, 11f), R.color.text_primary, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = GrimdarkSkins.button(this@RosterListActivity, ConsoleSurface.GREEN, ConsoleSurface.GREEN_PRESSED)
            setOnClickListener { showCreateChooser() }
        }, FrameLayout.LayoutParams(adaptiveDp(106, 82), adaptiveDp(42, 34), Gravity.END or Gravity.CENTER_VERTICAL))
    }

    private fun buildBody(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(adaptiveDp(8, 12), adaptiveDp(7, 8), adaptiveDp(8, 12), adaptiveDp(8, 12))

        addView(LinearLayout(this@RosterListActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("全部名单", adaptiveSp(19f, 16f), R.color.primary_light, Typeface.BOLD), LinearLayout.LayoutParams(0, adaptiveDp(42, 34), 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            })
            countText = label("", adaptiveSp(11f, 10f), R.color.text_secondary, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            }
            addView(countText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, adaptiveDp(42, 34)))
        })

        addView(label("这里保存双方都可选择的编成；完整对局会在开始时同时选择两支小队。", adaptiveSp(11f, 10f), R.color.text_steel).apply {
            setPadding(0, 0, 0, adaptiveDp(8, 6))
        })

        addView(ScrollView(this@RosterListActivity).apply {
            isFillViewport = true
            listContent = LinearLayout(this@RosterListActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(listContent)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun renderRosters() {
        val rosters = store.all()
        countText.text = "${rosters.size} 支小队"
        listContent.removeAllViews()

        if (rosters.isEmpty()) {
            listContent.addView(buildEmptyState())
            return
        }

        val columns = if (isTablet) 2 else 1
        rosters.chunked(columns).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEachIndexed { index, roster ->
                row.addView(buildRosterCard(roster), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = dp(5)
                    if (index < columns - 1) marginEnd = dp(5)
                })
            }
            if (chunk.size < columns) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(5) })
            listContent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = adaptiveDp(9, 7)
            })
        }
    }

    private fun buildEmptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(20), adaptiveDp(90, 65), dp(20), dp(30))
        addView(ImageView(this@RosterListActivity).apply {
            setImageResource(R.drawable.ic_roster)
            imageTintList = ColorStateList.valueOf(color(R.color.text_hint))
        }, LinearLayout.LayoutParams(adaptiveDp(64, 48), adaptiveDp(64, 48)))
        addView(label("还没有保存的小队", adaptiveSp(21f, 17f), R.color.text_secondary, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(6))
        })
        addView(label("先选择一套卡库，然后组织你的第一支杀戮小队。", adaptiveSp(12f, 10f), R.color.text_hint).apply {
            gravity = Gravity.CENTER
        })
        addView(label("新建小队", adaptiveSp(14f, 11f), R.color.primary_light, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = GrimdarkSkins.button(this@RosterListActivity, ConsoleSurface.SELECTED)
            setOnClickListener { showCreateChooser() }
        }, LinearLayout.LayoutParams(adaptiveDp(170, 126), adaptiveDp(44, 34)).apply { topMargin = dp(18) })
    }

    private fun buildRosterCard(roster: SavedRoster): View {
        val team = RosterCatalog.team(roster.teamId)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground(Color.rgb(20, 19, 16), Color.rgb(108, 78, 36), 8f)
            elevation = adaptiveDp(3, 2).toFloat()
            setPadding(adaptiveDp(10, 8), adaptiveDp(9, 7), adaptiveDp(10, 8), adaptiveDp(9, 7))

            addView(LinearLayout(this@RosterListActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label(roster.name, adaptiveSp(19f, 15f), R.color.text_primary, Typeface.BOLD).apply {
                    maxLines = 1
                }, LinearLayout.LayoutParams(0, adaptiveDp(34, 28), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            })

            addView(label("${team.name}  ·  ${roster.members.size} 名成员", adaptiveSp(12f, 10f), R.color.primary_light, Typeface.BOLD).apply {
                setPadding(0, adaptiveDp(4, 2), 0, adaptiveDp(5, 3))
            })
            val updated = if (roster.updatedAt > 0L) {
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(roster.updatedAt))
            } else "尚未保存"
            addView(LinearLayout(this@RosterListActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label("最后修改  $updated", adaptiveSp(11f, 9f), R.color.text_steel).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                addView(iconAction(R.drawable.ic_edit, "编辑${roster.name}", R.color.primary_light) {
                    editRoster(roster)
                }, iconActionParams())
                addView(iconAction(R.drawable.ic_copy, "复制${roster.name}", R.color.normal_success) {
                    store.duplicate(roster.rosterId)
                    renderRosters()
                }, iconActionParams(start = 4))
                addView(iconAction(R.drawable.ic_delete, "删除${roster.name}", R.color.failure_result) {
                    confirmDelete(roster)
                }, iconActionParams(start = 4))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, adaptiveDp(44, 36)).apply {
                topMargin = adaptiveDp(7, 5)
            })
        }
    }

    private fun showCreateChooser() {
        val labels = RosterCatalog.teams.map { "${it.name}  ·  ${it.operatives.size} 种成员" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("选择新小队的卡库")
            .setItems(labels) { _, index ->
                startActivity(Intent(this, RosterActivity::class.java).apply {
                    putExtra(RosterActivity.EXTRA_CREATE_TEAM_ID, RosterCatalog.teams[index].id)
                })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun editRoster(roster: SavedRoster) {
        startActivity(Intent(this, RosterActivity::class.java).apply {
            putExtra(RosterActivity.EXTRA_ROSTER_ID, roster.rosterId)
        })
    }

    private fun confirmDelete(roster: SavedRoster) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除小队？")
            .setMessage("将删除“${roster.name}”及其全部成员配置。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                store.delete(roster.rosterId)
                renderRosters()
            }
            .show()
    }

    private fun iconAction(iconRes: Int, description: String, colorRes: Int, onClick: () -> Unit): ImageButton =
        ImageButton(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(color(colorRes))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(adaptiveDp(10, 8), adaptiveDp(10, 8), adaptiveDp(10, 8), adaptiveDp(10, 8))
            background = GrimdarkSkins.button(this@RosterListActivity, ConsoleSurface.CELL)
            contentDescription = description
            setOnClickListener { onClick() }
        }

    private fun iconActionParams(start: Int = 0) =
        LinearLayout.LayoutParams(adaptiveDp(44, 36), adaptiveDp(44, 36)).apply {
            marginStart = dp(start)
        }

    private fun label(text: String, size: Float, colorRes: Int, style: Int = Typeface.NORMAL): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color(colorRes))
            typeface = Typeface.create(ResourcesCompat.getFont(this@RosterListActivity, R.font.teko_variable), style)
            includeFontPadding = false
        }

    private fun panelBackground(fill: Int, stroke: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(radius).toFloat()
        }

    private fun color(resource: Int): Int = ResourcesCompat.getColor(resources, resource, theme)
    private fun adaptiveDp(phone: Int, tablet: Int): Int = dp(if (isTablet) tablet else phone)
    private fun adaptiveSp(phone: Float, tablet: Float): Float = if (isTablet) tablet else phone
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()
}
