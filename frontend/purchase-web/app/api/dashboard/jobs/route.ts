import { handleCollectionJobsRequest } from "./handler";

/** GET은 browser의 요청 이력 목록 조회를 서버 전용 Product Backend 경로로 전달한다. */
export async function GET(request: Request): Promise<Response> {
  return handleCollectionJobsRequest(request);
}
