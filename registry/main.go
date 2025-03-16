package main

import (
	"errors"
	"flag"
	"fmt"
	"io/fs"
	"net/http"
	"os"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

const sessDir = "session"

func main() {
	port := flag.Int("port", 12020, "Port number the server should listen on")
	flag.Parse()

	err := createDir()
	if err != nil {
		panic(err)
	}

	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Use(middleware.AllowContentType("application/json"))

	r.Get(fmt.Sprintf("/sessions/{sess:%s}", sessIdRegex), getSingleSession)
	r.Get("/sessions", getAllSessions)
	r.Post("/sessions", addSession)
	r.Get("/sessions/all", getAllSessionInfo)

	err = http.ListenAndServe(fmt.Sprintf(":%d", *port), r)
	if err != nil {
		panic(err)
	}
}

func createDir() error {
	err := os.Mkdir(sessDir, 0o750)
	if err != nil && !errors.Is(err, fs.ErrExist) {
		return err
	}

	return nil
}
