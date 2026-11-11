package main

import (
	"fmt"
	"net"
	"os"
	"strings"
	"time"
)

func cmdDoctor(adbPath string, args []string) int {
	fmt.Println(msg("doctorAdb"), adbPath)

	out, code, err := runAdb(adbPath, []string{"version"})
	if err != nil || code != 0 {
		fmt.Fprintf(os.Stderr, msg("doctorVersionFail")+"\n", err)
	} else {
		for _, l := range strings.Split(out, "\n") {
			l = strings.TrimSpace(l)
			if strings.HasPrefix(l, "Android Debug Bridge") {
				fmt.Println(" ", l)
				break
			}
		}
	}

	out, _, _ = runAdb(adbPath, []string{"devices"})
	fmt.Println(msg("doctorDevices"))
	saw := false
	for _, l := range strings.Split(out, "\n") {
		l = strings.TrimSpace(l)
		if l == "" || strings.HasPrefix(l, "List of devices") {
			continue
		}
		fmt.Println(" ", l)
		if strings.Contains(l, "\t") {
			saw = true
		}
	}
	if !saw {
		fmt.Println("  !", msg("doctorNoDevice"))
	}

	addr := net.JoinHostPort("127.0.0.1", "27183")
	conn, derr := net.DialTimeout("tcp", addr, 2*time.Second)
	if derr == nil {
		conn.Close()
		fmt.Println("  " + fmt.Sprintf(msg("doctorServer"), 27183) + " " + msg("doctorServerOpen"))
	} else {
		fmt.Println("  " + fmt.Sprintf(msg("doctorServer"), 27183) + " " + msg("doctorServerClosed"))
	}
	fmt.Println(msg("doctorOk"))
	return 0
}
