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
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
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
    private lateinit var uninstallFooterButton: MaterialButton
    private var serverOnline: Boolean? = null
    private var pulse: ObjectAnimator? = null
    private lateinit var mainScroll: ScrollView
    private var wssStatusView: TextView? = null

    private var openSheet: BottomSheetDialog? = null

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
            label("Shell-доступ без root", 13f)
                .apply { setTextColor(hintColor); setPadding(0, 2.dp, 0, 0) }
        )
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(settingsButton())
        root.addView(header)

        buildStatusCard(root)

        val pcFold = fold(root, "Запуск с ПК", expanded = true, locked = true)
        pcFold.content.addView(space(12))
        pcFold.content.addView(
            label(
                "1. Включи «Отладку по USB» и подключи телефон кабелем к компьютеру.\n" +
                    "2. Нажми «Посмотреть команду», затем перешли её на ПК или скопируй.\n" +
                    "3. Вставь команду в терминал на компьютере: она запустит сервер по ADB.\n\n" +
                    "Спаривание по Wi-Fi для этого не нужно. После запуска кабель можно отключить.",
                13f
            ).apply { setTextColor(hintColor) }
        )
        pcFold.content.addView(space(10))
        pcFold.content.addView(button("Посмотреть команду") { showPcCommandDialog() })
        pcFold.content.addView(space(12))

        pairingFold = fold(root, "Запуск без ПК", expanded = true, locked = true)
        pairingFold.content.addView(space(12))
        pairingFold.content.addView(
            label(
                "1. Включи «Беспроводную отладку» — кнопка ниже откроет настройки.\n" +
                    "2. Нажми «Подключение по коду»: система покажет порт и код.\n" +
                    "3. Не закрывая настройки, потяни шторку и введи порт и код в уведомлении Foxhole.\n\n" +
                    "Foxhole спарит и предложит открыть приложение — сервер запустится сам.\n" +
                    "Код действует около 2 минут.\n\n" +
                    "Про ПК: спаривание `adb pair` даёт ключ компьютеру, а не Foxhole — " +
                    "для автозапуска нужен ключ самого Foxhole, поэтому шаг выше обязателен.\n\n" +
                    "Важно: спаривание по Wi-Fi работает только на Android 11 (API 30) и новее. " +
                    "На более старых версиях используй «Запуск с ПК».",
                13f
            ).apply { setTextColor(hintColor) }
        )
        pairingFold.content.addView(space(10))
        val openSettingsButton = button("Открыть настройки отладки", outlined = true) {
            openWirelessDebugging()
        }
        pairingFold.content.addView(openSettingsButton)
        pairPortField = field(
            "Порт спаривания",
            InputType.TYPE_CLASS_NUMBER,
            maxLength = 5,
            id = R.id.foxhole_pair_port
        ).apply {
            isHelperTextEnabled = true
            helperText = "Пока открыто окно с кодом (кабель или разделённый экран)"
        }
        pairCodeField = field(
            "Код спаривания (6 цифр)", InputType.TYPE_CLASS_NUMBER, maxLength = 6,
            id = R.id.foxhole_pair_code
        )
        pairingFold.content.addView(space(12))
        pairingFold.content.addView(pairPortField)
        pairingFold.content.addView(space(8))
        pairingFold.content.addView(pairCodeField)
        pairingFold.content.addView(space(10))
        pairButton = button("Спарить", outlined = true) { pairFromFields() }
        pairingFold.content.addView(pairButton)
        pairingFold.content.addView(
            label(
                "Порт спаривания держится открытым, только пока окно с кодом на экране — " +
                    "например, если вернуть Foxhole в разделённый экран настроек.",
                12f
            ).apply { setTextColor(hintColor) }
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            pairingFold.content.addView(space(12))
            pairingFold.content.addView(
                label(
                    "На Android ${Build.VERSION.RELEASE} спаривание по Wi-Fi недоступно: " +
                        "нужен Android 11+. Используй «Запуск с ПК».", 13f
                ).apply { setTextColor(themeColor(MR.attr.colorError)) }
            )
            listOf(pairPortField, pairCodeField).forEach {
                it.isEnabled = false
                it.editText?.isEnabled = false
                it.alpha = 0.5f
            }
            pairPortField.helperText = "Недоступно на этой версии Android"
            openSettingsButton.isEnabled = false
            pairButton.isEnabled = false
            pairButton.alpha = 0.5f
        }

        val cmdField = field(
            "Команда shell",
            InputType.TYPE_CLASS_TEXT,
            id = R.id.foxhole_cmd_input
        )
        section(
            root, "Команды",
            "Любые shell-команды от uid 2000: dumpsys, ps, ls, pm, settings и т.д. " +
                "Вывод — в консоли ниже."
        ).apply {
            addView(space(12))
            addView(cmdField)
            addView(space(12))
            addView(button("Отправить") {
                send(cmdField.editText?.text?.toString().orEmpty())
            })
            addView(space(8))
            addView(
                label(
                    "  dumpsys battery\n" +
                        "  pm list packages | head -n 5\n" +
                        "  settings get global adb_enabled",
                    12f
                ).apply { setTextColor(hintColor) }
            )
            addView(space(16))
            addView(label("Вывод", 13f, bold = true))
            addView(space(8))
            out = TextView(this@MainActivity).apply {
                id = R.id.foxhole_output
                appTypeface(400, code = true)
                textSize = 12f
                freezesText = true
                setTextIsSelectable(true)
                setLineSpacing(4f, 1f)
                setPadding(14.dp, 14.dp, 14.dp, 14.dp)
                setHintTextColor(hintColor)
                hint = "Вывод появится здесь."
                background = GradientDrawable().apply {
                    setColor(themeColor(MR.attr.colorSurfaceVariant))
                    cornerRadius = 14.dp.toFloat()
                }
            }
            addView(out)
            addView(space(8))
            addView(button("Очистить вывод", outlined = true) { out.text = "" })
        }

        val appsTitle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Приложения", 17f, bold = true))
            addView(
                label(
                    "Запросы доступа, уровни и список установленных приложений. " +
                        "Работает через сервер.",
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
            addView(label("Удаление приложений", 17f, bold = true))
            addView(
                label(
                    "Выбор пакетов, корзина «Удалённые», восстановление. Работает через сервер.",
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

        paneRoot = buildUninstallPane()
        paneRoot.visibility = View.GONE
        onBackPressedDispatcher.addCallback(this, paneBackCallback)
        onBackPressedDispatcher.addCallback(this, appsBackCallback)

        val journal = fold(root, "Журнал приложений", expanded = Settings.foldExpanded(this, "journal", def = false), persistKey = "journal")
        val logView = TextView(this).apply {
            appTypeface(400, code = true)
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, 12.dp, 0, 0)
        }
        journal.content.addView(space(8))
        journal.content.addView(row(
            button("Показать") {
                logView.text = AccessStore.readLog(this@MainActivity).ifBlank { "пусто" }
            },
            button("Очистить", outlined = true) {
                AccessStore.clearLog(this@MainActivity)
                logView.text = "Журнал очищен"
            }
        ))
        journal.content.addView(logView)

        val about = fold(root, "Как это работает", expanded = Settings.foldExpanded(this, "how", def = false), persistKey = "how")
        about.content.addView(space(8))
        about.content.addView(
            label(
                "Android разрешает выполнять команды от имени shell (uid 2000) через ADB — " +
                    "это не root (uid 0), но достаточно для pm, appops, settings, dumpsys и любых shell-команд.\n\n" +
                    "Foxhole запускает Java-процесс через app_process от имени shell и общается " +
                    "с ним по localhost с токеном. Сторонние приложения обращаются к " +
                    "ContentProvider com.sitikplay.foxhole.bridge и получают доступ только " +
                    "после твоего разрешения.",
                13f
            ).apply { setTextColor(hintColor) }
        )

        val guides = fold(root, "Гайды", expanded = Settings.foldExpanded(this, "guides", def = false), persistKey = "guides")
        guides.content.addView(space(12))
        guides.content.addView(label("Беспроводная отладка (Android 11+)", 14f, bold = true))
        guides.content.addView(space(6))
        guides.content.addView(
            label(
                "1. Настройки → Для разработчиков → «Беспроводная отладка».\n" +
                    "2. Открой «Подключение устройства по коду»: там видны IP, порт и код.\n" +
                    "3. Не закрывая настройки, открой шторку, разверни уведомление Foxhole, нажми «Ввести порт и код» и введи порт и код.\n\n" +
                    "С ПК вместо шага 3:\n" +
                    "adb pair <IP>:<порт спаривания> <код>\n" +
                    "adb connect <IP>:<порт отладки>\n\n" +
                    "Порт спаривания — из окна с кодом. Порт отладки — с экрана\n" +
                    "«Беспроводная отладка». Код живёт около 2 минут.",
                12f
            ).apply {
                setTextColor(hintColor)
                setTextIsSelectable(true)
                setLineSpacing(3f, 1f)
            }
        )
        guides.content.addView(space(20))
        guides.content.addView(label("Shell с ПК (любой Android)", 14f, bold = true))
        guides.content.addView(space(6))
        guides.content.addView(
            label(
                "1. Включи «Отладка по USB» и подключи телефон кабелем.\n" +
                    "2. Проверь, что компьютер видит устройство:\n" +
                    "adb devices\n" +
                    "3. В Foxhole нажми «Посмотреть команду» и скопируй её.\n" +
                    "4. Выполни команду в терминале на ПК:\n" +
                    "adb shell sh \"/storage/emulated/0/Android/data/com.sitikplay.foxhole/files/start.sh\"\n\n" +
                    "Сервер живёт до перезагрузки телефона или принудительной\n" +
                    "остановки Foxhole. Спаривание по Wi-Fi для этого не нужно.\n" +
                    "Чтобы сервер заработал снова после перезагрузки, повтори шаг 4.",
                12f
            ).apply {
                setTextColor(hintColor)
                setTextIsSelectable(true)
                setLineSpacing(3f, 1f)
            }
        )
        guides.content.addView(space(12))
        guides.content.addView(button("Показать команду для ADB", outlined = true) {
            showAdbPairCommand()
        })
        guides.content.addView(space(8))
        guides.content.addView(
            label(
                "Готовые adb pair и adb connect — для спаривания с компьютера. " +
                    "IP и порт смотри в окне «Подключение по коду», код возьми оттуда же.",
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
        appsPaneTitle = label("Приложения", 19f, bold = true)
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
            text = "Доступ"; isCheckable = true; isAllCaps = false
            textSize = 13f; appTypeface(500)
        }
        val tabInstalled = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_tab_installed
            text = "Установленные"; isCheckable = true; isAllCaps = false
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
                "Разрешения привязаны к uid — подделать нельзя. Приложение попадёт в список " +
                    "ожидающих, когда впервые попросит доступ.",
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
            label("Разреши приложение заранее — ждать его запроса не обязательно.", 13f)
                .apply { setTextColor(hintColor); setLineSpacing(3f, 1f) }
        )
        tabInstalledContent.addView(space(10))
        val search = field("Поиск: название или пакет", InputType.TYPE_CLASS_TEXT)
        tabInstalledContent.addView(search)
        tabInstalledContent.addView(space(8))
        installedBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        tabInstalledContent.addView(installedBox)
        tabInstalledContent.addView(space(10))
        tabInstalledContent.addView(button("Обновить список", outlined = true) {
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
        appsPaneTitle.text = if (showAppsTab) "Приложения — доступ" else "Установленные приложения"
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
        paneTitle = label("Удаление приложений", 19f, bold = true)
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
            text = "Удаление"; isCheckable = true; isAllCaps = false
            textSize = 13f; appTypeface(500)
        }
        val tabDeleted = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_tab_restore
            text = "Восстановление"; isCheckable = true; isAllCaps = false
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
                "Нужен работающий сервер. Можно выбрать любой пакет — никакого защитного " +
                    "списка нет.\n\n" +
                    "Пользовательские (pm uninstall): APK заранее копируется в корзину " +
                    "на ${UninstallCommands.RETENTION_DAYS} дней, оттуда приложение можно " +
                    "вернуть — данные при этом не восстанавливаются.\n\n" +
                    "Системные (pm uninstall -k --user 0): физически приложение остаётся в " +
                    "системном разделе, место не освобождается — оно просто скрывается для " +
                    "тебя. Вернуть можно в любой момент на вкладке «Восстановление».",
                13f
            ).apply { setTextColor(hintColor); setLineSpacing(3f, 1f) }
        )
        tabUninstallContent.addView(space(10))
        val uninstallSearch = field("Поиск: название или пакет", InputType.TYPE_CLASS_TEXT)
        tabUninstallContent.addView(uninstallSearch)
        tabUninstallContent.addView(space(8))

        val filterGroup = MaterialButtonToggleGroup(this)
        val btnUser = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_filter_user
            text = "Пользовательские"; isCheckable = true; isAllCaps = false
            textSize = 12f; appTypeface(500)
        }
        val btnSystem = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_filter_system
            text = "Системные"; isCheckable = true; isAllCaps = false
            textSize = 12f; appTypeface(500)
        }
        val btnAll = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
            id = R.id.foxhole_filter_all
            text = "Все"; isCheckable = true; isAllCaps = false
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
                button("Выбрать всё", outlined = true) { selectAllUninstall() },
                button("Снять выбор", outlined = true) {
                    uninstallSelected.clear()
                    renderUninstall()
                }
            )
        )
        tabUninstallContent.addView(space(10))
        uninstallFooterButton = button("Удалить выбранные") { confirmUninstall() }
        tabUninstallContent.addView(uninstallFooterButton)
        tabUninstallContent.addView(space(8))
        tabUninstallContent.addView(button("Обновить список", outlined = true) {
            uninstallLoaded = false
            loadUninstallApps()
        })
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
                "APK пользовательских приложений хранятся ${UninstallCommands.RETENTION_DAYS} дней, " +
                    "затем удаляются автоматически: кнопка «Восстановить» пропадёт. Скрытое " +
                    "системное приложение остаётся в системе — вернуть его после этого срока " +
                    "можно только командой pm install-existing --user 0 <пакет>.\n\n" +
                    "Восстановление возвращает только приложение, БЕЗ данных.",
                13f
            ).apply { setTextColor(hintColor); setLineSpacing(3f, 1f) }
        )
        deletedBox.addView(space(10))
        deletedList = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        deletedBox.addView(deletedList)
        deletedBox.addView(space(10))
        deletedBox.addView(button("Восстановить всё", outlined = true) { confirmRestoreAll() })
        deletedBox.addView(space(8))
        deletedBox.addView(
            row(
                button("Экспорт списка", outlined = true) { exportDeleted() },
                button("Очистить корзину", outlined = true) { confirmClearDeleted() }
            )
        )
        deletedBox.addView(space(8))
        deletedBox.addView(button("Обновить", outlined = true) { renderDeleted() })

        return pane
    }

    private fun openUninstallPane() {
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
        paneTitle.text = if (showUninstallTab) "Удаление приложений" else "Корзина «Удалённые»"
        tabUninstallContent.visibility = if (showUninstallTab) View.VISIBLE else View.GONE
        tabDeletedContent.visibility = if (showUninstallTab) View.GONE else View.VISIBLE
        paneScroll.scrollTo(0, 0)
    }

    private fun maybeAutoStart(intent: Intent?) {
        if (intent?.getBooleanExtra(PairService.EXTRA_AUTO_START, false) != true) return
        intent.removeExtra(PairService.EXTRA_AUTO_START)
        uiHandler.postDelayed({
            bg("автозапуск", { ServerClient.request(this, "ping") }) { res ->
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
                AccessStore.log(this, "корзина: автоочистка при открытии — удалено записей: $removed")
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
            button("Остановить", outlined = true) { stopServer() }.apply {
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
            true -> "Сервер работает"
            false -> "Сервер остановлен"
            null -> "Сервер запускается"
        }
        statusSubtitle.text = when (online) {
            true -> "uid 2000 · shell · localhost:${Settings.port(this)}"
            false -> "Запусти спаривание или сервер снова получит доступ"
            null -> "Проверка каждые 5 с — статус обновится сам"
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
                    snack(if (online) "Сервер отвечает" else "Сервер не отвечает", error = !online)
                }
            }
        }.start()
    }

    private fun stopServer() {
        bg("остановка сервера", { ServerClient.request(this, "stop")         }) { res ->
            val ok = res.startsWith("OK")
            if (ok) {
                setStatus(false)
                Settings.setStoppedBootCount(this, currentBootCount())
                snack(
                    if (Settings.bootStart(this)) {
                        "Сервер остановлен. До следующей перезагрузки автозапуск его не включит"
                    } else {
                        "Сервер остановлен"
                    }
                )
            } else if (res.contains("сервер недоступен")) {
                setStatus(false)
                Settings.setStoppedBootCount(this, currentBootCount())
                snack("Сервер уже не отвечает — считаю его остановленным")
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
            snack("Введи 6 цифр кода из окна «Подключение по коду»", error = true)
            return
        }
        if (typedPort == null || typedPort <= 0) {
            snack("Введи порт спаривания из того же окна", error = true)
            return
        }
        pairPortError(typedPort, Settings.port(this))?.let {
            snack(it, error = true)
            return
        }
        bg("спаривание", {
            val adb = FoxholeAdb.get(this@MainActivity)
            adb.setTimeout(30, TimeUnit.SECONDS)
            val service = discoverService(AdbMdns.SERVICE_TYPE_TLS_PAIRING, 4_000)
            val host = service?.first ?: localIpv4()
                ?: return@bg "ERR|Телефон не в Wi-Fi — жми «Открыть настройки отладки»"
            val paired = try {
                adb.pair(host, service?.second ?: typedPort, code)
            } catch (e: Exception) {
                return@bg "ERR|${friendlyAdbError(e)}"
            }
            if (!paired) {
                try {
                    if (adb.connectTls(this@MainActivity, 6_000)) {
                        FoxholeAdb.markPaired(this@MainActivity)
                        return@bg "OK|Уже спарено с $host"
                    }
                } catch (e: Exception) {
                }
                return@bg "ERR|Устройство отклонило спаривание: возьми свежий код"
            }
            "OK|Спарено с $host:${service?.second ?: typedPort}"
        }) { res ->
            finishAction(res, onError = { msg ->
                if (msg.contains("Wi-Fi", ignoreCase = true) ||
                    msg.contains("отладку", ignoreCase = true)
                ) {
                    setFoldExpanded(pairingFold, true)
                }
            })
            if (res.startsWith("OK")) startServer()
        }
    }

    private fun startServer() {
        bg("запуск сервера", {
            val path = writeScript() ?: return@bg "ERR|Нет доступа к папке приложения"
            val adb = FoxholeAdb.get(this@MainActivity)
            var connected = false
            var connectError = "ADB не найден в сети. Включена ли беспроводная отладка?"
            for (attempt in 0 until 2) {
                try {
                    if (adb.connectTls(this@MainActivity, 8_000)) {
                        FoxholeAdb.markPaired(this@MainActivity)
                        connected = true
                        break
                    }
                    connectError = "ADB не принял подключение"
                } catch (e: AdbPairingRequiredException) {
                    return@bg "ERR|Спаривание с ПК не подходит: нужен ключ самого Foxhole («Запуск без ПК» → «Спарить»)"
                } catch (e: AdbAuthenticationFailedException) {
                    return@bg "ERR|ADB отклонил ключ. Нажми «Забыть ADB-ключ» и спарь заново"
                } catch (e: InterruptedException) {
                    connectError = "ADB не найден в сети. Включена ли беспроводная отладка?"
                } catch (e: Exception) {
                    connectError = "Не удалось подключиться: ${e.message}"
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
                return@bg "ERR|Не удалось запустить: ${e.message}"
            }
            repeat(6) {
                if (ServerClient.request(this@MainActivity, "ping").startsWith("OK pong")) {
                    return@bg "OK|Сервер запущен и отвечает"
                }
                Thread.sleep(800)
            }
            "ERR|Запуск отправлен, но сервер не отвечает. Журнал: /data/local/tmp/foxhole.log"
        }) { res -> finishAction(res, onError = { msg ->
            if (msg.contains("спаривание", ignoreCase = true) ||
                msg.contains("ADB не найден", ignoreCase = true) ||
                msg.contains("Беспроводная отладка", ignoreCase = true)
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
                snack("Без уведомлений не получится ввести код, не закрывая настройки", error = true)
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
            snack("Открой Настройки → Для разработчиков → Беспроводная отладка", error = true)
        }
    }

    private fun showAdbPairCommand() {
        val code = pairCodeField.editText?.text?.toString()?.trim().orEmpty()
        val typedPort = pairPortField.editText?.text?.toString()?.trim()?.toIntOrNull()
        bg("команда ADB", {
            val pairService = discoverService(AdbMdns.SERVICE_TYPE_TLS_PAIRING, 4_000)
            val connectService = discoverService(AdbMdns.SERVICE_TYPE_TLS_CONNECT, 4_000)
            val host = pairService?.first ?: connectService?.first ?: localIpv4() ?: "<IP-телефона>"
            val pairPort = pairService?.second ?: typedPort
            val debugPort = connectService?.second
            val pairCode = if (code.length == 6) code else "<код>"
            val hostText = if (host.contains(':')) "[$host]" else host
            "OK|adb pair $hostText:${pairPort ?: "<порт спаривания>"} $pairCode\n" +
                "adb connect $hostText:${debugPort ?: "<порт отладки>"}"
        }) { res ->
            val tv = TextView(this).apply {
                text = res.substringAfter("|")
                appTypeface(400, code = true)
                textSize = 13f
                setTextIsSelectable(true)
                setPadding(24.dp, 8.dp, 24.dp, 0)
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Спаривание через ADB")
                .setMessage("Выполни на компьютере:")
                .setView(tv)
                .setPositiveButton("OK", null)
                .show()
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
                label("Пока пусто. Когда стороннее приложение попросит доступ, оно появится здесь.", 13f)
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
                "Разрешить" to { maybeConfirmAllow(app) },
                "Отклонить" to {
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
                "Уровень" to { changeLevel(app) },
                "Отозвать" to { AccessStore.revoke(this, app) }
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
        texts.addView(label(app.pkg, 15f, bold = true))
        texts.addView(
            label(
                "uid ${app.uid} · " +
                    (if (pending) "ожидает решения" else "доступ разрешён · ${app.level.label}"),
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
            label("Загрузка списка…", 13f).apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 6.dp) }
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
                    if (installedAll.isEmpty()) "Список пуст" else "Ничего не найдено",
                    13f
                ).apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 6.dp) }
            )
            return
        }
        installedBox.addView(
            label("${list.size} приложений", 12f).apply {
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
                label("Показаны первые 80 из ${list.size}. Уточни поиск.", 12f).apply {
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
            button("Отозвать", outlined = true) {
                AccessStore.revoke(this, AccessStore.App(app.uid, app.pkg))
                refreshApps()
                renderInstalled()
                snack("Доступ отозван: ${app.pkg}")
            }
        } else {
            button("Разрешить") {
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
            label("Загрузка списка…", 13f).apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 6.dp) }
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
                renderUninstall()
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
                    if (uninstallAll.isEmpty()) "Список пуст" else "Ничего не найдено",
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
                    "Показаны первые ${shown.size} из ${list.size}. Уточни поиск или фильтр.",
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
                app.pkg + (if (app.system) " · системное" else " · пользовательское"),
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
            contentDescription = "Выбрать ${app.label}"
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
            .setTitle("Осторожно: системное приложение")
            .setMessage(
                "Удаление некоторых системных пакетов может сломать телефон или помешать " +
                    "его загрузке.\n\nТы отвечаешь за свои действия: дальше Foxhole спросит " +
                    "только подтверждение перед удалением."
            )
            .setNegativeButton("Отмена") { _, _ ->
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
            .setPositiveButton("Понимаю, продолжить") { _, _ ->
                systemWarnedSession = true
                uninstallSelected.add(app.pkg)
                box.isChecked = true
                updateUninstallFooter()
            }
            .show()
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
                .setTitle("Осторожно: выбраны системные приложения")
                .setMessage(
                    "Удаление некоторых системных пакетов может сломать телефон или помешать " +
                        "его загрузке.\n\nТы отвечаешь за свои действия."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Понимаю, продолжить") { _, _ ->
                    systemWarnedSession = true
                    action()
                }
                .show()
        } else {
            action()
        }
    }

    private fun updateUninstallFooter() {
        if (!::uninstallFooterButton.isInitialized) return
        val n = uninstallSelected.size
        uninstallFooterButton.text =
            if (n == 0) "Удалить выбранные" else "Удалить выбранные ($n)"
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
            "Удаление ${selected.size} ${appsWord(selected.size)} (системных: " + selected.count { it.system } + ")"
        } else {
            "Удалить $userCount ${appsWord(userCount)}?"
        }
        body.addView(
            TextView(this).apply {
                text = selected.take(20).joinToString("\n") { "· ${it.pkg}" } +
                    if (selected.size > 20) "\n· … и ещё ${selected.size - 20}" else ""
                appTypeface(400, code = true)
                textSize = 12f
                setTextIsSelectable(true)
            }
        )
        if (hasSystem) {
            body.addView(space(10))
            body.addView(
                label(
                    "Осторожно: удаление некоторых системных пакетов может сломать телефон " +
                        "или помешать его загрузке. Ты отвечаешь за свои действия.",
                    12f
                ).apply { setTextColor(themeColor(MR.attr.colorError)); setLineSpacing(3f, 1f) }
            )
        }
        body.addView(space(14))
        val irreversible = MaterialCheckBox(this).apply {
            text = "Удалить безвозвратно"
            textSize = 13f
            appTypeface(500)
        }
        body.addView(irreversible)
        body.addView(
            label(
                "Обычно APK копируется в корзину «Удалённые». Безвозвратно: у пользовательских " +
                    "приложений копия не сохраняется (вернуть можно только заново скачав), " +
                    "у системных — не создаётся записи (но приложение физически остаётся " +
                    "скрытым в системе).",
                12f
            ).apply { setTextColor(hintColor); setLineSpacing(2f, 1f) }
        )

        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(body)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить", null)
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
        dlg.show()
    }

    private fun appsWord(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "приложение"
        n % 10 in 2..4 && (n % 100 < 12 || n % 100 > 14) -> "приложения"
        else -> "приложений"
    }

    private fun pkgsWord(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "пакет"
        n % 10 in 2..4 && (n % 100 < 12 || n % 100 > 14) -> "пакета"
        else -> "пакетов"
    }

    private fun performUninstall(selected: List<UninstallApp>, irreversible: Boolean) {
        Thread {
            var ok = 0
            var skipped = 0
            var failed = 0
            for (app in selected) {
                runOnUiThread { show("> удаление ${app.pkg}\n…") }
                val res = uninstallOne(app, irreversible)
                runOnUiThread {
                    show("> удаление ${app.pkg}\n" + res)
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
                    "Удалено: $ok, пропущено: $skipped, ошибок: $failed",
                    error = failed > 0
                )
            }
        }.start()
    }

    private fun uninstallOne(app: UninstallApp, irreversible: Boolean): String {
        var apkDirPath: String? = null
        if (!app.system && !irreversible) {
            val dir = DeletedStore.apkDirFor(this, app.pkg)
            val pmPath = ServerClient.request(this, "pm path ${app.pkg}")
            val paths = UninstallCommands.parsePmPath(pmPath.substringAfter(" ", ""))
            if (paths.isEmpty()) {
                AccessStore.log(this, "удаление ${app.pkg}: pm path не дал путей, APK не сохранён")
                if (!confirmContinueNoBackup(app.pkg)) {
                    return "SKIP ${app.pkg}: пропущен"
                }
            } else {
                val copied = copyApkFiles(paths, dir)
                if (!copied) {
                    AccessStore.log(this, "удаление ${app.pkg}: копия APK не удалась")
                    if (!confirmContinueNoBackup(app.pkg)) {
                        removeApkDir(app.pkg, dir.absolutePath)
                        return "SKIP ${app.pkg}: пропущен"
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
        AccessStore.log(this, "удаление ${app.pkg}: ${r.take(120)}")
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
        return "OK ${app.pkg}: удалено (${r.substringAfter(" ", "").trim()})"
    }

    private fun friendlyHideError(system: Boolean, raw: String): String =
        if (system && raw.contains("only root can delete system app", ignoreCase = true)) {
            "Прошивка не даёт shell скрывать системные приложения — нужен root"
        } else {
            raw
        }

    private fun copyApkFiles(paths: List<String>, dir: File): Boolean {
        val cmd = buildString {
            append("mkdir -p \"${dir.absolutePath}\"")
            paths.forEach { src ->
                val dst = File(dir, File(src).name).absolutePath
                append(" && cp -f \"${UninstallCommands.quoteShellArg(src)}\" \"$dst\"")
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
                .setTitle("APK не сохранён")
                .setMessage(
                    "Не удалось скопировать APK $pkg в корзину — после удаления восстановить " +
                        "это приложение будет невозможно.\n\nПродолжить?"
                )
                .setNegativeButton("Пропустить пакет") { _, _ -> latch.countDown() }
                .setPositiveButton("Удалить всё равно") { _, _ ->
                    proceed = true
                    latch.countDown()
                }
                .show()
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
            AccessStore.log(this, "корзина: не удалось удалить файлы ${pkg}: ${e.message}")
        }
    }

    private fun renderDeleted() {
        if (!::deletedBox.isInitialized) return
        Thread {
            val n = DeletedStore.cleanup(this)
            if (n > 0) AccessStore.log(this, "корзина: автоочистка при открытии — удалено записей: $n")
            runOnUiThread { renderDeletedRows() }
        }.start()
    }

    private fun renderDeletedRows() {
        if (!::deletedList.isInitialized) return
        deletedList.removeAllViews()
        val records = DeletedStore.all(this)
        if (records.isEmpty()) {
            deletedList.addView(
                label("Корзина пуста. Здесь появятся удалённые приложения.", 13f)
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
            label("Корзина занимает ${formatBytes(size)}.", 12f).apply { setTextColor(hintColor) }
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
                "${r.pkg} · ${if (r.system) "системное" else "пользовательское"} " +
                    "· удалено ${df.format(java.util.Date(r.removedAt))}",
                11f
            ).apply { setTextColor(hintColor); maxLines = 1; setPadding(0, 1.dp, 0, 0) }
        )
        val days = UninstallCommands.daysLeft(r.removedAt, now)
        box.addView(
            label(
                "осталось $days дн. из ${UninstallCommands.RETENTION_DAYS}" +
                    (if (!r.restorePossible) " · восстановление невозможно" else
                        if (r.system) " · вернётся в систему" else " · вернётся с APK"),
                11f
            ).apply { setTextColor(hintColor); maxLines = 1; setPadding(0, 1.dp, 0, 0) }
        )
        box.addView(space(8))
        box.addView(
            row(
                button("Восстановить", outlined = true) { confirmRestore(r) },
                button("Удалить сейчас", outlined = true) { confirmForget(r) }
            )
        )
        return box
    }

    private fun confirmRestore(r: DeletedStore.Record) {
        if (!r.restorePossible && !r.system) {
            snack("Сохранённого APK нет и восстановить нельзя: ${r.pkg}", error = true)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Восстановить ${r.label}?")
            .setMessage(
                if (r.system) {
                    "Приложение снова появится в системе (pm install-existing).\n" +
                        "Только само приложение — данные не восстанавливаются."
                } else {
                    "Приложение установится из сохранённых APK (pm install / install-multiple).\n" +
                        "Только само приложение — данные не восстанавливаются."
                }
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Восстановить") { _, _ ->
                Thread {
                    val res = restoreRecord(r)
                    runOnUiThread {
                        show("> восстановление ${r.pkg}\n" + res)
                        snack(
                            if (res.startsWith("OK")) res.substringAfter(": ") else res,
                            error = !res.startsWith("OK")
                        )
                        renderDeleted()
                    }
                }.start()
            }
            .show()
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
                return "ERR ${r.pkg}: сохранённых APK нет"
            }
            stagingDir = UninstallCommands.stagingDir(r.pkg)
            val copyRes = ServerClient.request(
                this, UninstallCommands.restoreCopyCmd(files, stagingDir)
            )
            if (!copyRes.startsWith("OK")) {
                val copyBody = copyRes.substringAfter(" ", "").trim().ifBlank { copyRes }
                return "ERR ${r.pkg}: не удалось скопировать APK во временную папку ($copyBody)"
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
        AccessStore.log(this, "восстановление ${r.pkg}: ${res.take(120)}")
        if (res.startsWith("OK")) {
            DeletedStore.remove(this, r.pkg)
            if (!r.system && r.apkDir != null) removeApkDir(r.pkg, r.apkDir)
            return "OK ${r.pkg}: восстановлено (${res.substringAfter(" ", "").trim()})"
        }
        val body = res.substringAfter(" ", "").trim().ifBlank { res }
        return "ERR ${r.pkg}: $body"
    }

    private fun confirmRestoreAll() {
        val records = DeletedStore.all(this)
        if (records.isEmpty()) {
            snack("Корзина пуста")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Восстановить все?")
            .setMessage(
                "В корзине ${records.size} ${appsWord(records.size)}. " +
                    "Приложения вернутся, но БЕЗ данных."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Восстановить всё") { _, _ ->
                Thread {
                    var ok = 0
                    var failed = 0
                    for (r in records) {
                        val res = restoreRecord(r)
                        runOnUiThread { show("> восстановление ${r.pkg}\n" + res) }
                        if (res.startsWith("OK")) ok++ else failed++
                    }
                    runOnUiThread {
                        snack("Восстановлено: $ok, ошибок: $failed", error = failed > 0)
                        renderDeleted()
                    }
                }.start()
            }
            .show()
    }

    private fun confirmForget(r: DeletedStore.Record) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить запись?")
            .setMessage(
                if (r.system) {
                    "Приложение остаётся скрытым в системе. Вернуть его позже можно только " +
                        "командой: pm install-existing --user 0 ${r.pkg}"
                } else {
                    "Сохранённый APK будет удалён тоже: вернуть приложение не получится."
                }
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                if (!r.system && r.apkDir != null) removeApkDir(r.pkg, r.apkDir)
                DeletedStore.remove(this, r.pkg)
                AccessStore.log(this, "корзина: запись удалена ${r.pkg}")
                renderDeleted()
            }
            .show()
    }

    private fun confirmClearDeleted() {
        val records = DeletedStore.all(this)
        if (records.isEmpty()) {
            snack("Корзина пуста")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Очистить корзину?")
            .setMessage(
                "Будут удалены ${records.size} ${appsWord(records.size)} и все сохранённые APK. " +
                    "Скрытые системные приложения останутся скрытыми — данные не возвращаются."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Очистить") { _, _ ->
                records.forEach { r ->
                    if (!r.system && r.apkDir != null) removeApkDir(r.pkg, r.apkDir)
                    DeletedStore.remove(this, r.pkg)
                }
                AccessStore.log(this, "корзина: очистена (${records.size} записей)")
                renderDeleted()
            }
            .show()
    }

    private fun exportDeleted() {
        val records = DeletedStore.all(this)
        if (records.isEmpty()) {
            snack("Корзина пуста — экспортировать нечего")
            return
        }
        Thread {
            val df = java.text.SimpleDateFormat(
                "dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()
            )
            val sb = StringBuilder()
            sb.append("Foxhole — удалённые приложения, экспорт ${df.format(java.util.Date())}\n")
            records.forEach { r ->
                sb.append(
                    "${r.pkg} | ${r.label} | " +
                        (if (r.system) "системное" else "пользовательское") +
                        " | удалено ${df.format(java.util.Date(r.removedAt))} | " +
                        "APK: ${r.apkDir ?: "-"} | восстановим: ${r.restorePossible}\n"
                )
            }
            val file = File(getExternalFilesDir(null), "deleted-export.txt")
            file.writeText(sb.toString())
            runOnUiThread {
                show("Экспорт: ${file.absolutePath}")
                copyToClipboard(file.absolutePath)
                snack("Экспорт готов, путь скопирован: ${file.name}")
            }
            AccessStore.log(this, "корзина: экспорт ${records.size} записей в ${file.absolutePath}")
        }.start()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f МБ".format(bytes / 1048576.0)
        bytes >= 1024 -> "%.1f КБ".format(bytes / 1024.0)
        else -> "$bytes Б"
    }

    private fun send(cmd: String) {
        if (cmd.isBlank()) {
            snack("Введите команду", error = true)
            return
        }
        bg(cmd, { ServerClient.request(this, cmd) }) { res ->
            if (res.startsWith("ERR сервер недоступен")) setStatus(false)
        }
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
        if (ok && msg.startsWith("Сервер запущен")) setStatus(true)
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
        bg("команда запуска", {
            if (writeScript() == null) "ERR|Нет доступа к папке приложения" else "OK"
        }) { res ->
            if (res.startsWith("ERR|")) {
                snack(res.substringAfter("|"), error = true)
            } else {
                offerShareOrCopy("Команда запуска", pcCommand())
            }
        }
    }

    private fun sharePcCommand() {
        val cmd = pcCommand()
        if (cmd.isBlank()) {
            snack("Сначала создай файл запуска", error = true)
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, cmd)
        }
        startActivity(Intent.createChooser(send, "Отправить команду запуска Foxhole"))
    }

    private fun copyPcCommand() {
        val cmd = pcCommand()
        copyToClipboard(cmd)
        snack("Скопировано. Вставь в терминал на компьютере")
    }

    private fun offerShareOrCopy(title: String, cmd: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(cmd)
            .setPositiveButton("Переслать") { _, _ -> sharePcCommand() }
            .setNegativeButton("Скопировать") { _, _ ->
                copyToClipboard(cmd)
                snack("Команда скопирована — вставь её в терминал на ПК")
            }
            .show()
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
        val combined = text + "\n\n" + out.text
        out.setTextColor(themeColor(MR.attr.colorOnSurface))
        out.text = if (combined.length > max) combined.take(max) else combined
    }

    private fun maybeConfirmAllow(app: AccessStore.App, onDone: () -> Unit = {}) {
        if (!Settings.confirmAllow(this)) {
            AccessStore.allow(this, app, AclLevel.READ)
            AccessNotifications.cancel(this, app.uid)
            refreshApps()
            onDone()
            snack("Доступ выдан (READ): ${app.pkg}")
            return
        }
        showLevelDialog(
            title = "Разрешить доступ?",
            message = "${app.pkg}\nuid ${app.uid}\n\nВыбери, что приложение сможет делать от имени shell.",
            initial = AclLevel.READ
        ) { level ->
            AccessStore.allow(this, app, level)
            AccessNotifications.cancel(this, app.uid)
            refreshApps()
            onDone()
            snack("Доступ выдан (${level.label}): ${app.pkg}")
        }
    }

    private fun changeLevel(app: AccessStore.App) {
        showLevelDialog(
            title = "Уровень доступа",
            message = "${app.pkg}\nuid ${app.uid}",
            initial = app.level
        ) { level ->
            AccessStore.setLevel(this, app, level)
            refreshApps()
            snack("Уровень ${app.pkg}: ${level.label}")
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
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Готово") { _, _ -> onPick(levels[chosen]) }
            .show()
    }

    private fun levelHint(level: AclLevel): String = when (level) {
        AclLevel.READ -> "только чтение (ping, getsetting)"
        AclLevel.PERMISSIONS -> "плюс grant/revoke/appops/settings"
        AclLevel.FULL -> "любые shell-команды"
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
                contentDescription = "Настройки"
            },
            FrameLayout.LayoutParams(22.dp, 22.dp, Gravity.CENTER)
        )
        return button
    }

    private fun showSettings() {
        val sheet = BottomSheetDialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 24.dp, 24.dp, 32.dp)
        }
        content.addView(label("Настройки", 22f, bold = true))
        content.addView(
            label("Изменения применяются сразу.", 13f)
                .apply { setTextColor(hintColor); setPadding(0, 4.dp, 0, 0) }
        )

        content.addView(space(20))
        val portField = field(
            "Порт сервера (по умолчанию ${Server.PORT})",
            InputType.TYPE_CLASS_NUMBER,
            maxLength = 5
        )
        portField.editText?.setText(Settings.port(this).toString())
        content.addView(portField)
        content.addView(space(8))
        content.addView(button("Сохранить порт", outlined = true) {
            val p = portField.editText?.text?.toString()?.trim()?.toIntOrNull()
            if (p == null || p !in 1024..65535) {
                snack("Порт должен быть от 1024 до 65535", error = true)
            } else {
                Settings.setPort(this, p)
                sheet.dismiss()
                snack("Порт сохранён. Перезапусти сервер, чтобы применить")
            }
        })
        content.addView(
            label("Порт localhost, на котором слушает сервер. Меняй, только если стандартный занят.", 12f)
                .apply { setTextColor(hintColor); setPadding(0, 6.dp, 0, 0) }
        )

        content.addView(space(20))
        content.addView(switchRow("Автопроверка сервера (каждые 5 с)", Settings.autoCheck(this)) {
            Settings.setAutoCheck(this, it)
        })
        content.addView(space(8))
        content.addView(switchRow("Подтверждать выдачу доступа", Settings.confirmAllow(this)) {
            Settings.setConfirmAllow(this, it)
        })

        content.addView(space(20))
        content.addView(divider())
        content.addView(space(16))
        content.addView(label("Автозапуск", 13f, bold = true).apply { letterSpacing = 0.08f })
        content.addView(space(6))
        content.addView(
            label(
                "После перезагрузки Foxhole сам включит беспроводную отладку, подключится " +
                    "спаренным ключом и запустит сервер. Нужны Android 11+, Wi-Fi, спаривание " +
                    "«Запуск без ПК» и работающий сервер для выдачи разрешения. После " +
                    "«Остановить» автозапуск включится только после следующей перезагрузки.\n\n" +
                    "MIUI/HyperOS могут блокировать фоновый старт — там автозапуск не сработает.",
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
                        "Недоступно: нужен Android 11+."
                    writeSecureGranted ->
                        "Разрешение WRITE_SECURE_SETTINGS: выдано ✓"
                    else ->
                        "Разрешение WRITE_SECURE_SETTINGS: не выдано (нужно для включения отладки)"
                },
                12f
            ).apply { setTextColor(hintColor) }
                .also { wssStatusView = it }
        )
        content.addView(space(8))
        lateinit var bootSwitch: MaterialSwitch
        bootSwitch = MaterialSwitch(this).apply {
            text = "Автозапуск после перезагрузки"
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
            switchRow("Выключать отладку после автозапуска", Settings.bootDisableWifi(this)) {
                Settings.setBootDisableWifi(this, it)
            }
        )
        content.addView(space(6))
        content.addView(
            label(
                "Отладка выключается, только если её включал сам автозапуск; " +
                    "твоё ручное включение не трогаем.",
                12f
            ).apply { setTextColor(hintColor) }
        )

        content.addView(space(20))
        content.addView(divider())
        content.addView(space(16))
        content.addView(label("Сервис", 13f, bold = true).apply { letterSpacing = 0.08f })
        content.addView(space(8))
        content.addView(button("Сбросить токен", outlined = true) {
            ServerClient.resetToken(this)
            sheet.dismiss()
            snack("Токен обновлён. Перезапусти сервер")
        })
        content.addView(space(8))
        content.addView(button("Забыть ADB-ключ", outlined = true) {
            FoxholeAdb.forget(this)
            sheet.dismiss()
            snack("ADB-ключ удалён. Нужно спарить устройство заново")
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
                    "Спаривание по Wi-Fi: только Android 11+ (API 30)."
                else "Спаривание по Wi-Fi недоступно: нужен Android 11+.",
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
            snack("Автозапуску нужен Android 11+", error = true)
            onDone(false)
            return
        }
        if (!FoxholeAdb.hasKey(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Нужно спаривание самого Foxhole")
                .setMessage(
                    "Спаривание с ПК не заменяет этот шаг: PC-ключ подходит только " +
                        "компьютеру. После перезагрузки телефон подключается ключом " +
                        "Foxhole, а он появляется после «Запуск без ПК» → «Спарить» " +
                        "(порт и код из окна «Подключение по коду» - достаточно один раз)."
                )
                .setNegativeButton("Позже", null)
                .setPositiveButton("Спарить сейчас") { _, _ ->
                    openSheet?.dismiss()
                    setFoldExpanded(pairingFold, true)
                    val target = pairingFold.header
                    mainScroll.post {
                        mainScroll.smoothScrollTo(0, target.top)
                    }
                }
                .show()
            onDone(false)
            return
        }
        val alreadyGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            Settings.setBootStart(this, true)
            wssStatusView?.text = "Разрешение WRITE_SECURE_SETTINGS: выдано ✓"
            snack("Автозапуск включён")
            onDone(true)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Разрешение для автозапуска")
            .setMessage(
                "Чтобы включать беспроводную отладку после перезагрузки, Foxhole нужно " +
                    "системное разрешение WRITE_SECURE_SETTINGS. Его выдаст сервер " +
                    "(uid shell) самому Foxhole.\n\n" +
                    "Отозвать можно с ПК:\n" +
                    "adb shell pm revoke $packageName ${Manifest.permission.WRITE_SECURE_SETTINGS}\n\n" +
                    "Продолжить?"
            )
            .setNegativeButton("Отмена") { _, _ -> onDone(false) }
            .setPositiveButton("Выдать разрешение") { _, _ ->
                bg("выдача WRITE_SECURE_SETTINGS", {
                    ServerClient.request(
                        this,
                        "pm grant $packageName ${Manifest.permission.WRITE_SECURE_SETTINGS}"
                    )
                }) { res ->
                    if (res.startsWith("OK")) {
                        Settings.setBootStart(this, true)
                        wssStatusView?.text = "Разрешение WRITE_SECURE_SETTINGS: выдано ✓"
                        snack("Автозапуск включён. Разрешение выдано")
                        onDone(true)
                    } else {
                        snack(
                            "Не удалось выдать разрешение: запусти сервер и попробуй ещё раз",
                            error = true
                        )
                        onDone(false)
                    }
                }
            }
            .show()
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

        const val EXTRA_OPEN_PENDING = "foxhole_open_pending"

        private const val UNINSTALL_MAX = 120

        private const val FILTER_USER = 0
        private const val FILTER_SYSTEM = 1
        private const val FILTER_ALL = 2
    }
}

