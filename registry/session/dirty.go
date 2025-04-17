package session

import (
	"maps"
	"slices"
	"sync"
)

type DirtyTracker struct {
	lock  sync.Mutex
	dirty map[Id]struct{}
}

func NewDirtyTracker() *DirtyTracker {
	return &DirtyTracker{dirty: map[Id]struct{}{}}
}

func (t *DirtyTracker) Mark(id Id) {
	t.lock.Lock()
	defer t.lock.Unlock()
	t.dirty[id] = struct{}{}
}

func (t *DirtyTracker) Unmark(id Id) {
	t.lock.Lock()
	defer t.lock.Unlock()
	delete(t.dirty, id)
}

func (t *DirtyTracker) Clear() []Id {
	t.lock.Lock()
	defer t.lock.Unlock()

	ids := slices.Collect(maps.Keys(t.dirty))
	t.dirty = map[Id]struct{}{}
	return ids
}
