// Package verification은 판매처 JSON과 HTML에서 읽은 상품 표시값을 공통 기준으로 비교한다.
package verification

import (
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

var (
	digitsPattern       = regexp.MustCompile(`[^0-9]`)
	abcmartProductID    = regexp.MustCompile(`(?:[?&]prdtNo=)(\d+)`)
	twentynineProductID = regexp.MustCompile(`/(?:catalog|products)/(\d+)`)
)

// ComparedFields는 두 수집 경로에서 공통으로 확인할 상품 표시 필드다.
var ComparedFields = []string{"title", "brand", "price", "price_original", "discount_percent", "image_url", "link"}

// Candidate는 JSON 또는 HTML에서 읽은 검증용 상품 표시값을 보관한다.
type Candidate struct {
	ExternalID      string
	Title           string
	Brand           string
	Price           string
	OriginalPrice   string
	DiscountPercent *int
	ImageURL        string
	Link            string
}

// Compare는 JSON 기본값과 HTML 표시값을 정규화해 검증 결과를 만든다.
func Compare(jsonValue, htmlValue Candidate, jsonSourceURL, htmlSourceURL string, verifiedAt time.Time) collector.Verification {
	differences := make([]collector.VerificationDifference, 0)
	values := []struct {
		field     string
		jsonValue string
		htmlValue string
	}{
		{field: "title", jsonValue: jsonValue.Title, htmlValue: htmlValue.Title},
		{field: "brand", jsonValue: jsonValue.Brand, htmlValue: htmlValue.Brand},
		{field: "price", jsonValue: jsonValue.Price, htmlValue: htmlValue.Price},
		{field: "price_original", jsonValue: jsonValue.OriginalPrice, htmlValue: htmlValue.OriginalPrice},
		{field: "discount_percent", jsonValue: optionalIntText(jsonValue.DiscountPercent), htmlValue: optionalIntText(htmlValue.DiscountPercent)},
		{field: "image_url", jsonValue: jsonValue.ImageURL, htmlValue: htmlValue.ImageURL},
		{field: "link", jsonValue: jsonValue.Link, htmlValue: htmlValue.Link},
	}
	for _, value := range values {
		if equivalent(value.field, value.jsonValue, value.htmlValue, jsonValue.Price) {
			continue
		}
		differences = append(differences, collector.VerificationDifference{
			Field: value.field, JSONValue: optionalText(value.jsonValue), HTMLValue: optionalText(value.htmlValue),
		})
	}
	status := collector.VerificationMatched
	if len(differences) > 0 {
		status = collector.VerificationMismatch
	}
	return collector.Verification{
		Status: status, ComparedFields: append([]string(nil), ComparedFields...), Differences: differences,
		JSONSourceURL: jsonSourceURL, HTMLSourceURL: htmlSourceURL, VerifiedAt: verifiedAt,
	}
}

// MissingInHTML은 JSON 상품이 HTML에 없을 때 저장할 검증 결과를 만든다.
func MissingInHTML(jsonSourceURL, htmlSourceURL string, verifiedAt time.Time) collector.Verification {
	return collector.Verification{
		Status: collector.VerificationMissingInHTML, ComparedFields: []string{}, Differences: []collector.VerificationDifference{},
		JSONSourceURL: jsonSourceURL, HTMLSourceURL: htmlSourceURL, VerifiedAt: verifiedAt,
	}
}

// Failed는 HTML 요청과 parsing 실패 이유를 과도하게 길지 않은 검증 결과로 만든다.
func Failed(jsonSourceURL, htmlSourceURL, reason string, verifiedAt time.Time) collector.Verification {
	if len(reason) > 500 {
		reason = reason[:500]
	}
	return collector.Verification{
		Status: collector.VerificationFailed, ComparedFields: append([]string(nil), ComparedFields...),
		Differences:   []collector.VerificationDifference{{Field: "html", HTMLValue: optionalText(reason)}},
		JSONSourceURL: jsonSourceURL, HTMLSourceURL: htmlSourceURL, VerifiedAt: verifiedAt,
	}
}

// DifferenceFields는 경고 메시지에 넣을 불일치 필드 목록을 반환한다.
func DifferenceFields(result collector.Verification) string {
	fields := make([]string, 0, len(result.Differences))
	for _, difference := range result.Differences {
		fields = append(fields, difference.Field)
	}
	return strings.Join(fields, ",")
}

// FormatWon은 원화 정수를 HTML 표시값과 비교할 숫자 문자열로 변환한다.
func FormatWon(value int) string {
	return strconv.Itoa(value)
}

// DiscountPercent는 현재가와 정상가로 반올림한 할인율을 계산한다.
func DiscountPercent(price, originalPrice int) *int {
	if originalPrice <= 0 {
		return nil
	}
	value := int(float64(originalPrice-price)*100/float64(originalPrice) + 0.5)
	return &value
}

// ProductIDFromLink는 판매처 URL에서 비교에 사용할 상품 ID를 추출한다.
func ProductIDFromLink(merchant, value string) string {
	pattern := abcmartProductID
	if merchant == "29cm" {
		pattern = twentynineProductID
	}
	match := pattern.FindStringSubmatch(value)
	if len(match) == 2 {
		return match[1]
	}
	return strings.TrimSpace(value)
}

// NormalizeImageURL은 이미지 변환 query를 제외하고 원본 파일 URL만 반환한다.
func NormalizeImageURL(value string) string {
	parsed, err := url.Parse(strings.TrimSpace(value))
	if err != nil {
		return strings.TrimSpace(value)
	}
	parsed.RawQuery = ""
	parsed.Fragment = ""
	return parsed.String()
}

// equivalent는 HTML의 무할인 필드 생략과 표시 형식 차이를 반영해 값을 비교한다.
func equivalent(field, jsonValue, htmlValue, jsonPrice string) bool {
	if field == "price_original" && strings.TrimSpace(htmlValue) == "" {
		original := normalize(field, jsonValue)
		return original == "" || original == normalize("price", jsonPrice)
	}
	if field == "discount_percent" && strings.TrimSpace(htmlValue) == "" {
		return strings.TrimSpace(jsonValue) == "" || strings.TrimSpace(jsonValue) == "0"
	}
	return normalize(field, jsonValue) == normalize(field, htmlValue)
}

// normalize는 필드별 표시 형식을 제거해 의미가 같은 값을 같게 만든다.
func normalize(field, value string) string {
	switch field {
	case "price", "price_original":
		return digitsPattern.ReplaceAllString(value, "")
	case "discount_percent":
		return strings.TrimSpace(value)
	case "image_url":
		return NormalizeImageURL(value)
	case "link":
		for _, merchant := range []string{"abcmart", "29cm"} {
			id := ProductIDFromLink(merchant, value)
			if id != strings.TrimSpace(value) {
				return id
			}
		}
		return strings.TrimSpace(value)
	default:
		return strings.ToLower(strings.Join(strings.Fields(value), " "))
	}
}

// optionalIntText는 선택 할인율을 비교 문자열로 변환한다.
func optionalIntText(value *int) string {
	if value == nil {
		return ""
	}
	return strconv.Itoa(*value)
}

// optionalText는 빈 문자열을 null로 기록하기 위한 포인터로 변환한다.
func optionalText(value string) *string {
	if strings.TrimSpace(value) == "" {
		return nil
	}
	if len(value) > 2000 {
		value = value[:2000]
	}
	return &value
}

// ValidateCandidate는 HTML parser가 필수 식별자를 비운 경우 오류를 반환한다.
func ValidateCandidate(value Candidate) error {
	if strings.TrimSpace(value.ExternalID) == "" {
		return fmt.Errorf("상품 ID가 비어 있습니다")
	}
	return nil
}
