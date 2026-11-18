package main

import (
	"fmt"
	"os"
	"strings"
)

var version = "dev"

var lang string

func main() {
	os.Exit(run(os.Args[1:]))
}

func run(argv []string) int {
	lang = detectLang()
	rest, err := extractGlobals(argv)
	if err != nil {
		fmt.Fprintln(os.Stderr, msg("errGlobal")+":", err)
		return 2
	}
	sub, args := shift(rest)

	switch sub {
	case "":
		fmt.Fprint(os.Stderr, msg("usage"))
		return 2
	case "-h", "--help", "help":
		fmt.Print(msg("usage"))
		return 0
	case "-v", "--version", "version":
		fmt.Println("foxhole-adb", version, "(GPL-3.0)")
		return 0
	case "ask":
		return cmdAsk(args)
	}

	ab := os.Getenv("FOXHOLE_ADB")
	if gAdbFlag != "" {
		ab = gAdbFlag
	}
	abPath, found := locateAdb(ab)
	if !found && sub != "fetch" {
		fmt.Fprintln(os.Stderr, strings.ReplaceAll(msg("noAdb"), "@CMD@", adbNameForOS())+
			"\n  "+msg("hintFetch"))
		return 1
	}

	switch sub {
	case "fetch":
		return cmdFetch(args)
	case "doctor":
		return cmdDoctor(abPath, args)
	case "pair":
		return cmdPair(abPath, args)
	case "connect":
		return cmdConnect(abPath, args)
	case "shell":
		return cmdPass(abPath, "shell", args)
	case "install":
		return cmdInstall(abPath, args)
	case "push":
		return cmdTwo(abPath, "push", args)
	case "pull":
		return cmdTwo(abPath, "pull", args)
	case "logcat":
		return cmdPass(abPath, "logcat", args)
	case "screenshot":
		return cmdScreenshot(abPath, args)
	case "reboot":
		return cmdReboot(abPath, args)
	case "devices":
		return cmdDevices(abPath, args)
	case "wait-for-device", "wait":
		return cmdWait(abPath, args)
	case "mdns":
		return cmdMdns(abPath, args)
	case "bugreport":
		return cmdBugreport(abPath, args)
	case "disconnect":
		return cmdDisconnect(abPath, args)
	case "forward":
		return cmdForward(abPath, args)
	case "reverse":
		return cmdReverse(abPath, args)
	case "tcpip":
		return cmdTcpip(abPath, args)
	case "usb":
		return cmdUsb(abPath, args)
	case "start-server":
		return cmdStartServer(abPath, args)
	case "kill-server":
		return cmdKillServer(abPath, args)
	case "dumpsys":
		return cmdDumpsys(abPath, args)
	case "getprop":
		return cmdGetprop(abPath, args)
	case "setprop":
		return cmdSetprop(abPath, args)
	case "pm":
		return cmdPm(abPath, args)
	case "setting", "settings":
		return cmdSetting(abPath, args)
	case "uninstall":
		return cmdUninstall(abPath, args)
	case "install-multiple":
		return cmdInstallMultiple(abPath, args)
	case "sideload":
		return cmdSideload(abPath, args)
	case "backup":
		return cmdBackup(abPath, args)
	case "wm":
		return cmdWm(abPath, args)
	case "root":
		return cmdRootUnroot(abPath, "root", args)
	case "unroot":
		return cmdRootUnroot(abPath, "unroot", args)
	case "start":
		return cmdFoxholeStart(abPath, args)
	case "status", "ping":
		return cmdFoxholeStatus(abPath, args)
	case "stop":
		return cmdFoxholeStop(abPath, args)
	case "clip":
		return cmdClip(abPath, args)
	case "type":
		return cmdType(abPath, args)
	case "photos":
		return cmdPhotos(abPath, args)
	case "record":
		return cmdRecord(abPath, args)
	case "apk":
		return cmdApk(abPath, args)
	case "install-all":
		return cmdInstallAll(abPath, args)
	case "anim":
		return cmdAnim(abPath, args)
	case "dns":
		return cmdDns(abPath, args)
	case "battery":
		return cmdBattery(abPath, args)
	case "key":
		return cmdKey(abPath, args)
	case "info":
		return cmdInfo(abPath, args)
	case "open":
		return cmdOpen(abPath, args)
	case "darkmode":
		return cmdDarkmode(abPath, args)
	case "stay-awake":
		return cmdStayAwake(abPath, args)
	case "search", "find":
		return cmdSearch(abPath, args)
	case "clear":
		return cmdClear(abPath, args)
	case "volume":
		return cmdVolume(abPath, args)
	case "top":
		return cmdTop(abPath, args)
	case "wifi":
		return cmdWifi(abPath, args)
	case "mirror":
		return cmdMirror(abPath, args)
	case "view":
		return cmdView(abPath, args)
	case "panel", "toolbar":
		launchToolbar(abPath, nil)
		return 0
	default:
		fmt.Fprintln(os.Stderr, msg("unknown")+" "+sub)
		fmt.Fprint(os.Stderr, msg("usage"))
		return 2
	}
}
