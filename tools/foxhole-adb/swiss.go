package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"
)

// cmdFoxholeStart запускает сервер Foxhole на телефоне в 1 клик
func cmdFoxholeStart(ab string, _ []string) int {
	fmt.Println(msg("fhStartSearching"))
	out, code, err := runAdb(ab, []string{"shell", "pm", "path", "com.sitikplay.foxhole"})
	if err != nil || code != 0 || !strings.Contains(out, "package:") {
		fmt.Fprintln(os.Stderr, msg("fhStartNotInstalled"))
		return 1
	}

	apkPath := ""
	for _, line := range strings.Split(out, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "package:") {
			apkPath = strings.TrimPrefix(line, "package:")
			break
		}
	}
	if apkPath == "" {
		fmt.Fprintln(os.Stderr, msg("fhStartNotInstalled"))
		return 1
	}

	fmt.Printf(msg("fhStartApkFound")+"\n", apkPath)

	// Проверяем наличие start.sh
	scriptCheck, _, _ := runAdb(ab, []string{"shell", "test -f /sdcard/Android/data/com.sitikplay.foxhole/files/start.sh && echo ok"})
	hasScript := strings.TrimSpace(scriptCheck) == "ok"

	if hasScript {
		fmt.Println(msg("fhStartLaunchingScript"))
		runAdb(ab, []string{"shell", "nohup sh /sdcard/Android/data/com.sitikplay.foxhole/files/start.sh >/dev/null 2>&1 &"})
	} else {
		fmt.Println(msg("fhStartLaunchingAppProcess"))
		launchCmd := fmt.Sprintf("nohup /system/bin/app_process -Djava.class.path=%s /system/bin com.sitikplay.foxhole.Server 27183 >/dev/null 2>&1 &", apkPath)
		runAdb(ab, []string{"shell", launchCmd})
	}

	// Ожидаем запуск и проверяем статус
	fmt.Println(msg("fhStartChecking"))
	time.Sleep(1500 * time.Millisecond)

	return cmdFoxholeStatus(ab, nil)
}

// cmdFoxholeStatus проверяет, работает ли сервер Foxhole на телефоне
func cmdFoxholeStatus(ab string, _ []string) int {
	checkNet, _, _ := runAdb(ab, []string{"shell", "cat /proc/net/tcp 2>/dev/null"})
	// 27183 в hex = 6A2F
	isListening := strings.Contains(strings.ToUpper(checkNet), ":6A2F")

	pidOut, _, _ := runAdb(ab, []string{"shell", "pidof app_process 2>/dev/null"})
	pid := strings.TrimSpace(pidOut)

	if isListening {
		fmt.Println(msg("fhStatusRunning"))
		fmt.Printf("  Порт: 27183 (127.0.0.1)\n")
		if pid != "" {
			fmt.Printf("  PID: %s\n", pid)
		}
		return 0
	}

	fmt.Println(msg("fhStatusStopped"))
	return 1
}

// cmdFoxholeStop останавливает сервер Foxhole
func cmdFoxholeStop(ab string, _ []string) int {
	// Пытаемся послать сигнал завершения процессу app_process сервера
	runAdb(ab, []string{"shell", "pkill -f com.sitikplay.foxhole.Server 2>/dev/null || true"})
	time.Sleep(500 * time.Millisecond)
	fmt.Println(msg("fhStopDone"))
	return 0
}

// cmdClip читает или записывает буфер обмена телефона
func cmdClip(ab string, args []string) int {
	if len(args) == 0 || args[0] == "get" {
		// Чтение буфера обмена (Android 13+ cmd clipboard get)
		out, code, err := runAdb(ab, []string{"shell", "cmd", "clipboard", "get", "2>/dev/null"})
		if err == nil && code == 0 && strings.TrimSpace(out) != "" {
			fmt.Println(strings.TrimSpace(out))
			return 0
		}
		// Fallback через service call
		fmt.Println(msg("clipGetHelp"))
		return 0
	}

	if args[0] == "set" && len(args) > 1 {
		args = args[1:]
	}
	text := strings.Join(args, " ")
	// Запись в буфер обмена через cmd clipboard set
	_, code, err := runAdb(ab, []string{"shell", "cmd", "clipboard", "set", text})
	if err == nil && code == 0 {
		fmt.Printf(msg("clipSetOk")+"\n", text)
		return 0
	}
	// Fallback через input text
	return cmdType(ab, args)
}

// cmdType печатает текст на телефоне как с клавиатуры
func cmdType(ab string, args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "Использование: fx type ТЕКСТ")
		return 2
	}
	raw := strings.Join(args, " ")
	// adb shell input text требует экранирования пробелов (%s) и спецсимволов
	escaped := strings.ReplaceAll(raw, " ", "%s")
	escaped = strings.ReplaceAll(escaped, "&", "\\&")
	escaped = strings.ReplaceAll(escaped, "<", "\\<")
	escaped = strings.ReplaceAll(escaped, ">", "\\>")
	escaped = strings.ReplaceAll(escaped, "\"", "\\\"")

	_, code, err := runAdb(ab, []string{"shell", "input", "text", escaped})
	if err != nil || code != 0 {
		reportRunFail("input text", code, err)
		return 1
	}
	fmt.Println(msg("typeOk"))
	return 0
}

// cmdPhotos выкачивает N последних фотографий или скриншотов
func cmdPhotos(ab string, args []string) int {
	count := 5
	if len(args) >= 1 {
		if c, err := strconv.Atoi(args[0]); err == nil && c > 0 && c <= 100 {
			count = c
		}
	}

	dirs := []string{"/sdcard/DCIM/Camera", "/sdcard/Pictures/Screenshots"}
	foundFiles := []string{}

	for _, d := range dirs {
		lsCmd := fmt.Sprintf("ls -tp %s 2>/dev/null | grep -v '/' | head -n %d", d, count)
		out, code, err := runAdb(ab, []string{"shell", lsCmd})
		if err == nil && code == 0 {
			for _, f := range strings.Split(out, "\n") {
				f = strings.TrimSpace(f)
				if f != "" {
					foundFiles = append(foundFiles, fmt.Sprintf("%s/%s", d, f))
				}
			}
		}
	}

	if len(foundFiles) == 0 {
		fmt.Println(msg("photosNone"))
		return 0
	}

	if len(foundFiles) > count {
		foundFiles = foundFiles[:count]
	}

	fmt.Printf(msg("photosDownloading")+"\n", len(foundFiles))
	for _, remote := range foundFiles {
		base := filepath.Base(remote)
		out, code, err := runAdb(ab, []string{"pull", remote, base})
		if err == nil && code == 0 {
			fmt.Printf("  + %s -> ./%s\n", remote, base)
		} else {
			fmt.Printf("  - %s: %s\n", remote, out)
		}
	}
	fmt.Println(msg("photosDone"))
	return 0
}

// cmdRecord записывает видео с экрана
func cmdRecord(ab string, args []string) int {
	sec := 10
	dest := "screen.mp4"
	if len(args) >= 1 {
		if s, err := strconv.Atoi(args[0]); err == nil && s > 0 && s <= 180 {
			sec = s
		} else if strings.HasSuffix(args[0], ".mp4") {
			dest = args[0]
		}
	}
	if len(args) >= 2 && strings.HasSuffix(args[1], ".mp4") {
		dest = args[1]
	}

	fmt.Printf(msg("recordStarting")+"\n", sec, dest)
	tmpRemote := "/sdcard/fx_record_tmp.mp4"
	runAdb(ab, []string{"shell", fmt.Sprintf("screenrecord --time-limit %d %s", sec, tmpRemote)})

	fmt.Println(msg("recordPulling"))
	_, code, err := runAdb(ab, []string{"pull", tmpRemote, dest})
	runAdb(ab, []string{"shell", "rm -f " + tmpRemote})

	if err != nil || code != 0 {
		reportRunFail("record pull", code, err)
		return 1
	}
	fmt.Printf(msg("recordDone")+"\n", dest)
	return 0
}

// cmdApkPull выгружает APK установленного приложения
func cmdApk(ab string, args []string) int {
	if len(args) < 2 || args[0] != "pull" {
		fmt.Fprintln(os.Stderr, "Использование: fx apk pull <имя_пакета> [выходной_файл.apk]")
		return 2
	}
	pkg := args[1]
	outApk := pkg + ".apk"
	if len(args) >= 3 {
		outApk = args[2]
	}

	out, code, err := runAdb(ab, []string{"shell", "pm", "path", pkg})
	if err != nil || code != 0 || !strings.Contains(out, "package:") {
		fmt.Fprintf(os.Stderr, msg("apkNotFound")+"\n", pkg)
		return 1
	}

	var remoteApk string
	for _, l := range strings.Split(out, "\n") {
		l = strings.TrimSpace(l)
		if strings.HasPrefix(l, "package:") {
			remoteApk = strings.TrimPrefix(l, "package:")
			break
		}
	}

	fmt.Printf(msg("apkPulling")+"\n", pkg, remoteApk)
	_, codePull, errPull := runAdb(ab, []string{"pull", remoteApk, outApk})
	if errPull != nil || codePull != 0 {
		reportRunFail("apk pull", codePull, errPull)
		return 1
	}
	fmt.Printf(msg("apkPulled")+"\n", outApk)
	return 0
}

// cmdInstallAll устанавливает все APK из каталога
func cmdInstallAll(ab string, args []string) int {
	dir := "."
	if len(args) >= 1 {
		dir = args[0]
	}

	entries, err := os.ReadDir(dir)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Ошибка чтения папки %s: %v\n", dir, err)
		return 1
	}

	var apks []string
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(strings.ToLower(e.Name()), ".apk") {
			apks = append(apks, filepath.Join(dir, e.Name()))
		}
	}

	if len(apks) == 0 {
		fmt.Printf("В папке %s не найдено файлов .apk\n", dir)
		return 0
	}

	fmt.Printf(msg("installAllStart")+"\n", len(apks))
	success := 0
	for _, apk := range apks {
		fmt.Printf("  Установка %s... ", filepath.Base(apk))
		out, code, err := runAdb(ab, []string{"install", "-r", apk})
		if err == nil && code == 0 && strings.Contains(out, "Success") {
			fmt.Println("OK")
			success++
		} else {
			fmt.Printf("ОШИБКА: %s\n", strings.TrimSpace(out))
		}
	}
	fmt.Printf(msg("installAllDone")+"\n", success, len(apks))
	return 0
}

// cmdAnim настраивает масштаб системных анимаций
func cmdAnim(ab string, args []string) int {
	scales := []string{"window_animation_scale", "transition_animation_scale", "animator_duration_scale"}

	if len(args) == 0 {
		fmt.Println("Текущие параметры анимации:")
		for _, s := range scales {
			val, _, _ := runAdb(ab, []string{"shell", "settings", "get", "global", s})
			fmt.Printf("  %s: %s\n", s, strings.TrimSpace(val))
		}
		fmt.Println("Использование: fx anim [0.5 | 1 | off]")
		return 0
	}

	val := args[0]
	if val == "off" || val == "0" {
		val = "0.0"
	}

	for _, s := range scales {
		runAdb(ab, []string{"shell", "settings", "put", "global", s, val})
	}
	fmt.Printf(msg("animSetOk")+"\n", val)
	return 0
}

// cmdDns настраивает частный DNS (Private DNS)
func cmdDns(ab string, args []string) int {
	if len(args) == 0 {
		mode, _, _ := runAdb(ab, []string{"shell", "settings", "get", "global", "private_dns_mode"})
		spec, _, _ := runAdb(ab, []string{"shell", "settings", "get", "global", "private_dns_specifier"})
		fmt.Printf("Private DNS: режим=%s, хост=%s\n", strings.TrimSpace(mode), strings.TrimSpace(spec))
		fmt.Println("Использование: fx dns [adguard | cloudflare | google | off]")
		return 0
	}

	prov := strings.ToLower(args[0])
	switch prov {
	case "adguard":
		runAdb(ab, []string{"shell", "settings", "put", "global", "private_dns_mode", "hostname"})
		runAdb(ab, []string{"shell", "settings", "put", "global", "private_dns_specifier", "dns.adguard-dns.com"})
		fmt.Println(msg("dnsAdguardOk"))
	case "cloudflare":
		runAdb(ab, []string{"shell", "settings", "put", "global", "private_dns_mode", "hostname"})
		runAdb(ab, []string{"shell", "settings", "put", "global", "private_dns_specifier", "one.one.one.one"})
		fmt.Println("Включен Cloudflare DNS (1.1.1.1)")
	case "google":
		runAdb(ab, []string{"shell", "settings", "put", "global", "private_dns_mode", "hostname"})
		runAdb(ab, []string{"shell", "settings", "put", "global", "private_dns_specifier", "dns.google"})
		fmt.Println("Включен Google DNS (8.8.8.8)")
	case "off":
		runAdb(ab, []string{"shell", "settings", "put", "global", "private_dns_mode", "off"})
		fmt.Println("Private DNS отключен.")
	default:
		fmt.Fprintln(os.Stderr, "Неизвестный провайдер. Допустимо: adguard, cloudflare, google, off")
		return 2
	}
	return 0
}

// cmdBattery выводит сводку по аккумулятору
func cmdBattery(ab string, _ []string) int {
	out, code, err := runAdb(ab, []string{"shell", "dumpsys", "battery"})
	if err != nil || code != 0 {
		reportRunFail("dumpsys battery", code, err)
		return 1
	}

	fmt.Println("Состояние аккумулятора:")
	for _, l := range strings.Split(out, "\n") {
		l = strings.TrimSpace(l)
		parts := strings.SplitN(l, ":", 2)
		if len(parts) != 2 {
			continue
		}
		k := strings.TrimSpace(parts[0])
		v := strings.TrimSpace(parts[1])
		switch k {
		case "level":
			fmt.Printf("  Заряд: %s%%\n", v)
		case "temperature":
			if t, err := strconv.Atoi(v); err == nil {
				fmt.Printf("  Температура: %.1f °C\n", float64(t)/10.0)
			}
		case "voltage":
			if volt, err := strconv.Atoi(v); err == nil {
				fmt.Printf("  Напряжение: %.2f V\n", float64(volt)/1000.0)
			}
		case "status":
			statusNames := map[string]string{"1": "Неизвестно", "2": "Заряжается", "3": "Разряжается", "4": "Не заряжается", "5": "Полный"}
			name := statusNames[v]
			if name == "" {
				name = v
			}
			fmt.Printf("  Статус: %s\n", name)
		case "health":
			healthNames := map[string]string{"1": "Неизвестно", "2": "Отличное", "3": "Перегрев", "4": "Мертв", "5": "Перенапряжение"}
			name := healthNames[v]
			if name == "" {
				name = v
			}
			fmt.Printf("  Здоровье: %s\n", name)
		}
	}
	return 0
}

func resolveKeyCode(k string) string {
	keyMap := map[string]string{
		"home":    "3",
		"back":    "4",
		"power":   "26",
		"volup":   "24",
		"voldown": "25",
		"menu":    "82",
		"enter":   "66",
		"space":   "62",
		"tab":     "61",
	}
	code, ok := keyMap[strings.ToLower(k)]
	if ok {
		return code
	}
	return k
}

// cmdKey отправляет аппаратную клавишу
func cmdKey(ab string, args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "Использование: fx key [back | home | power | volup | voldown | menu | enter]")
		return 2
	}

	k := args[0]
	code := resolveKeyCode(k)

	_, codeExit, err := runAdb(ab, []string{"shell", "input", "keyevent", code})
	if err != nil || codeExit != 0 {
		reportRunFail("input keyevent", codeExit, err)
		return 1
	}
	fmt.Printf("Нажата клавиша: %s\n", k)
	return 0
}

// cmdInfo выводит паспорт устройства
func cmdInfo(ab string, _ []string) int {
	brand, _, _ := runAdb(ab, []string{"shell", "getprop", "ro.product.brand"})
	model, _, _ := runAdb(ab, []string{"shell", "getprop", "ro.product.model"})
	device, _, _ := runAdb(ab, []string{"shell", "getprop", "ro.product.device"})
	androidVer, _, _ := runAdb(ab, []string{"shell", "getprop", "ro.build.version.release"})
	sdk, _, _ := runAdb(ab, []string{"shell", "getprop", "ro.build.version.sdk"})
	patch, _, _ := runAdb(ab, []string{"shell", "getprop", "ro.build.version.security_patch"})
	abi, _, _ := runAdb(ab, []string{"shell", "getprop", "ro.product.cpu.abi"})
	ip, _, _ := runAdb(ab, []string{"shell", "ip route 2>/dev/null | awk '{print $9}' | head -n 1"})

	fmt.Println("Паспорт устройства:")
	fmt.Printf("  Модель:        %s %s (%s)\n", strings.TrimSpace(brand), strings.TrimSpace(model), strings.TrimSpace(device))
	fmt.Printf("  Версия Android: %s (API %s)\n", strings.TrimSpace(androidVer), strings.TrimSpace(sdk))
	fmt.Printf("  Архитектура:   %s\n", strings.TrimSpace(abi))
	fmt.Printf("  Патч безопасн.: %s\n", strings.TrimSpace(patch))
	if strings.TrimSpace(ip) != "" {
		fmt.Printf("  IP-адрес:      %s\n", strings.TrimSpace(ip))
	}
	return 0
}

// cmdAsk задает вопрос ИИ-помощнику
func cmdAsk(args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "Использование: fx ask \"Ваш вопрос по Foxhole или Android\"")
		return 2
	}

	query := strings.Join(args, " ")
	fmt.Println("Запрос к ИИ-помощнику Foxhole...")

	apiUrl := "https://foxhole-telegram-bot.onrender.com/api/ask"
	reqBody, _ := json.Marshal(map[string]string{"query": query})

	client := &http.Client{Timeout: 60 * time.Second}
	resp, err := client.Post(apiUrl, "application/json", bytes.NewReader(reqBody))
	if err != nil {
		// Попытка через GET fallback
		fallbackUrl := apiUrl + "?q=" + url.QueryEscape(query)
		resp, err = client.Get(fallbackUrl)
	}

	if err != nil {
		fmt.Fprintf(os.Stderr, "Ошибка связи с сервисом ИИ: %v\n", err)
		return 1
	}
	defer resp.Body.Close()

	bodyBytes, _ := io.ReadAll(resp.Body)
	var res struct {
		Status string `json:"status"`
		Answer string `json:"answer"`
		Error  string `json:"error"`
	}
	if err := json.Unmarshal(bodyBytes, &res); err != nil || res.Answer == "" {
		if res.Error != "" {
			fmt.Fprintf(os.Stderr, "Ошибка ИИ: %s\n", res.Error)
		} else {
			fmt.Printf("Ответ: %s\n", string(bodyBytes))
		}
		return 1
	}

	fmt.Printf("\n[ИИ-Помощник]:\n%s\n\n", res.Answer)
	return 0
}

// cmdOpen открывает URL или диплинк в браузере / приложении на телефоне
func cmdOpen(ab string, args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "Использование: fx open <URL или диплинк> (например, fx open https://google.com)")
		return 2
	}
	target := args[0]
	if !strings.Contains(target, "://") {
		target = "https://" + target
	}
	fmt.Printf("Открытие ссылки на телефоне: %s\n", target)
	_, code, err := runAdb(ab, []string{"shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", target})
	if err != nil || code != 0 {
		reportRunFail("am start -a android.intent.action.VIEW", code, err)
		return 1
	}
	return 0
}

// cmdDarkmode переключает темную/светлую тему оформления
func cmdDarkmode(ab string, args []string) int {
	mode := "toggle"
	if len(args) > 0 {
		mode = strings.ToLower(args[0])
	}

	switch mode {
	case "on", "yes", "true", "1", "dark":
		runAdb(ab, []string{"shell", "cmd", "uimode", "night", "yes"})
		fmt.Println("Темная тема включена.")
	case "off", "no", "false", "0", "light":
		runAdb(ab, []string{"shell", "cmd", "uimode", "night", "no"})
		fmt.Println("Темная тема выключена (светлая тема активна).")
	case "toggle":
		out, _, _ := runAdb(ab, []string{"shell", "cmd", "uimode", "night"})
		if strings.Contains(strings.ToLower(out), "yes") {
			runAdb(ab, []string{"shell", "cmd", "uimode", "night", "no"})
			fmt.Println("Темная тема выключена (светлая тема).")
		} else {
			runAdb(ab, []string{"shell", "cmd", "uimode", "night", "yes"})
			fmt.Println("Темная тема включена.")
		}
	default:
		fmt.Fprintln(os.Stderr, "Использование: fx darkmode [on | off | toggle]")
		return 2
	}
	return 0
}

// cmdStayAwake настраивает экран, чтобы он не гас при подключении к питанию/ПК
func cmdStayAwake(ab string, args []string) int {
	mode := "on"
	if len(args) > 0 {
		mode = strings.ToLower(args[0])
	}

	switch mode {
	case "on", "yes", "true", "1":
		runAdb(ab, []string{"shell", "settings", "put", "global", "stay_on_while_plugged_in", "3"})
		runAdb(ab, []string{"shell", "svc", "power", "stayon", "true"})
		fmt.Println("Режим «Не выключать экран при подключении» ВКЛЮЧЕН.")
	case "off", "no", "false", "0":
		runAdb(ab, []string{"shell", "settings", "put", "global", "stay_on_while_plugged_in", "0"})
		runAdb(ab, []string{"shell", "svc", "power", "stayon", "false"})
		fmt.Println("Режим «Не выключать экран при подключении» ВЫКЛЮЧЕН.")
	default:
		fmt.Fprintln(os.Stderr, "Использование: fx stay-awake [on | off]")
		return 2
	}
	return 0
}

// cmdSearch ищет установленные пакеты по ключевому слову
func cmdSearch(ab string, args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "Использование: fx search <ключевое_слово>")
		return 2
	}
	query := strings.ToLower(args[0])
	out, code, err := runAdb(ab, []string{"shell", "pm", "list", "packages"})
	if err != nil || code != 0 {
		reportRunFail("pm list packages", code, err)
		return 1
	}

	found := 0
	for _, l := range strings.Split(out, "\n") {
		l = strings.TrimSpace(l)
		pkg := strings.TrimPrefix(l, "package:")
		if strings.Contains(strings.ToLower(pkg), query) {
			found++
			fmt.Printf("  %d. %s\n", found, pkg)
		}
	}
	if found == 0 {
		fmt.Printf("Пакеты по запросу %q не найдены.\n", query)
	} else {
		fmt.Printf("Найдено пакетов: %d\n", found)
	}
	return 0
}

// cmdClear очищает данные и кэш приложения
func cmdClear(ab string, args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "Использование: fx clear <имя_пакета>")
		return 2
	}
	pkg := args[0]
	if !validatePkg(pkg) {
		fmt.Fprintf(os.Stderr, "Недопустимое имя пакета %q\n", pkg)
		return 2
	}
	fmt.Printf("Очистка данных приложения %s...\n", pkg)
	out, code, err := runAdb(ab, []string{"shell", "pm", "clear", pkg})
	if err != nil || code != 0 {
		reportRunFail("pm clear", code, err)
		return 1
	}
	fmt.Println(strings.TrimSpace(out))
	return 0
}

// cmdVolume управляет громкостью медиа
func cmdVolume(ab string, args []string) int {
	if len(args) == 0 {
		fmt.Println("Использование: fx volume [0-15 | up | down | mute]")
		return 2
	}
	sub := strings.ToLower(args[0])
	switch sub {
	case "up":
		runAdb(ab, []string{"shell", "input", "keyevent", "24"})
		fmt.Println("Громкость увеличена (+).")
	case "down":
		runAdb(ab, []string{"shell", "input", "keyevent", "25"})
		fmt.Println("Громкость уменьшена (-).")
	case "mute":
		runAdb(ab, []string{"shell", "input", "keyevent", "164"})
		fmt.Println("Звук выключен (mute).")
	default:
		lvl, err := strconv.Atoi(sub)
		if err != nil || lvl < 0 || lvl > 15 {
			fmt.Fprintln(os.Stderr, "Уровень громкости должен быть числом от 0 до 15, либо up, down, mute.")
			return 2
		}
		runAdb(ab, []string{"shell", "cmd", "media_session", "volume", "--show", "--set", strconv.Itoa(lvl)})
		fmt.Printf("Уровень громкости медиа установлен в: %d\n", lvl)
	}
	return 0
}

// cmdTop показывает быструю сводку загрузки CPU и памяти RAM
func cmdTop(ab string, _ []string) int {
	fmt.Println("--- Топ процессов по CPU ---")
	topOut, _, _ := runAdb(ab, []string{"shell", "top -b -n 1 -m 5 -s cpu 2>/dev/null || top -n 1 | head -n 10"})
	for _, l := range strings.Split(topOut, "\n") {
		l = strings.TrimRight(l, "\r\n")
		if strings.TrimSpace(l) != "" {
			fmt.Println("  " + l)
		}
	}

	fmt.Println("\n--- Память RAM ---")
	memOut, _, _ := runAdb(ab, []string{"shell", "dumpsys", "meminfo"})
	for _, l := range strings.Split(memOut, "\n") {
		l = strings.TrimSpace(l)
		if strings.HasPrefix(l, "Total RAM:") || strings.HasPrefix(l, "Free RAM:") || strings.HasPrefix(l, "Used RAM:") {
			fmt.Println("  " + l)
		}
	}
	return 0
}

// cmdWifi управляет состоянием Wi-Fi на устройстве
func cmdWifi(ab string, args []string) int {
	mode := "status"
	if len(args) > 0 {
		mode = strings.ToLower(args[0])
	}

	switch mode {
	case "on", "enable", "1":
		runAdb(ab, []string{"shell", "svc", "wifi", "enable"})
		fmt.Println("Wi-Fi включен.")
	case "off", "disable", "0":
		runAdb(ab, []string{"shell", "svc", "wifi", "disable"})
		fmt.Println("Wi-Fi выключен.")
	case "status":
		out, _, _ := runAdb(ab, []string{"shell", "dumpsys", "wifi | grep -i 'Wi-Fi is' | head -n 1"})
		if strings.TrimSpace(out) != "" {
			fmt.Println("Статус Wi-Fi:", strings.TrimSpace(out))
		} else {
			fmt.Println("Использование: fx wifi [on | off]")
		}
	default:
		fmt.Fprintln(os.Stderr, "Использование: fx wifi [on | off]")
		return 2
	}
	return 0
}

func openFileInSystem(path string) error {
	switch runtime.GOOS {
	case "windows":
		return exec.Command("cmd", "/c", "start", "", path).Start()
	case "darwin":
		return exec.Command("open", path).Start()
	default:
		return exec.Command("xdg-open", path).Start()
	}
}

// cmdView делает мгновенный снимок экрана и открывает его на ПК в просмотрщике
func cmdView(ab string, _ []string) int {
	tmpFile, err := os.CreateTemp("", "foxhole_view_*.png")
	if err != nil {
		fmt.Fprintf(os.Stderr, "Не удалось создать временный файл: %v\n", err)
		return 1
	}
	tmpPath := tmpFile.Name()
	tmpFile.Close()

	fmt.Println("Снимок экрана устройства...")
	code := cmdScreenshot(ab, []string{tmpPath})
	if code != 0 {
		os.Remove(tmpPath)
		return code
	}

	fmt.Printf("Открытие экрана: %s\n", tmpPath)
	if err := openFileInSystem(tmpPath); err != nil {
		fmt.Fprintf(os.Stderr, "Не удалось открыть просмотрщик: %v\n", err)
		return 1
	}
	return 0
}

func findScrcpy() string {
	name := "scrcpy"
	if runtime.GOOS == "windows" {
		name = "scrcpy.exe"
	}
	// 1. В PATH
	if p, err := findInPath(name); err == nil && p != "" {
		return p
	}
	// 2. Рядом с fx.exe
	if exe, err := os.Executable(); err == nil {
		dir := filepath.Dir(exe)
		p := filepath.Join(dir, "scrcpy", name)
		if _, err := os.Stat(p); err == nil {
			return p
		}
		p = filepath.Join(dir, name)
		if _, err := os.Stat(p); err == nil {
			return p
		}
	}
	// 3. В каталоге AppData/scrcpy
	if appData, err := defaultToolsDir(); err == nil {
		parent := filepath.Dir(appData)
		scrcpyDir := filepath.Join(parent, "scrcpy")
		p := filepath.Join(scrcpyDir, name)
		if _, err := os.Stat(p); err == nil {
			return p
		}
		entries, _ := os.ReadDir(scrcpyDir)
		for _, e := range entries {
			if e.IsDir() {
				subP := filepath.Join(scrcpyDir, e.Name(), name)
				if _, err := os.Stat(subP); err == nil {
					return subP
				}
			}
		}
	}
	return ""
}

// cmdMirror запускает интерактивную трансляцию экрана и управление телефоном
func cmdMirror(_ string, args []string) int {
	scrcpyPath := findScrcpy()
	if scrcpyPath == "" {
		fetchFlag := false
		for _, a := range args {
			if a == "--fetch" || a == "-f" || a == "--download" {
				fetchFlag = true
				break
			}
		}

		if !fetchFlag {
			if runtime.GOOS == "windows" {
				fmt.Println("scrcpy (утилита зеркалирования экрана и управления мышью/клавиатурой) не найдена.")
				fmt.Println("Запустите: fx mirror --fetch (для автоматического скачивания официальной портативной scrcpy)")
				fmt.Println("Или скачайте вручную с: https://github.com/Genymobile/scrcpy/releases")
			} else {
				fmt.Println("scrcpy не найдена. Установите её через системный менеджер пакетов:")
				fmt.Println("  Linux:  sudo apt install scrcpy  (или pacman -S scrcpy)")
				fmt.Println("  macOS:  brew install scrcpy")
			}
			return 1
		}

		if runtime.GOOS != "windows" {
			fmt.Println("Автоматическое скачивание scrcpy через --fetch пока поддержано для Windows.")
			fmt.Println("На вашей системе используйте: sudo apt install scrcpy (или brew install scrcpy).")
			return 1
		}

		destDir, err := defaultToolsDir()
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			return 1
		}
		parent := filepath.Join(filepath.Dir(destDir), "scrcpy")
		os.MkdirAll(parent, 0o755)

		scrcpyUrl := "https://github.com/Genymobile/scrcpy/releases/download/v2.7/scrcpy-win64-v2.7.zip"
		fmt.Printf("Скачивание портативной scrcpy v2.7 (%s)...\n", scrcpyUrl)
		if err := downloadAndUnpack(scrcpyUrl, parent); err != nil {
			fmt.Fprintf(os.Stderr, "Не удалось скачать scrcpy: %v\n", err)
			return 1
		}
		fmt.Println("scrcpy успешно скачан и распакован!")
		scrcpyPath = findScrcpy()
		if scrcpyPath == "" {
			fmt.Fprintln(os.Stderr, "Не удалось найти scrcpy.exe после распаковки.")
			return 1
		}
	}

	fmt.Printf("Запуск трансляции экрана через %s...\n", scrcpyPath)
	scrcpyCmdArgs := []string{"--window-title", "Foxhole Screen Mirror"}
	for _, a := range args {
		if a != "--fetch" && a != "-f" && a != "--download" {
			scrcpyCmdArgs = append(scrcpyCmdArgs, a)
		}
	}

	c := exec.Command(scrcpyPath, scrcpyCmdArgs...)
	c.Stdin = os.Stdin
	c.Stdout = os.Stdout
	c.Stderr = os.Stderr
	if err := c.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "Ошибка при работе scrcpy: %v\n", err)
		return 1
	}
	return 0
}
