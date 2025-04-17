package session

import (
	"errors"
	"log/slog"
	"maps"
	"slices"
)

var (
	ErrInvalidSession    = errors.New("Invalid session metadata passed")
	ErrUpdateNonExistent = errors.New("Session to be updated does not exist")
)

type SessionStorer interface {
	List() []Id
	Get(Id) Session
	GetAll() []*Session
	Add(*Session) error
	Update(*Session) error
}

type SessionStore struct {
	Sessions map[Id]*Session
}

func New() *SessionStore {
	return &SessionStore{
		Sessions: map[Id]*Session{},
	}
}

func (s *SessionStore) List() []Id {
	return slices.Collect(maps.Keys(s.Sessions))
}

func (s *SessionStore) Get(id Id) Session {
	return *s.Sessions[id]
}

func (s *SessionStore) GetAll() []*Session {
	return slices.Collect(maps.Values(s.Sessions))
}

func (s *SessionStore) Add(sess *Session) error {
	if sess.Host == nil || sess.Port == 0 || sess.Options == nil {
		slog.Error("Invalid session metadata passed", "session", s)
		return ErrInvalidSession
	}
	sess.Id = genId()

	s.Sessions[sess.Id] = sess

	return nil
}

func (s *SessionStore) Update(sess *Session) error {
	v, ok := s.Sessions[sess.Id]
	if !ok {
		slog.Error("Requested update for non-existent session", "session", sess)
		return ErrUpdateNonExistent
	}

	v.Update(sess)
	return nil
}
