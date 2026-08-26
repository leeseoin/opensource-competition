import type { PurchaseCondition } from "./research-session.ts";

/** AgentRuntime은 구매 조건 구조화를 수행할 수 있는 서버측 AI 실행 환경이다. */
export type AgentRuntime = "codex" | "claude";

/** AgentCommandRunner는 실제 CLI process와 테스트 대역이 공유하는 실행 경계다. */
export type AgentCommandRunner = (
  args: string[],
  input: string,
  options: { cwd: string; timeoutMs: number },
) => Promise<string>;

/** AgentRuntimeError는 AI 실행 실패 유형을 API 상태로 변환할 수 있게 보존한다. */
export class AgentRuntimeError extends Error {
  readonly code: "AI_UNAVAILABLE" | "AI_OUTPUT_INVALID" | "AI_AUTH_REQUIRED";

  /** AI 실행 오류 코드와 사용자에게 노출 가능한 설명을 생성한다. */
  constructor(code: "AI_UNAVAILABLE" | "AI_OUTPUT_INVALID" | "AI_AUTH_REQUIRED", message: string) {
    super(message);
    this.code = code;
    this.name = "AgentRuntimeError";
  }
}

/** PurchaseQuestionStructurer는 런타임별 자연어 구매 조건 구조화 함수 계약이다. */
export type PurchaseQuestionStructurer = (question: string) => Promise<PurchaseCondition>;
