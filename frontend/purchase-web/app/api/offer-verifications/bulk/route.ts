import { handleBulkOfferVerificationRequest } from "./handler";

/** 폼 데이터 또는 JSON 본문에서 재검증할 상품 ID 목록을 안전한 정수로만 추출한다. */
async function readProductIds(request: Request): Promise<number[]> {
  const contentType = request.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    const body = (await request.json().catch(() => null)) as { productIds?: unknown } | null;
    const raw = Array.isArray(body?.productIds) ? body.productIds : [];
    return raw.map(Number).filter((id) => Number.isInteger(id) && id > 0);
  }

  const formData = await request.formData();
  return formData
    .getAll("productIds")
    .map((value) => Number(value))
    .filter((id) => Number.isInteger(id) && id > 0);
}

/** POST는 상품 재검증 화면의 선택 재검증 버튼 클릭을 서버 전용 Product Backend 일괄 재검증 API로 전달한다. */
export async function POST(request: Request): Promise<Response> {
  const acceptsHtml = request.headers.get("accept")?.includes("text/html") ?? false;
  const productIds = await readProductIds(request);
  return handleBulkOfferVerificationRequest(productIds, acceptsHtml);
}
