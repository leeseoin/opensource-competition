const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";
const DEFAULT_TIMEOUT_MS = 5000;

type Fetcher = typeof fetch;

/** 서버 설정에서 Product Backend 요청 제한 시간을 안전한 양의 정수로 읽는다. */
function getTimeoutMs(): number {
  const configured = Number(process.env.PRODUCT_BACKEND_REQUEST_TIMEOUT_MS);
  return Number.isInteger(configured) && configured > 0 ? configured : DEFAULT_TIMEOUT_MS;
}

/** redirectTo는 상대 경로로 303 See Other 응답을 만든다(폼 제출 뒤 진짜 페이지 이동용). */
function redirectTo(location: string): Response {
  return new Response(null, { status: 303, headers: { Location: location } });
}

interface BulkOfferVerificationResponseBody {
  batchId?: string;
  requestedCount?: number;
  message?: string;
  code?: string;
}

/**
 * handleBulkOfferVerificationRequest는 선택한 상품 ID 목록을 Product Backend 일괄
 * 재검증 API에 전달한다. acceptsHtml이 true면(일반 `<form>` 제출) 결과를 쿼리 파라미터로
 * 담은 진짜 페이지 이동(redirect)으로 돌려주고, 그 외에는 JSON을 반환한다.
 * fetcher 주입은 실제 네트워크 없이 정상/실패 경로를 테스트하기 위한 경계다.
 */
export async function handleBulkOfferVerificationRequest(
  productIds: number[],
  acceptsHtml: boolean,
  fetcher: Fetcher = fetch,
): Promise<Response> {
  const backendUrl = process.env.PRODUCT_BACKEND_BASE_URL?.replace(/\/$/, "") ?? DEFAULT_BACKEND_URL;

  if (productIds.length === 0) {
    const message = "선택한 상품이 없습니다.";
    if (acceptsHtml) {
      return redirectTo(`/admin/collections/products?error=${encodeURIComponent(message)}`);
    }
    return Response.json({ code: "NO_PRODUCTS_SELECTED", message }, { status: 400 });
  }

  try {
    const backendResponse = await fetcher(`${backendUrl}/internal/v1/offer-verifications/products/bulk`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ productIds }),
      signal: AbortSignal.timeout(getTimeoutMs()),
    });

    const responseBody = (await backendResponse.json().catch(() => null)) as BulkOfferVerificationResponseBody | null;

    if (!backendResponse.ok) {
      const message = responseBody?.message ?? "상품 서버가 재검증 요청을 처리하지 못했습니다.";
      if (acceptsHtml) {
        return redirectTo(`/admin/collections/products?error=${encodeURIComponent(message)}`);
      }
      return Response.json(
        { code: responseBody?.code ?? "PRODUCT_BACKEND_ERROR", message },
        { status: backendResponse.status },
      );
    }

    if (acceptsHtml) {
      const batchId = responseBody?.batchId ?? "";
      return redirectTo(`/admin/collections/products?batchId=${encodeURIComponent(batchId)}`);
    }
    return Response.json(responseBody, { status: backendResponse.status });
  } catch {
    if (acceptsHtml) {
      return redirectTo(`/admin/collections/products?error=${encodeURIComponent("상품 서버에 연결할 수 없습니다.")}`);
    }
    return Response.json(
      { code: "PRODUCT_BACKEND_UNAVAILABLE", message: "상품 서버에 연결할 수 없습니다." },
      { status: 503 },
    );
  }
}
