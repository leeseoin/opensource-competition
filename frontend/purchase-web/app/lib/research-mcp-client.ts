import path from "node:path";

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

import type { AgentRunResponse, PurchaseCondition, ResearchSessionResponse } from "./research-session.ts";

/** ResearchMcpOperations는 Route Handler와 실제 MCP client가 공유하는 도구 경계다. */
export interface ResearchMcpOperations {
  createSession(question: string, conditions: PurchaseCondition): Promise<ResearchSessionResponse>;
  confirmSession(sessionId: string, conditions: PurchaseCondition): Promise<ResearchSessionResponse>;
  searchCandidates(sessionId: string): Promise<ResearchSessionResponse>;
  startAgentRun(sessionId: string, merchants?: string[]): Promise<AgentRunResponse>;
  getAgentRun(runId: string): Promise<AgentRunResponse>;
  advanceAgentRun(runId: string): Promise<AgentRunResponse>;
  verifyAgentRunOffer(runId: string, productId: number): Promise<AgentRunResponse>;
}

/** ResearchMcpError는 MCP 연결 및 도구 응답 오류를 안전한 한 종류로 표현한다. */
export class ResearchMcpError extends Error {
  /** 사용자에게 공개할 수 있는 MCP 오류 설명을 생성한다. */
  constructor(message: string) {
    super(message);
    this.name = "ResearchMcpError";
  }
}

/** 저장소 루트 또는 명시한 환경 변수에서 MCP server entry 경로를 계산한다. */
function resolveMcpEntry(): { entry: string; cwd: string } {
  const root = process.env.PURCHASE_RESEARCH_REPO_ROOT
    ? path.resolve(process.env.PURCHASE_RESEARCH_REPO_ROOT)
    : path.resolve(process.cwd(), "../..");
  return {
    cwd: root,
    entry: process.env.PURCHASE_RESEARCH_MCP_ENTRY
      ? path.resolve(process.env.PURCHASE_RESEARCH_MCP_ENTRY)
      : path.join(root, "services/mcp-server/dist/index.js"),
  };
}

/** resolveProductBackendBaseUrl은 빈 환경변수에도 로컬 Product Backend 기본 주소를 사용한다. */
export function resolveProductBackendBaseUrl(
  configured = process.env.PRODUCT_BACKEND_BASE_URL,
): string {
  return configured?.trim() || "http://127.0.0.1:8080";
}

/** MCP structuredContent를 조사 세션 응답으로 확인하고 변환한다. */
function parseSessionResult(result: Awaited<ReturnType<Client["callTool"]>>): ResearchSessionResponse {
  if (result.isError || !result.structuredContent || typeof result.structuredContent !== "object") {
    throw new ResearchMcpError("Purchase Research MCP 도구 호출이 실패했습니다.");
  }
  const session = result.structuredContent as unknown as ResearchSessionResponse;
  if (typeof session.sessionId !== "string" || (session.status !== "DRAFT" && session.status !== "CONFIRMED")) {
    throw new ResearchMcpError("Purchase Research MCP 응답 형식이 올바르지 않습니다.");
  }
  return session;
}

/** MCP structuredContent를 상태 기반 Agent Run 응답으로 확인하고 변환한다. */
function parseAgentRunResult(result: Awaited<ReturnType<Client["callTool"]>>): AgentRunResponse {
  if (result.isError || !result.structuredContent || typeof result.structuredContent !== "object") {
    throw new ResearchMcpError("Purchase Research MCP Agent Run 호출이 실패했습니다.");
  }
  const run = result.structuredContent as unknown as AgentRunResponse;
  const statuses = ["SEARCHING", "COLLECTING", "READY", "VERIFYING", "COMPLETED", "NO_RESULTS", "FAILED"];
  if (typeof run.runId !== "string" || !statuses.includes(run.status) || !Array.isArray(run.events)) {
    throw new ResearchMcpError("Purchase Research MCP Agent Run 응답 형식이 올바르지 않습니다.");
  }
  return run;
}

/** StdioResearchMcpClient는 요청마다 MCP process를 열고 닫아 잔여 process를 남기지 않는다. */
export class StdioResearchMcpClient implements ResearchMcpOperations {
  /** AI 구매 조건을 MCP를 통해 DRAFT 조사 세션으로 저장한다. */
  createSession(question: string, conditions: PurchaseCondition): Promise<ResearchSessionResponse> {
    return this.callSession("create_research_session", { question, conditions });
  }

  /** 사용자 확인 조건을 MCP를 통해 CONFIRMED 상태로 저장한다. */
  confirmSession(sessionId: string, conditions: PurchaseCondition): Promise<ResearchSessionResponse> {
    return this.callSession("confirm_purchase_conditions", { sessionId, conditions });
  }

  /** 확정된 조사 세션의 후보를 MCP를 통해 검색한다. */
  searchCandidates(sessionId: string): Promise<ResearchSessionResponse> {
    return this.callSession("search_product_candidates", { sessionId });
  }

  /** 확정 조사 세션의 상태 기반 구매 조사를 시작한다. */
  startAgentRun(sessionId: string, merchants: string[] = []): Promise<AgentRunResponse> {
    return this.callAgentRun("start_agent_run", { sessionId, merchants });
  }

  /** 실행의 현재 상태와 진행 사건을 조회한다. */
  getAgentRun(runId: string): Promise<AgentRunResponse> {
    return this.callAgentRun("get_agent_run", { runId });
  }

  /** 수집 또는 재검증 상태를 확인해 실행을 한 단계 전진시킨다. */
  advanceAgentRun(runId: string): Promise<AgentRunResponse> {
    return this.callAgentRun("advance_agent_run", { runId });
  }

  /** READY 후보의 구매 직전 가격과 재고를 재검증한다. */
  verifyAgentRunOffer(runId: string, productId: number): Promise<AgentRunResponse> {
    return this.callAgentRun("verify_agent_run_offer", { runId, productId });
  }

  /** MCP stdio child process에 연결해 도구 하나를 호출하고 항상 연결을 닫는다. */
  private callSession(name: string, args: Record<string, unknown>): Promise<ResearchSessionResponse> {
    return this.call(name, args, parseSessionResult);
  }

  /** Agent Run MCP 도구를 호출하고 실행 응답 계약을 검사한다. */
  private callAgentRun(name: string, args: Record<string, unknown>): Promise<AgentRunResponse> {
    return this.call(name, args, parseAgentRunResult);
  }

  /** MCP stdio child process에 연결해 도구 하나를 호출하고 항상 연결을 닫는다. */
  private async call<T>(
    name: string,
    args: Record<string, unknown>,
    parse: (result: Awaited<ReturnType<Client["callTool"]>>) => T,
  ): Promise<T> {
    const { cwd, entry } = resolveMcpEntry();
    const transport = new StdioClientTransport({
      command: process.execPath,
      args: [entry],
      cwd,
      env: {
        PATH: process.env.PATH ?? "",
        LANG: process.env.LANG ?? "ko_KR.UTF-8",
        PRODUCT_BACKEND_BASE_URL: resolveProductBackendBaseUrl(),
      },
    });
    const client = new Client({ name: "purchase-web-agent-gateway", version: "0.1.0" });
    try {
      await client.connect(transport);
      return parse(await client.callTool({ name, arguments: args }));
    } catch (error) {
      if (error instanceof ResearchMcpError) {
        throw error;
      }
      throw new ResearchMcpError(error instanceof Error ? error.message : "Purchase Research MCP 연결에 실패했습니다.");
    } finally {
      await client.close().catch(() => undefined);
    }
  }
}
