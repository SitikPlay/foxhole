package com.sitikplay.foxhole

import android.Manifest
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import com.google.android.material.R as MR
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AdbMdns
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var out: TextView

    private var terminalInput: TextInputEditText? = null
    private var terminalExplain: TextView? = null
    private var terminalScroll: android.widget.ScrollView? = null
    private var historyRow: LinearLayout? = null
    private lateinit var appsBox: LinearLayout
    private lateinit var installedBox: LinearLayout
    private var installedAll: List<InstalledApp> = emptyList()
    private var installedLoaded = false
    private var installedQuery = ""
    private var statusCheckRunning = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val autoCheckTick = object : Runnable {
        override fun run() {
            if (Settings.autoCheck(this@MainActivity)) refreshStatus(notify = false)
            uiHandler.postDelayed(this, AUTO_CHECK_MS)
        }
    }
    private lateinit var statusDot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var stopButton: MaterialButton
    private lateinit var pairingFold: Fold
    private lateinit var pairButton: MaterialButton
    private lateinit var pairPortField: TextInputLayout
    private lateinit var pairCodeField: TextInputLayout
    private lateinit var uninstallBox: LinearLayout
    private lateinit var deletedBox: LinearLayout
    private lateinit var deletedList: LinearLayout
    private lateinit var paneRoot: View
    private lateinit var appsPaneRoot: View
    private lateinit var appsPaneScroll: ScrollView
    private lateinit var appsPaneTitle: TextView
    private lateinit var tabAccessContent: LinearLayout
    private lateinit var tabInstalledContent: LinearLayout
    private var showAppsTab = true
    private lateinit var paneTitle: TextView
    private lateinit var paneScroll: ScrollView
    private lateinit var paneBody: LinearLayout
    private lateinit var tabUninstallContent: LinearLayout
    private lateinit var tabDeletedContent: LinearLayout
    private var showUninstallTab = true
    private var uninstallAll: List<UninstallApp> = emptyList()
    private var uninstallLoaded = false
    private var uninstallQuery = ""
    private var uninstallFilter = FILTER_USER
    private val uninstallSelected = mutableSetOf<String>()
    private var systemWarnedSession = false

    private var pendingImportSet: Set<String>? = null

    private lateinit var suggestedBox: LinearLayout
    private lateinit var uninstallFooterButton: MaterialButton
    private var serverOnline: Boolean? = null
    private var pulse: ObjectAnimator? = null
    private lateinit var mainScroll: ScrollView
    private var wssStatusView: TextView? = null

    private var openSheet: BottomSheetDialog? = null

    private val saveSetLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) writeUninstallSet(uri)
        }
    private val importSetLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) readUninstallSet(uri)
        }

    private val hintColor: Int by lazy { themeColor(MR.attr.colorOnSurfaceVariant) }

    private val uiFont: Typeface by lazy {
        ResourcesCompat.getFont(this, R.font.inter) ?: Typeface.DEFAULT
    }
    private val codeFont: Typeface by lazy {
        ResourcesCompat.getFont(this, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
    }

    private fun TextView.appTypeface(weight: Int = 400, code: Boolean = false) {
        val base = if (code) codeFont else uiFont
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, weight, false)
        } else {
            Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun themeColor(attr: Int): Int {
        val tv = TypedValue()
        return if (theme.resolveAttribute(attr, tv, true)) tv.data else 0xFF888888.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        if (Settings.flagSecureScreens(this)) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                left = bars.left + 20.dp,
                top = bars.top + 16.dp,
                right = bars.right + 20.dp,
                bottom = maxOf(bars.bottom, ime.bottom) + 32.dp
            )
            insets
        }
        scroll.addView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.foxhole_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { cornerRadius = 13.dp.toFloat() }
            clipToOutline = true
        }
        header.addView(icon, LinearLayout.LayoutParams(44.dp, 44.dp).apply { marginEnd = 14.dp })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("Foxhole", 26f, bold = true))
        titles.addView(
            label(getString(R.string.t001), 13f)
                .apply { setTextColor(hintColor); setPadding(0, 2.dp, 0, 0) }
        )
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(settingsButton())
        root.addView(header)

        buildStatusCard(root)

        val pcFold = fold(root, getString(R.string.t002), expanded = true, locked = true)
        pcFold.content.addView(space(12))
        pcFold.content.addView(
            label(
                getString(R.string.t003),
                13f
            ).apply { setTextColor(hintColor) }
        )
        pcFold.content.addView(space(10))
        pcFold.content.addView(button(getString(R.string.t004)) { showPcCommandDialog() })
        pcFold.content.addView(space(12))

        pairingFold = fold(root, getString(R.string.t005), expanded = true, locked = true)
        pairingFold.content.addView(space(12))
        pairingFold.content.addView(
            label(
                getString(R.string.t006),
                13f
            ).apply { setTextColor(hintColor) }
        )
        pairingFold.content.addView(space(10))
        val openSettingsButton = button(getString(R.string.t007), outlined = true) {
            openWirelessDebugging()
        }
        pairingFold.content.addView(openSettingsButton)
        pairPortField = field(
            getString(R.string.t008),
            InputType.TYPE_CLASS_NUMBER,
            maxLength = 5,
            id = R.id.foxhole_pair_port
        ).apply {
            isHelperTextEnabled = true
            helperText = getString(R.string.t009)
        }
        pairCodeField = field(
            getString(R.string.t010), InputType.TYPE_CLASS_NUMBER, maxLength = 6,
            id = R.id.foxhole_pair_code
        )
        pairingFold.content.addView(space(12))
        pairingFold.content.addView(pairPortField)
        pairingFold.content.addView(space(8))
        pairingFold.content.addView(pairCodeField)
        pairingFold.content.addView(space(10))
        pairButton = button(getString(R.string.t011), outlined = true) { pairFromFields() }
        pairingFold.content.addView(pairButton)
        pairingFold.content.addView(
            label(
                getString(R.string.t012),
                12f
            ).apply { setTextColor(hintColor) }
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            pairingFold.content.addView(space(12))
            pairingFold.content.addView(
                label(
                    getString(R.string.t251, Build.VERSION.RELEASE), 13f
                ).apply { setTextColor(themeColor(MR.attr.colorError)) }
            )
            listOf(pairPortField, pairCodeField).forEach {
                it.isEnabled = false
                it.editText?.isEnabled = false
                it.alpha = 0.5f
            }
            pairPortField.helperText = getString(R.string.t013)
            openSettingsButton.isEnabled = false
            pairButton.isEnabled = false
            pairButton.alpha = 0.5f
        }

        val cmdField = field(
            getString(R.string.t014),
            InputType.TYPE_CLASS_TEXT,
            id = R.id.foxhole_cmd_input
        )

        cmdField.editText?.appTypeface(400, code = true)
        terminalInput = cmdField.editText as TextInputEditText
        val explainLabel = label("", 13f).apply {
            setTextColor(hintColor)
            setLineSpacing(3f, 1f)
            visibility = View.GONE
        }
        terminalExplain = explainLabel

        (cmdField.editText as TextInputEditText).addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    updateExplain()
                }
            }
        )
        section(
            root, getString(R.string.t015),
            getString(R.string.t016)
        ).apply {
            addView(space(12))
            addView(explainLabel)
            addView(space(8))
            addView(cmdField)
            addView(space(8))

            val histRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            historyRow = histRow
            addView(histRow)
            addView(space(8))
            addView(
                row(
                    button(getString(R.string.t017)) {
                        send(terminalInput?.text?.toString().orEmpty())
                    },
                    button(getString(R.string.t018), outlined = true) { out.text = "" }
                )
            )
            addView(space(16))

            val consoleHeader = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(label(getString(R.string.t019), 13f, bold = true))
                addView(
                    View(this@MainActivity),
                    LinearLayout.LayoutParams(0, 1, 1f)
                )
                addView(
                    TextView(this@MainActivity).apply {
                        text = getString(R.string.t020)
                        textSize = 13f
                        appTypeface(500)
                        setTextColor(themeColor(MR.attr.colorPrimary))
                        setPadding(12.dp, 6.dp, 4.dp, 6.dp)
                        setOnClickListener {
                            val text = out.text?.toString().orEmpty()
                            if (text.isBlank()) {
                                snack(getString(R.string.t021), error = true)
                            } else {
                                copyToClipboard(text)
                                snack(getString(R.string.t022))
                            }
                        }
                    }
                )
            }
            addView(consoleHeader)
            addView(space(8))

            out = TextView(this@MainActivity).apply {
                id = R.id.foxhole_output
                appTypeface(400, code = true)
                textSize = 12f
                freezesText = true
                setTextIsSelectable(true)
                setLineSpacing(4f, 1f)
                setPadding(14.dp, 14.dp, 14.dp, 14.dp)
                setTextColor(CONSOLE_TEXT)
                setHintTextColor(0xFF6B7280.toInt())
                hint = getString(R.string.t023)
                background = GradientDrawable().apply {
                    setColor(CONSOLE_BG)
                    cornerRadius = 14.dp.toFloat()
                }
            }
            val scroll = android.widget.ScrollView(this@MainActivity).apply {
                isVerticalScrollBarEnabled = true
                addView(
                    out,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            terminalScroll = scroll
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    300.dp
                )
            )
        }
        renderHistory()

        val appsTitle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(getString(R.string.t024), 17f, bold = true))
            addView(
                label(
                    getString(R.string.t025),
                    13f
                ).apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 0); setLineSpacing(3f, 1f) }
            )
        }
        val appsEntry = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            background = ripple()
            setPadding(20.dp, 20.dp, 20.dp, 20.dp)
            addView(appsTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                label("❯", 16f, bold = true).apply {
                    setTextColor(hintColor)
                    setPadding(14.dp, 0, 0, 0)
                }
            )
            setOnClickListener { openAppsPane() }
        }
        root.addView(
            cardFor(appsEntry),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 14.dp }
        )

        appsPaneRoot = buildAppsPane()
        appsPaneRoot.visibility = View.GONE

        val uninstallTitle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(getString(R.string.t026), 17f, bold = true))
            addView(
                label(
                    getString(R.string.t027),
                    13f
                ).apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 0); setLineSpacing(3f, 1f) }
            )
        }
        val uninstallEntry = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            background = ripple()
            setPadding(20.dp, 20.dp, 20.dp, 20.dp)
            addView(uninstallTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                label("❯", 16f, bold = true).apply {
                    setTextColor(hintColor)
                    setPadding(14.dp, 0, 0, 0)
                }
            )
            setOnClickListener { openUninstallPane() }
        }
        root.addView(
            cardFor(uninstallEntry),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 14.dp }
        )

        val profilesEntry = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            background = ripple()
            setPadding(20.dp, 20.dp, 20.dp, 20.dp)
        }
        val profilesTitle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(getString(R.string.t399), 17f, bold = true))
            addView(
                label(getString(R.string.t400), 13f).apply {
                    setTextColor(hintColor)
                    setPadding(0, 6.dp, 0, 0)
                    setLineSpacing(3f, 1f)
                }
            )
        }
        profilesEntry.addView(
            profilesTitle,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        profilesEntry.addView(
            label("❯", 16f, bold = true).apply {
                setTextColor(hintColor)
                setPadding(14.dp, 0, 0, 0)
            }
        )
        profilesEntry.setOnClickListener { showProfilesSheet() }
        root.addView(
            cardFor(profilesEntry),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 14.dp }
        )

        paneRoot = buildUninstallPane()
        paneRoot.visibility = View.GONE
        onBackPressedDispatcher.addCallback(this, paneBackCallback)
        onBackPressedDispatcher.addCallback(this, appsBackCallback)

        val journal = fold(root, getString(R.string.t028), expanded = Settings.foldExpanded(this, "journal", def = false), persistKey = "journal")
        val logView = TextView(this).apply {
            appTypeface(400, code = true)
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, 12.dp, 0, 0)
        }
        journal.content.addView(space(8))
        journal.content.addView(row(
            button(getString(R.string.t029)) {
                logView.text = AccessStore.readLog(this@MainActivity).ifBlank { getString(R.string.t030) }
            },
            button(getString(R.string.t031), outlined = true) {
                AccessStore.clearLog(this@MainActivity)
                logView.text = getString(R.string.t032)
            }
        ))
        journal.content.addView(logView)

        val about = fold(root, getString(R.string.t033), expanded = Settings.foldExpanded(this, "how", def = false), persistKey = "how")
        about.content.addView(space(8))
        about.content.addView(
            label(
                getString(R.string.t034),
                13f
            ).apply { setTextColor(hintColor) }
        )

        val guides = fold(root, getString(R.string.t035), expanded = Settings.foldExpanded(this, "guides", def = false), persistKey = "guides")
        guides.content.addView(space(12))
        guides.content.addView(label(getString(R.string.t036), 14f, bold = true))
        guides.content.addView(space(6))
        guides.content.addView(
            label(
                getString(R.string.t037),
                12f
            ).apply {
                setTextColor(hintColor)
                setTextIsSelectable(true)
                setLineSpacing(3f, 1f)
            }
        )
        guides.content.addView(space(20))
        guides.content.addView(label(getString(R.string.t038), 14f, bold = true))
        guides.content.addView(space(6))
        guides.content.addView(
            label(
                getString(R.string.t039),
                12f
            ).apply {
                setTextColor(hintColor)
                setTextIsSelectable(true)
                setLineSpacing(3f, 1f)
            }
        )
        guides.content.addView(space(12))
        guides.content.addView(button(getString(R.string.t040), outlined = true) {
            showAdbPairCommand()
        })
        guides.content.addView(space(8))
        guides.content.addView(
            label(
                getString(R.string.t041),
                12f
            ).apply { setTextColor(hintColor) }
        )

        val contentRoot = FrameLayout(this)
        contentRoot.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        contentRoot.addView(
            paneRoot,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        contentRoot.addView(
            appsPaneRoot,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(contentRoot)
        mainScroll = scroll
        maybeAutoStart(intent)
        maybeOpenPending(intent)

        if (!Settings.langChosen(this)) showLanguageDialog()
    }

    private val paneBackCallback = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeUninstallPane()
        }
    }

    private val appsBackCallback = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeAppsPane()
        }
    }

    private fun buildAppsPane(): View {
        val pane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor(MR.attr.colorSurface))
        }
        ViewCompat.setOnApplyWindowInsetsListener(pane) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            pane.updatePadding(
                left = bars.left, top = bars.top, right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom)
            )
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp, 14.dp, 20.dp, 14.dp)
        }
        appsPaneTitle = label(getString(R.string.t024), 19f, bold = true)
        header.addView(appsPaneTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(
            TextView(this).apply {
                text = "✕"
                textSize = 18f
                appTypeface(700)
                gravity = Gravity.CENTER
                setPadding(12.dp, 8.dp, 12.dp, 8.dp)
                isClickable = true
                background = ripple()
                setOnClickListener { closeAppsPane() }
            }
        )
        pane.addView(header)

        val tabs = MaterialButtonToggleGroup(this)
        val tabAccess = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_tab_access
            text = getString(R.string.t042); isCheckable = true; isAllCaps = false
            textSize = 13f; appTypeface(500)
        }
        val tabInstalled = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_tab_installed
            text = getString(R.string.t043); isCheckable = true; isAllCaps = false
            textSize = 13f; appTypeface(500)
        }
        tabs.addView(tabAccess)
        tabs.addView(tabInstalled)
        tabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showAppsTab = checkedId == R.id.foxhole_tab_access
            renderAppsPane()
        }
        tabs.check(R.id.foxhole_tab_access)
        pane.addView(tabs, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(20.dp, 6.dp, 20.dp, 6.dp) })

        appsPaneScroll = ScrollView(this).apply { clipToPadding = false }
        val appsBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 10.dp, 20.dp, 32.dp)
        }
        appsPaneScroll.addView(appsBody)
        pane.addView(appsPaneScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        ))

        tabAccessContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appsBody.addView(tabAccessContent)
        tabAccessContent.addView(space(2))
        tabAccessContent.addView(
            label(
                getString(R.string.t044),
                13f
            ).apply { setTextColor(hintColor); setLineSpacing(3f, 1f) }
        )
        tabAccessContent.addView(space(10))
        appsBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        tabAccessContent.addView(appsBox)

        tabInstalledContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appsBody.addView(tabInstalledContent)
        tabInstalledContent.addView(space(2))
        tabInstalledContent.addView(
            label(getString(R.string.t045), 13f)
                .apply { setTextColor(hintColor); setLineSpacing(3f, 1f) }
        )
        tabInstalledContent.addView(space(10))
        val search = field(getString(R.string.t046), InputType.TYPE_CLASS_TEXT)
        tabInstalledContent.addView(search)
        tabInstalledContent.addView(space(8))
        installedBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        tabInstalledContent.addView(installedBox)
        tabInstalledContent.addView(space(10))
        tabInstalledContent.addView(button(getString(R.string.t047), outlined = true) {
            installedLoaded = false
            loadInstalledApps()
        })
        search.editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                installedQuery = s?.toString().orEmpty()
                renderInstalled()
            }
        })

        return pane
    }

    private fun openAppsPane() {
        showAppsTab = true
        renderAppsPane()
        refreshApps()
        if (!installedLoaded) loadInstalledApps() else renderInstalled()
        appsBackCallback.isEnabled = true
        appsPaneRoot.visibility = View.VISIBLE
        appsPaneRoot.alpha = 0f
        appsPaneRoot.translationY = 48.dp.toFloat()
        appsPaneRoot.animate().alpha(1f).translationY(0f).setDuration(220)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .start()
    }

    private fun closeAppsPane() {
        appsBackCallback.isEnabled = false
        appsPaneRoot.visibility = View.GONE
    }

    private fun renderAppsPane() {
        if (!::tabAccessContent.isInitialized || !::tabInstalledContent.isInitialized ||
            !::appsPaneTitle.isInitialized || !::appsPaneScroll.isInitialized
        ) return
        appsPaneTitle.text = if (showAppsTab) getString(R.string.t048) else getString(R.string.t049)
        tabAccessContent.visibility = if (showAppsTab) View.VISIBLE else View.GONE
        tabInstalledContent.visibility = if (showAppsTab) View.GONE else View.VISIBLE
        appsPaneScroll.post { appsPaneScroll.smoothScrollTo(0, 0) }
    }
    private fun buildUninstallPane(): View {
        val pane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor(MR.attr.colorSurface))
        }
        ViewCompat.setOnApplyWindowInsetsListener(pane) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            pane.updatePadding(
                left = bars.left, top = bars.top, right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom)
            )
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp, 14.dp, 20.dp, 14.dp)
        }
        paneTitle = label(getString(R.string.t026), 19f, bold = true)
        header.addView(paneTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(
            TextView(this).apply {
                text = "✕"
                textSize = 18f
                appTypeface(700)
                gravity = Gravity.CENTER
                setPadding(12.dp, 8.dp, 12.dp, 8.dp)
                isClickable = true
                background = ripple()
                setOnClickListener { closeUninstallPane() }
            }
        )
        pane.addView(header)

        val tabs = MaterialButtonToggleGroup(this)
        val tabUninstall = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_tab_uninstall
            text = getString(R.string.t050); isCheckable = true; isAllCaps = false
            textSize = 13f; appTypeface(500)
        }
        val tabDeleted = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_tab_restore
            text = getString(R.string.t051); isCheckable = true; isAllCaps = false
            textSize = 13f; appTypeface(500)
        }
        tabs.addView(tabUninstall)
        tabs.addView(tabDeleted)
        tabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showUninstallTab = checkedId == R.id.foxhole_tab_uninstall
            renderUninstallPane()
        }
        tabs.check(R.id.foxhole_tab_uninstall)
        pane.addView(tabs, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(20.dp, 6.dp, 20.dp, 6.dp) })

        paneScroll = ScrollView(this).apply {
            clipToPadding = false
        }
        paneBody = LinearLayout(this).apply {
            id = R.id.foxhole_pane_body
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 10.dp, 20.dp, 32.dp)
        }
        paneScroll.addView(paneBody)
        pane.addView(paneScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        ))

        tabUninstallContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        paneBody.addView(tabUninstallContent)
        tabUninstallContent.addView(space(2))
        tabUninstallContent.addView(
            label(
                getString(R.string.t252, UninstallCommands.RETENTION_DAYS),
                13f
            ).apply { setTextColor(hintColor); setLineSpacing(3f, 1f) }
        )
        tabUninstallContent.addView(space(6))
        tabUninstallContent.addView(
            label(
                getString(R.string.t410),
                12f
            ).apply { setTextColor(hintColor); setLineSpacing(3f, 1f) }
        )
        tabUninstallContent.addView(space(10))
        val uninstallSearch = field(getString(R.string.t046), InputType.TYPE_CLASS_TEXT)
        tabUninstallContent.addView(uninstallSearch)
        tabUninstallContent.addView(space(8))

        val filterGroup = MaterialButtonToggleGroup(this)
        val btnUser = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_filter_user
            text = getString(R.string.t052); isCheckable = true; isAllCaps = false
            textSize = 12f; appTypeface(500)
        }
        val btnSystem = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_filter_system
            text = getString(R.string.t053); isCheckable = true; isAllCaps = false
            textSize = 12f; appTypeface(500)
        }
        val btnAll = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_filter_all
            text = getString(R.string.t054); isCheckable = true; isAllCaps = false
            textSize = 12f; appTypeface(500)
        }
        filterGroup.addView(btnUser)
        filterGroup.addView(btnSystem)
        filterGroup.addView(btnAll)
        filterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            uninstallFilter = when (checkedId) {
                R.id.foxhole_filter_user -> FILTER_USER
                R.id.foxhole_filter_system -> FILTER_SYSTEM
                else -> FILTER_ALL
            }
            renderUninstall()
        }
        filterGroup.check(R.id.foxhole_filter_user)
        tabUninstallContent.addView(filterGroup)
        tabUninstallContent.addView(space(10))

        uninstallBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        tabUninstallContent.addView(uninstallBox)
        tabUninstallContent.addView(space(10))
        tabUninstallContent.addView(
            row(
                button(getString(R.string.t055), outlined = true) { selectAllUninstall() },
                button(getString(R.string.t056), outlined = true) {
                    uninstallSelected.clear()
                    renderUninstall()
                }
            )
        )
        tabUninstallContent.addView(space(10))
        uninstallFooterButton = button(getString(R.string.t057)) { confirmUninstall() }
        tabUninstallContent.addView(uninstallFooterButton)
        tabUninstallContent.addView(space(8))
        tabUninstallContent.addView(button(getString(R.string.t047), outlined = true) {
            uninstallLoaded = false
            loadUninstallApps()
        })
        tabUninstallContent.addView(space(8))
        tabUninstallContent.addView(
            row(
                button(getString(R.string.t414), outlined = true) { saveUninstallSet() },
                button(getString(R.string.t415), outlined = true) { importSet() }
            )
        )

        tabUninstallContent.addView(space(16))
        tabUninstallContent.addView(divider())
        tabUninstallContent.addView(space(12))
        tabUninstallContent.addView(label(getString(R.string.t411), 15f, bold = true))
        tabUninstallContent.addView(space(4))
        tabUninstallContent.addView(
            label(
                getString(R.string.t412, UninstallCommands.RETENTION_DAYS),
                12f
            ).apply { setTextColor(hintColor); setLineSpacing(3f, 1f) }
        )
        tabUninstallContent.addView(space(8))
        tabUninstallContent.addView(
            row(
                button(getString(R.string.t413), outlined = true) { markSuggestions() },
                button(getString(R.string.t422), outlined = true) {
                    uninstallSelected.clear()
                    renderUninstall()
                }
            )
        )
        tabUninstallContent.addView(space(6))
        suggestedBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        tabUninstallContent.addView(suggestedBox)
        uninstallSearch.editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                uninstallQuery = s?.toString().orEmpty()
                renderUninstall()
            }
        })

        tabDeletedContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        paneBody.addView(tabDeletedContent)
        deletedBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        tabDeletedContent.addView(deletedBox)
        deletedBox.addView(
            label(
                getString(R.string.t253, UninstallCommands.RETENTION_DAYS),
                13f
            ).apply { setTextColor(hintColor); setLineSpacing(3f, 1f) }
        )
        deletedBox.addView(space(10))
        deletedList = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        deletedBox.addView(deletedList)
        deletedBox.addView(space(10))
        deletedBox.addView(button(getString(R.string.t058), outlined = true) { confirmRestoreAll() })
        deletedBox.addView(space(8))
        deletedBox.addView(
            row(
                button(getString(R.string.t059), outlined = true) { exportDeleted() },
                button(getString(R.string.t060), outlined = true) { confirmClearDeleted() }
            )
        )
        deletedBox.addView(space(8))
        deletedBox.addView(button(getString(R.string.t061), outlined = true) { renderDeleted() })

        return pane
    }

    private fun openUninstallPane() {

        BiometricGate.confirm(
            this,
            getString(R.string.t062),
            getString(R.string.t063)
        ) {
            openUninstallPaneNow()
        }
    }

    private fun openUninstallPaneNow() {
        showUninstallTab = true
        renderUninstallPane()
        if (!uninstallLoaded) loadUninstallApps()
        renderDeleted()
        paneBackCallback.isEnabled = true
        paneRoot.visibility = View.VISIBLE
        paneRoot.alpha = 0f
        paneRoot.translationY = 48.dp.toFloat()
        paneRoot.animate().alpha(1f).translationY(0f).setDuration(220)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .start()
    }

    private fun closeUninstallPane() {
        paneBackCallback.isEnabled = false
        paneRoot.visibility = View.GONE
    }

    private fun renderUninstallPane() {
        if (!::tabUninstallContent.isInitialized || !::tabDeletedContent.isInitialized ||
            !::paneTitle.isInitialized || !::paneScroll.isInitialized
        ) return
        paneTitle.text = if (showUninstallTab) getString(R.string.t026) else getString(R.string.t064)
        tabUninstallContent.visibility = if (showUninstallTab) View.VISIBLE else View.GONE
        tabDeletedContent.visibility = if (showUninstallTab) View.GONE else View.VISIBLE
        paneScroll.scrollTo(0, 0)
    }

    private fun maybeAutoStart(intent: Intent?) {
        if (intent?.getBooleanExtra(PairService.EXTRA_AUTO_START, false) != true) return
        intent.removeExtra(PairService.EXTRA_AUTO_START)
        uiHandler.postDelayed({
            bg(getString(R.string.t065), { ServerClient.request(this, "ping") }) { res ->
                if (!res.startsWith("OK pong")) startServer()
            }
        }, 400)
    }

    private fun maybeOpenPending(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PENDING, false) != true) return
        intent.removeExtra(EXTRA_OPEN_PENDING)
        refreshApps()
        mainScroll.post {
            openAppsPane()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
        uiHandler.removeCallbacks(autoCheckTick)
        if (Settings.autoCheck(this)) autoCheckTick.run() else startPulse()
        Thread {
            val removed = DeletedStore.cleanup(this)
            if (removed > 0) {
                AccessStore.log(this, getString(R.string.t066, removed))
                runOnUiThread { renderDeleted() }
            }
        }.start()
        maybeAskNotificationPermission()
    }

    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (Settings.notifAsked(this)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Settings.setNotifAsked(this, true)
        try {
            notifPermissionOnly.launch(Manifest.permission.POST_NOTIFICATIONS)
        } catch (e: Exception) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeAutoStart(intent)
        maybeOpenPending(intent)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(autoCheckTick)
        pulse?.cancel()
        pulse = null
        if (::statusDot.isInitialized) statusDot.alpha = 1f
    }

    private fun buildStatusCard(root: LinearLayout) {
        val container = themeColor(MR.attr.colorPrimaryContainer)
        val onContainer = themeColor(MR.attr.colorOnPrimaryContainer)
        val primary = themeColor(MR.attr.colorPrimary)
        val onPrimary = themeColor(MR.attr.colorOnPrimary)
        val accent = themeColor(MR.attr.colorTertiaryContainer)

        val hero = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(container, ColorUtils.blendARGB(container, accent, 0.55f))
            ).apply { cornerRadius = 28.dp.toFloat() }
            elevation = 6.dp.toFloat()
            clipToOutline = true
        }
        hero.addView(
            View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorUtils.setAlphaComponent(onContainer, 0x14))
                }
            },
            FrameLayout.LayoutParams(210.dp, 210.dp, Gravity.TOP or Gravity.END).apply {
                topMargin = (-80).dp
                marginEnd = (-70).dp
            }
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp, 24.dp, 22.dp, 22.dp)
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(onContainer, 0x80))
            }
        }
        statusRow.addView(statusDot, LinearLayout.LayoutParams(10.dp, 10.dp))
        statusTitle = TextView(this).apply {
            textSize = 26f
            appTypeface(700)
            setTextColor(onContainer)
        }
        statusRow.addView(
            statusTitle,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = 10.dp }
        )
        content.addView(statusRow)
        content.addView(space(6))

        statusSubtitle = TextView(this).apply {
            textSize = 13f
            appTypeface(400)
            setTextColor(ColorUtils.setAlphaComponent(onContainer, 0xCC))
            setLineSpacing(4f, 1f)
        }
        content.addView(statusSubtitle)
        content.addView(space(18))

        content.addView(
            button(getString(R.string.t067), outlined = true) { stopServer() }.apply {
                stopButton = this
                strokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onContainer, 0x4D))
                setTextColor(onContainer)
                cornerRadius = 14.dp
                visibility = View.GONE
            }
        )

        hero.addView(
            content,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
        root.addView(
            hero,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = 18.dp }
        )
        setStatus(null)
    }

    private fun setStatus(online: Boolean?) {
        serverOnline = online
        val onContainer = themeColor(MR.attr.colorOnPrimaryContainer)
        val dotColor = when (online) {
            true -> 0xFF4ADE80.toInt()
            false -> 0xFFFF6B6B.toInt()
            null -> ColorUtils.setAlphaComponent(onContainer, 0x80)
        }
        (statusDot.background as GradientDrawable).setColor(dotColor)
        statusTitle.text = when (online) {
            true -> getString(R.string.t068)
            false -> getString(R.string.t069)
            null -> getString(R.string.t070)
        }
        statusSubtitle.text = when (online) {
            true -> "uid 2000 · shell · localhost:${Settings.port(this)}"
            false -> getString(R.string.t071)
            null -> getString(R.string.t072)
        }
        if (::stopButton.isInitialized) {
            stopButton.visibility = if (online == true) View.VISIBLE else View.GONE
        }
        startPulse()
    }

    private fun startPulse() {
        if (serverOnline != true) {
            pulse?.cancel()
            pulse = null
            statusDot.alpha = 1f
            return
        }
        if (pulse?.isRunning == true) return
        pulse?.cancel()
        statusDot.alpha = 1f
        pulse = ObjectAnimator.ofFloat(statusDot, View.ALPHA, 1f, 0.35f).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun refreshStatus(notify: Boolean) {
        if (statusCheckRunning) return
        statusCheckRunning = true
        Thread {
            val res = try {
                ServerClient.request(this, "ping")
            } catch (e: Throwable) {
                ""
            }
            val online = res.startsWith("OK pong")
            runOnUiThread {
                statusCheckRunning = false
                setStatus(online)
                if (notify) {
                    snack(if (online) getString(R.string.t073) else getString(R.string.t074), error = !online)
                }
            }
        }.start()
    }

    private fun stopServer() {
        bg(getString(R.string.t075), { ServerClient.request(this, "stop")         }) { res ->
            val ok = res.startsWith("OK")
            if (ok) {
                setStatus(false)
                Settings.setStoppedBootCount(this, currentBootCount())
                snack(
                    if (Settings.bootStart(this)) {
                        getString(R.string.t076)
                    } else {
                        getString(R.string.t069)
                    }
                )
            } else if (res.contains("сервер недоступен")) {
                setStatus(false)
                Settings.setStoppedBootCount(this, currentBootCount())
                snack(getString(R.string.t078))
            } else {
                snack(res.substringAfter("|").ifBlank { res }, error = true)
            }
        }
    }

    private fun currentBootCount(): Int = try {
        android.provider.Settings.Global.getInt(
            contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1
        )
    } catch (e: Exception) {
        -1
    }

    private fun pairFromFields() {
        val code = pairCodeField.editText?.text?.toString()?.trim().orEmpty()
        val typedPort = pairPortField.editText?.text?.toString()?.trim()?.toIntOrNull()
        if (code.length != 6) {
            snack(getString(R.string.t079), error = true)
            return
        }
        if (typedPort == null || typedPort <= 0) {
            snack(getString(R.string.t080), error = true)
            return
        }
        pairPortError(this, typedPort, Settings.port(this))?.let {
            snack(it, error = true)
            return
        }
        bg(getString(R.string.t081), {
            val adb = FoxholeAdb.get(this@MainActivity)
            adb.setTimeout(30, TimeUnit.SECONDS)
            val service = discoverService(AdbMdns.SERVICE_TYPE_TLS_PAIRING, 4_000)
            val host = service?.first ?: localIpv4()
                ?: return@bg "ERR|" + getString(R.string.t082)
            val paired = try {
                adb.pair(host, service?.second ?: typedPort, code)
            } catch (e: Exception) {
                return@bg "ERR|" + friendlyAdbError(this@MainActivity, e)
            }
            if (!paired) {
                try {
                    if (adb.connectTls(this@MainActivity, 6_000)) {
                        FoxholeAdb.markPaired(this@MainActivity)
                        return@bg "OK|" + getString(R.string.t083, host)
                    }
                } catch (e: Exception) {
                }
                return@bg "ERR|" + getString(R.string.t084)
            }
            "OK|" + getString(R.string.t254, host, service?.second ?: typedPort)
        }) { res ->
            finishAction(res, onError = { msg ->
                if (msg.contains("Wi-Fi", ignoreCase = true) ||
                    msg.contains(getString(R.string.t085), ignoreCase = true)
                ) {
                    setFoldExpanded(pairingFold, true)
                }
            })
            if (res.startsWith("OK")) startServer()
        }
    }

    private fun startServer() {
        bg(getString(R.string.t086), {
            val path = writeScript() ?: return@bg "ERR|" + getString(R.string.t087)
            val adb = FoxholeAdb.get(this@MainActivity)
            var connected = false
            var connectError = getString(R.string.t088)
            for (attempt in 0 until 2) {
                try {
                    if (adb.connectTls(this@MainActivity, 8_000)) {
                        FoxholeAdb.markPaired(this@MainActivity)
                        connected = true
                        break
                    }
                    connectError = getString(R.string.t089)
                } catch (e: AdbPairingRequiredException) {
                    return@bg "ERR|" + getString(R.string.t090)
                } catch (e: AdbAuthenticationFailedException) {
                    return@bg "ERR|" + getString(R.string.t091)
                } catch (e: InterruptedException) {
                    connectError = getString(R.string.t088)
                } catch (e: Exception) {
                    connectError = getString(R.string.t256, e.message)
                }
                if (attempt == 0) Thread.sleep(1_200)
            }
            if (!connected) return@bg "ERR|$connectError"
            try {
                val stream = adb.openStream("shell:sh \"$path\"")
                Thread.sleep(4000)
                runCatching { stream.close() }
                adb.disconnect()
            } catch (e: Exception) {
                return@bg "ERR|" + getString(R.string.t255, e.message)
            }
            repeat(6) {
                if (ServerClient.request(this@MainActivity, "ping").startsWith("OK pong")) {
                    return@bg "OK|" + getString(R.string.t092)
                }
                Thread.sleep(800)
            }
            "ERR|" + getString(R.string.t093)
        }) { res -> finishAction(res, onError = { msg ->
            if (msg.contains(getString(R.string.t081), ignoreCase = true) ||
                msg.contains(getString(R.string.t094), ignoreCase = true) ||
                msg.contains(getString(R.string.t095), ignoreCase = true)
            ) {
                setFoldExpanded(pairingFold, true)
            }
        }) }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                PairService.prepare(this)
                openWirelessSettings()
            } else {
                snack(getString(R.string.t096), error = true)
            }
        }

    private val notifPermissionOnly =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun openWirelessDebugging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        PairService.prepare(this)
        openWirelessSettings()
    }

    private fun openWirelessSettings() {
        val direct = listOf(
            ComponentName("com.android.settings", "com.android.settings.Settings\$WirelessDebuggingActivity"),
            ComponentName("com.android.settings", "com.android.settings.wifi.debugging.WifiDebuggingActivity")
        )
        for (component in direct) {
            try {
                startActivity(Intent().setComponent(component))
                return
            } catch (e: Exception) {
            }
        }
        try {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: Exception) {
            snack(getString(R.string.t097), error = true)
        }
    }

    private fun showAdbPairCommand() {
        val code = pairCodeField.editText?.text?.toString()?.trim().orEmpty()
        val typedPort = pairPortField.editText?.text?.toString()?.trim()?.toIntOrNull()
        bg(getString(R.string.t098), {
            val pairService = discoverService(AdbMdns.SERVICE_TYPE_TLS_PAIRING, 4_000)
            val connectService = discoverService(AdbMdns.SERVICE_TYPE_TLS_CONNECT, 4_000)
            val host = pairService?.first ?: connectService?.first ?: localIpv4() ?: getString(R.string.t099)
            val pairPort = pairService?.second ?: typedPort
            val debugPort = connectService?.second
            val pairCode = if (code.length == 6) code else getString(R.string.t100)
            val hostText = if (host.contains(':')) "[$host]" else host
            "OK|adb pair $hostText:${pairPort ?: "<порт спаривания>"} $pairCode\nadb connect $hostText:${debugPort ?: "<порт отладки>"}"
        }) { res ->
            val tv = TextView(this).apply {
                text = res.substringAfter("|")
                appTypeface(400, code = true)
                textSize = 13f
                setTextIsSelectable(true)
                setPadding(24.dp, 8.dp, 24.dp, 0)
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.t101))
                .setMessage(getString(R.string.t102))
                .setView(tv)
                .setPositiveButton("OK", null)
                .secureShow()
        }
    }

    private fun discoverService(serviceType: String, timeoutMs: Long): Pair<String, Int>? =
        Discover.service(this, serviceType, timeoutMs)

    private fun localIpv4(): String? = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }

    private fun refreshApps() {
        appsBox.removeAllViews()
        val pending = AccessStore.pending(this)
        val allowed = AccessStore.allowed(this)

        if (pending.isEmpty() && allowed.isEmpty()) {
            appsBox.addView(
                label(getString(R.string.t103), 13f)
                    .apply { setTextColor(hintColor); setPadding(0, 8.dp, 0, 8.dp) }
            )
            return
        }
        var first = true
        pending.forEach { app ->
            if (!first) appsBox.addView(divider())
            first = false
            appRow(
                app, pending = true,
                getString(R.string.t104) to { maybeConfirmAllow(app) },
                getString(R.string.t105) to {
                    AccessStore.deny(this, app)
                    AccessNotifications.cancel(this, app.uid)
                }
            )
        }
        allowed.forEach { app ->
            if (!first) appsBox.addView(divider())
            first = false
            appRow(
                app, pending = false,
                getString(R.string.t106) to { changeLevel(app) },
                getString(R.string.t107) to { AccessStore.revoke(this, app) }
            )
        }
    }

    private fun appRow(
        app: AccessStore.App,
        pending: Boolean,
        vararg actions: Pair<String, () -> Unit>
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 14.dp, 0, 14.dp)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = TextView(this).apply {
            text = app.pkg.substringAfterLast('.').take(1).uppercase().ifEmpty { "?" }
            textSize = 16f
            appTypeface(700)
            gravity = Gravity.CENTER
            setTextColor(
                themeColor(if (pending) MR.attr.colorOnTertiaryContainer else MR.attr.colorOnSecondaryContainer)
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    themeColor(if (pending) MR.attr.colorTertiaryContainer else MR.attr.colorSecondaryContainer)
                )
            }
        }
        top.addView(avatar, LinearLayout.LayoutParams(40.dp, 40.dp))

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sharedUid = app.pkgList.size > 1
        texts.addView(
            label(
                if (sharedUid) {
                    app.pkgList.first() + " (+${app.pkgList.size - 1})"
                } else app.pkg,
                15f, bold = true
            ).apply { maxLines = 1 }
        )
        texts.addView(
            label(
                "uid ${app.uid}" +
                    (if (sharedUid) getString(R.string.t257, app.pkgList.joinToString()) else "") +
                    " · " +
                    (if (pending) getString(R.string.t108) else getString(R.string.t258, app.level.label)),
                12f
            ).apply {
                setTextColor(themeColor(if (pending) MR.attr.colorTertiary else MR.attr.colorSecondary))
                setPadding(0, 2.dp, 0, 0)
            }
        )
        top.addView(
            texts,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 12.dp }
        )
        box.addView(top)
        box.addView(space(10))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.forEachIndexed { i, (btnText, action) ->
            buttons.addView(
                button(btnText, outlined = true) {
                    action()
                    refreshApps()
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { if (i > 0) marginStart = 8.dp }
            )
        }
        box.addView(buttons)
        appsBox.addView(box)
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1.dp)
        background = GradientDrawable().apply {
            setColor(ColorUtils.setAlphaComponent(themeColor(MR.attr.colorOutlineVariant), 0x80))
        }
    }

    private fun loadInstalledApps() {
        if (installedLoaded) {
            renderInstalled()
            return
        }
        installedBox.removeAllViews()
        installedBox.addView(
            label(getString(R.string.t109), 13f).apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 6.dp) }
        )
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val seen = HashSet<String>()
            val list = pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
                val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (ai.packageName == packageName || !seen.add(ai.packageName)) return@mapNotNull null
                InstalledApp(ai.packageName, ai.loadLabel(pm).toString(), ai.uid)
            }.sortedBy { it.label.lowercase() }
            runOnUiThread {
                installedAll = list
                installedLoaded = true
                renderInstalled()
            }
        }.start()
    }

    private fun renderInstalled() {
        if (!::installedBox.isInitialized) return
        installedBox.removeAllViews()
        if (!installedLoaded) return
        val q = installedQuery.trim().lowercase()
        val list = if (q.isEmpty()) installedAll else installedAll.filter {
            it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q)
        }
        if (list.isEmpty()) {
            installedBox.addView(
                label(
                    if (installedAll.isEmpty()) getString(R.string.t110) else getString(R.string.t111),
                    13f
                ).apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 6.dp) }
            )
            return
        }
        installedBox.addView(
            label(getString(R.string.t259, list.size), 12f).apply {
                setTextColor(hintColor)
                setPadding(0, 2.dp, 0, 2.dp)
            }
        )
        val allowed = AccessStore.allowed(this).map { it.uid }.toSet()
        val shown = list.take(80)
        shown.forEachIndexed { i, app ->
            if (i > 0) installedBox.addView(divider())
            installedBox.addView(installedRow(app, app.uid in allowed))
        }
        if (list.size > shown.size) {
            installedBox.addView(
                label(getString(R.string.t260, list.size), 12f).apply {
                    setTextColor(hintColor)
                    setPadding(0, 8.dp, 0, 0)
                }
            )
        }
    }

    private fun installedRow(app: InstalledApp, allowed: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10.dp, 0, 10.dp)
        }
        val avatar = TextView(this).apply {
            text = app.label.take(1).uppercase().ifEmpty { "?" }
            textSize = 14f
            appTypeface(700)
            gravity = Gravity.CENTER
            setTextColor(themeColor(MR.attr.colorOnSecondaryContainer))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(themeColor(MR.attr.colorSecondaryContainer))
            }
        }
        row.addView(avatar, LinearLayout.LayoutParams(36.dp, 36.dp))

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(
            label(app.label, 14f).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        )
        texts.addView(
            label(app.pkg, 11f).apply {
                setTextColor(hintColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 1.dp, 0, 0)
            }
        )
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 10.dp; marginEnd = 8.dp }
        )

        val action = if (allowed) {
            button(getString(R.string.t107), outlined = true) {
                AccessStore.revoke(this, AccessStore.App(app.uid, app.pkg))
                refreshApps()
                renderInstalled()
                snack(getString(R.string.t261, app.pkg))
            }
        } else {
            button(getString(R.string.t104)) {
                maybeConfirmAllow(AccessStore.App(app.uid, app.pkg)) { renderInstalled() }
            }
        }
        row.addView(action)
        return row
    }

    private data class UninstallApp(
        val pkg: String,
        val label: String,
        val uid: Int,
        val system: Boolean
    )

    private fun filteredUninstallList(): List<UninstallApp> {
        val q = uninstallQuery.trim().lowercase()
        return uninstallAll.filter { app ->
            val bySearch = q.isEmpty() ||
                app.label.lowercase().contains(q) || app.pkg.lowercase().contains(q)
            val byFilter = when (uninstallFilter) {
                FILTER_USER -> !app.system
                FILTER_SYSTEM -> app.system
                else -> true
            }
            bySearch && byFilter
        }
    }

    private fun loadUninstallApps() {
        if (uninstallLoaded) {
            renderUninstall()
            return
        }
        uninstallBox.removeAllViews()
        uninstallBox.addView(
            label(getString(R.string.t109), 13f).apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 6.dp) }
        )
        Thread {
            val pm = packageManager
            val apps = try {
                pm.getInstalledPackages(0).mapNotNull { pi ->
                    val ai = pi.applicationInfo ?: return@mapNotNull null
                    if (ai.packageName == packageName) return@mapNotNull null
                    val label = try {
                        ai.loadLabel(pm).toString()
                    } catch (e: Exception) {
                        ai.packageName
                    }
                    UninstallApp(
                        ai.packageName, label, ai.uid,
                        ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                    )
                }.sortedBy { it.label.lowercase() }
            } catch (e: Exception) {
                emptyList<UninstallApp>()
            }
            runOnUiThread {
                uninstallAll = apps
                uninstallLoaded = true

                pendingImportSet?.let { pending ->
                    pendingImportSet = null
                    val present = pending intersect apps.map { it.pkg }.toSet()
                    if (present.isNotEmpty()) {
                        uninstallSelected.addAll(present)
                        snack(getString(R.string.t417, present.size))
                    }
                }
                renderUninstall()
                renderSuggested()
            }
        }.start()
    }

    private fun renderUninstall() {
        if (!::uninstallBox.isInitialized || !::uninstallFooterButton.isInitialized) return
        uninstallBox.removeAllViews()
        if (!uninstallLoaded) return
        updateUninstallFooter()
        val list = filteredUninstallList()
        if (list.isEmpty()) {
            uninstallBox.addView(
                label(
                    if (uninstallAll.isEmpty()) getString(R.string.t110) else getString(R.string.t111),
                    13f
                ).apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 6.dp) }
            )
            return
        }
        uninstallBox.addView(
            label("${list.size} ${pkgsWord(list.size)}", 12f).apply {
                setTextColor(hintColor)
                setPadding(0, 2.dp, 0, 2.dp)
            }
        )
        val shown = list.take(UNINSTALL_MAX)
        shown.forEachIndexed { i, app ->
            if (i > 0) uninstallBox.addView(divider())
            uninstallBox.addView(uninstallRow(app))
        }
        if (list.size > shown.size) {
            uninstallBox.addView(
                label(
                    getString(R.string.t262, shown.size, list.size),
                    12f
                ).apply { setTextColor(hintColor); setPadding(0, 8.dp, 0, 0) }
            )
        }
    }

    private fun uninstallRow(app: UninstallApp): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10.dp, 0, 10.dp)
        }
        val avatar = TextView(this).apply {
            text = app.label.take(1).uppercase().ifEmpty { "?" }
            textSize = 14f
            appTypeface(700)
            gravity = Gravity.CENTER
            setTextColor(themeColor(MR.attr.colorOnSecondaryContainer))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(themeColor(MR.attr.colorSecondaryContainer))
            }
        }
        row.addView(avatar, LinearLayout.LayoutParams(36.dp, 36.dp))

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(
            label(app.label, 14f).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        )
        texts.addView(
            label(
                app.pkg + (if (app.system) getString(R.string.t112) else getString(R.string.t113)),
                11f
            ).apply {
                setTextColor(hintColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 1.dp, 0, 0)
            }
        )
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 10.dp; marginEnd = 8.dp }
        )

        val checkBox = MaterialCheckBox(this).apply {
            isChecked = app.pkg in uninstallSelected
            contentDescription = getString(R.string.t263, app.label)
            setOnCheckedChangeListener { _, checked ->
                if (checked && app.system && !systemWarnedSession) {
                    warnFirstSystemCheckbox(app, this)
                } else {
                    if (checked) uninstallSelected.add(app.pkg) else uninstallSelected.remove(app.pkg)
                    updateUninstallFooter()
                }
            }
        }
        row.addView(
            checkBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 4.dp }
        )
        return row
    }

    private fun warnFirstSystemCheckbox(app: UninstallApp, box: android.widget.CheckBox) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.t114))
            .setMessage(
                getString(R.string.t115)
            )
            .setNegativeButton(getString(R.string.t116)) { _, _ ->
                uninstallSelected.remove(app.pkg)
                box.setOnCheckedChangeListener(null)
                box.isChecked = false
                box.setOnCheckedChangeListener { _, checked ->
                    if (checked && app.system && !systemWarnedSession) {
                        warnFirstSystemCheckbox(app, box)
                    } else {
                        if (checked) uninstallSelected.add(app.pkg) else uninstallSelected.remove(app.pkg)
                        updateUninstallFooter()
                    }
                }
                updateUninstallFooter()
            }
            .setPositiveButton(getString(R.string.t117)) { _, _ ->
                systemWarnedSession = true
                uninstallSelected.add(app.pkg)
                box.isChecked = true
                updateUninstallFooter()
            }
            .secureShow()
    }

    private fun selectAllUninstall() {
        val visible = filteredUninstallList().take(UNINSTALL_MAX)
        if (visible.isEmpty()) return
        val action = {
            uninstallSelected.addAll(visible.map { it.pkg })
            renderUninstall()
        }
        if (!systemWarnedSession && visible.any { it.system }) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.t118))
                .setMessage(
                    getString(R.string.t119)
                )
                .setNegativeButton(getString(R.string.t116), null)
                .setPositiveButton(getString(R.string.t117)) { _, _ ->
                    systemWarnedSession = true
                    action()
                }
                .secureShow()
        } else {
            action()
        }
    }

    private fun updateUninstallFooter() {
        if (!::uninstallFooterButton.isInitialized) return
        val n = uninstallSelected.size
        uninstallFooterButton.text =
            if (n == 0) getString(R.string.t057) else getString(R.string.t120, n)
        uninstallFooterButton.isEnabled = n > 0
    }

    private fun confirmUninstall() {
        val selected = uninstallAll.filter { it.pkg in uninstallSelected }
        if (selected.isEmpty()) return
        val hasSystem = selected.any { it.system }
        val userCount = selected.count { !it.system }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 8.dp, 24.dp, 0)
        }
        val title = if (hasSystem) {
            getString(R.string.t264, selected.size, appsWord(selected.size), selected.count { it.system })
        } else {
            getString(R.string.t265, userCount, appsWord(userCount))
        }
        body.addView(
            TextView(this).apply {
                text = selected.take(20).joinToString("\n") { "· ${it.pkg}" } +
                    if (selected.size > 20) getString(R.string.t266, selected.size - 20) else ""
                appTypeface(400, code = true)
                textSize = 12f
                setTextIsSelectable(true)
            }
        )
        if (hasSystem) {
            body.addView(space(10))
            body.addView(
                label(
                    getString(R.string.t121),
                    12f
                ).apply { setTextColor(themeColor(MR.attr.colorError)); setLineSpacing(3f, 1f) }
            )
        }
        body.addView(space(14))
        val irreversible = MaterialCheckBox(this).apply {
            text = getString(R.string.t122)
            textSize = 13f
            appTypeface(500)
        }
        body.addView(irreversible)
        body.addView(
            label(
                getString(R.string.t123),
                12f
            ).apply { setTextColor(hintColor); setLineSpacing(2f, 1f) }
        )

        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(body)
            .setNegativeButton(getString(R.string.t116), null)
            .setPositiveButton(getString(R.string.t124), null)
            .create()
        dlg.setOnShowListener {
            val positive = dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            if (hasSystem) {
                positive.isEnabled = false
                uiHandler.postDelayed({ positive.isEnabled = true }, 3000)
            }
            positive.setOnClickListener {
                val irr = irreversible.isChecked
                dlg.dismiss()
                performUninstall(selected, irr)
            }
        }

        if (Settings.flagSecureScreens(this)) {
            dlg.window?.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        dlg.show()
    }

    private fun appsWord(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> getString(R.string.t125)
        n % 10 in 2..4 && (n % 100 < 12 || n % 100 > 14) -> getString(R.string.t267)
        else -> getString(R.string.t126)
    }

    private fun pkgsWord(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> getString(R.string.t127)
        n % 10 in 2..4 && (n % 100 < 12 || n % 100 > 14) -> getString(R.string.t128)
        else -> getString(R.string.t129)
    }

    private fun performUninstall(selected: List<UninstallApp>, irreversible: Boolean) {
        Thread {
            var ok = 0
            var skipped = 0
            var failed = 0
            for (app in selected) {
                runOnUiThread { show("> " + getString(R.string.t268, app.pkg) + "\n…") }
                val res = uninstallOne(app, irreversible)
                runOnUiThread {
                    show("> " + getString(R.string.t268, app.pkg) + "\n" + res)
                    if (res.startsWith("OK") || res.startsWith("SKIP")) {
                        uninstallSelected.remove(app.pkg)
                        updateUninstallFooter()
                    }
                    renderDeleted()
                }
                when {
                    res.startsWith("OK") -> ok++
                    res.startsWith("SKIP") -> skipped++
                    else -> failed++
                }
            }
            runOnUiThread {
                renderUninstall()
                snack(
                    getString(R.string.t130, ok, skipped, failed),
                    error = failed > 0
                )
            }
        }.start()
    }

    private fun uninstallOne(app: UninstallApp, irreversible: Boolean): String {
        var apkDirPath: String? = null
        if (!app.system && !irreversible) {
            val dir = DeletedStore.apkDirFor(this, app.pkg)
            val pmPath = ServerClient.request(this, "pm path ${UninstallCommands.quoteShellArg(app.pkg)}")
            val paths = UninstallCommands.parsePmPath(pmPath.substringAfter(" ", ""))
            if (paths.isEmpty()) {
                AccessStore.log(this, getString(R.string.t269, app.pkg))
                if (!confirmContinueNoBackup(app.pkg)) {
                    return "SKIP " + getString(R.string.t270, app.pkg)
                }
            } else {
                val copied = copyApkFiles(paths, dir)
                if (!copied) {
                    AccessStore.log(this, getString(R.string.t271, app.pkg))
                    if (!confirmContinueNoBackup(app.pkg)) {
                        removeApkDir(app.pkg, dir.absolutePath)
                        return "SKIP " + getString(R.string.t270, app.pkg)
                    }
                } else {
                    apkDirPath = dir.absolutePath
                }
            }
        }

        val cmd = if (app.system) {
            UninstallCommands.uninstallSystemCmd(app.pkg)
        } else {
            UninstallCommands.uninstallUserCmd(app.pkg)
        }
        val r = ServerClient.request(this, cmd)
        AccessStore.log(this, getString(R.string.t272, app.pkg, r.take(120)))
        if (!r.startsWith("OK")) {
            if (apkDirPath != null) removeApkDir(app.pkg, apkDirPath)
            val body = r.substringAfter(" ", "").trim().ifBlank { r }
            return "ERR ${app.pkg}: ${friendlyHideError(app.system, body)}"
        }
        when {
            apkDirPath != null -> DeletedStore.add(
                this, DeletedStore.Record(
                    app.pkg, app.label, false, System.currentTimeMillis(), apkDirPath
                )
            )
            app.system && !irreversible -> DeletedStore.add(
                this, DeletedStore.Record(
                    app.pkg, app.label, true, System.currentTimeMillis(), null
                )
            )
        }
        return "OK " + getString(R.string.t273, app.pkg, r.substringAfter(" ", "").trim())
    }

    private fun friendlyHideError(system: Boolean, raw: String): String =
        if (system && raw.contains("only root can delete system app", ignoreCase = true)) {
            getString(R.string.t131)
        } else {
            raw
        }

    private fun copyApkFiles(paths: List<String>, dir: File): Boolean {
        val cmd = buildString {
            append("mkdir -p ${UninstallCommands.quoteShellArg(dir.absolutePath)}")
            paths.forEach { src ->
                val dst = File(dir, File(src).name).absolutePath
                append(" && cp -f ${UninstallCommands.quoteShellArg(src)} ${UninstallCommands.quoteShellArg(dst)}")
            }
        }
        val r = ServerClient.request(this, cmd)
        if (!r.startsWith("OK")) return false
        return paths.all { File(dir, File(it).name).exists() }
    }

    private fun confirmContinueNoBackup(pkg: String): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        var proceed = false
        runOnUiThread {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.t132))
                .setMessage(
                    getString(R.string.t133, pkg)
                )
                .setNegativeButton(getString(R.string.t134)) { _, _ -> latch.countDown() }
                .setPositiveButton(getString(R.string.t135)) { _, _ ->
                    proceed = true
                    latch.countDown()
                }
                .secureShow()
        }
        latch.await()
        return proceed
    }

    private fun removeApkDir(pkg: String, dirPath: String) {
        try {
            ServerClient.request(this, UninstallCommands.removeDirCmd(dirPath))
        } catch (e: Exception) {
        }
        try {
            File(dirPath).deleteRecursively()
        } catch (e: Exception) {
            AccessStore.log(this, getString(R.string.t274, pkg, e.message))
        }
    }

    private fun renderDeleted() {
        if (!::deletedBox.isInitialized) return
        Thread {
            val n = DeletedStore.cleanup(this)
            if (n > 0) AccessStore.log(this, getString(R.string.t136, n))
            runOnUiThread { renderDeletedRows() }
        }.start()
    }

    private fun renderDeletedRows() {
        if (!::deletedList.isInitialized) return
        deletedList.removeAllViews()
        val records = DeletedStore.all(this)
        if (records.isEmpty()) {
            deletedList.addView(
                label(getString(R.string.t137), 13f)
                    .apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 6.dp) }
            )
            return
        }
        val now = System.currentTimeMillis()
        records.forEachIndexed { i, r ->
            if (i > 0) deletedList.addView(divider())
            deletedList.addView(deletedRow(r, now))
        }
        deletedList.addView(space(8))
        val size = DeletedStore.bytes(this)
        deletedList.addView(
            label(getString(R.string.t275, formatBytes(size)), 12f).apply { setTextColor(hintColor) }
        )
    }

    private fun deletedRow(r: DeletedStore.Record, now: Long): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12.dp, 0, 12.dp)
        }
        box.addView(label(r.label, 14f, bold = true).apply { maxLines = 1 })
        val df = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
        box.addView(
            label(
                getString(
                    R.string.t276,
                    r.pkg,
                    if (r.system) getString(R.string.t152) else getString(R.string.t153),
                    df.format(java.util.Date(r.removedAt))
                ),
                11f
            ).apply { setTextColor(hintColor); maxLines = 1; setPadding(0, 1.dp, 0, 0) }
        )
        val days = UninstallCommands.daysLeft(r.removedAt, now)
        box.addView(
            label(
                getString(R.string.t277, days, UninstallCommands.RETENTION_DAYS) +
                    (if (!r.restorePossible) getString(R.string.t138) else
                        if (r.system) getString(R.string.t139) else getString(R.string.t140)),
                11f
            ).apply { setTextColor(hintColor); maxLines = 1; setPadding(0, 1.dp, 0, 0) }
        )
        box.addView(space(8))
        box.addView(
            row(
                button(getString(R.string.t141), outlined = true) { confirmRestore(r) },
                button(getString(R.string.t142), outlined = true) { confirmForget(r) }
            )
        )
        return box
    }

    private fun confirmRestore(r: DeletedStore.Record) {
        if (!r.restorePossible && !r.system) {
            snack(getString(R.string.t278, r.pkg), error = true)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.t279, r.label))
            .setMessage(
                if (r.system) {
                    getString(R.string.t143)
                } else {
                    getString(R.string.t144)
                }
            )
            .setNegativeButton(getString(R.string.t116), null)
            .setPositiveButton(getString(R.string.t141)) { _, _ ->
                Thread {
                    val res = restoreRecord(r)
                    runOnUiThread {
                        show("> " + getString(R.string.t280, r.pkg) + "\n" + res)
                        snack(
                            if (res.startsWith("OK")) res.substringAfter(": ") else res,
                            error = !res.startsWith("OK")
                        )
                        renderDeleted()
                    }
                }.start()
            }
            .secureShow()
    }

    private fun restoreRecord(r: DeletedStore.Record): String {
        val cmd: String
        var stagingDir: String? = null
        if (r.system) {
            cmd = UninstallCommands.restoreSystemCmd(r.pkg)
        } else {
            val files = r.apkDir
                ?.let { File(it).listFiles() }
                ?.filter { it.name.endsWith(".apk") }
                ?.map { it.absolutePath }
                .orEmpty()
            if (files.isEmpty()) {
                DeletedStore.remove(this, r.pkg)
                return "ERR " + getString(R.string.t281, r.pkg)
            }
            stagingDir = UninstallCommands.stagingDir(r.pkg)
            val copyRes = ServerClient.request(
                this, UninstallCommands.restoreCopyCmd(files, stagingDir)
            )
            if (!copyRes.startsWith("OK")) {
                val copyBody = copyRes.substringAfter(" ", "").trim().ifBlank { copyRes }
                return "ERR " + getString(R.string.t282, r.pkg, copyBody)
            }
            cmd = UninstallCommands.restoreUserCmd(
                files.map { UninstallCommands.stagingPath(stagingDir, File(it).name) }
            )
        }
        val res = ServerClient.request(this, cmd)
        if (stagingDir != null) {
            try {
                ServerClient.request(this, UninstallCommands.removeDirCmd(stagingDir))
            } catch (e: Exception) {
            }
        }
        AccessStore.log(this, getString(R.string.t283, r.pkg, res.take(120)))
        if (res.startsWith("OK")) {
            DeletedStore.remove(this, r.pkg)
            if (!r.system && r.apkDir != null) removeApkDir(r.pkg, r.apkDir)
            return "OK " + getString(R.string.t284, r.pkg, res.substringAfter(" ", "").trim())
        }
        val body = res.substringAfter(" ", "").trim().ifBlank { res }
        return "ERR ${r.pkg}: $body"
    }

    private fun confirmRestoreAll() {
        val records = DeletedStore.all(this)
        if (records.isEmpty()) {
            snack(getString(R.string.t145))
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.t146))
            .setMessage(
                getString(R.string.t285, records.size, appsWord(records.size))
            )
            .setNegativeButton(getString(R.string.t116), null)
            .setPositiveButton(getString(R.string.t058)) { _, _ ->
                Thread {
                    var ok = 0
                    var failed = 0
                    for (r in records) {
                        val res = restoreRecord(r)
                        runOnUiThread { show("> " + getString(R.string.t280, r.pkg) + "\n" + res) }
                        if (res.startsWith("OK")) ok++ else failed++
                    }
                    runOnUiThread {
                        snack(getString(R.string.t147, ok, failed), error = failed > 0)
                        renderDeleted()
                    }
                }.start()
            }
            .secureShow()
    }

    private fun confirmForget(r: DeletedStore.Record) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.t148))
            .setMessage(
                if (r.system) {
                    getString(R.string.t286, r.pkg)
                } else {
                    getString(R.string.t149)
                }
            )
            .setNegativeButton(getString(R.string.t116), null)
            .setPositiveButton(getString(R.string.t124)) { _, _ ->
                if (!r.system && r.apkDir != null) removeApkDir(r.pkg, r.apkDir)
                DeletedStore.remove(this, r.pkg)
                AccessStore.log(this, getString(R.string.t287, r.pkg))
                renderDeleted()
            }
            .secureShow()
    }

    private fun confirmClearDeleted() {
        val records = DeletedStore.all(this)
        if (records.isEmpty()) {
            snack(getString(R.string.t145))
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.t150))
            .setMessage(
                getString(R.string.t288, records.size, appsWord(records.size))
            )
            .setNegativeButton(getString(R.string.t116), null)
            .setPositiveButton(getString(R.string.t031)) { _, _ ->
                records.forEach { r ->
                    if (!r.system && r.apkDir != null) removeApkDir(r.pkg, r.apkDir)
                    DeletedStore.remove(this, r.pkg)
                }
                AccessStore.log(this, getString(R.string.t289, records.size))
                renderDeleted()
            }
            .secureShow()
    }

    private fun exportDeleted() {
        val records = DeletedStore.all(this)
        if (records.isEmpty()) {
            snack(getString(R.string.t151))
            return
        }
        Thread {
            val df = java.text.SimpleDateFormat(
                "dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()
            )
            val sb = StringBuilder()
            sb.append(getString(R.string.t290, df.format(java.util.Date())))
            records.forEach { r ->
                sb.append(
                    "${r.pkg} | ${r.label} | " +
                        (if (r.system) getString(R.string.t152) else getString(R.string.t153)) +
                        getString(
                            R.string.t291,
                            df.format(java.util.Date(r.removedAt)),
                            r.apkDir ?: "-",
                            r.restorePossible
                        )
                )
            }
            val file = File(getExternalFilesDir(null), "deleted-export.txt")
            file.writeText(sb.toString())
            runOnUiThread {
                show(getString(R.string.t292, file.absolutePath))
                copyToClipboard(file.absolutePath)
                snack(getString(R.string.t293, file.name))
            }
            AccessStore.log(this, getString(R.string.t294, records.size, file.absolutePath))
        }.start()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> getString(R.string.t154).format(bytes / 1048576.0)
        bytes >= 1024 -> getString(R.string.t155).format(bytes / 1024.0)
        else -> getString(R.string.t156, bytes)
    }

    private fun send(cmd: String) {
        val c = cmd.trim()
        if (c.isEmpty()) {
            snack(getString(R.string.t157), error = true)
            return
        }

        val danger = DangerousCmd.analyze(c)
        if (danger != null && Settings.dangerousWarn(this)) {
            confirmDangerous(c, danger) { proceedSend(c) }
        } else {
            proceedSend(c)
        }
    }

    private fun proceedSend(c: String) {

        BiometricGate.confirm(
            this,
            getString(R.string.t158),
            getString(R.string.t159)
        ) {

            TerminalHistory.record(this, c)
            bg(c, { ServerClient.request(this, c) }) { res ->
                if (res.startsWith("ERR сервер недоступен")) setStatus(false)

                AccessStore.log(
                    this@MainActivity,
                    "ТЕРМИНАЛ ${SecretMask.mask(c)} → ${res.take(60)}"
                )
                renderHistory()
            }
        }
    }

    private fun confirmDangerous(
        cmd: String,
        danger: DangerousCmd.Danger,
        onProceed: () -> Unit
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.t371))
            .setMessage(dangerText(danger.kind) + "\n\n" + cmd)
            .setNegativeButton(getString(R.string.t116), null)
            .setPositiveButton(getString(R.string.t372)) { _, _ -> onProceed() }
            .secureShow()
    }

    private fun dangerText(kind: DangerousCmd.DangerKind): String = when (kind) {
        DangerousCmd.DangerKind.UNINSTALL -> getString(R.string.t374)
        DangerousCmd.DangerKind.FILE_DESTROY -> getString(R.string.t375)
        DangerousCmd.DangerKind.SYSTEM_CHANGE -> getString(R.string.t376)
        DangerousCmd.DangerKind.REBOOT -> getString(R.string.t377)
        DangerousCmd.DangerKind.NETWORK_OFF -> getString(R.string.t378)
        DangerousCmd.DangerKind.APP_STOP -> getString(R.string.t379)
    }

    private fun writeScript(): String? = ServerScript.write(this)

    private fun bg(label: String, block: () -> String, onDone: ((String) -> Unit)? = null) {
        Thread {
            val res = try {
                block()
            } catch (e: Throwable) {
                "ERR|${e.message ?: e.toString()}"
            }
            runOnUiThread {
                show("> $label\n" + res.substringAfter("|"))
                onDone?.invoke(res)
            }
        }.start()
    }

    private fun finishAction(res: String, onError: (String) -> Unit = {}) {
        val ok = res.startsWith("OK|")
        val msg = res.substringAfter("|")
        snack(msg, error = !ok)
        if (ok && msg.startsWith(getString(R.string.t161))) setStatus(true)
        if (!ok) onError(msg)
    }

    private fun copyToClipboard(text: String) {
        val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("foxhole", text))
    }

    private fun pcCommand(): String {
        val dir = getExternalFilesDir(null)
        val path = dir?.let { File(it, "start.sh").absolutePath }
            ?: "/storage/emulated/0/Android/data/$packageName/files/start.sh"
        return "adb shell sh \"$path\""
    }

    private fun showPcCommandDialog() {
        bg(getString(R.string.t162), {
            if (writeScript() == null) "ERR|" + getString(R.string.t087) else "OK"
        }) { res ->
            if (res.startsWith("ERR|")) {
                snack(res.substringAfter("|"), error = true)
            } else {
                offerShareOrCopy(getString(R.string.t295), pcCommand())
            }
        }
    }

    private fun sharePcCommand() {
        val cmd = pcCommand()
        if (cmd.isBlank()) {
            snack(getString(R.string.t163), error = true)
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, cmd)
        }
        startActivity(Intent.createChooser(send, getString(R.string.t164)))
    }

    private fun copyPcCommand() {
        val cmd = pcCommand()
        copyToClipboard(cmd)
        snack(getString(R.string.t165))
    }

    private fun offerShareOrCopy(title: String, cmd: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(cmd)
            .setPositiveButton(getString(R.string.t166)) { _, _ -> sharePcCommand() }
            .setNegativeButton(getString(R.string.t167)) { _, _ ->
                copyToClipboard(cmd)
                snack(getString(R.string.t168))
            }
            .secureShow()
    }

    private fun MaterialAlertDialogBuilder.secureShow(): androidx.appcompat.app.AlertDialog {
        val dlg = create()
        if (Settings.flagSecureScreens(this@MainActivity)) {
            dlg.window?.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        dlg.show()
        return dlg
    }

    private fun snack(message: String, error: Boolean = false) {
        val host = openSheet?.window?.findViewById<android.view.ViewGroup>(
            android.R.id.content
        ) ?: findViewById(android.R.id.content)
        val bar = Snackbar.make(
            host,
            message,
            Snackbar.LENGTH_LONG
        )
        bar.setBackgroundTint(
            themeColor(if (error) MR.attr.colorError else MR.attr.colorPrimaryContainer)
        )
        bar.setTextColor(
            themeColor(if (error) MR.attr.colorOnError else MR.attr.colorOnPrimaryContainer)
        )
        bar.view.findViewById<TextView>(MR.id.snackbar_text)?.appTypeface(400)
        ViewCompat.setOnApplyWindowInsetsListener(bar.view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            insets
        }
        bar.show()
    }

    private fun show(text: String) {
        val max = 24_000
        val nl = text.indexOf('\n')
        val prompt = if (text.startsWith("> ") && nl > 2) text.substring(2, nl) else null
        val body = if (prompt != null) text.substring(nl + 1) else text
        val block = terminalBlock(prompt, body)
        val rest = out.text
        val restCapped =
            if (block.length + rest.length > max) {
                rest.subSequence(0, (max - block.length).coerceAtLeast(0))
            } else {
                rest
            }
        out.text =
            if (restCapped.isEmpty()) block else TextUtils.concat(block, "\n\n", restCapped)
        terminalScroll?.scrollTo(0, 0)
    }

    private fun terminalBlock(prompt: String?, body: String): CharSequence {
        val b = SpannableStringBuilder()
        if (prompt != null) {
            b.append("❯ ")
            val start = b.length
            b.append(prompt)
            b.setSpan(
                ForegroundColorSpan(CONSOLE_PROMPT),
                start - 2, b.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            b.setSpan(
                StyleSpan(Typeface.BOLD),
                start - 2, b.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            b.append('\n')
        }
        val start = b.length
        b.append(body)
        b.setSpan(
            ForegroundColorSpan(if (body.startsWith("ERR")) CONSOLE_ERR else CONSOLE_TEXT),
            start, b.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return b
    }

    private fun chipButton(text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            isAllCaps = false
            textSize = 12f
            appTypeface(500)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = 260.dp
            cornerRadius = 20.dp
            insetTop = 2.dp
            insetBottom = 2.dp
            setOnClickListener { onClick() }
        }

    private fun explain(cmd: String): String? {
        val first = cmd.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()
        return when (first) {
            null, "" -> null
            "ping" -> getString(R.string.t169)
            "stop" -> getString(R.string.t170)
            "dumpsys" -> getString(R.string.t171)
            "pm" -> getString(R.string.t172)
            "settings" -> getString(R.string.t173)
            "getsetting" -> getString(R.string.t174)
            "putsetting" -> getString(R.string.t175)
            "grant", "revoke" -> getString(R.string.t176)
            "appop" -> getString(R.string.t177)
            "am" -> getString(R.string.t178)
            "cmd" -> getString(R.string.t179)
            "logcat" -> getString(R.string.t180)
            "ls" -> getString(R.string.t181)
            "cat" -> getString(R.string.t182)
            "df" -> getString(R.string.t183)
            "ps" -> getString(R.string.t184)
            "top" -> getString(R.string.t185)
            "date" -> getString(R.string.t186)
            "id" -> getString(R.string.t187)
            "getprop" -> getString(R.string.t188)
            "wm" -> getString(R.string.t189)
            "svc" -> getString(R.string.t190)
            "input" -> getString(R.string.t191)
            "screencap" -> getString(R.string.t192)
            "sh" -> getString(R.string.t193)
            else -> getString(R.string.t194)
        }
    }

    private fun updateExplain() {
        val label = terminalExplain ?: return
        val cmd = terminalInput?.text?.toString().orEmpty()

        val danger = DangerousCmd.analyze(cmd)
        if (danger != null) {
            label.text = "⚠ " + dangerText(danger.kind)
            label.setTextColor(themeColor(MR.attr.colorError))
            label.visibility = View.VISIBLE
            return
        }
        val why = explain(cmd)
        if (why == null) {
            label.visibility = View.GONE
        } else {
            label.text = "ℹ $why"
            label.setTextColor(hintColor)
            label.visibility = View.VISIBLE
        }
    }

    private fun renderHistory() {
        val row = historyRow ?: return
        row.removeAllViews()
        val items = TerminalHistory.all(this)
        if (!Settings.terminalHistory(this) || items.isEmpty()) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        row.addView(
            label(getString(R.string.t195), 12f).apply {
                setTextColor(hintColor)
                setPadding(0, 2.dp, 0, 6.dp)
            }
        )
        val chips = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    items.forEach { cmd ->
                        addView(
                            chipButton(TerminalHistory.mask(cmd)) {
                                terminalInput?.setText(cmd)
                                terminalInput?.setSelection(cmd.length)
                                terminalInput?.requestFocus()
                                updateExplain()
                            },
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { marginEnd = 8.dp }
                        )
                    }
                    addView(
                        chipButton(getString(R.string.t196)) {
                            TerminalHistory.clear(this@MainActivity)
                            renderHistory()
                        }
                    )
                }
            )
        }
        row.addView(chips)
    }

    private fun profileCard(
        profile: CommandProfiles.Profile,
        tag: Int,
        onDelete: (() -> Unit)?
    ): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10.dp, 0, 10.dp)
        }
        box.addView(label("${profile.name}  ·  ${getString(tag)}", 14f, bold = true))
        box.addView(
            label(profile.template, 12f).apply {
                appTypeface(400, code = true)
                setTextColor(hintColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 2.dp, 0, 0)
            }
        )
        box.addView(space(8))
        box.addView(
            if (onDelete != null) {
                row(
                    button(getString(R.string.t405), outlined = true) { runProfile(profile) },
                    button(getString(R.string.t406), outlined = true) { onDelete() }
                )
            } else {
                row(button(getString(R.string.t405), outlined = true) { runProfile(profile) })
            }
        )
        return box
    }

    private fun showProfilesSheet() {
        val sheet = BottomSheetDialog(this)

        if (Settings.flagSecureScreens(this)) {
            sheet.window?.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 24.dp, 24.dp, 32.dp)
        }
        content.addView(label(getString(R.string.t399), 22f, bold = true))
        content.addView(
            label(getString(R.string.t400), 13f)
                .apply { setTextColor(hintColor); setPadding(0, 4.dp, 0, 0) }
        )

        content.addView(space(16))
        CommandProfiles.BUILTINS.forEach { profile ->
            content.addView(profileCard(profile, R.string.t407, null))
        }

        content.addView(space(10))
        content.addView(divider())
        content.addView(space(10))
        CommandProfiles.custom(this).forEach { profile ->
            content.addView(
                profileCard(profile, R.string.t408) {
                    CommandProfiles.deleteCustom(this, profile.name)
                    AccessStore.log(this, "профиль удалён: ${profile.name}")
                    sheet.dismiss()
                    snack(getString(R.string.t452))
                }
            )
        }

        content.addView(space(12))
        content.addView(divider())
        content.addView(space(16))
        content.addView(label(getString(R.string.t401), 14f, bold = true))
        content.addView(space(8))
        val nameField = field(getString(R.string.t402), InputType.TYPE_CLASS_TEXT)
        content.addView(nameField)
        content.addView(space(8))
        val tmplField = field(getString(R.string.t403), InputType.TYPE_CLASS_TEXT)
        content.addView(tmplField)
        content.addView(space(10))
        content.addView(button(getString(R.string.t404), outlined = true) {
            val name = nameField.editText?.text?.toString()?.trim().orEmpty()
            val tmpl = tmplField.editText?.text?.toString()?.trim().orEmpty()
            if (CommandProfiles.saveCustom(this, name, tmpl)) {
                AccessStore.log(this, "профиль сохранён: $name")
                sheet.dismiss()
                snack(getString(R.string.t451))
            } else {
                snack(
                    getString(R.string.t403) + " (≤ ${CommandProfiles.MAX_TEMPLATE_LEN})",
                    error = true
                )
            }
        })

        sheet.setContentView(ScrollView(this).apply { addView(content) })
        openSheet = sheet
        sheet.setOnDismissListener { openSheet = null }
        sheet.show()
    }

    private fun runProfile(profile: CommandProfiles.Profile) {
        val slots = CommandProfiles.slots(profile.template).distinct()
        if (slots.isEmpty()) {
            send(profile.template)
            return
        }
        val fields = LinkedHashMap<String, TextInputLayout>()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 8.dp, 24.dp, 0)
        }
        body.addView(
            label(
                getString(R.string.t409, slots.joinToString(" ") { "{$it}" }),
                13f
            ).apply { setLineSpacing(2f, 1f) }
        )
        slots.forEach { slot ->
            body.addView(space(8))
            val fl = field(slot, InputType.TYPE_CLASS_TEXT)
            fields[slot] = fl
            body.addView(fl)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("${getString(R.string.t405)}: ${profile.name}")
            .setView(body)
            .setNegativeButton(getString(R.string.t116), null)
            .setPositiveButton(getString(R.string.t405)) { _, _ ->
                val values = fields.mapValues { (_, fl) ->
                    fl.editText?.text?.toString()?.trim().orEmpty()
                }

                when (val cmd = CommandProfiles.compose(profile.template, values)) {
                    null -> snack(getString(R.string.t409, slots.joinToString(" ") { "{$it}" }), error = true)
                    else -> send(cmd)
                }
            }
            .secureShow()
    }

    private fun saveUninstallSet() {
        if (uninstallSelected.isEmpty()) {
            snack(getString(R.string.t453), error = true)
            return
        }
        saveSetLauncher.launch(getString(R.string.t421))
    }

    private fun importSet() {
        importSetLauncher.launch("*/*")
    }

    private fun writeUninstallSet(uri: Uri) {
        Thread {
            try {
                val body = buildString {
                    append("foxhole uninstall set v1\n")
                    uninstallSelected.toSortedSet().forEach { append(it).append('\n') }
                }
                val ok = contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(body.toByteArray(Charsets.UTF_8))
                    true
                } ?: false
                runOnUiThread {
                    if (ok) {
                        val name = uri.lastPathSegment ?: "?"
                        snack(getString(R.string.t416, name))
                        AccessStore.log(this, "набор сохранён: $name (${uninstallSelected.size})")
                    } else {
                        snack(getString(R.string.t418), error = true)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { snack(getString(R.string.t418), error = true) }
            }
        }.start()
    }

    private val SET_PKG = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{1,200}$")

    private fun readUninstallSet(uri: Uri) {
        Thread {
            val pkgs = LinkedHashSet<String>()
            try {
                val text = contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: ""

                if (text.length <= 64 * 1024) {
                    text.lineSequence().forEach { raw ->
                        val s = raw.trim()
                        if (s.isEmpty() || s.startsWith("#") || s.startsWith("foxhole")) {
                            return@forEach
                        }
                        if (SET_PKG.matches(s)) pkgs.add(s)
                    }
                }
            } catch (e: Exception) {
            }
            runOnUiThread {
                when {
                    pkgs.isEmpty() -> snack(getString(R.string.t420), error = true)
                    uninstallLoaded -> applyImportSet(pkgs)
                    else -> {

                        pendingImportSet = pkgs
                        uninstallLoaded = false
                        loadUninstallApps()
                    }
                }
            }
        }.start()
    }

    private fun applyImportSet(pkgs: Set<String>) {
        val present = pkgs intersect uninstallAll.map { it.pkg }.toSet()
        if (present.isNotEmpty()) {
            uninstallSelected.addAll(present)
            AccessStore.log(this, "набор импортирован: ${present.size} из ${pkgs.size}")
        }
        renderUninstall()
        renderSuggested()
        snack(getString(R.string.t417, present.size))
    }

    private fun markSuggestions() {
        val present = KnownPreinstalls.ALL.mapNotNull { item ->
            uninstallAll.firstOrNull { it.pkg == item.pkg }?.pkg
        }
        if (present.isEmpty()) return
        val hasSystem = uninstallAll.any { it.pkg in present && it.system }
        val action = {
            uninstallSelected.addAll(present)
            renderUninstall()
        }
        if (!systemWarnedSession && hasSystem) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.t118))
                .setMessage(getString(R.string.t119))
                .setNegativeButton(getString(R.string.t116), null)
                .setPositiveButton(getString(R.string.t117)) { _, _ ->
                    systemWarnedSession = true
                    action()
                }
                .secureShow()
        } else {
            action()
        }
    }

    private fun renderSuggested() {
        if (!::suggestedBox.isInitialized) return
        suggestedBox.removeAllViews()
        KnownPreinstalls.ALL.forEach { item ->
            val app = uninstallAll.firstOrNull { it.pkg == item.pkg } ?: return@forEach
            suggestedBox.addView(
                label(
                    "${app.label}  ·  ${app.pkg}" +
                        if (app.system) "  ·  ${getString(R.string.t112)}" else "",
                    13f
                ).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, 4.dp, 0, 0)
                }
            )
            suggestedBox.addView(
                label(getString(item.note), 12f).apply {
                    setTextColor(hintColor)
                    setLineSpacing(2f, 1f)
                    setPadding(0, 1.dp, 0, 0)
                }
            )
            suggestedBox.addView(space(8))
        }
        if (suggestedBox.childCount == 0) {
            suggestedBox.addView(
                label(getString(R.string.t437), 12f).apply { setTextColor(hintColor); setPadding(0, 4.dp, 0, 0) }
            )
        }
    }

    private fun showDeviceReport() {
        val text = DeviceReport.report(this)
        val body = TextView(this).apply {
            appTypeface(400, code = true)
            textSize = 12f
            setTextIsSelectable(true)
            setLineSpacing(2f, 1f)
            this@apply.text = text
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 8.dp, 24.dp, 0)
        }
        wrap.addView(body)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.t423))
            .setView(wrap)
            .setNegativeButton(getString(R.string.t116), null)
            .setPositiveButton(getString(R.string.t424)) { _, _ ->
                copyToClipboard(text)
                snack(getString(R.string.t165))
            }
            .secureShow()
    }

    private fun maybeConfirmAllow(app: AccessStore.App, onDone: () -> Unit = {}) {
        if (!Settings.confirmAllow(this)) {
            AccessStore.allow(this, app, AclLevel.READ)
            AccessNotifications.cancel(this, app.uid)
            refreshApps()
            onDone()
            snack(getString(R.string.t296, app.pkg))
            return
        }
        showLevelDialog(
            title = getString(R.string.t197),
            message = getString(R.string.t297, app.pkg, app.uid),
            initial = AclLevel.READ
        ) { level ->
            AccessStore.allow(this, app, level)
            AccessNotifications.cancel(this, app.uid)
            refreshApps()
            onDone()
            snack(getString(R.string.t298, level.label, app.pkg))
        }
    }

    private fun changeLevel(app: AccessStore.App) {
        showLevelDialog(
            title = getString(R.string.t198),
            message = getString(R.string.t297, app.pkg, app.uid),
            initial = app.level
        ) { level ->
            AccessStore.setLevel(this, app, level)
            refreshApps()
            snack(getString(R.string.t299, app.pkg, level.label))
        }
    }

    private fun showLevelDialog(
        title: String,
        message: String,
        initial: AclLevel,
        onPick: (AclLevel) -> Unit
    ) {
        val levels = AclLevel.values()
        var chosen = levels.indexOf(initial).coerceIn(0, levels.size - 1)
        val ids = IntArray(levels.size)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 8.dp, 24.dp, 0)
        }
        if (message.isNotBlank()) {
            body.addView(label(message, 14f).apply { setLineSpacing(2f, 1f) })
            body.addView(space(10))
        }
        val group = android.widget.RadioGroup(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        levels.forEachIndexed { index, level ->
            ids[index] = View.generateViewId()
            group.addView(
                MaterialRadioButton(this).apply {
                    id = ids[index]
                    text = "${level.label} — ${levelHint(level)}"
                    textSize = 15f
                    appTypeface(400)
                    setPadding(0, 10.dp, 0, 10.dp)
                    isChecked = index == chosen
                }
            )
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            chosen = ids.indexOf(checkedId)
        }
        body.addView(group)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(body)
            .setNegativeButton(getString(R.string.t116), null)
            .setPositiveButton(getString(R.string.t199)) { _, _ ->
                val target = levels[chosen]
                if (target == AclLevel.FULL) {

                    BiometricGate.confirm(
                        this@MainActivity,
                        getString(R.string.t200),
                        getString(R.string.t201)
                    ) { onPick(target) }
                } else {
                    onPick(target)
                }
            }
            .secureShow()
    }

    private fun levelHint(level: AclLevel): String = when (level) {
        AclLevel.READ -> getString(R.string.t202)
        AclLevel.PERMISSIONS -> getString(R.string.t203)
        AclLevel.FULL -> getString(R.string.t204)
    }

    private val LANGS = listOf(
        "en" to "English",
        "es" to "Español",
        "pt" to "Português",
        "hi" to "हिन्दी",
        "zh" to "中文",
        "ar" to "العربية",
        "ru" to "Русский"
    )

    private fun currentLangName(): String {
        val tag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: "en"
        return LANGS.firstOrNull { it.first == tag }?.second ?: "English"
    }

    private fun showLanguageDialog() {
        val current = AppCompatDelegate.getApplicationLocales()[0]?.language ?: "en"
        val checked = LANGS.indexOfFirst { it.first == current }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.t370))
            .setSingleChoiceItems(
                LANGS.map { it.second }.toTypedArray(),
                checked
            ) { dlg, which ->
                dlg.dismiss()
                Settings.setLangChosen(this)
                val tag = LANGS[which].first
                if (tag != current) {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(tag)
                    )
                }
            }
            .setNegativeButton(getString(R.string.t116), null)
            .secureShow()
    }

    private fun settingsButton(): View {
        val button = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(themeColor(MR.attr.colorSurfaceVariant))
            }
            foreground = ripple()
            clipToOutline = true
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(44.dp, 44.dp)
            setOnClickListener { showSettings() }
        }
        button.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_settings)
                imageTintList = ColorStateList.valueOf(themeColor(MR.attr.colorOnSurfaceVariant))
                contentDescription = getString(R.string.t205)
            },
            FrameLayout.LayoutParams(22.dp, 22.dp, Gravity.CENTER)
        )
        return button
    }

    private fun showSettings() {
        val sheet = BottomSheetDialog(this)

        if (Settings.flagSecureScreens(this)) {
            sheet.window?.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 24.dp, 24.dp, 32.dp)
        }
        content.addView(label(getString(R.string.t205), 22f, bold = true))
        content.addView(
            label(getString(R.string.t206), 13f)
                .apply { setTextColor(hintColor); setPadding(0, 4.dp, 0, 0) }
        )

        content.addView(space(20))
        val portField = field(
            getString(R.string.t300, Server.PORT),
            InputType.TYPE_CLASS_NUMBER,
            maxLength = 5
        )
        portField.editText?.setText(Settings.port(this).toString())
        content.addView(portField)
        content.addView(space(8))
        content.addView(button(getString(R.string.t207), outlined = true) {
            val p = portField.editText?.text?.toString()?.trim()?.toIntOrNull()
            if (p == null || p !in 1024..65535) {
                snack(getString(R.string.t208), error = true)
            } else {
                Settings.setPort(this, p)
                sheet.dismiss()
                snack(getString(R.string.t209))
            }
        })
        content.addView(
            label(getString(R.string.t210), 12f)
                .apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 0) }
        )

        content.addView(space(20))
        content.addView(switchRow(getString(R.string.t211), Settings.autoCheck(this)) {
            Settings.setAutoCheck(this, it)
        })
        content.addView(space(8))
        content.addView(
            protectiveSwitchRow(
                getString(R.string.t212), Settings.confirmAllow(this),
                getString(R.string.t213),
                getString(R.string.t214)
            ) { Settings.setConfirmAllow(this, it) }
        )
        content.addView(space(8))
        content.addView(
            protectiveSwitchRow(
                getString(R.string.t215),
                Settings.flagSecureScreens(this),
                getString(R.string.t216),
                getString(R.string.t217)
            ) {
                Settings.setFlagSecureScreens(this, it)
                snack(getString(R.string.t218))
            }
        )
        content.addView(space(8))
        content.addView(
            switchRow(getString(R.string.t219), Settings.confirmDevice(this)) {
                Settings.setConfirmDevice(this, it)
            }
        )
        content.addView(space(8))

        content.addView(
            protectiveSwitchRow(
                getString(R.string.t373), Settings.dangerousWarn(this),
                getString(R.string.t380),
                getString(R.string.t381)
            ) { Settings.setDangerousWarn(this, it) }
        )
        content.addView(space(8))
        content.addView(
            switchRow(getString(R.string.t220), Settings.terminalHistory(this)) {
                Settings.setTerminalHistory(this, it)
                if (!it) snack(getString(R.string.t221))
                renderHistory()
            }
        )
        content.addView(space(4))

        content.addView(space(8))
        content.addView(
            button(getString(R.string.t370) + ": " + currentLangName(), outlined = true) {
                showLanguageDialog()
            }
        )
        content.addView(space(4))
        val windowField = field(
            getString(R.string.t222),
            InputType.TYPE_CLASS_NUMBER,
            maxLength = 3
        )
        windowField.editText?.setText(Settings.confirmWindowSec(this).toString())
        content.addView(windowField)
        content.addView(
            button(getString(R.string.t223), outlined = true) {
                val v = windowField.editText?.text?.toString()?.trim()?.toIntOrNull()
                if (v == null || v !in 15..600) {
                    snack(getString(R.string.t224), error = true)
                } else {
                    Settings.setConfirmWindowSec(this, v)
                    snack(getString(R.string.t225, v))
                }
            }
        )

        content.addView(space(20))
        content.addView(divider())
        content.addView(space(16))
        content.addView(label(getString(R.string.t302), 13f, bold = true).apply { letterSpacing = 0.08f })
        content.addView(space(6))
        content.addView(
            label(
                getString(R.string.t226),
                12f
            ).apply { setTextColor(hintColor); setLineSpacing(2f, 1f) }
        )
        content.addView(space(8))
        val writeSecureGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        content.addView(
            label(
                when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R ->
                        getString(R.string.t227)
                    writeSecureGranted ->
                        getString(R.string.t228)
                    else ->
                        getString(R.string.t229)
                },
                12f
            ).apply { setTextColor(hintColor) }
                .also { wssStatusView = it }
        )
        content.addView(space(8))
        lateinit var bootSwitch: MaterialSwitch
        bootSwitch = MaterialSwitch(this).apply {
            text = getString(R.string.t230)
            textSize = 14f
            appTypeface(500)
            isChecked = Settings.bootStart(this@MainActivity)
            setOnCheckedChangeListener(
                object : android.widget.CompoundButton.OnCheckedChangeListener {
                    override fun onCheckedChanged(
                        buttonView: android.widget.CompoundButton,
                        isChecked: Boolean
                    ) {
                        if (!isChecked) {
                            Settings.setBootStart(this@MainActivity, false)
                            return
                        }
                        enableAutostart { success ->
                            if (!success) setSwitchQuietly(this@apply, false, this)
                        }
                    }
                }
            )
        }
        content.addView(bootSwitch)
        content.addView(space(8))
        content.addView(
            switchRow(getString(R.string.t231), Settings.bootDisableWifi(this)) {
                Settings.setBootDisableWifi(this, it)
            }
        )
        content.addView(space(6))
        content.addView(
            label(
                getString(R.string.t232),
                12f
            ).apply { setTextColor(hintColor) }
        )

        content.addView(space(16))
        content.addView(
            label(
                getString(R.string.t454),
                12f
            ).apply { setTextColor(hintColor); setLineSpacing(2f, 1f) }
        )
        content.addView(space(8))
        val wdsField = field(getString(R.string.t455), android.text.InputType.TYPE_CLASS_TEXT)
        wdsField.editText?.setText(Settings.wdsAddress(this))
        content.addView(wdsField)
        content.addView(space(8))
        content.addView(button(getString(R.string.t456), outlined = true) {
            val typed = wdsField.editText?.text?.toString()?.trim().orEmpty()
            when {
                typed.isEmpty() -> {
                    Settings.setWdsAddress(this, "")
                    snack(getString(R.string.t459))
                }
                Settings.parseWdsAddress(typed) == null ->
                    snack(getString(R.string.t458), error = true)
                else -> {
                    Settings.setWdsAddress(this, typed)
                    snack(getString(R.string.t457))
                }
            }
        })

        content.addView(space(20))
        content.addView(divider())
        content.addView(space(16))
        content.addView(label(getString(R.string.t233), 13f, bold = true).apply { letterSpacing = 0.08f })
        content.addView(space(8))
        content.addView(button(getString(R.string.t234), outlined = true) {
            ServerClient.resetToken(this)
            sheet.dismiss()
            snack(getString(R.string.t235))
        })
        content.addView(space(8))
        content.addView(button(getString(R.string.t236), outlined = true) {
            FoxholeAdb.forget(this)
            sheet.dismiss()
            snack(getString(R.string.t237))
        })
        content.addView(space(8))
        content.addView(button(getString(R.string.t423), outlined = true) {
            sheet.dismiss()
            showDeviceReport()
        })

        content.addView(space(24))
        content.addView(divider())
        content.addView(space(16))
        content.addView(label("Foxhole ${versionName()}", 13f))
        content.addView(
            label("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", 13f)
                .apply { setTextColor(hintColor) }
        )
        content.addView(
            label(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    getString(R.string.t238)
                else getString(R.string.t239),
                13f
            ).apply { setTextColor(hintColor) }
        )

        sheet.setContentView(ScrollView(this).apply { addView(content) })
        openSheet = sheet
        sheet.setOnDismissListener { openSheet = null }
        sheet.setOnShowListener {
            val decor = sheet.window?.decorView ?: return@setOnShowListener
            ViewCompat.setOnApplyWindowInsetsListener(decor) { _, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                )
                content.updatePadding(bottom = 32.dp + bars.bottom)
                insets
            }
            ViewCompat.requestApplyInsets(decor)
        }
        sheet.show()
    }

    private fun switchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit): MaterialSwitch =
        MaterialSwitch(this).apply {
            this.text = text
            textSize = 14f
            appTypeface(500)
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }

    private fun protectiveSwitchRow(
        text: String,
        checked: Boolean,
        gateTitle: String,
        gateReason: String,
        setter: (Boolean) -> Unit
    ): MaterialSwitch {
        val sw = MaterialSwitch(this).apply {
            this.text = text
            textSize = 14f
            appTypeface(500)
            isChecked = checked
        }
        val listener = object : android.widget.CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(
                buttonView: android.widget.CompoundButton,
                want: Boolean
            ) {
                if (want) {
                    setter(true)
                    return
                }
                setSwitchQuietly(sw, true, this)
                BiometricGate.confirm(this@MainActivity, gateTitle, gateReason) {
                    setter(false)
                    setSwitchQuietly(sw, false, this)
                }
            }
        }
        sw.setOnCheckedChangeListener(listener)
        return sw
    }

    private fun setSwitchQuietly(
        sw: MaterialSwitch,
        value: Boolean,
        listener: android.widget.CompoundButton.OnCheckedChangeListener
    ) {
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = value
        sw.setOnCheckedChangeListener(listener)
    }

    private fun enableAutostart(onDone: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            snack(getString(R.string.t240), error = true)
            onDone(false)
            return
        }
        if (!FoxholeAdb.hasKey(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.t241))
                .setMessage(
                    getString(R.string.t242)
                )
                .setNegativeButton(getString(R.string.t243), null)
                .setPositiveButton(getString(R.string.t244)) { _, _ ->
                    openSheet?.dismiss()
                    setFoldExpanded(pairingFold, true)
                    val target = pairingFold.header
                    mainScroll.post {
                        mainScroll.smoothScrollTo(0, target.top)
                    }
                }
                .secureShow()
            onDone(false)
            return
        }
        val alreadyGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            Settings.setBootStart(this, true)
            wssStatusView?.text = getString(R.string.t228)
            snack(getString(R.string.t245))
            onDone(true)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.t246))
            .setMessage(
                getString(
                    R.string.t301,
                    packageName,
                    Manifest.permission.WRITE_SECURE_SETTINGS
                )
            )
            .setNegativeButton(getString(R.string.t116)) { _, _ -> onDone(false) }
            .setPositiveButton(getString(R.string.t247)) { _, _ ->
                bg(getString(R.string.t248), {
                    ServerClient.request(
                        this,
                        "pm grant $packageName ${Manifest.permission.WRITE_SECURE_SETTINGS}"
                    )
                }) { res ->
                    if (res.startsWith("OK")) {
                        Settings.setBootStart(this, true)
                        wssStatusView?.text = getString(R.string.t228)
                        snack(getString(R.string.t249))
                        onDone(true)
                    } else {
                        snack(
                            getString(R.string.t250),
                            error = true
                        )
                        onDone(false)
                    }
                }
            }
            .secureShow()
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private fun section(root: LinearLayout, title: String, desc: String? = null): LinearLayout {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 20.dp, 20.dp, 20.dp)
        }
        content.addView(label(title, 17f, bold = true))
        if (desc != null) {
            content.addView(
                label(desc, 13f).apply {
                    setTextColor(hintColor)
                    setPadding(0, 6.dp, 0, 0)
                    setLineSpacing(3f, 1f)
                }
            )
        }
        root.addView(
            cardFor(content),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = 14.dp }
        )
        return content
    }

    private fun fold(
        root: LinearLayout,
        title: String,
        expanded: Boolean,
        locked: Boolean = false,
        persistKey: String? = null
    ): Fold {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 0, 20.dp, 20.dp)
        }
        val header = TextView(this).apply {
            textSize = 17f
            appTypeface(700)
            setPadding(20.dp, 18.dp, 20.dp, 18.dp)
        }
        val fold = Fold(header, content, title, expanded, locked, persistKey)
        if (locked) {
            header.text = title
            header.isClickable = false
        } else {
            header.isClickable = true
            header.background = ripple()
            header.setOnClickListener {
                setFoldExpanded(fold, !fold.expanded)
            }
        }
        setFoldExpanded(fold, expanded, animate = false)
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(header)
        inner.addView(content)

        root.addView(
            cardFor(inner),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = 14.dp }
        )
        return fold
    }

    private fun setFoldExpanded(fold: Fold, expanded: Boolean, animate: Boolean = true) {
        val target = if (fold.locked) true else expanded
        fold.header.text =
            if (fold.locked) fold.title else (if (target) "▾  " else "▸  ") + fold.title
        if (target == fold.expanded) {
            animateFold(fold, target, animate = false)
            return
        }
        fold.expanded = target
        fold.persistKey?.let { Settings.setFoldExpanded(this, it, target) }
        animateFold(fold, target, animate)
    }

    private fun animateFold(fold: Fold, expand: Boolean, animate: Boolean) {
        val content = fold.content
        if (!animate) {
            content.visibility = if (expand) View.VISIBLE else View.GONE
            content.alpha = 1f
            content.translationY = 0f
            return
        }
        fold.header.animate().scaleX(1.02f).scaleY(1.06f).setDuration(90)
            .withEndAction {
                fold.header.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }
            .start()
        if (expand) {
            content.visibility = View.VISIBLE
            content.translationY = (-14).dp.toFloat()
            content.alpha = 0f
            content.animate().translationY(0f).alpha(1f).setDuration(190)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                .start()
        } else {
            content.animate().translationY((-14).dp.toFloat()).alpha(0f).setDuration(150)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    content.visibility = View.GONE
                    content.translationY = 0f
                    content.alpha = 1f
                }
                .start()
        }
    }

    private fun cardFor(content: View): MaterialCardView =
        MaterialCardView(this).apply {
            radius = 24.dp.toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp
            strokeColor = themeColor(MR.attr.colorOutlineVariant)
            addView(
                content,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            )
        }

    private fun ripple(): android.graphics.drawable.Drawable? {
        val tv = TypedValue()
        return if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            androidx.core.content.ContextCompat.getDrawable(this, tv.resourceId)
        } else {
            null
        }
    }

private fun label(text: String, sizeSp: Float, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sizeSp
            appTypeface(if (bold) 700 else 400)
        }

    private fun button(text: String, outlined: Boolean = false, onClick: () -> Unit): MaterialButton =
        MaterialButton(
            this, null,
            if (outlined) MR.attr.materialButtonOutlinedStyle else MR.attr.materialButtonStyle
        ).apply {
            this.text = text
            isAllCaps = false
            maxLines = 1
            appTypeface(500)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 10, 14, 1, TypedValue.COMPLEX_UNIT_SP
            )
            cornerRadius = 14.dp
            setOnClickListener { onClick() }
        }

    private fun row(vararg views: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEachIndexed { i, v ->
                addView(
                    v,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { if (i > 0) marginStart = 8.dp }
                )
            }
        }

    private fun field(hint: String, inputType: Int, maxLength: Int = 0, id: Int = 0): TextInputLayout =
        TextInputLayout(this).apply {
            this.hint = hint
            addView(
                TextInputEditText(this@MainActivity).apply {
                    this.id = id
                    this.inputType = inputType
                    appTypeface(400)
                    if (maxLength > 0) filters = arrayOf(InputFilter.LengthFilter(maxLength))
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            )
        }

    private fun space(dp: Int): View =
        View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp.dp) }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density + 0.5f).toInt()

    private class Fold(
        val header: TextView,
        val content: LinearLayout,
        val title: String,
        var expanded: Boolean,
        val locked: Boolean = false,
        val persistKey: String? = null
    )

    private data class InstalledApp(val pkg: String, val label: String, val uid: Int)

    companion object {
        private const val AUTO_CHECK_MS = 5_000L

        private val CONSOLE_BG = 0xFF10131A.toInt()
        private val CONSOLE_TEXT = 0xFFD7DEE8.toInt()
        private val CONSOLE_PROMPT = 0xFF8AB4F8.toInt()
        private val CONSOLE_ERR = 0xFFFF8A80.toInt()

        const val EXTRA_OPEN_PENDING = "foxhole_open_pending"

        private const val UNINSTALL_MAX = 120

        private const val FILTER_USER = 0
        private const val FILTER_SYSTEM = 1
        private const val FILTER_ALL = 2
    }
}
