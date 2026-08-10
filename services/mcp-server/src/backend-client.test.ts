import assert from "node:assert/strict";
import test from "node:test";

import { BackendRequestError, ProductBackendClient, type PurchaseCondition } from "./backend-client.js";

const conditions: PurchaseCondition = {
  productType: { value: "구두", priority: "required" },
  usage: [{ value: "출근", priority: "preferred" }],
  price: { min: null, max: 100000, currency: "KRW", priority: "required" },
  colors: [{ value: "검정", priority: "preferred" }],
  sizes: [{ value: "270", priority: "required" }],
  requirements: [{ value: "편안함", priority: "preferred" }],
  merchant: null,
  missingConditions: [],
  assumptions: [],
  confidence: 0.9,
  requiresConfirmation: true,
};

/** DRAFT 조사 세션 요청이 runtime과 Plugin 식별자를 강제로 포함하는지 검증한다. */
test("DRAFT 조사 세션 생성 요청을 Product Backend에 전달한다", async () => {
  let requestBody = "";
  const client = new ProductBackendClient("http://backend", async (_input, init) => {
    requestBody = String(init?.body);
    return Response.json({ sessionId: "a", status: "DRAFT" });
  });

  await client.createSession("검정 구두를 찾아줘", conditions);
  assert.deepEqual(JSON.parse(requestBody), {
    question: "검정 구두를 찾아줘",
    runtime: "codex",
    pluginId: "purchase-research-agent",
    conditions,
  });
});

/** 미확정 검색 등 Product Backend 오류를 상태 코드와 함께 보존하는지 검증한다. */
test("Product Backend 상태 오류를 MCP 오류로 변환한다", async () => {
  const client = new ProductBackendClient("http://backend", async () => new Response(
    JSON.stringify({ code: "RESEARCH_SESSION_NOT_READY" }),
    { status: 409 },
  ));

  await assert.rejects(
    client.searchCandidates("00000000-0000-4000-8000-000000000000"),
    (error: unknown) => error instanceof BackendRequestError && error.status === 409,
  );
});

/** 상품 상세/근거/비교 도구가 정해진 REST 경계만 호출하는지 검증한다. */
test("상품 조사 도구 요청을 Product Backend REST API에 전달한다", async () => {
  const requests: Array<{ url: string; method: string; body?: string }> = [];
  const client = new ProductBackendClient("http://backend", async (input, init) => {
    requests.push({ url: String(input), method: init?.method ?? "GET", body: init?.body?.toString() });
    return Response.json({ ok: true });
  });

  await client.getProduct(11);
  await client.getEvidence(11);
  await client.compareProducts([11, 12]);

  assert.deepEqual(requests, [
    { url: "http://backend/internal/v1/products/11", method: "GET", body: undefined },
    { url: "http://backend/internal/v1/products/11/evidence", method: "GET", body: undefined },
    {
      url: "http://backend/internal/v1/product-comparisons",
      method: "POST",
      body: JSON.stringify({ productIds: [11, 12] }),
    },
  ]);
});
