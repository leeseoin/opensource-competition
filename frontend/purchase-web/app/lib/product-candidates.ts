/** ProductMoney는 Product Backend가 반환하는 금액과 통화를 표현한다. */
export interface ProductMoney {
  amount: number;
  currency: string;
}

/** ProductSource는 상품 사실을 확인한 공개 출처와 수집 시각을 표현한다. */
export interface ProductSource {
  sourceUrl: string;
  collectedAt: string;
  collectorVersion: string;
}

/** ProductOption은 사용자가 비교할 사이즈와 색상별 가격 및 재고를 표현한다. */
export interface ProductOption {
  externalId: string;
  label: string;
  size: string | null;
  color: string | null;
  stockStatus: string;
  price: ProductMoney | null;
}

/** ProductCandidate는 화면에 표시할 판매처 상품의 최신 snapshot을 표현한다. */
export interface ProductCandidate {
  id: number;
  merchant: string;
  externalId: string;
  name: string;
  brand: string | null;
  categoryPath: string[];
  productUrl: string;
  imageUrls: string[];
  price: ProductMoney | null;
  stockStatus: string;
  rating: number | null;
  reviewCount: number | null;
  options: ProductOption[];
  source: ProductSource;
}

/** CandidateAssessment는 후보별 옵션 일치와 완화 또는 확인 불가 조건을 표현한다. */
export interface CandidateAssessment {
  candidateId: number;
  keywordScore: number;
  semanticScore: number | null;
  wikiConceptScore: number | null;
  freshnessScore: number;
  evidenceCompletenessScore: number;
  sizeStatus: "MATCH" | "MISMATCH" | "UNKNOWN";
  colorStatus: "MATCH" | "MISMATCH" | "UNKNOWN";
  matchReasons: string[];
  relaxedConditions: string[];
  unknownConditions: string[];
}

/** CandidateListing은 상품군 카드에서 선택 가능한 원본 판매처 상품과 속성을 표현한다. */
export interface CandidateListing {
  product: ProductCandidate;
  attributes: Record<string, string[]>;
  assessment: CandidateAssessment | null;
}

/** CandidateGroup은 같은 상품군의 판매처 상품을 카드 하나로 묶은 응답이다. */
export interface CandidateGroup {
  groupId: string;
  name: string;
  brand: string | null;
  categoryPath: string[];
  groupingBasis: "DERIVED";
  groupingConfidence: number;
  listings: CandidateListing[];
}

/** CandidateAvailability는 선택 판매처 상품의 현재 구매 가능성을 화면용 상태로 표현한다. */
export interface CandidateAvailability {
  label: string;
  tone: "available" | "unknown" | "unavailable";
}

/** ProductCandidateResponse는 질문에 연결된 DB 상품 후보 API 응답이다. */
export interface ProductCandidateResponse {
  question: string;
  query: string;
  totalCount: number;
  hasNext: boolean;
  candidates: ProductCandidate[];
  assessments: CandidateAssessment[];
  groups?: CandidateGroup[];
}

/** 선택 상태와 일치하는 판매처 상품을 찾고 없으면 상품군의 첫 상품을 반환한다. */
export function selectedCandidateListing(
  group: CandidateGroup,
  selectedListingId: number | undefined,
): CandidateListing {
  return group.listings.find((listing) => listing.product.id === selectedListingId)
    ?? group.listings[0];
}

/** 판매처 상품 선택 버튼에 색상 또는 외부 상품번호를 구분 가능한 이름으로 표시한다. */
export function candidateListingLabel(listing: CandidateListing): string {
  const colors = listing.attributes.color ?? [];
  const label = colors.length > 0 ? colors.join("/") : "옵션 확인";
  return `${label} / ${listing.product.externalId}`;
}

/** 최신 재고와 조건 판정을 사용해 컬러별 구매 가능 상태를 과장 없이 계산한다. */
export function candidateAvailability(listing: CandidateListing): CandidateAvailability {
  if (listing.product.stockStatus !== "available") {
    return { label: "현재 품절", tone: "unavailable" };
  }
  if (listing.assessment?.sizeStatus === "MISMATCH") {
    return { label: "요청 사이즈 없음", tone: "unavailable" };
  }
  if (listing.assessment?.unknownConditions.some((condition) => condition.startsWith("필수 가격"))) {
    return { label: "예산 조건 불일치", tone: "unavailable" };
  }
  if (listing.assessment?.sizeStatus === "UNKNOWN") {
    return { label: "사이즈 확인 필요", tone: "unknown" };
  }
  if (listing.assessment?.sizeStatus === "MATCH") {
    return { label: "요청 사이즈 구매 가능", tone: "available" };
  }
  return { label: "재고 있음", tone: "available" };
}

/** additive 상품군 필드가 없는 이전 응답도 판매처 상품 하나짜리 상품군으로 변환한다. */
export function candidateGroups(response: ProductCandidateResponse | null): CandidateGroup[] {
  if (!response) {
    return [];
  }
  if (response.groups && response.groups.length > 0) {
    return response.groups;
  }
  const assessmentById = new Map(response.assessments.map((assessment) => [assessment.candidateId, assessment]));
  return response.candidates.map((product) => ({
    groupId: `listing:${product.merchant}:${product.externalId}`,
    name: product.name,
    brand: product.brand,
    categoryPath: product.categoryPath,
    groupingBasis: "DERIVED",
    groupingConfidence: 1,
    listings: [{ product, attributes: {}, assessment: assessmentById.get(product.id) ?? null }],
  }));
}

const knownProductQueries = [
  "러닝화",
  "운동화",
  "스니커즈",
  "로퍼",
  "구두",
  "부츠",
  "샌들",
] as const;

/**
 * deriveProductQuery는 PoC 질문에서 DB 검색에 사용할 명시적 상품 단어를 선택한다.
 * 알려진 단어가 없으면 전체 질문을 사용하며 LLM 기반 조건 추출로 가장하지 않는다.
 */
export function deriveProductQuery(question: string): string {
  return knownProductQueries.find((query) => question.includes(query)) ?? question.trim();
}

/** 금액을 통화에 맞는 한국어 표시 문자열로 변환한다. */
export function formatProductPrice(price: ProductMoney | null): string {
  if (!price) {
    return "가격 확인 필요";
  }

  return new Intl.NumberFormat("ko-KR", {
    style: "currency",
    currency: price.currency,
    maximumFractionDigits: 0,
  }).format(price.amount);
}

/**
 * fetchProductCandidates는 browser에서 같은 출처의 Next.js server route만 호출한다.
 * @throws Error 입력 오류 또는 Next.js/Product Backend 요청 실패 시 발생한다.
 */
export async function fetchProductCandidates(
  question: string,
  query = deriveProductQuery(question),
): Promise<ProductCandidateResponse> {
  const response = await fetch("/api/product-candidates", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question, query, limit: 5 }),
  });
  const body = await response.json() as ProductCandidateResponse & { message?: string };

  if (!response.ok) {
    throw new Error(body.message ?? "상품 후보를 불러오지 못했습니다.");
  }

  return body;
}
