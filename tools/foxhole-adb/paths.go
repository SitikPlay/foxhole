package main

import (
	"errors"
	"os"
	"path/filepath"
	"runtime"
	"strings"
)

var gAdbFlag string

func extractGlobals(argv []string) ([]string, error) {
	rest := []string{}
	i := 0
	for i < len(argv) {
		a := argv[i]
		switch {
		case a == "--adb":
			if i+1 >= len(argv) {
				return nil, errors.New("--adb " + msg("needValue"))
			}
			gAdbFlag = argv[i+1]
			i += 2
		case strings.HasPrefix(a, "--adb="):
			gAdbFlag = strings.TrimPrefix(a, "--adb=")
			i++
		case a == "--lang":
			if i+1 >= len(argv) {
				return nil, errors.New("--lang " + msg("needValue"))
			}
			setLang(argv[i+1])
			i += 2
		case strings.HasPrefix(a, "--lang="):
			setLang(strings.TrimPrefix(a, "--lang="))
			i++
		default:
			rest = append(rest, a)
			i++
		}
	}
	return rest, nil
}

func setLang(v string) {
	if v == "en" || v == "en-US" {
		lang = "en"
	} else {
		lang = "ru"
	}
}

func detectLang(argv []string) string {
	if strings.HasPrefix(strings.ToLower(os.Getenv("FOXHOLE_ADB_LANG")), "en") {
		return "en"
	}
	return "ru"
}

func adbNameForOS() string {
	if runtime.GOOS == "windows" {
		return "adb.exe"
	}
	return "adb"
}

func locateAdb(explicit string) (string, bool) {
	candidates := []string{}
	if explicit != "" {
		candidates = append(candidates, explicit)
	}
	if exe, err := os.Executable(); err == nil {
		candidates = append(candidates,
			filepath.Join(filepath.Dir(exe), "platform-tools", adbNameForOS()))
	}
	if p, err := findInPath(adbNameForOS()); err == nil && p != "" {
		candidates = append(candidates, p)
	}
	if d, err := defaultToolsDir(); err == nil {
		candidates = append(candidates, filepath.Join(d, adbNameForOS()))
	}
	for _, c := range candidates {
		if st, err := os.Stat(c); err == nil && !st.IsDir() {
			return c, true
		}
	}
	return explicit, false
}

func findInPath(file string) (string, error) {
	dirs := filepath.SplitList(os.Getenv("PATH"))
	for _, d := range dirs {
		if d == "" {
			continue
		}
		p := filepath.Join(d, file)
		if st, err := os.Stat(p); err == nil && !st.IsDir() {
			return p, nil
		}
	}
	return "", os.ErrNotExist
}

func defaultToolsDir() (string, error) {
	if runtime.GOOS == "windows" {
		lad := os.Getenv("LOCALAPPDATA")
		if lad == "" {
			h, err := os.UserHomeDir()
			if err != nil {
				return "", err
			}
			lad = filepath.Join(h, "AppData", "Local")
		}
		return filepath.Join(lad, "Foxhole", "platform-tools"), nil
	}
	h, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(h, ".foxhole-adb", "platform-tools"), nil
}

func shift(s []string) (string, []string) {
	if len(s) == 0 {
		return "", nil
	}
	return s[0], s[1:]
}
