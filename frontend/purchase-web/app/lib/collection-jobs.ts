/** CollectionJobSummary는 한 번의 수집 요청과 그 성공률/상품 수 요약이다. */
export interface CollectionJobSummary {
  jobId: string;
  merchant: string;
  query: string;
  status: string;
  taskCount: number;
  succeededTaskCount: number;
  failedTaskCount: number;
  successRate: number | null;
  productCount: number;
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
