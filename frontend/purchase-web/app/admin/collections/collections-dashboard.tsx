"use client";

import { useSearchParams } from "next/navigation";
import { CollectionJobSummary } from "../../lib/collection-jobs";
import { StatusCounts } from "../../lib/dashboard-summary";
import type { DashboardData } from "./dashboard-data";
import styles from "./page.module.css";

const RETRYABLE_STATUSES = new Set(["FAILED", "PARTIAL"]);

/**
 * CollectionsDashboard는 서버에서 미리 받은 최근 24시간(기본값) 수집/검증 현황과 요청 이력을 보여준다.
 * 새로고침/재실행은 브라우저 fetch가 아니라 일반 링크·form 제출(진짜 페이지 이동)로 처리해,
 * fetch/XHR을 가로채는 확장 프로그램·보안 소프트웨어가 있어도 항상 동작한다.
 */
export default function CollectionsDashboard({ initialData }: { initialData: DashboardData }) {
  const { summary, jobs, errorMessage } = initialData;
  const searchParams = useSearchParams();
  const retriedJobId = searchParams.get("retried");
  const retryError = searchParams.get("retryError");

  return (
    <main className={styles.page}>
      <header className={styles.heading}>
        <div>
          <h1>COLLECTION DASHBOARD</h1>
          <p>{summary ? `${formatWindow(summary.window)} 기준 집계` : "대시보드 데이터를 불러오지 못했습니다."}</p>
        </div>
        <a href="/admin/collections" className={styles.refreshButton}>
          새로고침
        </a>
      </header>

      {errorMessage && <p className={styles.errorMessage}>{errorMessage}</p>}
      {retriedJobId && (
        <p className={styles.successMessage}>재실행 접수됨 · 새 jobId {retriedJobId}</p>
      )}
      {retryError && <p className={styles.errorMessage}>{retryError}</p>}

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

      <section aria-label="요청 이력">
        <h2 className={styles.tableHeading}>요청 이력</h2>
        {jobs.length === 0 ? (
          <p className={styles.muted}>아직 수집 요청이 없음</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>요청 시각</th>
                  <th>판매처</th>
                  <th>검색어</th>
                  <th>상태</th>
                  <th title="상품 단위로 JSON/HTML이 실제 일치한 비율 — 데이터 품질 지표">검증 일치율</th>
                  <th title="페이지(최대 50개) 안 상품이 전부 일치해 SUCCESS로 끝난 페이지 비율. 상품 하나만 불일치해도 그 페이지는 PARTIAL로 잡혀 낮게 나올 수 있음">
                    페이지 완전일치
                  </th>
                  <th>상품 수</th>
                  <th>재실행</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.jobId}>
                    <td>{new Date(job.requestedAt).toLocaleString("ko-KR")}</td>
                    <td>{job.merchant}</td>
                    <td>{job.query}</td>
                    <td>{job.status}</td>
                    <td>{formatVerificationMatchRate(job)}</td>
                    <td>{formatTaskSuccessRate(job)}</td>
                    <td>{job.productCount}</td>
                    <td>
                      {RETRYABLE_STATUSES.has(job.status) ? (
                        <form method="POST" action={`/api/collection-jobs/${job.jobId}/retry`}>
                          <button type="submit" className={styles.retryButton}>
                            재실행
                          </button>
                        </form>
                      ) : (
                        <span className={styles.muted}>-</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  );
}

/** formatVerificationMatchRate는 상품 단위 검증 일치율을 "일치/전체 (백분율)" 형태로 보여준다. */
function formatVerificationMatchRate(job: CollectionJobSummary): string {
  if (job.verificationMatchRate === null) {
    return "N/A";
  }
  return `${job.verificationMatched}/${job.verificationTotal} (${(job.verificationMatchRate * 100).toFixed(1)}%)`;
}

/** formatTaskSuccessRate는 페이지 단위 완전일치율을 "성공/전체 (백분율)" 형태로 보여준다. */
function formatTaskSuccessRate(job: CollectionJobSummary): string {
  if (job.taskSuccessRate === null) {
    return "N/A";
  }
  return `${job.succeededTaskCount}/${job.taskCount} (${(job.taskSuccessRate * 100).toFixed(0)}%)`;
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
function formatWindow(window: NonNullable<DashboardData["summary"]>["window"]): string {
  const since = new Date(window.since).toLocaleString("ko-KR");
  const until = new Date(window.until).toLocaleString("ko-KR");
  return `${since} ~ ${until}`;
}
