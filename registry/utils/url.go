package utils

import (
	"fmt"
	"net/netip"
)

func CreateUrl(addr netip.AddrPort, path string) string {
	host := addr.Addr().String()
	port := addr.Port()

	if addr.Addr().Is6() {
		return fmt.Sprintf("http://[%s]:%d%s", host, port, path)
	} else {
		return fmt.Sprintf("http://%s:%d%s", host, port, path)
	}
}
