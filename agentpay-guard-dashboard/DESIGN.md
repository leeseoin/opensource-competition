# AgentPay Guard Dashboard DESIGN.md

작성일: 2026-07-06  
상태: draft

## 1. Visual Theme & Atmosphere

AgentPay Guard Dashboard는 AI Agent의 유료 리소스 사용, 정책 판단, 승인 흐름, 감사 hash anchoring 상태를 확인하는 운영형 보안 대시보드다.

분위기는 조용하고 신뢰감 있어야 한다. SaaS 랜딩 페이지처럼 장식적이거나 과장된 히어로 구성을 피하고, 반복적으로 확인하고 조작하는 업무 도구처럼 보여야 한다.

핵심 키워드:

- 보안 게이트웨이
- 비용 통제
- 감사 가능성
- 정책 판단
- 운영 대시보드

## 2. Color Palette & Roles

기본 화면은 밝은 배경과 차분한 중립색을 사용한다. 상태 표현에만 강한 색을 사용한다.

```text
Background: #f8fafc
Surface: #ffffff
Surface Subtle: #f1f5f9
Border: #e2e8f0
Border Strong: #cbd5e1

Text Primary: #0f172a
Text Secondary: #475569
Text Muted: #64748b
Text Inverse: #ffffff

Brand Primary: #2563eb
Brand Primary Hover: #1d4ed8
Brand Soft: #dbeafe

Allow: #16a34a
Allow Soft: #dcfce7
Deny: #dc2626
Deny Soft: #fee2e2
Require Approval: #d97706
Require Approval Soft: #fef3c7
Anchored: #4f46e5
Anchored Soft: #e0e7ff
Pending: #64748b
Pending Soft: #f1f5f9
```

상태 색상 규칙:

- `ALLOW`, `ALLOWED`: green 계열
- `DENY`, `DENIED`, `FAILED`: red 계열
- `REQUIRE_APPROVAL`, `WAITING_APPROVAL`: amber 계열
- `ANCHORED`, `VERIFIED`: indigo/blue 계열
- `PENDING`: neutral gray 계열

## 3. Typography Rules

시스템 폰트를 우선 사용한다. 숫자, 금액, hash, txHash는 고정폭 폰트로 표시한다.

```text
Font Sans: Inter, Pretendard, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
Font Mono: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace
```

타입 스케일:

```text
Page Title: 24px / 32px, weight 700
Section Title: 18px / 28px, weight 650
Panel Title: 15px / 22px, weight 650
Body: 14px / 22px, weight 400
Table Header: 12px / 16px, weight 650, uppercase optional
Caption: 12px / 18px, weight 400
Code/Hash: 12px / 18px, mono
```

## 4. Component Stylings

### Buttons

기본 버튼은 36px 높이를 기준으로 한다.

```text
Primary Button:
  background #2563eb
  hover #1d4ed8
  text #ffffff
  border none

Secondary Button:
  background #ffffff
  hover #f8fafc
  text #0f172a
  border #cbd5e1

Danger Button:
  background #dc2626
  hover #b91c1c
  text #ffffff
```

버튼 radius는 6px을 기본으로 한다. 아이콘이 있으면 왼쪽에 16px 크기로 배치한다.

### Status Badges

상태 배지는 24px 높이, 6px radius, 12px 폰트를 사용한다.

```text
ALLOW: green text on green soft background
DENY: red text on red soft background
REQUIRE_APPROVAL: amber text on amber soft background
ANCHORED: indigo text on indigo soft background
PENDING: gray text on gray soft background
```

### Tables

Payment Request 목록, Audit event 목록은 테이블 중심으로 설계한다.

```text
Header background: #f8fafc
Row background: #ffffff
Row hover: #f8fafc
Border bottom: #e2e8f0
Cell padding: 12px 16px
```

hash와 txHash는 말줄임 처리하되 copy 버튼을 제공한다.

### Detail Panels

상세 화면은 좌우 또는 상하 패널로 구성한다.

```text
Summary panel: 정책 결과, 금액, merchant, status
Audit panel: canonicalJson, eventHash, chainId, txHash
Action panel: approve, reject, mock payment
```

카드는 중첩하지 않는다. 반복 항목 카드가 아닌 페이지 섹션은 단순한 panel 또는 table로 표현한다.

## 5. Layout Principles

대시보드 레이아웃은 밀도 있게 구성한다.

```text
Page max width: 1280px
Page padding desktop: 24px
Page padding mobile: 16px
Section gap: 24px
Panel gap: 16px
Inline control gap: 8px
```

기본 구조:

```text
Top bar
  project name, environment, API status

Main content
  left navigation or tabs
  list/table view
  detail panel
```

랜딩 페이지형 hero, 큰 일러스트, 장식적인 gradient section은 사용하지 않는다.

## 6. Depth & Elevation

그림자는 강하게 쓰지 않는다. 운영 도구처럼 경계선과 약한 elevation을 사용한다.

```text
Panel Border: 1px solid #e2e8f0
Panel Shadow: 0 1px 2px rgba(15, 23, 42, 0.04)
Popover Shadow: 0 8px 24px rgba(15, 23, 42, 0.12)
Focus Ring: 0 0 0 3px rgba(37, 99, 235, 0.18)
```

## 7. Do's and Don'ts

Do:

- 상태는 색상과 텍스트를 함께 사용한다.
- 금액, merchant, decision, txHash를 한눈에 스캔할 수 있게 배치한다.
- txHash와 eventHash는 copy 가능한 UI로 제공한다.
- 위험한 동작은 confirm 또는 명확한 버튼 라벨을 사용한다.
- 로컬/테스트넷/운영 환경 구분을 화면 상단에 표시한다.

Don't:

- 전체 화면을 하나의 큰 카드로 감싸지 않는다.
- 색상만으로 상태를 구분하지 않는다.
- purple/blue gradient를 화면 전체 테마로 쓰지 않는다.
- marketing hero, decorative blob, oversized headline을 사용하지 않는다.
- 실제 결제처럼 오해될 수 있는 카드/계좌 UI를 만들지 않는다.

## 8. Responsive Behavior

Desktop:

- 목록과 상세를 나란히 볼 수 있게 구성한다.
- 테이블은 주요 컬럼을 모두 표시한다.

Tablet:

- 목록과 상세를 위아래로 쌓는다.
- action button은 상단 또는 하단에 고정하지 않고 상세 패널 안에 둔다.

Mobile:

- 테이블은 카드형 list 또는 핵심 컬럼 중심으로 축약한다.
- 버튼 터치 영역은 최소 44px 높이를 유지한다.
- hash/txHash는 1줄 말줄임 + copy 버튼을 사용한다.

## 9. Agent Prompt Guide

프론트엔드 작업 전에는 이 파일을 먼저 읽고, 색상/타이포/컴포넌트 규칙을 따른다.

예시 프롬프트:

```text
DESIGN.md를 먼저 읽고 AgentPay Guard의 Payment Request 목록 화면을 구현해줘.
테이블 중심의 운영형 대시보드로 만들고, ALLOW/DENY/REQUIRE_APPROVAL 상태 배지를 DESIGN.md 색상 규칙에 맞춰줘.
```

```text
DESIGN.md 기준으로 Payment Request 상세 패널을 만들어줘.
정책 판단 결과, canonicalJson, eventHash, chainId, txHash를 구분해서 표시하고 hash 값은 mono font와 copy 버튼을 사용해줘.
```

```text
DESIGN.md의 Do's and Don'ts를 지켜서 Vite 기본 scaffold 화면을 AgentPay Guard 대시보드 첫 화면으로 교체해줘.
랜딩 페이지가 아니라 실제 운영 화면이 첫 화면이어야 해.
```
