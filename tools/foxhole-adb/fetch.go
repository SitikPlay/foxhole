package main

import (
	"archive/zip"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

func fetchURL() string {
	switch runtime.GOOS {
	case "windows":
		return "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
	case "linux":
		return "https://dl.google.com/android/repository/platform-tools-latest-linux.zip"
	case "darwin":
		return "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
	}
	return ""
}

func cmdFetch(args []string) int {
	url := fetchURL()
	if url == "" {
		fmt.Fprintln(os.Stderr, "fetch "+fetchNoOs())
		return 1
	}
	dest, err := defaultToolsDir()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	parent := filepath.Dir(dest)
	if err := os.MkdirAll(parent, 0o755); err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	fmt.Println("fetch:", url)
	fmt.Println("dest:", dest)
	if err := downloadAndUnpack(url, parent); err != nil {
		fmt.Fprintf(os.Stderr, msg("fetchFail")+"\n", err)
		return 1
	}
	adb := filepath.Join(dest, adbNameForOS())
	out, _, _ := runAdb(adb, []string{"version"})
	first := ""
	for _, l := range strings.Split(out, "\n") {
		if strings.TrimSpace(l) != "" {
			first = strings.TrimSpace(l)
			break
		}
	}
	fmt.Printf(msg("fetchDone")+"\n", dest, first)
	return 0
}

func fetchNoOs() string {
	if lang == "en" {
		return "unsupported OS (" + runtime.GOOS + "): download platform-tools manually"
	}
	return "неподдерживаемая ОС (" + runtime.GOOS + "): скачайте platform-tools вручную"
}

func downloadAndUnpack(url, destParent string) error {
	client := &http.Client{Timeout: 15 * time.Minute}
	resp, err := client.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	tmp, err := os.CreateTemp("", "foxhole-platform-tools-*.zip")
	if err != nil {
		return err
	}
	tmpPath := tmp.Name()
	defer os.Remove(tmpPath)
	if _, err := io.Copy(tmp, resp.Body); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return unpackZip(tmpPath, destParent)
}

func unpackZip(zipPath, destParent string) error {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer r.Close()
	for _, f := range r.File {
		name := filepath.FromSlash(f.Name)
		if strings.Contains(name, "..") {
			continue
		}
		target := filepath.Join(destParent, name)
		if f.FileInfo().IsDir() {
			os.MkdirAll(target, 0o755)
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return err
		}
		src, err := f.Open()
		if err != nil {
			return err
		}
		dst, err := os.OpenFile(target, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o644)
		if err != nil {
			src.Close()
			return err
		}
		_, err = io.Copy(dst, src)
		src.Close()
		dst.Close()
		if err != nil {
			return err
		}
		if runtime.GOOS != "windows" && strings.HasSuffix(name, "adb") {
			os.Chmod(target, 0o755)
		}
	}
	return nil
}
