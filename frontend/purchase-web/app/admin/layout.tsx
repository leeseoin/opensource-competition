import AdminNav from "./admin-nav";

/** AdminLayout은 모든 관리 화면에 공통 메뉴 탭을 붙인다. */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <AdminNav />
      {children}
    </>
  );
}
