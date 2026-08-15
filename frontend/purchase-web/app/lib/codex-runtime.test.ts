import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import {
  classifyCodexProcessFailure,
  CodexRuntimeError,
  resolveCodexCommand,
  structurePurchaseQuestion,
} from "./codex-runtime.ts";

const validCondition = {
  productType: { value: "구두", priority: "required" },
  usage: [{ value: "출근", priority: "preferred" }],
  price: { min: null, max: 100000, currency: "KRW", priority: "required" },
  colors: [{ value: "검정", priority: "preferred" }],
  sizes: [{ value: "270", priority: "required" }],
  requirements: [{ value: "편안함", priority: "preferred" }],
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

  assert.equal(result.productType.value, "구두");
  assert.ok(receivedArgs.includes("read-only"));
  assert.ok(receivedArgs.includes("--output-schema"));
  assert.ok(receivedArgs.some((arg) => arg.endsWith("purchase-condition.codex-output.schema.json")));
  assert.match(receivedPrompt, /Purchase Research/);
  assert.match(receivedPrompt, /검정 구두를 찾아줘/);
});

/** Codex 전용 Schema의 모든 객체가 구조화 출력의 required 제약을 지키는지 검증한다. */
test("Codex 출력 Schema는 모든 객체 속성을 required로 선언한다", () => {
  const schemaPath = path.resolve(
    process.cwd(),
    "../../contracts/research/v1/purchase-condition.codex-output.schema.json",
  );
  const schema = JSON.parse(readFileSync(schemaPath, "utf8")) as Record<string, unknown>;
  const pending: unknown[] = [schema];

  while (pending.length > 0) {
    const current = pending.pop();
    if (!current || typeof current !== "object" || Array.isArray(current)) {
      continue;
    }
    const object = current as Record<string, unknown>;
    if (object.type === "object" && object.properties && typeof object.properties === "object") {
      const properties = Object.keys(object.properties as Record<string, unknown>).sort();
      const required = Array.isArray(object.required) ? [...object.required].sort() : [];
      assert.deepEqual(required, properties);
      assert.equal(object.additionalProperties, false);
    }
    pending.push(...Object.values(object));
  }
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

/** 지원하지 않는 출력 Schema 오류를 일반 장애가 아닌 계약 오류로 안전하게 분류하는지 검증한다. */
test("Codex 출력 Schema 오류를 안전한 계약 오류로 변환한다", () => {
  const error = classifyCodexProcessFailure("invalid_json_schema: private schema details");

  assert.equal(error.code, "AI_OUTPUT_INVALID");
  assert.doesNotMatch(error.message, /private schema details/);
});

/** Codex가 productType에 색상을 중복해도 DB 검색어에서는 상품 종류만 남기는지 검증한다. */
test("상품 종류에서 구조화된 색상 중복을 제거한다", async () => {
  const result = await structurePurchaseQuestion("검정 구두를 찾아줘", async () => JSON.stringify({
    ...validCondition,
    productType: { value: "검정 구두", priority: "required" },
  }));

  assert.equal(result.productType.value, "구두");
  assert.deepEqual(result.colors, [{ value: "검정", priority: "required" }]);
});

/** 단정해 요청한 색상은 필수로, 완화 표현이 있는 색상은 선호로 보정하는지 검증한다. */
test("사용자 색상 표현의 필수와 선호 강도를 구분한다", async () => {
  const explicit = await structurePurchaseQuestion(
    "갈색 구두 찾아줘",
    async () => JSON.stringify({ ...validCondition, colors: [{ value: "갈색", priority: "preferred" }] }),
  );
  const preferred = await structurePurchaseQuestion(
    "갈색 구두를 찾아줘. 없으면 색은 달라도 괜찮아",
    async () => JSON.stringify({ ...validCondition, colors: [{ value: "갈색", priority: "required" }] }),
  );

  assert.equal(explicit.colors[0]?.priority, "required");
  assert.equal(preferred.colors[0]?.priority, "preferred");
});

/** 다른 색을 명시적으로 거부한 문장은 완화 단어가 함께 있어도 필수 색상으로 유지하는지 검증한다. */
test("다른 색 불가 문맥은 필수 색상으로 유지한다", async () => {
  const result = await structurePurchaseQuestion(
    "갈색 구두를 찾아줘. 없으면 다른 색은 안 돼",
    async () => JSON.stringify({ ...validCondition, colors: [{ value: "갈색", priority: "preferred" }] }),
  );

  assert.equal(result.colors[0]?.priority, "required");
});

/** 특정 모델명이 필수 조건으로 잘못 분류돼도 상품 검색어로 복구하는지 검증한다. */
test("미확인 상품 종류에서 필수 모델명을 검색어로 승격한다", async () => {
  const result = await structurePurchaseQuestion("탈리타 5 블랙 250 찾아줘", async () => JSON.stringify({
    ...validCondition,
    productType: { value: "미확인", priority: "required" },
    requirements: [{ value: "탈리타 5", priority: "required" }],
  }));

  assert.equal(result.productType.value, "탈리타 5");
  assert.deepEqual(result.requirements, []);
});

/** 일반 기능 요구사항은 미확인 상품 종류를 대신하는 모델명으로 오인하지 않는지 검증한다. */
test("미확인 상품 종류에서 일반 필수 조건은 검색어로 승격하지 않는다", async () => {
  const result = await structurePurchaseQuestion("방수 기능은 필수야", async () => JSON.stringify({
    ...validCondition,
    productType: { value: "미확인", priority: "required" },
    requirements: [{ value: "방수", priority: "required" }],
  }));

  assert.equal(result.productType.value, "미확인");
  assert.deepEqual(result.requirements, [{ value: "방수", priority: "required" }]);
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
