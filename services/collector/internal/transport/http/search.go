package http

import (
	"encoding/json"
	"errors"
	"io"
	stdhttp "net/http"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/app"
	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

const maxSearchRequestBytes = 64 * 1024

// searchHandler는 상품 검색 JSON 요청을 검사하고 판매처 검색 결과를 반환한다.
type searchHandler struct {
	searcher collector.Searcher
}

// apiErrorResponse는 HTTP 요청 자체가 잘못됐을 때 반환하는 오류 body다.
type apiErrorResponse struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

// newSearchHandler는 등록된 판매처 검색기를 사용하는 HTTP handler를 생성한다.
func newSearchHandler(searchTimeout time.Duration) *searchHandler {
	return &searchHandler{searcher: app.NewSearchRegistry(searchTimeout)}
}

// ServeHTTP는 POST 검색 요청만 허용하고 요청 검증 후 실제 판매처 검색 결과를 반환한다.
func (h *searchHandler) ServeHTTP(w stdhttp.ResponseWriter, r *stdhttp.Request) {
	if r.Method != stdhttp.MethodPost {
		w.Header().Set("Allow", stdhttp.MethodPost)
		writeAPIError(w, stdhttp.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "POST 요청만 허용됩니다")
		return
	}

	var request collector.SearchRequest
	decoder := json.NewDecoder(stdhttp.MaxBytesReader(w, r.Body, maxSearchRequestBytes))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&request); err != nil {
		writeAPIError(w, stdhttp.StatusBadRequest, "INVALID_JSON", "검색 요청 JSON을 읽을 수 없습니다")
		return
	}
	if err := ensureJSONEnds(decoder); err != nil {
		writeAPIError(w, stdhttp.StatusBadRequest, "INVALID_JSON", "검색 요청에는 JSON 객체 하나만 허용됩니다")
		return
	}

	request.ApplyDefaults()
	if err := request.Validate(); err != nil {
		writeAPIError(w, stdhttp.StatusBadRequest, "INVALID_REQUEST", err.Error())
		return
	}
	writeJSON(w, stdhttp.StatusOK, h.searcher.Search(r.Context(), request))
}

// ensureJSONEnds는 첫 JSON 객체 뒤에 다른 JSON 값이 없는지 검사한다.
func ensureJSONEnds(decoder *json.Decoder) error {
	var extra interface{}
	err := decoder.Decode(&extra)
	if errors.Is(err, io.EOF) {
		return nil
	}
	if err == nil {
		return errors.New("additional JSON value")
	}
	return err
}

// writeAPIError는 요청 형식 오류를 일정한 JSON 구조로 반환한다.
func writeAPIError(w stdhttp.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, apiErrorResponse{Code: code, Message: message})
}

// writeJSON은 상태 코드와 JSON content type을 설정하고 값을 응답 body에 기록한다.
func writeJSON(w stdhttp.ResponseWriter, status int, value interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}
