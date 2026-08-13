import NewCollectionRequestForm from "./new-collection-request-form";
import styles from "./page.module.css";

export const dynamic = "force-dynamic";

/** NewCollectionRequestPage는 판매처/검색어/페이지범위를 입력해 새 수집 job을 등록하는 화면이다. */
export default async function NewCollectionRequestPage({
  searchParams,
}: {
  searchParams: Promise<{ created?: string; error?: string }>;
}) {
  const { created, error } = await searchParams;

  return (
    <main className={styles.page}>
      <header className={styles.heading}>
        <h1>NEW REQUEST</h1>
        <p>판매처와 검색어, 페이지 범위를 입력해 새 수집 job을 등록합니다.</p>
      </header>
      {created && <p className={styles.successMessage}>접수 완료 · jobId {created}</p>}
      {error && <p className={styles.errorMessage}>{error}</p>}
      <NewCollectionRequestForm />
    </main>
  );
}
