# foxhole-adb

На ПК-компаньон для [Foxhole](../../README.md): одна программа `foxhole-adb.exe`,
которая говорит с телефоном через **официальный adb (platform-tools)**.
Своего протокола ADB в ней нет — она только собирает корректные аргументы
и запускает `adb` без shell, поэтому мета-символы (`;`, `|`, `&`) в аргументах
безопасны: никто их не интерпретирует.

Лицензия: **GPL-3.0-or-later** (см. `LICENSE` в корне репозитория).

---

## Быстрый старт (Windows)

1. Скачай релиз `android-v*`/`adb-v*` со страницы Releases репозитория
   (или собери сам: `go build .` — нужен установленный [Go](https://go.dev)).
2. Положи `foxhole-adb.exe` в любую папку.
3. `foxhole-adb doctor` — покажет, найден ли adb и что видит.
   В сборке из `tools/foxhole-adb/` adb уже встроен рядом с exe и ничего
   дополнительно не требуется.

Если adb нигде нет:

```
foxhole-adb fetch
```

Скачает официальный zip platform-tools с dl.google.com и распакует в
`%LOCALAPPDATA%\Foxhole\platform-tools`. Скачивание выполняется **только**
по этой явной команде — doctor и остальные команды никогда ничего не загружают.
Google не публикует sha-суммы к zip «latest», поэтому честная проверка такая:
после распаковки `fetch` сам запускает `adb version` и показывает результат.

Переносимый вариант рядом с `foxhole-adb.exe`: в комплект кладётся папка
`platform-tools` (только `adb.exe` + `AdbWinApi.dll` + `AdbWinUsbApi.dll` —
файлы официального adb, Apache-2.0, распространяются по NOTICE Google).
В локальной сборке `tools\foxhole-adb\` она уже есть — поэтому
`foxhole-adb devices` работает без `--adb` и без установки чего-либо.

Другие пути поиска adb (по порядку, первый найденный побеждает):

1. флаг `--adb ПУТЬ`;
2. переменная окружения `FOXHOLE_ADB`;
3. папка `platform-tools` рядом с `foxhole-adb.exe` (переносимый zip);
4. `adb` из PATH;
5. `%LOCALAPPDATA%\Foxhole\platform-tools\adb.exe` — куда кладёт `fetch`.

Язык сообщений: русский по умолчанию; `--lang en` или переменная
`FOXHOLE_ADB_LANG=en` переключают на английский.

---

## Команды

`foxhole-adb help` — самый свежий и полный список (оно же выводится автоматически
при опечатке), с русскими пояснениями по каждой команде. Кратко:

| Группа | Команды |
|---|---|
| сервис | `help`, `version`, `doctor`, `fetch` |
| подключение | `devices`, `pair`, `connect`, `disconnect`, `mdns`, `wait-for-device`, `start-server`, `kill-server`, `tcpip`* , `usb`, `root`* , `unroot`* , `reboot`* |
| файлы и экран | `install`, `install-multiple`, `push`, `pull`, `screenshot`, `sideload`, `bugreport`, `backup`* |
| чтение (не меняет телефон) | `shell`, `logcat`, `dumpsys`, `getprop`, `pm list packages`, `setting get/list`, `wm size`/`wm density` (без значения) |
| изменение (под замком) | `setting put/delete`, `setprop`, `pm grant/revoke`, `uninstall`, `wm size/density` со значением, `forward`/`reverse` |

* — команды со звёздочкой «под замком»: требуют флаг `--yes` или подтверждаются
словом `y`/`да` в консоли перед выполнением; случайно не сработают.

Глобальные флаги: `--adb ПУТЬ`, `--lang ru|en`.

Примеры:

```
foxhole-adb doctor
foxhole-adb pair 192.168.1.5:38815 123456
foxhole-adb connect 192.168.1.5:38815
foxhole-adb shell getprop ro.build.version.release
foxhole-adb screenshot screen.png
```

Foxhole-специфика:

* Сервер Foxhole слушает **только localhost (127.0.0.1)** на телефоне, поэтому
  ПК не может дотянуться до него напрямую — доступ идёт через `adb` (uid shell):
  `foxhole-adb shell` и есть этот путь.
* `doctor` не требует телефона: он проверяет также порт 27183 и скажет, что
  сервер не запущен (это нормально — его поднимает сам телефон).

---

## Сборка из исходников

Нужен [Go](https://go.dev) 1.22+:

```
cd tools/foxhole-adb
go test ./...
go build .
```

Сборка в CI: `.github/workflows/foxhole-adb.yml` — на тег `adb-v*` собирает
Windows-primary exe (плюс linux/darwin), гоняет тесты и публикует релиз.

## Тесты

`go test ./...` — только чистая логика: разбор «хост:порт», отбраковка
инъекций, пределы порта, код спаривания, пути поиска adb, защита от
zip-slip, список режимов reboot, работа глобальных флагов и двух языков.
Все вызовы самого adb идут только в ручной прогон и doctor.

---

## foxhole-adb (English)

A PC companion for Foxhole: one binary `foxhole-adb.exe` that talks to the
phone through the **official adb (platform-tools)**. It contains no ADB
protocol of its own; it builds arguments and spawns `adb` without a shell,
so metacharacters (`;`, `|`, `&`) in arguments are safe.

License: **GPL-3.0-or-later** (see `LICENSE` in the repository root).

Language: Russian by default; switch with `--lang en` (or `FOXHOLE_ADB_LANG=en`).

### Quick start

```
foxhole-adb doctor          # check adb, devices, the Foxhole port
foxhole-adb fetch           # explicit download of platform-tools
foxhole-adb pair IP:PORT 6DIGITS
foxhole-adb connect IP:PORT
foxhole-adb shell "ps -A | grep -i sitik"    # quoted by your console, not by us
foxhole-adb screenshot s.png
```

`fetch` unpacks official platform-tools into
`%LOCALAPPDATA%\Foxhole\platform-tools` (Windows) or
`~/.foxhole-adb/platform-tools` (Linux/macOS). Nothing downloads outside
this explicit command. adb lookup order: `--adb` flag → `FOXHOLE_ADB` env →
`platform-tools` next to the binary → PATH → the default directory.

`foxhole-adb help` is the authoritative, full command list (it also prints on a
typo), with a one-line explanation per command. It covers the whole usual adb
surface: devices/pair/connect/disconnect/mdns/wait-for-device, start-server/
kill-server/tcpip/usb/root/unroot/reboot, install/install-multiple/push/pull/
screenshot/sideload/bugreport/backup, read-only shell/logcat/dumpsys/getprop/
pm list/setting get-list/wm, guarded changes (setting put-delete, setprop,
pm grant-revoke, uninstall, wm with a value, forward/reverse). Commands that
change the phone are guarded: they require `--yes` or an interactive y/N
confirmation and validate their arguments before adb runs at all.

Build: Go 1.22+, `go test ./...`, `go build .` — CI builds it on tags
`adb-v*` (Windows primary, plus linux/darwin).

Note: the Foxhole server listens only on the phone's localhost, so the PC
never talks to it directly — `shell` over adb is the supported path.
