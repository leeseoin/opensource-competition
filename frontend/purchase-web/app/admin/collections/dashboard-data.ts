import type { CollectionJobSummary } from "../../lib/collection-jobs";
import type { DashboardSummary } from "../../lib/dashboard-summary";

const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";

/** DashboardData는 서버에서 미리 불러와 초기 렌더에 그대로 심어주는 대시보드 데이터다. */
export interface DashboardData {
  summary: DashboardSummary | null;
  jobs: CollectionJobSummary[];
  errorMessage: string;
}

function backendUrl(): string {
  return process.env.PRODUCT_BACKEND_BASE_URL?.replace(/\/$/, "") ?? DEFAULT_BACKEND_URL;
}

/**
 * loadDashboardData는 Next.js 서버에서 직접 Product Backend를 호출해 집계와 요청 이력을 가져온다.
 * 브라우저가 아닌 서버가 요청을 보내므로, 브라우저의 fetch/XHR을 가로채는 확장 프로그램이나
 * 보안 소프트웨어와 무관하게 항상 동작한다.
 */
export async function loadDashboardData(): Promise<DashboardData> {
  try {
    const [summaryResponse, jobsResponse] = await Promise.all([
      fetch(`${backendUrl()}/internal/v1/dashboard/summary`, { cache: "no-store" }),
      fetch(`${backendUrl()}/internal/v1/collection-jobs?page=0&size=20`, { cache: "no-store" }),
    ]);

    if (!summaryResponse.ok || !jobsResponse.ok) {
      return { summary: null, jobs: [], errorMessage: "대시보드 데이터를 불러오지 못했습니다." };
    }

    const summary = (await summaryResponse.json()) as DashboardSummary;
    const jobsBody = (await jobsResponse.json()) as { items: CollectionJobSummary[] };
    return { summary, jobs: jobsBody.items, errorMessage: "" };
  } catch {
    return { summary: null, jobs: [], errorMessage: "상품 서버에 연결할 수 없습니다." };
  }
}
