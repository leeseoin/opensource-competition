import assert from "node:assert/strict";
import test from "node:test";

import { CodexRuntimeError } from "../../../lib/codex-runtime.ts";
import { ResearchMcpError, type ResearchMcpOperations } from "../../../lib/research-mcp-client.ts";
import type { PurchaseCondition, ResearchSessionResponse } from "../../../lib/research-session.ts";
import { handleConditionsRequest } from "./handler.ts";

export const testConditions: PurchaseCondition = {
  productType: "구두",
  usage: ["출근"],
  price: { min: null, max: 100000, currency: "KRW" },
  colors: ["검정"],
  sizes: ["270"],
  requirements: ["편안함"],
  merchant: null,
  missingConditions: [],
  assumptions: [],
  confidence: 0.91,
  requiresConfirmation: true,
};

/** 테스트용 조사 세션 응답을 생성한다. */
export function sessionResponse(conditions = testConditions): ResearchSessionResponse {
  return {
    sessionId: "00000000-0000-4000-8000-000000000001",
    question: "출근용 검정 구두",
    runtime: "codex",
    pluginId: "purchase-research-agent",
    status: "DRAFT",
    conditions,
    confirmedAt: null,
    result: null,
  };
}

/** 호출하지 않는 MCP method를 명시적으로 실패시키는 테스트 대역을 생성한다. */
export function mcpStub(overrides: Partial<ResearchMcpOperations> = {}): ResearchMcpOperations {
  return {
    createSession: async (_question, conditions) => sessionResponse(conditions),
    confirmSession: async () => { throw new Error("unexpected confirm"); },
    searchCandidates: async () => { throw new Error("unexpected search"); },
    ...overrides,
  };
}

/** Codex 결과를 DRAFT로 저장하되 상품 검색은 수행하지 않는지 검증한다. */
test("Codex 조건을 MCP DRAFT 세션으로 저장한다", async () => {
  let structuredQuestion = "";
  let searched = false;
  const response = await handleConditionsRequest(new Request("http://localhost/api/research/conditions", {
    method: "POST",
    body: JSON.stringify({ question: "출근용 검정 구두", runtime: "codex" }),
  }), {
    structure: async (question) => {
      structuredQuestion = question;
      return testConditions;
    },
    mcp: mcpStub({ searchCandidates: async () => {
      searched = true;
      return sessionResponse();
    } }),
  });

  assert.equal(response.status, 200);
  assert.equal(structuredQuestion, "출근용 검정 구두");
  assert.equal(searched, false);
  assert.equal((await response.json()).status, "DRAFT");
});

/** 조건이 부족하면 missingConditions를 유지한 DRAFT를 반환하는지 검증한다. */
test("부족한 구매 조건을 사용자 확인 대상으로 반환한다", async () => {
  const missing = { ...testConditions, missingConditions: ["사이즈"] };
  const response = await handleConditionsRequest(new Request("http://localhost/api/research/conditions", {
    method: "POST",
    body: JSON.stringify({ question: "출근용 구두", runtime: "codex" }),
  }), { structure: async () => missing, mcp: mcpStub() });

  assert.equal(response.status, 200);
  assert.deepEqual((await response.json()).conditions.missingConditions, ["사이즈"]);
});

/** Schema와 다른 Codex 결과를 browser에 502로 반환하는지 검증한다. */
test("잘못된 AI JSON을 502로 변환한다", async () => {
  const response = await handleConditionsRequest(new Request("http://localhost/api/research/conditions", {
    method: "POST",
    body: JSON.stringify({ question: "구두", runtime: "codex" }),
  }), {
    structure: async () => { throw new CodexRuntimeError("AI_OUTPUT_INVALID", "invalid"); },
    mcp: mcpStub(),
  });
  assert.equal(response.status, 502);
  assert.equal((await response.json()).code, "AI_OUTPUT_INVALID");
});

/** Codex CLI 실행 실패를 browser에 503으로 반환하는지 검증한다. */
test("AI 실행 실패를 503으로 변환한다", async () => {
  const response = await handleConditionsRequest(new Request("http://localhost/api/research/conditions", {
    method: "POST",
    body: JSON.stringify({ question: "구두", runtime: "codex" }),
  }), {
    structure: async () => { throw new CodexRuntimeError("AI_UNAVAILABLE", "offline"); },
    mcp: mcpStub(),
  });
  assert.equal(response.status, 503);
  assert.equal((await response.json()).code, "AI_UNAVAILABLE");
});

/** MCP 연결 실패를 browser에 503으로 반환하는지 검증한다. */
test("MCP 연결 실패를 503으로 변환한다", async () => {
  const response = await handleConditionsRequest(new Request("http://localhost/api/research/conditions", {
    method: "POST",
    body: JSON.stringify({ question: "구두", runtime: "codex" }),
  }), {
    structure: async () => testConditions,
    mcp: mcpStub({ createSession: async () => { throw new ResearchMcpError("offline"); } }),
  });
  assert.equal(response.status, 503);
  assert.equal((await response.json()).code, "MCP_UNAVAILABLE");
});
