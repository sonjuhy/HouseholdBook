# HouseholdBook (가계부 & 공통 생활비 관리 앱) AI 에이전트 개발 가이드 (`GUIDE.md`)

이 문서는 AI 코딩 에이전트가 **HouseholdBook** 안드로이드 프로젝트의 **도메인 요구사항, 비즈니스 규칙, 데이터 스키마 및 단계별 구현 로드맵**을 일관되게 준수하도록 작성된 기능 마스터 가이드입니다.
> **참고**:
> - 빌드 환경(`minSdk = 26`, `Java 17`), 라이브러리 버전(`libs.versions.toml`), DI(`Koin Compose`) 등 기술 스택 명세는 **[TECH_STACK.md](TECH_STACK.md)**를 참조하세요.
> - 컬러 팔레트, 화면별 와이어프레임, 시각적 레퍼런스 링크 등 UI/UX 디자인 명세는 **[DESIGN_GUIDE.md](DESIGN_GUIDE.md)**를 참조하세요.

---

## 1. 프로젝트 개요 및 유저 티어 정책 (Project Overview & User Tiers)

**HouseholdBook**은 개인의 용돈과 다수 인원이 함께 사용하는 **공통 생활비**를 분리·통합 관리하고, 금융 앱 알림/SMS 및 KB국민은행 빠른조회 등을 통해 수집된 지출·수입 내역을 **대기 큐(Pending Queue)**에서 검수 후 등록하며, **전체 목표 예산 대비 잔여 금액 추적** 및 **집밥(직접 해먹은 끼니) 절약 금액 카운트** 기능을 제공하는 안드로이드 애플리케이션입니다.

- **Package Name**: `com.budget.householdbook`
- **Target Platform**: Android (`minSdk = 26`, `targetSdk = 37`, `compileSdk = 37`, `Java 17`)
- **기술 스택 상세**: [TECH_STACK.md](TECH_STACK.md) 참조

### 1.1. 유저 티어 (`UserTier`: `FREE` vs `PRO`) 및 오프라인 퍼스트 정책
1. **`FREE` 티어 (무료 유저)**:
   - **로컬 DB (`Room`) 전용**으로 동작합니다.
   - 개인 용돈 및 단일 기기 내 장부 관리 기능은 모두 사용 가능하지만, **멀티유저 공유(공통 생활비에 타 유저 초대 및 실시간 동기화) 기능은 잠금(Lock)** 처리되며 선택 시 Pro 업그레이드 안내 모달을 표시합니다.
2. **`PRO` 티어 (유료/구독 유저)**:
   - **Firebase (`Firebase Auth` + `Cloud Firestore`)**를 기본 백엔드로 사용하여 여러 사람(부부, 커플, 가족 등)이 하나의 **공통 생활비(`SHARED` 공간)**에 참여해 함께 내역을 관리할 수 있습니다.
   - **오프라인 사용 필수 보장 (Offline-First Architecture)**:
     - `PRO` 티어 유저라도 네트워크가 없는 오프라인 환경에서 앱의 모든 기능(조회, 수동 입력, 대기 큐 승인/수정, 예산 확인, 집밥 카운트)이 100% 정상 동작해야 합니다.
     - 이를 위해 앱의 **단일 진실 공급원(SSOT)은 항상 로컬 Room DB**로 두며, 변경 사항은 `syncStatus` (`SYNCED`, `PENDING_UPLOAD`, `PENDING_DELETE`) 플래그와 백그라운드 동기화(`WorkManager`)를 통해 네트워크 연결 시 자동 반영합니다.
     - `PRO` 구독이 만료되어 `FREE`로 전환되더라도 로컬 DB에 저장된 기존 내역은 유실되지 않고 로컬 단독 모드로 안전하게 유지됩니다.

---

## 2. 핵심 도메인 요구사항 (Core Domain Requirements)

AI 에이전트는 기능 구현 시 아래 핵심 도메인 요구사항을 정확히 준수해야 합니다.

### 2.1. 자동 수집 파이프라인 (Auto-Ingestion Pipeline)
수집 경로는 **1순위(기본 실시간 수집)**와 **2순위(Beta 빠른조회 수집)**로 구분합니다.

1. **[1순위 - 기본] 실시간 푸시 알림 및 SMS 파싱**:
   - **앱 푸시 알림 (`NotificationListenerService`)**: 아래 금융기관 앱의 푸시 알림을 실시간 감지 및 분류합니다. 목록에 없는 앱도 패키지 이름에 `bank`·`card`·`pay`·`toss`가 들어가면 `그 외 금융 앱`으로 읽습니다.
   - **문자 메시지 (`BroadcastReceiver`)**: 카드 승인/취소 및 은행 입출금 SMS/LMS 텍스트를 감지하고, 본문의 금융기관 이름으로 은행·카드사를 판별합니다.
   - **지원 금융기관 (`domain/model/FinancialInstitutions.kt`, 앱 패키지는 Play 스토어에서 확인)**:

     | 분류 | 금융기관 |
     | :--- | :--- |
     | 시중은행 | KB국민, 신한, 우리, 하나, SC제일, 한국씨티 |
     | 특수은행 | NH농협, IBK기업, KDB산업, Sh수협 |
     | 인터넷은행 | 카카오뱅크, 케이뱅크, 토스뱅크·토스 |
     | 지방은행 | BNK부산, BNK경남, iM뱅크(대구), 광주, 전북, 제주 |
     | 상호금융·우체국 | 지역농축협(NH콕뱅크), 새마을금고, 신협, 우체국 |
     | 카드·간편결제 | KB국민카드(KB Pay), 신한카드(SOL페이), 현대카드 |
   - **금융사 선택**: 설정 > `알림을 읽어올 금융사`에서 금융기관별로 켜고 끌 수 있으며(기본값: 전체 + 그 외 금융 앱·문자), 꺼둔 금융기관의 알림·문자는 대기 큐에 넣지 않습니다 (`AppUserSettings.enabledInstitutionIds`).
   - **앱별 알림 제외**: 같은 결제가 카드 앱·은행 앱·문자에서 모두 오는 경우를 위해, 실제로 금융 알림을 보낸 앱(과 문자)을 설정 > `앱별 알림 제외`에 모아 보여주고 앱 단위로 끌 수 있습니다 (`excludedSourceIds`, 출처 ID = 앱 패키지 / 문자는 `sms`). 대기내역 삭제 다이얼로그에서도 `앞으로 '앱 이름' 알림은 읽지 않기`로 바로 끌 수 있습니다.
   - **같은 결제 알림 자동 거르기** (기본 켜짐, `DuplicateAlertDetector`): 서로 다른 출처에서 **3분 이내 · 같은 금액 · 같은 방향(지출/수입, 결제/취소)** 알림이 오고 가맹점이 비슷하거나(정규화 비교) 한쪽이 가맹점을 알 수 없는 알림이면 처음 들어온 것만 남깁니다. 같은 출처에서 연달아 온 같은 금액은 실제 두 번 결제일 수 있어 거르지 않습니다.
   - **새 알림 내역을 담을 공간**: 가맹점 학습 규칙 > 설정의 기본 공간(`보던 공간 | 공통 생활비 | 내 용돈`) > 마지막으로 보던 공간 순으로 추천 공간을 정합니다. 가맹점 규칙이 여러 개 맞으면 **가장 최근에 학습한 규칙**을 씁니다 (첫 실행 때 만든 기본 규칙이 사용자의 나중 선택을 덮지 않도록).
2. **[2순위 - Beta 기능] 은행 계좌 무로그인 빠른조회 (`QuickInquiryProvider`)**:
   - UI 상에 **`[Beta]`** 배지를 붙여 제공하는 기능이며, 설정 > `계좌 빠른조회 등록`에서 은행을 먼저 고릅니다.
   - 은행별 상태: **KB국민은행 `빠른조회` = 앱 연동 지원**, 신한(간편조회)·우리(스피드계좌조회)·하나·SC제일·씨티·NH농협·IBK기업·KDB산업·수협·지방은행·새마을금고·신협·우체국 = 은행은 서비스를 제공하지만 **앱 연동 준비 중**(다이얼로그에 서비스 이름과 은행 등록 위치 안내), 인터넷은행·지역농축협 = **서비스 없음**(알림 수집으로 대체).
   - 은행을 추가하려면 `QuickInquiryProvider`를 구현해 `di/AppModule.kt`의 provider 목록에 넣고 해당 은행의 `quickInquiry.status`를 `SUPPORTED_BETA`로 바꿉니다.
   - KB국민은행의 **무로그인 빠른조회 웹페이지**를 대상으로 사용자가 사전 등록한 조회 정보(계좌번호, 생년월일, 빠른조회 비밀번호 등 — 암호화 저장)를 이용해 최근 입출금 내역을 긁어옵니다.
   - 스크래핑된 내역은 고유 해시(`dedupHash`: 거래일시 + 입출금액 + 잔액 + 적요)를 생성하여 이미 수집된 내역과 비교한 뒤, **신규 내역만 등록 대기 큐(`PendingQueue`)에 추가**합니다.

### 2.2. 등록 대기 큐 (`PendingTransactionQueue`) 및 3대 예외 처리 규칙
> **검수 중 선택 저장 원칙**: 대기 카드에서 고른 공간·유형(이체 제외)·분류는 화면 상태가 아니라 대기 항목(`suggestedSpaceId`, `parsedType`, `suggestedCategoryId`, `suggestedSubCategoryId`)에 즉시 저장합니다 (`UpdatePendingClassificationUseCase`). 그래야 일괄 승인·수정·목록 스크롤 후에도 고른 값이 유지됩니다. 공간을 바꾸면 카테고리도 그 공간 것으로 맞춥니다 (`CategoryDefaults`).
자동 수집된 모든 내역은 절대 최종 장부(`Transaction`)에 즉시 등록되지 않으며, 반드시 **등록 대기 큐(`PENDING`)**를 거쳐 사용자가 **삭제, 수정, 저장(승인)** 할 수 있어야 합니다.

#### 예외 1: 결제 취소 / 승인 취소 알림 매칭
- 알림/문자에서 `"취소"`, `"승인취소"`, `"환불"` 키워드가 감지된 경우:
  1. **자동 매칭 시도**: 최근 확정 거래(`Transaction`) 또는 대기 큐(`PendingTransaction`)에서 **동일 금액 + 동일/유사 가맹점명(또는 동일 카드사)**을 가진 원본 결제 내역을 탐색합니다.
  2. **매칭 성공 시**: 대기 큐 상단에 `[기존 결제 취소 매칭됨]` 배지(`CANCEL_MATCHED`)와 함께 원본 거래 정보를 연결해 보여주며, 사용자가 **[취소 반영]**을 누르면 원본 거래를 취소 상태(`isCanceled = true`)로 전환하거나 상쇄 처리합니다.
  3. **매칭 실패/불확실 시**: 임의로 무시하지 않고 대기 큐에 `CANCEL_UNMATCHED` 상태로 적재하여 사용자가 직접 대상 거래를 지정하거나 수정·삭제할 수 있게 합니다.
     - 지출로 잘못 합산되지 않도록 기본 유형은 `TRANSFER`(이체/제외)로 두며, **[일괄 승인] 대상에서 제외**되어 대기 목록에 남습니다.
     - 카드의 **[원본 결제 선택]** 버튼으로 최근 60일 미취소 지출 중 환불 금액 이상인 거래(같은 금액 우선)를 골라 연결합니다. 금액이 같으면 원본을 취소(`isCanceled`) 처리하고, 원본이 더 크면 환불 금액만큼 원본 금액을 차감(부분 취소)합니다.
  4. **매칭 규칙 (`CancelMatcher`)**: 최근 30일 내 **동일 금액**이면서 아직 취소되지 않은 **지출(`EXPENSE`)** 중, 정규화한 가맹점명이 서로 포함되거나 앞 3글자가 같은 거래만 연결합니다. 금액만 같고 가맹점이 다르면 연결하지 않습니다.

#### 예외 2: 분할 등록 (Split Transaction — 공통 생활비 + 개인 용돈 쪼개기)
- 예: 마트에서 50,000원을 결제했으나 40,000원은 `공통 생활비`, 10,000원은 `개인 용돈`인 경우.
- 대기 큐의 항목 수정/승인 모달에서 **[금액 나누기(분할 등록)]** 기능을 제공합니다.
- 분할 시 원본 대기 큐 항목(50,000원) 1건이 승인(`APPROVED`) 처리되면서, 확정 거래 테이블(`Transaction`)에는 분할된 2건 이상의 레코드(각각 다른 `spaceId` 또는 다른 세부 카테고리)가 동일한 `originQueueId`를 공유하며 생성됩니다. (분할 금액의 합계는 원본 금액과 일치해야 함).

#### 예외 3: 내 계좌 간 이체 / 카드 대금 출금 / 자동 제외 구체화
실제 지출·수입이 아닌 내역이 예산과 지출 합계를 왜곡하지 않도록 3단계 필터링 체계를 구현합니다:
1. **거래 유형(`TransactionType`) 분리**:
   - `EXPENSE` (지출 - 예산 차감 O)
   - `INCOME` (수입 - 수입 합산 O)
   - `TRANSFER` (계좌 간 이체 / 카드대금 납부 - 장부 이력에는 남기되 지출·수입 통계 및 예산 소진액에서는 제외)
2. **자동 이체/중복 감지 엔진 (`TransferDetector`)**:
   - **키워드 기반**: `"카드대금"`, `"결제대금출금"`, `"자동납부"`, 또는 **사용자 본인의 실명**(내 계좌 ↔ 내 계좌 이체)이 입출금자명에 포함된 경우 대기 큐 진입 시 기본 타입을 `TRANSFER`(집계 제외)로 자동 지정합니다.
   - **시간차 쌍(Pair) 감지**: 3분 이내에 서로 다른 두 금융 앱(예: 토스 출금 100,000원 + KB 입금 100,000원)에서 **동일 금액의 출금과 입금이 쌍으로 수집**되면 대기 큐에서 `[내 계좌 간 이체 추정]`(`TRANSFER_PAIR`)으로 묶어 보여주며, 원터치로 둘 다 이체/제외 처리할 수 있게 합니다.
3. **사용자 정의 무시 규칙 (`IgnoredKeywordEntity`, 테이블 `ignored_keywords`)**:
   - 대기 큐에서 특정 알림을 삭제할 때 ***"다음부터 이 키워드/발신처 알림은 대기 큐에 넣지 않기(자동 무시)"*** 옵션을 제공하여 반복되는 불필요 알림을 차단합니다.

### 2.2-1. 휴대폰 알림 (1차: 새 결제 · 예산 · 자동 기록 멈춤)
사용자가 무언가를 해야 할 때만 알림을 보냅니다 (저장 완료 같은 확인은 화면 안 스낵바).
1. **새 결제 대기**: 결제 알림·문자가 대기내역에 들어오면 `가게 이름 / -5,800원 · 어디에 저장할까요?` 알림. `[공용에 저장] [개인에 저장]` 버튼으로 앱을 열지 않고 저장 (분류: 추천 공간이면 추천 분류 → 그 공간에서 이 가게로 배운 분류 → 그 공간의 첫 분류). 결제 취소·내 계좌 이체는 버튼 없이 앱에서 확인. 2건 이상이면 `확인할 결제 N건`으로 묶고, 앱에서 처리한 항목의 알림은 자동으로 지움. 백그라운드 은행 불러오기는 `새 결제 N건` 한 번.
2. **예산 80% · 100%**: 공간마다 정산 주기마다 단계별로 한 번 (`예산의 80%를 썼어요 · 남은 예산 · 하루 쓸 수 있는 금액`, `예산을 넘었어요`). 거래가 어디서 바뀌든(알림 버튼 저장, 대기내역, 직접 입력, 수정, 함께 쓰는 멤버의 기록) 확인.
3. **자동 기록 멈춤**: 결제 알림 읽기 권한을 쓰다가 꺼지면 12시간마다 확인해 한 번 알림(누르면 권한 설정), 은행 계좌 자동 불러오기가 연속 3회 실패하면 한 번 알림. 다시 켜지거나 성공하면 알림을 지움.
- 채널: `새 결제` / `예산` / `자동 기록 상태` (Android 설정에서 종류별로 끄기). 금액·가게 이름은 잠금화면에서 숨김.
- 알림 권한(Android 13+)은 설정 > 알림 > `알림 켜기`에서 요청.
- 함께 쓰는 멤버 소식(상대의 큰 지출, 초대 수락 등)은 앱이 꺼져 있을 때도 받으려면 서버 푸시(FCM + Cloud Functions)가 필요해 2단계로 미룸.

### 2.3. 계층형 카테고리 및 세부 카테고리 사용자 관리 (Category & Sub-Category)
1. **상위 카테고리(`Category`) + 세부 카테고리(`SubCategory`) 2단계 구조**:
   - 지출(`EXPENSE`)과 수입(`INCOME`) 각각에 대해 상위 카테고리(예: `식비`, `생활/마트`, `교통`, `고정비`, `용돈`)와 세부 카테고리(예: `식재료`, `배달/외식`, `카페/간식`, `생필품`)를 지정할 수 있습니다.
2. **사용자 직접 관리 (CRUD)**:
   - 사용자가 상위 카테고리와 세부 카테고리를 자유롭게 **추가, 이름/아이콘/색상 수정, 순서 변경, 삭제(소프트 삭제 `isArchived = true`)** 할 수 있어야 합니다.
   - **공용·개인 분류 따로 관리** (설정 > `분류 관리`): `공용 분류 | 개인 분류` 탭으로 장부별 분류를 따로 추가·수정·삭제. 처음에는 지금 보고 있는 장부의 분류를 보여주고, 탭을 바꿔도 상단 장부 전환은 그대로. 한쪽을 바꿔도 다른 쪽 분류는 그대로 (같은 이름의 분류가 공용·개인에 각각 있어도 됨, 같은 장부·같은 대분류 안에서는 중복 불가). 세부 분류는 같은 장부의 대분류 아래에만 붙고 대분류의 수입/지출 유형을 따름.
   - 장부 입력·대기내역·내역 수정의 분류 선택도 그 거래의 장부 분류만 보여줌.
   - **공용 분류는 함께 쓰는 사람과 동기화** (함께 쓰기 + 로그인 상태): 한 사람이 공용 분류를 추가·수정·삭제하면 상대 휴대폰에도 반영. 개인 분류는 휴대폰에만.
     - 두 사람의 같은 기본 분류(처음 설치할 때 만든 `식비` 등)는 하나로 합쳐지고, 따로 만든 같은 이름 공용 분류도 처음 연결할 때 하나로 합쳐짐.
     - 같은 분류를 양쪽에서 바꾸면 나중에 바꾼 쪽이 남음. 지운 분류는 숨김으로 남아 기존 거래는 계속 그 분류로 보임.
     - 공용 거래는 분류의 원격 ID를 함께 올려, 상대 휴대폰에서도 같은 분류로 보임 (업데이트 전에 받아 둔 거래도 상대가 다시 올리면 바로잡힘).
3. **자동 분류 학습**:
   - 사용자가 대기 큐에서 특정 가맹점(예: `"배달의민족"`)을 특정 세부 카테고리(`식비 > 배달/외식`)로 지정해 저장하면, 이후 동일 가맹점 알림 수집 시 해당 세부 카테고리를 자동으로 기본 선택해 줍니다.

### 2.4. 공통 생활비 vs. 개인 용돈 분리 관리 (Shared vs. Personal Spaces)
1. **장부 공간(`LedgerSpace`) 분리**:
   - **공통 생활비 (`SHARED`)**: 생활비, 공과금, 공동 식비 등을 관리하는 공간 (`PRO` 티어에서는 Firebase를 통해 여러 멤버가 함께 접속/공유, `FREE` 티어에서는 로컬 전용).
   - **개인 용돈 (`PERSONAL`)**: 각자 개인적으로 쓰는 용돈, 취미, 개인 수입 등을 타인에게 노출하지 않고 독립 관리하는 공간.
   - **초대 수락**: 따로 누르는 수락 버튼은 없음. 초대받은 사람이 초대받은 그 구글 이메일로 로그인하면 자동으로 수락되고 초대한 사람의 공유 장부에 연결됨. 이미 로그인해 둔 상태라면 앱을 열거나 앱으로 돌아올 때 받은 초대를 확인해 자동으로 수락(`초대를 받아 공유 장부에 연결됐어요` 안내, 15초 안에는 다시 확인하지 않음).
   - **함께 쓰는 멤버 관리** (설정 > 내 구글 계정 및 초대 연결 > `함께 쓰는 멤버 관리`):

     | 멤버 상태 | 뜻 | 할 수 있는 관리 |
     | :--- | :--- | :--- |
     | 방장 | 공유 장부를 만든 사람(또는 넘겨받은 사람) | 내보낼 수 없고 나갈 수도 없음. **함께 쓰는 중인 멤버에게 방장 넘기기** 후 나가기·탈퇴 가능 |
     | 함께 쓰는 중 | 초대를 수락(구글 로그인)한 멤버 | **방장만** 내보내기 |
     | 수락 대기 | 초대했지만 아직 로그인하지 않은 사람 | **멤버 누구나** 초대 취소 (초대장도 취소 상태가 되어 나중에 로그인해도 연결되지 않음) |
     | 나 (방장 아님) | | 공유 장부에서 나가기 |
   - 내보내지거나 스스로 나간 멤버의 휴대폰은 공통 생활비를 혼자 쓰기 상태로 바꾸고 기록은 그대로 둡니다. 그 멤버가 남긴 기록은 공유 장부에도 그대로 남습니다.
   - **회원 탈퇴** (설정 > 내 구글 계정 카드 맨 아래 `회원 탈퇴`): 구글 계정을 한 번 더 선택해 본인 확인 → 공유 장부 정리(멤버면 나가기, 혼자 남은 방장이면 공유 장부·공유 기록 삭제, 함께 쓰는 멤버가 있는 방장이면 먼저 방장 넘기기 안내) → 보낸·받은 초대장, `users/{uid}` 프로필, 로그인 계정 삭제. 이 휴대폰의 기록은 기본으로 남기고(로그인 없이 계속 사용), `이 휴대폰의 기록도 모두 지우기`를 고르면 앱 데이터를 모두 지우고 앱이 닫힙니다.
2. **빠른 공간 전환**:
   - 홈 대시보드 및 장부 화면 상단에 세그먼트 탭/드롭다운으로 **`[공통 생활비 | 내 용돈]`** 스위처를 배치하여 즉시 전환할 수 있어야 합니다.
   - 단, **대기내역 탭에서는 상단 스위처를 숨깁니다.** 대기내역은 모든 공간의 항목을 함께 검수하고, 저장할 공간은 각 카드의 `저장할 곳 [개인 | 공용]`과 `{공간}에 저장` 버튼으로 정합니다.

### 2.5. 전체 목표 예산 및 정산 기준일 관리 (Budget Goals & Custom Billing Cycle)
1. **공간별 전체 목표 금액 중심 (단순하고 명확한 구조)**:
   - 세부 카테고리별로 복잡하게 쪼개는 목표 설정은 두지 않고, **각 공간/대분류 단위(예: 공통 생활비 이번 달 목표 150만 원, 개인 용돈 이번 달 목표 50만 원)**의 **전체 목표 금액(`targetBudgetAmount`)**을 설정합니다.
   - 대시보드 상단에서 즉시 확인할 수 있는 핵심 지표:
     - **목표 금액 (`targetBudgetAmount`)**
     - **현재까지 쓴 금액 (`spentAmount`)**
     - **남은 금액 (`remainingAmount = targetBudgetAmount - spentAmount`)** 및 소진율 프로그레스 바 (초과 시 초과 금액 경고 표시)
     - **오늘 권장 지출액** (`남은 금액 ÷ 정산일까지 남은 일수`)
2. **사용자 지정 월 기준일 (`billingStartDay`)**:
   - 기본값은 **`매월 1일` (1일 ~ 말일)**입니다.
   - 사용자가 설정에서 **`1일 ~ 28일` 또는 `매월 말일`** 중 원하는 날짜(예: 급여일/생활비 입금일인 `25일`)를 기준일로 선택할 수 있습니다.
   - `매월 말일`(`99`, `BillingCycleCalculator.LAST_DAY_OF_MONTH`)은 전월 말일 ~ 당월 말일 전날 주기이며, 달마다 일수가 달라도 매달의 말일로 다시 계산합니다 (예: 1.31 ~ 2.27, 2.28 ~ 3.30).
   - 기준일을 변경하더라도 과거 데이터가 꼬이지 않도록 모든 집계는 `BillingCycleCalculator`가 산출한 `[startMillis, endMillis)` 기간 범위 쿼리로 동적 계산합니다.

### 2.6. 부가 기능: 끼니 직접 해먹기(집밥) 카운터 (Home-Cooked Meal Saver)
복잡한 식재료 차감 계산 없이 **직관적이고 가볍게(Rough & Simple)** 성취감을 주는 부가 기능으로 구현합니다.
1. **끼니별 단일 단가 설정 (`MealUnitPriceConfig`)**:
   - 끼니 종류(`아침`, `점심`, `저녁`, `야식/간식`)별로 **1회당 지정된 단일 금액**을 설정합니다.
   - 예: 아침 `6,000원`, 점심 `10,000원`, 저녁 `15,000원` (사용자가 설정 화면에서 언제든 단가 수정 가능).
2. **원터치 카운트 & 단순 `횟수 × 금액` 산출**:
   - 사용자가 직접 해먹은 끼니 버튼(예: `[점심 집밥 +1]`, `[저녁 집밥 +1]`)을 누르면 즉시 기록됩니다.
   - **절약 금액 표시**: 단순히 **`횟수 × 해당 끼니 지정 금액`의 합산**으로 계산하여 *"이번 달 총 N끼 직접 해먹어 배달비·외식비 OOO,OOO원을 아꼈어요!"* 형태로 직관적인 카드 UI를 제공합니다.
   - 실계좌 지출/수입 통계와는 완전히 분리된 별도 테이블로 관리합니다.
3. **아낀 금액 직접 입력 (`MealType.CUSTOM`)**:
   - 정해진 끼니 단가와 맞지 않을 때(도시락, 텀블러 커피 등) `✏️ 아낀 금액 직접 입력` 버튼(홈 카드·집밥절약 탭)으로 금액(필수, 1원~100만 원)과 내용(선택)을 입력해 기록합니다.
   - 같은 `meal_saving_logs` 테이블에 `mealType = CUSTOM`, `unitAmount = savedAmount = 입력 금액`, `count = 1`, `memo = 입력한 내용`으로 저장하므로 합계·위젯·끼니 수에 그대로 포함됩니다 (enum 이름으로 저장되어 DB 변경 없음).
   - 원터치 +1 버튼과 홈 화면 위젯은 `MealType.quickTypes`(아침·점심·저녁·간식)만 보여줍니다.

---

## 3. 패키지 구조 가이드 (Package Structure)

`app/src/main/java/com/budget/householdbook/` 하위의 **현재 실제 구조**입니다. 의존 방향은 `ui → domain/usecase → domain/repository(인터페이스) ← data(구현체)`이며, `di/AppModule.kt`가 구현체를 인터페이스에 연결합니다.

```text
com.budget.householdbook
├── MainActivity.kt                      # 화면 골격 (상단 헤더 + HouseholdNavHost + 플로팅 캡슐 탭바)
├── HouseholdBookApp.kt                  # Koin 초기화 Application 클래스
├── di/AppModule.kt                      # Koin 모듈 4개: databaseModule / repositoryModule / useCaseModule / viewModelModule (appModules)
├── core/
│   ├── common/BillingCycleCalculator.kt # 월 기준일 주기 계산 + CurrencyAndDateFormatter(원화/날짜 포맷)
│   ├── database/AppDatabase.kt          # Room DB (version 1), 기본 공간 ID 상수
│   ├── datastore/                       # UserPreferencesRepository (UserTier, 테마, 실명, 로그인, 끼니별 단가)
│   ├── security/                        # SecureCredentialManager (KB 빠른조회 자격증명 Keystore AES/GCM 암호화)
│   └── designsystem/IosComponents.kt    # iOS 스타일 공통 Compose 컴포넌트
├── data/
│   ├── local/dao/HouseholdDaos.kt       # 모든 DAO
│   ├── local/entity/Entities.kt         # 모든 Room Entity (SSOT)
│   ├── ingestion/
│   │   ├── notification/                # FinancialNotificationListenerService
│   │   ├── sms/                         # SmsBroadcastReceiver
│   │   ├── kbquick/                     # [Beta] KbQuickInquiryScraper (무로그인 빠른조회), KbQuickInquiryRepositoryImpl
│   │   └── parser/                      # FinancialParserEngine(파싱·이체 감지), CancelMatcher(취소 매칭)
│   ├── sync/                            # [Pro] RemoteSyncManager(구글 로그인, 초대, Firestore <-> Room 실시간 동기화),
│   │                                    #   FirebaseSharedSyncRepository(SharedSyncRepository 구현 어댑터)
│   ├── work/                            # BackgroundWorkScheduler(BackgroundTaskScheduler 구현), SyncWorker(오프라인 업로드), QuickInquiryWorker(KB 자동 조회)
│   └── repository/RoomRepositories.kt   # Room DAO 위임 구현체 (RoomTransactionRunner, RoomPendingQueueRepository 등) — 비즈니스 로직 없음
├── domain/
│   ├── model/                           # Models.kt(Enum, SplitApprovalItem, MealWidgetSummary 등), OverviewModels.kt(BudgetSummary, HouseholdOverview 등)
│   ├── repository/HouseholdRepositories.kt # 저장소 인터페이스 (Space, Category, PendingQueue, Transaction, Meal, AutomationRule, Settings,
│   │                                    #   SharedSync, KbQuickInquiry, BackgroundTaskScheduler, MealWidgetRefresher, TransactionRunner)
│   └── usecase/                         # 비즈니스 규칙 (Android 의존성 없음, JVM 단위 테스트 대상)
│       ├── IngestionUseCases.kt         # 대기 큐 적재(무시 키워드·분류 추천·취소 매칭·이체 쌍), 알림 파싱, KB 빠른조회·자동 조회
│       ├── PendingQueueUseCases.kt      # 단건/분할/일괄 승인, 취소 반영, 취소 원본 직접 연결(전체/부분), 삭제·무시 키워드
│       ├── TransactionUseCases.kt       # 직접 입력, 취소, 삭제(공유 중이면 PENDING_DELETE)
│       ├── CategoryUseCases.kt          # 카테고리 추가/수정/소프트 삭제
│       ├── BudgetUseCases.kt            # 목표 예산·기준일 변경, 예산 요약 계산(CalculateBudgetSummaryUseCase)
│       ├── MealUseCases.kt              # 집밥 +1, 삭제, 단가 변경, 위젯 요약
│       ├── SharedSyncUseCases.kt        # [Pro] 동기화, 로그인·초대·로그아웃, 공유 멤버, 플랜 변경
│       ├── SettingsUseCases.kt          # 공간 전환, 화면 스타일, 무시 키워드 삭제, 처음 실행 튜토리얼 확인 여부
│       ├── AppStartupUseCases.kt        # 기본 시드(SeedInitialDataUseCase), 예시 데이터 넣기(InsertSampleDataUseCase), 앱 시작 초기화
│       ├── ObserveHouseholdOverviewUseCase.kt # 현재 공간·정산 주기 기준 화면 데이터 구독
│       └── UseCaseBundles.kt            # ViewModel 주입용 기능별 묶음 (PendingQueueUseCases 등)
└── ui/
    ├── MainViewModel.kt                 # 앱 전역 단일 ViewModel (HouseholdUiState) — UseCase 호출과 스낵바 문구만 담당
    ├── theme/                           # Color, Theme, Type (iOS 컬러 토큰, 한국어 줄바꿈)
    ├── onboarding/                      # 처음 실행 튜토리얼 (설정 > 앱 사용법 다시 보기로 다시 열기)
    ├── dashboard/                       # 공간 스위처, 목표 예산 프로그레스, 대기내역 배지, 집밥 요약
    ├── queue/                           # 대기내역 검수 (수정, 분류, 분할 등록, 취소 반영, 일괄 승인)
    ├── ledger/                          # 기간별(기준일 적용) 지출/수입 내역 및 직접 입력
    ├── mealsaver/                       # 끼니별 +1 카운터 & 누적 절약 금액
    ├── settings/                        # 구글 계정·초대, 플랜, 테마, 예산·기준일, 카테고리, 알림/문자 권한, KB 빠른조회·자동 조회
    ├── widget/                          # MealSaverWidgetProvider (집밥 절약 홈 화면 위젯, 원터치 +1), AndroidMealWidgetRefresher
    └── navigation/                      # Type-Safe Navigation: @Serializable 라우트 + TopLevelDestination, HouseholdNavHost, IosFloatingCapsuleTabBar
```

---

## 4. 핵심 데이터 스키마 명세 (Core Data Schema Blueprint)

### 4.1. 장부 공간 (`LedgerSpaceEntity`)
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `id` | `String` (PK) | 공간 고유 ID (UUID) |
| `name` | `String` | 공간 이름 (예: `"공통 생활비"`, `"개인 용돈"`) |
| `type` | `SpaceType` | `SHARED` (공통 생활비) / `PERSONAL` (개인 용돈) |
| `billingStartDay` | `Int` | 월 정산 시작 기준일 (기본값 `1`, `1..28` 또는 말일 `99`) |
| `targetBudgetAmount`| `Long` | 해당 공간의 월간 전체 목표 금액 (원 단위, 예: `1500000`) |
| `memberNamesCsv` | `String` | 함께 쓰는 멤버 이름 목록 (쉼표 구분) |
| `inviteCode` | `String?` | 공유 장부 초대번호 (`HB-XXXX-XXXX` 무작위 발급, 미공유 시 `null`) |
| `isRemoteShared` | `Boolean` | `PRO` 티어 Firebase 공유 활성화 여부 (`FREE` 티어는 항상 `false`) |
| `syncStatus` | `SyncStatus` | `SYNCED`, `PENDING_UPLOAD`, `PENDING_DELETE` (오프라인 동기화용) |

### 4.2. 카테고리 & 세부 카테고리 (`CategoryEntity`)
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `id` | `Long` (PK) | 카테고리 ID |
| `spaceId` | `String` (FK) | 소속 장부 공간 ID |
| `parentId` | `Long?` (FK) | `null`이면 상위 카테고리, 값이 있으면 해당 상위 카테고리의 **세부 카테고리** |
| `transactionType` | `TransactionType` | `EXPENSE` / `INCOME` |
| `name` | `String` | 카테고리명 (사용자 추가/수정 가능) |
| `emoji` | `String` | 카테고리 아이콘 이모지 (예: `🍔`) |
| `colorHex` | `String` | 색상 HEX 코드 |
| `sortOrder` | `Int` | 정렬 순서 |
| `isArchived` | `Boolean` | 삭제 시 기존 거래 보존을 위한 소프트 삭제 플래그 |
| `syncStatus` | `SyncStatus` | Pro 티어 동기화 상태 |

### 4.3. 등록 대기 큐 (`PendingTransactionEntity`)
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `id` | `Long` (PK) | 대기 큐 고유 ID |
| `sourceType` | `IngestionSource` | `APP_NOTIFICATION` (KB, 토스 등), `SMS`, `KB_QUICK_WEB_BETA`, `MANUAL` |
| `sourceAppName` | `String` | 발신 앱 표시 이름 (예: `KB Pay(국민카드)`, `문자(15881688)`) |
| `rawText` | `String` | 원본 알림/문자/빠른조회 텍스트 |
| `parsedAmount` | `Long` | 파싱된 금액 (양수 `Long`) |
| `parsedType` | `TransactionType` | `EXPENSE`(지출), `INCOME`(수입), `TRANSFER`(이체/카드대금 제외) |
| `parsedMerchant` | `String` | 사용처 또는 입출금 적요 |
| `occurredAt` | `Long` | 발생 시각 (Epoch Millis) |
| `queueKind` | `QueueKind` | `NORMAL` (일반), `CANCEL_MATCHED` (기존 거래 취소 매칭됨), `CANCEL_UNMATCHED` (미매칭 취소), `TRANSFER_PAIR` (계좌간 이체 추정) |
| `matchedTransactionId`| `Long?` | 취소 알림이 기존 확정 거래와 매칭된 경우 해당 `TransactionEntity.id` |
| `pairedQueueId` | `Long?` | 내 계좌 간 이체 쌍(`TRANSFER_PAIR`)으로 묶인 상대 대기 항목 ID (한쪽 승인 시 함께 승인) |
| `suggestedSpaceId` | `String` | 추천 공간 (`SHARED` 또는 `PERSONAL`) |
| `suggestedCategoryId`| `Long?` | 추천 상위 카테고리 ID |
| `suggestedSubCategoryId`| `Long?` | 추천 세부 카테고리 ID |
| `status` | `QueueStatus` | `PENDING` (대기중), `APPROVED` (저장완료), `DISMISSED` (삭제됨) |
| `dedupHash` | `String` (Unique) | 중복 수집 방지 해시 |

### 4.4. 확정 거래 내역 (`TransactionEntity`)
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `id` | `Long` (PK) | 로컬 거래 ID |
| `remoteId` | `String` (Unique) | Firebase 동기화용 UUID |
| `spaceId` | `String` (FK) | 소속 장부 공간 (`SHARED` / `PERSONAL`) |
| `type` | `TransactionType` | `EXPENSE` / `INCOME` / `TRANSFER` (`TRANSFER`는 예산/지출 합산에서 제외) |
| `amount` | `Long` | 확정 금액 (분할 승인 시 분할된 개별 금액) |
| `categoryId` | `Long` (FK) | 상위 카테고리 ID |
| `subCategoryId` | `Long?` (FK) | 세부 카테고리 ID |
| `merchantOrTitle` | `String` | 사용처 또는 제목 |
| `memo` | `String?` | 메모 |
| `isCanceled` | `Boolean` | 결제 취소 반영 여부 (`true`이면 예산/지출 합산에서 제외) |
| `originQueueId` | `Long?` | 원본 대기 큐 ID (1건의 큐를 공통+개인으로 **분할 등록** 시 동일 `originQueueId` 공유) |
| `authorName` | `String` | 기록/결제 유저 이름 |
| `transactionDate` | `Long` | 거래 일시 (Epoch Millis) |
| `updatedAt` | `Long` | 최종 수정 시각 (오프라인 충돌 해결 LWW 기준) |
| `syncStatus` | `SyncStatus` | `SYNCED`, `PENDING_UPLOAD`, `PENDING_DELETE` (`PENDING_DELETE`는 화면에서 숨기고 원격 삭제 후 로컬에서 제거) |

### 4.5. 집밥(끼니 직접 해결) 설정 및 기록 (`MealPriceConfig` & `MealSavingLogEntity`)
- **끼니별 단가 설정 (`DataStore`)**:
  - `breakfastUnitPrice: Long` (기본 `6000`)
  - `lunchUnitPrice: Long` (기본 `10000`)
  - `dinnerUnitPrice: Long` (기본 `15000`)
  - `snackUnitPrice: Long` (기본 `6000`, 화면 표기 `간식·홈카페`)
- **집밥 카운트 기록 (`MealSavingLogEntity`)**:
  | 필드명 | 타입 | 설명 |
  | :--- | :--- | :--- |
  | `id` | `Long` (PK) | 기록 ID |
  | `spaceId` | `String` (FK) | 적용 공간 (`PERSONAL` 또는 `SHARED`) |
  | `dateIso` | `String` | 기록 날짜 (`"YYYY-MM-DD"`) |
  | `mealType` | `MealType` | `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`, `CUSTOM`(금액 직접 입력) |
  | `count` | `Int` | 횟수 (기본 `1`) |
  | `unitAmount` | `Long` | 해당 끼니의 지정 단가 |
  | `savedAmount` | `Long` | 단순 `count * unitAmount` |
  | `memo` | `String` | 메모 (예: `"점심 도시락"`) |
  | `occurredAt` | `Long` | 기록 일시 (Epoch Millis — 정산 주기 범위 쿼리용) |

### 4.6. 자동화 규칙 (`IgnoredKeywordEntity` & `MerchantCategoryRuleEntity`)
| 테이블 | 주요 필드 | 설명 |
| :--- | :--- | :--- |
| `ignored_keywords` | `keyword`, `createdAt` | 원문에 이 단어가 포함된 알림은 대기 큐에 넣지 않음 |
| `merchant_category_rules` | `merchantKeyword`(Unique), `targetSpaceId`, `categoryId`, `subCategoryId` | 승인 시 학습되는 가맹점 → 공간/분류 자동 추천 규칙 |

---

## 5. AI 에이전트 작업 수칙 및 코딩 컨벤션 (Agent Coding Rules)

1. **언어 및 문서화**:
   - 모든 주석, 아티팩트(`task.md`, `implementation_plan.md`, `walkthrough.md`), 설명 및 UI 텍스트는 **한국어**로 작성합니다.
2. **스킬 및 기술 명세 준수**:
   - `.agent/skills/` 디렉터리에 스킬(`SKILL.md`)이 존재하는 경우 코드 작성 전 반드시 읽고 준수하며, 기술 스택은 [TECH_STACK.md](TECH_STACK.md)를 따릅니다.
3. **금액 및 정산 주기 계산 무결성**:
   - 모든 금액은 `Long` 타입을 사용합니다.
   - 이번 달 지출 합계(`spentAmount`) 및 남은 목표 금액(`remainingAmount`) 계산 시:
     - `TransactionType == EXPENSE` 이고 `isCanceled == false` 인 확정 거래만 합산합니다 (`TRANSFER` 및 미승인 `PENDING` 큐 항목은 합산 제외).
     - 조회 기간은 무조건 1일~말일로 하드코딩하지 않고, 해당 공간(`LedgerSpace`)에 설정된 **`billingStartDay` (기본 1일, 사용자 변경 가능)**를 기준으로 시작일시~종료일시를 계산하는 `BillingCycleCalculator`를 사용합니다.
4. **공유 범위 원칙**:
   - Firebase 공유 장부(`shared_spaces/{inviteCode}/transactions`)에는 **`공통 생활비`(`SPACE_ID_SHARED`) 내역만** 올라가야 합니다. `개인 용돈` 내역은 어떤 경우에도 업로드하지 않습니다.
   - 초대번호는 고정 기본값을 쓰지 않고 `RemoteSyncManager.generateInviteCode()`로 발급합니다.
5. **DB 스키마 변경 원칙**:
   - 엔티티(테이블·컬럼·인덱스)를 바꾸면 `AppDatabase.version`을 올리고 `core/database/Migrations.kt`에 `MIGRATION_n_(n+1)`을 추가합니다. 사용자 데이터를 지우는 `fallbackToDestructiveMigration`은 쓰지 않습니다.
   - 빌드 시 생성되는 `app/schemas/` JSON을 함께 커밋하고, `androidTest/.../MigrationTest.kt`에 이전 버전 데이터 보존 검증을 추가합니다.
6. **대기 큐 불변 원칙**:
   - 푸시 알림, SMS, `[Beta]` KB 무로그인 빠른조회 등 모든 자동 수집 데이터는 **반드시 `PendingTransactionDao`로만 들어가야 하며**, 사용자의 승인 액션 없이 `TransactionDao`에 직접 저장해서는 안 됩니다.
   - 코드상 진입점은 `EnqueueParsedDraftUseCase`(→ `PendingQueueRepository`) 하나이며, 확정 거래는 승인 UseCase(`ApprovePendingItemUseCase` 등)와 직접 입력(`AddManualTransactionUseCase`)에서만 만들어집니다.
6. **계층 규칙**:
   - 새 비즈니스 규칙은 `domain/usecase`에 작성하고 `domain/repository` 인터페이스에만 의존합니다 (DAO·DataStore·Firebase·`Context` 직접 사용 금지). 구현체는 `data/`에 두고 `di/AppModule.kt`에서 연결합니다.
   - `MainViewModel`·Worker·Receiver·Widget은 UseCase만 호출합니다. 순수 로직 UseCase는 `app/src/test/.../domain/usecase/`에 메모리 저장소(`FakeRepositories.kt`)로 단위 테스트를 추가합니다.
   - 새 화면은 `ui/navigation/HouseholdDestinations.kt`에 `@Serializable` 라우트로 추가하고 `HouseholdNavHost`에 등록합니다.

---

## 6. 단계별 구현 로드맵 (Implementation Roadmap)

- **Phase 1: 프로젝트 기반 설정 및 로컬 DB ([TECH_STACK.md](TECH_STACK.md) 적용)**
  - `minSdk = 26`, `JavaVersion.VERSION_17` 변경 및 Koin, Room(KSP), DataStore, Navigation Compose, Serialization 설정.
  - 핵심 엔티티(`LedgerSpace`, `Category`, `PendingTransaction`, `Transaction`, `MealSavingLog`, `IgnoredKeyword`, `MerchantCategoryRule`) 및 DAO 구현.
  - `BillingCycleCalculator` (월 기준일 1일~28일/말일 주기 계산기) 및 초기 시드 데이터(기본 공통 생활비/개인 용돈 공간, 기본 상위/세부 카테고리) 구성.
- **Phase 2: 공간 분리 대시보드, 전체 목표 예산 & 카테고리 관리 (Space, Budget & Category UI)**
  - 상단 `[공통 생활비 | 개인 용돈]` 공간 전환 스위처 및 월 기준일 선택 설정 구현.
  - 공간별 **전체 목표 금액** 설정 및 `현재 사용 금액 / 남은 금액 / 소진율` 대시보드 카드 구현.
  - 상위 카테고리 및 세부 카테고리 추가·수정·삭제(CRUD) 화면 구현.
- **Phase 3: 등록 대기 큐 워크플로우 고도화 (Pending Queue & Split/Cancel/Transfer)**
  - 대기 큐 리스트 및 검수 UI 구현: 단건 수정/저장, 삭제, 일괄 승인.
  - **분할 등록(Split Transaction)** 모달 구현 (1건의 대기 항목을 공통 생활비 + 개인 용돈 등으로 쪼개어 승인).
  - **결제 취소 매칭 UI** 및 **계좌 간 이체/카드대금(`TRANSFER`) 자동 분류·제외** 처리 구현.
- **Phase 4: 자동 수집 엔진 (1순위 알림/SMS + 2순위 Beta KB 무로그인 빠른조회)**
  - `FinancialNotificationListenerService` (KB, 토스 등) 및 `SmsBroadcastReceiver` + 정규식 파서 구현.
  - 개발 및 검증 편의를 위한 **알림/문자 시뮬레이터(테스트 데이터 주입기)** 제공.
  - **`[Beta]` KB국민은행 무로그인 빠른조회 스크래퍼 (`KbQuickInquiryScraper`)** 및 암호화 자격증명 설정 화면 구현.
- **Phase 5: 집밥 절약 카운터 & Free/Pro 멀티유저 동기화 (Meal Saver & Tier Sync)**
  - 끼니별(아침/점심/저녁/간식) 단일 금액 설정 및 원터치 `+1` 카운트(`횟수 × 금액`) 화면·위젯 구현.
  - `FREE` / `PRO` 티어 분기 처리 및 `PRO` 티어용 Firebase Offline-First 동기화(`SyncManager`) 연동.

---

## 7. 현재 구현 현황 (2026-10-18 기준)

| 로드맵 | 상태 | 비고 |
| :--- | :--- | :--- |
| Phase 1: 기반 설정 및 로컬 DB | ✅ 완료 | Room v2 + 단계별 마이그레이션(`Migrations.kt`, 스키마 JSON 관리). 첫 실행은 공간·카테고리만 만들고 튜토리얼을 보여줌. 예시 데이터는 튜토리얼 마지막 장 또는 설정 > `예시 데이터 넣기` |
| Phase 2: 공간·예산·카테고리 UI | ✅ 완료 | 기준일 `1~28일` + `매월 말일` |
| Phase 3: 대기 큐 (분할/취소/이체) | ✅ 완료 | 미매칭 취소 건은 원본 결제 직접 선택(전체/부분 취소) |
| Phase 4: 자동 수집 엔진 | ✅ 완료 | 알림·SMS·KB 빠른조회(Beta)·예시 알림·KB 주기 자동 조회(`QuickInquiryWorker`, 3/6/12/24시간) |
| Phase 5: 집밥 카운터 & Pro 동기화 | ✅ 완료 | 홈 화면 위젯(`MealSaverWidgetProvider`), 오프라인 업로드(`SyncWorker`) + Firestore 실시간 리스너 |
| 아키텍처 정리 | ✅ 완료 | `domain/repository` 인터페이스 + `domain/usecase` 분리(`HouseholdRepository` 제거), Koin 모듈 4개 분리, Type-Safe Navigation Compose 적용 |

