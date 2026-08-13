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

/** parseCommaList는 쉼표로 구분한 입력을 공백 제거한 비어 있지 않은 항목 목록으로 바꾼다. */
function parseCommaList(value: string): string[] | undefined {
  const items = value.split(",").map((item) => item.trim()).filter((item) => item.length > 0);
  return items.length > 0 ? items : undefined;
}

/** parseOptionalInt는 빈 입력을 undefined로, 그 외는 정수로 바꾼다. */
function parseOptionalInt(value: string): number | undefined {
  if (value.trim() === "") return undefined;
  const parsed = Number.parseInt(value, 10);
  return Number.isNaN(parsed) ? undefined : parsed;
}

/** buildPayloadFromFormData는 일반 `<form>` 제출 값을 backend가 받는 JSON 조건으로 바꾼다. */
function buildPayloadFromFormData(formData: FormData): unknown {
  const getText = (name: string) => String(formData.get(name) ?? "").trim();
  const filters = {
    priceMin: parseOptionalInt(getText("priceMin")),
    priceMax: parseOptionalInt(getText("priceMax")),
    categories: parseCommaList(getText("categories")),
    sizes: parseCommaList(getText("sizes")),
    colors: parseCommaList(getText("colors")),
    inStockOnly: formData.get("inStockOnly") === "on" ? true : undefined,
  };
  const hasFilters = Object.values(filters).some((value) => value !== undefined);

  return {
    merchant: getText("merchant"),
    query: getText("query"),
    startPage: parseOptionalInt(getText("startPage")) ?? 1,
    pageCount: parseOptionalInt(getText("pageCount")) ?? 1,
    limit: parseOptionalInt(getText("limit")),
    locale: getText("locale") || undefined,
    currency: getText("currency") || undefined,
    filters: hasFilters ? filters : undefined,
  };
}

/**
 * handleCollectionTaskCreateRequest는 새 수집 요청 입력을 Product Backend 여러 페이지 수집 작업
 * 등록 API(`/collection-tasks/pages`)에 전달한다.
 *
 * 일반 `<form>` 제출(application/x-www-form-urlencoded)일 때는 브라우저의 fetch/XHR을 거치지 않도록
 * 결과를 쿼리 파라미터로 담은 진짜 페이지 이동(redirect)으로 응답하고, JSON 요청일 때는 기존처럼
 * JSON을 그대로 반환한다. fetcher 주입은 실제 네트워크 없이 정상/실패 경로를 테스트하기 위한 경계다.
 */
export async function handleCollectionTaskCreateRequest(
  request: Request,
  fetcher: Fetcher = fetch,
): Promise<Response> {
  const contentType = request.headers.get("content-type") ?? "";
  const isFormSubmit = contentType.includes("application/x-www-form-urlencoded")
    || contentType.includes("multipart/form-data");

  let body: unknown;
  try {
    body = isFormSubmit ? buildPayloadFromFormData(await request.formData()) : await request.json();
  } catch {
    return Response.json(
      { code: "INVALID_REQUEST_BODY", message: "요청 본문이 올바른 형식이 아닙니다." },
      { status: 400 },
    );
  }

  const backendUrl = process.env.PRODUCT_BACKEND_BASE_URL?.replace(/\/$/, "") ?? DEFAULT_BACKEND_URL;

  try {
    const backendResponse = await fetcher(`${backendUrl}/internal/v1/collection-tasks/pages`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(getTimeoutMs()),
    });

    const responseBody = (await backendResponse.json().catch(() => null)) as
      | { code?: string; jobId?: string; message?: string }
      | null;

    if (!backendResponse.ok) {
      const message = responseBody?.message ?? "상품 서버가 수집 요청을 처리하지 못했습니다.";
      if (isFormSubmit) {
        return redirectTo(`/admin/collections/new?error=${encodeURIComponent(message)}`);
      }
      return Response.json(
        { code: responseBody?.code ?? "PRODUCT_BACKEND_ERROR", message },
        { status: backendResponse.status },
      );
    }

    if (isFormSubmit) {
      return redirectTo(`/admin/collections/new?created=${encodeURIComponent(responseBody?.jobId ?? "")}`);
    }
    return Response.json(responseBody, { status: backendResponse.status });
  } catch {
    if (isFormSubmit) {
      return redirectTo(`/admin/collections/new?error=${encodeURIComponent("상품 서버에 연결할 수 없습니다.")}`);
    }
    return Response.json(
      { code: "PRODUCT_BACKEND_UNAVAILABLE", message: "상품 서버에 연결할 수 없습니다." },
      { status: 503 },
    );
  }
}
