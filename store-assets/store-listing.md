# Foxhole — материалы для магазина

## Название

Foxhole

## Короткое описание (RU, до 80 символов)

Shell-доступ без root: любые команды Android от имени shell

## Короткое описание (EN, до 80 символов)

Root-free shell access for Android apps you trust

## Полное описание (RU)

Foxhole даёт приложениям, которым ты доверяешь, доступ к shell-командам Android без root.

Приложение запускает небольшой серверный процесс от имени пользователя `shell` (uid 2000) и общается с ним только по localhost с секретным токеном. Сторонние приложения обращаются через ContentProvider и получают доступ лишь после твоего подтверждения. Разрешения привязаны к uid приложения, поэтому подделать их нельзя.

Что умеет:
- любые shell-команды от uid 2000: dumpsys, ps, pm, settings, конвейеры;
- готовые команды grant/revoke, appops, чтение и запись системных настроек;
- три уровня доступа (READ / PERMISSIONS / FULL) — выбираешь при выдаче и меняешь без отзыва;
- подтверждение устройством (биометрия или PIN) перед выдачей доступа и опасными действиями;
- подтверждение опасных команд (rm, pm uninstall и др.) — обходы через подстановки и long-опции закрыты;
- токен и секреты шифруются ключом Android Keystore;
- маскирование секретов (токены, пароли, cookie) в терминале и истории команд;
- ограничение частоты запросов клиентов (rate-limiter);
- плитка в быстрых настройках: запуск и остановка сервера одним тапом, без открытия приложения;
- автозапуск сервера по Wi-Fi (Android 11+), в том числе на сетях без интернета;
- терминал с живым выводом и историей; высота консоли растёт под объём вывода;
- профили команд: готовые шаблоны со слотами и свои профили; опасные значения отбрасываются с предупреждением;
- отчёт по устройству и статистика активности приложений;
- список ожидающих и установленных приложений с поиском — доступ можно выдать заранее;
- установка, удаление и корзина приложений;
- журнал всех запросов (последние 200);
- foxhole-adb — консольный помощник для ПК: обёртка над adb с русским интерфейсом и встроенным platform-tools;
- 7 языков интерфейса: русский, английский, испанский, португальский, китайский, арабский, хинди;
- свои шрифты и аккуратные светлая/тёмная темы (Material You на Android 12+).

Требования:
- Android 8 (API 26) и новее;
- спаривание по Wi-Fi — только Android 11+;
- на Android 8–10 сервер запускается с ПК через `adb shell`.

Ограничения:
- это не root: доступны только права shell (uid 2000);
- после перезагрузки телефона сервер нужно запускать заново (или включи автозапуск на Android 11+);
- запуск возможен только после ручного спаривания или команды с ПК.

Без рекламы, трекеров и сбора данных. Исходный код открыт под GPL-3.0-or-later.

## Full description (EN)

Foxhole gives apps you trust access to Android shell commands without root.

It starts a small server process as the `shell` user (uid 2000) and talks to it over localhost with a secret token. Third-party apps call a ContentProvider and get access only after you approve them. Permissions are tied to the app's uid, so they cannot be spoofed.

Features:
- any shell command as uid 2000: dumpsys, ps, pm, settings, pipelines;
- shortcuts for grant/revoke, appops and system settings;
- three access levels (READ / PERMISSIONS / FULL) — pick one when granting, change later without revoking;
- device confirmation (biometrics or PIN) before granting access and dangerous actions;
- dangerous command confirmation (rm, pm uninstall, etc.) — bypasses via substitutions and long options are blocked;
- token and secrets encrypted with an Android Keystore key;
- secret masking (tokens, passwords, cookies) in the terminal and command history;
- client request rate limiting;
- quick settings tile: start and stop the server with one tap, no need to open the app;
- automatic server start on Wi-Fi (Android 11+), including networks without internet;
- terminal with live output and history; console height grows with output;
- command profiles: ready templates with slots and your own profiles; dangerous slot values are rejected with a warning;
- device report and app activity statistics;
- pending and installed app lists with search — approve apps in advance;
- app install, uninstall and trash;
- request log (last 200);
- foxhole-adb — a PC console helper: a wrapper around the official adb with a Russian interface and bundled platform-tools;
- 7 UI languages: Russian, English, Spanish, Portuguese, Chinese, Arabic, Hindi;
- bundled fonts and polished light/dark themes (Material You on Android 12+).

Requirements:
- Android 8 (API 26) or newer;
- Wi-Fi pairing only on Android 11+;
- on Android 8–10 the server is started from a PC via `adb shell`.

Limitations:
- not root: only shell privileges (uid 2000);
- the server must be started again after a reboot (or turn on autostart on Android 11+);
- startup requires manual pairing or a PC command.

No ads, trackers or data collection. Source code is available under GPL-3.0-or-later.

## Категория и теги

- Категория: Инструменты / Tools.
- Теги: shell, adb, root, developer tools, automation, material you.
- GitHub About: «Shell-доступ для Android без root: любые команды от uid 2000 после твоего разрешения.»
- Topics GitHub: `android`, `adb`, `shell`, `root`, `kotlin`, `material-you`.

## Ссылки для листинга

- Исходный код: https://github.com/SitikPlay/foxhole
- Лицензия: GPL-3.0-or-later (полный текст в `LICENSE`).
- Политика безопасности: `SECURITY.md`.
- Скриншоты: положить в `store-assets/screenshots/` (светлая и тёмная тема, минимум 2 шт.).
