import { handleCollectionTaskCreateRequest } from "./handler";

/** POST는 새 수집 요청 폼 제출을 서버 전용 Product Backend 경로로 전달한다. */
export async function POST(request: Request): Promise<Response> {
  return handleCollectionTaskCreateRequest(request);
}
