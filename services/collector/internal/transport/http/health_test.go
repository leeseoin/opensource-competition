package http

import (
	"encoding/json"
	stdhttp "net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestHealthHandler(t *testing.T) {
	t.Parallel()

	request := httptest.NewRequest(stdhttp.MethodGet, "/internal/v1/health", nil)
	recorder := httptest.NewRecorder()

	testHandler().ServeHTTP(recorder, request)

	if recorder.Code != stdhttp.StatusOK {
		t.Fatalf("status code = %d, want %d", recorder.Code, stdhttp.StatusOK)
	}
	if got := recorder.Header().Get("Content-Type"); got != "application/json" {
		t.Errorf("Content-Type = %q, want %q", got, "application/json")
	}

	var response healthResponse
	if err := json.NewDecoder(recorder.Body).Decode(&response); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if response.Status != "ok" {
		t.Errorf("response status = %q, want %q", response.Status, "ok")
	}
}

func TestHealthHandlerRejectsNonGETMethod(t *testing.T) {
	t.Parallel()

	request := httptest.NewRequest(stdhttp.MethodPost, "/internal/v1/health", nil)
	recorder := httptest.NewRecorder()

	testHandler().ServeHTTP(recorder, request)

	if recorder.Code != stdhttp.StatusMethodNotAllowed {
		t.Fatalf("status code = %d, want %d", recorder.Code, stdhttp.StatusMethodNotAllowed)
	}
	if got := recorder.Header().Get("Allow"); got != stdhttp.MethodGet {
		t.Errorf("Allow = %q, want %q", got, stdhttp.MethodGet)
	}
}

func testHandler() stdhttp.Handler {
	server := NewServer(":0", time.Second, time.Second, time.Second, time.Second)
	return server.server.Handler
}
