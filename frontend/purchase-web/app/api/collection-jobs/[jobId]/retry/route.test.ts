import assert from "node:assert/strict";
import test from "node:test";

import { handleCollectionJobRetryRequest } from "./handler.ts";

/** jobId를 backend 재실행 API 경로에 그대로 담아 호출하는지 검증한다. */
test("jobId를 backend 재실행 API로 전달한다", async () => {
  let requestedUrl = "";
  let method = "";
  const fetcher: typeof fetch = async (input, init) => {
    requestedUrl = String(input);
    method = String(init?.method);
    return Response.json(
      { jobId: "job-2", status: "QUEUED", merchant: "29cm", operation: "search", startPage: 1, endPage: 1, taskCount: 1 },
      { status: 202 },
    );
  };

  const response = await handleCollectionJobRetryRequest("job-original", false, fetcher);

  assert.equal(response.status, 202);
  assert.equal((await response.json()).jobId, "job-2");
  assert.equal(requestedUrl, "http://127.0.0.1:8080/internal/v1/collection-jobs/job-original/retry");
  assert.equal(method, "POST");
});

/** 재실행할 수 없는 상태의 backend 400 오류를 그대로 전달하는지 검증한다. */
test("backend의 400 오류를 그대로 전달한다", async () => {
  const fetcher: typeof fetch = async () =>
    Response.json({ code: "INVALID_COLLECTION_TASK", message: "재실행 불가" }, { status: 400 });

  const response = await handleCollectionJobRetryRequest("job-completed", false, fetcher);

  assert.equal(response.status, 400);
  assert.equal((await response.json()).code, "INVALID_COLLECTION_TASK");
});

/** 존재하지 않는 jobId의 backend 404 오류를 그대로 전달하는지 검증한다. */
test("backend의 404 오류를 그대로 전달한다", async () => {
  const fetcher: typeof fetch = async () =>
    Response.json({ code: "COLLECTION_JOB_NOT_FOUND", message: "없음" }, { status: 404 });

  const response = await handleCollectionJobRetryRequest("job-missing", false, fetcher);

  assert.equal(response.status, 404);
  assert.equal((await response.json()).code, "COLLECTION_JOB_NOT_FOUND");
});

/** Product Backend 연결 실패를 503으로 변환하는지 검증한다. */
test("상품 서버 연결 실패를 503으로 변환한다", async () => {
  const fetcher: typeof fetch = async () => {
    throw new Error("connection refused");
  };

  const response = await handleCollectionJobRetryRequest("job-original", false, fetcher);

  assert.equal(response.status, 503);
});

/** 일반 form 제출(text/html Accept)에는 fetch/XHR 없이 진짜 페이지 이동(303)으로 응답하는지 검증한다. */
test("acceptsHtml이 true면 성공 시 303으로 대시보드에 jobId를 담아 되돌린다", async () => {
  const fetcher: typeof fetch = async () =>
    Response.json({ jobId: "job-new", status: "QUEUED" }, { status: 202 });

  const response = await handleCollectionJobRetryRequest("job-original", true, fetcher);

  assert.equal(response.status, 303);
  assert.equal(response.headers.get("location"), "/admin/collections?retried=job-new");
});

/** acceptsHtml이 true면 실패도 303 redirect로 오류 메시지를 담아 되돌리는지 검증한다. */
test("acceptsHtml이 true면 실패 시 303으로 오류 메시지를 담아 되돌린다", async () => {
  const fetcher: typeof fetch = async () =>
    Response.json({ code: "INVALID_COLLECTION_TASK", message: "재실행 불가" }, { status: 400 });

  const response = await handleCollectionJobRetryRequest("job-completed", true, fetcher);

  assert.equal(response.status, 303);
  assert.equal(response.headers.get("location"), "/admin/collections?retryError=%EC%9E%AC%EC%8B%A4%ED%96%89%20%EB%B6%88%EA%B0%80");
});
