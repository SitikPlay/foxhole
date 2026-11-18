//go:build windows

package main

import (
	"syscall"
	"unsafe"
)

var (
	user32               = syscall.NewLazyDLL("user32.dll")
	kernel32             = syscall.NewLazyDLL("kernel32.dll")
	gdi32                = syscall.NewLazyDLL("gdi32.dll")
	procCreateWindowExW  = user32.NewProc("CreateWindowExW")
	procDefWindowProcW   = user32.NewProc("DefWindowProcW")
	procDestroyWindow    = user32.NewProc("DestroyWindow")
	procDispatchMessageW = user32.NewProc("DispatchMessageW")
	procGetMessageW      = user32.NewProc("GetMessageW")
	procPostQuitMessage  = user32.NewProc("PostQuitMessage")
	procRegisterClassExW = user32.NewProc("RegisterClassExW")
	procShowWindow       = user32.NewProc("ShowWindow")
	procTranslateMessage = user32.NewProc("TranslateMessage")
	procSendMessageW     = user32.NewProc("SendMessageW")
	procPostMessageW     = user32.NewProc("PostMessageW")
	procGetStockObject   = gdi32.NewProc("GetStockObject")
	procGetModuleHandleW = kernel32.NewProc("GetModuleHandleW")
)

const (
	WS_OVERLAPPED    = 0x00000000
	WS_CAPTION       = 0x00C00000
	WS_SYSMENU       = 0x00080000
	WS_VISIBLE       = 0x10000000
	WS_CHILD         = 0x40000000
	WS_EX_TOPMOST    = 0x00000008
	BS_PUSHBUTTON    = 0x00000000
	WM_DESTROY       = 0x0002
	WM_COMMAND       = 0x0111
	WM_SETFONT       = 0x0030
	WM_CLOSE         = 0x0010
	DEFAULT_GUI_FONT = 17
)

type WNDCLASSEXW struct {
	CbSize        uint32
	Style         uint32
	LpfnWndProc   uintptr
	CbClsExtra    int32
	CbWndExtra    int32
	HInstance     uintptr
	HIcon         uintptr
	HCursor       uintptr
	HbrBackground uintptr
	LpszMenuName  *uint16
	LpszClassName *uint16
	HIconSm       uintptr
}

type MSG struct {
	Hwnd    uintptr
	Message uint32
	WParam  uintptr
	LParam  uintptr
	Time    uint32
	Pt      struct{ X, Y int32 }
}

var (
	toolbarAdbPath string
	toolbarHwnd    uintptr
)

func toolbarWndProc(hwnd uintptr, msg uint32, wParam, lParam uintptr) uintptr {
	switch msg {
	case WM_COMMAND:
		id := int(wParam & 0xffff)
		go handleToolbarButton(id)
		return 0
	case WM_DESTROY:
		procPostQuitMessage.Call(0)
		return 0
	}
	r, _, _ := procDefWindowProcW.Call(hwnd, uintptr(msg), wParam, lParam)
	return r
}

func handleToolbarButton(id int) {
	if toolbarAdbPath == "" {
		return
	}
	switch id {
	case 101: // Питание
		runAdb(toolbarAdbPath, []string{"shell", "input", "keyevent", "26"})
	case 102: // Громкость +
		runAdb(toolbarAdbPath, []string{"shell", "input", "keyevent", "24"})
	case 103: // Громкость -
		runAdb(toolbarAdbPath, []string{"shell", "input", "keyevent", "25"})
	case 104: // Назад
		runAdb(toolbarAdbPath, []string{"shell", "input", "keyevent", "4"})
	case 105: // Домой
		runAdb(toolbarAdbPath, []string{"shell", "input", "keyevent", "3"})
	case 106: // Недавние
		runAdb(toolbarAdbPath, []string{"shell", "input", "keyevent", "187"})
	}
}

func createToolbarButton(parent uintptr, text string, id, x, y, w, h int, font uintptr) {
	textPtr, _ := syscall.UTF16PtrFromString(text)
	classPtr, _ := syscall.UTF16PtrFromString("BUTTON")
	btn, _, _ := procCreateWindowExW.Call(
		0,
		uintptr(unsafe.Pointer(classPtr)),
		uintptr(unsafe.Pointer(textPtr)),
		WS_VISIBLE|WS_CHILD|BS_PUSHBUTTON,
		uintptr(x), uintptr(y), uintptr(w), uintptr(h),
		parent,
		uintptr(id),
		0, 0,
	)
	if btn != 0 && font != 0 {
		procSendMessageW.Call(btn, WM_SETFONT, font, 1)
	}
}

func launchToolbar(ab string, autoCloseChan <-chan struct{}) {
	toolbarAdbPath = ab
	className, _ := syscall.UTF16PtrFromString("FoxholeToolbarWindow")
	hInstance, _, _ := procGetModuleHandleW.Call(0)

	var wc WNDCLASSEXW
	wc.CbSize = uint32(unsafe.Sizeof(wc))
	wc.Style = 0x0003
	wc.LpfnWndProc = syscall.NewCallback(toolbarWndProc)
	wc.HInstance = hInstance
	wc.HbrBackground = 6
	wc.LpszClassName = className

	procRegisterClassExW.Call(uintptr(unsafe.Pointer(&wc)))

	title, _ := syscall.UTF16PtrFromString("Foxhole")
	hwnd, _, _ := procCreateWindowExW.Call(
		WS_EX_TOPMOST,
		uintptr(unsafe.Pointer(className)),
		uintptr(unsafe.Pointer(title)),
		WS_OVERLAPPED|WS_CAPTION|WS_SYSMENU|WS_VISIBLE,
		50, 50, 165, 310,
		0, 0, hInstance, 0,
	)
	if hwnd == 0 {
		return
	}
	toolbarHwnd = hwnd

	font, _, _ := procGetStockObject.Call(DEFAULT_GUI_FONT)

	createToolbarButton(hwnd, "⏻ Питание", 101, 10, 10, 128, 34, font)
	createToolbarButton(hwnd, "🔊 Громкость +", 102, 10, 50, 128, 34, font)
	createToolbarButton(hwnd, "🔉 Громкость -", 103, 10, 90, 128, 34, font)
	createToolbarButton(hwnd, "◀ Назад", 104, 10, 135, 128, 34, font)
	createToolbarButton(hwnd, "⌂ Домой", 105, 10, 175, 128, 34, font)
	createToolbarButton(hwnd, "▢ Недавние", 106, 10, 215, 128, 34, font)

	if autoCloseChan != nil {
		go func() {
			<-autoCloseChan
			procPostMessageW.Call(hwnd, WM_CLOSE, 0, 0)
		}()
	}

	var m MSG
	for {
		ret, _, _ := procGetMessageW.Call(uintptr(unsafe.Pointer(&m)), 0, 0, 0)
		if int32(ret) <= 0 {
			break
		}
		procTranslateMessage.Call(uintptr(unsafe.Pointer(&m)))
		procDispatchMessageW.Call(uintptr(unsafe.Pointer(&m)))
	}
}
