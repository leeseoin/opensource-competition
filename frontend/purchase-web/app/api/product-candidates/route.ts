const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";
const DEFAULT_TIMEOUT_MS = 5000;

interface CandidateRequestBody {
  question?: unknown;
  query?: unknown;
  merchant?: unknown;
  limit?: unknown;
}

type Fetcher = typeof fetch;

/** 요청 본문이 상품 후보 API에 전달 가능한 최소 계약을 만족하는지 확인한다. */
function validateRequest(body: CandidateRequestBody): string | null {
  if (typeof body.question !== "string" || !body.question.trim()) {
    return "question은 비어 있지 않은 문자열이어야 합니다.";
  }
  if (body.question.length > 1000) {
    return "question은 1000자 이하여야 합니다.";
  }
  if (typeof body.query !== "string" || !body.query.trim()) {
    return "query는 비어 있지 않은 문자열이어야 합니다.";
  }
  if (body.query.length > 200) {
    return "query는 200자 이하여야 합니다.";
  }
  if (body.merchant !== undefined && body.merchant !== null
      && (typeof body.merchant !== "string" || !/^[a-z0-9][a-z0-9-]*$/.test(body.merchant))) {
    return "merchant 형식이 올바르지 않습니다.";
  }
  if (body.limit !== undefined && (!Number.isInteger(body.limit) || Number(body.limit) < 1 || Number(body.limit) > 10)) {
    return "limit은 1부터 10 사이의 정수여야 합니다.";
  }
  return null;
}

/** 서버 설정에서 Product Backend 요청 제한 시간을 안전한 양의 정수로 읽는다. */
function getTimeoutMs(): number {
  const configured = Number(process.env.PRODUCT_BACKEND_REQUEST_TIMEOUT_MS);
  return Number.isInteger(configured) && configured > 0 ? configured : DEFAULT_TIMEOUT_MS;
}

/**
 * handleProductCandidateRequest는 입력 검증 후 Product Backend에 요청을 전달한다.
 * fetcher 주입은 실제 네트워크 없이 정상/실패 경로를 테스트하기 위한 경계다.
 */
export async function handleProductCandidateRequest(
  request: Request,
  fetcher: Fetcher = fetch,
): Promise<Response> {
  let body: CandidateRequestBody;
  try {
    body = await request.json() as CandidateRequestBody;
  } catch {
    return Response.json({ code: "INVALID_REQUEST", message: "JSON 요청 본문이 필요합니다." }, { status: 400 });
  }

  const validationError = validateRequest(body);
  if (validationError) {
    return Response.json({ code: "INVALID_REQUEST", message: validationError }, { status: 400 });
  }

  const backendUrl = process.env.PRODUCT_BACKEND_BASE_URL?.replace(/\/$/, "") ?? DEFAULT_BACKEND_URL;
  try {
    const backendResponse = await fetcher(`${backendUrl}/internal/v1/product-candidates/search`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      cache: "no-store",
      signal: AbortSignal.timeout(getTimeoutMs()),
    });

    if (!backendResponse.ok) {
      return Response.json(
        { code: "PRODUCT_BACKEND_ERROR", message: "상품 서버가 요청을 처리하지 못했습니다." },
        { status: 502 },
      );
    }

    return Response.json(await backendResponse.json());
  } catch {
    return Response.json(
      { code: "PRODUCT_BACKEND_UNAVAILABLE", message: "상품 서버에 연결할 수 없습니다." },
      { status: 503 },
    );
  }
}

/** POST는 browser의 상품 후보 요청을 서버 전용 Product Backend 경로로 전달한다. */
export async function POST(request: Request): Promise<Response> {
  return handleProductCandidateRequest(request);
}
