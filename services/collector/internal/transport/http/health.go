package http

import (
	"encoding/json"
	stdhttp "net/http"
)

// healthResponse는 Collector health endpoint의 최소 응답 body를 표현한다.
type healthResponse struct {
	Status string `json:"status"`
}

// healthHandler는 GET health 요청에 정상 상태를 JSON으로 반환하고 다른 method는 405로 거부한다.
func healthHandler(w stdhttp.ResponseWriter, r *stdhttp.Request) {
	if r.Method != stdhttp.MethodGet {
		w.Header().Set("Allow", stdhttp.MethodGet)
		stdhttp.Error(w, "method not allowed", stdhttp.StatusMethodNotAllowed)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(stdhttp.StatusOK)
	_ = json.NewEncoder(w).Encode(healthResponse{Status: "ok"})
}
