"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  candidateAvailability,
  candidateGroups,
  candidateListingLabel,
  fetchProductCandidates,
  formatProductPrice,
  ProductCandidateResponse,
  selectedCandidateListing,
} from "../lib/product-candidates";
import styles from "./page.module.css";

const proofLabels = ["BEST MATCH", "ALTERNATIVE", "SOURCE LINKED", "DB SNAPSHOT", "MORE TO EXPLORE"] as const;
const proofTones = ["lime", "blue", "orange", "lime", "blue"] as const;

/** CompareExperience는 URL의 질문과 검색어를 사용해 실제 DB 후보 세 개를 비교한다. */
export default function CompareExperience() {
  const [result, setResult] = useState<ProductCandidateResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [selectedListings, setSelectedListings] = useState<Record<string, number>>({});

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

  const groups = candidateGroups(result);
  const products = groups.map((group) => selectedCandidateListing(
    group,
    selectedListings[group.groupId],
  ).product);
  const firstProduct = products[0];

  return (
    <main className={styles.page}>
      <div className={styles.topline}>
        <Link href="/chat">← BACK TO CHAT</Link>
        <span>05 / BUYING BOARD V2</span>
      </div>
      <header className={styles.heading}>
        <div><h1>FIVE PRODUCT FAMILIES.</h1><p>{result?.question ?? "PostgreSQL 상품 후보를 불러오고 있습니다."}</p></div>
        <strong>{result ? `${result.totalCount} DB MATCHES` : "SOURCE CHECKING"}</strong>
      </header>

      {errorMessage && <p className={styles.errorMessage}>{errorMessage}</p>}
      <section className={styles.productGrid} aria-label="실제 DB 상품 비교">
        {groups.map((group, index) => {
          const listing = selectedCandidateListing(group, selectedListings[group.groupId]);
          const product = listing.product;
          const availability = candidateAvailability(listing);
          return (
          <article className={styles.card} key={group.groupId}>
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
            <p>{availability.label} / {new Date(product.source.collectedAt).toLocaleString("ko-KR")}</p>
            {group.listings.length > 1 ? (
              <div className={styles.variantSelector} aria-label={`${group.name} 판매처 상품 선택`}>
                {group.listings.map((choice) => {
                  const choiceAvailability = candidateAvailability(choice);
                  return (
                    <button
                      key={choice.product.id}
                      type="button"
                      className={`${choice.product.id === product.id ? styles.selectedVariant : ""} ${choiceAvailability.tone === "unavailable" ? styles.unavailableVariant : ""}`}
                      onClick={() => setSelectedListings((current) => ({
                        ...current,
                        [group.groupId]: choice.product.id,
                      }))}
                      title={`${candidateListingLabel(choice)} / ${choiceAvailability.label}`}
                      aria-label={`${candidateListingLabel(choice)} / ${choiceAvailability.label}`}
                    >
                      {choice.product.imageUrls[0] ? <Image src={choice.product.imageUrls[0]} alt="" fill sizes="58px" /> : <span>{choice.attributes.color?.[0] ?? "?"}</span>}
                    </button>
                  );
                })}
              </div>
            ) : Object.keys(listing.attributes).length === 0 ? (
              <p className={styles.optionNotice}>상세 옵션 확인 필요</p>
            ) : null}
            <a className={styles.merchantLink} href={product.productUrl} target="_blank" rel="noreferrer">
              선택한 판매처 상품 보기
            </a>
            <strong className={`${styles.proof} ${styles[proofTones[index] ?? "lime"]}`}>{proofLabels[index] ?? "DB CANDIDATE"}</strong>
          </article>
          );
        })}
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
