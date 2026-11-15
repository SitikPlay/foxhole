package main

import (
	"errors"
	"strings"
	"testing"
)

// А39: при ненулевом коде выхода err бывает nil — раньше печаталось
// "%!s(<nil>)". Теперь для этого случая отдельное сообщение с кодом.
func TestRunFailTextNilError(t *testing.T) {
	lang = "en"
	s := runFailText("pair", 1, nil)
	if strings.Contains(s, "%!") {
		t.Fatalf("nil error leaks %%!s: %q", s)
	}
	if !strings.Contains(s, "pair") || !strings.Contains(s, "1") {
		t.Fatalf("cmd/code lost: %q", s)
	}
	if !strings.Contains(s, "exited with code") {
		t.Fatalf("expected exit-code message: %q", s)
	}
}

func TestRunFailTextRealError(t *testing.T) {
	lang = "en"
	s := runFailText("install", -1, errors.New("spawn failed"))
	if !strings.Contains(s, "install") || !strings.Contains(s, "spawn failed") {
		t.Fatalf("real error lost: %q", s)
	}
	if strings.Contains(s, "exited with code") {
		t.Fatalf("exit-code message used for real error: %q", s)
	}
}

// А41: ошибки спаривания и таймауты переводятся в понятный подвод, а не
// печатаются сырым выводом adb.
func TestExplainConnPairFailures(t *testing.T) {
	lang = "en"
	out := explainConn("Failed to pair to 192.168.1.5:37013: incorrect code")
	if !strings.Contains(out, "Pairing failed") {
		t.Fatalf("pair failure not translated: %q", out)
	}
	if !strings.Contains(out, "incorrect code") {
		t.Fatalf("failure details lost: %q", out)
	}
	out = explainConn("* daemon not running; starting now at tcp:5037\nfailed to connect to 192.168.1.5:5555: timed out")
	if !strings.Contains(out, "could not connect") {
		t.Fatalf("timeout not translated: %q", out)
	}
	if strings.Contains(out, "daemon not running") {
		t.Fatalf("daemon noise leaked into result: %q", out)
	}
}

func TestExplainConnSuccessesIntact(t *testing.T) {
	lang = "en"
	if out := explainConn("Successfully paired to 192.168.1.5:37013"); !strings.Contains(out, "Paired with 192.168.1.5:37013") {
		t.Fatalf("pair ok broken: %q", out)
	}
	if out := explainConn("connected to 192.168.1.5:5555"); !strings.Contains(out, "Connected: 192.168.1.5:5555") {
		t.Fatalf("connect ok broken: %q", out)
	}
	if out := explainConn("already connected to 192.168.1.5:5555"); !strings.Contains(out, "Already connected: 192.168.1.5:5555") {
		t.Fatalf("connect already broken: %q", out)
	}
}

// А40: пустой вывод и stderr adb не теряются, а попадают в текст ошибки.
func TestScreenshotFailErr(t *testing.T) {
	lang = "en"
	if err := screenshotFailErr(nil, ""); err != nil {
		t.Fatalf("success must not be error: %v", err)
	}
	err := screenshotFailErr(errors.New("exit status 1"), "error: device offline")
	if err == nil {
		t.Fatal("real error swallowed")
	}
	text := err.Error()
	if !strings.Contains(text, "exit status 1") || !strings.Contains(text, "device offline") {
		t.Fatalf("stderr lost: %q", text)
	}
	err = screenshotFailErr(nil, "* daemon started successfully\n")
	if err == nil || !strings.Contains(err.Error(), "empty output") {
		t.Fatalf("empty output not reported: %v", err)
	}
}
