package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io/fs"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

const sessDir = "session"

func main() {
	port := flag.Int("port", 12020, "Port number the server should listen on")
	rawlogLvl := flag.Int("level", 2, "Minimum level of logs to output (0 -> 3)")

	flag.Parse()

	var logLvl slog.Level
	switch *rawlogLvl {
	case 0:
		logLvl = slog.LevelError
	case 1:
		logLvl = slog.LevelWarn
	case 2:
		logLvl = slog.LevelInfo
	case 3:
		logLvl = slog.LevelDebug
	default:
		logLvl = slog.LevelInfo
	}

	logger := slog.New(
		slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.Level(logLvl)}),
	)
	slog.SetDefault(logger)

	run(*port)
}

func run(port int) {
	err := createDir()
	if err != nil {
		panic(err)
	}

	server := &http.Server{Addr: fmt.Sprintf("0.0.0.0:%d", port), Handler: service()}
	serverCtx, serverStopCtx := context.WithCancel(context.Background())

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, os.Interrupt, syscall.SIGTERM)

	go func() {
		<-sig

		shutdownCtx, cancel := context.WithCancel(context.Background())
		defer cancel()

		go func() {
			<-shutdownCtx.Done()
			if shutdownCtx.Err() == context.DeadlineExceeded {
				slog.Error("graceful shutdown timed out... forcing exit")
			}
		}()

		err := server.Shutdown(shutdownCtx)
		if err != nil {
			panic(err)
		}

		serverStopCtx()
	}()

	err = server.ListenAndServe()
	if err != nil && err != http.ErrServerClosed {
		panic(err)
	}

	<-serverCtx.Done()
}

func service() http.Handler {
	r := chi.NewRouter()

	r.Use(middleware.Logger)
	r.Use(middleware.AllowContentType("application/json"))

	r.Get(fmt.Sprintf("/sessions/{sess:%s}", sessIdRegex), getSingleSession)
	r.Get("/sessions", getAllSessions)
	r.Post("/sessions", addSession)
	r.Get("/sessions/all", getAllSessionInfo)

	return r
}

func createDir() error {
	err := os.Mkdir(sessDir, 0o750)
	if err != nil && !errors.Is(err, fs.ErrExist) {
		return err
	}

	return nil
}
