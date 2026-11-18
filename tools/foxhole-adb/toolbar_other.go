//go:build !windows

package main

import "fmt"

func launchToolbar(ab string, autoCloseChan <-chan struct{}) {
	fmt.Println("Горячие клавиши управления экраном (scrcpy):")
	fmt.Println("  MOD+p       — Питание (вкл/выкл экран)")
	fmt.Println("  MOD+u       — Громкость +")
	fmt.Println("  MOD+d       — Громкость -")
	fmt.Println("  MOD+b       — Назад (или правая кнопка мыши)")
	fmt.Println("  MOD+h       — Домой (или средняя кнопка мыши)")
	fmt.Println("  MOD+s       — Недавние приложения")
	fmt.Println("  (MOD — это левый Alt на клавиатуре)")
	if autoCloseChan != nil {
		<-autoCloseChan
	}
}
