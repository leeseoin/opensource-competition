import assert from "node:assert/strict";
import test from "node:test";

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

/** stdio MCP 초기화와 조사 세션/상품 상세/근거/비교 도구 목록을 실제 child process로 검증한다. */
test("stdio MCP 서버가 조사 세션 도구를 공개한다", async () => {
  const transport = new StdioClientTransport({
    command: "node",
    args: ["dist/index.js"],
    env: { ...process.env, PRODUCT_BACKEND_BASE_URL: "http://127.0.0.1:1" } as Record<string, string>,
  });
  const client = new Client({ name: "purchase-research-test", version: "0.1.0" });
  try {
    await client.connect(transport);
    const tools = await client.listTools();
    assert.deepEqual(
      tools.tools.map((tool) => tool.name).sort(),
      [
        "compare_products",
        "confirm_purchase_conditions",
        "create_research_session",
        "get_evidence",
        "get_product",
        "get_verification_status",
        "request_collection",
        "search_product_candidates",
        "verify_offer",
      ],
    );
  } finally {
    await client.close();
  }
});
