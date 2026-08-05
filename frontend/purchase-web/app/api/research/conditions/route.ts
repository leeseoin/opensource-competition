import { handleConditionsRequest } from "./handler";

/** POST는 browser 질문을 서버 전용 Codex 및 MCP 흐름으로 전달한다. */
export async function POST(request: Request): Promise<Response> {
  return handleConditionsRequest(request);
}
