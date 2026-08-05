import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

import { ProductBackendClient, type PurchaseCondition } from "./backend-client.js";

const priceSchema = z.object({
  min: z.number().int().nonnegative().nullable(),
  max: z.number().int().nonnegative().nullable(),
  currency: z.string().regex(/^[A-Z]{3}$/),
});

const conditionSchema = z.object({
  productType: z.string().min(1).max(200),
  usage: z.array(z.string().min(1).max(100)).max(20),
  price: priceSchema,
  colors: z.array(z.string().min(1).max(100)).max(20),
  sizes: z.array(z.string().min(1).max(100)).max(20),
  requirements: z.array(z.string().min(1).max(200)).max(30),
  merchant: z.string().regex(/^[a-z0-9][a-z0-9-]*$/).max(64).nullable(),
  missingConditions: z.array(z.string().min(1).max(200)).max(20),
  assumptions: z.array(z.string().min(1).max(500)).max(20),
  confidence: z.number().min(0).max(1),
  requiresConfirmation: z.literal(true),
});

/** toToolResult는 Product Backend JSON을 MCP model-readable 결과로 변환한다. */
function toToolResult(value: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(value) }],
    structuredContent: value as Record<string, unknown>,
  };
}

/** main은 stdio MCP 서버를 시작하고 조사 세션 도구 세 개를 등록한다. */
async function main(): Promise<void> {
  const client = new ProductBackendClient(
    process.env.PRODUCT_BACKEND_BASE_URL ?? "http://127.0.0.1:8080",
  );
  const server = new McpServer({ name: "purchase-research", version: "0.1.0" }, {
    instructions: "구매 조건은 먼저 create_research_session으로 DRAFT 저장한다. 사용자가 명시적으로 확인한 뒤 confirm_purchase_conditions를 호출하고, 그 다음에만 search_product_candidates를 호출한다. 판매처 사실을 추측하지 않는다.",
  });

  server.registerTool(
    "create_research_session",
    {
      description: "AI가 구조화한 구매 조건을 사용자 확인 전 DRAFT 조사 세션으로 저장한다.",
      inputSchema: { question: z.string().min(1).max(1000), conditions: conditionSchema },
      annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false },
    },
    async ({ question, conditions }) => toToolResult(
      await client.createSession(question, conditions as PurchaseCondition),
    ),
  );

  server.registerTool(
    "confirm_purchase_conditions",
    {
      description: "사용자가 화면에서 확인하거나 수정한 구매 조건을 CONFIRMED 상태로 저장한다.",
      inputSchema: { sessionId: z.string().uuid(), conditions: conditionSchema },
      annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: true },
    },
    async ({ sessionId, conditions }) => toToolResult(
      await client.confirmSession(sessionId, conditions as PurchaseCondition),
    ),
  );

  server.registerTool(
    "search_product_candidates",
    {
      description: "CONFIRMED 조사 세션의 조건으로 PostgreSQL 상품 후보 최대 3개와 근거를 검색한다.",
      inputSchema: { sessionId: z.string().uuid() },
      annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true },
    },
    async ({ sessionId }) => toToolResult(await client.searchCandidates(sessionId)),
  );

  await server.connect(new StdioServerTransport());
}

await main();
