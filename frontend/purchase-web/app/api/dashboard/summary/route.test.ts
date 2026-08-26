import assert from "node:assert/strict";
import test from "node:test";

import { handleDashboardSummaryRequest } from "./handler.ts";

/** 정상 응답을 그대로 browser에 전달하고 since/until을 backend에 전달하는지 검증한다. */
test("집계 정상 응답을 전달하고 since/until을 backend에 전달한다", async () => {
  let requestedUrl = "";
  const fetcher: typeof fetch = async (input) => {
    requestedUrl = String(input);
    return Response.json({
      window: { since: "2026-08-10T14:00:00Z", until: "2026-08-11T14:00:00Z" },
      jobs: { total: 1, byStatus: { COMPLETED: 1 } },
    });
  };
  const response = await handleDashboardSummaryRequest(
    new Request("http://localhost/api/dashboard/summary?since=2026-08-10T14:00:00Z&until=2026-08-11T14:00:00Z"),
    fetcher,
  );

  assert.equal(response.status, 200);
  assert.equal((await response.json()).jobs.total, 1);
  assert.match(requestedUrl, /since=2026-08-10T14%3A00%3A00Z/);
  assert.match(requestedUrl, /until=2026-08-11T14%3A00%3A00Z/);
});

/** since/until 없이 요청해도 backend에 그대로 전달하는지 검증한다. */
test("since/until 없이 요청해도 backend를 호출한다", async () => {
  let calledUrl = "";
  const fetcher: typeof fetch = async (input) => {
    calledUrl = String(input);
    return Response.json({ jobs: { total: 0, byStatus: {} } });
  };
  const response = await handleDashboardSummaryRequest(
    new Request("http://localhost/api/dashboard/summary"),
    fetcher,
  );

  assert.equal(response.status, 200);
  assert.equal(calledUrl.includes("since="), false);
  assert.equal(calledUrl.includes("until="), false);
});

/** Product Backend가 잘못된 시간 창을 400으로 반환하면 그대로 400을 전달하는지 검증한다. */
test("잘못된 시간 창 400 오류를 그대로 전달한다", async () => {
  const fetcher: typeof fetch = async () => Response.json(
    { code: "INVALID_DASHBOARD_WINDOW", message: "since는 until보다 이전이어야 합니다" },
    { status: 400 },
  );
  const response = await handleDashboardSummaryRequest(
    new Request("http://localhost/api/dashboard/summary?since=2026-08-11T14:00:00Z&until=2026-08-10T14:00:00Z"),
    fetcher,
  );

  assert.equal(response.status, 400);
  assert.equal((await response.json()).code, "INVALID_DASHBOARD_WINDOW");
});

/** Product Backend의 그 외 오류를 502로 변환하는지 검증한다. */
test("상품 서버 처리 오류를 502로 변환한다", async () => {
  const fetcher: typeof fetch = async () => new Response(null, { status: 500 });
  const response = await handleDashboardSummaryRequest(
    new Request("http://localhost/api/dashboard/summary"),
    fetcher,
  );

  assert.equal(response.status, 502);
});

/** Product Backend 연결이 실패하면 browser에 503 경계를 제공하는지 검증한다. */
test("상품 서버 연결 실패를 503으로 변환한다", async () => {
  const fetcher: typeof fetch = async () => {
    throw new Error("connection refused");
  };
  const response = await handleDashboardSummaryRequest(
    new Request("http://localhost/api/dashboard/summary"),
    fetcher,
  );

  assert.equal(response.status, 503);
});
