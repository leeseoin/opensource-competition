"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import {
  deriveProductQuery,
  fetchProductCandidates,
  formatProductPrice,
  ProductCandidateResponse,
} from "../lib/product-candidates";
import styles from "./page.module.css";

const defaultQuestion = "출근용 검정 구두를 10만원 안에서 찾고 있어";

/**
 * ChatExperience는 구매 질문 입력과 추천 결과 확인 흐름을 화면 안에서 시연한다.
 * 실제 모델 호출 전 단계이므로 제출된 문장을 예시 추천 결과의 질문 문구에 반영한다.
 */
export default function ChatExperience() {
  const [question, setQuestion] = useState(defaultQuestion);
  const [draft, setDraft] = useState("");
  const [result, setResult] = useState<ProductCandidateResponse | null>(null);
  const [requestState, setRequestState] = useState<"loading" | "success" | "error">("loading");
  const [errorMessage, setErrorMessage] = useState("");

  /** 첫 화면에서도 예시 질문을 사용해 PostgreSQL 후보를 조회한다. */
  useEffect(() => {
    let active = true;
    void fetchProductCandidates(defaultQuestion)
      .then((response) => {
        if (active) {
          setResult(response);
          setRequestState("success");
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorMessage(error instanceof Error ? error.message : "상품 후보를 불러오지 못했습니다.");
          setRequestState("error");
        }
      });

    return () => {
      active = false;
    };
  }, []);

  /** 입력된 구매 조건을 현재 쇼핑 브리프에 반영한다. */
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextQuestion = draft.trim();

    if (!nextQuestion) {
      return;
    }

    setQuestion(nextQuestion);
    setDraft("");
    setRequestState("loading");
    setErrorMessage("");
    try {
      const response = await fetchProductCandidates(nextQuestion);
      setResult(response);
      setRequestState("success");
    } catch (error) {
      setResult(null);
      setErrorMessage(error instanceof Error ? error.message : "상품 후보를 불러오지 못했습니다.");
      setRequestState("error");
    }
  }

  const candidates = result?.candidates ?? [];
  const featured = candidates[0];
  const merchantCount = new Set(candidates.map((candidate) => candidate.merchant)).size;
  const compareHref = {
    pathname: "/compare",
    query: {
      question: result?.question ?? question,
      query: result?.query ?? deriveProductQuery(question),
    },
  };

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <Link href="/" className={styles.brand}>PRA / SHOPPING RESEARCH</Link>
        <nav aria-label="사용자 메뉴">
          <Link href="/chat">상품 탐색</Link>
          <a href="#evidence">검증 근거</a>
          <span>SESSION 08/05</span>
        </nav>
      </header>

      <section className={styles.workspace}>
        <article className={styles.brief}>
          <p className={styles.orangeLabel}>SHOPPING BRIEF / 001</p>
          <h1>{question}</h1>
          <p className={styles.filters}>SIZE 270 / BLACK / IN STOCK / ₩100,000↓</p>
          <hr />
          <p className={styles.sectionLabel}>RESEARCH NOTE</p>
          <p className={styles.note}>
            {requestState === "loading" && "PostgreSQL에서 최근 수집 상품을 확인하고 있습니다."}
            {requestState === "error" && errorMessage}
            {requestState === "success" && result && (
              <>{`검색어 “${result.query}”와 일치하는 상품 ${result.totalCount}개를 확인했습니다.`}<br />
              가격과 재고 및 출처가 있는 후보 {candidates.length}개를 보여드립니다.</>
            )}
          </p>

          <ol className={styles.reasonList}>
            {candidates.map((candidate, index) => (
              <li key={`${candidate.merchant}-${candidate.externalId}`}>
                <em>{String(index + 1).padStart(2, "0")}</em>
                <div>
                  <strong>{candidate.merchant.toUpperCase()} 최신 수집 후보</strong>
                  <span>{candidate.name} / {formatProductPrice(candidate.price)}</span>
                </div>
              </li>
            ))}
            {requestState === "success" && candidates.length === 0 && (
              <li className={styles.emptyResult}>DB에 일치하는 상품이 없습니다. 다른 상품 검색어로 질문해 주세요.</li>
            )}
          </ol>

          <div className={styles.evidence} id="evidence">
            {result?.totalCount ?? 0} DB MATCHES / {merchantCount} MERCHANTS / SOURCES LINKED
          </div>
        </article>

        <aside className={styles.shortlist}>
          <p className={styles.shortlistLabel}>TODAY&apos;S EDIT / WORK SHOES</p>
          <h2>THE SHORTLIST</h2>
          {featured ? (
            <>
              <div className={styles.featured}>
                <div className={styles.productImage}>
                  <Image
                    src={featured.imageUrls[0] ?? "/images/landing-v2/hero-abcmart.jpeg"}
                    alt={`${featured.merchant} ${featured.name}`}
                    fill
                    priority
                    sizes="(max-width: 900px) 80vw, 350px"
                  />
                </div>
                <span className={styles.rank}>01</span>
                <div className={styles.productSummary}>
                  <small>{featured.merchant.toUpperCase()}</small>
                  <strong>{featured.name}</strong>
                  <b>{formatProductPrice(featured.price)}</b>
                  <p>{featured.stockStatus === "available" ? "재고 있음" : "재고 확인 필요"}<br />
                    {new Date(featured.source.collectedAt).toLocaleString("ko-KR")} 수집</p>
                </div>
              </div>
              {candidates.slice(1).map((candidate, index) => (
                <div className={styles.alternative} key={`${candidate.merchant}-${candidate.externalId}`}>
                  <span>{String(index + 2).padStart(2, "0")} {candidate.merchant.toUpperCase()} / {candidate.name}</span>
                  <b>{formatProductPrice(candidate.price)}</b>
                </div>
              ))}
              <Link className={styles.compareLink} href={compareHref}>실제 DB 상품 자세히 비교하기 →</Link>
            </>
          ) : (
            <div className={styles.shortlistEmpty}>상품 서버 응답을 기다리고 있습니다.</div>
          )}
        </aside>
      </section>

      <form className={styles.composer} onSubmit={handleSubmit}>
        <label className={styles.srOnly} htmlFor="shopping-question">구매 질문</label>
        <input
          id="shopping-question"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="조건을 더 말해 주세요. 예: 굽이 낮고 비 오는 날에도 신을 수 있는 것"
        />
        <button type="submit" aria-label="질문 보내기" disabled={requestState === "loading"}>→</button>
      </form>
    </main>
  );
}
