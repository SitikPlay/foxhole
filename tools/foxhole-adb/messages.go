package main

type ruEn struct {
	ru, en string
}

var messages = map[string]ruEn{}

func init() {
	messages["usage"] = ruEn{ru: `foxhole-adb ` + version + ` — обёртка над официальным adb (platform-tools), GPL-3.0

Использование: foxhole-adb [--adb ПУТЬ] [--lang ru|en] КОМАНДА [аргументы]

Сервис foxhole-adb:
  help                     эта справка; version — номер версии
  doctor                   проверить adb, устройства и порт сервера Foxhole; ничего не меняет
  fetch                    скачать platform-tools (единственная сетевая команда)

Подключение и демон adb:
  devices                  список устройств (как «adb devices -l»)
  pair ХОСТ:ПОРТ КОД       спарить Wi-Fi (6 цифр из окна «Беспроводная отладка»)
  connect ХОСТ[:ПОРТ]      подключиться спаренным ключом (порт по умолчанию 5555)
  disconnect [ХОСТ:ПОРТ]   отключить указанное или все Wi-Fi-подключения
  mdns [check|services]    обнаружение устройств в сети по mDNS
  wait-for-device          подождать, пока устройство появится (заблокирует ввод)
  start-server             запустить демон adb на компьютере
  kill-server              остановить демон adb на компьютере
  tcpip [ПОРТ]             перевести adb телефона в режим Wi-Fi; под замком
  usb                      вернуть adb телефона в режим USB
  root / unroot            права root у adbd на телефоне; под замком
  reboot [режим]           перезагрузка: пусто|recovery|bootloader|fastboot|sideload; под замком

Файлы и экран:
  install APK              установить APK (adb install -r); файл проверяется заранее
  install-multiple APK…    установить набор сплит-APK (несколько файлов)
  push ЛОКАЛ УДАЛЁН        копировать файл на телефон (ровно два аргумента)
  pull УДАЛЁН ЛОКАЛ        копировать файл с телефона (ровно два аргумента)
  screenshot ФАЙЛ.png      снимок экрана телефона (exec-out screencap, бинарно)
  sideload ZIP             прошивка zip в режиме recovery; файл проверяется заранее
  bugreport [ФАЙЛ.zip]     полный отчёт для отладки (может занять несколько минут)
  backup [опции]           резервная копия в формате adb; под замком

Читать с телефона (безопасно, ничего не меняет):
  shell …                  интерактивный шелл телефона; Ctrl+C останавливает
  logcat [опции]           живой журнал Android; Ctrl+C останавливает
  dumpsys [службы…]        дамп состояния системных служб (dumpsys battery и пр.)
  getprop [имя]            свойства телефона: все или одно (getprop ro.build.version.release)
  pm list packages [ф]     список установленных приложений (фильтр — необязательно)
  setting get NS КЛЮЧ      прочитать настройку (NS: global|secure|system)
  setting list NS          список настроек выбранного пространства
  wm size / wm density     текущее разрешение экрана / плотность точек

Менять телефон (под замком: нужно --yes или подтверждение в консоли):
  setting put NS КЛЮЧ ЗН   изменить настройку (NS: global|secure|system)
  setting delete NS КЛЮЧ   удалить настройку
  setprop ИМЯ ЗНАЧЕНИЕ     изменить системное свойство
  pm grant ПАКЕТ ПРАВО     выдать разрешение приложению
  pm revoke ПАКЕТ ПРАВО    отозвать разрешение
  uninstall [-k] ПАКЕТ     удалить приложение (-k — оставить данные)
  wm size ШxВ              задать разрешение экрана (например 1080x2400)
  wm density DPI           задать плотность точек (например 420)
  forward ЛОКАЛ УДАЛЁН     туннель порта с ПК на телефон; reverse — с телефона на ПК
                           формы: tcp:ПОРТ, localabstract:И, localfilesystem:П, dev:Д, jdwp:PID

Подсказки:
  «Под замком» = foxhole-adb требует --yes или спросит y/N в консоли; случайно не сработает.
  Значения дальше передаются через adb безопасно: апострофы, пробелы и «;» цитируются.
  Скачивание platform-tools — только по явной команде fetch.
`, en: `foxhole-adb ` + version + ` — a wrapper around the official adb (platform-tools), GPL-3.0

Usage: foxhole-adb [--adb PATH] [--lang ru|en] COMMAND [args]

foxhole-adb service:
  help                     this help; version — print the version
  doctor                   check adb, devices and the Foxhole server port; changes nothing
  fetch                    download platform-tools (the only networked command)

Connection and the adb daemon:
  devices                  list devices (like "adb devices -l")
  pair HOST:PORT CODE      pair over Wi-Fi (6 digits from the wireless debugging dialog)
  connect HOST[:PORT]      connect with the paired key (port defaults to 5555)
  disconnect [HOST:PORT]   disconnect one or all Wi-Fi connections
  mdns [check|services]    mDNS device discovery
  wait-for-device          block until a device appears
  start-server             start the adb daemon on the PC
  kill-server              stop the adb daemon on the PC
  tcpip [PORT]             switch the phone's adb to Wi-Fi mode; guarded
  usb                      switch the phone's adb back to USB
  root / unroot            root privileges for adbd on the phone; guarded
  reboot [mode]            reboot: none|recovery|bootloader|fastboot|sideload; guarded

Files and screen:
  install APK              install an APK (adb install -r); the file is checked first
  install-multiple APK…    install a set of split APKs (several files)
  push LOCAL REMOTE        copy a file to the phone (exactly two arguments)
  pull REMOTE LOCAL        copy a file from the phone (exactly two arguments)
  screenshot FILE.png      phone screenshot (exec-out screencap, binary safe)
  sideload ZIP             flash a zip in recovery mode; the file is checked first
  bugreport [FILE.zip]     full debug report (can take minutes)
  backup [options]         adb-format backup; guarded

Read-only from the phone (safe, changes nothing):
  shell …                  interactive phone shell; Ctrl+C stops it
  logcat [options]         live Android log; Ctrl+C stops it
  dumpsys [services…]      dump of system services (dumpsys battery etc.)
  getprop [name]           phone properties: all or one
  pm list packages [f]     list installed apps (optional filter)
  setting get NS KEY       read a setting (NS: global|secure|system)
  setting list NS          list settings of the chosen namespace
  wm size / wm density     current screen size / density

Change the phone (guarded: --yes or a console confirmation is required):
  setting put NS KEY VAL   change a setting
  setting delete NS KEY    delete a setting
  setprop NAME VALUE       change a system property
  pm grant PKG PERM        grant a permission to an app
  pm revoke PKG PERM       revoke a permission
  uninstall [-k] PKG       uninstall an app (-k keeps the data)
  wm size WxH              set the screen size (e.g. 1080x2400)
  wm density DPI           set the density (e.g. 420)
  forward LOCAL REMOTE     port tunnel from the PC to the phone; reverse — back
                           forms: tcp:PORT, localabstract:N, localfilesystem:P, dev:D, jdwp:PID

Hints:
  "Guarded" = foxhole-adb requires --yes or asks y/N in the console; it cannot fire by accident.
  Values are quoted safely on the way to adb: apostrophes, spaces and ";".
  platform-tools are downloaded only by the explicit fetch command.
`}
	messages["errGlobal"] = ruEn{ru: "Ошибка разбора флагов", en: "Flag parsing error"}
	messages["needValue"] = ruEn{ru: "требует значение", en: "requires a value"}
	messages["unknown"] = ruEn{ru: "Неизвестная команда:", en: "Unknown command:"}

	messages["noAdb"] = ruEn{
		ru: "adb не найден. Найдите его так: флаг --adb, переменная FOXHOLE_ADB, PATH, папка platform-tools рядом с foxhole-adb или каталог по умолчанию (@CMD@).",
		en: "adb not found. It is looked up via: --adb flag, FOXHOLE_ADB env, PATH, a platform-tools folder next to foxhole-adb, or the default directory (@CMD@).",
	}
	messages["hintFetch"] = ruEn{
		ru: "Подсказка: foxhole-adb fetch скачает и распакует platform-tools (только по явной команде).",
		en: "Hint: foxhole-adb fetch downloads and unpacks platform-tools (only on an explicit command).",
	}

	messages["fetchDone"] = ruEn{
		ru: "platform-tools распакованы: %s\nПроверка: %s",
		en: "platform-tools unpacked: %s\nCheck: %s",
	}
	messages["fetchFail"] = ruEn{
		ru: "Не удалось скачать platform-tools: %v\nПроверьте сеть или скачайте zip вручную с developer.android.com и запустите adb оттуда (--adb ПУТЬ).",
		en: "Could not download platform-tools: %v\nCheck the network, or download the zip manually from developer.android.com and use its adb (--adb PATH).",
	}

	messages["doctorAdb"] = ruEn{ru: "adb:", en: "adb:"}
	messages["doctorVersionFail"] = ruEn{
		ru: "adb найден, но ответить отказался: %v",
		en: "adb found but failed to answer: %v",
	}
	messages["doctorDevices"] = ruEn{ru: "Устройства:", en: "Devices:"}
	messages["doctorNoDevice"] = ruEn{
		ru: "устройств не видно. На телефоне: «Отладка по USB» (кабель) или «Беспроводная отладка» (Wi-Fi), затем connect ХОСТ:ПОРТ.",
		en: "no devices visible. On the phone: USB debugging (cable) or wireless debugging (Wi-Fi), then connect HOST:PORT.",
	}
	messages["doctorServer"] = ruEn{ru: "Порт сервера Foxhole %d:", en: "Foxhole server port %d:"}
	messages["doctorServerOpen"] = ruEn{
		ru: "порт слушается — сервер Foxhole работает",
		en: "port is listening — the Foxhole server is running",
	}
	messages["doctorServerClosed"] = ruEn{
		ru: "порт закрыт — сервер Foxhole не запущен (это нормально: его поднимает телефон)",
		en: "port is closed — the Foxhole server is not running (normal: the phone starts it)",
	}
	messages["doctorOk"] = ruEn{ru: "doctor: всё проверено", en: "doctor: checks finished"}

	messages["pairBadForm"] = ruEn{
		ru: "Формат: foxhole-adb pair ХОСТ:ПОРТ КОД (код — 6 цифр из окна «Беспроводная отладка»).",
		en: "Format: foxhole-adb pair HOST:PORT CODE (the code is 6 digits from the wireless debugging dialog).",
	}
	messages["connectBadForm"] = ruEn{
		ru: "Формат: foxhole-adb connect ХОСТ[:ПОРТ] (порт по умолчанию 5555).",
		en: "Format: foxhole-adb connect HOST[:PORT] (port defaults to 5555).",
	}
	messages["badPort"] = ruEn{
		ru: "порт должен быть числом 1024–65535, получено: %s",
		en: "port must be a number 1024–65535, got: %s",
	}
	messages["badCode"] = ruEn{
		ru: "код спаривания — 6 цифр, получено: %s",
		en: "the pairing code is 6 digits, got: %s",
	}

	messages["installNoFile"] = ruEn{
		ru: "APK не найден: %s",
		en: "APK not found: %s",
	}
	messages["twoBadForm"] = ruEn{
		ru: "Формат: foxhole-adb %s Источник Приёмник (два аргумента, как в adb).",
		en: "Format: foxhole-adb %s SOURCE DEST (two arguments, same as adb).",
	}
	messages["screenshotNoArg"] = ruEn{
		ru: "Формат: foxhole-adb screenshot ФАЙЛ.png",
		en: "Format: foxhole-adb screenshot FILE.png",
	}
	messages["screenshotOk"] = ruEn{
		ru: "Снимок сохранён: %s (%d байт)",
		en: "Screenshot saved: %s (%d bytes)",
	}
	messages["screenshotFail"] = ruEn{
		ru: "Не удалось снять экран: %v",
		en: "Could not take a screenshot: %v",
	}

	messages["rebootMode"] = ruEn{
		ru: "Недопустимый режим перезагрузки: %s (пусто, recovery, bootloader, fastboot, sideload).",
		en: "Invalid reboot mode: %s (none, recovery, bootloader, fastboot, sideload).",
	}
	messages["rebootAsk"] = ruEn{
		ru: "Перезагрузить устройство %s? [y/N] ",
		en: "Reboot the device %s? [y/N] ",
	}
	messages["rebootCanceled"] = ruEn{ru: "Отменено.", en: "Cancelled."}
	messages["rebootNoArgs"] = ruEn{
		ru: "Формат: foxhole-adb reboot [режим] --yes. Без --yes нужно подтвердить словом y в stdin.",
		en: "Format: foxhole-adb reboot [mode] --yes. Without --yes confirmation via stdin is required.",
	}

	messages["runFail"] = ruEn{
		ru: "adb завершился с ошибкой (%s): %s",
		en: "adb failed (%s): %s",
	}

	messages["confirmAsk"] = ruEn{
		ru: "Выполнить «%s»? Это изменит телефон. [y/N] ",
		en: "Run \"%s\"? This will change the phone. [y/N] ",
	}

	messages["devicesHeader"] = ruEn{ru: "Подключённые устройства:", en: "Attached devices:"}
	messages["devicesLegend"] = ruEn{
		ru: "Состояния: device — подключено и разрешено; offline — нет связи; unauthorized — доступ не разрешён (нужно подтвердить на экране телефона).",
		en: "States: device — attached and allowed; offline — link is down; unauthorized — access not allowed (confirm on the phone screen).",
	}
	messages["pairOk"] = ruEn{ru: "Спаривание выполнено с %s.", en: "Paired with %s."}
	messages["connectOk"] = ruEn{ru: "Подключено: %s.", en: "Connected: %s."}
	messages["connectAlready"] = ruEn{ru: "Уже подключено: %s.", en: "Already connected: %s."}
	messages["connectFail"] = ruEn{ru: "adb не смог подключиться: %s", en: "adb could not connect: %s"}
	messages["installOk"] = ruEn{ru: "Установка завершена успешно.", en: "Install finished successfully."}
	messages["uninstallOk"] = ruEn{ru: "Приложение удалено.", en: "App uninstalled."}
	messages["daemonStart"] = ruEn{
		ru: "Демон adb не был запущен — запускаю (порт 5037)…",
		en: "The adb daemon was not running — starting it (port 5037)…",
	}
	messages["daemonStarted"] = ruEn{ru: "Демон adb запущен.", en: "The adb daemon started."}

	messages["mdnsBad"] = ruEn{
		ru: "Формат: foxhole-adb mdns [check|services].",
		en: "Format: foxhole-adb mdns [check|services].",
	}
	messages["bugreportBad"] = ruEn{
		ru: "Формат: foxhole-adb bugreport [ФАЙЛ.zip] — не более одного аргумента.",
		en: "Format: foxhole-adb bugreport [FILE.zip] — at most one argument.",
	}
	messages["peerBad"] = ruEn{
		ru: "Формат: foxhole-adb %s ЛОКАЛ УДАЛЁН — формы: tcp:ПОРТ, localabstract:ИМЯ, localfilesystem:ПУТЬ, dev:ДЕСКРИПТОР, jdwp:PID.",
		en: "Format: foxhole-adb %s LOCAL REMOTE — forms: tcp:PORT, localabstract:NAME, localfilesystem:PATH, dev:DESCRIPTOR, jdwp:PID.",
	}
	messages["tcpipBad"] = ruEn{
		ru: "Формат: foxhole-adb tcpip [ПОРТ] — порт 1024–65535 или пусто (5555).",
		en: "Format: foxhole-adb tcpip [PORT] — port 1024–65535 or empty (5555).",
	}
	messages["getpropBad"] = ruEn{
		ru: "Формат: foxhole-adb getprop [имя свойства] — не более одного аргумента.",
		en: "Format: foxhole-adb getprop [property name] — at most one argument.",
	}
	messages["pmBad"] = ruEn{
		ru: "Формат: foxhole-adb pm list packages [фильтр] | pm grant ПАКЕТ ПРАВО | pm revoke ПАКЕТ ПРАВО.",
		en: "Format: foxhole-adb pm list packages [filter] | pm grant PKG PERM | pm revoke PKG PERM.",
	}
	messages["pmListBad"] = ruEn{
		ru: "Формат: foxhole-adb pm list packages [фильтр] — не более одного аргумента.",
		en: "Format: foxhole-adb pm list packages [filter] — at most one argument.",
	}
	messages["settingBad"] = ruEn{
		ru: "Формат: foxhole-adb setting get|list|put|delete … (подробности: foxhole-adb help).",
		en: "Format: foxhole-adb setting get|list|put|delete … (details: foxhole-adb help).",
	}
	messages["nsBad"] = ruEn{
		ru: "Пространство настроек должно быть global, secure или system, получено: %s",
		en: "The settings namespace must be global, secure or system, got: %s",
	}
	messages["setpropBad"] = ruEn{
		ru: "Формат: foxhole-adb setprop ИМЯ ЗНАЧЕНИЕ (два аргумента).",
		en: "Format: foxhole-adb setprop NAME VALUE (two arguments).",
	}
	messages["pkgBad"] = ruEn{
		ru: "Формат: foxhole-adb %s ПАКЕТ … — имя пакета: буквы, цифры, точка, «_», «-».",
		en: "Format: foxhole-adb %s PKG … — package name: letters, digits, dot, \"_\", \"-\".",
	}
	messages["wmBad"] = ruEn{
		ru: "Формат: foxhole-adb wm size [ШxВ] | foxhole-adb wm density [DPI].",
		en: "Format: foxhole-adb wm size [WxH] | foxhole-adb wm density [DPI].",
	}
	messages["wmSizeBad"] = ruEn{
		ru: "Размер должен быть вида ШИРИНАxВЫСОТА, например 1080x2400.",
		en: "The size must be WIDTHxHEIGHT, e.g. 1080x2400.",
	}
	messages["wmDensityBad"] = ruEn{
		ru: "Плотность должна быть числом от 100 до 2000, например 420.",
		en: "The density must be a number 100–2000, e.g. 420.",
	}
	messages["rootBad"] = ruEn{
		ru: "Формат: foxhole-adb %s — без аргументов.",
		en: "Format: foxhole-adb %s — no arguments.",
	}
	messages["backupNote"] = ruEn{
		ru: "Примечание: это бэкап в формате adb; восстановление — adb restore. В приложении Foxhole резервное копирование отключено по умолчанию.",
		en: "Note: this is an adb-format backup; restore it with adb restore. Backup is disabled by default in the Foxhole app.",
	}
	messages["sideloadBad"] = ruEn{
		ru: "Формат: foxhole-adb sideload ZIP-ФАЙЛ.",
		en: "Format: foxhole-adb sideload ZIP-FILE.",
	}
	messages["installMultiBad"] = ruEn{
		ru: "Формат: foxhole-adb install-multiple APK… (один или несколько файлов).",
		en: "Format: foxhole-adb install-multiple APK… (one or more files).",
	}
}

func msg(k string) string {
	m, ok := messages[k]
	if !ok {
		return k
	}
	if lang == "en" {
		return m.en
	}
	return m.ru
}
