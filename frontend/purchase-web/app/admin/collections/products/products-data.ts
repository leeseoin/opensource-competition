const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";

/** LIMIT_OPTIONS는 Product Backend GET /internal/v1/products가 허용하는 1~10,000 범위 안의 선택지다. */
export const LIMIT_OPTIONS = [20, 50, 100, 1000, 10000] as const;

/** ProductRow는 상품 재검증 화면 표 한 줄에 필요한 최소 정보다. */
export interface ProductRow {
  id: number;
  merchant: string;
  externalId: string;
  name: string;
  brand: string | null;
  collectedAt: string;
}

/** ProductsData는 서버에서 미리 불러와 초기 렌더에 그대로 심어주는 상품 목록이다. */
export interface ProductsData {
  products: ProductRow[];
  errorMessage: string;
}

interface BackendProductSummary {
  id: number;
  merchant: string;
  externalId: string;
  name: string;
  brand: string | null;
  source: { collectedAt: string };
}

function backendUrl(): string {
  return process.env.PRODUCT_BACKEND_BASE_URL?.replace(/\/$/, "") ?? DEFAULT_BACKEND_URL;
}

/** BatchStatus는 대량 재검증 배치 하나의 진행 상태다. */
export interface BatchStatus {
  batchId: string;
  status: "PROCESSING" | "COMPLETED";
  requestedCount: number;
  processedCount: number;
  queuedCount: number;
}

/**
 * loadBatchStatus는 대량 재검증 배치의 지금까지 진행 상태를 Product Backend에서 가져온다.
 * 상품마다 Queue 발행이 걸리는 대량 배치는 배경 스레드에서 처리되므로, 접수 직후에는
 * PROCESSING이고 새로고침해야 최신 진행률을 볼 수 있다.
 *
 * @param batchId 조회할 배치 ID
 */
export async function loadBatchStatus(batchId: string): Promise<BatchStatus | null> {
  try {
    const response = await fetch(
      `${backendUrl()}/internal/v1/offer-verifications/products/bulk/${encodeURIComponent(batchId)}`,
      { cache: "no-store" },
    );
    if (!response.ok) {
      return null;
    }
    return (await response.json()) as BatchStatus;
  } catch {
    return null;
  }
}

/**
 * loadProductsData는 Next.js 서버에서 직접 Product Backend를 호출해 상품 목록을 가져온다.
 * dashboard-data.ts와 같은 이유로 서버에서 직접 호출한다 — 브라우저 fetch/XHR을 가로채는
 * 확장 프로그램·보안 소프트웨어와 무관하게 항상 동작한다.
 *
 * @param merchant 선택 판매처이며 없으면 전체 판매처를 조회한다
 * @param sort "oldest"면 마지막 수집이 오래된 상품부터, 그 외면 최근 수집 순으로 정렬한다
 * @param limit 조회할 최대 상품 수(1~10,000)
 * @param staleHours 지정하면 마지막 수집이 이 시간(시) 이전인 상품만 조회한다
 */
export async function loadProductsData(
  merchant: string | undefined,
  sort: string,
  limit: number,
  staleHours: number | undefined,
): Promise<ProductsData> {
  try {
    const params = new URLSearchParams({ limit: String(limit), sort });
    if (merchant) params.set("merchant", merchant);
    if (staleHours !== undefined) params.set("staleHours", String(staleHours));

    const response = await fetch(`${backendUrl()}/internal/v1/products?${params.toString()}`, { cache: "no-store" });
    if (!response.ok) {
      return { products: [], errorMessage: "상품 목록을 불러오지 못했습니다." };
    }

    const body = (await response.json()) as { products: BackendProductSummary[] };
    const products = body.products.map((product) => ({
      id: product.id,
      merchant: product.merchant,
      externalId: product.externalId,
      name: product.name,
      brand: product.brand,
      collectedAt: product.source.collectedAt,
    }));
    return { products, errorMessage: "" };
  } catch {
    return { products: [], errorMessage: "상품 서버에 연결할 수 없습니다." };
  }
}
