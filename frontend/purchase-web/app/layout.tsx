import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Purchase Research Agent",
  description: "가격, 재고와 출처를 함께 확인하는 오픈소스 구매 리서치 도구",
};

/**
 * RootLayout은 외부 font 요청 없는 전역 문서 골격과 메타데이터를 제공한다.
 */
export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
