/** ConditionPriority는 후보 제외용 필수 조건과 순위용 선호 조건을 구분한다. */
export type ConditionPriority = "required" | "preferred";

/** ConditionDerivation은 정규화 값의 생성 근거를 구분한다. */
export type ConditionDerivation = "original" | "rule" | "dictionary" | "wiki" | "fuzzy" | "llm";

/** PrioritizedText는 구매 조건 값과 사용자가 확인할 강도를 함께 표현한다. */
export interface PrioritizedText {
  value: string;
  priority: ConditionPriority;
  normalizedValue?: string | null;
  canonicalId?: string | null;
  confidence?: number | null;
  derivedBy?: ConditionDerivation | null;
  requiresConfirmation?: boolean;
}

/** AttributeCondition은 상품군별 속성을 공통 key/value 계약으로 전달한다. */
export interface AttributeCondition extends PrioritizedText {
  key: string;
}

/** PurchaseCondition은 MCP가 Product Backend에 전달할 사용자 확인 대상 구매 조건이다. */
export interface PurchaseCondition {
  productType: PrioritizedText;
  usage: PrioritizedText[];
  price: { min: number | null; max: number | null; currency: string; priority: ConditionPriority };
  colors: PrioritizedText[];
  sizes: PrioritizedText[];
  requirements: PrioritizedText[];
  attributes?: AttributeCondition[];
  merchant: string | null;
  missingConditions: string[];
  assumptions: string[];
  confidence: number;
  requiresConfirmation: true;
}

/** ResearchSessionResponse는 조사 세션 상태와 선택 상품 검색 결과를 보존한다. */
export interface ResearchSessionResponse {
  sessionId: string;
  question: string;
  runtime: "codex";
  pluginId: "purchase-research-agent";
  status: "DRAFT" | "CONFIRMED";
  conditions: PurchaseCondition;
  confirmedAt: string | null;
  result: unknown | null;
}

/** BackendRequestError는 Product Backend의 상태 코드와 안전한 응답 본문을 보존한다. */
export class BackendRequestError extends Error {
  /**
   * Product Backend 오류를 생성한다.
   *
   * @param status HTTP 상태 코드
   * @param message 안전하게 변환한 오류 설명
   */
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "BackendRequestError";
  }
}

/** ProductBackendClient는 MCP 도구를 Product Backend REST API로만 변환한다. */
export class ProductBackendClient {
  /**
   * Product Backend 주소와 요청 구현을 주입한다.
   *
   * @param baseUrl Product Backend 내부 주소
   * @param request HTTP 요청 구현
   */
  constructor(
    private readonly baseUrl: string,
    private readonly request: typeof fetch = fetch,
  ) {}

  /** AI가 구조화한 조건을 DRAFT 조사 세션으로 저장한다. */
  createSession(question: string, conditions: PurchaseCondition): Promise<ResearchSessionResponse> {
    return this.call("/internal/v1/research-sessions", {
      method: "POST",
      body: JSON.stringify({
        question,
        runtime: "codex",
        pluginId: "purchase-research-agent",
        conditions,
      }),
    });
  }

  /** 사용자가 확인한 조건으로 조사 세션을 CONFIRMED 상태로 전환한다. */
  confirmSession(sessionId: string, conditions: PurchaseCondition): Promise<ResearchSessionResponse> {
    return this.call(`/internal/v1/research-sessions/${encodeURIComponent(sessionId)}/confirm`, {
      method: "POST",
      body: JSON.stringify({ conditions }),
    });
  }

  /** CONFIRMED 조사 세션의 상품 후보를 검색한다. */
  searchCandidates(sessionId: string): Promise<ResearchSessionResponse> {
    return this.call(`/internal/v1/research-sessions/${encodeURIComponent(sessionId)}/search`, {
      method: "POST",
    });
  }

  /**
   * Product Backend JSON API를 호출하고 오류를 MCP가 처리할 수 있는 예외로 변환한다.
   *
   * @param path 내부 API 경로
   * @param init fetch 설정
   * @return 조사 세션 응답
   * @throws BackendRequestError timeout, 연결 실패 또는 비정상 HTTP 응답
   */
  private async call(path: string, init: RequestInit): Promise<ResearchSessionResponse> {
    let response: Response;
    try {
      response = await this.request(`${this.baseUrl}${path}`, {
        ...init,
        headers: { "Content-Type": "application/json" },
        signal: AbortSignal.timeout(10_000),
      });
    } catch (error) {
      throw new BackendRequestError(503, error instanceof Error ? error.message : "Product Backend 연결 실패");
    }
    const body = await response.text();
    if (!response.ok) {
      throw new BackendRequestError(response.status, body.slice(0, 1000));
    }
    return JSON.parse(body) as ResearchSessionResponse;
  }
}
