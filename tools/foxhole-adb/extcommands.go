package main

import (
	"bufio"
	"fmt"
	"os"
	"os/exec"
	"strconv"
	"strings"
)

func guardYes(cmdline string, args []string) ([]string, bool) {
	yes := false
	rest := []string{}
	for _, a := range args {
		if a == "--yes" {
			yes = true
			continue
		}
		rest = append(rest, a)
	}
	if yes {
		return rest, true
	}
	fmt.Printf(msg("confirmAsk")+"\n", cmdline)
	r := bufio.NewReader(os.Stdin)
	line, err := r.ReadString('\n')
	if err != nil {
		fmt.Println(msg("rebootCanceled"))
		return nil, false
	}
	line = strings.ToLower(strings.TrimSpace(line))
	if line == "y" || line == "yes" || strings.HasPrefix(line, "д") {
		return rest, true
	}
	fmt.Println(msg("rebootCanceled"))
	return nil, false
}

func validatePkg(pkg string) bool {
	if len(pkg) < 3 || len(pkg) > 256 {
		return false
	}
	for _, r := range pkg {
		if !(r == '.' || r == '_' || r == '-' ||
			(r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9')) {
			return false
		}
	}
	return true
}

var namespaces = map[string]bool{"global": true, "secure": true, "system": true}

func validateWmSize(v string) bool {
	i := strings.IndexByte(v, 'x')
	if i <= 0 || i == len(v)-1 {
		return false
	}
	a, err1 := strconv.Atoi(v[:i])
	b, err2 := strconv.Atoi(v[i+1:])
	return err1 == nil && err2 == nil && a > 0 && a < 10000 && b > 0 && b < 10000
}

func validateDensity(v string) bool {
	d, err := strconv.Atoi(v)
	return err == nil && d >= 100 && d <= 2000
}

func validatePeer(v string) bool {
	if v == "" || len(v) > 256 || strings.ContainsAny(v, " \t\r\n") {
		return false
	}
	switch {
	case strings.HasPrefix(v, "tcp:"):
		p, err := strconv.Atoi(v[4:])
		return err == nil && p > 0 && p <= 65535
	case strings.HasPrefix(v, "localabstract:"),
		strings.HasPrefix(v, "localfilesystem:"),
		strings.HasPrefix(v, "dev:"),
		strings.HasPrefix(v, "jdwp:"):
		return true
	}
	return false
}

func q(s string) string {
	return "'" + strings.ReplaceAll(s, "'", `'\''`) + "'"
}

func runInteractive(adbPath string, args []string) int {
	cmd := exec.Command(adbPath, args...)
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	err := cmd.Run()
	if err == nil {
		return 0
	}
	if ee, ok := err.(*exec.ExitError); ok {
		return ee.ExitCode()
	}
	fmt.Fprintf(os.Stderr, msg("runFail")+"\n", args[0], err)
	return 1
}

func splitDaemonNoise(lines []string) ([]string, []string) {
	rest := []string{}
	noise := []string{}
	for _, l := range lines {
		l = strings.TrimSpace(l)
		switch {
		case strings.Contains(l, "daemon not running"):
			noise = append(noise, msg("daemonStart"))
		case strings.Contains(l, "daemon started successfully"):
			noise = append(noise, msg("daemonStarted"))
		case strings.HasPrefix(l, "*"):
		default:
			rest = append(rest, l)
		}
	}
	return rest, noise
}

func explainConn(out string) string {
	lines, _ := splitDaemonNoise(strings.Split(strings.ReplaceAll(out, "\r\n", "\n"), "\n"))
	l := strings.TrimSpace(strings.Join(lines, " "))
	addr := ""
	if f := strings.Fields(l); len(f) > 0 {
		addr = f[len(f)-1]
	}
	switch {
	case strings.Contains(l, "Successfully paired"):
		return fmt.Sprintf(msg("pairOk"), addr)
	case strings.Contains(l, "already connected"):
		return fmt.Sprintf(msg("connectAlready"), addr)
	case strings.HasPrefix(l, "connected to"):
		return fmt.Sprintf(msg("connectOk"), addr)
	case strings.HasPrefix(l, "cannot connect"),
		strings.HasPrefix(l, "failed to authenticate"),
		strings.HasPrefix(l, "failed to connect"):
		return fmt.Sprintf(msg("connectFail"), l)
	}
	return out
}

func explainInstall(out string) string {
	lines, _ := splitDaemonNoise(strings.Split(strings.ReplaceAll(out, "\r\n", "\n"), "\n"))
	l := strings.TrimSpace(strings.Join(lines, "\n"))
	if l == "Success" || strings.Contains(l, "\nSuccess") || strings.HasSuffix(l, "Success") {
		return msg("installOk")
	}
	return out
}

func explainUninstall(out string) string {
	lines, _ := splitDaemonNoise(strings.Split(strings.ReplaceAll(out, "\r\n", "\n"), "\n"))
	if strings.Contains(strings.Join(lines, "\n"), "Success") {
		return msg("uninstallOk")
	}
	return out
}

func cmdDevices(ab string, _ []string) int {
	out, code, err := runAdb(ab, []string{"devices", "-l"})
	if err != nil {
		fmt.Fprintf(os.Stderr, msg("runFail")+"\n", "devices", err)
		return 1
	}
	rows, daemonMsgs := splitDaemonNoise(strings.Split(strings.ReplaceAll(out, "\r\n", "\n"), "\n"))
	for _, m := range daemonMsgs {
		fmt.Println("  " + m)
	}
	fmt.Println(msg("devicesHeader"))
	shown := 0
	for _, l := range rows {
		if l == "" || strings.HasPrefix(l, "List of devices") {
			continue
		}
		fmt.Println("  " + l)
		shown++
	}
	if shown > 0 {
		fmt.Println("  " + msg("devicesLegend"))
	}
	return code
}

func cmdWait(ab string, _ []string) int {
	return runInteractive(ab, []string{"wait-for-device"})
}

func cmdMdns(ab string, args []string) int {
	for _, a := range args {
		if a != "services" && a != "check" {
			fmt.Fprintln(os.Stderr, msg("mdnsBad"))
			return 2
		}
	}
	return runInteractive(ab, append([]string{"mdns"}, args...))
}

func cmdBugreport(ab string, args []string) int {
	if len(args) > 1 {
		fmt.Fprintln(os.Stderr, msg("bugreportBad"))
		return 2
	}
	if len(args) == 1 {
		return runInteractive(ab, []string{"bugreport", args[0]})
	}
	return runInteractive(ab, []string{"bugreport"})
}

func cmdDisconnect(ab string, args []string) int {
	if len(args) > 1 {
		fmt.Fprintln(os.Stderr, msg("connectBadForm"))
		return 2
	}
	if len(args) == 1 {
		host, port, problem := parseHostPort(args[0], false)
		if problem != 0 {
			fmt.Fprintln(os.Stderr, msg("connectBadForm"))
			return 2
		}
		return runInteractive(ab, []string{"disconnect", fmt.Sprintf("%s:%d", host, port)})
	}
	return runInteractive(ab, []string{"disconnect"})
}

func cmdForward(ab string, args []string) int {
	return cmdPeerMap(ab, "forward", args)
}

func cmdReverse(ab string, args []string) int {
	return cmdPeerMap(ab, "reverse", args)
}

func cmdPeerMap(ab string, sub string, args []string) int {
	if len(args) != 2 || !validatePeer(args[0]) || !validatePeer(args[1]) {
		fmt.Fprintf(os.Stderr, msg("peerBad")+"\n", sub)
		return 2
	}
	return runInteractive(ab, []string{sub, args[0], args[1]})
}

func cmdTcpip(ab string, args []string) int {
	if len(args) > 1 {
		fmt.Fprintln(os.Stderr, msg("tcpipBad"))
		return 2
	}
	if len(args) == 1 {
		p, err := strconv.Atoi(args[0])
		if err != nil || p < 1024 || p > 65535 {
			fmt.Fprintln(os.Stderr, msg("tcpipBad"))
			return 2
		}
	}
	rest, ok := guardYes("tcpip", args)
	if !ok {
		return 0
	}
	full := []string{"tcpip"}
	if len(rest) == 1 {
		full = append(full, rest[0])
	}
	return runInteractive(ab, full)
}

func cmdUsb(ab string, _ []string) int {
	return runInteractive(ab, []string{"usb"})
}

func cmdStartServer(ab string, _ []string) int {
	return runInteractive(ab, []string{"start-server"})
}

func cmdKillServer(ab string, _ []string) int {
	return runInteractive(ab, []string{"kill-server"})
}

func cmdDumpsys(ab string, args []string) int {
	remote := []string{"dumpsys"}
	for _, a := range args {
		remote = append(remote, q(a))
	}
	return runInteractive(ab, append([]string{"shell"}, remote...))
}

func cmdGetprop(ab string, args []string) int {
	if len(args) > 1 {
		fmt.Fprintln(os.Stderr, msg("getpropBad"))
		return 2
	}
	remote := []string{"getprop"}
	if len(args) == 1 {
		remote = append(remote, q(args[0]))
	}
	return runInteractive(ab, append([]string{"shell"}, remote...))
}

func cmdPm(ab string, args []string) int {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, msg("pmBad"))
		return 2
	}
	switch strings.ToLower(args[0]) {
	case "list":
		if len(args) < 2 || strings.ToLower(args[1]) != "packages" {
			fmt.Fprintln(os.Stderr, msg("pmListBad"))
			return 2
		}
		return cmdPmList(ab, args[2:])
	case "grant", "revoke":
		return cmdPmGrantRevoke(ab, strings.ToLower(args[0]), args[1:])
	default:
		fmt.Fprintln(os.Stderr, msg("pmBad"))
		return 2
	}
}

func cmdPmList(ab string, args []string) int {
	if len(args) > 1 {
		fmt.Fprintln(os.Stderr, msg("pmListBad"))
		return 2
	}
	remote := []string{"pm", "list", "packages"}
	if len(args) == 1 {
		remote = append(remote, q(args[0]))
	}
	return runInteractive(ab, append([]string{"shell"}, remote...))
}

func cmdSetting(ab string, args []string) int {
	if len(args) < 2 {
		fmt.Fprintln(os.Stderr, msg("settingBad"))
		return 2
	}
	sub := strings.ToLower(args[0])
	ns := strings.ToLower(args[1])
	if !namespaces[ns] {
		fmt.Fprintf(os.Stderr, msg("nsBad")+"\n", ns)
		return 2
	}
	switch sub {
	case "get":
		if len(args) != 3 {
			fmt.Fprintln(os.Stderr, msg("settingBad"))
			return 2
		}
		return runInteractive(ab, []string{"shell", "settings", "get", q(ns), q(args[2])})
	case "list":
		if len(args) != 2 {
			fmt.Fprintln(os.Stderr, msg("settingBad"))
			return 2
		}
		return runInteractive(ab, []string{"shell", "settings", "list", q(ns)})
	case "put":
		if len(args) < 4 {
			fmt.Fprintln(os.Stderr, msg("settingBad"))
			return 2
		}
		rest, ok := guardYes("setting put "+ns, args[2:])
		if !ok {
			return 0
		}
		remote := []string{"settings", "put", q(ns), q(rest[0])}
		val := strings.Join(rest[1:], " ")
		remote = append(remote, q(val))
		return runInteractive(ab, append([]string{"shell"}, remote...))
	case "delete":
		if len(args) != 3 {
			fmt.Fprintln(os.Stderr, msg("settingBad"))
			return 2
		}
		rest, ok := guardYes("setting delete "+ns, args[2:])
		if !ok {
			return 0
		}
		return runInteractive(ab, []string{"shell", "settings", "delete", q(ns), q(rest[0])})
	default:
		fmt.Fprintln(os.Stderr, msg("settingBad"))
		return 2
	}
}

func cmdSetprop(ab string, args []string) int {
	if len(args) != 2 {
		fmt.Fprintln(os.Stderr, msg("setpropBad"))
		return 2
	}
	if _, ok := guardYes("setprop", args); !ok {
		return 0
	}
	return runInteractive(ab, []string{"shell", "setprop", q(args[0]), q(args[1])})
}

func cmdPmGrantRevoke(ab string, sub string, args []string) int {
	if len(args) != 2 || !validatePkg(args[0]) {
		fmt.Fprintf(os.Stderr, msg("pkgBad")+"\n", "pm "+sub)
		return 2
	}
	if _, ok := guardYes("pm "+sub, args); !ok {
		return 0
	}
	return runInteractive(ab, []string{"shell", "pm", sub, q(args[0]), q(args[1])})
}

func cmdUninstall(ab string, args []string) int {
	keep := false
	rest := []string{}
	for _, a := range args {
		if a == "-k" {
			keep = true
			continue
		}
		rest = append(rest, a)
	}
	if len(rest) < 1 || len(rest) > 2 {
		fmt.Fprintf(os.Stderr, msg("pkgBad")+"\n", "uninstall")
		return 2
	}
	for _, a := range rest {
		if !validatePkg(a) {
			fmt.Fprintf(os.Stderr, msg("pkgBad")+"\n", "uninstall")
			return 2
		}
	}
	if _, ok := guardYes("uninstall "+rest[0], args); !ok {
		return 0
	}
	full := []string{"uninstall"}
	if keep {
		full = append(full, "-k")
	}
	full = append(full, rest[0])
	out, code_, err := runAdb(ab, full)
	if err != nil {
		fmt.Fprintf(os.Stderr, msg("runFail")+"\n", "uninstall", err)
		return 1
	}
	fmt.Println(explainUninstall(out))
	if code_ != 0 {
		return code_
	}
	return 0
}

func cmdWm(ab string, args []string) int {
	if len(args) < 1 || len(args) > 2 {
		fmt.Fprintln(os.Stderr, msg("wmBad"))
		return 2
	}
	sub := strings.ToLower(args[0])
	if sub != "size" && sub != "density" {
		fmt.Fprintln(os.Stderr, msg("wmBad"))
		return 2
	}
	full := []string{"shell", "wm", sub}
	if len(args) == 1 {
		return runInteractive(ab, full)
	}
	switch sub {
	case "size":
		if !validateWmSize(args[1]) {
			fmt.Fprintln(os.Stderr, msg("wmSizeBad"))
			return 2
		}
	case "density":
		if !validateDensity(args[1]) {
			fmt.Fprintln(os.Stderr, msg("wmDensityBad"))
			return 2
		}
	}
	if _, ok := guardYes("wm "+sub, args[1:]); !ok {
		return 0
	}
	full = append(full, q(args[1]))
	return runInteractive(ab, full)
}

func cmdRootUnroot(ab string, sub string, args []string) int {
	if len(args) > 0 {
		fmt.Fprintf(os.Stderr, msg("rootBad")+"\n", sub)
		return 2
	}
	if _, ok := guardYes(sub, args); !ok {
		return 0
	}
	return runInteractive(ab, []string{sub})
}

func cmdBackup(ab string, args []string) int {
	rest, ok := guardYes("backup", args)
	if !ok {
		return 0
	}
	fmt.Fprintln(os.Stderr, msg("backupNote"))
	return runInteractive(ab, append([]string{"backup"}, rest...))
}

func cmdSideload(ab string, args []string) int {
	if len(args) != 1 {
		fmt.Fprintln(os.Stderr, msg("sideloadBad"))
		return 2
	}
	if st, err := os.Stat(args[0]); err != nil || st.IsDir() {
		fmt.Fprintf(os.Stderr, msg("installNoFile")+"\n", args[0])
		return 2
	}
	return runInteractive(ab, []string{"sideload", args[0]})
}

func cmdInstallMultiple(ab string, args []string) int {
	if len(args) < 1 {
		fmt.Fprintln(os.Stderr, msg("installMultiBad"))
		return 2
	}
	for _, a := range args {
		if st, err := os.Stat(a); err != nil || st.IsDir() {
			fmt.Fprintf(os.Stderr, msg("installNoFile")+"\n", a)
			return 2
		}
	}
	full := []string{"install-multiple", "-r"}
	full = append(full, args...)
	out, code_, err := runAdb(ab, full)
	if err != nil {
		fmt.Fprintf(os.Stderr, msg("runFail")+"\n", "install-multiple", err)
		return 1
	}
	fmt.Println(explainInstall(out))
	if code_ != 0 {
		return code_
	}
	return 0
}
