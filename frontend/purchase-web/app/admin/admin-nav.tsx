"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import styles from "./admin-layout.module.css";

const TABS = [
  { href: "/admin/collections", label: "현황" },
  { href: "/admin/collections/new", label: "새 수집 요청" },
  { href: "/admin/collections/products", label: "상품 재검증" },
];

/** AdminNav는 관리 화면 기능이 늘어난 만큼 현황/새 요청 화면을 탭으로 구분해 보여준다. */
export default function AdminNav() {
  const pathname = usePathname();

  return (
    <nav className={styles.nav} aria-label="관리자 메뉴">
      {TABS.map((tab) => (
        <Link
          key={tab.href}
          href={tab.href}
          className={pathname === tab.href ? `${styles.navLink} ${styles.navLinkActive}` : styles.navLink}
        >
          {tab.label}
        </Link>
      ))}
    </nav>
  );
}
