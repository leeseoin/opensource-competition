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

/** ProductCandidateResponse는 질문에 연결된 DB 상품 후보 API 응답이다. */
export interface ProductCandidateResponse {
  question: string;
  query: string;
  totalCount: number;
  hasNext: boolean;
  candidates: ProductCandidate[];
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
    body: JSON.stringify({ question, query, limit: 3 }),
  });
  const body = await response.json() as ProductCandidateResponse & { message?: string };

  if (!response.ok) {
    throw new Error(body.message ?? "상품 후보를 불러오지 못했습니다.");
  }

  return body;
}
