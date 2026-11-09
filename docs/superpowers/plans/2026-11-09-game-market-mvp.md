# Game Market MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Steam 스타일 가상 게임 스토어(검색·장바구니·모의 구매)에 Steam 리뷰 AI 요약(Gemini)과 브라우저 TTS를 붙이고, 요약 생성 방식 3단계(none/lazy/warm)의 응답 시간을 측정해 표로 남긴다.

**Architecture:** Java 21 + Spring Boot 모듈형 모놀리스(패키지 `com.gamemarket.{auth,game,cart,order,review,summary,bench}`). PostgreSQL + JPA. 요약은 `ReviewSource`(Steam → Seed 폴백)로 리뷰를 얻고 Spring AI(Gemini)로 JSON 요약을 만들어 DB에 저장한다. React(Vite, TS)는 UI와 `speechSynthesis`만 담당한다.

**Tech Stack:** Java 21, Spring Boot, Spring Security(BCrypt, HTTP 세션), Spring Data JPA, PostgreSQL, Spring AI `spring-ai-starter-model-google-genai`, JUnit 5, Testcontainers, React + TypeScript + Vite, Docker Compose.

**Spec:** `MVP.md`

## Global Constraints

- Spring Boot와 Spring AI(`spring-ai-bom`)는 Task 1에서 공식 문서로 호환 버전을 확인해 `build.gradle`에 **정확한 버전으로 고정**한다.
- 실결제·게임 키·Steam 계정 연동 없음. 주문은 내역만 생성한다.
- 유료 API 자동 전환 금지. Gemini 키는 환경변수 `GEMINI_API_KEY`로만 주입하고 저장소에 커밋하지 않는다.
- 모든 서버 로직은 Spring Boot, React는 UI와 TTS만 담당한다.
- 요약 모드는 설정 `summary.mode=none|lazy|warm`.
- 요약은 한국어 `pros`(장점) / `cons`(단점) / `verdict`(총평) 구조 JSON.
- 시드 게임 20~30개, 리뷰는 가짜 데이터 허용.
- 헤더 위장 등 봇 차단 우회 금지. Steam이 JSON을 안 주면 시드로 폴백.

## Review Focus

- 중복 가입(같은 아이디) → 409, 비밀번호 평문 저장 없음.
- 이미 구매한 게임을 장바구니에 담거나 다시 구매 → 거부(409).
- 빈 장바구니 주문 → 400, 주문 레코드 생성 안 됨.
- 같은 게임 요약 동시 요청 N개 → Gemini 호출 1회, `review_summary` 행 1개.
- Gemini가 잘못된 JSON·빈 응답·429 반환 → 서비스 유지, 재시도 후 오류 응답(요약 행 저장 안 됨).
- Steam 리뷰 0건 또는 비 JSON 응답 → 시드 리뷰로 폴백.
- 브라우저에 한국어 음성이 없거나 `speechSynthesis` 미지원 → 버튼 비활성 + 안내 문구.

---

### Task 1: 프로젝트 골격

**Files:**
- Create: `backend/build.gradle`, `backend/settings.gradle`, `backend/src/main/java/com/gamemarket/GameMarketApplication.java`, `backend/src/main/resources/application.yml`, `docker-compose.yml`, `.gitignore`
- Test: `backend/src/test/java/com/gamemarket/ContextLoadsTest.java`

**Interfaces:** Produces: 실행 가능한 Boot 앱, Testcontainers PostgreSQL 기반 테스트 설정(`@Testcontainers` 공용 베이스 `IntegrationTestBase`).

- [ ] **Step 1:** `ContextLoadsTest.contextLoads()`를 작성한다(`IntegrationTestBase` 상속, 컨텍스트 기동만 검증).
- [ ] **Step 2:** `./gradlew test` 실행 → 빌드 파일 없어 FAIL 확인.
- [ ] **Step 3:** Gradle 프로젝트 생성. 의존성: web, security, data-jpa, validation, postgresql, spring-ai-bom + google-genai 스타터, testcontainers. 버전은 공식 문서에서 확인해 고정한다. `docker-compose.yml`에 postgres 서비스. `.gitignore`에 `.idea/`, `build/`, `node_modules/`, `.env` 포함. 스키마는 `spring.jpa.hibernate.ddl-auto=update`(개인 프로젝트, Flyway 생략).
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** Commit `chore: scaffold spring boot backend`.

### Task 2: 게임 카탈로그 + 시드

**Files:**
- Create: `game/Game.java`, `game/GameRepository.java`, `game/GameController.java`, `game/SeedLoader.java`, `resources/seed/games.json`
- Test: `game/GameControllerTest.java`

**Interfaces:** Produces: `Game(Long id, long steamAppId, String name, String genre, int priceKrw, String imageUrl, String description)`; `GET /api/games?q=&genre=` → `List<GameDto>`; `GET /api/games/{id}` → `GameDto`, 없으면 404.

- [ ] **Step 1:** 테스트 작성: `searchByName_isCaseInsensitive`, `filterByGenre`, `unknownId_returns404`, `seed_loadsBetween20And30Games`.
- [ ] **Step 2:** 실행 → FAIL.
- [ ] **Step 3:** 엔티티·리포지토리·컨트롤러 구현. `SeedLoader`는 시작 시 `games.json`을 읽고 `steamAppId` 기준으로 없는 행만 삽입(멱등). `games.json`은 `appdetails`(`l=koreana&cc=kr`)로 받은 핵심 필드(이름·장르·이미지·설명)만 정적 파일로 저장, 가격은 모의값.
- [ ] **Step 4:** 실행 → PASS.
- [ ] **Step 5:** Commit `feat: game catalog with seed data`.

### Task 3: 인증

**Files:**
- Create: `auth/User.java`, `auth/UserRepository.java`, `auth/AuthController.java`, `auth/SecurityConfig.java`
- Test: `auth/AuthControllerTest.java`

**Interfaces:** Produces: `POST /api/auth/signup {username,password}` 201/409/400, `POST /api/auth/login` 200/401, `POST /api/auth/logout`, `GET /api/auth/me`. 세션 인증. 비로그인은 `/api/cart/**`, `/api/orders/**`, `/api/library`, `/api/games/*/summary` 접근 시 401. 나머지 `/api/games/**`는 공개.

- [ ] **Step 1:** 테스트: `signup_duplicateUsername_returns409`, `signup_blankPassword_returns400`, `login_wrongPassword_returns401`, `password_isStoredAsBcryptHash`, `cart_withoutLogin_returns401`.
- [ ] **Step 2:** 실행 → FAIL.
- [ ] **Step 3:** BCrypt `PasswordEncoder`, `username` unique 제약, 세션 기반 `SecurityFilterChain`(CSRF는 SPA 프록시 사용 가정으로 비활성, 코드에 한 줄 주석). 
- [ ] **Step 4:** 실행 → PASS.
- [ ] **Step 5:** Commit `feat: session auth with bcrypt`.

### Task 4: 장바구니

**Files:** Create `cart/CartItem.java`, `cart/CartRepository.java`, `cart/CartController.java`; Test `cart/CartControllerTest.java`.

**Interfaces:** Consumes: 로그인 사용자, `Game`. Produces: `POST /api/cart/{gameId}` (이미 담김 또는 이미 소유 → 409), `DELETE /api/cart/{gameId}`, `GET /api/cart` → `{items:[GameDto], totalKrw:int}`.

- [ ] **Step 1:** 테스트: `add_twice_returns409`, `remove_updatesTotal`, `total_sumsPrices`, `add_alreadyOwned_returns409`(Task 5 이후 활성화).
- [ ] **Step 2:** 실행 → FAIL. **Step 3:** 구현(`(user_id, game_id)` unique). **Step 4:** PASS. **Step 5:** Commit `feat: cart`.

### Task 5: 모의 주문 + 라이브러리

**Files:** Create `order/Order.java`, `order/OrderItem.java`, `order/OrderService.java`, `order/OrderController.java`, `order/LibraryController.java`; Test `order/OrderServiceTest.java`.

**Interfaces:** Produces: `POST /api/orders` → `OrderDto`(빈 장바구니 400), `GET /api/library` → `List<GameDto>`. `OrderService.checkout(Long userId)`는 `@Transactional`: 주문+항목 저장, 장바구니 비움.

- [ ] **Step 1:** 테스트: `checkout_emptyCart_returns400_andCreatesNoOrder`, `checkout_movesGamesToLibrary_andClearsCart`, `checkout_alreadyOwned_returns409`, `checkout_failureMidway_rollsBack`.
- [ ] **Step 2:** FAIL. **Step 3:** 구현, `(user_id, game_id)` 소유 unique 제약. **Step 4:** PASS. **Step 5:** Commit `feat: mock checkout and library`.

### Task 6: 리뷰 소스 (Steam + 시드 폴백)

**Files:** Create `review/ReviewSource.java`, `review/SteamReviewSource.java`, `review/SeedReviewSource.java`, `review/FallbackReviewSource.java`, `resources/seed/reviews.json`; Test `review/FallbackReviewSourceTest.java`.

**Interfaces:** Produces: `interface ReviewSource { List<String> fetch(long steamAppId); }`. `FallbackReviewSource`가 주 빈이며 Steam 결과가 예외·비 JSON·0건이면 `SeedReviewSource`를 반환한다.

- [ ] **Step 1:** 테스트(Steam 소스를 스텁): `steamThrows_usesSeed`, `steamReturnsEmpty_usesSeed`, `steamReturnsReviews_usesSteam`, `seed_hasReviewsForEverySeedGame`.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** `SteamReviewSource`는 `RestClient`로 `https://store.steampowered.com/appreviews/{appid}?json=1&language=english&num_per_page=20&filter=recent`를 호출한다. 응답이 `application/json`이 아니거나 `success!=1`이면 예외. **추가 스파이크:** 구현 후 실제 호출 1회로 JSON 여부를 기록하고, 안 되면 README에 "시드 사용"이라고 적는다. 헤더 위장은 하지 않는다.
- [ ] **Step 4:** PASS. **Step 5:** Commit `feat: review source with seed fallback`.

### Task 7: AI 요약 서비스 (3모드)

**Files:** Create `summary/ReviewSummary.java`, `summary/SummaryRepository.java`, `summary/GeminiSummarizer.java`, `summary/SummaryService.java`, `summary/SummaryController.java`, `summary/SummaryLog.java`, `summary/SummaryWarmup.java`; Test `summary/SummaryServiceTest.java`.

**Interfaces:**
- Consumes: `ReviewSource.fetch`.
- Produces: `record SummaryResult(List<String> pros, List<String> cons, String verdict)`; `interface Summarizer { SummaryResult summarize(String gameName, List<String> reviews); }`; `SummaryService.get(Long gameId) -> SummaryDto(pros, cons, verdict, createdAt)`; `GET /api/games/{id}/summary`. `ReviewSummary`는 `game_id` unique. 각 호출은 `summary_log(game_id, mode, fetch_ms, llm_ms, total_ms, created_at)`에 기록.

- [ ] **Step 1:** 테스트(Summarizer·ReviewSource 스텁): `none_callsSummarizerEveryTime`, `lazy_secondCall_doesNotCallSummarizerOrReviewSource`, `warm_precomputesAllGamesOnStartup`, `everyCall_writesSummaryLog`.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** `GeminiSummarizer`는 Spring AI `ChatClient`로 JSON 출력(`response-mime-type=application/json`)을 요청하고 `SummaryResult`로 파싱한다. 모드별 분기는 `SummaryService`에서만 처리. `none`은 DB에 저장하지 않고, `lazy`·`warm`은 저장한다. `SummaryWarmup`은 `summary.mode=warm`일 때만 시작 시 실행.
- [ ] **Step 4:** PASS. **Step 5:** Commit `feat: ai review summary with none/lazy/warm modes`.

### Task 8: 동시성·오류 처리

**Files:** Modify `summary/SummaryService.java`, `summary/GeminiSummarizer.java`; Create `summary/SummaryExceptionHandler.java`; Test `summary/SummaryConcurrencyTest.java`, `summary/SummaryFailureTest.java`.

**Interfaces:** Produces: 요약 실패 시 HTTP 503 `{code:"SUMMARY_UNAVAILABLE", message}`. 생성 중 동일 게임 요청은 같은 결과를 공유.

- [ ] **Step 1:** 테스트: `concurrent20Requests_callSummarizerOnce_andStoreOneRow`, `malformedJson_returns503_andStoresNothing`, `rateLimit429_retriesThenReturns503`, `emptyReviewList_returns503OrUsesSeed`.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** 게임별 락(`ConcurrentHashMap<Long, CompletableFuture<SummaryDto>>` single-flight) + DB unique 제약을 최종 방어선으로 사용. 429는 지수 백오프로 최대 3회 재시도. 파싱 실패는 재시도 없이 실패 처리.
- [ ] **Step 4:** PASS. **Step 5:** Commit `feat: single-flight and failure handling for summaries`.

### Task 9: 성능 측정

**Files:** Create `bench/BenchRunner.java`(Profile `bench`), `docs/benchmark.md`; Test `bench/BenchStatsTest.java`.

**Interfaces:** Produces: `BenchStats.of(List<Long> millis) -> (avg, p95)`; 실행 결과를 `docs/benchmark.md` 표(모드 × avg/p95/최초·이후)로 출력.

- [ ] **Step 1:** 테스트: `p95_of100Values`, `avg_ofKnownValues`.
- [ ] **Step 2:** FAIL. **Step 3:** 구현. `BenchRunner`는 시드 게임 전체에 대해 모드별(`none`, `lazy` 첫/이후, `warm`)로 게임당 3회 이상 `SummaryService.get`을 호출하고 `summary_log`를 집계한다. **Step 4:** PASS.
- [ ] **Step 5:** 실제 Gemini 키로 한 번 실행해 `docs/benchmark.md`를 채운다. **Step 6:** Commit `feat: summary benchmark and results`.

### Task 10: 프론트 스토어 화면

**Files:** Create `frontend/` (Vite React TS), `src/api.ts`, `src/pages/{Home,GameDetail,Login,Signup,Cart,Library}.tsx`, `vite.config.ts`(`/api` 프록시); Test `src/pages/Home.test.tsx`(Vitest).

- [ ] **Step 1:** 테스트: `Home renders cards from API`, `Home search filters by name`. **Step 2:** FAIL.
- [ ] **Step 3:** 홈(카드·검색·장르), 상세(장바구니 담기), 로그인/가입, 장바구니(합계·모의 구매), 라이브러리. `api.ts`가 모든 fetch를 감싸고 401 시 로그인으로 이동. **Step 4:** PASS. **Step 5:** Commit `feat: store frontend`.

### Task 11: AI 요약 패널 + TTS

**Files:** Create `src/components/SummaryPanel.tsx`, `src/hooks/useTts.ts`; Test `src/components/SummaryPanel.test.tsx`.

**Interfaces:** Produces: `useTts(text: string) -> {supported: boolean, hasKoVoice: boolean, speaking: boolean, play(): void, stop(): void}`.

- [ ] **Step 1:** 테스트: `shows loading then pros/cons/verdict`, `shows error message on 503`, `play button disabled when speechSynthesis missing`, `stop cancels speech`.
- [ ] **Step 2:** FAIL. **Step 3:** 상세 페이지에 요약 버튼 → 로딩 → 장점·단점·총평·생성 시각 표시, 재생/중지. 한국어(`ko-KR`) 음성이 없으면 안내 문구 표시. **Step 4:** PASS. **Step 5:** Commit `feat: summary panel with tts`.

### Task 12: 통합 실행 + README

**Files:** Modify `docker-compose.yml`(backend·frontend 포함), Create `README.md`.

- [ ] **Step 1:** `docker compose up`으로 DB·백엔드·프론트가 기동하는지 확인.
- [ ] **Step 2:** 수동 시나리오 통과 확인: 가입 → 검색 → 장바구니 → 모의 구매 → 라이브러리 → 요약 보기·음성 재생 → 두 번째 요청 시 `summary_log`에 외부 호출 없음.
- [ ] **Step 3:** README에 실행법, 모드 설정, `docs/benchmark.md` 표 링크, Steam 엔드포인트 상태, `MVP.md` 완료 기준 체크 반영. **Step 4:** Commit `docs: readme and integration`.

---

## Self-Review 메모

- 스펙 커버: 회원/검색/장바구니/모의구매/라이브러리(T2–5), Steam 수집+폴백(T6), 요약·캐시·3모드(T7), 동시성·오류(T8), 속도 표(T9), TTS(T11), 버전 고정(T1/Global).
- 의도적으로 제외: 관리자, 결제, 추천, 자동 갱신, Flyway.
- 남은 리스크: Steam `appreviews`가 JSON을 주는지는 T6에서 실측. Gemini 무료 모델명은 T7 시작 시 공식 문서로 확인.
