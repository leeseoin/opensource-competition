import { handleVerifyAgentRun } from "../../handler.ts";

/** POST는 사용자가 선택한 판매처 상품의 구매 직전 재검증을 시작한다. */
export async function POST(
  request: Request,
  context: { params: Promise<{ runId: string }> },
): Promise<Response> {
  const { runId } = await context.params;
  return handleVerifyAgentRun(runId, request);
}
