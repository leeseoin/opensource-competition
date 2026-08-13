import CollectionsDashboard from "./collections-dashboard";
import { loadDashboardData } from "./dashboard-data";

export const dynamic = "force-dynamic";

/**
 * CollectionsAdminPage는 서버에서 미리 대시보드 데이터를 가져와 화면에 심어준다.
 * 브라우저의 fetch가 확장 프로그램/보안 소프트웨어에 막히는 환경에서도 첫 화면은 항상 뜬다.
 */
export default async function CollectionsAdminPage() {
  const initialData = await loadDashboardData();
  return <CollectionsDashboard initialData={initialData} />;
}
