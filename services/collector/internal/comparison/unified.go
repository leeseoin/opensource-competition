// Package comparison은 운영 CollectorResult를 Python/Go 비교 전용 v1-unified 계약으로 변환한다.
package comparison

import (
	"fmt"
	"regexp"
	"strings"
	"unicode/utf8"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
)

var pricePattern = regexp.MustCompile(`^$|^[0-9,]+원$`)

// UnifiedProduct는 contracts/collector/unified의 상품 한 건과 같은 JSON 구조를 표현한다.
type UnifiedProduct struct {
	SourceProductID string          `json:"source_product_id"`
	Title           string          `json:"title"`
	Brand           string          `json:"brand"`
	Price           string          `json:"price"`
	PriceOriginal   string          `json:"price_original"`
	DiscountPercent *int            `json:"discount_percent"`
	ImageURL        string          `json:"image_url"`
	Images          []string        `json:"images"`
	Color           string          `json:"color"`
	StyleCode       string          `json:"style_code"`
	Link            string          `json:"link"`
	Site            string          `json:"site"`
	Rating          *float64        `json:"rating"`
	ReviewCount     *int            `json:"review_count"`
	Category        string          `json:"category"`
	CategoryPath    string          `json:"category_path"`
	InStock         *bool           `json:"in_stock"`
	Options         UnifiedOptions  `json:"options"`
	Reviews         []UnifiedReview `json:"reviews"`
}

// UnifiedOptions는 비교 상품의 중복 없는 색상과 사이즈 목록을 표현한다.
type UnifiedOptions struct {
	Colors []string `json:"colors"`
	Sizes  []string `json:"sizes"`
}

// UnifiedReview는 작성자 식별정보를 제외한 비교 계약의 리뷰 필드를 표현한다.
type UnifiedReview struct {
	ReviewSourceID *string            `json:"review_source_id"`
	Content        string             `json:"content"`
	Score          *float64           `json:"score"`
	Date           string             `json:"date"`
	Size           string             `json:"size"`
	HelpfulCount   int                `json:"helpful_count"`
	Images         []string           `json:"images"`
	DetailScores   map[string]float64 `json:"detail_scores,omitempty"`
	ReviewColor    *string            `json:"review_color,omitempty"`
	IsBest         *bool              `json:"is_best,omitempty"`
	UserSize       []string           `json:"user_size,omitempty"`
	PartnerComment *string            `json:"partner_comment,omitempty"`
	SizeSurvey     *string            `json:"size_survey,omitempty"`
	IsBlind        *bool              `json:"is_blind,omitempty"`
}

// FromCollectorProduct는 운영 상품 사실을 만들지 않고 v1-unified 비교 상품으로 변환한다.
func FromCollectorProduct(product collector.Product, merchant string) UnifiedProduct {
	brand := ""
	if product.Brand != nil {
		brand = *product.Brand
	}
	price := ""
	if product.Price != nil && product.Price.Currency == "KRW" {
		price = fmt.Sprintf("%s원", formatThousands(product.Price.Amount))
	}
	images := make([]string, len(product.ImageURLs))
	copy(images, product.ImageURLs)
	imageURL := ""
	if len(images) > 0 {
		imageURL = images[0]
	}
	category := ""
	if len(product.CategoryPath) > 0 {
		category = product.CategoryPath[len(product.CategoryPath)-1]
	}
	colors, sizes := optionValues(product.Options)
	stock := stockValue(product.StockStatus)
	reviews := make([]UnifiedReview, 0, len(product.Reviews))
	for _, review := range product.Reviews {
		reviews = append(reviews, fromCollectorReview(review))
	}
	return UnifiedProduct{
		SourceProductID: product.ExternalID,
		Title:           product.Name,
		Brand:           brand,
		Price:           price,
		PriceOriginal:   "",
		DiscountPercent: nil,
		ImageURL:        imageURL,
		Images:          images,
		Color:           strings.Join(colors, ", "),
		StyleCode:       "",
		Link:            product.ProductURL,
		Site:            merchant,
		Rating:          product.Rating,
		ReviewCount:     product.ReviewCount,
		Category:        category,
		CategoryPath:    strings.Join(product.CategoryPath, " > "),
		InStock:         stock,
		Options:         UnifiedOptions{Colors: colors, Sizes: sizes},
		Reviews:         reviews,
	}
}

// Validate는 v1-unified의 필수 필드, 길이와 값 범위를 외부 의존성 없이 검사한다.
func (p UnifiedProduct) Validate() error {
	if err := requiredString("source_product_id", p.SourceProductID, 50); err != nil {
		return err
	}
	if err := requiredString("title", p.Title, 300); err != nil {
		return err
	}
	if err := optionalString("brand", p.Brand, 100); err != nil {
		return err
	}
	if !pricePattern.MatchString(p.Price) || !pricePattern.MatchString(p.PriceOriginal) {
		return fmt.Errorf("price 또는 price_original 형식이 올바르지 않습니다")
	}
	if p.DiscountPercent != nil && (*p.DiscountPercent < 0 || *p.DiscountPercent > 100) {
		return fmt.Errorf("discount_percent 범위가 올바르지 않습니다")
	}
	if err := optionalString("image_url", p.ImageURL, 500); err != nil {
		return err
	}
	if p.Images == nil || p.Options.Colors == nil || p.Options.Sizes == nil || p.Reviews == nil {
		return fmt.Errorf("images, options.colors, options.sizes와 reviews는 null일 수 없습니다")
	}
	for _, image := range p.Images {
		if err := optionalString("images", image, 500); err != nil {
			return err
		}
	}
	if err := optionalString("color", p.Color, 200); err != nil {
		return err
	}
	if err := optionalString("style_code", p.StyleCode, 50); err != nil {
		return err
	}
	if err := requiredString("link", p.Link, 500); err != nil {
		return err
	}
	if p.Site != "abcmart" && p.Site != "29cm" {
		return fmt.Errorf("site는 abcmart 또는 29cm여야 합니다")
	}
	if p.Rating != nil && (*p.Rating < 0 || *p.Rating > 5) {
		return fmt.Errorf("rating 범위가 올바르지 않습니다")
	}
	if p.ReviewCount != nil && *p.ReviewCount < 0 {
		return fmt.Errorf("review_count는 0 이상이어야 합니다")
	}
	if err := optionalString("category", p.Category, 100); err != nil {
		return err
	}
	if err := optionalString("category_path", p.CategoryPath, 200); err != nil {
		return err
	}
	for _, value := range p.Options.Colors {
		if err := optionalString("options.colors", value, 100); err != nil {
			return err
		}
	}
	for _, value := range p.Options.Sizes {
		if err := optionalString("options.sizes", value, 50); err != nil {
			return err
		}
	}
	for index, review := range p.Reviews {
		if err := review.validate(); err != nil {
			return fmt.Errorf("reviews[%d]: %w", index, err)
		}
	}
	return nil
}

// MissingFieldCount는 검색 응답에서 비어 있는 비교용 필드 수를 계산한다.
func (p UnifiedProduct) MissingFieldCount() int {
	missing := 0
	if p.Brand == "" {
		missing++
	}
	if p.PriceOriginal == "" {
		missing++
	}
	if p.ImageURL == "" {
		missing++
	}
	if len(p.Images) == 0 {
		missing++
	}
	if p.Color == "" {
		missing++
	}
	if p.StyleCode == "" {
		missing++
	}
	if p.Rating == nil {
		missing++
	}
	if p.ReviewCount == nil {
		missing++
	}
	if p.Category == "" {
		missing++
	}
	if p.CategoryPath == "" {
		missing++
	}
	if p.InStock == nil {
		missing++
	}
	if len(p.Reviews) == 0 {
		missing++
	}
	if len(p.Options.Colors) == 0 {
		missing++
	}
	if len(p.Options.Sizes) == 0 {
		missing++
	}
	return missing
}

// validate는 비교 리뷰의 필수 필드와 값 범위를 검사한다.
func (r UnifiedReview) validate() error {
	if r.Images == nil {
		return fmt.Errorf("images는 null일 수 없습니다")
	}
	if r.ReviewSourceID != nil && utf8.RuneCountInString(*r.ReviewSourceID) > 50 {
		return fmt.Errorf("review_source_id가 50자를 넘습니다")
	}
	if utf8.RuneCountInString(r.Content) > 500 || utf8.RuneCountInString(r.Date) > 10 || utf8.RuneCountInString(r.Size) > 200 {
		return fmt.Errorf("리뷰 문자열 길이 제한을 넘습니다")
	}
	if r.Score != nil && (*r.Score < 0 || *r.Score > 5) {
		return fmt.Errorf("score 범위가 올바르지 않습니다")
	}
	if r.HelpfulCount < 0 {
		return fmt.Errorf("helpful_count는 0 이상이어야 합니다")
	}
	return nil
}

// fromCollectorReview는 운영 리뷰를 개인정보 없는 비교 리뷰로 변환한다.
func fromCollectorReview(review collector.Review) UnifiedReview {
	content := ""
	if review.Text != nil {
		content = truncateRunes(*review.Text, 500)
	}
	date := ""
	if review.CreatedAt != nil {
		date = review.CreatedAt.Format("2006-01-02")
	}
	size := ""
	if review.PurchasedOption != nil {
		size = truncateRunes(*review.PurchasedOption, 200)
	}
	return UnifiedReview{
		ReviewSourceID: review.ExternalID,
		Content:        content,
		Score:          review.Rating,
		Date:           date,
		Size:           size,
		HelpfulCount:   0,
		Images:         []string{},
	}
}

// optionValues는 옵션 순서를 유지하며 색상과 사이즈 중복을 제거한다.
func optionValues(options []collector.Option) ([]string, []string) {
	colors := make([]string, 0)
	sizes := make([]string, 0)
	seenColors := map[string]bool{}
	seenSizes := map[string]bool{}
	for _, option := range options {
		if option.Color != nil && *option.Color != "" && !seenColors[*option.Color] {
			colors = append(colors, *option.Color)
			seenColors[*option.Color] = true
		}
		if option.Size != nil && *option.Size != "" && !seenSizes[*option.Size] {
			sizes = append(sizes, *option.Size)
			seenSizes[*option.Size] = true
		}
	}
	return colors, sizes
}

// stockValue는 운영 재고 상태를 비교 계약의 nullable boolean으로 변환한다.
func stockValue(status string) *bool {
	value := false
	switch status {
	case collector.StockAvailable:
		value = true
	case collector.StockOut:
	default:
		return nil
	}
	return &value
}

// formatThousands는 0 이상의 정수에 세 자리 쉼표를 넣는다.
func formatThousands(value int) string {
	digits := fmt.Sprintf("%d", value)
	if len(digits) <= 3 {
		return digits
	}
	parts := make([]string, 0, (len(digits)+2)/3)
	for len(digits) > 3 {
		index := len(digits) - 3
		parts = append(parts, digits[index:])
		digits = digits[:index]
	}
	parts = append(parts, digits)
	for left, right := 0, len(parts)-1; left < right; left, right = left+1, right-1 {
		parts[left], parts[right] = parts[right], parts[left]
	}
	return strings.Join(parts, ",")
}

// requiredString은 필수 문자열의 빈 값과 최대 rune 길이를 검사한다.
func requiredString(field, value string, maximum int) error {
	if value == "" {
		return fmt.Errorf("%s가 비어 있습니다", field)
	}
	return optionalString(field, value, maximum)
}

// optionalString은 문자열의 최대 rune 길이를 검사한다.
func optionalString(field, value string, maximum int) error {
	if utf8.RuneCountInString(value) > maximum {
		return fmt.Errorf("%s가 %d자를 넘습니다", field, maximum)
	}
	return nil
}

// truncateRunes는 UTF-8 문자열을 rune 경계에서 최대 길이로 자른다.
func truncateRunes(value string, maximum int) string {
	if utf8.RuneCountInString(value) <= maximum {
		return value
	}
	runes := []rune(value)
	return string(runes[:maximum])
}
