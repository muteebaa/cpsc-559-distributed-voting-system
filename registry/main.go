package main

import (
	"context"
	"flag"
	"fmt"
	"log/slog"
	"net/http"
	"net/netip"
	"os"
	"os/signal"
	"syscall"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/httplog/v2"

	"github.com/muteebaa/cpsc-559-distributed-voting-system/clock"
	"github.com/muteebaa/cpsc-559-distributed-voting-system/node"
	"github.com/muteebaa/cpsc-559-distributed-voting-system/session"
	"github.com/muteebaa/cpsc-559-distributed-voting-system/sync"
)

var self sync.Peer

// Handles basic configuration for the rest of the program
func main() {
	var paddr netip.Addr
	flag.TextVar(
		&paddr,
		"paddr",
		netip.IPv6Loopback(),
		"IP address of the first peer to connect to, used if -first is not set",
	)
	pport := flag.Uint(
		"pport",
		80,
		"Port number of the first peer to connect to, used if -first is not set",
	)

	var addr netip.Addr
	flag.TextVar(
		&addr,
		"addr",
		netip.IPv6Loopback(),
		"IP address the server is available on",
	)
	port := flag.Int("port", 12020, "Port number the server should listen on")
	rawlogLvl := flag.Int("level", 2, "Minimum level of logs to output (0 -> 3)")
	first := flag.Bool("first", false, `Ignore -paddr and -pport, skipping the "catchup" process`)

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

	addrPort := netip.AddrPortFrom(addr, uint16(*port))
	slog.Debug("initial peer port set", "addrPort", addrPort)
	var pid clock.Id
	if !*first {
		paddrPort := netip.AddrPortFrom(paddr, uint16(*pport))
		slog.Debug("initial peer address set", "addrPort", paddrPort)

		err := join(paddrPort)
		if err != nil {
			slog.Error(err.Error())
			os.Exit(1)
		}

		pid, err = sendSelf(addrPort)
		if err != nil {
			slog.Error(err.Error())
			os.Exit(1)
		}
	} else {
		pid = 0
		slog.Debug("skipping the catchup process")
	}

	httplogOpts := httplog.Options{
		LogLevel: logLvl,
		Concise:  true,
	}

	sessionStore := session.New(pid)
	self.Id = pid
	self.Host = addrPort
	self.Alive = true

	run(*port, httplogOpts, sessionStore)
}

// Begin running the HTTP server, ensuring that shutdowns may be handled
// gracefully
func run(port int, logOpts httplog.Options, s *session.SessionStore) {
	server := &http.Server{Addr: fmt.Sprintf("0.0.0.0:%d", port), Handler: service(logOpts, s)}
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

	err := server.ListenAndServe()
	if err != nil && err != http.ErrServerClosed {
		slog.Error("server closed unexpectedly")
		panic(err)
	}

	<-serverCtx.Done()
}

// Configures the HTTP router
func service(logOpts httplog.Options, s *session.SessionStore) http.Handler {
	r := chi.NewRouter()

	logger := httplog.NewLogger("registry", logOpts)

	// Add some middleware processing on every request
	r.Use(httplog.RequestLogger(logger, []string{"/ping"}))
	r.Use(middleware.StripSlashes)
	r.Use(middleware.Heartbeat("/ping"))

	r.Mount("/sessions", node.Handler(s))
	r.Mount("/peers", sync.Handler(s))

	return r
}
