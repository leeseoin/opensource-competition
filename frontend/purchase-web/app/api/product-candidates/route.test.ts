import assert from "node:assert/strict";
import test from "node:test";

import { handleProductCandidateRequest } from "./handler.ts";

/** 정상 요청이 Product Backend 응답을 browser에 전달하는지 검증한다. */
test("상품 후보 정상 응답을 전달한다", async () => {
  const fetcher: typeof fetch = async () => Response.json({
    question: "검정 구두를 찾아줘",
    query: "구두",
    totalCount: 1,
    hasNext: false,
    candidates: [{ externalId: "1010110882" }],
  });
  const response = await handleProductCandidateRequest(new Request("http://localhost/api/product-candidates", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question: "검정 구두를 찾아줘", query: "구두", limit: 3 }),
  }), fetcher);

  assert.equal(response.status, 200);
  assert.equal((await response.json()).candidates[0].externalId, "1010110882");
});

/** 검색어가 비어 있는 요청을 Product Backend 호출 전에 거절하는지 검증한다. */
test("빈 검색어 요청을 거절한다", async () => {
  let called = false;
  const fetcher: typeof fetch = async () => {
    called = true;
    return Response.json({});
  };
  const response = await handleProductCandidateRequest(new Request("http://localhost/api/product-candidates", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question: "검정 구두", query: " " }),
  }), fetcher);

  assert.equal(response.status, 400);
  assert.equal(called, false);
});

/** 후보를 3개보다 많이 요청하면 Product Backend 호출 전에 거절하는지 검증한다. */
test("후보 4개 요청을 거절한다", async () => {
  let called = false;
  const fetcher: typeof fetch = async () => {
    called = true;
    return Response.json({});
  };
  const response = await handleProductCandidateRequest(new Request("http://localhost/api/product-candidates", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question: "검정 구두", query: "구두", limit: 4 }),
  }), fetcher);

  assert.equal(response.status, 400);
  assert.equal(called, false);
});

/** Product Backend가 오류를 반환하면 browser에 502 경계를 제공하는지 검증한다. */
test("상품 서버 처리 오류를 502로 변환한다", async () => {
  const fetcher: typeof fetch = async () => new Response(null, { status: 500 });
  const response = await handleProductCandidateRequest(new Request("http://localhost/api/product-candidates", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question: "검정 구두", query: "구두" }),
  }), fetcher);

  assert.equal(response.status, 502);
});

/** Product Backend 연결이 실패하면 browser에 503 경계를 제공하는지 검증한다. */
test("상품 서버 연결 실패를 503으로 변환한다", async () => {
  const fetcher: typeof fetch = async () => {
    throw new Error("connection refused");
  };
  const response = await handleProductCandidateRequest(new Request("http://localhost/api/product-candidates", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question: "검정 구두", query: "구두" }),
  }), fetcher);

  assert.equal(response.status, 503);
});
