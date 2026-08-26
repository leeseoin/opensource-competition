import assert from "node:assert/strict";
import test from "node:test";

import { handleCollectionTaskCreateRequest } from "./handler.ts";

/** 정상 요청 본문을 backend 여러 페이지 등록 API로 그대로 전달하는지 검증한다. */
test("새 수집 요청 본문을 backend pages API로 전달한다", async () => {
  let requestedUrl = "";
  let requestedBody: unknown = null;
  const fetcher: typeof fetch = async (input, init) => {
    requestedUrl = String(input);
    requestedBody = JSON.parse(String(init?.body));
    return Response.json(
      { jobId: "job-1", status: "QUEUED", merchant: "29cm", operation: "search", startPage: 1, endPage: 1, taskCount: 1 },
      { status: 202 },
    );
  };
  const request = new Request("http://localhost/api/collection-tasks", {
    method: "POST",
    body: JSON.stringify({ merchant: "29cm", query: "구두", startPage: 1, pageCount: 1 }),
  });

  const response = await handleCollectionTaskCreateRequest(request, fetcher);

  assert.equal(response.status, 202);
  assert.equal((await response.json()).jobId, "job-1");
  assert.equal(requestedUrl, "http://127.0.0.1:8080/internal/v1/collection-tasks/pages");
  assert.deepEqual(requestedBody, { merchant: "29cm", query: "구두", startPage: 1, pageCount: 1 });
});

/** 올바르지 않은 JSON 본문을 400으로 거절하고 backend를 호출하지 않는지 검증한다. */
test("잘못된 JSON 본문을 400으로 거절한다", async () => {
  let called = false;
  const fetcher: typeof fetch = async () => {
    called = true;
    return Response.json({});
  };
  const request = new Request("http://localhost/api/collection-tasks", {
    method: "POST",
    body: "not-json",
  });

  const response = await handleCollectionTaskCreateRequest(request, fetcher);

  assert.equal(response.status, 400);
  assert.equal(called, false);
});

/** backend 검증 실패를 그대로 400과 원본 오류 코드로 전달하는지 검증한다. */
test("backend의 400 검증 오류를 그대로 전달한다", async () => {
  const fetcher: typeof fetch = async () =>
    Response.json({ code: "INVALID_COLLECTION_TASK", message: "잘못된 조건" }, { status: 400 });
  const request = new Request("http://localhost/api/collection-tasks", {
    method: "POST",
    body: JSON.stringify({ merchant: "29cm", query: "구두" }),
  });

  const response = await handleCollectionTaskCreateRequest(request, fetcher);

  assert.equal(response.status, 400);
  assert.equal((await response.json()).code, "INVALID_COLLECTION_TASK");
});

/** Product Backend 연결 실패를 503으로 변환하는지 검증한다. */
test("상품 서버 연결 실패를 503으로 변환한다", async () => {
  const fetcher: typeof fetch = async () => {
    throw new Error("connection refused");
  };
  const request = new Request("http://localhost/api/collection-tasks", {
    method: "POST",
    body: JSON.stringify({ merchant: "29cm", query: "구두" }),
  });

  const response = await handleCollectionTaskCreateRequest(request, fetcher);

  assert.equal(response.status, 503);
});

/** 일반 `<form>` 제출(x-www-form-urlencoded)을 backend JSON 조건으로 변환해 전달하는지 검증한다. */
test("form 제출 값을 backend JSON 조건으로 변환해 전달한다", async () => {
  let requestedBody: unknown = null;
  const fetcher: typeof fetch = async (_input, init) => {
    requestedBody = JSON.parse(String(init?.body));
    return Response.json({ jobId: "job-form-1", status: "QUEUED" }, { status: 202 });
  };
  const formBody = new URLSearchParams({
    merchant: "29cm",
    query: "구두",
    startPage: "2",
    pageCount: "3",
    limit: "10",
    sizes: "260, 270",
    inStockOnly: "on",
  });
  const request = new Request("http://localhost/api/collection-tasks", { method: "POST", body: formBody });

  const response = await handleCollectionTaskCreateRequest(request, fetcher);

  assert.equal(response.status, 303);
  assert.equal(response.headers.get("location"), "/admin/collections/new?created=job-form-1");
  assert.deepEqual(requestedBody, {
    merchant: "29cm",
    query: "구두",
    startPage: 2,
    pageCount: 3,
    limit: 10,
    filters: { sizes: ["260", "270"], inStockOnly: true },
  });
});

/** form 제출이 backend 검증에 실패하면 오류 메시지를 담아 폼 화면으로 되돌리는지 검증한다. */
test("form 제출이 backend 검증에 실패하면 오류 메시지를 담아 되돌린다", async () => {
  const fetcher: typeof fetch = async () =>
    Response.json({ code: "INVALID_COLLECTION_TASK", message: "잘못된 조건" }, { status: 400 });
  const formBody = new URLSearchParams({ merchant: "29cm", query: "구두" });
  const request = new Request("http://localhost/api/collection-tasks", { method: "POST", body: formBody });

  const response = await handleCollectionTaskCreateRequest(request, fetcher);

  assert.equal(response.status, 303);
  assert.equal(response.headers.get("location"), "/admin/collections/new?error=%EC%9E%98%EB%AA%BB%EB%90%9C%20%EC%A1%B0%EA%B1%B4");
});
