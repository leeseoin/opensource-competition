import { CodexRuntimeError, structurePurchaseQuestion } from "../../../lib/codex-runtime.ts";
import { ResearchMcpError, StdioResearchMcpClient, type ResearchMcpOperations } from "../../../lib/research-mcp-client.ts";
import type { PurchaseCondition } from "../../../lib/research-session.ts";

interface ConditionsRequestBody {
  question?: unknown;
  runtime?: unknown;
}

/** ConditionsDependencies는 Route Handler에서 Codex와 MCP를 테스트 대역으로 교체한다. */
export interface ConditionsDependencies {
  structure(question: string): Promise<PurchaseCondition>;
  mcp: ResearchMcpOperations;
}

const defaultDependencies: ConditionsDependencies = {
  structure: structurePurchaseQuestion,
  mcp: new StdioResearchMcpClient(),
};

/** 조건 생성 요청의 질문 길이와 현재 지원 runtime을 확인한다. */
function validateRequest(body: ConditionsRequestBody): string | null {
  if (typeof body.question !== "string" || !body.question.trim()) {
    return "question은 비어 있지 않은 문자열이어야 합니다.";
  }
  if (body.question.length > 1000) {
    return "question은 1000자 이하여야 합니다.";
  }
  if (body.runtime !== "codex") {
    return "현재 지원하는 AI 실행 환경은 codex뿐입니다.";
  }
  return null;
}

/** AI 조건 구조화와 MCP DRAFT 저장을 순서대로 실행한다. */
export async function handleConditionsRequest(
  request: Request,
  dependencies: ConditionsDependencies = defaultDependencies,
): Promise<Response> {
  let body: ConditionsRequestBody;
  try {
    body = await request.json() as ConditionsRequestBody;
  } catch {
    return Response.json({ code: "INVALID_REQUEST", message: "JSON 요청 본문이 필요합니다." }, { status: 400 });
  }
  const validationError = validateRequest(body);
  if (validationError) {
    return Response.json({ code: "INVALID_REQUEST", message: validationError }, { status: 400 });
  }

  try {
    const question = (body.question as string).trim();
    const conditions = await dependencies.structure(question);
    return Response.json(await dependencies.mcp.createSession(question, conditions));
  } catch (error) {
    if (error instanceof CodexRuntimeError) {
      return Response.json({ code: error.code, message: error.message }, {
        status: error.code === "AI_OUTPUT_INVALID" ? 502 : 503,
      });
    }
    if (error instanceof ResearchMcpError) {
      return Response.json({ code: "MCP_UNAVAILABLE", message: "구매 조사 MCP 서버에 연결할 수 없습니다." }, { status: 503 });
    }
    return Response.json({ code: "AGENT_GATEWAY_ERROR", message: "구매 조건 처리 중 오류가 발생했습니다." }, { status: 500 });
  }
}
