package http

import (
	"embed"
	stdhttp "net/http"
)

// swaggerAssets는 Collector OpenAPI 문서와 Swagger UI 정적 파일을 실행 파일에 포함한다.
//
//go:embed openapi.json swagger.html swagger-initializer.js
var swaggerAssets embed.FS

// openAPIHandler는 Collector의 정적 OpenAPI 3.1 JSON 문서를 반환한다.
func openAPIHandler(w stdhttp.ResponseWriter, r *stdhttp.Request) {
	if r.Method != stdhttp.MethodGet {
		w.Header().Set("Allow", stdhttp.MethodGet)
		writeAPIError(w, stdhttp.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "GET 요청만 허용됩니다")
		return
	}
	body, err := swaggerAssets.ReadFile("openapi.json")
	if err != nil {
		writeAPIError(w, stdhttp.StatusInternalServerError, "OPENAPI_UNAVAILABLE", "OpenAPI 문서를 읽을 수 없습니다")
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(stdhttp.StatusOK)
	_, _ = w.Write(body)
}

// swaggerRedirectHandler는 slash가 없는 Swagger UI 주소를 표준 경로로 이동시킨다.
func swaggerRedirectHandler(w stdhttp.ResponseWriter, r *stdhttp.Request) {
	if r.Method != stdhttp.MethodGet {
		w.Header().Set("Allow", stdhttp.MethodGet)
		writeAPIError(w, stdhttp.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "GET 요청만 허용됩니다")
		return
	}
	stdhttp.Redirect(w, r, "/swagger-ui/", stdhttp.StatusTemporaryRedirect)
}

// swaggerUIHandler는 공식 Swagger UI CDN과 로컬 초기화 코드를 사용해 Collector OpenAPI 화면을 제공한다.
func swaggerUIHandler(w stdhttp.ResponseWriter, r *stdhttp.Request) {
	if r.Method != stdhttp.MethodGet {
		w.Header().Set("Allow", stdhttp.MethodGet)
		writeAPIError(w, stdhttp.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "GET 요청만 허용됩니다")
		return
	}

	assetName := ""
	contentType := ""
	switch r.URL.Path {
	case "/swagger-ui/":
		assetName = "swagger.html"
		contentType = "text/html; charset=utf-8"
	case "/swagger-ui/swagger-initializer.js":
		assetName = "swagger-initializer.js"
		contentType = "application/javascript; charset=utf-8"
	default:
		stdhttp.NotFound(w, r)
		return
	}

	body, err := swaggerAssets.ReadFile(assetName)
	if err != nil {
		writeAPIError(w, stdhttp.StatusInternalServerError, "SWAGGER_UI_UNAVAILABLE", "Swagger UI를 읽을 수 없습니다")
		return
	}
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Content-Security-Policy", "default-src 'none'; script-src 'self' https://unpkg.com; style-src https://unpkg.com 'unsafe-inline'; img-src data: https://validator.swagger.io; connect-src 'self'; font-src https://unpkg.com")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.WriteHeader(stdhttp.StatusOK)
	_, _ = w.Write(body)
}
