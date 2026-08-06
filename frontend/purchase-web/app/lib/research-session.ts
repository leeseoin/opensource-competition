import type { ProductCandidateResponse } from "./product-candidates.ts";

/** ConditionPriority는 후보 제외용 필수 조건과 순위용 선호 조건을 구분한다. */
export type ConditionPriority = "required" | "preferred";

/** PrioritizedText는 구매 조건 값과 사용자가 확인할 강도를 함께 표현한다. */
export interface PrioritizedText {
  value: string;
  priority: ConditionPriority;
}

/** PurchaseCondition은 AI가 구조화하고 사용자가 확인할 공통 구매 조건이다. */
export interface PurchaseCondition {
  productType: PrioritizedText;
  usage: PrioritizedText[];
  price: { min: number | null; max: number | null; currency: string; priority: ConditionPriority };
  colors: PrioritizedText[];
  sizes: PrioritizedText[];
  requirements: PrioritizedText[];
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
): Promise<ResearchSessionResponse> {
  const response = await fetch("/api/research/confirm", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId, conditions }),
  });
  const body = await response.json() as ResearchSessionResponse & { message?: string };
  if (!response.ok) {
    throw new Error(body.message ?? "확인한 조건으로 상품을 검색하지 못했습니다.");
  }
  return body;
}
