import assert from "node:assert/strict";
import test from "node:test";

import type { AgentRunResponse, PurchaseCondition, ResearchSessionResponse } from "../../../lib/research-session.ts";
import type { ResearchMcpOperations } from "../../../lib/research-mcp-client.ts";
import { handleConfirmRequest } from "./handler.ts";

const sessionId = "00000000-0000-4000-8000-000000000001";
const testConditions: PurchaseCondition = {
  productType: { value: "구두", priority: "required" },
  usage: [{ value: "출근", priority: "preferred" }],
  price: { min: null, max: 100000, currency: "KRW", priority: "required" },
  colors: [{ value: "검정", priority: "preferred" }],
  sizes: [{ value: "270", priority: "required" }],
  requirements: [{ value: "편안함", priority: "preferred" }],
  merchant: null,
  missingConditions: [],
  assumptions: [],
  confidence: 0.91,
  requiresConfirmation: true,
};

/** 확인 Route 테스트에 사용할 DRAFT 세션 응답을 생성한다. */
function sessionResponse(): ResearchSessionResponse {
  return {
    sessionId,
    question: "출근용 검정 구두",
    runtime: "codex",
    pluginId: "purchase-research-agent",
    status: "DRAFT",
    conditions: testConditions,
    confirmedAt: null,
    result: null,
  };
}

/** 확인 Route에서 필요한 MCP method를 선택적으로 교체하는 테스트 대역을 생성한다. */
function mcpStub(overrides: Partial<ResearchMcpOperations>): ResearchMcpOperations {
  return {
    createSession: async () => { throw new Error("unexpected create"); },
    confirmSession: async () => { throw new Error("unexpected confirm"); },
    searchCandidates: async () => { throw new Error("unexpected search"); },
    startAgentRun: async () => { throw new Error("unexpected run start"); },
    getAgentRun: async () => { throw new Error("unexpected run get"); },
    advanceAgentRun: async () => { throw new Error("unexpected run advance"); },
    verifyAgentRunOffer: async () => { throw new Error("unexpected verify"); },
    ...overrides,
  };
}

/** 미확정 조건이 남은 요청은 MCP를 호출하기 전에 차단하는지 검증한다. */
test("확인하지 않은 조건 검색을 차단한다", async () => {
  let confirmed = false;
  const response = await handleConfirmRequest(new Request("http://localhost/api/research/confirm", {
    method: "POST",
    body: JSON.stringify({
      sessionId,
      conditions: { ...testConditions, missingConditions: ["사이즈"] },
    }),
  }), mcpStub({ confirmSession: async () => {
    confirmed = true;
    return sessionResponse();
  } }));
  assert.equal(response.status, 409);
  assert.equal(confirmed, false);
});

/** 사용자 확인 뒤 MCP 확인과 Agent Run 시작을 순서대로 실행하는지 검증한다. */
test("확정 조건으로 MCP Agent Run을 시작한다", async () => {
  const calls: string[] = [];
  const run: AgentRunResponse = {
    runId: "00000000-0000-4000-8000-000000000002",
    sessionId,
    status: "COLLECTING",
    research: null,
    collectionJobs: [],
    verification: null,
    events: [],
    error: null,
    nextAction: "POLL_ADVANCE",
  };
  const response = await handleConfirmRequest(new Request("http://localhost/api/research/confirm", {
    method: "POST",
    body: JSON.stringify({ sessionId, conditions: testConditions }),
  }), mcpStub({
    confirmSession: async () => {
      calls.push("confirm");
      return { ...sessionResponse(), status: "CONFIRMED" };
    },
    startAgentRun: async () => {
      calls.push("start");
      return run;
    },
  }));
  assert.equal(response.status, 200);
  assert.deepEqual(calls, ["confirm", "start"]);
  assert.equal((await response.json()).status, "COLLECTING");
});
