package com.purchasesearch.product_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.purchasesearch.product_backend.product.repository.MerchantProductRepository;

/** RetrievalPerformanceIntegrationTests는 10,000개 PostgreSQL 후보 검색의 로컬 p95를 측정한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RetrievalPerformanceIntegrationTests {

	private static final int PRODUCT_COUNT = 10_000;
	private static final int WARM_UP_RUNS = 5;
	private static final int MEASURED_RUNS = 30;
	private static final double MAX_P95_MILLISECONDS = 1_000.0;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MerchantProductRepository merchantProductRepository;

	/**
	 * 명시적으로 활성화한 경우 10,000개 최신 snapshot에서 FTS/trigram 후보 검색 p95를 측정한다.
	 */
	@Test
	void keepsTenThousandSnapshotFullTextSearchBelowOneSecondP95() {
		assumeTrue("true".equalsIgnoreCase(System.getenv("RETRIEVAL_PERFORMANCE_ENABLED")),
				"RETRIEVAL_PERFORMANCE_ENABLED=true인 opt-in 실행에서만 측정합니다.");
		seedTenThousandSnapshots();

		for (int run = 0; run < WARM_UP_RUNS; run++) {
			search(run);
		}
		List<Double> elapsedMilliseconds = new ArrayList<>();
		for (int run = 0; run < MEASURED_RUNS; run++) {
			long startedAt = System.nanoTime();
			search(run);
			elapsedMilliseconds.add((System.nanoTime() - startedAt) / 1_000_000.0);
		}
		Collections.sort(elapsedMilliseconds);
		double p95 = percentile(elapsedMilliseconds, 0.95);

		System.out.printf(Locale.ROOT,
				"retrieval-perf rows=%d runs=%d p50=%.3fms p95=%.3fms max=%.3fms%n",
				PRODUCT_COUNT,
				MEASURED_RUNS,
				percentile(elapsedMilliseconds, 0.50),
				p95,
				elapsedMilliseconds.get(elapsedMilliseconds.size() - 1));
		assertThat(p95).isLessThanOrEqualTo(MAX_P95_MILLISECONDS);
	}

	/** 운영 구조와 같은 상품/판매처/snapshot/option 행을 SQL set 연산으로 생성한다. */
	private void seedTenThousandSnapshots() {
		jdbcTemplate.update("""
				INSERT INTO products (name, brand, category_path, image_urls)
				SELECT '구두 benchmark ' || sequence,
				       'benchmark-brand',
				       '["신발", "구두"]'::jsonb,
				       '[]'::jsonb
				FROM GENERATE_SERIES(1, ?) AS sequence
				""", PRODUCT_COUNT);
		jdbcTemplate.update("""
				INSERT INTO merchant_products
				    (product_id, merchant, external_id, product_url, last_collected_at)
				SELECT product.id,
				       'benchmark',
				       'benchmark-' || product.id,
				       'https://example.test/products/' || product.id,
				       '2026-08-06T00:00:00+09:00'
				FROM products product
				WHERE product.brand = 'benchmark-brand'
				""");
		jdbcTemplate.update("""
				INSERT INTO collection_search_contexts
				    (request_id, merchant, search_query, filters, collected_at, collector_version)
				SELECT 'benchmark-request-' || merchant_product.id,
				       'benchmark',
				       '구두',
				       '{}'::jsonb,
				       '2026-08-06T00:00:00+09:00',
				       'benchmark-v1'
				FROM merchant_products merchant_product
				WHERE merchant_product.merchant = 'benchmark'
				""");
		jdbcTemplate.update("""
				INSERT INTO offer_snapshots
				    (merchant_product_id, request_id, price_amount, currency, stock_status,
				     source_url, collected_at, collector_version)
				SELECT merchant_product.id,
				       'benchmark-request-' || merchant_product.id,
				       50000 + MOD(merchant_product.id, 50000),
				       'KRW',
				       'available',
				       merchant_product.product_url,
				       '2026-08-06T00:00:00+09:00',
				       'benchmark-v1'
				FROM merchant_products merchant_product
				WHERE merchant_product.merchant = 'benchmark'
				""");
		jdbcTemplate.update("""
				INSERT INTO product_options
				    (offer_snapshot_id, label, size, color, stock_status, source_url,
				     collected_at, collector_version)
				SELECT snapshot.id,
				       'brown/265',
				       '265',
				       'brown',
				       'available',
				       snapshot.source_url,
				       snapshot.collected_at,
				       snapshot.collector_version
				FROM offer_snapshots snapshot
				JOIN merchant_products merchant_product
				  ON merchant_product.id = snapshot.merchant_product_id
				WHERE merchant_product.merchant = 'benchmark'
				""");
		jdbcTemplate.execute("ANALYZE products, merchant_products, collection_search_contexts, offer_snapshots, product_options");
	}

	/** 짧은 한국어 오타와 구조화 조건을 함께 적용해 상위 20개 후보를 읽는다. */
	private void search(int run) {
		var result = merchantProductRepository.searchCandidates(
				"benchmark",
				"구두우 " + (run % 10),
				null,
				100_000L,
				"KRW",
				",265,",
				",brown,",
				true,
				null,
				null,
				null,
				PageRequest.of(0, 20));
		assertThat(result.getContent()).isNotEmpty();
	}

	/** 정렬된 millisecond 표본에서 nearest-rank percentile을 반환한다. */
	private double percentile(List<Double> sortedValues, double percentile) {
		int index = Math.max(0, (int) Math.ceil(sortedValues.size() * percentile) - 1);
		return sortedValues.get(index);
	}
}
