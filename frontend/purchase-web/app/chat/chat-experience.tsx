"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, useState } from "react";

import { formatProductPrice } from "../lib/product-candidates";
import {
  confirmResearchDraft,
  createResearchDraft,
  type ConditionPriority,
  type PrioritizedText,
  type PurchaseCondition,
  type ResearchSessionResponse,
} from "../lib/research-session";
import styles from "./page.module.css";

const defaultQuestion = "출근할 때 신을 검정 구두가 필요해. 10만 원 이하이고 270 사이즈였으면 좋겠어.";

type FlowState = "idle" | "structuring" | "draft" | "searching" | "success" | "error";

/** 쉼표 입력을 기존 강도 또는 기본 강도를 보존한 조건 배열로 변환한다. */
function parseList(
  value: string,
  current: PrioritizedText[],
  defaultPriority: ConditionPriority,
): PrioritizedText[] {
  const priorityByValue = new Map(current.map((item) => [item.value, item.priority]));
  return value.split(",").map((item) => item.trim()).filter(Boolean).map((item) => ({
    value: item,
    priority: priorityByValue.get(item) ?? defaultPriority,
  }));
}

/** 조건 배열을 사람이 수정하기 쉬운 한 줄 문자열로 변환한다. */
function formatList(value: PrioritizedText[]): string {
  return value.map((item) => item.value).join(", ");
}

/** ChatExperience는 Codex 조건 구조화부터 사용자 확인과 MCP 상품 검색까지 제공한다. */
export default function ChatExperience() {
  const [question, setQuestion] = useState(defaultQuestion);
  const [runtime, setRuntime] = useState<"codex">("codex");
  const [session, setSession] = useState<ResearchSessionResponse | null>(null);
  const [conditions, setConditions] = useState<PurchaseCondition | null>(null);
  const [flowState, setFlowState] = useState<FlowState>("idle");
  const [errorMessage, setErrorMessage] = useState("");

  /** 자연어 질문을 Codex에 전달하고 검색하지 않은 DRAFT 조건만 표시한다. */
  async function handleQuestionSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const nextQuestion = question.trim();
    if (!nextQuestion) {
      return;
    }
    setFlowState("structuring");
    setErrorMessage("");
    setSession(null);
    setConditions(null);
    try {
      const draft = await createResearchDraft(nextQuestion);
      setSession(draft);
      setConditions(draft.conditions);
      setFlowState("draft");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "AI가 구매 조건을 정리하지 못했습니다.");
      setFlowState("error");
    }
  }

  /** 사용자가 수정한 조건을 MCP로 확인한 뒤에만 PostgreSQL 후보를 검색한다. */
  async function handleConfirm(): Promise<void> {
    if (!session || !conditions) {
      return;
    }
    setFlowState("searching");
    setErrorMessage("");
    try {
      const confirmed = await confirmResearchDraft(session.sessionId, {
        ...conditions,
        missingConditions: [],
        requiresConfirmation: true,
      });
      setSession(confirmed);
      setConditions(confirmed.conditions);
      setFlowState("success");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "상품 후보를 검색하지 못했습니다.");
      setFlowState("error");
    }
  }

  /** 단일 문자열 구매 조건을 수정하고 화면 상태에 반영한다. */
  function updateTextCondition(field: "productType" | "merchant", value: string): void {
    if (!conditions) {
      return;
    }
    if (field === "merchant") {
      setConditions({ ...conditions, merchant: value.trim() || null });
      return;
    }
    setConditions({ ...conditions, productType: { ...conditions.productType, value } });
  }

  /** 쉼표 구분 구매 조건 배열을 수정하고 화면 상태에 반영한다. */
  function updateListCondition(
    field: "usage" | "colors" | "sizes" | "requirements",
    value: string,
  ): void {
    if (conditions) {
      const defaultPriority = field === "sizes" ? "required" : "preferred";
      setConditions({ ...conditions, [field]: parseList(value, conditions[field], defaultPriority) });
    }
  }

  /** 조건 묶음의 필수/선호 강도를 사용자가 한 번에 변경하도록 반영한다. */
  function updatePriority(
    field: "productType" | "usage" | "price" | "colors" | "sizes" | "requirements",
    priority: ConditionPriority,
  ): void {
    if (!conditions) {
      return;
    }
    if (field === "productType") {
      setConditions({ ...conditions, productType: { ...conditions.productType, priority } });
      return;
    }
    if (field === "price") {
      setConditions({ ...conditions, price: { ...conditions.price, priority } });
      return;
    }
    setConditions({
      ...conditions,
      [field]: conditions[field].map((item) => ({ ...item, priority })),
    });
  }

  /** 최대 가격 입력을 원 단위 정수 또는 미지정 상태로 반영한다. */
  function updateMaxPrice(value: string): void {
    if (!conditions) {
      return;
    }
    const amount = value === "" ? null : Math.max(0, Math.trunc(Number(value)));
    setConditions({ ...conditions, price: { ...conditions.price, max: Number.isFinite(amount) ? amount : null } });
  }

  const result = session?.result ?? null;
  const candidates = result?.candidates ?? [];
  const assessments = new Map((result?.assessments ?? []).map((assessment) => [assessment.candidateId, assessment]));
  const featured = candidates[0];
  const merchantCount = new Set(candidates.map((candidate) => candidate.merchant)).size;
  const compareHref = {
    pathname: "/compare",
    query: { question: session?.question ?? question, query: result?.query ?? conditions?.productType.value ?? "" },
  };

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <Link href="/" className={styles.brand}>PRA / SHOPPING RESEARCH</Link>
        <nav aria-label="사용자 메뉴">
          <Link href="/chat">상품 탐색</Link>
          <a href="#conditions">조건 확인</a>
          <span>CODEX / MCP</span>
        </nav>
      </header>

      <section className={styles.flowBar} aria-label="구매 조사 진행 단계">
        <span className={flowState !== "idle" ? styles.activeStep : ""}>01 질문</span>
        <span className={["draft", "searching", "success"].includes(flowState) ? styles.activeStep : ""}>02 AI 조건 정리</span>
        <span className={["searching", "success"].includes(flowState) ? styles.activeStep : ""}>03 사용자 확인</span>
        <span className={flowState === "success" ? styles.activeStep : ""}>04 DB 후보</span>
      </section>

      <section className={styles.workspace}>
        <article className={styles.brief}>
          <div className={styles.runtimeRow}>
            <p className={styles.orangeLabel}>AGENT RUNTIME / SELECT</p>
            <label>
              <span>실행 환경</span>
              <select value={runtime} onChange={() => setRuntime("codex")}>
                <option value="codex">Codex CLI + Purchase Research Plugin</option>
              </select>
            </label>
          </div>

          <form className={styles.questionForm} onSubmit={handleQuestionSubmit}>
            <label htmlFor="shopping-question">무엇을 찾고 있나요?</label>
            <textarea
              id="shopping-question"
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              rows={4}
              maxLength={1000}
            />
            <button type="submit" disabled={flowState === "structuring" || flowState === "searching"}>
              {flowState === "structuring" ? "CODEX가 조건을 정리하는 중" : "AI에게 조건 정리 요청"}
            </button>
          </form>

          <div className={styles.researchNote}>
            <p className={styles.sectionLabel}>RESEARCH NOTE</p>
            {flowState === "idle" && <p>질문을 보내면 Codex가 조건만 정리합니다. 아직 상품 검색은 시작하지 않습니다.</p>}
            {flowState === "structuring" && <p>Codex CLI가 Plugin 규칙과 공통 Schema에 맞춰 질문을 분석하고 있습니다.</p>}
            {flowState === "searching" && <p>확인한 조건을 MCP Server가 Product Backend에 전달하고 있습니다.</p>}
            {flowState === "error" && <p className={styles.errorMessage}>{errorMessage}</p>}
            {flowState === "success" && result && <p>PostgreSQL에서 “{result.query}” 상품 {result.totalCount}개를 확인했습니다.</p>}
          </div>

          {conditions && (flowState === "draft" || flowState === "error") && (
            <section className={styles.conditionPanel} id="conditions">
              <div className={styles.conditionTitle}>
                <div>
                  <p className={styles.orangeLabel}>AI PURCHASE CONDITION</p>
                  <h2>제가 이해한 조건이 맞나요?</h2>
                </div>
                <span>CONFIDENCE {Math.round(conditions.confidence * 100)}%</span>
              </div>
              {conditions.missingConditions.length > 0 && (
                <div className={styles.missingAlert}>
                  추가 확인이 필요한 조건: {conditions.missingConditions.join(" / ")}<br />
                  아래 값을 수정하거나 비워 둔 상태도 괜찮다면 그대로 확인해 주세요.
                </div>
              )}
              <div className={styles.conditionGrid}>
                <label>상품 종류<input value={conditions.productType.value} onChange={(event) => updateTextCondition("productType", event.target.value)} /><select aria-label="상품 종류 강도" value={conditions.productType.priority} onChange={(event) => updatePriority("productType", event.target.value as ConditionPriority)}><option value="required">필수</option><option value="preferred">선호</option></select></label>
                <label>용도<input value={formatList(conditions.usage)} onChange={(event) => updateListCondition("usage", event.target.value)} /><select aria-label="용도 강도" value={conditions.usage[0]?.priority ?? "preferred"} onChange={(event) => updatePriority("usage", event.target.value as ConditionPriority)}><option value="required">필수</option><option value="preferred">선호</option></select></label>
                <label>최대 가격<input type="number" min="0" value={conditions.price.max ?? ""} onChange={(event) => updateMaxPrice(event.target.value)} /><select aria-label="가격 강도" value={conditions.price.priority} onChange={(event) => updatePriority("price", event.target.value as ConditionPriority)}><option value="required">필수</option><option value="preferred">선호</option></select></label>
                <label>색상<input value={formatList(conditions.colors)} onChange={(event) => updateListCondition("colors", event.target.value)} /><select aria-label="색상 강도" value={conditions.colors[0]?.priority ?? "preferred"} onChange={(event) => updatePriority("colors", event.target.value as ConditionPriority)}><option value="required">필수</option><option value="preferred">선호</option></select></label>
                <label>사이즈<input value={formatList(conditions.sizes)} onChange={(event) => updateListCondition("sizes", event.target.value)} /><select aria-label="사이즈 강도" value={conditions.sizes[0]?.priority ?? "required"} onChange={(event) => updatePriority("sizes", event.target.value as ConditionPriority)}><option value="required">필수</option><option value="preferred">선호</option></select></label>
                <label>중요 조건<input value={formatList(conditions.requirements)} onChange={(event) => updateListCondition("requirements", event.target.value)} /><select aria-label="중요 조건 강도" value={conditions.requirements[0]?.priority ?? "preferred"} onChange={(event) => updatePriority("requirements", event.target.value as ConditionPriority)}><option value="required">필수</option><option value="preferred">선호</option></select></label>
                <label>판매처<input value={conditions.merchant ?? ""} placeholder="전체" onChange={(event) => updateTextCondition("merchant", event.target.value)} /></label>
              </div>
              {conditions.assumptions.length > 0 && <p className={styles.assumptions}>AI 추론: {conditions.assumptions.join(" / ")}</p>}
              <div className={styles.confirmActions}>
                <button type="button" className={styles.secondaryButton} onClick={() => setFlowState("idle")}>질문 다시 쓰기</button>
                <button type="button" className={styles.confirmButton} onClick={() => void handleConfirm()}>이 조건으로 검색</button>
              </div>
            </section>
          )}

          {flowState === "success" && (
            <ol className={styles.reasonList}>
              {candidates.map((candidate, index) => (
                <li key={`${candidate.merchant}-${candidate.externalId}`}>
                  <em>{String(index + 1).padStart(2, "0")}</em>
                  <div>
                    <strong>{candidate.merchant.toUpperCase()} 최신 수집 후보</strong>
                    <span>{candidate.name} / {formatProductPrice(candidate.price)}</span>
                    {assessments.get(candidate.id) && (
                      <small>
                        검색 신호: keyword {assessments.get(candidate.id)?.keywordScore.toFixed(2)}
                        {assessments.get(candidate.id)?.semanticScore != null && ` / vector ${assessments.get(candidate.id)?.semanticScore?.toFixed(2)}`}
                        {` / 최신성 ${assessments.get(candidate.id)?.freshnessScore.toFixed(2)}`}
                        {` / 근거 ${assessments.get(candidate.id)?.evidenceCompletenessScore.toFixed(2)}`}
                      </small>
                    )}
                    {assessments.get(candidate.id)?.matchReasons.map((reason) => <small key={reason}>일치: {reason}</small>)}
                    {assessments.get(candidate.id)?.relaxedConditions.map((reason) => <small key={reason}>선호 완화: {reason}</small>)}
                    {assessments.get(candidate.id)?.unknownConditions.map((reason) => <small key={reason}>확인 필요: {reason}</small>)}
                  </div>
                </li>
              ))}
              {candidates.length === 0 && <li className={styles.emptyResult}>확정 조건과 일치하는 DB 상품이 없습니다.</li>}
            </ol>
          )}

          <div className={styles.evidence} id="evidence">
            {result?.totalCount ?? 0} DB MATCHES / {merchantCount} MERCHANTS / {session?.status ?? "NOT SEARCHED"}
          </div>
        </article>

        <aside className={styles.shortlist}>
          <p className={styles.shortlistLabel}>CONFIRMED EDIT / EVIDENCE FIRST</p>
          <h2>THE SHORTLIST</h2>
          {featured ? (
            <>
              <div className={styles.featured}>
                <div className={styles.productImage}>
                  <Image src={featured.imageUrls[0] ?? "/images/landing-v2/hero-abcmart.jpeg"} alt={`${featured.merchant} ${featured.name}`} fill priority sizes="(max-width: 900px) 80vw, 350px" />
                </div>
                <span className={styles.rank}>01</span>
                <div className={styles.productSummary}>
                  <small>{featured.merchant.toUpperCase()}</small><strong>{featured.name}</strong><b>{formatProductPrice(featured.price)}</b>
                  <p>{featured.stockStatus === "available" ? "재고 있음" : "재고 확인 필요"}<br />{new Date(featured.source.collectedAt).toLocaleString("ko-KR")} 수집</p>
                </div>
              </div>
              {candidates.slice(1).map((candidate, index) => (
                <div className={styles.alternative} key={`${candidate.merchant}-${candidate.externalId}`}><span>{String(index + 2).padStart(2, "0")} {candidate.merchant.toUpperCase()} / {candidate.name}</span><b>{formatProductPrice(candidate.price)}</b></div>
              ))}
              <Link className={styles.compareLink} href={compareHref}>실제 DB 상품 자세히 비교하기 →</Link>
            </>
          ) : (
            <div className={styles.shortlistEmpty}>
              <span>{flowState === "searching" ? "MCP SEARCHING" : "WAITING FOR CONFIRMATION"}</span>
              <p>AI가 정리한 조건을 사용자가 확인한 뒤에만 상품 후보가 이곳에 표시됩니다.</p>
            </div>
          )}
        </aside>
      </section>
    </main>
  );
}
