"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, useState } from "react";
import styles from "./page.module.css";

const defaultQuestion = "출근용 검정 구두를 10만원 안에서 찾고 있어";

/**
 * ChatExperience는 구매 질문 입력과 추천 결과 확인 흐름을 화면 안에서 시연한다.
 * 실제 모델 호출 전 단계이므로 제출된 문장을 예시 추천 결과의 질문 문구에 반영한다.
 */
export default function ChatExperience() {
  const [question, setQuestion] = useState(defaultQuestion);
  const [draft, setDraft] = useState("");

  /** 입력된 구매 조건을 현재 쇼핑 브리프에 반영한다. */
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextQuestion = draft.trim();

    if (!nextQuestion) {
      return;
    }

    setQuestion(nextQuestion);
    setDraft("");
  }

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
            조건에 맞는 상품 18개를 확인했습니다.<br />
            가격과 재고와 출처가 모두 확인된 3개를 먼저 보여드릴게요.
          </p>

          <ol className={styles.reasonList}>
            <li><em>01</em><div><strong>가장 저렴함</strong><span>ABC마트 페니 로퍼 / ₩69,000</span></div></li>
            <li><em>02</em><div><strong>출근용 균형</strong><span>29CM 레더 로퍼 / ₩79,000</span></div></li>
            <li><em>03</em><div><strong>후기 근거</strong><span>사이즈 관련 공개 리뷰 신호 확인</span></div></li>
          </ol>

          <div className={styles.evidence} id="evidence">
            3 PRODUCTS / 2 MERCHANTS / ALL SOURCES VERIFIED
          </div>
        </article>

        <aside className={styles.shortlist}>
          <p className={styles.shortlistLabel}>TODAY&apos;S EDIT / WORK SHOES</p>
          <h2>THE SHORTLIST</h2>
          <div className={styles.featured}>
            <div className={styles.productImage}>
              <Image
                src="/images/landing-v2/hero-abcmart.jpeg"
                alt="ABC마트 페니 로퍼"
                fill
                priority
                sizes="(max-width: 900px) 80vw, 350px"
              />
            </div>
            <span className={styles.rank}>01</span>
            <div className={styles.productSummary}>
              <small>ABC-MART</small>
              <strong>페니 로퍼</strong>
              <b>₩69,000</b>
              <p>270 재고 있음<br />2개 출처 일치</p>
            </div>
          </div>
          <div className={styles.alternative}><span>02 29CM / 레더 로퍼</span><b>₩79,000</b></div>
          <div className={styles.alternative}><span>03 29CM / 더비 슈즈</span><b>₩92,000</b></div>
          <Link className={styles.compareLink} href="/compare">세 상품 자세히 비교하기 →</Link>
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
        <button type="submit" aria-label="질문 보내기">→</button>
      </form>
    </main>
  );
}
