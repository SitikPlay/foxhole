/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SitikPlay
 */

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
import android.net.wifi.WifiManager
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Экран: карточка статуса, быстрые действия, сворачиваемые секции.
 *
 *  1. Шапка — название и суть.
 *  2. Hero-карточка статуса: работает / остановлен, uid, кнопки «Запустить/Остановить» и «Проверить».
 *  3. «Запуск без ПК» — спаривание через уведомление, пока системные настройки остаются открыты.
 *  4. «Запуск с ПК» — генерация start.sh.
 *  5. «Приложения» — ожидающие, разрешённые и список установленных приложений.
 *  6. «Команды» — произвольные shell-команды + консоль вывода.
 *  7. «Гайды», «Журнал приложений» и «Как это работает» — сворачиваемые секции.
 *
 * Ошибки и успехи показываются Snackbar-ом снизу (мини-уведомление),
 * а не только текстом в консоли.
 */
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
    private lateinit var statusChip: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var pairingFold: Fold
    private lateinit var pairButton: MaterialButton
    private lateinit var pairPortField: TextInputLayout
    private lateinit var pairCodeField: TextInputLayout
    private lateinit var pcCommandField: TextInputLayout
    private var serverOnline: Boolean? = null
    private var pulse: ObjectAnimator? = null

    private val hintColor: Int by lazy { themeColor(MR.attr.colorOnSurfaceVariant) }

    /** Собственные шрифты приложения — не зависят от системного. */
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
        // Android 12+: подстраиваем палитру под обои (Material You), иначе — брендовая тема.
        DynamicColors.applyToActivityIfAvailable(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Прозрачные системные панели: под ними виден фон приложения, а не цвет темы.
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
        // Инсеты на самом ScrollView: при прокрутке контент обрезается, а не уезжает под статус-бар.
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // Клавиатура: поднимаем контент, иначе поля уходят под неё.
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

        // --- Шапка ---
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

        // --- Запуск без ПК ---
        pairingFold = fold(root, "Запуск без ПК", expanded = true)
        pairingFold.content.addView(space(12))
        pairingFold.content.addView(
            label(
                "1. Включи «Беспроводную отладку» — кнопка ниже откроет настройки.\n" +
                    "2. Нажми «Подключение по коду»: система покажет порт и код.\n" +
                    "3. Не закрывая настройки, потяни шторку и введи порт и код в уведомлении Foxhole.\n\n" +
                    "Foxhole спарит и предложит открыть приложение — сервер запустится сам.\n" +
                    "Код действует около 2 минут.\n\n" +
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
        pairingFold.header.setOnClickListener {
            setFoldExpanded(pairingFold, !pairingFold.expanded)
        }

        // До Android 11 нет Wireless debugging и mDNS-спаривания — секция остаётся, но неактивной.
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

        // --- Запуск с ПК ---
        val pcFold = fold(root, "Запуск с ПК", expanded = false)
        pcFold.content.addView(space(12))
        pcFold.content.addView(
            label(
                "1. Включи «Отладка по USB» и подключи телефон кабелем к компьютеру.\n" +
                    "2. Нажми «Создать файл запуска» — телефон запишет и покажет команду.\n" +
                    "3. Вставь команду в терминал на компьютере: она запустит сервер по ADB.\n\n" +
                    "Спаривание по Wi-Fi для этого не нужно. После запуска кабель можно отключить.",
                13f
            ).apply { setTextColor(hintColor) }
        )
        pcFold.content.addView(space(10))
        pcFold.content.addView(button("Создать файл запуска", outlined = true) {
            bg("создание файла запуска", {
                val path = writeScript()
                if (path == null) "ERR|Нет доступа к папке приложения"
                else "OK|adb shell sh \"$path\""
            }) { res ->
                val cmd = res.substringAfter("|")
                val ok = res.startsWith("OK|")
                if (ok) {
                    pcCommandField.editText?.setText(cmd)
                    copyToClipboard(cmd)
                    snack("Файл готов. Команда скопирована — вставь её в терминал на ПК")
                } else {
                    snack(cmd, error = true)
                }
            }
        })
        pcFold.content.addView(space(8))
        pcCommandField = field("Команда для ПК", InputType.TYPE_NULL)
        pcCommandField.editText?.apply {
            keyListener = null
            appTypeface(400, code = true)
            val dir = getExternalFilesDir(null)
            val path = dir?.let { File(it, "start.sh").absolutePath }
                ?: "/storage/emulated/0/Android/data/$packageName/files/start.sh"
            setText("adb shell sh \"$path\"")
        }
        pcFold.content.addView(space(8))
        pcFold.content.addView(button("Скопировать команду", outlined = true) {
            val cmd = pcCommandField.editText?.text?.toString()?.trim().orEmpty()
            if (cmd.isBlank()) {
                snack("Сначала создай файл запуска", error = true)
            } else {
                copyToClipboard(cmd)
                snack("Скопировано. Вставь в терминал на компьютере")
            }
        })
        pcFold.content.addView(space(12))
        pcFold.content.addView(button("Показать команду для ADB", outlined = true) {
            showAdbPairCommand()
        })
        pcFold.content.addView(space(8))
        pcFold.content.addView(
            label(
                "Команда для спаривания с ПК. IP и порт смотри в окне «Подключение по коду»; " +
                    "код возьми оттуда же.",
                12f
            ).apply { setTextColor(hintColor) }
        )

        // --- Гайды ---
        val guides = fold(root, "Гайды", expanded = false)
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
                    "3. В Foxhole нажми «Создать файл запуска» — команда сразу скопируется.\n" +
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

        // --- Приложения ---
        section(
            root, "Приложения",
            "Кто просит доступ и кому он уже разрешён. Разрешения привязаны к uid — подделать нельзя."
        ).apply {
            addView(space(12))
            appsBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(appsBox)
        }

        // --- Установленные приложения ---
        val installedFold = fold(root, "Установленные приложения", expanded = false)
        installedFold.content.addView(space(12))
        installedFold.content.addView(
            label("Разреши приложение заранее — ждать его запроса не обязательно.", 13f)
                .apply { setTextColor(hintColor) }
        )
        installedFold.content.addView(space(10))
        val search = field("Поиск: название или пакет", InputType.TYPE_CLASS_TEXT)
        installedFold.content.addView(search)
        installedFold.content.addView(space(10))
        installedBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        installedFold.content.addView(installedBox)
        installedFold.content.addView(space(10))
        installedFold.content.addView(button("Обновить список", outlined = true) {
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
        installedFold.header.setOnClickListener {
            setFoldExpanded(installedFold, !installedFold.expanded)
            if (installedFold.expanded && !installedLoaded) loadInstalledApps()
        }

        // --- Команды ---
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

        // --- Журнал приложений ---
        val journal = fold(root, "Журнал приложений", expanded = false)
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

        // --- Как это работает ---
        val about = fold(root, "Как это работает", expanded = false)
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

        setContentView(scroll)
        maybeAutoStart(intent)
    }

    /** После успешного спаривания уведомление открывает Foxhole с просьбой запустить сервер. */
    private fun maybeAutoStart(intent: Intent?) {
        if (intent?.getBooleanExtra(PairService.EXTRA_AUTO_START, false) != true) return
        intent.removeExtra(PairService.EXTRA_AUTO_START)
        uiHandler.postDelayed({
            bg("автозапуск", { ServerClient.request(this, "ping") }) { res ->
                if (!res.startsWith("OK pong")) startServer()
            }
        }, 400)
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
        uiHandler.removeCallbacks(autoCheckTick)
        if (Settings.autoCheck(this)) autoCheckTick.run() else startPulse()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeAutoStart(intent)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(autoCheckTick)
        pulse?.cancel()
        pulse = null
        if (::statusDot.isInitialized) statusDot.alpha = 1f
    }

    // ---------- Статус и запуск ----------

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
        // Декоративный блик в углу.
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
            setPadding(22.dp, 22.dp, 22.dp, 22.dp)
        }

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(onContainer, 0x80))
            }
        }
        pill.addView(statusDot, LinearLayout.LayoutParams(9.dp, 9.dp))

        statusChip = TextView(this).apply {
            textSize = 10f
            letterSpacing = 0.12f
            appTypeface(700)
            setTextColor(onContainer)
            setPadding(10.dp, 5.dp, 10.dp, 5.dp)
            background = GradientDrawable().apply {
                cornerRadius = 100f
                setColor(ColorUtils.setAlphaComponent(onContainer, 0x1F))
            }
        }
        pill.addView(
            statusChip,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = 9.dp }
        )
        content.addView(pill)
        content.addView(space(16))

        statusTitle = TextView(this).apply {
            textSize = 26f
            appTypeface(700)
            setTextColor(onContainer)
        }
        statusSubtitle = TextView(this).apply {
            textSize = 13f
            appTypeface(400)
            setTextColor(ColorUtils.setAlphaComponent(onContainer, 0xCC))
            setLineSpacing(4f, 1f)
        }
        content.addView(statusTitle)
        content.addView(space(4))
        content.addView(statusSubtitle)
        content.addView(space(20))

        content.addView(
            button("Проверить", outlined = true) { refreshStatus(true) }.apply {
                strokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onContainer, 0x4D))
                setTextColor(onContainer)
                cornerRadius = 14.dp
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
        statusChip.text = when (online) {
            true -> "РАБОТАЕТ"
            false -> "ОСТАНОВЛЕН"
            null -> "НЕ ПРОВЕРЕН"
        }
        statusTitle.text = when (online) {
            true -> "Сервер работает"
            false -> "Сервер остановлен"
            null -> "Статус не проверен"
        }
        statusSubtitle.text = when (online) {
            true -> "uid 2000 · shell · localhost:${Settings.port(this)}"
            false -> "Запусти спаривание или сервер снова получит доступ"
            null -> "Нажми «Проверить»"
        }
        startPulse()
    }

    /** Мягкое мигание точки, пока сервер работает. */
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

    /** Спаривание по введённым порту и коду — без ПК и без уведомления. */
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
        bg("спаривание", {
            val adb = FoxholeAdb.get(this@MainActivity)
            adb.setTimeout(30, TimeUnit.SECONDS)
            // Порт берём из mDNS, если окно с кодом ещё на экране; иначе вводим вручную.
            val service = discoverService(AdbMdns.SERVICE_TYPE_TLS_PAIRING, 4_000)
            val host = service?.first ?: localIpv4()
                ?: return@bg "ERR|Телефон не в Wi-Fi — жми «Открыть настройки отладки»"
            val paired = try {
                adb.pair(host, service?.second ?: typedPort, code)
            } catch (e: Exception) {
                return@bg "ERR|${friendlyAdbError(e)}"
            }
            if (!paired) {
                // «Already paired» приходит как отказ: проверим реальным connect.
                try {
                    if (adb.connectTls(this@MainActivity, 6_000)) {
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
            // После спаривания TLS-сервис появляется не сразу, поэтому вторая попытка.
            for (attempt in 0 until 2) {
                try {
                    if (adb.connectTls(this@MainActivity, 8_000)) {
                        connected = true
                        break
                    }
                    connectError = "ADB не принял подключение"
                } catch (e: AdbPairingRequiredException) {
                    return@bg "ERR|Нужно спаривание: включи беспроводную отладку и спарь устройство"
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

    // ---------- Спаривание ----------

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                PairService.prepare(this)
                openWirelessSettings()
            } else {
                snack("Без уведомлений не получится ввести код, не закрывая настройки", error = true)
            }
        }

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

    /** Открывает системный экран, откуда берут код и порт спаривания. */
    private fun openWirelessSettings() {
        try {
            val intent = Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.Settings")
                putExtra(":settings:show_fragment", "com.android.settings.development.WirelessDebuggingFragment")
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }
        } catch (e: Exception) {
        }
        val direct = listOf(
            ComponentName("com.android.settings", "com.android.settings.Settings\$WirelessDebuggingActivity"),
            ComponentName("com.android.settings", "com.android.settings.wifi.debugging.WifiDebuggingActivity")
        )
        for (component in direct) {
            try {
                val intent = Intent().setComponent(component)
                if (intent.resolveActivity(packageManager) == null) continue
                startActivity(intent)
                return
            } catch (e: Exception) {
                // На этой прошивке может не быть отдельного экрана — откроем «Для разработчиков».
            }
        }
        try {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: Exception) {
            snack("Открой Настройки → Для разработчиков → Беспроводная отладка", error = true)
        }
    }

    /** Показывает готовую команду `adb pair` для спаривания с ПК. */
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

    private fun discoverService(serviceType: String, timeoutMs: Long): Pair<String, Int>? {
        val found = AtomicReference<Pair<String, Int>?>(null)
        val latch = CountDownLatch(1)
        val mdns = AdbMdns(this, serviceType) { address, port ->
            val host = address?.hostAddress
            if (host != null && port > 0) {
                found.set(host to port)
                latch.countDown()
            }
        }
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("foxhole-mdns")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        mdns.start()
        return try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            found.get()
        } finally {
            mdns.stop()
            runCatching { lock?.release() }
        }
    }

    /** IPv4-адрес Wi-Fi для команды `adb connect` с другого устройства. */
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

    // ---------- Список приложений ----------

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
                "Отклонить" to { AccessStore.deny(this, app) }
            )
        }
        allowed.forEach { app ->
            if (!first) appsBox.addView(divider())
            first = false
            appRow(app, pending = false, "Отозвать" to { AccessStore.revoke(this, app) })
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
                "uid ${app.uid} · ${if (pending) "ожидает решения" else "доступ разрешён"}",
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

    // ---------- Установленные приложения ----------

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


    // ---------- Команды и вывод ----------

    private fun send(cmd: String) {
        if (cmd.isBlank()) {
            snack("Введите команду", error = true)
            return
        }
        bg(cmd, { ServerClient.request(this, cmd) }) { res ->
            if (res.startsWith("ERR сервер недоступен")) setStatus(false)
        }
    }

    private fun writeScript(): String? {
        val dir = getExternalFilesDir(null) ?: return null
        val file = File(dir, "start.sh")
        val cls = Server::class.java.name
        val token = ServerClient.token(this)
        val port = Settings.port(this)
        // APK-путь берётся динамически, чтобы скрипт пережил обновление приложения.
        file.writeText(
            "#!/system/bin/sh\n" +
                "CLASSPATH=\$(pm path $packageName | head -n 1 | sed 's/^package://')\n" +
                "setsid app_process -Djava.class.path=\"\$CLASSPATH\" " +
                "/system/bin --nice-name=foxhole $cls $token $port " +
                "> /data/local/tmp/foxhole.log 2>&1 < /dev/null &\n" +
                "sleep 3\n"
        )
        return file.absolutePath
    }

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

    /** Показывает результат действия: Snackbar + при успехе обновляет статус. */
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

    private fun snack(message: String, error: Boolean = false) {
        val bar = Snackbar.make(
            findViewById(android.R.id.content),
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

    // ---------- Настройки ----------

    private fun maybeConfirmAllow(app: AccessStore.App, onDone: () -> Unit = {}) {
        if (!Settings.confirmAllow(this)) {
            AccessStore.allow(this, app)
            refreshApps()
            onDone()
            snack("Доступ выдан: ${app.pkg}")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Разрешить доступ?")
            .setMessage(
                "${app.pkg}\nuid ${app.uid}\n\n" +
                    "Приложение сможет выполнять любые shell-команды от имени uid 2000 " +
                    "(grant, appops, settings, dumpsys и т.д.)."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Разрешить") { _, _ ->
                AccessStore.allow(this, app)
                refreshApps()
                onDone()
                snack("Доступ выдан: ${app.pkg}")
            }
            .show()
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

        // Порт сервера
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

        // Переключатели
        content.addView(space(20))
        content.addView(switchRow("Автопроверка сервера (каждые 5 с)", Settings.autoCheck(this)) {
            Settings.setAutoCheck(this, it)
        })
        content.addView(space(8))
        content.addView(switchRow("Подтверждать выдачу доступа", Settings.confirmAllow(this)) {
            Settings.setConfirmAllow(this, it)
        })

        // Сервис
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

        // О приложении
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

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    // ---------- Элементы UI ----------

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

    private fun fold(root: LinearLayout, title: String, expanded: Boolean): Fold {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 0, 20.dp, 20.dp)
        }
        val header = TextView(this).apply {
            textSize = 17f
            appTypeface(700)
            setPadding(20.dp, 18.dp, 20.dp, 18.dp)
            isClickable = true
            background = ripple()
        }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(header)
        inner.addView(content)

        val fold = Fold(header, content, title, expanded)
        header.setOnClickListener { setFoldExpanded(fold, !fold.expanded) }
        setFoldExpanded(fold, expanded)

        root.addView(
            cardFor(inner),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = 14.dp }
        )
        return fold
    }

    private fun setFoldExpanded(fold: Fold, expanded: Boolean) {
        fold.expanded = expanded
        fold.content.visibility = if (expanded) View.VISIBLE else View.GONE
        fold.header.text = (if (expanded) "▾  " else "▸  ") + fold.title
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
        var expanded: Boolean
    )

    private data class InstalledApp(val pkg: String, val label: String, val uid: Int)

    companion object {
        private const val AUTO_CHECK_MS = 5_000L
    }
}
