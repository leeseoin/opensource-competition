import assert from "node:assert/strict";
import test from "node:test";

import { CodexRuntimeError, structurePurchaseQuestion } from "./codex-runtime.ts";

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
