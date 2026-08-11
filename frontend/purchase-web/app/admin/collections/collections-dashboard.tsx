"use client";

import { useCallback, useEffect, useState } from "react";
import { DashboardSummary, fetchDashboardSummary, StatusCounts } from "../../lib/dashboard-summary";
import styles from "./page.module.css";

/** CollectionsDashboard는 최근 24시간(기본값) 수집/검증 현황을 카드로 보여준다. */
export default function CollectionsDashboard() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [loading, setLoading] = useState(true);

  const reload = useCallback(() => {
    setLoading(true);
    setErrorMessage("");
    void fetchDashboardSummary()
      .then(setSummary)
      .catch((error: unknown) => {
        setErrorMessage(error instanceof Error ? error.message : "대시보드 집계를 불러오지 못했습니다.");
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const reloadTimer = window.setTimeout(reload, 0);
    return () => window.clearTimeout(reloadTimer);
  }, [reload]);

  return (
    <main className={styles.page}>
      <header className={styles.heading}>
        <div>
          <h1>COLLECTION DASHBOARD</h1>
          <p>{summary ? `${formatWindow(summary.window)} 기준 집계` : "최근 24시간 수집/검증 현황을 불러오고 있습니다."}</p>
        </div>
        <button type="button" className={styles.refreshButton} onClick={reload} disabled={loading}>
          {loading ? "새로고침 중..." : "새로고침"}
        </button>
      </header>

      {errorMessage && <p className={styles.errorMessage}>{errorMessage}</p>}

      {summary && (
        <section className={styles.grid} aria-label="집계 카드">
          <StatusCard title="수집 JOB" counts={summary.jobs} />
          <StatusCard title="페이지 작업" counts={summary.tasks} />

          <article className={styles.card}>
            <h2>판매처별 수집 상품</h2>
            <strong className={styles.bigNumber}>{summary.products.totalMerchantProductsCollected}</strong>
            <ul className={styles.breakdown}>
              {summary.products.byMerchant.length === 0 && <li className={styles.muted}>수집된 상품 없음</li>}
              {summary.products.byMerchant.map((row) => (
                <li key={row.merchant}>
                  <span>{row.merchant}</span>
                  <span>{row.count}</span>
                </li>
              ))}
            </ul>
          </article>

          <article className={styles.card}>
            <h2>JSON/HTML 검증</h2>
            <strong className={styles.bigNumber}>
              {summary.verifications.matchRate === null
                ? "N/A"
                : `${(summary.verifications.matchRate * 100).toFixed(1)}%`}
            </strong>
            <p className={styles.muted}>일치율 · 전체 {summary.verifications.total}건</p>
            <ul className={styles.breakdown}>
              {Object.entries(summary.verifications.byStatus).map(([status, count]) => (
                <li key={status}>
                  <span>{status}</span>
                  <span>{count}</span>
                </li>
              ))}
            </ul>
          </article>
        </section>
      )}
    </main>
  );
}

/** StatusCard는 상태별 개수 목록 하나를 총합과 함께 카드로 보여준다. */
function StatusCard({ title, counts }: { title: string; counts: StatusCounts }) {
  return (
    <article className={styles.card}>
      <h2>{title}</h2>
      <strong className={styles.bigNumber}>{counts.total}</strong>
      <ul className={styles.breakdown}>
        {Object.entries(counts.byStatus).map(([status, count]) => (
          <li key={status}>
            <span>{status}</span>
            <span>{count}</span>
          </li>
        ))}
      </ul>
    </article>
  );
}

/** formatWindow는 집계 시간 창을 사람이 읽을 수 있는 로컬 시각 범위로 바꾼다. */
function formatWindow(window: DashboardSummary["window"]): string {
  const since = new Date(window.since).toLocaleString("ko-KR");
  const until = new Date(window.until).toLocaleString("ko-KR");
  return `${since} ~ ${until}`;
}
