import type { ProductCandidateResponse } from "./product-candidates.ts";

/** PurchaseCondition은 AI가 구조화하고 사용자가 확인할 공통 구매 조건이다. */
export interface PurchaseCondition {
  productType: string;
  usage: string[];
  price: { min: number | null; max: number | null; currency: string };
  colors: string[];
  sizes: string[];
  requirements: string[];
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

  return typeof condition.productType === "string"
    && condition.productType.trim().length > 0
    && isStringArray(condition.usage)
    && price !== null
    && nullableNonNegativeInteger(price.min)
    && nullableNonNegativeInteger(price.max)
    && typeof price.currency === "string"
    && /^[A-Z]{3}$/.test(price.currency)
    && isStringArray(condition.colors)
    && isStringArray(condition.sizes)
    && isStringArray(condition.requirements)
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
