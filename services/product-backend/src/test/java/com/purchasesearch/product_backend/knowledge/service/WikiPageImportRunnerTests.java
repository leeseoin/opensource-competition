package com.purchasesearch.product_backend.knowledge.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationArguments;

import com.purchasesearch.product_backend.knowledge.dto.WikiPageDocument;
import com.purchasesearch.product_backend.knowledge.dto.WikiPageDocument.WikiClaimDocument;

import tools.jackson.databind.ObjectMapper;

/** WikiPageImportRunnerTests는 시작 시 검토 상태별 Wiki 적재와 비활성 fallback을 검증한다. */
class WikiPageImportRunnerTests {

	@TempDir
	Path temporaryDirectory;

	/** PUBLISHED page만 index 서비스로 전달하고 DRAFT page는 운영 검색에서 건너뛰는지 검증한다. */
	@Test
	void importsPublishedPageAndSkipsDraftPage() throws Exception {
		Path draftPath = temporaryDirectory.resolve("a-draft.json");
		Path publishedPath = temporaryDirectory.resolve("b-published.json");
		Files.writeString(draftPath, "draft");
		Files.writeString(publishedPath, "published");
		ObjectMapper objectMapper = mock(ObjectMapper.class);
		WikiConceptIndexService indexService = mock(WikiConceptIndexService.class);
		WikiPageDocument draft = wikiPage("DRAFT", null, null);
		WikiPageDocument published = wikiPage(
				"PUBLISHED",
				"human-reviewer",
				OffsetDateTime.parse("2026-08-08T21:00:00+09:00"));
		when(objectMapper.readValue("draft", WikiPageDocument.class)).thenReturn(draft);
		when(objectMapper.readValue("published", WikiPageDocument.class)).thenReturn(published);
		when(indexService.indexReviewedPage(published)).thenReturn(1);
		WikiPageImportRunner runner = new WikiPageImportRunner(
				true,
				temporaryDirectory.toString(),
				objectMapper,
				indexService);

		runner.run(mock(ApplicationArguments.class));

		verify(indexService, never()).indexReviewedPage(draft);
		verify(indexService).indexReviewedPage(published);
	}

	/** Wiki 적재가 비활성화되면 파일을 읽거나 PostgreSQL index를 변경하지 않는지 검증한다. */
	@Test
	void skipsDirectoryWhenWikiImportIsDisabled() throws Exception {
		Files.writeString(temporaryDirectory.resolve("published.json"), "published");
		ObjectMapper objectMapper = mock(ObjectMapper.class);
		WikiConceptIndexService indexService = mock(WikiConceptIndexService.class);
		WikiPageImportRunner runner = new WikiPageImportRunner(
				false,
				temporaryDirectory.toString(),
				objectMapper,
				indexService);

		runner.run(mock(ApplicationArguments.class));

		verify(objectMapper, never()).readValue("published", WikiPageDocument.class);
		verify(indexService, never()).indexReviewedPage(org.mockito.ArgumentMatchers.any());
	}

	/** 테스트 상태에 맞춘 단일 claim Wiki page를 만든다. */
	private WikiPageDocument wikiPage(String status, String reviewer, OffsetDateTime reviewedAt) {
		return new WikiPageDocument(
				"sports-shoes-taxonomy",
				1,
				status,
				"운동화 분류",
				reviewer,
				reviewedAt,
				null,
				List.of(new WikiClaimDocument(
						"sports-shoes-running",
						"운동화",
						"narrower",
						"러닝화",
						true,
						0.9,
						List.of("source"),
						List.of("product.category=러닝화"))));
	}
}
