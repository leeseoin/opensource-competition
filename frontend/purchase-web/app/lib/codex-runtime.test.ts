import assert from "node:assert/strict";
import test from "node:test";

import {
  classifyCodexProcessFailure,
  CodexRuntimeError,
  resolveCodexCommand,
  structurePurchaseQuestion,
} from "./codex-runtime.ts";

const validCondition = {
  productType: "구두",
  usage: ["출근"],
  price: { min: null, max: 100000, currency: "KRW" },
  colors: ["검정"],
  sizes: ["270"],
  requirements: ["편안함"],
  merchant: null,
  missingConditions: [],
  assumptions: [],
  confidence: 0.9,
  requiresConfirmation: true,
};

/** Codex CLI 인자에 읽기 전용/비대화형/공통 Schema 설정이 포함되는지 검증한다. */
test("Codex를 읽기 전용 구조화 모드로 실행한다", async () => {
  let receivedArgs: string[] = [];
  let receivedPrompt = "";
  const result = await structurePurchaseQuestion("검정 구두를 찾아줘", async (args, input) => {
    receivedArgs = args;
    receivedPrompt = input;
    return JSON.stringify(validCondition);
  });

  assert.equal(result.productType, "구두");
  assert.ok(receivedArgs.includes("read-only"));
  assert.ok(receivedArgs.includes("--output-schema"));
  assert.match(receivedPrompt, /Purchase Research/);
  assert.match(receivedPrompt, /검정 구두를 찾아줘/);
});

/** 비어 있는 Codex 실행 경로는 PATH에서 찾는 기본 명령으로 복구하는지 검증한다. */
test("빈 Codex 실행 경로에 기본 명령을 사용한다", () => {
  assert.equal(resolveCodexCommand(""), "codex");
  assert.equal(resolveCodexCommand("  "), "codex");
  assert.equal(resolveCodexCommand("/opt/homebrew/bin/codex"), "/opt/homebrew/bin/codex");
});

/** 폐기된 OAuth token 오류를 프롬프트 비노출 인증 안내로 변환하는지 검증한다. */
test("Codex 인증 만료 오류에서 stderr를 노출하지 않는다", () => {
  const stderr = "비공개 Plugin 규칙 token_invalidated refresh_token_invalidated";
  const error = classifyCodexProcessFailure(stderr);

  assert.equal(error.code, "AI_AUTH_REQUIRED");
  assert.match(error.message, /codex logout/);
  assert.doesNotMatch(error.message, /비공개 Plugin 규칙/);
  assert.doesNotMatch(error.message, /token_invalidated/);
});

/** 일반 Codex process 실패도 원문 stderr 없이 서버 확인 안내만 반환하는지 검증한다. */
test("Codex 일반 실행 실패에서 stderr를 노출하지 않는다", () => {
  const error = classifyCodexProcessFailure("민감할 수 있는 process stderr");

  assert.equal(error.code, "AI_UNAVAILABLE");
  assert.doesNotMatch(error.message, /민감할 수 있는/);
});

/** Codex가 productType에 색상을 중복해도 DB 검색어에서는 상품 종류만 남기는지 검증한다. */
test("상품 종류에서 구조화된 색상 중복을 제거한다", async () => {
  const result = await structurePurchaseQuestion("검정 구두를 찾아줘", async () => JSON.stringify({
    ...validCondition,
    productType: "검정 구두",
  }));

  assert.equal(result.productType, "구두");
  assert.deepEqual(result.colors, ["검정"]);
});

/** JSON이 아닌 Codex 최종 응답을 계약 오류로 거절하는지 검증한다. */
test("Codex의 잘못된 JSON을 거절한다", async () => {
  await assert.rejects(
    structurePurchaseQuestion("구두", async () => "not-json"),
    (error: unknown) => error instanceof CodexRuntimeError && error.code === "AI_OUTPUT_INVALID",
  );
});

/** 필수 조건이 빠진 Codex JSON을 계약 오류로 거절하는지 검증한다. */
test("Codex의 Schema 불일치 응답을 거절한다", async () => {
  await assert.rejects(
    structurePurchaseQuestion("구두", async () => JSON.stringify({ productType: "구두" })),
    (error: unknown) => error instanceof CodexRuntimeError && error.code === "AI_OUTPUT_INVALID",
  );
});

/** 동시에 여러 Codex process를 실행하지 않고 두 번째 요청을 빠르게 거절하는지 검증한다. */
test("Codex 구조화 동시 실행을 한 개로 제한한다", async () => {
  let release: (() => void) | undefined;
  const blocker = new Promise<void>((resolve) => {
    release = resolve;
  });
  const first = structurePurchaseQuestion("첫 번째 구두", async () => {
    await blocker;
    return JSON.stringify(validCondition);
  });
  await new Promise<void>((resolve) => setImmediate(resolve));

  await assert.rejects(
    structurePurchaseQuestion("두 번째 구두", async () => JSON.stringify(validCondition)),
    (error: unknown) => error instanceof CodexRuntimeError && error.code === "AI_UNAVAILABLE",
  );
  release?.();
  await first;
});
