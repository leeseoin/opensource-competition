package twentyninecm

import (
	"context"
	"encoding/json"
	"fmt"
	stdhtml "html"
	"io"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/verification"
)

const maxDetailBodyBytes = 8 * 1024 * 1024

var jsonLDPattern = regexp.MustCompile(`(?is)<script\b[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>`)

// verifyProducts는 선택한 29CM 상품 전체의 공개 상세 HTML을 차례로 요청하고 Product JSON-LD와 비교한다.
func (s *Searcher) verifyProducts(
	ctx context.Context,
	jsonBody []byte,
	products []collector.Product,
	jsonSourceURL string,
	verifiedAt time.Time,
) ([]collector.Product, []collector.Issue) {
	jsonCandidates, err := parseVerificationCandidates(jsonBody)
	if err != nil {
		for index := range products {
			failed := verification.Failed(jsonSourceURL, products[index].ProductURL, err.Error(), verifiedAt)
			products[index].Verification = &failed
		}
		return products, []collector.Issue{{
			Code: "29CM_VERIFICATION_JSON_INVALID", Message: err.Error(), Retryable: false, SourceURL: &jsonSourceURL,
		}}
	}

	issues := make([]collector.Issue, 0)
	for index := range products {
		product := &products[index]
		if err := s.waitForTurn(ctx); err != nil {
			failed := verification.Failed(jsonSourceURL, product.ProductURL, err.Error(), s.now())
			product.Verification = &failed
			issues = append(issues, verificationIssue("29CM_DETAIL_REQUEST_CANCELED", product.ExternalID, failed, product.ProductURL, true))
			continue
		}
		htmlBody, finalURL, fetchErr := s.fetchDetailHTML(ctx, product.ProductURL)
		if fetchErr != nil {
			failed := verification.Failed(jsonSourceURL, product.ProductURL, fetchErr.Error(), s.now())
			product.Verification = &failed
			issues = append(issues, verificationIssue("29CM_DETAIL_VERIFICATION_FAILED", product.ExternalID, failed, product.ProductURL, isRetryableDetailError(fetchErr)))
			continue
		}
		if s.artifacts != nil {
			label := fmt.Sprintf("%s_%s", product.ExternalID, s.now().UTC().Format("20060102_150405"))
			if saveErr := s.artifacts.SaveHTML(merchantName, label, htmlBody); saveErr != nil {
				issues = append(issues, collector.Issue{
					Code: "29CM_RAW_HTML_SAVE_FAILED", Message: saveErr.Error(), Retryable: false, SourceURL: &finalURL,
				})
			}
		}
		htmlCandidate, parseErr := ParseProductJSONLD(htmlBody, finalURL)
		if parseErr != nil {
			failed := verification.Failed(jsonSourceURL, finalURL, parseErr.Error(), s.now())
			product.Verification = &failed
			issues = append(issues, verificationIssue("29CM_DETAIL_JSON_LD_INVALID", product.ExternalID, failed, finalURL, false))
			continue
		}
		jsonCandidate, exists := jsonCandidates[product.ExternalID]
		if !exists {
			failed := verification.Failed(jsonSourceURL, finalURL, "검색 JSON 검증 후보에서 상품을 찾지 못했습니다", s.now())
			product.Verification = &failed
			issues = append(issues, verificationIssue("29CM_JSON_CANDIDATE_MISSING", product.ExternalID, failed, jsonSourceURL, false))
			continue
		}
		compared := verification.Compare(jsonCandidate, htmlCandidate, jsonSourceURL, finalURL, s.now())
		product.Verification = &compared
		if compared.Status != collector.VerificationMatched {
			issues = append(issues, verificationIssue("29CM_JSON_HTML_MISMATCH", product.ExternalID, compared, finalURL, false))
		}
	}
	return products, issues
}

// fetchDetailHTML은 29CM 상품 상세 HTML을 응답 크기 상한 내에서 읽고 최종 URL을 반환한다.
func (s *Searcher) fetchDetailHTML(ctx context.Context, targetURL string) ([]byte, string, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		return nil, targetURL, fmt.Errorf("29CM 상세 요청 생성 실패: %w", err)
	}
	request.Header.Set("User-Agent", "PurchaseResearchAgent/0.1 (+public product verification; low rate)")
	request.Header.Set("Accept", "text/html,application/xhtml+xml")
	response, err := s.client.Do(request)
	if err != nil {
		return nil, targetURL, fmt.Errorf("29CM 상세 HTML 요청 실패: %w", err)
	}
	defer response.Body.Close()
	finalURL := targetURL
	if response.Request != nil && response.Request.URL != nil {
		finalURL = response.Request.URL.String()
	}
	if response.StatusCode != http.StatusOK {
		return nil, finalURL, &detailHTTPError{statusCode: response.StatusCode}
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, maxDetailBodyBytes+1))
	if err != nil {
		return nil, finalURL, fmt.Errorf("29CM 상세 HTML 읽기 실패: %w", err)
	}
	if len(body) > maxDetailBodyBytes {
		return nil, finalURL, fmt.Errorf("29CM 상세 HTML이 크기 상한을 넘었습니다")
	}
	return body, finalURL, nil
}

// ParseProductJSONLD는 29CM 상세 HTML의 Product JSON-LD를 공통 비교 후보로 변환한다.
func ParseProductJSONLD(body []byte, sourceURL string) (verification.Candidate, error) {
	for _, match := range jsonLDPattern.FindAllSubmatch(body, -1) {
		if len(match) != 2 {
			continue
		}
		var payload any
		if err := json.Unmarshal([]byte(stdhtml.UnescapeString(string(match[1]))), &payload); err != nil {
			continue
		}
		product, ok := findProductObject(payload)
		if !ok {
			continue
		}
		externalID := textValue(product["sku"])
		if externalID == "" {
			return verification.Candidate{}, fmt.Errorf("29CM Product JSON-LD의 sku가 비어 있습니다")
		}
		offer := firstObject(product["offers"])
		priceSpecification := firstObject(offer["priceSpecification"])
		price, hasPrice := intValue(offer["price"])
		originalPrice, hasOriginalPrice := intValue(priceSpecification["price"])
		if !hasPrice {
			return verification.Candidate{}, fmt.Errorf("29CM Product JSON-LD의 판매가가 비어 있습니다")
		}
		originalText := ""
		if hasOriginalPrice {
			originalText = verification.FormatWon(originalPrice)
		}
		return verification.Candidate{
			ExternalID:      externalID,
			Title:           textValue(product["name"]),
			Brand:           brandName(product["brand"]),
			Price:           verification.FormatWon(price),
			OriginalPrice:   originalText,
			DiscountPercent: verification.DiscountPercent(price, originalPrice),
			ImageURL:        firstImage(product["image"]),
			Link:            firstNonEmpty(textValue(offer["url"]), sourceURL),
		}, nil
	}
	return verification.Candidate{}, fmt.Errorf("29CM 상세 HTML에서 Product JSON-LD를 찾지 못했습니다")
}

// parseVerificationCandidates는 29CM 검색 JSON의 상품을 ID별 비교 값으로 변환한다.
func parseVerificationCandidates(body []byte) (map[string]verification.Candidate, error) {
	var payload searchResponse
	if err := json.Unmarshal(body, &payload); err != nil {
		return nil, fmt.Errorf("29CM 검증 JSON 해석 실패: %w", err)
	}
	products := make(map[string]verification.Candidate, len(payload.Data.List))
	for _, item := range payload.Data.List {
		if item.ItemID <= 0 || item.ItemType != "PRODUCT" {
			continue
		}
		price := effectiveSellPrice(item)
		originalPrice := item.ItemInfo.OriginalPrice
		originalText := ""
		if originalPrice > 0 {
			originalText = verification.FormatWon(originalPrice)
		}
		link := item.ItemURL.WebLink
		if link == "" {
			link = "https://product.29cm.co.kr/catalog/" + strconv.Itoa(item.ItemID)
		}
		products[strconv.Itoa(item.ItemID)] = verification.Candidate{
			ExternalID: strconv.Itoa(item.ItemID), Title: item.ItemInfo.ProductName, Brand: item.ItemInfo.BrandName,
			Price: verification.FormatWon(price), OriginalPrice: originalText,
			DiscountPercent: verification.DiscountPercent(price, originalPrice),
			ImageURL:        item.ItemInfo.ThumbnailURL, Link: link,
		}
	}
	return products, nil
}

// detailHTTPError는 상세 HTML의 HTTP 상태를 재시도 판단에 전달한다.
type detailHTTPError struct {
	statusCode int
}

// Error는 29CM 상세 HTTP 실패 상태를 한국어 문자열로 반환한다.
func (e *detailHTTPError) Error() string {
	return fmt.Sprintf("29CM 상세 HTML이 HTTP %d를 반환했습니다", e.statusCode)
}

// isRetryableDetailError는 429와 5xx 상태만 일시적 실패로 분류한다.
func isRetryableDetailError(err error) bool {
	httpErr, ok := err.(*detailHTTPError)
	return ok && (httpErr.statusCode == http.StatusTooManyRequests || httpErr.statusCode >= 500)
}

// verificationIssue는 29CM 상품 검증 결과를 Collector 경고로 변환한다.
func verificationIssue(code, productID string, result collector.Verification, sourceURL string, retryable bool) collector.Issue {
	message := fmt.Sprintf("29CM JSON/HTML 검증 %s: %s", productID, result.Status)
	if fields := verification.DifferenceFields(result); fields != "" {
		message += " (" + fields + ")"
	}
	return collector.Issue{Code: code, Message: message, Retryable: retryable, SourceURL: &sourceURL}
}

// artifactLabel은 requestId, page와 수집 시각을 29CM 원본 파일명으로 결합한다.
func artifactLabel(requestID string, page int, collectedAt time.Time) string {
	return fmt.Sprintf("%s_%s_page%d", requestID, collectedAt.UTC().Format("20060102_150405"), page)
}

// findProductObject는 JSON-LD 객체 또는 @graph에서 Product 객체를 찾는다.
func findProductObject(value any) (map[string]any, bool) {
	switch typed := value.(type) {
	case map[string]any:
		if hasProductType(typed["@type"]) {
			return typed, true
		}
		if graph, exists := typed["@graph"]; exists {
			return findProductObject(graph)
		}
	case []any:
		for _, item := range typed {
			if product, ok := findProductObject(item); ok {
				return product, true
			}
		}
	}
	return nil, false
}

// hasProductType는 JSON-LD @type 값이 Product를 포함하는지 확인한다.
func hasProductType(value any) bool {
	if text, ok := value.(string); ok {
		return text == "Product"
	}
	if values, ok := value.([]any); ok {
		for _, item := range values {
			if textValue(item) == "Product" {
				return true
			}
		}
	}
	return false
}

// firstObject는 JSON-LD의 단일 객체 또는 배열 첫 객체를 반환한다.
func firstObject(value any) map[string]any {
	if object, ok := value.(map[string]any); ok {
		return object
	}
	if values, ok := value.([]any); ok && len(values) > 0 {
		if object, ok := values[0].(map[string]any); ok {
			return object
		}
	}
	return map[string]any{}
}

// intValue는 JSON 숫자와 숫자 문자열을 원화 정수로 변환한다.
func intValue(value any) (int, bool) {
	switch typed := value.(type) {
	case float64:
		return int(typed), true
	case json.Number:
		parsed, err := strconv.Atoi(typed.String())
		return parsed, err == nil
	case string:
		parsed, err := strconv.ParseFloat(typed, 64)
		return int(parsed), err == nil
	default:
		return 0, false
	}
}

// textValue는 JSON-LD 값을 빈 값이 허용되는 문자열로 변환한다.
func textValue(value any) string {
	if value == nil {
		return ""
	}
	return strings.TrimSpace(fmt.Sprint(value))
}

// brandName은 JSON-LD 브랜드 객체 또는 문자열에서 브랜드명을 반환한다.
func brandName(value any) string {
	if object, ok := value.(map[string]any); ok {
		return textValue(object["name"])
	}
	return textValue(value)
}

// firstImage는 JSON-LD 이미지 객체, 문자열 또는 배열의 첫 URL을 반환한다.
func firstImage(value any) string {
	if values, ok := value.([]any); ok && len(values) > 0 {
		return firstImage(values[0])
	}
	if object, ok := value.(map[string]any); ok {
		return firstNonEmpty(textValue(object["contentUrl"]), textValue(object["url"]))
	}
	return textValue(value)
}

// firstNonEmpty는 앞에서부터 첫 빈 값이 아닌 문자열을 반환한다.
func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return value
		}
	}
	return ""
}
