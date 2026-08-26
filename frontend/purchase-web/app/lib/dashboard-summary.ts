/** DashboardSummary는 Product Backend 집계 API가 돌려주는 시간 창별 현황이다. */
export interface DashboardSummary {
  window: { since: string; until: string };
  jobs: StatusCounts;
  tasks: StatusCounts;
  products: {
    totalMerchantProductsCollected: number;
    byMerchant: { merchant: string; count: number }[];
  };
  verifications: StatusCounts & { matchRate: number | null };
  generatedAt: string;
}

/** StatusCounts는 상태 문자열별 개수와 합계다. */
export interface StatusCounts {
  total: number;
  byStatus: Record<string, number>;
}

/** fetchDashboardSummary는 Next.js server route를 거쳐 최근(또는 지정한) 집계를 조회한다. */
export async function fetchDashboardSummary(since?: string, until?: string): Promise<DashboardSummary> {
  const params = new URLSearchParams();
  if (since) params.set("since", since);
  if (until) params.set("until", until);
  const query = params.toString();

  const response = await fetch(`/api/dashboard/summary${query ? `?${query}` : ""}`, { cache: "no-store" });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(body?.message ?? "대시보드 집계를 불러오지 못했습니다.");
  }
  return response.json() as Promise<DashboardSummary>;
}
