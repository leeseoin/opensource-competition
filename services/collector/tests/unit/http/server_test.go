package http_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net"
	stdhttp "net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	collectorhttp "github.com/leeseoin/opensource-competition/services/collector/internal/transport/http"
)

// searcherFunc는 함수로 검색 결과를 제어하는 HTTP 테스트 대역이다.
type searcherFunc func(context.Context, collector.SearchRequest) collector.SearchResult

// Search는 등록된 함수에 검색 요청을 전달한다.
func (f searcherFunc) Search(ctx context.Context, request collector.SearchRequest) collector.SearchResult {
	return f(ctx, request)
}

// TestHealthRoute는 health route의 정상 응답과 method 제한을 검증한다.
func TestHealthRoute(t *testing.T) {
	handler := testHandler(searcherFunc(func(context.Context, collector.SearchRequest) collector.SearchResult {
		return collector.SearchResult{}
	}))

	t.Run("GET", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		handler.ServeHTTP(recorder, httptest.NewRequest(stdhttp.MethodGet, "/internal/v1/health", nil))
		if recorder.Code != stdhttp.StatusOK || recorder.Header().Get("Content-Type") != "application/json" {
			t.Fatalf("response = %#v", recorder.Result())
		}
		var body map[string]string
		if err := json.NewDecoder(recorder.Body).Decode(&body); err != nil || body["status"] != "ok" {
			t.Fatalf("body = %#v, error = %v", body, err)
		}
	})

	t.Run("POST", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		handler.ServeHTTP(recorder, httptest.NewRequest(stdhttp.MethodPost, "/internal/v1/health", nil))
		if recorder.Code != stdhttp.StatusMethodNotAllowed {
			t.Fatalf("status = %d", recorder.Code)
		}
	})
}

// TestSearchRoute는 검색 요청 기본값과 JSON 응답을 공개 route에서 검증한다.
func TestSearchRoute(t *testing.T) {
	searcher := searcherFunc(func(_ context.Context, request collector.SearchRequest) collector.SearchResult {
		if request.Limit != 10 || request.Locale != "ko-KR" || request.Currency != "KRW" {
			t.Errorf("defaults not applied: %#v", request)
		}
		return collector.SearchResult{
			RequestID:        request.RequestID,
			Operation:        collector.OperationSearch,
			Status:           collector.StatusSuccess,
			Merchant:         request.Merchant,
			CollectedAt:      time.Now(),
			CollectorVersion: "test-v1",
			Products:         []collector.Product{},
			Warnings:         []collector.Issue{},
			Errors:           []collector.Issue{},
		}
	})
	body := `{"requestId":"research-001","merchant":"abcmart","query":"구두","requestedAt":"2026-07-16T12:00:00+09:00"}`
	recorder := httptest.NewRecorder()
	testHandler(searcher).ServeHTTP(
		recorder,
		httptest.NewRequest(stdhttp.MethodPost, "/internal/v1/collect/search", bytes.NewBufferString(body)),
	)
	if recorder.Code != stdhttp.StatusOK {
		t.Fatalf("status = %d, body = %s", recorder.Code, recorder.Body.String())
	}
	var result collector.SearchResult
	if err := json.NewDecoder(recorder.Body).Decode(&result); err != nil || result.Status != collector.StatusSuccess {
		t.Fatalf("result = %#v, error = %v", result, err)
	}
}

// TestDefaultServerRoutesMusinsaSearcher는 운영 Registry가 무신사 요청을 실제 검색기로 전달하는지 검증한다.
func TestDefaultServerRoutesMusinsaSearcher(t *testing.T) {
	server := collectorhttp.NewServer(":0", time.Second, time.Second, time.Second, time.Second)
	body := `{"requestId":"musinsa-001","merchant":"musinsa","query":"구두","requestedAt":"2026-07-19T12:00:00+09:00"}`
	recorder := httptest.NewRecorder()
	request := httptest.NewRequest(stdhttp.MethodPost, "/internal/v1/collect/search", bytes.NewBufferString(body))
	ctx, cancel := context.WithCancel(request.Context())
	cancel()
	server.Handler().ServeHTTP(
		recorder,
		request.WithContext(ctx),
	)

	var result collector.SearchResult
	if err := json.NewDecoder(recorder.Body).Decode(&result); err != nil {
		t.Fatalf("decode result: %v", err)
	}
	if recorder.Code != stdhttp.StatusOK || result.Status != collector.StatusTemporarilyUnavailable {
		t.Fatalf("status = %d, result = %#v", recorder.Code, result)
	}
	if len(result.Errors) != 1 || result.Errors[0].Code != "MUSINSA_REQUEST_FAILED" {
		t.Fatalf("errors = %#v", result.Errors)
	}
}

// TestSearchRouteRejectsBadRequests는 잘못된 JSON 요청을 거부하는지 검증한다.
func TestSearchRouteRejectsBadRequests(t *testing.T) {
	handler := testHandler(searcherFunc(func(context.Context, collector.SearchRequest) collector.SearchResult {
		t.Fatal("invalid request must not call searcher")
		return collector.SearchResult{}
	}))
	testCases := []struct {
		name       string
		body       string
		wantStatus int
	}{
		{name: "missing fields", body: `{"merchant":"abcmart"}`, wantStatus: stdhttp.StatusBadRequest},
		{name: "unknown field", body: `{"requestId":"r","merchant":"abcmart","query":"구두","requestedAt":"2026-07-16T12:00:00+09:00","unknown":true}`, wantStatus: stdhttp.StatusBadRequest},
	}
	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			recorder := httptest.NewRecorder()
			handler.ServeHTTP(
				recorder,
				httptest.NewRequest(stdhttp.MethodPost, "/internal/v1/collect/search", strings.NewReader(testCase.body)),
			)
			if recorder.Code != testCase.wantStatus {
				t.Fatalf("status = %d, want %d", recorder.Code, testCase.wantStatus)
			}
		})
	}
}

// blockingListener는 실제 socket 없이 서버 종료 흐름을 검증하는 테스트 listener다.
type blockingListener struct {
	acceptStarted chan struct{}
	closed        chan struct{}
	startOnce     sync.Once
	closeOnce     sync.Once
}

// testAddr는 blockingListener가 반환하는 고정 테스트 주소다.
type testAddr struct{}

// Accept는 Close가 호출될 때까지 기다린 뒤 종료 오류를 반환한다.
func (l *blockingListener) Accept() (net.Conn, error) {
	l.startOnce.Do(func() { close(l.acceptStarted) })
	<-l.closed
	return nil, net.ErrClosed
}

// Close는 대기 중인 Accept를 해제한다.
func (l *blockingListener) Close() error {
	l.closeOnce.Do(func() { close(l.closed) })
	return nil
}

// Addr는 테스트 listener의 주소를 반환한다.
func (l *blockingListener) Addr() net.Addr { return testAddr{} }

// Network는 테스트 network 이름을 반환한다.
func (testAddr) Network() string { return "test" }

// String은 테스트 주소 문자열을 반환한다.
func (testAddr) String() string { return "blocking-listener" }

// TestServerLifecycle는 listen 실패와 context 취소 종료를 검증한다.
func TestServerLifecycle(t *testing.T) {
	server := collectorhttp.NewServer("127.0.0.1:-1", time.Second, time.Second, time.Second, time.Second)
	if err := server.Run(context.Background()); err == nil || !strings.Contains(err.Error(), "listen for collector HTTP") {
		t.Fatalf("Run() error = %v", err)
	}

	server = collectorhttp.NewServer("127.0.0.1:0", time.Second, time.Second, time.Second, time.Second)
	listener := &blockingListener{acceptStarted: make(chan struct{}), closed: make(chan struct{})}
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- server.Serve(ctx, listener) }()
	<-listener.acceptStarted
	cancel()
	select {
	case err := <-done:
		if err != nil {
			t.Fatalf("Serve() error = %v", err)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("Serve() did not stop")
	}
}

// testHandler는 실제 socket 없이 route를 검증할 handler를 생성한다.
func testHandler(searcher collector.Searcher) stdhttp.Handler {
	return collectorhttp.NewServerWithSearcher(
		":0",
		time.Second,
		time.Second,
		time.Second,
		time.Second,
		searcher,
	).Handler()
}
