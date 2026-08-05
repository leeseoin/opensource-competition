import { ResearchMcpError, StdioResearchMcpClient, type ResearchMcpOperations } from "../../../lib/research-mcp-client.ts";
import { isPurchaseCondition, type PurchaseCondition } from "../../../lib/research-session.ts";

interface ConfirmRequestBody {
  sessionId?: unknown;
  conditions?: unknown;
}

const sessionIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** 사용자 확인 요청의 세션 식별자와 구매 조건 계약을 검증한다. */
function validateRequest(body: ConfirmRequestBody): string | null {
  if (typeof body.sessionId !== "string" || !sessionIdPattern.test(body.sessionId)) {
    return "sessionId는 올바른 UUID여야 합니다.";
  }
  if (!isPurchaseCondition(body.conditions)) {
    return "conditions가 PurchaseCondition 계약과 일치하지 않습니다.";
  }
  if (body.conditions.missingConditions.length > 0) {
    return "확인하지 않은 구매 조건이 남아 있습니다.";
  }
  return null;
}

/** 사용자 확인을 MCP에 저장한 뒤 같은 세션의 후보 검색만 허용한다. */
export async function handleConfirmRequest(
  request: Request,
  mcp: ResearchMcpOperations = new StdioResearchMcpClient(),
): Promise<Response> {
  let body: ConfirmRequestBody;
  try {
    body = await request.json() as ConfirmRequestBody;
  } catch {
    return Response.json({ code: "INVALID_REQUEST", message: "JSON 요청 본문이 필요합니다." }, { status: 400 });
  }
  const validationError = validateRequest(body);
  if (validationError) {
    const status = isPurchaseCondition(body.conditions) && body.conditions.missingConditions.length > 0 ? 409 : 400;
    return Response.json({ code: "RESEARCH_SESSION_NOT_READY", message: validationError }, { status });
  }

  try {
    const sessionId = body.sessionId as string;
    const conditions = body.conditions as PurchaseCondition;
    await mcp.confirmSession(sessionId, conditions);
    return Response.json(await mcp.searchCandidates(sessionId));
  } catch (error) {
    if (error instanceof ResearchMcpError) {
      return Response.json({ code: "MCP_UNAVAILABLE", message: "구매 조사 MCP 서버가 요청을 처리하지 못했습니다." }, { status: 503 });
    }
    return Response.json({ code: "AGENT_GATEWAY_ERROR", message: "확정 조건 검색 중 오류가 발생했습니다." }, { status: 500 });
  }
}
