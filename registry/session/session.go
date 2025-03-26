package session

import (
	"encoding/json"
	"errors"
	"math/rand"
	"net"
	"regexp"
)

// Represents the information for an ongoing session. Can be encoded to/from
// JSON
type Session struct {
	Id      Id       `json:"id"`      // 6 character upper-alphanumeric string
	Host    net.IP   `json:"host"`    // IP of the leader node
	Port    int      `json:"port"`    // IP the leader node is broadcasting on
	Options []string `json:"options"` // Options for the ongoing election
}

type Id string

// Validating regex for a session ID
const (
	SessIdRegex   = `^[A-Z0-9]{6}$`
	SessFileRegex = `^[A-Z0-9]{6}\.json$`
)

func genId() Id {
	const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	id := make([]byte, 6)
	for i := range id {
		id[i] = chars[rand.Intn(len(chars))]
	}

	return Id(id)
}

// Validates that the [Session] ID is a 6 character upper-alphanumeric string
// when parsing a session object from JSON. Returns an error if it does not
func (id *Id) UnmarshalJSON(data []byte) error {
	var rawId string
	if err := json.Unmarshal(data, &rawId); err != nil {
		return err
	}

	validRegex := regexp.MustCompile(SessIdRegex)
	if !validRegex.MatchString(rawId) {
		return errors.New("Invalid Session ID: Must be uppercase alphanumeric")
	}

	*id = Id(rawId)
	return nil
}

// Updates s1 with the (possibly empty) IP and port of s2. Any other attributes
// are simply ignored if present. The IP and port are also ignored if empty
func (s1 *Session) Update(s2 *Session) {
	if s1.Host != nil {
		s2.Host = s1.Host
	}

	if s1.Port != 0 {
		s2.Port = s1.Port
	}
}
