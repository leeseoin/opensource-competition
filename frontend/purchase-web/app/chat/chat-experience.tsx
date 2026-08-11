"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, useState } from "react";

import { AppSelect, type AppSelectOption } from "../components/ui/app-select";
import {
  candidateAvailability,
  candidateGroups,
  candidateListingLabel,
  formatProductPrice,
  selectedCandidateListing,
  type CandidateGroup,
} from "../lib/product-candidates";
import { parsePrioritizedList } from "../lib/condition-editing";
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
const runtimeOptions: AppSelectOption[] = [
  { value: "codex", label: "Codex CLI + Purchase Research Plugin" },
];
const priorityOptions: AppSelectOption[] = [
  { value: "required", label: "필수" },
  { value: "preferred", label: "선호" },
];

type FlowState = "idle" | "structuring" | "draft" | "searching" | "success" | "error";

/** 조건 배열을 사람이 수정하기 쉬운 한 줄 문자열로 변환한다. */
function formatList(value: PrioritizedText[]): string {
  return value.map((item) => item.value).join(", ");
}

interface ShoppingProductCardProps {
  group: CandidateGroup;
  rank: number;
  selectedListingId?: number;
  onSelect(listingId: number): void;
}

/** ShoppingProductCard는 상품군 하나를 이미지/가격/컬러/재고 중심의 쇼핑 카드로 표시한다. */
function ShoppingProductCard({
  group,
  rank,
  selectedListingId,
  onSelect,
}: ShoppingProductCardProps) {
  const listing = selectedCandidateListing(group, selectedListingId);
  const candidate = listing.product;
  const assessment = listing.assessment;
  const availability = candidateAvailability(listing);
  const colors = listing.attributes.color ?? [];
  const sizes = listing.attributes.size ?? [];

  return (
    <article className={styles.shopCard}>
      <header className={styles.shopCardHeader}>
        <span>추천 {String(rank).padStart(2, "0")}</span>
        <span>{candidate.merchant.toUpperCase()}</span>
      </header>
      <div className={styles.shopImage}>
        <Image
          src={candidate.imageUrls[0] ?? "/images/landing-v2/hero-abcmart.jpeg"}
          alt={`${candidate.brand ?? candidate.merchant} ${candidate.name}`}
          fill
          priority={rank <= 2}
          sizes="(max-width: 700px) 92vw, (max-width: 1100px) 44vw, 30vw"
        />
        <span className={`${styles.stockBadge} ${styles[availability.tone]}`}>{availability.label}</span>
      </div>
      <div className={styles.shopCardBody}>
        <p className={styles.shopMeta}>{candidate.brand ?? candidate.merchant} / {candidate.categoryPath.at(-1) ?? "상품"}</p>
        <h3>{candidate.name}</h3>
        <strong className={styles.shopPrice}>{formatProductPrice(candidate.price)}</strong>
        <p className={styles.variantCount}>컬러/판매 행 {group.listings.length}개</p>
        <div className={styles.swatchRow} aria-label={`${group.name} 컬러 선택`}>
          {group.listings.map((choice) => {
            const choiceAvailability = candidateAvailability(choice);
            return (
              <button
                key={choice.product.id}
                type="button"
                className={`${styles.swatch} ${choice.product.id === candidate.id ? styles.activeSwatch : ""} ${choiceAvailability.tone === "unavailable" ? styles.unavailableSwatch : ""}`}
                onClick={() => onSelect(choice.product.id)}
                title={`${candidateListingLabel(choice)} / ${choiceAvailability.label}`}
                aria-label={`${candidateListingLabel(choice)} / ${choiceAvailability.label}`}
              >
                {choice.product.imageUrls[0] ? (
                  <Image src={choice.product.imageUrls[0]} alt="" fill sizes="56px" />
                ) : (
                  <span>{choice.attributes.color?.[0] ?? "?"}</span>
                )}
              </button>
            );
          })}
        </div>
        <div className={styles.selectedVariantInfo}>
          <span>{colors.join("/") || "컬러 확인 필요"}</span>
          <small>상품번호 {candidate.externalId}</small>
          <small>{sizes.length > 0 ? `구매 가능 사이즈 ${sizes.slice(0, 6).join("/")}${sizes.length > 6 ? " 외" : ""}` : "상세 옵션 확인 필요"}</small>
        </div>
        <a className={styles.shopLink} href={candidate.productUrl} target="_blank" rel="noreferrer">
          이 컬러 판매처에서 보기
        </a>
        {assessment && (
          <details className={styles.reasonDetails}>
            <summary>추천 근거와 주의사항</summary>
            {assessment.matchReasons.map((reason) => <small key={reason}>일치 / {reason}</small>)}
            {assessment.relaxedConditions.map((reason) => <small key={reason}>선호 완화 / {reason}</small>)}
            {assessment.unknownConditions.map((reason) => <small key={reason}>확인 필요 / {reason}</small>)}
          </details>
        )}
      </div>
    </article>
  );
}

/** ChatExperience는 Codex 조건 구조화부터 사용자 확인과 MCP 상품 검색까지 제공한다. */
export default function ChatExperience() {
  const [question, setQuestion] = useState(defaultQuestion);
  const [runtime, setRuntime] = useState<"codex">("codex");
  const [session, setSession] = useState<ResearchSessionResponse | null>(null);
  const [conditions, setConditions] = useState<PurchaseCondition | null>(null);
  const [flowState, setFlowState] = useState<FlowState>("idle");
  const [errorMessage, setErrorMessage] = useState("");
  const [selectedListings, setSelectedListings] = useState<Record<string, number>>({});

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
    setSelectedListings({});
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
      const fallbackPriority = field === "sizes" ? "required" : "preferred";
      setConditions({
        ...conditions,
        [field]: parsePrioritizedList(value, conditions[field], fallbackPriority),
      });
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
  const groups = candidateGroups(result);
  const selectedCandidates = groups.map((group) => selectedCandidateListing(
    group,
    selectedListings[group.groupId],
  ).product);
  const featured = selectedCandidates[0];
  const merchantCount = new Set(selectedCandidates.map((candidate) => candidate.merchant)).size;
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

      <section className={`${styles.workspace} ${flowState === "success" ? styles.shoppingWorkspace : ""}`}>
        <article className={styles.brief}>
          {flowState !== "success" && (
            <>
              <div className={styles.runtimeRow}>
                <p className={styles.orangeLabel}>AGENT RUNTIME / SELECT</p>
                <label>
                  <span>실행 환경</span>
                  <AppSelect
                    ariaLabel="실행 환경"
                    value={runtime}
                    options={runtimeOptions}
                    onValueChange={() => setRuntime("codex")}
                    tone="lime"
                  />
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
            </>
          )}

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
                <label>상품 종류<input value={conditions.productType.value} onChange={(event) => updateTextCondition("productType", event.target.value)} /><AppSelect ariaLabel="상품 종류 강도" value={conditions.productType.priority} options={priorityOptions} onValueChange={(value) => updatePriority("productType", value as ConditionPriority)} /></label>
                <label>용도<input value={formatList(conditions.usage)} onChange={(event) => updateListCondition("usage", event.target.value)} /><AppSelect ariaLabel="용도 강도" value={conditions.usage[0]?.priority ?? "preferred"} options={priorityOptions} onValueChange={(value) => updatePriority("usage", value as ConditionPriority)} /></label>
                <label>최대 가격<input type="number" min="0" value={conditions.price.max ?? ""} onChange={(event) => updateMaxPrice(event.target.value)} /><AppSelect ariaLabel="가격 강도" value={conditions.price.priority} options={priorityOptions} onValueChange={(value) => updatePriority("price", value as ConditionPriority)} /></label>
                <label>색상<input value={formatList(conditions.colors)} onChange={(event) => updateListCondition("colors", event.target.value)} /><AppSelect ariaLabel="색상 강도" value={conditions.colors[0]?.priority ?? "preferred"} options={priorityOptions} onValueChange={(value) => updatePriority("colors", value as ConditionPriority)} /></label>
                <label>사이즈<input value={formatList(conditions.sizes)} onChange={(event) => updateListCondition("sizes", event.target.value)} /><AppSelect ariaLabel="사이즈 강도" value={conditions.sizes[0]?.priority ?? "required"} options={priorityOptions} onValueChange={(value) => updatePriority("sizes", value as ConditionPriority)} /></label>
                <label>중요 조건<input value={formatList(conditions.requirements)} onChange={(event) => updateListCondition("requirements", event.target.value)} /><AppSelect ariaLabel="중요 조건 강도" value={conditions.requirements[0]?.priority ?? "preferred"} options={priorityOptions} onValueChange={(value) => updatePriority("requirements", value as ConditionPriority)} /></label>
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
            <section className={styles.shopResults} aria-label="추천 상품군">
              <div className={styles.shopResultsHeader}>
                <div><span>CURATED FROM LIVE MERCHANT DATA</span><h2>조건에 맞춘 추천 상품군</h2></div>
                <div className={styles.shopResultsActions}>
                  <p>조건을 통과한 상품군은 최대 5개까지 보여줍니다.<br />카드 안의 썸네일을 눌러 다른 컬러와 재고 상태를 확인하세요.</p>
                  <button type="button" onClick={() => setFlowState("idle")}>새 상품 검색</button>
                </div>
              </div>
              <div className={styles.shopGrid}>
                {groups.map((group, index) => (
                  <ShoppingProductCard
                    key={group.groupId}
                    group={group}
                    rank={index + 1}
                    selectedListingId={selectedListings[group.groupId]}
                    onSelect={(listingId) => setSelectedListings((current) => ({
                      ...current,
                      [group.groupId]: listingId,
                    }))}
                  />
                ))}
              </div>
              {groups.length === 0 && <p className={styles.emptyResult}>확정 조건과 일치하는 DB 상품이 없습니다.</p>}
            </section>
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
              {selectedCandidates.slice(1).map((candidate, index) => (
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
