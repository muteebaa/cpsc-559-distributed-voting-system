package session

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"log/slog"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

const dir = "session"

func List() (fnames []string, err error) {
	f, err := os.Open(dir)
	if err != nil {
		return fnames, err
	}
	defer f.Close()

	finfo, err := f.Readdirnames(0)
	if err != nil {
		return fnames, err
	}

	idValidator := regexp.MustCompile(SessFileRegex)
	for _, file := range finfo {
		if idValidator.MatchString(file) {
			fnames = append(fnames, strings.TrimSuffix(file, ".json"))
		}
	}

	return fnames, nil
}

func Find(id Id) (s Session, err error) {
	fname := fmt.Sprintf("%s.json", id)
	f, err := os.OpenInRoot(dir, fname)
	if err != nil {
		slog.Debug("Session could not be read from", "id", id, "error", err)
		return s, err
	}
	defer f.Close()

	dec := json.NewDecoder(f)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&s); err != nil {
		slog.Error("Session file could not be properly decoded", "Id", id, "error", err)
		return s, err
	}

	return s, nil
}

func FindAll() (sessions []Session, err error) {
	f, err := filepath.Glob(filepath.Join(dir, "*.json"))
	if err != nil {
		slog.Error("Failed to read session directory")
		return sessions, err
	}

	for _, file := range f {
		data, err := os.Open(file)
		if err != nil {
			// TODO: Handle error
			slog.Warn("Session file could not be read", "filename", file)
			continue
		}
		defer data.Close()

		var s Session
		dec := json.NewDecoder(data)
		dec.DisallowUnknownFields()
		if err := dec.Decode(&s); err != nil {
			// TODO: Handle error
			slog.Warn("Session file does not contain valid JSON", "filename", file)
			continue
		}

		sessions = append(sessions, s)
	}

	return sessions, nil
}

func Create(s Session) error {
	if s.Host == nil || s.Port == 0 || s.Options == nil {
		slog.Error("Invalid session metadata passed", "session", s)
		return errors.New("Invalid session metadata passed")
	}
	s.Id = genId()

	root, err := os.OpenRoot(dir)
	if err != nil {
		slog.Error("Session directory could not be opened", "dir", dir)
		return err
	}

	// TODO: Check for existing file
	fname := fmt.Sprintf("%s.json", s.Id)
	f, err := root.Create(fname)
	if err != nil {
		slog.Error("Session file could not be created", "filename", fname)
		return err
	}

	e := json.NewEncoder(f)
	if err := e.Encode(s); err != nil {
		slog.Error("Session could not be JSON encoded", "session", s)
		return err
	}

	return nil
}

func Update(id Id, sNew Session) error {
	fname := fmt.Sprintf("%s.json", id)
	f, err := os.OpenInRoot(dir, fname)
	if err != nil {
		slog.Error("Session file could not be opened", "filename", fname)
		return err
	}
	defer f.Close()

	var sOld Session
	dec := json.NewDecoder(f)
	dec.DisallowUnknownFields()
	if err = dec.Decode(&sOld); err != nil {
		slog.Error("Session file could not be decoded", "filename", fname)
		return err
	}

	sOld.Update(&sNew)
	enc := json.NewEncoder(f)
	if err := enc.Encode(sOld); err != nil {
		slog.Error("Session could not be JSON encoded", "session", sOld)
		return err
	}

	return nil
}

func CreateDir() error {
	err := os.Mkdir(dir, 0o750)
	if err != nil && !errors.Is(err, fs.ErrExist) {
		slog.Error(fmt.Sprintf(`Directory "%s" could not be created`, dir))
		return err
	}

	slog.Debug(fmt.Sprintf(`Directory "%s" created or found`, dir))
	return nil
}
