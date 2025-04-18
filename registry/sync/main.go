package sync

import (
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"maps"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/httplog/v2"

	"github.com/muteebaa/cpsc-559-distributed-voting-system/clock"
	"github.com/muteebaa/cpsc-559-distributed-voting-system/session"
)

var (
	ErrInvalidMsg        = errors.New("Peer message could not be decoded")
	ErrSessionIncomplete = errors.New("Incomplete session metadata given")
	ErrVcIncomplete      = errors.New("Incomplete vector clock given")
	ErrPeerInvalid       = errors.New("Peer metadata could not be decoded")
	ErrPeerIncomplete    = errors.New("Incomplete peer information given")
	Self                 Peer
)

type PeerMsg struct {
	SenderId clock.Id        `json:"senderId"`
	Session  session.Session `json:"session"`
	Vc       clock.VClock    `json:"vc"`
}

var sessionStore *session.SessionStore

func Handler(s *session.SessionStore) http.Handler {
	sessionStore = s

	r := chi.NewRouter()

	r.Use(middleware.AllowContentType("application/json"))

	r.Get("/", getPeers)
	r.Post("/", addPeer)

	r.Put(fmt.Sprintf("/sessions/{sess:%s}", session.SessIdRegex), updateSession)

	return r
}

func getPeers(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())

	tmp := maps.Clone(Peers)
	tmp[sessionStore.Pid] = &Self

	// FIXME: Possible race on peers
	if err := json.NewEncoder(w).Encode(tmp); err != nil {
		logger.Error(err.Error())
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
}

func addPeer(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())

	var p Peer
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&p); err != nil {
		logger.Error(ErrPeerInvalid.Error())
		http.Error(w, ErrPeerInvalid.Error(), http.StatusBadRequest)
		return
	}

	if !p.Host.IsValid() {
		logger.Error(ErrPeerIncomplete.Error(), "peer", p)
		http.Error(w, ErrPeerIncomplete.Error(), http.StatusBadRequest)
		return
	}

	p.Alive = true
	p.LastSeen = time.Now()
	addPeerToList(&p)

	if err := json.NewEncoder(w).Encode(p.Id); err != nil {
		logger.Error(err.Error())
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
}

func updateSession(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())
	id := session.Id(chi.URLParam(r, "sess"))

	var m PeerMsg
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&m); err != nil {
		logger.Error(errors.Join(ErrInvalidMsg, err).Error())
		http.Error(w, errors.Join(ErrInvalidMsg, err).Error(), http.StatusBadRequest)
		return
	}

	if m.Session.Id == "" || m.Session.Id != id {
		m.Session.Id = id
	}

	if m.Session.Host == nil || m.Session.Port == 0 || m.Session.Options == nil {
		logger.Error(ErrSessionIncomplete.Error())
		http.Error(w, ErrSessionIncomplete.Error(), http.StatusBadRequest)
		return
	}

	// Since each node should at least track itself
	if len(m.Vc) == 0 {
		logger.Error(ErrVcIncomplete.Error())
		http.Error(w, ErrVcIncomplete.Error(), http.StatusBadRequest)
		return
	}

	peer := Peers[m.SenderId]
	peer.Alive = true
	peer.LastSeen = time.Now()

	if _, ok := m.Vc[m.SenderId]; !ok {
		logger.Error(ErrVcIncomplete.Error())
		http.Error(w, ErrVcIncomplete.Error(), http.StatusBadRequest)
		return
	}

	c, ok := sessionStore.State[m.Session.Id]
	if !ok {
		slog.Warn("vector clock expected but not found", "clock", c)
	}

	o := c.Compare(m.Vc)
	if !(o == clock.LESS || o == clock.INCOMPATIBLE) {
		w.WriteHeader(http.StatusOK)
		return
	}

	if sessionStore.Get(m.Session.Id) == nil {
		err := sessionStore.Add(&m.Session)
		if err != nil {
			var rCode int
			switch {
			case errors.Is(err, session.ErrInvalidSession):
				rCode = http.StatusBadRequest
			default:
				rCode = http.StatusInternalServerError
			}

			logger.Error(err.Error())
			http.Error(w, err.Error(), rCode)
			return
		}
	} else {
		err := sessionStore.PeerUpdate(m.SenderId, &m.Session)
		if err != nil {
			logger.Error(err.Error())
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}

	// Is incrementing necessary?
	// c.Incr(sessionStore.Pid)
	c.Update(m.Vc)

	w.WriteHeader(http.StatusOK)
}
