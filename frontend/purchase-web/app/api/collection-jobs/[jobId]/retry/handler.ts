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

/**
 * handleCollectionJobRetryRequest는 jobId를 그대로 Product Backend job 재실행 API에 전달한다.
 * acceptsHtml이 true면(일반 `<form>` 제출) 브라우저의 fetch/XHR을 거치지 않도록 결과를 쿼리
 * 파라미터로 담은 진짜 페이지 이동(redirect)으로 돌려주고, 그 외에는 기존처럼 JSON을 반환한다.
 * fetcher 주입은 실제 네트워크 없이 정상/실패 경로를 테스트하기 위한 경계다.
 */
export async function handleCollectionJobRetryRequest(
  jobId: string,
  acceptsHtml: boolean,
  fetcher: Fetcher = fetch,
): Promise<Response> {
  const backendUrl = process.env.PRODUCT_BACKEND_BASE_URL?.replace(/\/$/, "") ?? DEFAULT_BACKEND_URL;

  try {
    const backendResponse = await fetcher(
      `${backendUrl}/internal/v1/collection-jobs/${encodeURIComponent(jobId)}/retry`,
      { method: "POST", signal: AbortSignal.timeout(getTimeoutMs()) },
    );

    const responseBody = (await backendResponse.json().catch(() => null)) as
      | { code?: string; jobId?: string; message?: string }
      | null;

    if (!backendResponse.ok) {
      const message = responseBody?.message ?? "상품 서버가 재실행 요청을 처리하지 못했습니다.";
      if (acceptsHtml) {
        return redirectTo(`/admin/collections?retryError=${encodeURIComponent(message)}`);
      }
      return Response.json(
        { code: responseBody?.code ?? "PRODUCT_BACKEND_ERROR", message },
        { status: backendResponse.status },
      );
    }

    if (acceptsHtml) {
      return redirectTo(`/admin/collections?retried=${encodeURIComponent(responseBody?.jobId ?? "")}`);
    }
    return Response.json(responseBody, { status: backendResponse.status });
  } catch {
    if (acceptsHtml) {
      return redirectTo(`/admin/collections?retryError=${encodeURIComponent("상품 서버에 연결할 수 없습니다.")}`);
    }
    return Response.json(
      { code: "PRODUCT_BACKEND_UNAVAILABLE", message: "상품 서버에 연결할 수 없습니다." },
      { status: 503 },
    );
  }
}
