package main

import (
	"archive/zip"
	"bytes"
	"os"
	"path/filepath"
	"runtime"
	"testing"
)

func TestExtractGlobals(t *testing.T) {
	gAdbFlag = ""
	lang = "ru"
	rest, err := extractGlobals([]string{"--adb", "C:/tools/adb.exe", "--lang", "en", "doctor"})
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if gAdbFlag != "C:/tools/adb.exe" {
		t.Fatalf("--adb не считан: %q", gAdbFlag)
	}
	if lang != "en" {
		t.Fatalf("lang: %q", lang)
	}
	if len(rest) != 1 || rest[0] != "doctor" {
		t.Fatalf("rest: %v", rest)
	}
}

func TestExtractGlobalsEqualsForm(t *testing.T) {
	gAdbFlag = ""
	lang = "ru"
	rest, err := extractGlobals([]string{"--adb=/x/y", "connect"})
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if gAdbFlag != "/x/y" {
		t.Fatalf("--adb=: %q", gAdbFlag)
	}
	if len(rest) != 1 || rest[0] != "connect" {
		t.Fatalf("rest: %v", rest)
	}
}

func TestExtractGlobalsMissingValue(t *testing.T) {
	if _, err := extractGlobals([]string{"--adb"}); err == nil {
		t.Fatal("ожидалась ошибка на --adb без значения")
	}
}

func TestParseHostPort(t *testing.T) {
	h, p, pr := parseHostPort("192.168.1.5:38815", true)
	if pr != 0 || h != "192.168.1.5" || p != 38815 {
		t.Fatalf("got %q %d pr=%d", h, p, pr)
	}
	h, p, pr = parseHostPort("phone.lan", false)
	if pr != 0 || h != "phone.lan" || p != 5555 {
		t.Fatalf("default port: got %q %d pr=%d", h, p, pr)
	}
	if _, _, pr := parseHostPort("phone.lan", true); pr != 1 {
		t.Fatal("обязательный порт не проверен")
	}
	if _, _, pr := parseHostPort("192.168.1.5:80", true); pr != 2 {
		t.Fatal("порт 80 должен отбрасываться")
	}
	if _, _, pr := parseHostPort("192.168.1.5:65536", true); pr != 2 {
		t.Fatal("порт 65536 должен отбрасываться")
	}
	if _, _, pr := parseHostPort("bad host:1234", true); pr != 1 {
		t.Fatal("пробел в хосте должен отбрасываться")
	}
	if _, _, pr := parseHostPort(";reboot:1234", true); pr != 1 {
		t.Fatal("мета-символы в хосте должны отбрасываться")
	}
}

func TestParseHostPortIPv6Colons(t *testing.T) {
	h, p, pr := parseHostPort("2001:db8::1:40123", true)
	if pr != 0 || h != "2001:db8::1" || p != 40123 {
		t.Fatalf("got %q %d pr=%d", h, p, pr)
	}
}

func TestIsDigits(t *testing.T) {
	if !isDigits("123456") {
		t.Fatal("6 цифр — валидно")
	}
	if isDigits("") || isDigits("12345a") || isDigits("12 456") {
		t.Fatal("пустое/буквы/пробел — невалидно")
	}
}

func TestFetchURLOfCurrentOS(t *testing.T) {
	url := fetchURL()
	switch runtime.GOOS {
	case "windows", "linux", "darwin":
		want := "https://dl.google.com/android/repository/platform-tools-latest-" + map[string]string{
			"windows": "windows", "linux": "linux", "darwin": "darwin",
		}[runtime.GOOS] + ".zip"
		if url != want {
			t.Fatalf("got %q want %q", url, want)
		}
	default:
		if url != "" {
			t.Fatalf("неожиданный url: %q", url)
		}
	}
}

func TestRebootModesExact(t *testing.T) {
	for _, m := range []string{"recovery", "bootloader", "fastboot", "sideload"} {
		if !rebootModes[m] {
			t.Fatalf("режим %s должен быть разрешён", m)
		}
	}
	for _, m := range []string{"system", "user", "poweroff", ""} {
		if rebootModes[m] {
			t.Fatalf("режим %s должен быть запрещён", m)
		}
	}
}

func TestLocateAdbExplicit(t *testing.T) {
	dir := t.TempDir()
	p := filepath.Join(dir, "adbtest")
	if err := os.WriteFile(p, []byte("stub"), 0o755); err != nil {
		t.Fatal(err)
	}
	got, ok := locateAdb(p)
	if !ok || got != p {
		t.Fatalf("got %q ok=%v", got, ok)
	}
	_, pathErr := findInPath(adbNameForOS())
	defDir, _ := defaultToolsDir()
	defHit := statFile(filepath.Join(defDir, adbNameForOS()))
	if pathErr == nil || defHit {
		t.Skip("adb доступен через PATH или каталог по умолчанию на этой машине")
	}
	if _, ok = locateAdb(filepath.Join(dir, "missing")); ok {
		t.Fatal("несуществующий adb не должен находиться")
	}
}

func statFile(p string) bool {
	st, err := os.Stat(p)
	return err == nil && !st.IsDir()
}

func TestUnpackZipRejectsPathEscape(t *testing.T) {
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	w, err := zw.Create("../evil.txt")
	if err != nil {
		t.Fatal(err)
	}
	w.Write([]byte("nope"))
	w2, err := zw.Create("platform-tools/inside.txt")
	if err != nil {
		t.Fatal(err)
	}
	w2.Write([]byte("ok"))
	zw.Close()

	outer := t.TempDir()
	target := filepath.Join(outer, "platform-tools")
	if err := os.MkdirAll(target, 0o755); err != nil {
		t.Fatal(err)
	}
	tmp := filepath.Join(outer, "t.zip")
	if err := os.WriteFile(tmp, buf.Bytes(), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := unpackZip(tmp, outer); err != nil {
		t.Fatalf("unpack: %v", err)
	}
	if _, err := os.Stat(filepath.Join(outer, "evil.txt")); err == nil {
		t.Fatal("zip-slip: файл ../evil.txt вылез за границы")
	}
	if b, err := os.ReadFile(filepath.Join(target, "inside.txt")); err != nil || string(b) != "ok" {
		t.Fatalf("нормальный файл не распакован: %v", err)
	}
}

func TestDefaultToolsDirNonEmpty(t *testing.T) {
	d, err := defaultToolsDir()
	if err != nil || d == "" {
		t.Fatalf("dir: %q err: %v", d, err)
	}
}

func TestValidatePkg(t *testing.T) {
	good := []string{"com.sitikplay.foxhole", "abc", "a-b_c.d", "X1.2"}
	for _, p := range good {
		if !validatePkg(p) {
			t.Fatalf("валидный пакет отброшен: %q", p)
		}
	}
	bad := []string{"", "ab", "co m", "com;x", "com'reboot", "пакет", "a\tb", "a$b"}
	for _, p := range bad {
		if validatePkg(p) {
			t.Fatalf("плохой пакет пропущен: %q", p)
		}
	}
}

func TestNamespacesExact(t *testing.T) {
	for _, ns := range []string{"global", "secure", "system"} {
		if !namespaces[ns] {
			t.Fatalf("ns %s должен быть разрешён", ns)
		}
	}
	for _, ns := range []string{"", "Global", "user", "diag"} {
		if namespaces[ns] {
			t.Fatalf("ns %s должен быть запрещён", ns)
		}
	}
}

func TestValidateWmSizeAndDensity(t *testing.T) {
	if !validateWmSize("1080x2400") || !validateWmSize("720x1520") {
		t.Fatal("валидный размер отброшен")
	}
	for _, s := range []string{"1080X2400", "x2400", "1080x", "0x2400", "1080 x2400", "1080x-4", "garbage"} {
		if validateWmSize(s) {
			t.Fatalf("плохой размер пропущен: %q", s)
		}
	}
	if !validateDensity("420") || !validateDensity("160") {
		t.Fatal("валидная плотность отброшена")
	}
	for _, d := range []string{"0", "99", "-420", "abc", "123456", "42x"} {
		if validateDensity(d) {
			t.Fatalf("плохая плотность пропущена: %q", d)
		}
	}
}

func TestValidatePeer(t *testing.T) {
	good := []string{"tcp:8080", "tcp:5555", "localabstract:logd", "localfilesystem:/data/x.sock", "dev:us", "jdwp:1234"}
	for _, p := range good {
		if !validatePeer(p) {
			t.Fatalf("валидный peer отброшен: %q", p)
		}
	}
	bad := []string{"", "tcp:", "tcp:0", "tcp:-1", "tcp:70000", "tcp:abc", "unix:x", "tcp:80 tcp:443", "localabstract:a b"}
	for _, p := range bad {
		if validatePeer(p) {
			t.Fatalf("плохой peer пропущен: %q", p)
		}
	}
}

func TestQQuoting(t *testing.T) {
	if got := q("1"); got != "'1'" {
		t.Fatalf("q simple: %q", got)
	}
	if got := q("1;reboot"); got != `'1;reboot'` {
		t.Fatalf("q semicolon: %q", got)
	}
	if got := q("it's"); got != `'it'\''s'` {
		t.Fatalf("q apostrophe: %q", got)
	}
	if got := q("a b"); got != "'a b'" {
		t.Fatalf("q space: %q", got)
	}
	if got := q("'"); got != `''\'''` {
		t.Fatalf("q lone apostrophe: %q", got)
	}
}

func TestGuardYesFlagAndPrompt(t *testing.T) {
	rest, ok := guardYes("wm size", []string{"1080x2400", "--yes"})
	if !ok || len(rest) != 1 || rest[0] != "1080x2400" {
		t.Fatalf("rest=%v ok=%v", rest, ok)
	}
}

func TestExtValidatorsNoInjectionReach(t *testing.T) {
	if validatePkg("a;reboot") || validatePeer("tcp:1;reboot") || validateWmSize("1;reboot") {
		t.Fatal("инъекция прошла валидатор")
	}
}
