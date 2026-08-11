import { handleDashboardSummaryRequest } from "./handler";

/** GET은 browser의 대시보드 집계 요청을 서버 전용 Product Backend 경로로 전달한다. */
export async function GET(request: Request): Promise<Response> {
  return handleDashboardSummaryRequest(request);
}
