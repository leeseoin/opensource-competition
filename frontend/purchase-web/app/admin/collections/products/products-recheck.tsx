import { LIMIT_OPTIONS, type ProductRow } from "./products-data";
import styles from "./page.module.css";

/**
 * ProductsRecheck는 상품 목록을 표로 보여주고, 체크박스로 고른 상품을 한 번에
 * 재검증(재수집) 요청하는 폼을 렌더링한다. 클라이언트 자바스크립트 없이 일반
 * `<form>` GET/POST로만 동작해(fetch/XHR 없음), 브라우저 확장 프로그램·보안
 * 소프트웨어가 있어도 항상 동작한다.
 */
export default function ProductsRecheck({
  products,
  merchant,
  sort,
  limit,
  staleHours,
}: {
  products: ProductRow[];
  merchant: string | undefined;
  sort: string;
  limit: number;
  staleHours: number | undefined;
}) {
  return (
    <>
      <form method="GET" className={styles.filterForm}>
        <div className={styles.filterField}>
          <label htmlFor="merchant">판매처</label>
          <select id="merchant" name="merchant" defaultValue={merchant ?? ""}>
            <option value="">전체</option>
            <option value="abcmart">abcmart</option>
            <option value="29cm">29cm</option>
          </select>
        </div>
        <div className={styles.filterField}>
          <label htmlFor="sort">정렬</label>
          <select id="sort" name="sort" defaultValue={sort}>
            <option value="oldest">마지막 수집 오래된 순</option>
            <option value="latest">마지막 수집 최근 순</option>
          </select>
        </div>
        <div className={styles.filterField}>
          <label htmlFor="limit">조회 개수</label>
          <select id="limit" name="limit" defaultValue={String(limit)}>
            {LIMIT_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}건
              </option>
            ))}
          </select>
        </div>
        <div className={styles.filterField}>
          <label htmlFor="staleHours">경과 시간(시간 이상)</label>
          <input
            id="staleHours"
            name="staleHours"
            type="number"
            min={0}
            step={1}
            placeholder="전체"
            defaultValue={staleHours ?? ""}
          />
        </div>
        <button type="submit" className={styles.filterButton}>
          조회
        </button>
      </form>

      <div className={styles.summaryRow}>
        <p className={styles.summaryText}>
          {merchant ?? "전체 판매처"} · {sort === "latest" ? "마지막 수집 최근 순" : "마지막 수집 오래된 순"}
          {staleHours !== undefined && ` · 마지막 수집 ${staleHours}시간 이상 지난 것만`} ·{" "}
          <strong>{products.length}개</strong> 조회됨
        </p>
        <form method="POST" action="/api/offer-verifications/bulk">
          {products.map((product) => (
            <input key={product.id} type="hidden" name="productIds" value={product.id} />
          ))}
          <button type="submit" className={styles.bulkButton} disabled={products.length === 0}>
            조회된 {products.length}건 전체 재검증
          </button>
        </form>
      </div>

      <form method="POST" action="/api/offer-verifications/bulk">
        {products.length === 0 ? (
          <p className={styles.muted}>조회된 상품이 없음</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th aria-label="선택" />
                  <th>판매처</th>
                  <th>상품명</th>
                  <th>브랜드</th>
                  <th>외부 상품번호</th>
                  <th>마지막 수집</th>
                </tr>
              </thead>
              <tbody>
                {products.map((product) => (
                  <tr key={product.id}>
                    <td>
                      <input type="checkbox" name="productIds" value={product.id} aria-label={`${product.name} 선택`} />
                    </td>
                    <td>{product.merchant}</td>
                    <td>{product.name}</td>
                    <td>{product.brand ?? "-"}</td>
                    <td>{product.externalId}</td>
                    <td>{formatElapsed(product.collectedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className={styles.actions}>
          <button type="submit" className={styles.submitButton} disabled={products.length === 0}>
            선택 재검증
          </button>
        </div>
      </form>
    </>
  );
}

/** formatElapsed는 마지막 수집 시각을 절대 시각과 함께 경과 시간으로도 보여준다. */
function formatElapsed(collectedAt: string): string {
  const collected = new Date(collectedAt);
  const elapsedMs = Date.now() - collected.getTime();
  const elapsedHours = Math.floor(elapsedMs / (60 * 60 * 1000));
  const label = collected.toLocaleString("ko-KR");

  if (elapsedHours < 1) {
    return `${label} (1시간 이내)`;
  }
  if (elapsedHours < 24) {
    return `${label} (${elapsedHours}시간 전)`;
  }
  return `${label} (${Math.floor(elapsedHours / 24)}일 전)`;
}
