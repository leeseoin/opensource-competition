import { handleConfirmRequest } from "./handler";

/** POST는 browser에서 확인한 조건을 MCP 확인 및 검색 흐름으로 전달한다. */
export async function POST(request: Request): Promise<Response> {
  return handleConfirmRequest(request);
}
