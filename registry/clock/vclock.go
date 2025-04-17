package clock

import (
	"maps"
)

type VClock map[Id]int

type Id int

type Ordering int

const (
	LESS Ordering = iota - 1
	EQUAL
	GREATER
	INCOMPATIBLE
)

func New() VClock {
	return VClock{}
}

func (c VClock) Get(id Id) (int, bool) {
	v, ok := c[id]
	return v, ok
}

func (c VClock) Set(id Id, v int) {
	c[id] = v
}

func (c VClock) Incr(id Id) {
	c[id]++
}

func (c1 VClock) Update(c2 VClock) {
	for k := range c2 {
		v1, ok1 := c1[k]
		v2, ok2 := c2[k]
		if v1 < v2 || (!ok1 && ok2) {
			c1[k] = c2[k]
		}
	}
}

func (c1 VClock) Compare(c2 VClock) Ordering {
	// Just used to grab all keys between c1 and c2
	union := maps.Clone(c1)
	maps.Copy(union, c2)

	hasLesser := false
	hasGreater := false

	for k := range union {
		a := c1[k]
		b := c2[k]

		switch {
		case a < b:
			hasLesser = true
		case a > b:
			hasGreater = true
		}
	}

	switch {
	case hasLesser && hasGreater:
		return INCOMPATIBLE
	case hasLesser:
		return LESS
	case hasGreater:
		return GREATER
	default:
		return EQUAL
	}
}
