import styles from "./page.module.css";

/**
 * NewCollectionRequestForm은 판매처/검색어/페이지범위와 선택 필터로 새 수집 job을 등록한다.
 * 일반 `<form>` POST로 제출해(JS fetch 없음) 브라우저 확장 프로그램·보안 소프트웨어가 fetch/XHR을
 * 가로막는 환경에서도 항상 동작하고, 결과는 리다이렉트 뒤 쿼리 파라미터 배너로 보여준다.
 */
export default function NewCollectionRequestForm() {
  return (
    <form className={styles.form} method="POST" action="/api/collection-tasks">
      <div className={styles.field}>
        <label htmlFor="merchant">판매처</label>
        <input id="merchant" name="merchant" type="text" placeholder="29cm" required pattern="^[a-z0-9][a-z0-9-]*$" />
      </div>
      <div className={styles.field}>
        <label htmlFor="query">검색어</label>
        <input id="query" name="query" type="text" placeholder="구두" required />
      </div>
      <div className={styles.field}>
        <label htmlFor="startPage">시작 페이지</label>
        <input id="startPage" name="startPage" type="number" min={1} max={200} defaultValue={1} />
      </div>
      <div className={styles.field}>
        <label htmlFor="pageCount">페이지 수</label>
        <input id="pageCount" name="pageCount" type="number" min={1} max={200} defaultValue={1} />
      </div>
      <div className={styles.field}>
        <label htmlFor="limit">페이지당 상품 수</label>
        <input id="limit" name="limit" type="number" min={1} max={50} placeholder="30" />
      </div>
      <div className={styles.field}>
        <label htmlFor="locale">지역</label>
        <input id="locale" name="locale" type="text" placeholder="ko-KR" pattern="^[a-z]{2}-[A-Z]{2}$" />
      </div>
      <div className={styles.field}>
        <label htmlFor="currency">통화</label>
        <input id="currency" name="currency" type="text" placeholder="KRW" pattern="^[A-Z]{3}$" />
      </div>
      <div className={styles.field}>
        <label htmlFor="priceMin">최소 가격</label>
        <input id="priceMin" name="priceMin" type="number" min={0} />
      </div>
      <div className={styles.field}>
        <label htmlFor="priceMax">최대 가격</label>
        <input id="priceMax" name="priceMax" type="number" min={0} />
      </div>
      <div className={styles.field}>
        <label htmlFor="sizes">사이즈(쉼표 구분)</label>
        <input id="sizes" name="sizes" type="text" placeholder="260, 270" />
      </div>
      <div className={styles.field}>
        <label htmlFor="colors">색상(쉼표 구분)</label>
        <input id="colors" name="colors" type="text" placeholder="black, white" />
      </div>
      <div className={`${styles.field} ${styles.fieldWide}`}>
        <label htmlFor="categories">카테고리(쉼표 구분)</label>
        <input id="categories" name="categories" type="text" placeholder="shoes" />
      </div>
      <div className={`${styles.checkboxRow} ${styles.fieldWide}`}>
        <input id="inStockOnly" name="inStockOnly" type="checkbox" />
        <label htmlFor="inStockOnly">재고 보유 상품만</label>
      </div>

      <div className={styles.actions}>
        <button type="submit" className={styles.submitButton}>
          수집 요청
        </button>
      </div>
    </form>
  );
}
