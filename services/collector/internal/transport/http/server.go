// Package http provides the Collector's internal HTTP server and routes.
package http

import (
	"context"
	"errors"
	"fmt"
	"net"
	stdhttp "net/http"
	"time"
)

// Server owns the Collector HTTP lifecycle. Run blocks until startup fails,
// the server fails, or the context is canceled; cancellation triggers a
// graceful shutdown bounded by the configured shutdown timeout.
type Server struct {
	server          *stdhttp.Server
	shutdownTimeout time.Duration
}

// NewServer constructs a Collector HTTP server with its routes and transport
// timeouts. It does not bind a socket and has no expected failure mode.
func NewServer(address string, readTimeout, writeTimeout, idleTimeout, shutdownTimeout time.Duration) *Server {
	mux := stdhttp.NewServeMux()
	mux.HandleFunc("/internal/v1/health", healthHandler)

	return &Server{
		server: &stdhttp.Server{
			Addr:         address,
			Handler:      mux,
			ReadTimeout:  readTimeout,
			WriteTimeout: writeTimeout,
			IdleTimeout:  idleTimeout,
		},
		shutdownTimeout: shutdownTimeout,
	}
}

// Run serves HTTP requests until ctx is canceled. It returns nil after a clean
// shutdown, or an error if listening, serving, or graceful shutdown fails.
func (s *Server) Run(ctx context.Context) error {
	listener, err := net.Listen("tcp", s.server.Addr)
	if err != nil {
		return fmt.Errorf("listen for collector HTTP: %w", err)
	}

	serveErr := make(chan error, 1)
	go func() {
		serveErr <- s.server.Serve(listener)
	}()

	select {
	case err := <-serveErr:
		if errors.Is(err, stdhttp.ErrServerClosed) {
			return nil
		}
		return fmt.Errorf("serve collector HTTP: %w", err)
	case <-ctx.Done():
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), s.shutdownTimeout)
	defer cancel()
	if err := s.server.Shutdown(shutdownCtx); err != nil {
		return fmt.Errorf("shut down collector HTTP: %w", err)
	}

	err = <-serveErr
	if err != nil && !errors.Is(err, stdhttp.ErrServerClosed) {
		return fmt.Errorf("serve collector HTTP: %w", err)
	}
	return nil
}
