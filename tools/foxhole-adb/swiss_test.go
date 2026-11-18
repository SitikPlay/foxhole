package main

import (
	"testing"
)

func TestResolveKeyCode(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"home", "3"},
		{"HOME", "3"},
		{"back", "4"},
		{"power", "26"},
		{"volup", "24"},
		{"voldown", "25"},
		{"menu", "82"},
		{"enter", "66"},
		{"space", "62"},
		{"tab", "61"},
		{"123", "123"},
		{"custom_key", "custom_key"},
	}

	for _, tc := range tests {
		got := resolveKeyCode(tc.input)
		if got != tc.expected {
			t.Errorf("resolveKeyCode(%q) = %q; want %q", tc.input, got, tc.expected)
		}
	}
}

func TestSwissCommandArgsValidation(t *testing.T) {
	if code := cmdAsk(nil); code != 2 {
		t.Errorf("cmdAsk(nil) code = %d; want 2", code)
	}
	if code := cmdKey("adb", nil); code != 2 {
		t.Errorf("cmdKey(nil) code = %d; want 2", code)
	}
	if code := cmdType("adb", nil); code != 2 {
		t.Errorf("cmdType(nil) code = %d; want 2", code)
	}
	if code := cmdApk("adb", nil); code != 2 {
		t.Errorf("cmdApk(nil) code = %d; want 2", code)
	}
	if code := cmdDns("adb", []string{"invalid_provider"}); code != 2 {
		t.Errorf("cmdDns('invalid_provider') code = %d; want 2", code)
	}
	if code := cmdOpen("adb", nil); code != 2 {
		t.Errorf("cmdOpen(nil) code = %d; want 2", code)
	}
	if code := cmdSearch("adb", nil); code != 2 {
		t.Errorf("cmdSearch(nil) code = %d; want 2", code)
	}
	if code := cmdClear("adb", nil); code != 2 {
		t.Errorf("cmdClear(nil) code = %d; want 2", code)
	}
	if code := cmdClear("adb", []string{"invalid;pkg"}); code != 2 {
		t.Errorf("cmdClear(invalid) code = %d; want 2", code)
	}
	if code := cmdVolume("adb", nil); code != 2 {
		t.Errorf("cmdVolume(nil) code = %d; want 2", code)
	}
	if code := cmdVolume("adb", []string{"999"}); code != 2 {
		t.Errorf("cmdVolume(999) code = %d; want 2", code)
	}
	if code := cmdDarkmode("adb", []string{"invalid"}); code != 2 {
		t.Errorf("cmdDarkmode(invalid) code = %d; want 2", code)
	}
	if code := cmdStayAwake("adb", []string{"invalid"}); code != 2 {
		t.Errorf("cmdStayAwake(invalid) code = %d; want 2", code)
	}
	if code := cmdWifi("adb", []string{"invalid"}); code != 2 {
		t.Errorf("cmdWifi(invalid) code = %d; want 2", code)
	}
}
