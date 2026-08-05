"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  fetchProductCandidates,
  formatProductPrice,
  ProductCandidateResponse,
} from "../lib/product-candidates";
import styles from "./page.module.css";

const proofLabels = ["LATEST CANDIDATE", "SOURCE LINKED", "DB SNAPSHOT"] as const;
const proofTones = ["lime", "blue", "orange"] as const;

/** CompareExperience는 URL의 질문과 검색어를 사용해 실제 DB 후보 세 개를 비교한다. */
export default function CompareExperience() {
  const [result, setResult] = useState<ProductCandidateResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState("");

  /** 비교 화면 진입 시 chat 화면이 넘긴 질문과 검색어로 후보를 다시 조회한다. */
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const question = params.get("question") ?? "출근용 검정 구두를 10만원 안에서 찾고 있어";
    const query = params.get("query") ?? "구두";

    void fetchProductCandidates(question, query)
      .then(setResult)
      .catch((error: unknown) => {
        setErrorMessage(error instanceof Error ? error.message : "상품 후보를 불러오지 못했습니다.");
      });
  }, []);

  const products = result?.candidates ?? [];
  const firstProduct = products[0];

  return (
    <main className={styles.page}>
      <div className={styles.topline}>
        <Link href="/chat">← BACK TO CHAT</Link>
        <span>05 / BUYING BOARD V2</span>
      </div>
      <header className={styles.heading}>
        <div><h1>THREE DB OPTIONS.</h1><p>{result?.question ?? "PostgreSQL 상품 후보를 불러오고 있습니다."}</p></div>
        <strong>{result ? `${result.totalCount} DB MATCHES` : "SOURCE CHECKING"}</strong>
      </header>

      {errorMessage && <p className={styles.errorMessage}>{errorMessage}</p>}
      <section className={styles.productGrid} aria-label="실제 DB 상품 비교">
        {products.map((product, index) => (
          <article className={styles.card} key={`${product.merchant}-${product.externalId}`}>
            <div className={styles.cardHeader}><em>{String(index + 1).padStart(2, "0")}</em><span>{product.merchant.toUpperCase()}</span></div>
            <div className={styles.imageWrap}>
              <Image
                src={product.imageUrls[0] ?? "/images/landing-v2/hero-abcmart.jpeg"}
                alt={`${product.merchant} ${product.name}`}
                fill
                sizes="(max-width: 900px) 90vw, 390px"
              />
            </div>
            <h2>{product.name}</h2>
            <b>{formatProductPrice(product.price)}</b>
            <p>{product.stockStatus.toUpperCase()} / {new Date(product.source.collectedAt).toLocaleString("ko-KR")}</p>
            <strong className={`${styles.proof} ${styles[proofTones[index] ?? "lime"]}`}>{proofLabels[index] ?? "DB CANDIDATE"}</strong>
          </article>
        ))}
      </section>

      {firstProduct && (
        <section className={styles.receipt}>
          <p>CANDIDATE RECEIPT</p>
          <div>
            <em>01</em>
            <section><h2>{firstProduct.merchant.toUpperCase()}의 {firstProduct.name}부터 확인해 보세요.</h2><p>최근 DB snapshot 후보입니다. 결제 전 상품 페이지에서 가격과 재고를 다시 검증해야 합니다.</p></section>
            <strong>1 SOURCE<br />{new Date(firstProduct.source.collectedAt).toLocaleTimeString("ko-KR")}</strong>
          </div>
        </section>
      )}
    </main>
  );
}
