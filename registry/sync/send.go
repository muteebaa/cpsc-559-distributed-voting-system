package sync

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"math/rand/v2"
	"net/http"
	"net/netip"
	"time"

	"github.com/muteebaa/cpsc-559-distributed-voting-system/clock"
	"github.com/muteebaa/cpsc-559-distributed-voting-system/utils"
)

type Peer struct {
	Id       clock.Id       `json:"id"`
	Host     netip.AddrPort `json:"host"`
	Alive    bool           `json:"alive"`
	LastSeen time.Time      `json:"lastSeen"`
}

var Peers = map[clock.Id]Peer{}

// TODO: Use hash of IP + Port
func addPeerToList(p *Peer) {
	for {
		p.Id = clock.Id(rand.Int())
		if _, ok := Peers[p.Id]; ok {
			Peers[p.Id] = *p
		}
	}
}

func Replicate(msg PeerMsg) {
	ok := true
	for _, v := range Peers {
		if !v.Alive {
			ok := revive(v)
			if !ok {
				continue
			}

			v.Alive = true
			v.LastSeen = time.Now()
		}

		err := handleSend(v, msg)
		if err != nil {
			ok = false
			slog.Warn(err.Error())
			continue
		}

		v.LastSeen = time.Now()
	}

	if ok {
		sessionStore.Tracker.Unmark(msg.Session.Id)
	}
}

func revive(p Peer) bool {
	path := "/ping"
	url := utils.CreateUrl(p.Host, path)
	if _, err := http.Get(url); err != nil {
		return false
	}

	return true
}

func handleSend(p Peer, msg PeerMsg) error {
	emsg, err := json.Marshal(msg)
	if err != nil {
		return err
	}

	path := fmt.Sprintf("/peers/sessions/%s", msg.Session.Id)
	url := utils.CreateUrl(p.Host, path)

	for try := 1; try <= 3; try++ {
		_, err := http.Post(url, "application/json", bytes.NewBuffer(emsg))
		if err != nil {
			slog.Warn(err.Error())
			continue
		}

		return nil
	}

	return errors.New("Could not replicate session write")
}

func SetPeers(p map[clock.Id]Peer) {
	Peers = p
}
