import { handleProductCandidateRequest } from "./handler";

/** POST는 browser의 상품 후보 요청을 서버 전용 Product Backend 경로로 전달한다. */
export async function POST(request: Request): Promise<Response> {
  return handleProductCandidateRequest(request);
}
