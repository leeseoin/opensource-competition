package abcmart

import (
	"context"
	"encoding/json"
	"fmt"
	stdhtml "html"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/verification"
)

var (
	productItemPattern = regexp.MustCompile(`(?is)<li\b[^>]*>.*?</li>`)
	tagPattern         = regexp.MustCompile(`(?is)<[^>]+>`)
	discountPattern    = regexp.MustCompile(`\d+`)
)

// verifySearchPage는 검색 JSON 상품 전체를 JavaScript rendering HTML과 ID 기준으로 대조한다.
func (s *Searcher) verifySearchPage(
	ctx context.Context,
	jsonBody []byte,
	products []collector.Product,
	jsonSourceURL string,
	htmlSourceURL string,
	requestID string,
	verifiedAt time.Time,
	page int,
) ([]collector.Product, []collector.Issue) {
	jsonCandidates, err := parseVerificationCandidates(jsonBody)
	if err != nil {
		return markVerificationFailed(products, jsonSourceURL, htmlSourceURL, verifiedAt, err)
	}
	htmlBody, err := s.renderer.Render(ctx, htmlSourceURL)
	if err != nil {
		return markVerificationFailed(products, jsonSourceURL, htmlSourceURL, verifiedAt, err)
	}
	issues := make([]collector.Issue, 0)
	if s.artifacts != nil {
		label := artifactLabel(requestID, page, verifiedAt)
		if saveErr := s.artifacts.SaveHTML(merchantName, label, htmlBody); saveErr != nil {
			issues = append(issues, collector.Issue{
				Code: "ABCMART_RAW_HTML_SAVE_FAILED", Message: saveErr.Error(), Retryable: false, SourceURL: &htmlSourceURL,
			})
		}
	}
	htmlCandidates := ParseSearchHTML(htmlBody)
	for index := range products {
		product := &products[index]
		jsonCandidate, exists := jsonCandidates[product.ExternalID]
		if !exists {
			failure := verification.Failed(jsonSourceURL, htmlSourceURL, "JSON 검증 후보에서 상품을 찾지 못했습니다", verifiedAt)
			product.Verification = &failure
			issues = append(issues, verificationIssue("ABCMART_JSON_CANDIDATE_MISSING", product.ExternalID, failure, jsonSourceURL))
			continue
		}
		htmlCandidate, exists := htmlCandidates[product.ExternalID]
		if !exists {
			missing := verification.MissingInHTML(jsonSourceURL, htmlSourceURL, verifiedAt)
			product.Verification = &missing
			issues = append(issues, verificationIssue("ABCMART_MISSING_IN_HTML", product.ExternalID, missing, htmlSourceURL))
			continue
		}
		compared := verification.Compare(jsonCandidate, htmlCandidate, jsonSourceURL, htmlSourceURL, verifiedAt)
		product.Verification = &compared
		if compared.Status != collector.VerificationMatched {
			issues = append(issues, verificationIssue("ABCMART_JSON_HTML_MISMATCH", product.ExternalID, compared, htmlSourceURL))
		}
	}
	return products, issues
}

// ParseSearchHTML은 ABC마트 rendering HTML의 검색 카드를 상품 ID별 검증 후보로 변환한다.
func ParseSearchHTML(body []byte) map[string]verification.Candidate {
	items := productItemPattern.FindAllString(string(body), -1)
	products := make(map[string]verification.Candidate, len(items))
	for _, item := range items {
		classNames := attribute(item, "class")
		if !hasClassToken(classNames, "col-list-item") || !hasClassToken(classNames, "prod-item") {
			continue
		}
		productID := attribute(item, "data-product-no")
		if productID == "" {
			productID = verification.ProductIDFromLink(merchantName, attributeFromClass(item, "prod-link", "href"))
		}
		if productID == "" {
			continue
		}
		price := textFromClass(item, "price-cost")
		if price == "" {
			price = textFromClass(item, "price-normal-cost")
		}
		originalPrice := textFromClass(item, "price-normal-cost")
		discountText := textFromClass(item, "price-sale-percent")
		var discount *int
		if match := discountPattern.FindString(discountText); match != "" {
			if value, err := strconv.Atoi(match); err == nil {
				discount = &value
			}
		}
		imageURL := attributeFromClass(item, "img-wrap", "src")
		if imageURL == "" {
			imageURL = firstTagAttribute(item, "img", "src")
		}
		products[productID] = verification.Candidate{
			ExternalID:      productID,
			Title:           productNameFromHTML(item),
			Brand:           cleanText(textFromClass(item, "prod-brand")),
			Price:           price,
			OriginalPrice:   originalPrice,
			DiscountPercent: discount,
			ImageURL:        imageURL,
			Link:            productEndpoint + "?prdtNo=" + productID,
		}
	}
	return products
}

// hasClassToken은 HTML class 속성에 정확한 class 이름이 있는지 확인한다.
func hasClassToken(classNames, target string) bool {
	for _, className := range strings.Fields(classNames) {
		if className == target {
			return true
		}
	}
	return false
}

// productNameFromHTML은 상품명 요소의 성별 badge를 제거하고 실제 상품명만 반환한다.
func productNameFromHTML(body string) string {
	pattern := regexp.MustCompile(`(?is)<[^>]*class=["'][^"']*\bprod-name\b[^"']*["'][^>]*>(.*?)</[^>]+>`)
	match := pattern.FindStringSubmatch(body)
	if len(match) != 2 {
		return ""
	}
	badgePattern := regexp.MustCompile(`(?is)<[^>]*class=["'][^"']*\bbadge-gender\b[^"']*["'][^>]*>.*?</[^>]+>`)
	return cleanText(badgePattern.ReplaceAllString(match[1], " "))
}

// parseVerificationCandidates는 ABC마트 검색 JSON을 상품 ID별 비교 값으로 변환한다.
func parseVerificationCandidates(body []byte) (map[string]verification.Candidate, error) {
	var payload searchResponse
	if err := jsonUnmarshal(body, &payload); err != nil {
		return nil, err
	}
	products := make(map[string]verification.Candidate, len(payload.Search))
	for _, raw := range payload.Search {
		item, err := normalizeItem(raw)
		if err != nil {
			return nil, err
		}
		products[item.ProductNo] = verification.Candidate{
			ExternalID: item.ProductNo, Title: item.Name, Brand: item.Brand,
			Price: verification.FormatWon(item.Price), OriginalPrice: verification.FormatWon(item.NormalPrice),
			DiscountPercent: item.Discount, ImageURL: item.ImageURL,
			Link: productEndpoint + "?prdtNo=" + item.ProductNo,
		}
	}
	return products, nil
}

// jsonUnmarshal은 검증 파일이 search.go의 JSON decoder와 같은 오류 경계를 사용하게 한다.
func jsonUnmarshal(body []byte, target any) error {
	if err := json.Unmarshal(body, target); err != nil {
		return fmt.Errorf("ABC마트 검증 JSON 해석 실패: %w", err)
	}
	return nil
}

// markVerificationFailed는 페이지 단위 검증 실패를 선택된 모든 상품에 명시한다.
func markVerificationFailed(products []collector.Product, jsonSourceURL, htmlSourceURL string, verifiedAt time.Time, cause error) ([]collector.Product, []collector.Issue) {
	for index := range products {
		failed := verification.Failed(jsonSourceURL, htmlSourceURL, cause.Error(), verifiedAt)
		products[index].Verification = &failed
	}
	return products, []collector.Issue{{
		Code: "ABCMART_HTML_VERIFICATION_FAILED", Message: "ABC마트 HTML 전수 검증 실패: " + cause.Error(),
		Retryable: false, SourceURL: &htmlSourceURL,
	}}
}

// verificationIssue는 상품별 검증 상태와 불일치 필드를 Collector 경고로 변환한다.
func verificationIssue(code, productID string, result collector.Verification, sourceURL string) collector.Issue {
	message := fmt.Sprintf("ABC마트 JSON/HTML 검증 %s: %s", productID, result.Status)
	if fields := verification.DifferenceFields(result); fields != "" {
		message += " (" + fields + ")"
	}
	return collector.Issue{Code: code, Message: message, Retryable: false, SourceURL: &sourceURL}
}

// artifactLabel은 requestId, page와 수집 시각을 원본 파일명으로 결합한다.
func artifactLabel(requestID string, page int, collectedAt time.Time) string {
	return fmt.Sprintf("%s_%s_page%d", requestID, collectedAt.UTC().Format("20060102_150405"), page)
}

// textFromClass는 지정 class token을 가진 첫 요소의 텍스트를 반환한다.
func textFromClass(body, className string) string {
	pattern := regexp.MustCompile(`(?is)<[^>]*class=["'][^"']*\b` + regexp.QuoteMeta(className) + `\b[^"']*["'][^>]*>(.*?)</[^>]+>`)
	match := pattern.FindStringSubmatch(body)
	if len(match) != 2 {
		return ""
	}
	return cleanText(match[1])
}

// attribute는 HTML 조각의 첫 속성값을 반환한다.
func attribute(body, name string) string {
	pattern := regexp.MustCompile(`(?is)\b` + regexp.QuoteMeta(name) + `\s*=\s*["']([^"']*)["']`)
	match := pattern.FindStringSubmatch(body)
	if len(match) != 2 {
		return ""
	}
	return stdhtml.UnescapeString(strings.TrimSpace(match[1]))
}

// attributeFromClass는 특정 class token 요소의 속성값을 반환한다.
func attributeFromClass(body, className, attributeName string) string {
	pattern := regexp.MustCompile(`(?is)<[^>]*class=["'][^"']*\b` + regexp.QuoteMeta(className) + `\b[^"']*["'][^>]*>`)
	tag := pattern.FindString(body)
	if tag == "" {
		return ""
	}
	if value := attribute(tag, attributeName); value != "" {
		return value
	}
	if className == "img-wrap" {
		containerPattern := regexp.MustCompile(`(?is)<[^>]*class=["'][^"']*\bimg-wrap\b[^"']*["'][^>]*>(.*?)</[^>]+>`)
		match := containerPattern.FindStringSubmatch(body)
		if len(match) == 2 {
			return firstTagAttribute(match[1], "img", attributeName)
		}
	}
	return ""
}

// firstTagAttribute는 첫 태그의 속성값을 반환한다.
func firstTagAttribute(body, tagName, attributeName string) string {
	pattern := regexp.MustCompile(`(?is)<` + regexp.QuoteMeta(tagName) + `\b[^>]*>`)
	tag := pattern.FindString(body)
	if tag == "" {
		return ""
	}
	return attribute(tag, attributeName)
}

// cleanText는 HTML tag, entity와 중복 공백을 제거한 표시 문자열을 반환한다.
func cleanText(value string) string {
	return strings.Join(strings.Fields(stdhtml.UnescapeString(tagPattern.ReplaceAllString(value, " "))), " ")
}
