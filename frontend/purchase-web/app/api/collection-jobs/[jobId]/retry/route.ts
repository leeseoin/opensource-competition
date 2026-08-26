import { handleCollectionJobRetryRequest } from "./handler";

/** POST는 요청 이력 화면의 재실행 버튼 클릭을 서버 전용 Product Backend 재실행 API로 전달한다. */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ jobId: string }> },
): Promise<Response> {
  const { jobId } = await params;
  const acceptsHtml = request.headers.get("accept")?.includes("text/html") ?? false;
  return handleCollectionJobRetryRequest(jobId, acceptsHtml);
}
