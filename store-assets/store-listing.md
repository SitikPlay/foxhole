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
- список ожидающих и установленных приложений с поиском — доступ можно выдать заранее;
- журнал всех запросов (последние 200);
- проверка статуса сервера каждые 5 секунд, пока приложение открыто;
- свои шрифты и аккуратные светлая/тёмная темы (Material You на Android 12+).

Требования:
- Android 8 (API 26) и новее;
- спаривание по Wi-Fi — только Android 11+;
- на Android 8–10 сервер запускается с ПК через `adb shell`.

Ограничения:
- это не root: доступны только права shell (uid 2000);
- после перезагрузки телефона сервер нужно запускать заново;
- запуск возможен только после ручного спаривания или команды с ПК.

Без рекламы, трекеров и сбора данных. Исходный код открыт под GPL-3.0-or-later.

## Full description (EN)

Foxhole gives apps you trust access to Android shell commands without root.

It starts a small server process as the `shell` user (uid 2000) and talks to it over localhost with a secret token. Third-party apps call a ContentProvider and get access only after you approve them. Permissions are tied to the app's uid, so they cannot be spoofed.

Features:
- any shell command as uid 2000: dumpsys, ps, pm, settings, pipelines;
- shortcuts for grant/revoke, appops and system settings;
- pending and installed app lists with search — approve apps in advance;
- request log (last 200);
- server status checked every 5 seconds while the app is open;
- bundled fonts and polished light/dark themes (Material You on Android 12+).

Requirements:
- Android 8 (API 26) or newer;
- Wi-Fi pairing only on Android 11+;
- on Android 8–10 the server is started from a PC via `adb shell`.

Limitations:
- not root: only shell privileges (uid 2000);
- the server must be started again after a reboot;
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
