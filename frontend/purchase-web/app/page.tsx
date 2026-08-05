import Image from "next/image";
import Link from "next/link";
import styles from "./page.module.css";

const landingImages = {
  main: "/images/landing-v2/hero-29cm-main.jpeg",
  abcMart: "/images/landing-v2/hero-abcmart.jpeg",
  side: "/images/landing-v2/hero-29cm-side.jpeg",
  verified: "/images/landing-v2/verified-sticker.svg",
  status: "/images/landing-v2/status-dot.svg",
} as const;

/**
 * Home은 판매처별 상품 탐색과 근거 검증 흐름을 소개하는 공개 랜딩 화면을 제공한다.
 * 정적인 PoC 화면이며 실제 상품 검색은 이후 Agent Gateway 연결 작업에서 활성화한다.
 */
export default function Home() {
  return (
    <main className={styles.page}>
      <nav className={styles.navigation} aria-label="주요 메뉴">
        <a className={styles.brand} href="#top" aria-label="PRA 홈">
          <span className={styles.brandMark} aria-hidden="true" />
          <span>PRA / SHOP WITH PROOF</span>
        </a>

        <div className={styles.navigationLinks}>
          <a href="#discover">DISCOVER</a>
          <a href="#merchants">MERCHANTS</a>
          <a href="#proof">HOW IT WORKS</a>
          <Link className={styles.askButton} href="/chat">
            ASK PRA <span aria-hidden="true">↗</span>
          </Link>
        </div>
      </nav>

      <section className={styles.hero} id="top">
        <div className={styles.heroCopy}>
          <p className={styles.eyebrow}>
            THE OPEN COMMERCE RESEARCH ENGINE / 2026
          </p>
          <h1>
            <span className={styles.editorialTitle}>SHOP LESS.</span>
            <span className={styles.koreanTitle}>더 잘 사는 법.</span>
          </h1>
          <span className={styles.orangeUnderline} aria-hidden="true" />
          <p className={styles.description}>
            수천 개의 상품을 더 오래 보는 대신,
            <br />
            가격과 재고와 출처가 검증된 선택만 만나보세요.
          </p>
        </div>

        <div className={styles.productCollage} aria-label="판매처 상품 미리보기">
          <article className={`${styles.productPhoto} ${styles.mainPhoto}`}>
            <Image
              src={landingImages.main}
              alt="29CM의 검정 구두 상품"
              fill
              priority
              sizes="(max-width: 900px) 72vw, 350px"
            />
            <div className={styles.mainTag}>
              <span>29CM / 기호</span>
              <strong>83,520 KRW</strong>
            </div>
          </article>

          <article className={`${styles.productPhoto} ${styles.abcPhoto}`}>
            <Image
              src={landingImages.abcMart}
              alt="ABC마트의 검정 구두 상품"
              fill
              priority
              sizes="(max-width: 900px) 48vw, 240px"
            />
            <strong className={styles.abcTag}>ABC-MART / 19,000</strong>
          </article>

          <article className={`${styles.productPhoto} ${styles.sidePhoto}`}>
            <Image
              src={landingImages.side}
              alt="29CM의 검정 스트랩 구두 상품"
              fill
              priority
              sizes="(max-width: 900px) 42vw, 220px"
            />
          </article>

          <div className={styles.verifiedSticker}>
            <Image src={landingImages.verified} alt="출처 검증 완료" fill />
            <span>
              SOURCE
              <br />
              VERIFIED
            </span>
          </div>
        </div>

        <div className={styles.searchRow} id="discover">
          <div className={styles.searchCapsule}>
            <span>WHAT ARE YOU LOOKING FOR?</span>
            <strong>검정 로퍼 / 270 / 10만원 이하</strong>
            <Link href="/chat" aria-label="AI에게 상품 질문하기">
              ↗
            </Link>
          </div>

          <div className={styles.liveStatus}>
            <Image src={landingImages.status} alt="" width={8} height={8} />
            <span>10,482 LIVE PRODUCTS</span>
          </div>
        </div>
      </section>

      <div className={styles.ticker} aria-label="지원 기능">
        <p>
          ABC-MART <span>✦</span> 29CM <span>✦</span> PRICE VERIFIED{" "}
          <span>✦</span> STOCK CHECKED <span>✦</span> SOURCE LINKED{" "}
          <span>✦</span> OPEN SOURCE <span>✦</span>
        </p>
      </div>

      <section className={styles.merchantSpaces} id="merchants">
        <article className={`${styles.merchantCard} ${styles.abcMerchant}`}>
          <p>01 / MERCHANT SPACE</p>
          <h2>ABC-MART</h2>
          <span>운동화와 구두 / 옵션 재고까지 확인</span>
          <Link href="/chat">ASK ABOUT ABC-MART →</Link>
        </article>

        <article className={`${styles.merchantCard} ${styles.cmMerchant}`}>
          <p>02 / MERCHANT SPACE</p>
          <h2>29CM</h2>
          <span>디자이너 브랜드 / 리뷰와 평점까지 비교</span>
          <Link href="/chat">ASK ABOUT 29CM →</Link>
        </article>

        <article className={`${styles.merchantCard} ${styles.proofCard}`} id="proof">
          <p>WHY PRA</p>
          <strong>99.2%</strong>
          <span>
            최근 검증 성공률
            <br />
            가격 / 재고 / 출처를 한 번에 확인
          </span>
        </article>
      </section>
    </main>
  );
}
