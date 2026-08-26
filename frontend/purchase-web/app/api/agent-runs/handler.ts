import { ResearchMcpError, StdioResearchMcpClient, type ResearchMcpOperations } from "../../lib/research-mcp-client.ts";

const runIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** 실행 ID를 검증하고 MCP Agent Run 진행 도구를 호출한다. */
export async function handleAdvanceAgentRun(
  runId: string,
  mcp: ResearchMcpOperations = new StdioResearchMcpClient(),
): Promise<Response> {
  if (!runIdPattern.test(runId)) {
    return Response.json({ code: "INVALID_RUN_ID", message: "runId는 올바른 UUID여야 합니다." }, { status: 400 });
  }
  try {
    return Response.json(await mcp.advanceAgentRun(runId));
  } catch (error) {
    const status = error instanceof ResearchMcpError ? 503 : 500;
    return Response.json({ code: "AGENT_RUN_ADVANCE_FAILED", message: "구매 조사 진행 상태를 확인하지 못했습니다." }, { status });
  }
}

/** 실행 ID와 선택 상품을 검증하고 MCP Agent Run 재검증 도구를 호출한다. */
export async function handleVerifyAgentRun(
  runId: string,
  request: Request,
  mcp: ResearchMcpOperations = new StdioResearchMcpClient(),
): Promise<Response> {
  if (!runIdPattern.test(runId)) {
    return Response.json({ code: "INVALID_RUN_ID", message: "runId는 올바른 UUID여야 합니다." }, { status: 400 });
  }
  let productId: unknown;
  try {
    productId = (await request.json() as { productId?: unknown }).productId;
  } catch {
    return Response.json({ code: "INVALID_REQUEST", message: "JSON 요청 본문이 필요합니다." }, { status: 400 });
  }
  if (!Number.isSafeInteger(productId) || Number(productId) <= 0) {
    return Response.json({ code: "INVALID_PRODUCT_ID", message: "productId는 양의 정수여야 합니다." }, { status: 400 });
  }
  try {
    return Response.json(await mcp.verifyAgentRunOffer(runId, Number(productId)));
  } catch (error) {
    const status = error instanceof ResearchMcpError ? 503 : 500;
    return Response.json({ code: "AGENT_RUN_VERIFY_FAILED", message: "선택 상품 재검증을 시작하지 못했습니다." }, { status });
  }
}
