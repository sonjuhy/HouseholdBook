# HouseholdBook

**함께 쓰는 생활비와 내 용돈을 한 앱에서 나눠 관리하는 안드로이드 가계부**

결제 알림과 문자를 읽어 지출을 자동으로 모으고, 사용자가 확인한 내역만 장부에 저장합니다. 부부·커플·가족이 공통 생활비 장부를 함께 쓸 수 있고, 인터넷이 없어도 모든 기능이 동작합니다.

- 패키지: `com.budget.householdbook`
- 버전: `0.0.1`
- 지원: Android 8.0 (API 26) 이상

---

## 주요 기능

### 지출 자동 수집
- **결제 알림·문자 읽기**: 은행·카드·간편결제 앱 알림과 카드 승인·입출금 문자를 읽어 금액·가게 이름·지출/수입을 알아냅니다.
  - 시중·인터넷·지방은행, 상호금융, 카드사 등 29곳을 지원합니다.
  - 금융사별로 켜고 끌 수 있고, 같은 결제가 여러 앱에서 오면 하나만 남깁니다.
- **[Beta] 계좌 빠른조회**: KB국민은행 무로그인 빠른조회로 최근 입출금 내역을 불러옵니다. 조회 정보는 Android Keystore로 암호화해 저장합니다.

### 대기내역에서 확인 후 저장
자동으로 모은 내역은 바로 장부에 들어가지 않고 **대기내역**에서 확인한 뒤 저장합니다.
- **결제 취소 연결**: 취소·환불 알림이 오면 원래 결제를 찾아 연결합니다.
- **금액 나누기**: 한 결제를 공통 생활비와 내 용돈으로 나눠 저장합니다.
- **이체 거르기**: 카드대금, 내 계좌 간 이체처럼 실제 지출이 아닌 내역은 예산 계산에서 뺍니다.
- **다시 안 받기**: 필요 없는 알림은 키워드나 보낸 앱 단위로 다음부터 받지 않을 수 있습니다.

### 장부와 예산
- **공통 생활비 / 내 용돈** 두 장부를 따로 관리합니다. 분류(대분류·세부 분류)도 장부마다 따로 둡니다.
- **장부별 목표 예산**: 쓴 금액, 남은 금액, 하루에 써도 되는 금액을 보여줍니다.
- **월 기준일**: 1일~28일 또는 매월 말일 중에서 고를 수 있습니다. 예를 들어 월급날에 맞출 수 있습니다.
- **휴대폰 알림**:
  - 새 결제가 들어오면 알림의 버튼으로 앱을 열지 않고 바로 저장할 수 있습니다.
  - 예산의 80%, 100%를 쓰면 알려줍니다.
  - 자동 기록이 멈추면 알려줍니다.

### 함께 쓰기 (구글 로그인)
- **초대**: 상대의 구글 이메일로 초대하면, 상대가 그 계정으로 로그인하는 순간 같은 공통 생활비 장부에 연결됩니다.
- **실시간 동기화**: 공통 생활비 거래와 공용 분류가 실시간으로 맞춰집니다. 개인 용돈은 내 휴대폰에만 남습니다.
- **멤버 관리**: 초대 취소, 내보내기, 나가기, 방장 넘기기, 회원 탈퇴를 지원합니다.

### 집밥 절약 카운터
- 직접 해먹은 끼니를 한 번 누르면 기록되고, `횟수 × 끼니 단가`로 아낀 금액을 보여줍니다.
- 홈 화면 위젯에서도 바로 기록할 수 있습니다.

---

## 기술 스택

| 영역 | 사용 기술 |
| :--- | :--- |
| 언어·빌드 | Kotlin 2.2, Gradle 9.5 / AGP 9.3, Java 17 대상 |
| UI | Jetpack Compose, Material 3 (iOS 스타일 디자인 시스템), Navigation Compose, 홈 화면 위젯(AppWidget) |
| 구조 | Clean Architecture (UI → UseCase → Repository) + 단방향 데이터 흐름 |
| 의존성 주입 | Koin |
| 로컬 저장 | Room (기기 안의 원본 데이터, 스키마 마이그레이션 테스트 포함), DataStore, Android Keystore |
| 백그라운드 | WorkManager (동기화, 빠른조회, 수집 상태 확인) |
| 함께 쓰기 | Firebase Authentication (Google 로그인, Credential Manager), Cloud Firestore + 보안 규칙 |
| 빠른조회 | OkHttp, Jsoup |
| 테스트 | JUnit, Coroutines Test, Compose UI Test, Room MigrationTestHelper, Firebase 에뮬레이터 규칙 테스트 |

모든 데이터는 먼저 휴대폰의 Room DB에 저장됩니다. 함께 쓰는 장부는 인터넷이 연결되면 Firestore로 올리고 받습니다.

---

## 시작하기

### 필요한 것
- Android Studio (최신 버전)
- JDK: Gradle 데몬은 JDK 25 툴체인을 사용합니다 (`gradle/gradle-daemon-jvm.properties`). Android Studio 내장 JDK로도 빌드할 수 있습니다.
- (규칙 테스트를 돌릴 때) Node.js 22, Java 11 이상

### 빌드
```bash
git clone https://github.com/sonjuhy/HouseholdBook.git
```

```bash
./gradlew assembleDebug
```

`app/google-services.json`이 없으면 **로컬 전용 모드**로 빌드됩니다. 이 모드에서는 함께 쓰기만 빼고 모든 기능을 쓸 수 있습니다.

### 함께 쓰기(Firebase) 설정
Firebase 설정 파일은 저장소에 포함되어 있지 않습니다. 직접 Firebase 프로젝트를 만들어 연결하세요.

1. [Firebase 콘솔](https://console.firebase.google.com/)에서 프로젝트를 만들고 Android 앱(`com.budget.householdbook`)을 추가합니다.
2. 앱 설정의 **SHA 인증서 지문**에 빌드하는 PC의 디버그 키 SHA-1을 등록합니다. 출시 키로 서명한다면 그 키의 SHA-1도 등록합니다.
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -storepass android
   ```
3. **Authentication → 로그인 방법**에서 **Google**을 사용 설정합니다.
4. **Firestore Database**를 만듭니다.
5. 그다음 `google-services.json`을 내려받아 `app/` 폴더에 넣습니다. Google 로그인을 켠 뒤에 받아야 웹 클라이언트 ID가 들어 있습니다.
6. 보안 규칙을 배포합니다.
   ```bash
   firebase deploy --only firestore:rules --project <your-project-id>
   ```

### 출시 빌드
`keystore.properties.example`을 `keystore.properties`로 복사하고 서명 키 정보를 채운 뒤 빌드합니다. 이 파일이 없으면 출시 빌드도 디버그 키로 서명됩니다. 이렇게 만든 빌드는 확인용이라 Play 스토어에 올릴 수 없습니다.

```bash
./gradlew assembleRelease
```

출시 빌드는 R8로 코드를 줄이고 난독화합니다.

---

## 테스트

| 종류 | 실행 |
| :--- | :--- |
| 단위 테스트 (도메인·UseCase) | `./gradlew testDebugUnitTest` |
| 계측 테스트 (DB 마이그레이션, Compose UI) | `./gradlew connectedDebugAndroidTest` (기기 또는 에뮬레이터 필요) |
| Firestore 보안 규칙 | `cd firestore-tests && npm install && npm test` |
| 실제 계정이 필요한 흐름 | [MANUAL_TEST_CHECKLIST.md](MANUAL_TEST_CHECKLIST.md)를 보고 직접 확인 |

`connectedDebugAndroidTest`는 테스트가 끝나면 앱을 삭제합니다. 기기에 남겨둘 데이터가 있으면 주의하세요.

GitHub Actions(`.github/workflows/ci.yml`)는 푸시할 때마다 아래를 확인합니다.
- 디버그 빌드와 단위 테스트
- 계측 테스트 APK 빌드
- R8 출시 빌드
- DB 스키마 파일 누락
- 보안 규칙 테스트

---

## 프로젝트 구조

```
app/src/main/java/com/budget/householdbook/
├── core/        # DB(Room)·DataStore·보안·디자인 시스템
├── data/        # Repository 구현, 알림·문자 수집, 빠른조회, Firestore 동기화
├── domain/      # 모델, Repository 인터페이스, UseCase
├── di/          # Koin 모듈
└── ui/          # 화면 (홈, 대기내역, 장부, 집밥, 설정, 위젯, 튜토리얼)
app/schemas/     # Room 스키마 (마이그레이션 테스트용)
firestore.rules  # Firestore 보안 규칙
firestore-tests/ # 보안 규칙 테스트 (Firebase 에뮬레이터)
```

---

## 문서

| 문서 | 내용 |
| :--- | :--- |
| [GUIDE.md](GUIDE.md) | 기능 요구사항, 업무 규칙, 데이터 구조 |
| [TECH_STACK.md](TECH_STACK.md) | 빌드 환경, 라이브러리, 아키텍처, 동기화 방식 |
| [DESIGN_GUIDE.md](DESIGN_GUIDE.md) | 화면 디자인 규칙 (iOS 스타일, 다크 모드) |
| [MANUAL_TEST_CHECKLIST.md](MANUAL_TEST_CHECKLIST.md) | 실제 기기·계정으로 확인할 항목 |
| [history.md](history.md) | 단계별 개발 기록 |

---

## 라이선스

[Apache License 2.0](LICENSE)
