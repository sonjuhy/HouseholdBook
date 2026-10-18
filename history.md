# HouseholdBook 개발 및 개선 히스토리 (`history.md`)

이 문서는 **HouseholdBook (`com.budget.householdbook`, v0.0.1)** 안드로이드 애플리케이션의 초기 설계·구현부터 UI/UX 고도화, 한국어 가독성 개선, Firebase 실시간 클라우드 연동, 그리고 구글 계정 로그인 및 파트너 초대 시스템 구축까지의 전체 과정을 시간순으로 기록한 문서입니다.

---

## 1단계: 프로젝트 기반 구축 및 핵심 도메인 구현

### 1.1. 기술 스택 및 아키텍처 구성
- **플랫폼 및 빌드 환경**: Android (`minSdk = 26`, `targetSdk = 37`, `compileSdk = 37`), Kotlin 2.2.10, Jetpack Compose (Material 3)
- **오프라인 퍼스트 아키텍처 (Offline-First SSOT)**:
  - 모든 데이터의 단일 진실 공급원(SSOT)을 로컬 **Room DB (`AppDatabase`)**에 두어 인터넷 연결이 없어도 모든 기능이 100% 동작하도록 설계
  - 의존성 주입: **Koin (`AppModule.kt`)**
  - 환경설정 및 보안 저장소: **Preferences DataStore (`UserPreferencesRepository`)** + **Android Keystore AES/GCM 암호화 저장소 (`SecureCredentialManager`)**

### 1.2. 핵심 가계부 및 공통 생활비 기능 구현
1. **공간 분리 및 통합 관리 (`LedgerSpaceEntity`)**:
   - `공통 생활비 (SHARED)`와 `개인 용돈 (PERSONAL)` 공간을 상단 세그먼트 피커로 즉시 전환하며 관리
2. **금융 알림·문자 자동 수집 및 대기내역 검수 (`PendingQueue`)**:
   - 금융 앱 푸시 알림(`FinancialNotificationListenerService`), 문자(`SmsBroadcastReceiver`), KB 빠른조회 스크래퍼(`KbQuickInquiryScraper`)를 통해 입출금·결제 내역 자동 수집
   - **3대 예외 처리**:
     - ① 결제 취소·환불 알림 감지 시 원본 결제 자동 매칭 (`CANCEL_MATCHED`)
     - ② 하나의 결제 건을 `공통 생활비`와 `개인 용돈` 또는 여러 카테고리로 쪼개서 등록하는 **금액 나누기(분할 등록)**
     - ③ 내 계좌끼리 이체·카드대금 자동출금 자동 분류 및 자동 건너뛰기 단어(`IgnoredKeywordEntity`) 관리
3. **목표 예산 관리 및 월 기준일 커스텀 (`BillingCycleCalculator`)**:
   - 월급일(1일~28일) 기준 한 달 예산 주기 계산, 권장 일일 지출액 및 초과 시 빨간색 경고 표시
4. **집밥 절약 기록 (`MealSaver`)**:
   - 아침·점심·저녁·간식(홈카페)별 외식 대체 절약 단가 설정 및 원터치 기록·절약 누적 통계 제공

---

## 2단계: Apple iOS 스타일 디자인 시스템 및 플로팅 캡슐 하단바 고도화

### 2.1. iOS HIG 기반 테마 및 라이트/다크 모드 구축
- **컬러 및 서체 시스템 (`Color.kt`, `Theme.kt`, `Type.kt`)**:
  - 라이트 모드(`#F2F2F7` 배경 + `#FFFFFF` 카드) 및 다크 모드(`#000000` 순수 검정 배경 + `#1C1C1E` 카드) 지원
  - 금액 및 날짜가 흔들리지 않도록 고정폭 숫자(`fontFeatureSettings = "tnum"`) 적용
  - 상단 헤더 우측에 원터치 **`밝게 / 어둡게`** 전환 캡슐 버튼 배치

### 2.2. iOS 18 스타일 플로팅 캡슐 하단바 (`IosFloatingCapsuleTabBar`) 적용
- **요구사항**: 하단 5개 탭(`홈 - 대기내역 - 장부 - 집밥절약 - 설정`)을 iOS 감성으로 바꾸면서도 한글 가독성이 떨어지지 않도록 개선
- **해결 과정**:
  1. **방안 B 채택 (플로팅 캡슐 독 + 선택 탭 알약 하이라이트)**:
     - 바닥에 붙은 사각 탭 바 대신 좌우·하단에 여백을 둔 둥근 캡슐(Dock) 형태로 띄우고, 선택된 탭에 부드러운 알약(Pill) 배경과 채움(Filled) 아이콘을 적용
  2. **배경 투명 오버레이 처리 (`MainActivity.kt`)**:
     - 플로팅 캡슐 주변이 불투명한 배경색으로 막혀 스크롤 영역이 잘려 보이던 문제를 해결하기 위해, `Scaffold`의 `bottomBar`에서 분리하여 `Box`의 `Alignment.BottomCenter` 위에 투명하게 떠 있는 오버레이 구조로 변경
     - 각 화면(`홈`, `대기내역`, `장부`, `집밥절약`, `설정`)의 `LazyColumn` 하단 여백을 `110.dp`로 확보해 끝까지 스크롤했을 때도 마지막 카드가 가려지지 않도록 처리
  3. **정사각형 빨간 알림 뱃지**:
     - `대기내역` 탭 우측 상단의 빨간 알림 뱃지를 가로·세로 `16.dp × 16.dp` 1:1 정사각형(`RoundedCornerShape(4.dp)`)과 `includeFontPadding = false`로 정밀 정렬

---

## 3단계: 전문 용어 전면 제거 및 한국어 문맥 줄바꿈 개선

### 3.1. 일반 사용자 눈높이에 맞춘 쉬운 한국어 표현 적용
- 앱 전체 화면에서 개발·디자인 전문 용어를 전수 조사하여 일상적인 한국어로 교체:
  - `OLED Pure Black #000000 배경 + #1C1C1E 그룹 카드` → **`눈이 편안한 어두운 화면으로 표시합니다`**
  - `Apple iOS 라이트 (#F2F2F7 배경 + 화이트 카드)` → **`환하고 깔끔한 밝은 화면으로 표시합니다`**
  - `Free (무료 - 로컬 전용)` / `Pro (구독 - 멀티유저 공유)` → **`기본 (혼자 쓰기)`** / **`프리미엄 (함께 쓰기)`**
  - `대기 큐` → **`대기내역`**, `로컬 DB 전용` → **`내 휴대폰에만 보관`**, `시뮬레이터` → **`예시 알림 넣어보기`**, `히스토리` → **`지난 기록`**, `트래커` → **`절약 기록`**

### 3.2. 한국어 어절·문맥 단위 줄바꿈 (`LineBreak.WordBreak.Phrase`) 적용
- **문제점**: 설명문이 길어 다음 줄로 넘어갈 때 한국어 단어(어절) 중간이 뚝 끊겨 가독성이 떨어짐
- **해결**:
  - [`Type.kt`](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/ui/theme/Type.kt)의 모든 본문·설명·제목 텍스트 스타일에 안드로이드 최신 한국어 구절 줄바꿈 옵션인 `LineBreak.Paragraph.copy(strategy = LineBreak.Strategy.HighQuality, wordBreak = LineBreak.WordBreak.Phrase)` 및 `LineBreak.Heading`을 전역 적용
  - [`SettingsAndCategoryScreen.kt`](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/ui/settings/SettingsAndCategoryScreen.kt), [`PendingQueueScreen.kt`](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/ui/queue/PendingQueueScreen.kt) 등의 긴 안내문은 문장·의미 단위로 줄바꿈(`\n`)과 넉넉한 줄 간격(`lineHeight = 18.sp~19.sp`)을 함께 적용

---

## 4단계: Firebase 실시간 클라우드 연동 (`<firebase-project-id>`)

### 4.1. Firebase SDK 및 프로젝트 설정 연결
- **의존성 및 플러그인 구성**:
  - `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`에 `com.google.gms.google-services (4.4.2)` 및 `firebase-bom (33.10.0)`, `firebase-auth`, `firebase-firestore`, `kotlinx-coroutines-play-services` 추가
- **실제 Firebase 프로젝트 연동**:
  - `app/google-services.json` (`project_id: <firebase-project-id>`, `package_name: com.budget.householdbook`) 배치 및 `processDebugGoogleServices` 빌드 검증 완료
- **Firebase Console 설정 완료**:
  1. **Authentication**: 익명(Anonymous) 및 Google 로그인 인증 활성화
  2. **Cloud Firestore**: 데이터베이스 생성 및 보안 규칙(`request.auth != null`) 게시 완료

### 4.2. 실시간 양방향 장부 동기화 (`RemoteSyncManager.kt`)
- `shared_spaces/{inviteCode}/transactions/{remoteId}` 경로에 실시간 `SnapshotListener`를 연결하여, 함께 쓰는 멤버가 내역을 추가·수정·취소하면 상대방 휴대폰의 Room DB와 화면에 즉시 반영되도록 구현

---

## 5단계: 구글 계정 로그인 · 첫 로그인 시 Free/Pro 자동 판별 · 파트너 초대 구현

### 5.1. 구글 계정 로그인 및 플랜(`기본` / `프리미엄`) 자동 판별
- **[`RemoteSyncManager.kt`](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/data/sync/RemoteSyncManager.kt) (`signInWithGoogleAccount`)**:
  - 사용자가 설정 탭에서 구글 이메일과 이름을 입력하거나 공식 Google 인증 창으로 로그인하면, 클라우드 데이터를 조회해 다음 순서로 계정을 자동 설정합니다:
    1. **초대장 도착 여부 확인 (`__invitations__`)**:
       - 가족·연인이 내 구글 이메일로 초대를 보낸 기록이 있으면, 첫 로그인이라도 **즉시 `프리미엄 (함께 쓰기)` 플랜으로 자동 전환**되고 초대받은 공유 장부(`inviteCode`)에 자동 연결됩니다.
    2. **기존 가입자 프로필 확인 (`__user_profiles__`)**:
       - 기존에 로그인했던 기록이 있으면 저장된 플랜(`PRO` 또는 `FREE`), 공유 초대번호, 함께 쓰는 멤버 이름을 그대로 복원합니다.
    3. **신규 가입자 초기 설정**:
       - 초대가 없는 첫 로그인 사용자는 **자동으로 `기본 (혼자 쓰기)` 플랜**으로 설정되며 클라우드에 초기 프로필이 생성됩니다.

### 5.2. 구글 계정을 통한 파트너 초대 및 공유
- **[`SettingsAndCategoryScreen.kt`](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/ui/settings/SettingsAndCategoryScreen.kt) & [`HouseholdRepository.kt`](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/data/repository/HouseholdRepository.kt)**:
  - 설정 탭 상단에 **`내 구글 계정 및 초대 연결`** 전용 카드를 배치하여 현재 로그인된 구글 계정, 적용된 플랜(`기본` / `프리미엄`), 공유 초대번호를 한눈에 표시
  - **`상대방 초대하기`** 모달에서 상대방의 구글 이메일(`예: partner@gmail.com`)과 호칭(`예: 짝꿍`)을 입력해 초대를 보내면, 상대방이 로그인하는 즉시 같은 공유 장부로 자동 묶이도록 구현
  - **`초대장 보내기`** 버튼을 통해 카카오톡·문자 등으로 초대 안내 메시지와 초대번호를 바로 공유할 수 있는 기능 함께 제공

---

## 6단계: 문서 정리 및 동작 검증 중 발견된 버그 수정 (2026-10-18)

### 6.1. 문서 정리
- `DESIGN_GUIDE.md` 2장 비교표에 잘못 붙여넣어진 Firebase 콘솔 화면 텍스트(계정 이름·프로젝트 목록 포함) 제거
- `GUIDE.md` / `TECH_STACK.md` / `DESIGN_GUIDE.md` / `history.md`의 클래스명·패키지 구조·스키마를 실제 코드와 일치시키고, 아직 구현되지 않은 항목(UseCase 계층, WorkManager, 말일 기준일 등)을 별도로 표시

### 6.2. 함께 쓰기(Firebase) 동기화 버그 수정 — [`RemoteSyncManager.kt`](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/data/sync/RemoteSyncManager.kt)
- **개인 용돈 유출 차단**: 분할 등록·취소 처리 시 `개인 용돈` 내역까지 `PENDING_UPLOAD`가 되어 공유 장부로 올라가던 문제 수정. 업로드는 `공통 생활비` 공간만 대상으로 하고, 수신 시에도 다른 공간 문서는 무시
- **고정 초대번호 제거**: 기본 초대번호 `HB-2026`이 모든 사용자에게 같아 서로 다른 가정의 장부가 하나로 섞일 수 있던 문제 수정. `generateInviteCode()`로 `HB-XXXX-XXXX` 형식의 무작위 번호를 발급
- **삭제 동기화**: 함께 쓰는 장부에서 삭제한 내역이 원격에 남아 다시 나타나던 문제 수정 (`PENDING_DELETE` → 원격 삭제 → 로컬 삭제, 상대방 기기는 `REMOVED` 변경을 받아 삭제)

### 6.3. 대기내역 처리 버그 수정 — [`HouseholdRepository.kt`](file:///c:/WorkSpace/Dev/Android/HouseholdBook/app/src/main/java/com/budget/householdbook/data/repository/HouseholdRepository.kt)
- 결제 취소 매칭을 `CancelMatcher`로 분리: 같은 금액이라도 가맹점명이 비슷한 **미취소 지출**만 매칭 (이전에는 아무 같은 금액 거래에 연결될 수 있었음)
- 원본을 찾지 못한 취소 알림(`CANCEL_UNMATCHED`)은 기본 유형을 `이체/제외`로 두고, `일괄 승인`에서 제외해 지출로 잘못 합산되지 않도록 함
- 이미 처리된 대기 항목의 중복 저장 방지, 분할 금액 합계 검증 추가
- 설정 화면에 `문자 자동 수집 켜기` 버튼 추가 (이전에는 `RECEIVE_SMS` 런타임 권한을 요청하는 곳이 없어 문자 수집이 동작하지 않았음)

### 6.4. 검증
- 단위 테스트 9건 통과 (`HouseholdBookCoreTest`, `CancelMatcherAndInviteCodeTest`)
- 에뮬레이터에서 확인: 대시보드 예산 계산, 예시 알림 4종, 실제 SMS 수신(에뮬레이터 `sms send`), 취소 자동 연결, 내 계좌 이체 쌍 감지, 일괄 승인

---

## 7단계: 공유 장부 보안 강화 — 멤버만 접근하는 Firestore 규칙 (2026-10-18)

### 7.1. 문제점
- 기존 `firestore.rules`는 `request.auth != null`(익명 로그인 포함)이면 모든 문서를 읽고 쓸 수 있어, **초대번호만 알면 누구나 남의 장부를 보고 고칠 수 있었음**
- 이메일을 직접 입력하면 익명 로그인으로 넘어가 **다른 사람의 이메일로 로그인해 그 사람에게 온 초대장을 가로챌 수 있었음**
- 규칙에 선언된 `/users`, `/invitations` 컬렉션은 실제로 쓰이지 않고, 프로필·초대장이 `shared_spaces/__user_profiles__`, `__invitations__` 아래에 섞여 있었음

### 7.2. 변경 내용
- **Firestore 구조 정리 ([`RemoteSyncManager.kt`](app/src/main/java/com/budget/householdbook/data/sync/RemoteSyncManager.kt))**:
  - 프로필 `users/{uid}`, 초대장 `invitations/{inviteCode}__{partnerEmail}`, 방 정보는 `shared_spaces/{inviteCode}` 문서 자체(`ownerUid`, `ownerEmail`, `memberEmails`, `memberNamesCsv`)로 이동
  - 동기화·초대 전에 `ensureRoomMembership()`으로 멤버인지 확인하고, 방이 없으면 내가 방장인 방을 생성. 멤버가 아니면 "초대받지 않은 공유 장부" 안내 후 동기화 중단
  - 예전 형식 초대번호(`HB-2026` 등)는 `isValidInviteCode()`로 걸러 새 번호로 교체
- **보안 규칙 ([`firestore.rules`](firestore.rules))**: 구글 로그인(`google.com`, 이메일 인증) 사용자만 허용. 방과 거래 내역은 `memberEmails` 멤버만, 프로필은 본인만, 초대장은 초대한 멤버·초대받은 사람만 접근. 초대받은 사람은 수락/거절 상태만 변경 가능
- **로그인**: 익명 로그인 제거. 이메일을 입력해도 항상 구글 인증 창으로 본인 확인을 하며, 인증한 계정이 입력한 이메일과 다르면 로그인하지 않음
- **설정 화면**: 초대번호 칸을 읽기 전용으로 변경 (다른 방 번호를 입력해 들어가는 방식 대신 구글 이메일 초대로만 연결). 초대 안내 문구도 함께 수정

### 7.3. 검증 및 남은 일
- 단위 테스트: 발급된 초대번호가 규칙 정규식과 일치하는지, 초대장 문서 ID 형식 테스트 추가 (`assembleDebug testDebugUnitTest` 성공, 단위 테스트 11건 통과)
- 규칙은 Firebase 에뮬레이터로 아직 실행해 보지 않았고, **Firebase 콘솔에 아직 게시하지 않음** (`firebase deploy --only firestore:rules`로 게시 필요. 게시 후 Authentication의 익명 로그인은 꺼도 됨)
- 예전 경로에 저장된 데이터는 옮기지 않음 (새 규칙에서는 접근 불가)
- `userTier`(PRO)는 아직 사용자가 직접 기록하는 값이라 서버에서 결제 여부를 강제하지 못함

---

## 8단계: 미구현 기능 개발 (2026-10-18)

- **매월 말일 정산 기준일**: `BillingCycleCalculator.LAST_DAY_OF_MONTH`(`99`) 추가 — 전월 말일 ~ 당월 말일 전날 주기, 2월·윤년 포함 주기 사이 빈틈 없음. 홈/설정의 기준일 입력을 `BillingStartDayField`(`날짜 지정 | 매월 말일`)로 통일
- **취소 원본 직접 선택**: `결제 취소 확인 필요` 항목에서 원본 결제를 골라 연결. 같은 금액은 전체 취소, 원본이 크면 부분 취소(원본 금액 차감 + 메모 기록)
- **백그라운드 작업 (`data/work/`)**: `SyncWorker`(오프라인에서 쌓인 공통 생활비 변경을 인터넷 연결 시 업로드 + 6시간 주기 보정), `QuickInquiryWorker`(KB 빠른조회 3/6/12/24시간 자동 조회, 설정 토글)
- **집밥 절약 홈 화면 위젯**: 이번 주기 절약 금액·횟수 표시, 끼니별 원터치 +1, 앱에서 기록·삭제·공간 전환·단가 변경 시 즉시 갱신
- **검증**: 단위 테스트 15건 통과 (`LastDayBillingCycleTest` 추가). 에뮬레이터에서 부분 취소(52,000원 → 19,000원), 말일 기준일(9.30 ~ 10.30), 위젯 추가·저녁 +1(25,000원 → 40,000원), `SyncWorker` 주기 작업 등록 확인
- **미확인**: 실제 Firebase 환경의 `SyncWorker` 업로드, 실제 KB 계좌로 `QuickInquiryWorker` 실행

---

## 9단계: 아키텍처 정리 — UseCase 계층 분리 및 Type-Safe Navigation (2026-10-18)

### 9.1. 도메인 계층 분리
- **저장소 인터페이스 (`domain/repository/HouseholdRepositories.kt`)**: `LedgerSpace`·`Category`·`PendingQueue`·`Transaction`·`Meal`·`AutomationRule`·`Settings`·`SharedSync`·`KbQuickInquiry` 저장소, `BackgroundTaskScheduler`, `MealWidgetRefresher`, 원자적 저장용 `TransactionRunner`
- **구현체 (`data/`)**: `RoomRepositories.kt`(DAO 위임만), `UserPreferencesRepository`(→ `SettingsRepository`), `FirebaseSharedSyncRepository`(`RemoteSyncManager` 어댑터), `KbQuickInquiryRepositoryImpl`, `BackgroundWorkScheduler`(→ `BackgroundTaskScheduler`), `AndroidMealWidgetRefresher`
- **UseCase (`domain/usecase/`)**: 약 1,000줄이던 `HouseholdRepository`와 `MainViewModel`의 예산 계산 로직을 기능별 UseCase로 옮기고 `HouseholdRepository`는 삭제
  - 수집: `EnqueueParsedDraftUseCase`(무시 키워드 → 분류 추천 → 취소 매칭 → 3분 이체 쌍), `IngestRawNotificationUseCase`, `RunKbQuickInquiryUseCase` 등
  - 대기 큐: `ApprovePendingItemUseCase`, `ApproveSplitPendingItemUseCase`, `ApplyMatchedCancelUseCase`, `LinkCancelToTransactionUseCase`, `ApproveAllPendingItemsUseCase`(처리·남은 건수를 `BulkApprovalResult`로 반환) 등
  - 거래·카테고리·예산·집밥·동기화·설정: `AddManualTransactionUseCase`, `DeleteTransactionUseCase`, `CalculateBudgetSummaryUseCase`(순수 함수), `RecordHomeCookedMealUseCase`, `SyncPendingSharedChangesUseCase`, `UpdateSharedMembersUseCase` 등
  - 화면 구독: `ObserveHouseholdOverviewUseCase` (공간·정산 주기에 맞춰 거래·집밥 쿼리를 다시 구독)
- **호출부**: `MainViewModel`은 기능별 UseCase 번들(`UseCaseBundles.kt`)만 주입받아 호출하고 스낵바 문구만 만듦. `SyncWorker`, `QuickInquiryWorker`, 알림 리스너, SMS 리시버, 집밥 위젯도 UseCase를 직접 주입받음
- **DI**: 단일 `appModule` → `databaseModule` / `repositoryModule` / `useCaseModule` / `viewModelModule` (`appModules`)
- 공용 모델 이동: `MealPeriodTotal`(DAO 파일), `MealWidgetSummary`(Repository 파일), `SharedSyncOutcome`(SyncWorker 파일), `CategoryGroup`·`CategorySpendSummary`(ViewModel 파일) → `domain/model/`

### 9.2. Navigation Compose 적용
- `MainActivity`의 `MainTab` 인덱스(`rememberSaveable`) 전환을 `@Serializable` 라우트 + `NavHost`로 교체 (`ui/navigation/`)
- 플로팅 캡슐 탭 바는 모양을 그대로 유지한 채 `ui/navigation/FloatingCapsuleTabBar.kt`로 옮기고, 선택 상태는 현재 백스택 라우트로 판별
- 탭 전환 애니메이션은 끄고(기존처럼 즉시 전환), 탭별 스크롤 상태는 저장·복원
- 동작 차이: 예전에는 어느 탭에서든 뒤로 가기를 누르면 앱이 종료되었지만, 이제는 홈이 아닌 탭에서 뒤로 가기를 누르면 홈으로 돌아감

### 9.3. 검증
- 단위 테스트 36건 통과 (기존 15건 + UseCase 테스트 21건: `PendingQueueUseCasesTest`, `BudgetMealAndSyncUseCasesTest`, 메모리 저장소 `FakeRepositories.kt`)
- 에뮬레이터: 변경 전 빌드로 기록한 대시보드 수치(공통 생활비 남은 예산 1,457,500원 / 지출 42,500원 / 하루 권장 112,115원, 개인 용돈 430,200원 / 69,800원 / 61,457원)가 새 빌드에서 동일. 예시 알림 4건 후 일괄 승인(2건 저장, 미매칭 취소 1건 남김), 미매칭 취소를 150,000원 직접 입력 거래에 연결(부분 취소 → 5,000원), 홈 화면 위젯 저녁 +1(40,000원 → 55,000원, 앱에도 반영), 탭 전환·대시보드 `확인하기` 이동 확인
- 미확인: 실제 Firebase 환경의 로그인·초대·동기화(코드 경로는 그대로 위임만 바꿈)

---

## 10단계: Git 저장소 구성 (2026-10-18)

### 10.1. 저장소 초기화
- `git init -b main` 후 현재 상태 전체를 첫 커밋 `e39d29e` ("HouseholdBook 초기 커밋 (v0.0.1)", 파일 105개)으로 기록
- 원격 저장소는 아직 연결하지 않음 (푸시 없음)

### 10.2. 제외 파일 정리 ([`.gitignore`](file:///c:/WorkSpace/Dev/Android/HouseholdBook/.gitignore))
- 기존 Android Studio 기본 규칙(`build/`, `.gradle/`, `local.properties`, `.idea` 개인 설정 등) 유지
- 추가 제외:
  - `.kotlin/` — Kotlin 컴파일러 캐시
  - `google-services.json` — Firebase 프로젝트 ID·API 키 포함. 파일이 없으면 Google Services 플러그인이 적용되지 않아 **로컬 전용 모드로 빌드**되므로, 저장소를 새로 받은 경우 Firebase 콘솔에서 받은 파일을 `app/`에 직접 넣어야 함
  - `*.jks`, `*.keystore` — 앱 서명 키

### 10.3. 줄바꿈 통일 ([`.gitattributes`](file:///c:/WorkSpace/Dev/Android/HouseholdBook/.gitattributes))
- 작업 폴더에 LF·CRLF가 섞여 있던 문제를 정리: 저장소 안의 텍스트 파일은 모두 LF
- `gradlew`는 LF, `gradlew.bat`은 CRLF로 고정해 macOS·Linux·Windows 어디서든 Gradle 래퍼가 실행되도록 함
- `*.jar`, `*.png`, `*.webp`, `*.jpg`는 바이너리로 지정
- 이 저장소에서만 `core.autocrlf = false` 설정 (줄바꿈 변환은 `.gitattributes`가 담당)

---

## 11단계: 출시 준비 — DB 마이그레이션 · 예시 데이터 분리 · R8 · 테스트 · CI (2026-10-18)

### 11.1. Room 마이그레이션 체계
- 사용자 데이터를 지우던 `fallbackToDestructiveMigration(true)`를 제거하고 `core/database/Migrations.kt`의 단계별 마이그레이션으로 교체
- `exportSchema = true` + `app/schemas/`에 버전별 스키마 JSON 저장 (v1 기준선, v2)
- **v1 → v2**: `transactions.remoteId` 고유 인덱스 추가 (동기화가 remoteId로 거래를 찾으므로). 이미 중복된 값은 지우지 않고 `-{id}`를 붙여 구분
- 검증: `MigrationTest` 2건 통과, 에뮬레이터에서 기존 v1 데이터 위에 새 버전을 설치해 24건 그대로 유지·인덱스 생성 확인

### 11.2. 예시 데이터 분리
- 첫 실행 시드(`SeedInitialDataUseCase`)는 기본 공간·카테고리·가맹점 분류 규칙만 생성
- 예시 거래·집밥 기록·대기 내역은 설정 > **앱 둘러보기 > 예시 데이터 넣기**(`InsertSampleDataUseCase`)로 원할 때만 추가, 두 번 눌러도 중복되지 않음

### 11.3. 출시 빌드
- R8 코드 축소·난독화 사용, 출시 APK 약 3.4MB. Worker·위젯·알림 리스너·SMS 리시버가 유지되는 것을 매핑 파일로 확인하고, 출시 빌드로 대시보드·예시 알림·예시 데이터·위젯 +1 동작 확인
- `keystore.properties`(저장소 제외) 기반 서명 설정, 없으면 디버그 키로 서명(로컬 확인용). 예시: `keystore.properties.example`

### 11.4. 테스트 추가
- 단위: `SeedAndSampleDataUseCasesTest` 2건 (총 38건 통과)
- 계측(에뮬레이터): `MigrationTest` 2건, `BillingStartDayFieldTest` 3건, `PendingQueueScreenTest` 3건(일괄 승인, 분할 합계, 미매칭 취소 원본 선택) — 총 9건 통과
- Firestore 규칙: `firestore-tests/rules.test.mjs` 16건, Firebase 에뮬레이터에서 모두 통과 (익명 차단, 비멤버 차단, 방장 보호, 개인 용돈 업로드 거부, 초대장 권한 등). `firebase.json`에 에뮬레이터 설정 추가. 비멤버도 거래를 읽을 수 있게 규칙을 일부러 느슨하게 바꾸면 해당 테스트가 실패하는 것도 확인 (`RULES_FILE` 환경 변수로 다른 규칙 파일 지정 가능)
- 실제 계정이 필요한 흐름은 [MANUAL_TEST_CHECKLIST.md](file:///c:/WorkSpace/Dev/Android/HouseholdBook/MANUAL_TEST_CHECKLIST.md)로 정리

### 11.5. CI
- `.github/workflows/ci.yml`: Android 빌드·단위 테스트·R8 빌드·스키마 커밋 누락 확인, Firestore 규칙 테스트
- Linux CI에서 실행되도록 `gradlew`에 실행 권한(100755) 부여

---

## 12단계: 시중은행 전체 지원 — 알림 수집 금융사 선택 · 은행별 빠른조회 구조 (2026-10-18)

### 12.1. 금융기관 목록 (`FinancialInstitutions`)
- 은행 23곳(시중 6, 특수 4, 인터넷 3, 지방 6, 상호금융·우체국 4) + 카드사 3곳의 앱 패키지·문자 별칭·빠른조회 상태를 한곳에서 관리
- 앱 패키지는 Google Play 스토어 상세 페이지로 하나씩 확인. 이 과정에서 **기존 신한 SOL페이 패키지(`com.shinhancard.smartshinhan`)가 존재하지 않는 값**이던 것을 `com.shcard.smartpay`로 바로잡고, 종료 예정인 구 하나원큐와 새 하나원큐(`com.hanabank.oqf`)를 함께 등록

### 12.2. 알림·문자 수집
- 설정 > `알림을 읽어올 금융사`에서 금융기관별로 켜고 끄기 (기본: 전체 + 그 외 금융 앱·문자)
- 문자는 본문의 금융기관 이름으로 판별(긴 이름 우선: `KB국민카드` vs `KB국민`), 알림은 앱 패키지로 판별
- 파서가 판별된 금융기관 이름만 가맹점명에서 지우도록 일반화하고, 계좌번호 마스킹(`352-****-1234-56` 등)·`체크카드`·`|` 구분자 정리 추가

### 12.3. 은행별 빠른조회
- 계좌 빠른조회 설정에 은행 선택 추가. KB국민은행만 앱 연동(`[Beta]`), 나머지 은행은 각 은행의 서비스 이름(간편조회·스피드계좌조회·빠른서비스 등)과 미리 등록하는 위치를 안내하는 `준비 중`, 인터넷은행·지역농축협은 `서비스 없음`
- `QuickInquiryProvider` 인터페이스와 `QuickInquiryCredentials`(은행 ID 포함)로 은행별 연동을 추가할 수 있는 구조로 변경
- 설정 다이얼로그가 예시 계좌번호·생년월일·비밀번호를 미리 채워두던 것을 제거 (그대로 저장될 위험)

### 12.4. 검증
- 단위 테스트 55건 통과 (`FinancialInstitutionsTest` 13건: 목록 무결성·패키지 판별·문자 판별·은행별 문구 파싱, `InstitutionFilterUseCaseTest` 4건)
- 에뮬레이터: 금융사 선택 다이얼로그, 신한은행을 끈 뒤 신한 문자는 건너뛰고 KB 문자만 대기내역에 들어오는 것(가맹점 `GS25`), 빠른조회 은행 목록·우리은행 `준비 중` 안내 확인
- 미확인: 실제 각 은행 앱 알림 문구 (테스트는 실제 문구를 본뜬 예시)
