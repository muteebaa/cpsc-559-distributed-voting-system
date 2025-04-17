package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"maps"
	"net/http"
	"net/netip"
	"slices"

	"github.com/muteebaa/cpsc-559-distributed-voting-system/clock"
	"github.com/muteebaa/cpsc-559-distributed-voting-system/sync"
	"github.com/muteebaa/cpsc-559-distributed-voting-system/utils"
)

const path = "/peers"

func join(addr netip.AddrPort) error {
	url := utils.CreateUrl(addr, path)
	resp, err := http.Get(url)
	if err != nil {
		return err
	}

	var peers map[clock.Id]sync.Peer
	dec := json.NewDecoder(resp.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&peers); err != nil {
		return err
	}

	sync.SetPeers(peers)

	return nil
}

func sendSelf(host netip.AddrPort) (clock.Id, error) {
	self := sync.Peer{Host: host}
	msg, err := json.Marshal(self)
	if err != nil {
		return 0, errors.New("Could not encode payload")
	}

	left := slices.Collect(maps.Values(sync.Peers))
	for _, v := range left {
		url := utils.CreateUrl(v.Host, path)

		for try := 1; try <= 3; try++ {
			id, err := handleSend(url, msg)
			if err != nil {
				slog.Debug(err.Error())
				continue
			}

			return id, nil
		}

		slog.Warn(fmt.Sprintf("Could not network join via %s after 3 failed attempts", url))
	}

	return 0, errors.New("Could not join network")
}

func handleSend(url string, msg []byte) (clock.Id, error) {
	resp, err := http.Post(url, "application/json", bytes.NewBuffer(msg))
	if err != nil {
		return 0, err
	}

	code := resp.StatusCode
	if code != http.StatusOK {
		return 0, fmt.Errorf("Bad status code %d received", code)
	}

	var id clock.Id
	dec := json.NewDecoder(resp.Body)
	dec.DisallowUnknownFields()
	if err = dec.Decode(&id); err != nil {
		return 0, err
	}

	return id, nil
}
