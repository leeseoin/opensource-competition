import assert from "node:assert/strict";
import test from "node:test";

import { AgentRuntimeError } from "./agent-runtime.ts";
import {
  classifyClaudeProcessFailure,
  resolveClaudeCommand,
  structurePurchaseQuestionWithClaude,
} from "./claude-runtime.ts";

const validCondition = {
  productType: { value: "구두", priority: "required" },
  usage: [{ value: "출근", priority: "preferred" }],
  price: { min: null, max: 100000, currency: "KRW", priority: "required" },
  colors: [{ value: "검정", priority: "preferred" }],
  sizes: [{ value: "270", priority: "required" }],
  requirements: [],
  attributes: [],
  merchant: null,
  missingConditions: [],
  assumptions: [],
  confidence: 0.9,
  requiresConfirmation: true,
};

/** Claude CLI에 도구 차단과 비대화형 JSON Schema 설정이 포함되는지 검증한다. */
test("Claude를 도구 없는 구조화 출력 모드로 실행한다", async () => {
  let receivedArgs: string[] = [];
  let receivedPrompt = "";
  const result = await structurePurchaseQuestionWithClaude("검정 구두를 찾아줘", async (args, input) => {
    receivedArgs = args;
    receivedPrompt = input;
    return JSON.stringify({ type: "result", structured_output: validCondition });
  });

  assert.equal(result.productType.value, "구두");
  assert.ok(receivedArgs.includes("--json-schema"));
  const schema = JSON.parse(receivedArgs[receivedArgs.indexOf("--json-schema") + 1]) as Record<string, unknown>;
  assert.equal(schema.$schema, undefined);
  assert.ok(receivedArgs.includes("--no-session-persistence"));
  assert.equal(receivedArgs[receivedArgs.indexOf("--tools") + 1], "");
  assert.match(receivedPrompt, /Purchase Research/);
  assert.match(receivedPrompt, /검정 구두를 찾아줘/);
});

/** Claude CLI 경로가 비어 있으면 PATH 기본 명령으로 복구하는지 검증한다. */
test("빈 Claude 실행 경로에 기본 명령을 사용한다", () => {
  assert.equal(resolveClaudeCommand(""), "claude");
  assert.equal(resolveClaudeCommand("  "), "claude");
  assert.equal(resolveClaudeCommand("/usr/local/bin/claude"), "/usr/local/bin/claude");
});

/** Claude 인증 실패에서 stderr 원문을 노출하지 않는지 검증한다. */
test("Claude 인증 실패를 안전한 안내로 변환한다", () => {
  const error = classifyClaudeProcessFailure("private prompt: not logged in; please run /login");

  assert.equal(error.code, "AI_AUTH_REQUIRED");
  assert.doesNotMatch(error.message, /private prompt/);
});

/** Claude envelope의 Schema 불일치 결과를 계약 오류로 거절하는지 검증한다. */
test("Claude의 Schema 불일치 응답을 거절한다", async () => {
  await assert.rejects(
    structurePurchaseQuestionWithClaude("구두", async () => JSON.stringify({
      type: "result",
      structured_output: { productType: "구두" },
    })),
    (error: unknown) => error instanceof AgentRuntimeError && error.code === "AI_OUTPUT_INVALID",
  );
});
