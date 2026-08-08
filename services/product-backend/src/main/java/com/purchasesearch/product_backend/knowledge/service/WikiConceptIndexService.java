package com.purchasesearch.product_backend.knowledge.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.purchasesearch.product_backend.knowledge.dto.WikiPageDocument;
import com.purchasesearch.product_backend.knowledge.dto.WikiPageDocument.WikiClaimDocument;

import tools.jackson.databind.ObjectMapper;

/** WikiConceptIndexService는 검토 완료 Wiki page를 적재하고 검색어의 직접 의미 관계를 확장한다. */
@Service
public class WikiConceptIndexService {

	private static final Logger LOGGER = LoggerFactory.getLogger(WikiConceptIndexService.class);
	private static final int MAX_EXPANSIONS = 12;

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	/**
	 * Wiki index 저장과 JSON provenance 변환에 필요한 구성요소를 연결한다.
	 *
	 * @param jdbcTemplate PostgreSQL Wiki index 접근 도구
	 * @param objectMapper source/evidence 목록 JSON 변환기
	 */
	public WikiConceptIndexService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	/**
	 * 사람 검토 정보가 있는 PUBLISHED/SUPERSEDED page만 PostgreSQL index에 원자적으로 반영한다.
	 *
	 * @param page Git에서 읽은 Wiki page
	 * @return 실제 검색에 활성화한 claim 개수, SUPERSEDED page는 0
	 * @throws IllegalArgumentException 검토 정보, 관계 또는 provenance 계약이 잘못된 경우
	 */
	@Transactional
	public int indexReviewedPage(WikiPageDocument page) {
		validateReviewedPage(page);
		Long pageDatabaseId = jdbcTemplate.queryForObject("""
				INSERT INTO wiki_pages (
				  page_id, version, status, title, reviewed_by, reviewed_at, supersedes, indexed_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
				ON CONFLICT (page_id, version) DO UPDATE SET
				  status = EXCLUDED.status,
				  title = EXCLUDED.title,
				  reviewed_by = EXCLUDED.reviewed_by,
				  reviewed_at = EXCLUDED.reviewed_at,
				  supersedes = EXCLUDED.supersedes,
				  indexed_at = CURRENT_TIMESTAMP
				RETURNING id
				""",
				Long.class,
				page.pageId(),
				page.version(),
				page.status(),
				page.title(),
				page.reviewedBy(),
				Timestamp.from(page.reviewedAt().toInstant()),
				page.supersedes());
		jdbcTemplate.update("DELETE FROM wiki_claims WHERE wiki_page_id = ?", pageDatabaseId);
		if (!"PUBLISHED".equals(page.status())) {
			return 0;
		}
		for (WikiClaimDocument claim : page.claims()) {
			jdbcTemplate.update("""
					INSERT INTO wiki_claims (
					  wiki_page_id, claim_id, subject, relation, object, derived, confidence,
					  source_ids, evidence_pointers)
					VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB))
					""",
					pageDatabaseId,
					claim.claimId(),
					claim.subject().trim(),
					claim.relation(),
					claim.object().trim(),
					claim.derived(),
					claim.confidence(),
					writeJson(claim.sourceIds()),
					writeJson(claim.evidencePointers()));
		}
		return page.claims().size();
	}

	/**
	 * 최신 PUBLISHED page의 직접 narrower/synonym/merchant_category 관계로 검색어를 확장한다.
	 *
	 * @param query 사용자가 확인한 상품 종류
	 * @return 원문을 제외한 중복 없는 검토 확장어와 근거
	 */
	@Transactional(readOnly = true)
	public ConceptExpansion expand(String query) {
		if (!StringUtils.hasText(query)) {
			return new ConceptExpansion(query, List.of());
		}
		String normalized = query.trim();
		try {
			List<WikiExpansionTerm> rows = jdbcTemplate.query("""
					WITH latest_published AS (
					  SELECT DISTINCT ON (page_id) id
					  FROM wiki_pages
					  WHERE status = 'PUBLISHED'
					  ORDER BY page_id, version DESC
					)
					SELECT
					  CASE
					    WHEN claim.relation = 'synonym' AND LOWER(claim.object) = LOWER(?)
					      THEN claim.subject
					    ELSE claim.object
					  END AS expanded_term,
					  claim.relation,
					  claim.claim_id,
					  claim.confidence
					FROM wiki_claims claim
					JOIN latest_published page ON page.id = claim.wiki_page_id
					WHERE LOWER(claim.subject) = LOWER(?)
					   OR (claim.relation = 'synonym' AND LOWER(claim.object) = LOWER(?))
					ORDER BY claim.confidence DESC, claim.claim_id
					LIMIT ?
					""",
					(resultSet, rowNumber) -> new WikiExpansionTerm(
							resultSet.getString("expanded_term"),
							resultSet.getString("relation"),
							resultSet.getString("claim_id"),
							resultSet.getDouble("confidence")),
					normalized,
					normalized,
					normalized,
					MAX_EXPANSIONS);
			Map<String, WikiExpansionTerm> unique = new LinkedHashMap<>();
			for (WikiExpansionTerm row : rows) {
				String key = row.value().trim().toLowerCase(Locale.ROOT);
				if (!key.equals(normalized.toLowerCase(Locale.ROOT))) {
					unique.putIfAbsent(key, row);
				}
			}
			return new ConceptExpansion(normalized, List.copyOf(unique.values()));
		} catch (DataAccessException exception) {
			LOGGER.warn("검토 Wiki 의미 확장에 실패해 원문 검색으로 fallback합니다: {}", exception.getMessage());
			return new ConceptExpansion(normalized, List.of());
		}
	}

	/** PUBLISHED 전환에 필요한 사람 검토, 허용 relation과 provenance를 fail-closed로 검증한다. */
	private void validateReviewedPage(WikiPageDocument page) {
		if (page == null || !List.of("PUBLISHED", "SUPERSEDED").contains(page.status())) {
			throw new IllegalArgumentException("DRAFT Wiki page는 운영 index에 적재할 수 없습니다.");
		}
		if (!StringUtils.hasText(page.pageId()) || page.version() < 1 || !StringUtils.hasText(page.title())) {
			throw new IllegalArgumentException("Wiki page 식별자, version과 title이 필요합니다.");
		}
		if (!StringUtils.hasText(page.reviewedBy()) || page.reviewedAt() == null) {
			throw new IllegalArgumentException("PUBLISHED/SUPERSEDED Wiki page에는 사람 검토 정보가 필요합니다.");
		}
		if (page.claims() == null || page.claims().isEmpty()) {
			throw new IllegalArgumentException("Wiki page에는 최소 한 개 claim이 필요합니다.");
		}
		for (WikiClaimDocument claim : page.claims()) {
			if (!StringUtils.hasText(claim.claimId())
					|| !StringUtils.hasText(claim.subject())
					|| !StringUtils.hasText(claim.object())
					|| !List.of("narrower", "synonym", "merchant_category").contains(claim.relation())
					|| claim.confidence() < 0 || claim.confidence() > 1
					|| claim.sourceIds() == null || claim.sourceIds().isEmpty()
					|| claim.evidencePointers() == null || claim.evidencePointers().isEmpty()) {
				throw new IllegalArgumentException("Wiki claim 관계, confidence와 provenance가 올바르지 않습니다.");
			}
		}
	}

	/** JSON 변환 실패를 page 계약 오류로 변환한다. */
	private String writeJson(List<String> values) {
		try {
			return objectMapper.writeValueAsString(values);
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Wiki provenance를 JSON으로 변환하지 못했습니다.", exception);
		}
	}

	/** ConceptExpansion은 원문과 운영 검색에 추가할 검토된 개념 관계를 함께 반환한다. */
	public record ConceptExpansion(String originalQuery, List<WikiExpansionTerm> terms) {

		/** 원문과 확장어를 검색 실행 순서로 반환한다. */
		public List<String> searchTerms() {
			List<String> searchTerms = new ArrayList<>();
			searchTerms.add(originalQuery);
			terms.stream().map(WikiExpansionTerm::value).forEach(searchTerms::add);
			return List.copyOf(searchTerms);
		}
	}

	/** WikiExpansionTerm은 검색 확장어와 사용자에게 설명할 relation/claim/confidence를 보존한다. */
	public record WikiExpansionTerm(String value, String relation, String claimId, double confidence) {
	}
}
