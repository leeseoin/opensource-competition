import assert from "node:assert/strict";
import test from "node:test";

import type { ResearchMcpOperations } from "../../lib/research-mcp-client.ts";
import type { AgentRunResponse } from "../../lib/research-session.ts";
import { handleAdvanceAgentRun, handleVerifyAgentRun } from "./handler.ts";

const runId = "00000000-0000-4000-8000-000000000002";

/** Agent Run route 테스트에서 호출하지 않는 MCP method를 차단한다. */
function mcpStub(overrides: Partial<ResearchMcpOperations>): ResearchMcpOperations {
  return {
    createSession: async () => { throw new Error("unexpected create"); },
    confirmSession: async () => { throw new Error("unexpected confirm"); },
    searchCandidates: async () => { throw new Error("unexpected search"); },
    startAgentRun: async () => { throw new Error("unexpected start"); },
    getAgentRun: async () => { throw new Error("unexpected get"); },
    advanceAgentRun: async () => { throw new Error("unexpected advance"); },
    verifyAgentRunOffer: async () => { throw new Error("unexpected verify"); },
    ...overrides,
  };
}

/** 테스트에서 사용할 최소 Agent Run 응답을 생성한다. */
function run(status: AgentRunResponse["status"]): AgentRunResponse {
  return {
    runId,
    sessionId: "00000000-0000-4000-8000-000000000001",
    status,
    research: null,
    collectionJobs: [],
    verification: null,
    events: [],
    error: null,
    nextAction: status === "COLLECTING" ? "POLL_ADVANCE" : "NONE",
  };
}

/** 유효한 실행 ID를 MCP 진행 도구에 전달하는지 검증한다. */
test("Agent Run을 한 단계 진행한다", async () => {
  const response = await handleAdvanceAgentRun(runId, mcpStub({
    advanceAgentRun: async (received) => {
      assert.equal(received, runId);
      return run("COLLECTING");
    },
  }));

  assert.equal(response.status, 200);
  assert.equal((await response.json()).status, "COLLECTING");
});

/** 선택 상품 ID를 Agent Run 재검증 도구에 전달하는지 검증한다. */
test("Agent Run 선택 상품을 재검증한다", async () => {
  const response = await handleVerifyAgentRun(runId, new Request("http://localhost", {
    method: "POST",
    body: JSON.stringify({ productId: 11 }),
  }), mcpStub({
    verifyAgentRunOffer: async (receivedRunId, productId) => {
      assert.equal(receivedRunId, runId);
      assert.equal(productId, 11);
      return run("VERIFYING");
    },
  }));

  assert.equal(response.status, 200);
  assert.equal((await response.json()).status, "VERIFYING");
});

/** 잘못된 상품 ID는 MCP 호출 전에 거부하는지 검증한다. */
test("잘못된 재검증 상품 ID를 차단한다", async () => {
  const response = await handleVerifyAgentRun(runId, new Request("http://localhost", {
    method: "POST",
    body: JSON.stringify({ productId: 0 }),
  }), mcpStub({}));

  assert.equal(response.status, 400);
});
