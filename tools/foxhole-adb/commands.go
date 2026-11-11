package main

import (
	"fmt"
	"os"
	"os/exec"
	"strconv"
	"strings"
)

func runAdb(adbPath string, args []string) (string, int, error) {
	cmd := exec.Command(adbPath, args...)
	out, err := cmd.CombinedOutput()
	text := strings.TrimRight(string(out), "\r\n")
	if err != nil {
		if ee, ok := err.(*exec.ExitError); ok {
			return text, ee.ExitCode(), nil
		}
		return text, -1, err
	}
	return text, 0, nil
}

func parseHostPort(v string, requirePort bool) (string, int, int) {
	v = strings.TrimSpace(v)
	if v == "" {
		return "", 0, 1
	}
	host := v
	port := 5555
	if i := strings.LastIndex(v, ":"); i >= 0 {
		host = strings.TrimSpace(v[:i])
		p, err := strconv.Atoi(strings.TrimSpace(v[i+1:]))
		if err != nil || p < 1024 || p > 65535 {
			return "", 0, 2
		}
		port = p
	} else if requirePort {
		return "", 0, 1
	}
	if host == "" || strings.ContainsAny(host, " \t&|;<>$`\"'\n\r") {
		return "", 0, 1
	}
	return host, port, 0
}

func cmdPair(adbPath string, args []string) int {
	if len(args) != 2 {
		fmt.Fprintln(os.Stderr, msg("pairBadForm"))
		return 2
	}
	host, port, problem := parseHostPort(args[0], true)
	if problem == 2 {
		fmt.Fprintf(os.Stderr, msg("badPort")+"\n", tailPort(args[0]))
		return 2
	}
	if problem != 0 {
		fmt.Fprintln(os.Stderr, msg("pairBadForm"))
		return 2
	}
	code := strings.TrimSpace(args[1])
	if len(code) != 6 || !isDigits(code) {
		fmt.Fprintf(os.Stderr, msg("badCode")+"\n", code)
		return 2
	}
	out, code_, err := runAdb(adbPath, []string{"pair", fmt.Sprintf("%s:%d", host, port), code})
	fmt.Println(explainConn(out))
	if code_ != 0 || err != nil {
		fmt.Fprintf(os.Stderr, msg("runFail")+"\n", "pair", err)
		return 1
	}
	return 0
}

func cmdConnect(adbPath string, args []string) int {
	if len(args) != 1 {
		fmt.Fprintln(os.Stderr, msg("connectBadForm"))
		return 2
	}
	host, port, problem := parseHostPort(args[0], false)
	if problem == 2 {
		fmt.Fprintf(os.Stderr, msg("badPort")+"\n", tailPort(args[0]))
		return 2
	}
	if problem != 0 {
		fmt.Fprintln(os.Stderr, msg("connectBadForm"))
		return 2
	}
	out, code_, err := runAdb(adbPath, []string{"connect", fmt.Sprintf("%s:%d", host, port)})
	fmt.Println(explainConn(out))
	if code_ != 0 || err != nil {
		fmt.Fprintf(os.Stderr, msg("runFail")+"\n", "connect", err)
		return 1
	}
	return 0
}

func tailPort(v string) string {
	if i := strings.LastIndex(v, ":"); i >= 0 {
		return strings.TrimSpace(v[i+1:])
	}
	return v
}

func cmdPass(adbPath string, subCmd string, args []string) int {
	return runInteractive(adbPath, append([]string{subCmd}, args...))
}

func cmdInstall(adbPath string, args []string) int {
	if len(args) != 1 || strings.HasPrefix(strings.TrimSpace(args[0]), "-") {
		fmt.Fprintf(os.Stderr, msg("twoBadForm")+"\n", "install APK")
		return 2
	}
	if st, err := os.Stat(args[0]); err != nil || st.IsDir() {
		fmt.Fprintf(os.Stderr, msg("installNoFile")+"\n", args[0])
		return 2
	}
	full := []string{"install", "-r", args[0]}
	out, code_, err := runAdb(adbPath, full)
	if err != nil {
		fmt.Fprintf(os.Stderr, msg("runFail")+"\n", "install", err)
		return 1
	}
	fmt.Println(explainInstall(out))
	if code_ != 0 {
		return code_
	}
	return 0
}

func cmdTwo(adbPath string, subCmd string, args []string) int {
	if len(args) != 2 {
		fmt.Fprintf(os.Stderr, msg("twoBadForm")+"\n", subCmd)
		return 2
	}
	full := append([]string{subCmd}, args...)
	out, code_, err := runAdb(adbPath, full)
	fmt.Println(out)
	if err != nil {
		fmt.Fprintf(os.Stderr, msg("runFail")+"\n", subCmd, err)
		return 1
	}
	if code_ != 0 {
		return code_
	}
	return 0
}

func cmdScreenshot(adbPath string, args []string) int {
	if len(args) != 1 || strings.TrimSpace(args[0]) == "" {
		fmt.Fprintln(os.Stderr, msg("screenshotNoArg"))
		return 2
	}
	file := strings.TrimSpace(args[0])
	cmd := exec.Command(adbPath, "exec-out", "screencap", "-p")
	data, err := cmd.Output()
	if err != nil || len(data) == 0 {
		if err == nil {
			err = fmt.Errorf("пустой вывод")
		}
		fmt.Fprintf(os.Stderr, msg("screenshotFail")+"\n", err)
		return 1
	}
	if err := os.WriteFile(file, data, 0o644); err != nil {
		fmt.Fprintf(os.Stderr, msg("screenshotFail")+"\n", err)
		return 1
	}
	fmt.Printf(msg("screenshotOk")+"\n", file, len(data))
	return 0
}

var rebootModes = map[string]bool{
	"recovery":   true,
	"bootloader": true,
	"fastboot":   true,
	"sideload":   true,
}

func cmdReboot(adbPath string, args []string) int {
	rest := []string{}
	for _, a := range args {
		if a == "--yes" {
			continue
		}
		rest = append(rest, a)
	}
	if len(rest) > 1 {
		fmt.Fprintln(os.Stderr, msg("rebootNoArgs"))
		return 2
	}
	mode := ""
	if len(rest) == 1 {
		mode = strings.ToLower(strings.TrimSpace(rest[0]))
		if !rebootModes[mode] {
			fmt.Fprintf(os.Stderr, msg("rebootMode")+"\n", mode)
			return 2
		}
	}
	serial, _ := currentSerial(adbPath)
	label := "reboot"
	if mode != "" {
		label += " " + mode
	}
	full := []string{"reboot"}
	if mode != "" {
		full = append(full, mode)
	}
	if _, ok := guardYes(label+serialName(serial), args); !ok {
		return 0
	}
	return runInteractive(adbPath, full)
}

func currentSerial(adbPath string) (string, int) {
	out, _, err := runAdb(adbPath, []string{"devices", "-l"})
	if err != nil {
		return "", 1
	}
	for _, line := range strings.Split(out, "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "List of devices") {
			continue
		}
		f := strings.Fields(line)
		if len(f) >= 2 && f[1] == "device" {
			return f[0], 0
		}
	}
	return "", 1
}

func serialName(s string) string {
	if s == "" {
		return ""
	}
	return " (" + s + ")"
}

func isDigits(s string) bool {
	for _, r := range s {
		if r < '0' || r > '9' {
			return false
		}
	}
	return len(s) > 0
}
