import assert from "node:assert/strict";
import test from "node:test";

import {
  candidateAvailability,
  candidateGroups,
  candidateListingLabel,
  selectedCandidateListing,
  type ProductCandidate,
  type ProductCandidateResponse,
} from "./product-candidates.ts";

/** 테스트 판매처 상품의 색상과 외부 번호를 바꿔 상품군 선택 fixture를 만든다. */
function product(id: number, externalId: string, color: string): ProductCandidate {
  return {
    id,
    merchant: "abcmart",
    externalId,
    name: "밸롭 구름 브리즈",
    brand: "밸롭",
    categoryPath: ["신발", "스포츠", "워킹화"],
    productUrl: `https://example.com/${externalId}`,
    imageUrls: [`https://example.com/${externalId}.jpg`],
    price: { amount: 49900, currency: "KRW" },
    stockStatus: "available",
    rating: null,
    reviewCount: null,
    options: [{
      externalId: `${externalId}-270`,
      label: "270",
      size: "270",
      color,
      stockStatus: "available",
      price: null,
    }],
    source: {
      sourceUrl: `https://example.com/${externalId}`,
      collectedAt: "2026-08-08T10:00:00+09:00",
      collectorVersion: "fixture",
    },
  };
}

/** additive 상품군 응답이 없을 때 기존 후보를 한 행 상품군으로 유지하는지 검증한다. */
test("이전 후보 응답을 한 행 상품군으로 호환한다", () => {
  const black = product(1, "black-1", "BLACK");
  const response: ProductCandidateResponse = {
    question: "검정 구두",
    query: "구두",
    totalCount: 1,
    hasNext: false,
    candidates: [black],
    assessments: [],
  };

  const groups = candidateGroups(response);

  assert.equal(groups.length, 1);
  assert.equal(groups[0].listings[0].product.externalId, "black-1");
});

/** 같은 상품군에서 선택한 WHITE 판매처 상품과 구분 label을 반환하는지 검증한다. */
test("상품군에서 선택한 색상별 판매처 상품을 반환한다", () => {
  const black = product(1, "black-1", "BLACK");
  const white = product(2, "white-1", "WHITE");
  const group = {
    groupId: "derived:abcmart:ballop",
    name: black.name,
    brand: black.brand,
    categoryPath: black.categoryPath,
    groupingBasis: "DERIVED" as const,
    groupingConfidence: 0.8,
    listings: [
      { product: black, attributes: { color: ["BLACK"], size: ["270"] }, assessment: null },
      { product: white, attributes: { color: ["WHITE"], size: ["270"] }, assessment: null },
    ],
  };

  const selected = selectedCandidateListing(group, 2);

  assert.equal(selected.product.externalId, "white-1");
  assert.equal(candidateListingLabel(selected), "WHITE / white-1");
});

/** 상품군 전체 컬러 중 요청 사이즈가 없는 판매 행을 숨기지 않고 구매 불가로 표시하는지 검증한다. */
test("요청 사이즈가 없는 다른 컬러를 구매 불가로 표시한다", () => {
  const brown = product(3, "brown-1", "BROWN");
  const listing = {
    product: brown,
    attributes: { color: ["BROWN"], size: ["260"] },
    assessment: {
      candidateId: 3,
      keywordScore: 1,
      semanticScore: null,
      wikiConceptScore: null,
      freshnessScore: 1,
      evidenceCompletenessScore: 1,
      sizeStatus: "MISMATCH" as const,
      colorStatus: "MISMATCH" as const,
      matchReasons: [],
      relaxedConditions: ["색상 검정 불일치"],
      unknownConditions: [],
    },
  };

  assert.deepEqual(candidateAvailability(listing), {
    label: "요청 사이즈 없음",
    tone: "unavailable",
  });
});
