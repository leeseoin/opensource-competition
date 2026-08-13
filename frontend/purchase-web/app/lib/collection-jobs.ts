/**
 * CollectionJobSummary는 한 번의 수집 요청과 그 페이지/상품 단위 성공률, 상품 수 요약이다.
 *
 * 페이지(task)는 그 안 상품 하나라도 불일치하면 SUCCESS가 아닌 PARTIAL로 기록되므로
 * taskSuccessRate(페이지 전체가 완전히 일치한 비율)와 verificationMatchRate(상품 단위로 실제
 * 일치한 비율)는 서로 다른 걸 측정한다. 데이터 품질을 볼 때는 verificationMatchRate를 봐야 한다.
 */
export interface CollectionJobSummary {
  jobId: string;
  merchant: string;
  query: string;
  status: string;
  taskCount: number;
  succeededTaskCount: number;
  failedTaskCount: number;
  taskSuccessRate: number | null;
  productCount: number;
  verificationTotal: number;
  verificationMatched: number;
  verificationMatchRate: number | null;
  requestedAt: string;
  completedAt: string | null;
}

/** CollectionJobListResponse는 요청 이력 목록과 페이지네이션 정보다. */
export interface CollectionJobListResponse {
  totalCount: number;
  hasNext: boolean;
  items: CollectionJobSummary[];
}

/** fetchCollectionJobs는 Next.js server route를 거쳐 최신 요청순 수집 이력을 조회한다. */
export async function fetchCollectionJobs(
  merchant?: string,
  status?: string,
  page = 0,
  size = 20,
): Promise<CollectionJobListResponse> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (merchant) params.set("merchant", merchant);
  if (status) params.set("status", status);

  const response = await fetch(`/api/dashboard/jobs?${params.toString()}`, { cache: "no-store" });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(body?.message ?? "요청 이력을 불러오지 못했습니다.");
  }
  return response.json() as Promise<CollectionJobListResponse>;
}
