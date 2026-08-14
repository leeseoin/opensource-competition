import type { ProductCandidateResponse } from "./product-candidates.ts";

/** ConditionPriority는 후보 제외용 필수 조건과 순위용 선호 조건을 구분한다. */
export type ConditionPriority = "required" | "preferred";

/** ConditionDerivation은 표준 조건값을 만든 근거를 구분한다. */
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

/** AttributeCondition은 상품군별 조건을 공통 key/value 구조로 표현한다. */
export interface AttributeCondition extends PrioritizedText {
  key: string;
}

/** PurchaseCondition은 AI가 구조화하고 사용자가 확인할 공통 구매 조건이다. */
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

/** ResearchSessionResponse는 사용자 확인 전후의 조건과 검색 결과를 표현한다. */
export interface ResearchSessionResponse {
  sessionId: string;
  question: string;
  runtime: "codex";
  pluginId: "purchase-research-agent";
  status: "DRAFT" | "CONFIRMED";
  conditions: PurchaseCondition;
  confirmedAt: string | null;
  result: ProductCandidateResponse | null;
}

/** AgentRunEvent는 사용자가 확인할 수 있는 구매 조사 단계와 당시 상태다. */
export interface AgentRunEvent {
  sequence: number;
  type: string;
  status: string;
  message: string;
  createdAt: string;
}

/** AgentRunResponse는 DB 검색, 조건부 수집과 재검증의 복구 가능한 실행 상태다. */
export interface AgentRunResponse {
  runId: string;
  sessionId: string;
  status: "SEARCHING" | "COLLECTING" | "READY" | "VERIFYING" | "COMPLETED" | "NO_RESULTS" | "FAILED";
  research: ResearchSessionResponse | null;
  collectionJobs: Array<{
    jobId: string;
    merchant: string;
    dataStatus: string;
    status: string;
    productCount: number;
  }>;
  verification: {
    status?: string;
    changes?: string[];
    baseline?: { priceAmount?: number | null; stockStatus?: string | null };
    latest?: { priceAmount?: number | null; stockStatus?: string | null } | null;
  } | null;
  events: AgentRunEvent[];
  error: { code: string; message: string } | null;
  nextAction: "POLL_ADVANCE" | "SELECT_AND_VERIFY" | "NONE";
}

/** 알 수 없는 값이 필수/선호 강도인지 확인한다. */
function isConditionPriority(value: unknown): value is ConditionPriority {
  return value === "required" || value === "preferred";
}

/** 알 수 없는 값이 강도를 포함한 비어 있지 않은 문자열 조건인지 확인한다. */
function isPrioritizedText(value: unknown): value is PrioritizedText {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return false;
  }
  const condition = value as Record<string, unknown>;
  return typeof condition.value === "string"
    && condition.value.trim().length > 0
    && isConditionPriority(condition.priority);
}

/** 강도를 포함한 문자열 조건 배열인지 확인한다. */
function isPrioritizedTextArray(value: unknown): value is PrioritizedText[] {
  return Array.isArray(value) && value.every(isPrioritizedText);
}

/** 알 수 없는 값이 범용 속성 조건 배열인지 확인한다. */
function isAttributeArray(value: unknown): value is AttributeCondition[] {
  return value === undefined || (Array.isArray(value) && value.every((item) => {
    if (!isPrioritizedText(item)) {
      return false;
    }
    return typeof (item as AttributeCondition).key === "string"
      && /^[a-z][a-z0-9._-]*$/.test((item as AttributeCondition).key);
  }));
}

/** 문자열 배열인지와 각 값이 비어 있지 않은지 확인한다. */
function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === "string" && item.trim().length > 0);
}

/** 알 수 없는 AI 응답이 PurchaseCondition 계약을 만족하는지 확인한다. */
export function isPurchaseCondition(value: unknown): value is PurchaseCondition {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return false;
  }
  const condition = value as Record<string, unknown>;
  const price = condition.price as Record<string, unknown> | null;
  const nullableNonNegativeInteger = (amount: unknown) => amount === null
    || (Number.isInteger(amount) && Number(amount) >= 0);

  return isPrioritizedText(condition.productType)
    && isPrioritizedTextArray(condition.usage)
    && price !== null
    && nullableNonNegativeInteger(price.min)
    && nullableNonNegativeInteger(price.max)
    && typeof price.currency === "string"
    && /^[A-Z]{3}$/.test(price.currency)
    && isConditionPriority(price.priority)
    && isPrioritizedTextArray(condition.colors)
    && isPrioritizedTextArray(condition.sizes)
    && isPrioritizedTextArray(condition.requirements)
    && isAttributeArray(condition.attributes)
    && (condition.merchant === null
      || (typeof condition.merchant === "string" && /^[a-z0-9][a-z0-9-]*$/.test(condition.merchant)))
    && isStringArray(condition.missingConditions)
    && isStringArray(condition.assumptions)
    && typeof condition.confidence === "number"
    && condition.confidence >= 0
    && condition.confidence <= 1
    && condition.requiresConfirmation === true;
}

/** browser에서 조건 초안 생성을 Next.js Agent Gateway에 요청한다. */
export async function createResearchDraft(question: string): Promise<ResearchSessionResponse> {
  const response = await fetch("/api/research/conditions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question, runtime: "codex" }),
  });
  const body = await response.json() as ResearchSessionResponse & { message?: string };
  if (!response.ok) {
    throw new Error(body.message ?? "AI가 구매 조건을 정리하지 못했습니다.");
  }
  return body;
}

/** browser에서 확인한 조건을 MCP 검색 흐름에 전달한다. */
export async function confirmResearchDraft(
  sessionId: string,
  conditions: PurchaseCondition,
): Promise<AgentRunResponse> {
  const response = await fetch("/api/research/confirm", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId, conditions }),
  });
  const body = await response.json() as AgentRunResponse & { message?: string };
  if (!response.ok) {
    throw new Error(body.message ?? "확인한 조건으로 상품을 검색하지 못했습니다.");
  }
  return body;
}

/** browser에서 수집 또는 재검증 중인 Agent Run을 한 단계 진행한다. */
export async function advanceAgentRun(runId: string): Promise<AgentRunResponse> {
  const response = await fetch(`/api/agent-runs/${encodeURIComponent(runId)}/advance`, { method: "POST" });
  const body = await response.json() as AgentRunResponse & { message?: string };
  if (!response.ok) {
    throw new Error(body.message ?? "구매 조사 진행 상태를 확인하지 못했습니다.");
  }
  return body;
}

/** browser에서 READY 후보의 구매 직전 가격과 재고 재검증을 시작한다. */
export async function verifyAgentRunOffer(runId: string, productId: number): Promise<AgentRunResponse> {
  const response = await fetch(`/api/agent-runs/${encodeURIComponent(runId)}/verify`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ productId }),
  });
  const body = await response.json() as AgentRunResponse & { message?: string };
  if (!response.ok) {
    throw new Error(body.message ?? "선택 상품을 재검증하지 못했습니다.");
  }
  return body;
}
