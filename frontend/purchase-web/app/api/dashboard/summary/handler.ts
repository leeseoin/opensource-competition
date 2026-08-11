const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";
const DEFAULT_TIMEOUT_MS = 5000;

type Fetcher = typeof fetch;

/** 서버 설정에서 Product Backend 요청 제한 시간을 안전한 양의 정수로 읽는다. */
function getTimeoutMs(): number {
  const configured = Number(process.env.PRODUCT_BACKEND_REQUEST_TIMEOUT_MS);
  return Number.isInteger(configured) && configured > 0 ? configured : DEFAULT_TIMEOUT_MS;
}

/**
 * handleDashboardSummaryRequest는 since/until을 그대로 Product Backend 집계 API에 전달한다.
 * fetcher 주입은 실제 네트워크 없이 정상/실패 경로를 테스트하기 위한 경계다.
 */
export async function handleDashboardSummaryRequest(
  request: Request,
  fetcher: Fetcher = fetch,
): Promise<Response> {
  const requestUrl = new URL(request.url);
  const since = requestUrl.searchParams.get("since");
  const until = requestUrl.searchParams.get("until");

  const backendUrl = process.env.PRODUCT_BACKEND_BASE_URL?.replace(/\/$/, "") ?? DEFAULT_BACKEND_URL;
  const summaryUrl = new URL(`${backendUrl}/internal/v1/dashboard/summary`);
  if (since) summaryUrl.searchParams.set("since", since);
  if (until) summaryUrl.searchParams.set("until", until);

  try {
    const backendResponse = await fetcher(summaryUrl.toString(), {
      cache: "no-store",
      signal: AbortSignal.timeout(getTimeoutMs()),
    });

    if (!backendResponse.ok) {
      const body = await backendResponse.json().catch(() => null) as { code?: string; message?: string } | null;
      return Response.json(
        {
          code: body?.code ?? "PRODUCT_BACKEND_ERROR",
          message: body?.message ?? "상품 서버가 집계 요청을 처리하지 못했습니다.",
        },
        { status: backendResponse.status === 400 ? 400 : 502 },
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
