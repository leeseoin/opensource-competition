import assert from "node:assert/strict";
import test from "node:test";

import { handleBulkOfferVerificationRequest } from "./handler.ts";

/** 선택한 상품 ID 목록을 backend 일괄 재검증 API에 JSON으로 전달하는지 검증한다. */
test("상품 ID 목록을 backend 일괄 재검증 API로 전달한다", async () => {
  let requestedUrl = "";
  let method = "";
  let body: unknown = null;
  const fetcher: typeof fetch = async (input, init) => {
    requestedUrl = String(input);
    method = String(init?.method);
    body = JSON.parse(String(init?.body));
    return Response.json(
      { batchId: "batch-1", status: "PROCESSING", requestedCount: 2, processedCount: 0, queuedCount: 0, results: [] },
      { status: 202 },
    );
  };

  const response = await handleBulkOfferVerificationRequest([11, 22], false, fetcher);

  assert.equal(response.status, 202);
  assert.equal((await response.json()).batchId, "batch-1");
  assert.equal(requestedUrl, "http://127.0.0.1:8080/internal/v1/offer-verifications/products/bulk");
  assert.equal(method, "POST");
  assert.deepEqual(body, { productIds: [11, 22] });
});

/** 상품을 하나도 선택하지 않으면 backend를 호출하지 않고 400을 반환하는지 검증한다. */
test("선택한 상품이 없으면 backend 호출 없이 400을 반환한다", async () => {
  let called = false;
  const fetcher: typeof fetch = async () => {
    called = true;
    return Response.json({}, { status: 200 });
  };

  const response = await handleBulkOfferVerificationRequest([], false, fetcher);

  assert.equal(response.status, 400);
  assert.equal(called, false);
});

/** backend 오류를 그대로 전달하는지 검증한다. */
test("backend 오류를 그대로 전달한다", async () => {
  const fetcher: typeof fetch = async () =>
    Response.json({ code: "INVALID_REQUEST", message: "상품 ID가 100개를 넘었습니다" }, { status: 400 });

  const response = await handleBulkOfferVerificationRequest([11], false, fetcher);

  assert.equal(response.status, 400);
  assert.equal((await response.json()).code, "INVALID_REQUEST");
});

/** Product Backend 연결 실패를 503으로 변환하는지 검증한다. */
test("상품 서버 연결 실패를 503으로 변환한다", async () => {
  const fetcher: typeof fetch = async () => {
    throw new Error("connection refused");
  };

  const response = await handleBulkOfferVerificationRequest([11], false, fetcher);

  assert.equal(response.status, 503);
});

/** 일반 form 제출(text/html Accept)에는 fetch/XHR 없이 진짜 페이지 이동(303)으로 응답하는지 검증한다. */
test("acceptsHtml이 true면 성공 시 303으로 상품 화면에 배치 ID를 담아 되돌린다", async () => {
  const fetcher: typeof fetch = async () =>
    Response.json(
      { batchId: "batch-42", status: "PROCESSING", requestedCount: 3, processedCount: 0, queuedCount: 0, results: [] },
      { status: 202 },
    );

  const response = await handleBulkOfferVerificationRequest([11, 22, 33], true, fetcher);

  assert.equal(response.status, 303);
  assert.equal(response.headers.get("location"), "/admin/collections/products?batchId=batch-42");
});

/** acceptsHtml이 true면 실패도 303 redirect로 오류 메시지를 담아 되돌리는지 검증한다. */
test("acceptsHtml이 true면 실패 시 303으로 오류 메시지를 담아 되돌린다", async () => {
  const fetcher: typeof fetch = async () =>
    Response.json({ code: "PRODUCT_NOT_FOUND", message: "상품을 찾을 수 없습니다" }, { status: 404 });

  const response = await handleBulkOfferVerificationRequest([11], true, fetcher);

  assert.equal(response.status, 303);
  assert.equal(
    response.headers.get("location"),
    `/admin/collections/products?error=${encodeURIComponent("상품을 찾을 수 없습니다")}`,
  );
});

/** acceptsHtml이 true면 상품을 하나도 선택하지 않아도 303 redirect로 안내하는지 검증한다. */
test("acceptsHtml이 true면 선택한 상품이 없어도 303으로 안내한다", async () => {
  const response = await handleBulkOfferVerificationRequest([], true, async () => Response.json({}));

  assert.equal(response.status, 303);
  assert.equal(
    response.headers.get("location"),
    `/admin/collections/products?error=${encodeURIComponent("선택한 상품이 없습니다.")}`,
  );
});
