package node

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/httplog/v2"

	"github.com/muteebaa/cpsc-559-distributed-voting-system/session"
)

var sessionStore *session.SessionStore

func Handler(s *session.SessionStore) http.Handler {
	sessionStore = s

	r := chi.NewRouter()

	r.Use(middleware.AllowContentType("application/json"))

	r.Get(fmt.Sprintf("/{sess:%s}", session.SessIdRegex), getSingleSession)
	r.Patch(fmt.Sprintf("/{sess:%s}", session.SessIdRegex), updateSession)
	r.Get("/", getAllSessions)
	r.Post("/", addSession)
	r.Get("/all", getAllSessionInfo)

	return r
}

// Returns the [session.Session] information for a single session ID contained
// within the URL
func getSingleSession(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())
	id := session.Id(chi.URLParam(r, "sess"))

	s := sessionStore.Get(id)

	e := json.NewEncoder(w)
	if err := e.Encode(s); err != nil {
		logger.Error("Could not finish connection")
		http.Error(w, "Could not finish writing response", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
}

// Returns the [session.Session] IDs for every ongoing session in a JSON object
//
// Example Response:
//
//	{"session":["ABC123","ABC124"]}
func getAllSessionInfo(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())
	s := sessionStore.List()

	if s == nil {
		s = make([]session.Id, 0)
	}

	resp := map[string][]session.Id{"sessions": s}
	if err := json.NewEncoder(w).Encode(resp); err != nil {
		logger.Error("Could not finish writing response")
		http.Error(w, "Connection ended prematurely", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
}

// Returns the complete JSON-encoded information on every ongoing
// [session.Session] in a JSON array
func getAllSessions(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())
	// TODO: Handle nil arrays
	s := sessionStore.GetAll()

	if s == nil {
		s = make([]*session.Session, 0)
	}

	if err := json.NewEncoder(w).Encode(s); err != nil {
		logger.Error("Could not finish writing response")
		http.Error(w, "Connection ended prematurely", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
}

// Assign the requested session an ID and store it in the database. Expects a
// JSON-encoded [session.Session] (the id is ignored if present) as a request
// and returns the assigned ID as a JSON string as a response
//
// Example Session Request:
//
//	[{"host":"127.0.0.1","ip":8080,"options":[]}]
//
// Example Response:
//
//	"ABC123"
func addSession(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())
	var s session.Session
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&s); err != nil {
		logger.Error("Session metadata could not be decoded")
		http.Error(w, "Bad session metadata given", http.StatusBadRequest)
		return
	}

	// TODO: Merge w/ underlying error
	if s.Host == nil || s.Port == 0 || s.Options == nil {
		logger.Error("Incomplete session metadata given")
		http.Error(w, "Missing required fields", http.StatusBadRequest)
		return
	}

	err := sessionStore.Add(&s)
	if err != nil {
		switch {
		case errors.Is(err, session.ErrInvalidSession):
			http.Error(w, err.Error(), http.StatusBadRequest)
		default:
			logger.Error("Could not add session", "session", s)
			http.Error(w, "Failed to add session", http.StatusInternalServerError)
		}
		return
	}

	if err := json.NewEncoder(w).Encode(s.Id); err != nil {
		logger.Error("Could not finish writing response")
		http.Error(w, "Connection ended prematurely", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
}

// Updates the requested [session.Session] (ID in URL) with the new leader nodes
// (potentially partial) information. That is, only the Host and Port may be
// updated for a session
func updateSession(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())
	var s session.Session
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&s); err != nil {
		logger.Error("Session metadata could not be decoded")
		http.Error(w, "Bad session metadata given", http.StatusBadRequest)
		return
	}

	s.Id = session.Id(chi.URLParam(r, "sess"))
	err := sessionStore.SelfUpdate(&s)
	if err != nil {
		switch {
		case errors.Is(err, session.ErrUpdateNonExistent):
			http.Error(w, err.Error(), http.StatusBadRequest)
		default:
			logger.Error("Could not update session", "session", s)
			http.Error(w, "Could not update session", http.StatusInternalServerError)
		}
		return
	}

	w.WriteHeader(http.StatusOK)
}
