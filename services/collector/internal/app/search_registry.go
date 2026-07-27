// Package app은 Collector 실행 진입점들이 공유하는 판매처 Adapter 조립을 담당한다.
package app

import (
	"time"

	"github.com/leeseoin/opensource-competition/services/collector/internal/collector"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/abcmart"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/musinsa"
	"github.com/leeseoin/opensource-competition/services/collector/internal/merchants/twentyninecm"
)

// NewSearchRegistry는 HTTP 서버와 RabbitMQ Worker가 함께 사용할 판매처 검색 Registry를 생성한다.
// 검색 timeout을 입력받아 ABC마트·29CM·무신사 PoC Adapter가 등록된 Searcher를 반환한다.
func NewSearchRegistry(searchTimeout time.Duration) collector.Searcher {
	return collector.NewSearchRegistry(map[string]collector.Searcher{
		"29cm":    twentyninecm.NewSearcher(searchTimeout),
		"abcmart": abcmart.NewSearcher(searchTimeout),
		"musinsa": musinsa.NewSearcher(searchTimeout),
	})
}
