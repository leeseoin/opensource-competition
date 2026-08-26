import { handleAdvanceAgentRun } from "../../handler.ts";

/** POST는 상태 기반 구매 조사 실행을 한 단계 진행한다. */
export async function POST(
  _request: Request,
  context: { params: Promise<{ runId: string }> },
): Promise<Response> {
  const { runId } = await context.params;
  return handleAdvanceAgentRun(runId);
}
