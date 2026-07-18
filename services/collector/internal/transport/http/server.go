// Package http는 Collector의 내부 HTTP server와 route를 제공한다.
package http

import (
	"context"
	"errors"
	"fmt"
	"net"
	stdhttp "net/http"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

// Server는 Collector HTTP lifecycle을 소유한다.
// Run은 시작·serve 실패 또는 context 취소까지 대기하며, 취소 시 설정된 timeout 안에서 graceful shutdown을 수행한다.
type Server struct {
	server          *stdhttp.Server
	shutdownTimeout time.Duration
}

// NewServer는 route와 transport timeout이 설정된 Collector HTTP server를 생성한다.
// socket은 바인딩하지 않으며 현재 입력에 대한 실패 조건은 없다.
func NewServer(address string, readTimeout, writeTimeout, idleTimeout, shutdownTimeout time.Duration) *Server {
	return NewServerWithSearcher(
		address,
		readTimeout,
		writeTimeout,
		idleTimeout,
		shutdownTimeout,
		newSearchHandler(writeTimeout).abcmartSearcher,
	)
}

// NewServerWithSearcher는 지정한 상품 검색기를 주입해 Collector HTTP server를 생성한다.
// 테스트나 판매처 구현 교체 시 실제 외부 요청 없이 같은 HTTP 계약을 검증할 수 있다.
func NewServerWithSearcher(
	address string,
	readTimeout time.Duration,
	writeTimeout time.Duration,
	idleTimeout time.Duration,
	shutdownTimeout time.Duration,
	searcher collector.Searcher,
) *Server {
	mux := stdhttp.NewServeMux()
	mux.HandleFunc("/internal/v1/health", healthHandler)
	mux.Handle("/internal/v1/collect/search", &searchHandler{abcmartSearcher: searcher})

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

// Run은 ctx가 취소될 때까지 HTTP 요청을 처리한다.
// 정상 종료 후에는 nil을, listen·serve·graceful shutdown 실패 시에는 원인을 감싼 오류를 반환한다.
func (s *Server) Run(ctx context.Context) error {
	listener, err := net.Listen("tcp", s.server.Addr)
	if err != nil {
		return fmt.Errorf("listen for collector HTTP: %w", err)
	}
	return s.Serve(ctx, listener)
}

// Handler는 socket을 열지 않고 Collector의 HTTP route를 처리하는 handler를 반환한다.
func (s *Server) Handler() stdhttp.Handler {
	return s.server.Handler
}

// Serve는 준비된 listener에서 HTTP 요청을 처리하고 context 취소 시 graceful shutdown을 수행한다.
// Serve 또는 shutdown이 실패하면 원인을 감싼 오류를 반환하며, 정상 종료 시에는 nil을 반환한다.
func (s *Server) Serve(ctx context.Context, listener net.Listener) error {
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

	err := <-serveErr
	if err != nil && !errors.Is(err, stdhttp.ErrServerClosed) {
		return fmt.Errorf("serve collector HTTP: %w", err)
	}
	return nil
}
