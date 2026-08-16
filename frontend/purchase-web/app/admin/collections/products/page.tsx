import ProductsRecheck from "./products-recheck";
import { LIMIT_OPTIONS, loadBatchStatus, loadProductsData } from "./products-data";
import styles from "./page.module.css";

export const dynamic = "force-dynamic";

const DEFAULT_LIMIT = 100;

/**
 * ProductsRecheckPage는 마지막 수집 시각 기준으로 상품을 정렬해 보여주고, 선택한 상품을
 * 골라 재검증(재수집)을 요청하는 관리 화면이다.
 */
export default async function ProductsRecheckPage({
  searchParams,
}: {
  searchParams: Promise<{
    merchant?: string;
    sort?: string;
    limit?: string;
    staleHours?: string;
    batchId?: string;
    error?: string;
  }>;
}) {
  const { merchant, sort, limit, staleHours, batchId, error } = await searchParams;
  const effectiveSort = sort === "latest" ? "latest" : "oldest";
  const parsedLimit = Number(limit);
  const effectiveLimit = LIMIT_OPTIONS.includes(parsedLimit as (typeof LIMIT_OPTIONS)[number])
    ? parsedLimit
    : DEFAULT_LIMIT;
  const parsedStaleHours = Number(staleHours);
  const effectiveStaleHours =
    staleHours && Number.isInteger(parsedStaleHours) && parsedStaleHours >= 0 ? parsedStaleHours : undefined;
  const [data, batch] = await Promise.all([
    loadProductsData(merchant, effectiveSort, effectiveLimit, effectiveStaleHours),
    batchId ? loadBatchStatus(batchId) : Promise.resolve(null),
  ]);

  return (
    <main className={styles.page}>
      <header className={styles.heading}>
        <h1>PRODUCT RECHECK</h1>
        <p>마지막 수집 시각이 오래된 상품을 찾아 선택 재검증(재수집)을 요청합니다.</p>
      </header>

      {data.errorMessage && <p className={styles.errorMessage}>{data.errorMessage}</p>}
      {error && <p className={styles.errorMessage}>{error}</p>}
      {batchId && (
        <div className={styles.successMessage}>
          {batch ? (
            <>
              <strong>재검증 배치 {batch.status === "COMPLETED" ? "완료" : "진행 중"}</strong>
              <p>
                요청 {batch.requestedCount}건 중 {batch.processedCount}건 처리됨 · {batch.queuedCount}건 큐 등록 성공
              </p>
              {batch.status === "PROCESSING" && (
                <a href={`/admin/collections/products?batchId=${encodeURIComponent(batchId)}`}>새로고침</a>
              )}
            </>
          ) : (
            <p>배치 상태를 불러오지 못했습니다 · batchId {batchId}</p>
          )}
        </div>
      )}

      <ProductsRecheck
        products={data.products}
        merchant={merchant}
        sort={effectiveSort}
        limit={effectiveLimit}
        staleHours={effectiveStaleHours}
      />
    </main>
  );
}
