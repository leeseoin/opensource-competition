// Package artifact는 수집 및 검증에 사용한 원본 JSON과 HTML을 로컬 증거 파일로 저장한다.
package artifact

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

var unsafeFilename = regexp.MustCompile(`[^A-Za-z0-9._-]+`)

// Store는 원본 수집 파일을 저장할 루트 디렉터리를 보관한다.
type Store struct {
	root string
}

// NewStore는 주어진 루트 아래에 판매처별 JSON과 HTML을 저장할 Store를 만든다.
func NewStore(root string) *Store {
	return &Store{root: root}
}

// SaveJSON은 검색 JSON 원본을 raw_json/판매처 디렉터리에 저장한다.
func (s *Store) SaveJSON(merchant, label string, body []byte) error {
	return s.save("raw_json", merchant, label+".json", body)
}

// SaveHTML은 검증 HTML 원본을 raw_html/판매처 디렉터리에 저장한다.
func (s *Store) SaveHTML(merchant, label string, body []byte) error {
	return s.save("raw_html", merchant, label+".html", body)
}

// save는 경로 이탈 문자를 제거하고 원본 바이트를 새 파일로 기록한다.
func (s *Store) save(kind, merchant, filename string, body []byte) error {
	if s == nil || strings.TrimSpace(s.root) == "" {
		return nil
	}
	merchant = unsafeFilename.ReplaceAllString(merchant, "_")
	filename = unsafeFilename.ReplaceAllString(filename, "_")
	directory := filepath.Join(s.root, kind, merchant)
	if err := os.MkdirAll(directory, 0o755); err != nil {
		return fmt.Errorf("원본 디렉터리 생성 실패: %w", err)
	}
	if err := os.WriteFile(filepath.Join(directory, filename), body, 0o644); err != nil {
		return fmt.Errorf("원본 파일 저장 실패: %w", err)
	}
	return nil
}
