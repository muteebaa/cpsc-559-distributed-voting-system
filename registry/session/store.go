package session

import (
	"errors"
	"log/slog"
	"maps"
	"slices"

	"github.com/muteebaa/cpsc-559-distributed-voting-system/clock"
)

var (
	ErrInvalidSession    = errors.New("Invalid session metadata passed")
	ErrUpdateNonExistent = errors.New("Session to be updated does not exist")
)

type State map[Id]clock.VClock

type SessionStore struct {
	Pid      clock.Id
	Sessions map[Id]*Session
	State    State
	Tracker  *DirtyTracker
}

func newState() State {
	return map[Id]clock.VClock{}
}

func New(pid clock.Id) *SessionStore {
	return &SessionStore{
		Pid:      pid,
		Sessions: map[Id]*Session{},
		State:    newState(),
		Tracker:  NewDirtyTracker(),
	}
}

func (s *SessionStore) List() []Id {
	return slices.Collect(maps.Keys(s.Sessions))
}

func (s *SessionStore) Get(id Id) *Session {
	return s.Sessions[id]
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

func (s *SessionStore) update(sess *Session) error {
	v, ok := s.Sessions[sess.Id]
	if !ok {
		slog.Error("Requested update for non-existent session", "session", sess)
		return ErrUpdateNonExistent
	}

	v.Update(sess)
	return nil
}

func (s *SessionStore) SelfUpdate(sess *Session) error {
	return s.PeerUpdate(s.Pid, sess)
}

func (s *SessionStore) PeerUpdate(pId clock.Id, sess *Session) error {
	if err := s.update(sess); err != nil {
		return err
	}

	s.State[sess.Id].Incr(pId)
	s.Tracker.Mark(sess.Id)

	return nil
}
