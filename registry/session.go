package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"math/rand"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/httplog/v2"
)

// Represents the information for an ongoing session. Can be marshalled to/from
// JSON
type Session struct {
	Id      SessId   `json:"id"`      // 6 character upper-alphanumeric string
	Host    net.IP   `json:"host"`    // IP of the leader node
	Port    int      `json:"port"`    // IP the leader node is broadcasting on
	Options []string `json:"options"` // Options for the ongoing election
}

type SessId string

// Validating regex for a session ID
const sessIdRegex = `^[A-Z0-9]{6}$`

// TODO: Find out if session ids need to more random
func genId() SessId {
	const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	id := make([]byte, 6)
	for i := range id {
		id[i] = chars[rand.Intn(len(chars))]
	}

	return SessId(id)
}

// Validates that the [Session] ID is a 6 character upper-alphanumeric string
// when parsing a session object from JSON. Returns an error if it does not
func (id *SessId) UnmarshalJSON(data []byte) error {
	var rawId string
	if err := json.Unmarshal(data, &rawId); err != nil {
		return err
	}

	validRegex := regexp.MustCompile(sessIdRegex)
	if !validRegex.MatchString(rawId) {
		return errors.New("Invalid Session ID: Must be uppercase alphanumeric")
	}

	*id = SessId(rawId)
	return nil
}

// Returns the [Session] information for a single session ID contained within
// the URL
func getSingleSession(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())
	sessId := chi.URLParam(r, "sess")
	_, err := os.Stat(fmt.Sprintf("%s/%s.json", sessDir, sessId))
	if err != nil && errors.Is(err, fs.ErrNotExist) {
		logger.Debug(fmt.Sprintf("Session %s could not be located", sessId))
		http.Error(w, "File not found", http.StatusNotFound)
		return
	}

	d, err := os.ReadFile(fmt.Sprintf("%s/%s.json", sessDir, sessId))
	if err != nil {
		logger.Error(fmt.Sprintf("Session %s could not be read from", sessId))
		http.Error(w, "Session file could not be read", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	w.Write(d)
}

// Returns the [Session] IDs for every ongoing session in a JSON object
//
// Example Response:
//
//	{"session":["ABC123","ABC124"]}
func getAllSessionInfo(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())
	sessions, err := findSessionFiles(sessDir)
	if err != nil {
		logger.Error("Failed to read session directory info")
		http.Error(w, "Failed to retrieve sessions", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)

	resp := map[string][]string{"sessions": sessions}
	json.NewEncoder(w).Encode(resp)
}

// Returns the complete JSON-encoded information on every ongoing [Session] in a
// JSON array
func getAllSessions(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())
	files, err := filepath.Glob(sessDir + "/*.json")
	if err != nil {
		logger.Error("Failed to read session directory info")
		http.Error(w, "Failed to read sessions", http.StatusInternalServerError)
		return
	}

	sessions := make([]Session, 0)
	for _, file := range files {
		data, err := os.Open(file)
		if err != nil {
			// FIXME: Handle error
			logger.Warn(fmt.Sprintf(`Session file "%s" could not be read`, file))
			continue
		}
		defer data.Close()

		dec := json.NewDecoder(data)
		var session Session
		if err := dec.Decode(&session); err != nil {
			// FIXME: Handle error
			logger.Warn(fmt.Sprintf(`Session file "%s" could not be decoded`, file))
			continue
		}

		sessions = append(sessions, session)
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(sessions)
}

// Find every ongoing session recorded within the database
func findSessionFiles(dir string) ([]string, error) {
	files := make([]string, 0)
	f, err := os.Open(dir)
	if err != nil {
		return files, err
	}
	defer f.Close()

	fileInfo, err := f.Readdirnames(0)
	if err != nil {
		return files, err
	}

	validRegex := strings.TrimSuffix(sessIdRegex, "$") + `\.json$`
	idValidator := regexp.MustCompile(validRegex)
	for _, file := range fileInfo {
		if idValidator.MatchString(file) {
			files = append(files, strings.TrimSuffix(file, ".json"))
		}
	}

	return files, nil
}

// Assign the requested session an ID and store it in the database. Expects a
// JSON-encoded [Session] (the id is ignored if present) as a request and
// returns the assigned ID as a JSON string as a response
//
// Example Session Request:
//
//	[{"Host":"hello","Ip":"127.0.0.1","Options":[]}]
//
// Example Response:
//
//	"ABC123"
func addSession(w http.ResponseWriter, r *http.Request) {
	logger := httplog.LogEntry(r.Context())
	var s Session
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&s); err != nil {
		logger.Error("Session metadata could not be decoded")
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	if s.Host == nil || s.Port == 0 || s.Options == nil {
		logger.Error("Invalid session metadata received")
		http.Error(w, "Missing required fields", http.StatusBadRequest)
		return
	}

	newId := genId()
	s.Id = newId

	filepath := fmt.Sprintf("%s/%s.json", sessDir, s.Id)
	d, err := json.Marshal(s)
	if err != nil {
		logger.Error("Session metadata could not be encoded to JSON")
		http.Error(w, "Failed to marshal JSON", http.StatusInternalServerError)
		return
	}

	if err = os.WriteFile(filepath, d, 0o644); err != nil {
		logger.Error(fmt.Sprintf(`Session file "%s" could not be written to`, filepath))
		http.Error(w, "Failed to write file", http.StatusInternalServerError)
		return
	}

	logger.Debug("Saved session: " + fmt.Sprintf("%#v", s))
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)

	json.NewEncoder(w).Encode(newId)
}
