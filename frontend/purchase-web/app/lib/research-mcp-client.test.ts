import assert from "node:assert/strict";
import test from "node:test";

import { resolveProductBackendBaseUrl } from "./research-mcp-client.ts";

/** 빈 Product Backend 주소가 로컬 기본 주소로 복구되는지 검증한다. */
test("빈 Product Backend 주소에 로컬 기본값을 사용한다", () => {
  assert.equal(resolveProductBackendBaseUrl(""), "http://127.0.0.1:8080");
  assert.equal(resolveProductBackendBaseUrl("  "), "http://127.0.0.1:8080");
  assert.equal(resolveProductBackendBaseUrl(undefined), "http://127.0.0.1:8080");
});

/** 명시한 Product Backend 주소의 양끝 공백을 제거하는지 검증한다. */
test("명시한 Product Backend 주소를 정규화한다", () => {
  assert.equal(resolveProductBackendBaseUrl(" http://product-backend:8080 "), "http://product-backend:8080");
});
