# HouseholdBook UI/UX 디자인 가이드 (`DESIGN_GUIDE.md` — Apple iOS HIG 에디션)

이 문서는 **HouseholdBook** 안드로이드 애플리케이션에 적용된 **Apple Human Interface Guidelines (iOS HIG / Cupertino)** 기반 디자인 시스템, 컬러 토큰, 전용 컴포넌트([IosComponents.kt](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/core/designsystem/IosComponents.kt)) 및 시각적 레퍼런스 링크를 관리하는 문서입니다.
- 기능/도메인 명세: [GUIDE.md](file:///c:/WorkSpace/Dev/Android/HouseholdBook/GUIDE.md)
- 기술 스택 명세: [TECH_STACK.md](file:///c:/WorkSpace/Dev/Android/HouseholdBook/TECH_STACK.md)
- 파일 위치: iOS 전용 컴포넌트는 `core/designsystem/IosComponents.kt`, 컬러·테마·서체 토큰은 `ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`)에 있습니다.

---

## 1. Apple Human Interface Guidelines (HIG) 시각적 레퍼런스 링크

본 앱의 UI는 Material 3의 두꺼운 테두리/그림자 대신 **Apple iOS (지갑/건강/설정 앱)** 특유의 정갈한 여백, `#F2F2F7` 그룹형 배경, `0.5dp` 헤어라인 구분선, 슬라이딩 세그먼트 피커를 채택했습니다.

| Apple HIG 공식 가이드 & 레퍼런스 | 본 앱에 적용된 컴포넌트 | 바로가기 링크 |
| :--- | :--- | :--- |
| **Apple HIG — Color (iOS System Colors)** | `systemBlue(#007AFF)`, `systemGreen(#34C759)`, `systemRed(#FF3B30)`, `systemOrange(#FF9500)`, `systemGroupedBackground(#F2F2F7)` | [Apple HIG - Color 공식 문서](https://developer.apple.com/design/human-interface-guidelines/color) |
| **Apple HIG — Segmented Controls** | 상단 `[우리집 공통 생활비 \| 내 개인 용돈]` 및 장부 `[전체 \| 지출 \| 수입 \| 이체]` 슬라이딩 피커 (`IosSegmentedControl`) | [Apple HIG - Segmented Controls](https://developer.apple.com/design/human-interface-guidelines/segmented-controls) |
| **Apple HIG — Lists and Tables (`insetGrouped`)** | 그림자 없이 `#F2F2F7` 배경 위에 떠 있는 `14.dp` 곡률의 순백색 그룹 카드 + `0.5dp` 헤어라인 구분선 (`IosInsetGroupedCard`) | [Apple HIG - Lists and Tables](https://developer.apple.com/design/human-interface-guidelines/lists-and-tables) |
| **Apple HIG — Buttons (`tinted` & `filled`)** | 외곽선(Border) 없이 12% 투명도의 틴트 배경을 갖는 버튼(`IosTintedButton`)과 단색 채움 버튼(`IosFilledButton`) | [Apple HIG - Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons) |
| **Apple HIG — Tab Bars (`UITabBar`)** | Material 기본 하단 바 대신 iOS 18 스타일 **플로팅 캡슐 독(`IosFloatingCapsuleTabBar`)** + 선택 탭 알약(Pill) 하이라이트 + Filled/Outlined 아이콘 대비 + `systemRed` 정사각형 배지(`16.dp`, 곡률 `4.dp`) 적용 (상세: 2장) | [Apple HIG - Tab Bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars) |
| **Dribbble — iOS Finance & Wallet UI** | Apple Card / Apple Wallet 감성의 예산 게이지 및 거래 리스트 | [Dribbble - iOS Wallet & Expense UI 보기](https://dribbble.com/search/ios-wallet-expense-app) |

---

## 2. 기존 Material 3 대비 Apple HIG 변경점 비교

| 요소 | 변경 전 (Material 3 기본) | 변경 후 (Apple iOS HIG 스타일) |
| :--- | :--- | :--- |
| **배경 & 카드 (Surface)** | 회색 그림자(Elevation 2dp)가 들어간 두꺼운 카드 | **그림자 0dp** + iOS Grouped Background(`#F2F2F7` / Dark `#000000`)와 순백색 카드(`#FFFFFF` / Dark `#1C1C1E`), 곡률 `14.dp` |
| **상단 공간 전환 & 필터** | 체크 아이콘이 붙는 Material `FilterChip` 나열 | 회색 트랙(`#E5E5EA`) 내부에서 흰색 썸(Thumb)이 움직이는 **`IosSegmentedControl` (`UISegmentedControl`)** |
| **액션 버튼 (`삭제/분할/수정/저장`)** | 검은색/회색 테두리가 둘러진 `OutlinedButton` | 테두리 없이 부드러운 틴트 배경(`tintColor.copy(alpha = 0.12f)`)을 사용하는 **`IosTintedButton`** + **`IosFilledButton`** |
| **리스트 구분선** | 두꺼운 `1.dp` Divider 또는 개별 카드 남발 | 하나의 `IosInsetGroupedCard` 내부에서 **`0.5.dp` `IosHairlineDivider`(`#C6C6C8`)**로 정교하게 행 구분 |
| **하단 탭 바 (`IosFloatingCapsuleTabBar`)** | 화면 바닥에 꽉 찬 직사각형 바 또는 색상만 바뀌는 탭 | **[방안 B] iOS 18 스타일 플로팅 캡슐 독(`RoundedCornerShape(32.dp)`)** + 선택 탭 알약 하이라이트 + **Filled/Outlined 아이콘 대비** + `systemRed(#FF3B30)` 배지 |

---

## 3. Apple iOS 시맨틱 컬러 토큰 ([Color.kt](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/ui/theme/Color.kt))

| 토큰명 | Light Mode | Dark Mode | 용도 |
| :--- | :--- | :--- | :--- |
| **`IosSystemBlue`** | `#007AFF` | `#0A84FF` | **공통 생활비 (`SHARED`)** 메인 틴트 컬러 및 수입 표시 |
| **`IosSystemGreen`** | `#34C759` | `#30D158` | **개인 용돈 (`PERSONAL`)** 메인 틴트 컬러 |
| **`IosSystemRed`** | `#FF3B30` | `#FF453A` | 지출 금액, 예산 초과 경고, 파괴적 액션(`삭제`) 및 탭 바 알림 배지 |
| **`IosSystemOrange`** | `#FF9500` | `#FF9F0A` | **집밥 절약 카운터** 포인트 컬러 (Apple Fitness 링 감성) |
| **`IosSystemGray`** | `#8E8E93` | `#8E8E93` | 보조 설명 텍스트, 섹션 헤더(`IosSectionHeader`), 비활성 탭 아이콘 |
| **`IosSystemGray5`** | `#E5E5EA` | `#2C2C2E` | `IosSegmentedControl` 트랙 배경 및 비활성 `IosPillChip` 배경 |
| **`IosSystemGray6`** | `#F2F2F7` | `#000000` | 전체 화면 기본 배경 (`systemGroupedBackground`) |
| **`IosCardSurface`** | `#FFFFFF` | `#1C1C1E` | `IosInsetGroupedCard` 표면 색상 (`secondarySystemGroupedBackground`) |

---

## 4. 한글 버튼/칩 반응형 레이아웃 필수 규칙 (세로 줄바꿈 절대 금지)

1. **`Row` 내 좌측 텍스트 `Modifier.weight(1f)` 필수**:
   - 좌측에 제목/설명 `Column`이 있고 우측에 버튼(`일괄 승인`, `단가 설정`, `검수하기` 등)이 올 때는 반드시 좌측 `Column`에 `Modifier.weight(1f)`를 지정하여 우측 버튼의 가로 폭이 절대 침범받지 않도록 합니다.
2. **모든 버튼 및 칩 텍스트 한 줄 고정 (`maxLines = 1, softWrap = false`)**:
   - [IosComponents.kt](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/core/designsystem/IosComponents.kt)의 `IosTintedButton`, `IosFilledButton`, `IosPillChip`, `IosSegmentedControl`은 내부적으로 `maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis`가 강제되어 있어 어떤 기기 해상도에서도 글자가 세로(`일\n괄\n승\n인`)로 꺾이지 않습니다.

---

## 5. 카테고리 & 세부 카테고리 UI 가독성 규칙 (방안 1 + 방안 3 적용)

카드 내부나 다이얼로그에 10~20개의 상위/세부 카테고리 칩(`FlowRow`)을 한꺼번에 늘어놓는 '칩 구름(Chip Cloud)' 현상을 방지하기 위해 다음 2가지 규칙을 표준으로 사용합니다.

1. **[방안 1] 선택(Picker) UI — 1줄 요약 바 + 2단(2-Pane) iOS 바텀시트 (`IosCategorySelectorField`)**:
   - **적용 화면**: [PendingQueueScreen.kt](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/ui/queue/PendingQueueScreen.kt) (검수 카드, 단건 수정 모달, 분할 등록 모달), [LedgerScreen.kt](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/ui/ledger/LedgerScreen.kt) (직접 입력 모달)
   - 평소에는 카드/모달 내부에 **`분류   [ 🍔 식비 › 🛵 배달/외식   변경 › ]`** 형태의 깔끔한 **단 1줄(`38.dp`)** 만 표시합니다.
   - 터치 시 하단에서 **2단 분할(2-Pane) `ModalBottomSheet`**가 올라오며, **좌측(42%)에서 상위 카테고리**를 탭하면 **우측(58%)에서 세부 카테고리**를 즉시 체크(`✓`)하고 원탭으로 닫힙니다.
2. **[방안 3] 조회/관리(Display) UI — 칩 나열 대신 들여쓰기된 iOS Sub-Row 리스트 정렬**:
   - **적용 화면**: [DashboardScreen.kt](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/ui/dashboard/DashboardScreen.kt) (`카테고리 및 세부 카테고리별 지출`), [SettingsAndCategoryScreen.kt](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/ui/settings/SettingsAndCategoryScreen.kt) (`카테고리 및 세부 분류`)
   - 상위 카테고리 행 아래에 캡슐 칩을 흩뿌리지 않고, **좌측 들여쓰기(`· 🥬 식재료/장보기` / `↳ 🛵 배달/외식`) + 우측 정렬(`145,000원 (88%)` 또는 수정/삭제 아이콘)** 구조의 정돈된 Sub-Row 테이블로 표시하여 시선이 위에서 아래로 편안하게 흐르도록 유지합니다.

---

## 6. Apple iOS Dark Mode (OLED Pure Black & Elevated Surface) 아키텍처

1. **3단계 테마 전환 (`ThemeMode` — [Models.kt](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/domain/model/Models.kt))**:
   - `SYSTEM("📱 기기 설정")`, `LIGHT("☀️ 밝은 화면")`, `DARK("🌙 어두운 화면")` 3가지 모드를 지원하며, [UserPreferencesRepository.kt](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/core/datastore/UserPreferencesRepository.kt)의 DataStore에 영구 저장됩니다.
   - **빠른 전환**: 상단 네비게이션 바([MainActivity.kt](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/MainActivity.kt)) 우측의 `밝게 / 어둡게` 캡슐 버튼으로 어느 탭에서든 원터치 전환이 가능하며, **설정 탭** 상단의 `화면 스타일` 세그먼트 컨트롤에서도 변경할 수 있습니다.
2. **iOS 다크 모드 계층별 명도 대비 (Elevation by Luminance)**:
   - 다크 모드에서는 그림자 대신 표면의 밝기 차이로 깊이를 표현합니다:
     - **기본 배경 (`systemGroupedBackground`)**: `#000000` (`IosSystemGray6Dark`, OLED Pure Black)
     - **1단 그룹 카드 (`secondarySystemGroupedBackground`)**: `#1C1C1E` (`IosCardSurfaceDark`)
     - **2단 컨트롤 트랙 (`tertiarySystemGroupedBackground`)**: `#2C2C2E` (`IosSystemGray5Dark`)
     - **3단 세그먼트 활성 썸 (`IosSegmentedThumbDark`)**: `#3A3A3C` — 카드(`#1C1C1E`) 및 트랙(`#2C2C2E`) 위에서도 선택된 탭이 선명하게 부각됩니다.
   - 안드로이드 시스템 상태바·내비게이션바 아이콘 색상도 `WindowCompat.getInsetsController`를 통해 라이트/다크 전환 시 실시간으로 동기화됩니다.

---

## 7. 추가 화면 요소 (2026-10-18)

1. **월 정산 기준일 입력 (`BillingStartDayField` — `core/designsystem/BillingStartDayField.kt`)**:
   - `[날짜 지정 | 매월 말일]` `IosSegmentedControl` + 날짜 지정 시 `1 ~ 28일` 숫자 입력. 홈 `목표·기준일 편집` 다이얼로그와 설정 탭 예산 카드에서 같은 컴포넌트를 씁니다.
   - 표기는 `BillingCycleCalculator.startDayLabel()`로 통일합니다 (`매월 25일`, `매월 말일`).
2. **취소 원본 선택 (`CancelTargetPickerDialog` — `ui/queue/PendingQueueScreen.kt`)**:
   - `결제 취소 확인 필요` 카드 상단에 `systemRed` 12% 틴트 안내 박스 + `원본 결제 선택` 채움 버튼.
   - 다이얼로그는 후보 결제를 둥근 행(`10.dp`)으로 나열하고, 오른쪽에 금액과 `전체 취소`(`systemRed`) / `부분 취소`(`systemOrange`) 라벨, 선택 시 체크 아이콘을 표시합니다.
3. **집밥 절약 홈 화면 위젯 (`MealSaverWidgetProvider` — `res/layout/widget_meal_saver.xml`)**:
   - 4×2 셀, 곡률 `22.dp` 카드(라이트 `#FFFFFF` / 다크 `#1C1C1E`, `values-night/widget_colors.xml`).
   - 상단 `🍚 집밥 절약 · {공간 이름}`, `systemOrange` 굵은 절약 금액, `이번 주기 N끼 직접 해결 · 기간`, 하단 `아침/점심/저녁/간식` 버튼 4개(`systemOrange` 12% 틴트, 곡률 `12.dp`, 한 줄 고정).
   - 버튼은 앱을 열지 않고 현재 선택된 공간에 바로 +1 기록하며, 카드의 나머지 영역을 누르면 앱이 열립니다.
