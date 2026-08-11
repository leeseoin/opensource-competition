import assert from "node:assert/strict";
import test from "node:test";

import { parsePrioritizedList } from "./condition-editing.ts";

/** 필수 색상을 다른 값으로 교체해도 기존 필수 강도를 유지하는지 검증한다. */
test("목록 값을 교체할 때 기존 조건 강도를 유지한다", () => {
  const result = parsePrioritizedList(
    "검정",
    [{ value: "갈색", priority: "required" }],
    "preferred",
  );

  assert.deepEqual(result, [{ value: "검정", priority: "required" }]);
});

/** 기존 조건이 없는 목록은 필드가 제공한 fallback 강도를 사용하는지 검증한다. */
test("기존 조건이 없으면 필드 fallback 강도를 사용한다", () => {
  const result = parsePrioritizedList("출근, 면접", [], "preferred");

  assert.deepEqual(result, [
    { value: "출근", priority: "preferred" },
    { value: "면접", priority: "preferred" },
  ]);
});
