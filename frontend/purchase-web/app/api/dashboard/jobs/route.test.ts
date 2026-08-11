import assert from "node:assert/strict";
import test from "node:test";

import { handleCollectionJobsRequest } from "./handler.ts";

/** 정상 응답을 전달하고 merchant/status/page/size를 backend에 전달하는지 검증한다. */
test("job 목록 정상 응답을 전달하고 필터/페이지 파라미터를 backend에 전달한다", async () => {
  let requestedUrl = "";
  const fetcher: typeof fetch = async (input) => {
    requestedUrl = String(input);
    return Response.json({ totalCount: 1, hasNext: false, items: [{ jobId: "job-1" }] });
  };
  const response = await handleCollectionJobsRequest(
    new Request("http://localhost/api/dashboard/jobs?merchant=abcmart&status=COMPLETED&page=1&size=10"),
    fetcher,
  );

  assert.equal(response.status, 200);
  assert.equal((await response.json()).items[0].jobId, "job-1");
  assert.match(requestedUrl, /merchant=abcmart/);
  assert.match(requestedUrl, /status=COMPLETED/);
  assert.match(requestedUrl, /page=1/);
  assert.match(requestedUrl, /size=10/);
});

/** 필터 없이 요청해도 backend를 호출하는지 검증한다. */
test("필터 없이 요청해도 backend를 호출한다", async () => {
  let calledUrl = "";
  const fetcher: typeof fetch = async (input) => {
    calledUrl = String(input);
    return Response.json({ totalCount: 0, hasNext: false, items: [] });
  };
  const response = await handleCollectionJobsRequest(new Request("http://localhost/api/dashboard/jobs"), fetcher);

  assert.equal(response.status, 200);
  assert.equal(calledUrl, "http://127.0.0.1:8080/internal/v1/collection-jobs");
});

/** Product Backend 처리 오류를 502로 변환하는지 검증한다. */
test("상품 서버 처리 오류를 502로 변환한다", async () => {
  const fetcher: typeof fetch = async () => new Response(null, { status: 500 });
  const response = await handleCollectionJobsRequest(new Request("http://localhost/api/dashboard/jobs"), fetcher);

  assert.equal(response.status, 502);
});

/** Product Backend 연결 실패를 503으로 변환하는지 검증한다. */
test("상품 서버 연결 실패를 503으로 변환한다", async () => {
  const fetcher: typeof fetch = async () => {
    throw new Error("connection refused");
  };
  const response = await handleCollectionJobsRequest(new Request("http://localhost/api/dashboard/jobs"), fetcher);

  assert.equal(response.status, 503);
});
