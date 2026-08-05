import Image from "next/image";
import Link from "next/link";
import styles from "./page.module.css";

const products = [
  { rank: "01", merchant: "ABC-MART", name: "페니 로퍼", price: "₩69,000", image: "/images/landing-v2/hero-abcmart.jpeg", proof: "PRICE WINNER", tone: "lime" },
  { rank: "02", merchant: "29CM", name: "레더 로퍼", price: "₩79,000", image: "/images/landing-v2/hero-29cm-main.jpeg", proof: "BEST BALANCE", tone: "blue" },
  { rank: "03", merchant: "29CM", name: "더비 슈즈", price: "₩92,000", image: "/images/landing-v2/hero-29cm-side.jpeg", proof: "REVIEW PICK", tone: "orange" },
] as const;

/**
 * ComparePage는 추천 후보 세 개의 가격과 재고와 검증 근거를 나란히 보여준다.
 * 현재 상품 데이터는 디자인 검증용 예시이며 이후 Product Backend 조회 결과로 교체한다.
 */
export default function ComparePage() {
  return (
    <main className={styles.page}>
      <div className={styles.topline}>
        <Link href="/chat">← BACK TO CHAT</Link>
        <span>05 / BUYING BOARD V2</span>
      </div>
      <header className={styles.heading}>
        <div><h1>THREE GOOD OPTIONS.</h1><p>출근용 검정 구두 / 270 / 10만원 이하 / 재고 확인</p></div>
        <strong>ALL SOURCES MATCHED</strong>
      </header>

      <section className={styles.productGrid} aria-label="추천 상품 비교">
        {products.map((product) => (
          <article className={styles.card} key={product.rank}>
            <div className={styles.cardHeader}><em>{product.rank}</em><span>{product.merchant}</span></div>
            <div className={styles.imageWrap}>
              <Image src={product.image} alt={`${product.merchant} ${product.name}`} fill sizes="(max-width: 900px) 90vw, 390px" />
            </div>
            <h2>{product.name}</h2>
            <b>{product.price}</b>
            <p>SIZE 270 / IN STOCK</p>
            <strong className={`${styles.proof} ${styles[product.tone]}`}>{product.proof}</strong>
          </article>
        ))}
      </section>

      <section className={styles.receipt}>
        <p>DECISION RECEIPT</p>
        <div>
          <em>01</em>
          <section><h2>ABC마트 페니 로퍼를 먼저 확인하세요.</h2><p>같은 조건에서 가장 저렴하고 270 재고가 확인됐습니다. 결제 전 상품 페이지에서 재고를 다시 검증합니다.</p></section>
          <strong>2 SOURCES<br />14:32:08</strong>
        </div>
      </section>
    </main>
  );
}
