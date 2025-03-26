package main

import (
	"context"
	"flag"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/httplog/v2"

	"github.com/muteebaa/cpsc-559-distributed-voting-system/node"
	"github.com/muteebaa/cpsc-559-distributed-voting-system/session"
)

// Handles basic configuration for the rest of the program
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

	httplogOpts := httplog.Options{
		LogLevel: logLvl,
		Concise:  true,
	}

	run(*port, httplogOpts)
}

// Begin running the HTTP server, ensuring that shutdowns may be handled
// gracefully
func run(port int, logOpts httplog.Options) {
	err := session.CreateDir()
	if err != nil {
		panic(err)
	}

	server := &http.Server{Addr: fmt.Sprintf("0.0.0.0:%d", port), Handler: service(logOpts)}
	serverCtx, serverStopCtx := context.WithCancel(context.Background())

	sig := make(chan os.Signal, 1)
	// Catch SIGINT (kill -2) and SIGTERM (kill -15) for safe handling
	signal.Notify(sig, os.Interrupt, syscall.SIGTERM)

	slog.Info("server startup initiated")

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

		slog.Info("shutting down server")
		err := server.Shutdown(shutdownCtx)
		if err != nil {
			slog.Error("cannot shutdown server")
			panic(err)
		}

		serverStopCtx()
	}()

	err = server.ListenAndServe()
	if err != nil && err != http.ErrServerClosed {
		slog.Error("server closed unexpectedly")
		panic(err)
	}

	<-serverCtx.Done()
}

// Configures the HTTP router
func service(logOpts httplog.Options) http.Handler {
	r := chi.NewRouter()

	logger := httplog.NewLogger("registry", logOpts)

	// Add some middleware processing on every request
	r.Use(httplog.RequestLogger(logger))
	r.Use(middleware.StripSlashes)
	r.Use(middleware.Heartbeat("/ping"))

	r.Mount("/sessions", node.Handler())

	return r
}
