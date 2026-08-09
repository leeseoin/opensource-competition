package com.purchasesearch.product_backend.knowledge.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.purchasesearch.product_backend.knowledge.dto.WikiPageDocument;

import tools.jackson.databind.ObjectMapper;

/** WikiPageImportRunner는 애플리케이션 시작 시 Git의 검토 완료 Wiki만 PostgreSQL index에 적재한다. */
@Component
public class WikiPageImportRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(WikiPageImportRunner.class);

	private final boolean enabled;
	private final Path wikiDirectory;
	private final ObjectMapper objectMapper;
	private final WikiConceptIndexService indexService;

	/**
	 * Wiki 적재 활성화 여부와 디렉터리 및 index 서비스를 연결한다.
	 *
	 * @param enabled 검토 Wiki 적재 활성화 여부
	 * @param wikiDirectory Git Wiki JSON 디렉터리
	 * @param objectMapper Wiki JSON 파서
	 * @param indexService PostgreSQL Wiki index 서비스
	 */
	public WikiPageImportRunner(
			@Value("${purchase.wiki.enabled:true}") boolean enabled,
			@Value("${purchase.wiki.directory:../../knowledge/wiki}") String wikiDirectory,
			ObjectMapper objectMapper,
			WikiConceptIndexService indexService) {
		this.enabled = enabled;
		this.wikiDirectory = Path.of(wikiDirectory).normalize();
		this.objectMapper = objectMapper;
		this.indexService = indexService;
	}

	/**
	 * 시작 인자를 소비하지 않고 설정된 디렉터리의 PUBLISHED/SUPERSEDED page를 적재한다.
	 *
	 * @param args Spring Boot 시작 인자
	 */
	@Override
	public void run(ApplicationArguments args) {
		if (!enabled || !Files.isDirectory(wikiDirectory)) {
			LOGGER.info("검토 Wiki 적재를 건너뜁니다: enabled={}, directory={}", enabled, wikiDirectory);
			return;
		}
		try {
			List<Path> pages;
			try (var paths = Files.list(wikiDirectory)) {
				pages = paths
						.filter(path -> path.getFileName().toString().endsWith(".json"))
						.sorted(Comparator.comparing(Path::toString))
						.toList();
			}
			int claimCount = 0;
			for (Path pagePath : pages) {
				WikiPageDocument page = objectMapper.readValue(Files.readString(pagePath), WikiPageDocument.class);
				if ("DRAFT".equals(page.status())) {
					continue;
				}
				claimCount += indexService.indexReviewedPage(page);
			}
			LOGGER.info("검토 Wiki page {}개에서 활성 claim {}개를 적재했습니다.", pages.size(), claimCount);
		} catch (IOException | IllegalArgumentException exception) {
			LOGGER.warn("검토 Wiki 적재에 실패해 기존 검색으로 fallback합니다: {}", exception.getMessage());
		}
	}
}
