# 노을별 (sunset-star) 작업 안내

노을과 별 관측 점수를 알려주고 더 잘 보이는 근처 장소를 추천하는 1인 스터디 프로젝트.

## 구조
- `backend/`: Spring Boot 4.1, Java 21, Gradle, MySQL, 기본 패키지 `com.sunsetstar`
- `frontend/`: React + Vite + **TypeScript** + **Tailwind CSS**. `/api` 요청은 Vite 프록시로 8080에 전달

## 규칙
- 비밀값(DB 비밀번호, API 키)은 `backend/src/main/resources/application-secret.yml`에만 둔다. 이 파일은 커밋하지 않는다.
- Open-Meteo 호출 주소와 모델명은 `application.yml`의 `open-meteo.*` 설정값을 쓰고 코드에 직접 쓰지 않는다. 기상청 모델(kma_seamless)은 현재 데이터가 null이라 기본값 best_match를 쓴다.
- 점수 기준 숫자(가중치, 구간)는 상수나 설정으로 모아두고, 점수 계산 로직에는 JUnit 단위 테스트를 함께 작성한다.
- 에러 응답은 `{code, message}` 형식으로 통일한다.
- 패키지는 기능별로 나눈다: `forecast`, `location`, `auth`, `observation`, `notification`, 공통은 `global`.
- 한 번에 기능 하나씩 작업하고, 기능 단위로 커밋한다.

## Git 규칙
- 레포는 하나(`suminn01/sunset-star`)이고 `backend/`, `frontend/` 폴더로 나눈다.
- `main`은 항상 실행되는 상태로 둔다. `main`에 바로 커밋하지 않고, 작업마다 브랜치를 만든다.
- 브랜치 이름: `be/` 또는 `fe/` + 영어 소문자와 `-`. 예: `be/open-meteo-client`, `be/sunset-score`, `fe/main-screen`
- 커밋 메시지: `말머리(be|fe): 한국어 설명`. 예: `feat(be): 노을 점수 계산 서비스 추가`
  - feat 새 기능 / fix 버그 수정 / test 테스트 / refactor 동작은 그대로 코드 정리 / docs 문서 / chore 설정·의존성
  - 양쪽 다 바꾸거나 공통 파일이면 괄호 없이 쓴다. 예: `docs: CLAUDE.md Git 규칙 추가`
- 테스트가 통과하는 작은 단위로 자주 커밋한다.
- 작업이 끝나면 GitHub에서 PR을 열어 `main`에 합친다. PR 설명에 한 일, 확인 방법, 관련 이슈(`closes #번호`)를 적는다.

## 프론트 규칙
- 모든 코드는 TypeScript(`.ts`, `.tsx`)로 쓰고 `strict` 모드를 켠다. `any`는 쓰지 않는다.
- 서버 응답은 타입(`types/`)으로 정의하고, API 호출은 `api/` 폴더에 모은다.
- 스타일은 Tailwind CSS 유틸리티 클래스로 쓴다. 따로 CSS 파일을 만드는 것은 전역 설정(폰트, Tailwind 불러오기)만 허용한다.
- 디자인 값은 Tailwind 테마에 등록해서 이름으로 쓴다.
  - 노을 그라데이션 #FFC27A → #F79A8E → #C6A8E8, 글자 #4A1F3A
  - 밤 그라데이션 #1A1D45 → #2E2A62 → #4E3F86, 글자 #EDE9FF
  - 배경 #F7F4F8, 강조 보라 #6B3F86
  - 글꼴: 제목·숫자 Jua, 본문 Gowun Dodum
- 화면은 모바일(390px 기준) 우선으로 만든다. 화면 디자인은 Figma "노을별 화면 디자인"을 따른다.

## 문서 (기준: 2026-10-26)
- 기능명세서·화면 명세 **Ver.1.5** (가장 최신, 기준 문서): https://claude.ai/code/artifact/7a1c60ea-2a14-4cc7-8b5f-1e005cbc3d0c
- 엑셀 기능정의서 Ver1.5 / 화면정의서 Ver1.2 (기능 목록 확인용)
- 화면 디자인: Figma "노을별 화면 디자인" (10개 화면, 프로토타입 연결)
- 문서끼리 내용이 다르면 기능명세서를 따른다.

## 점수 계산 (기능명세서 F2·F4가 기준)
- 노을(F2): 50 + 위쪽 구름 + 낮은 구름 + 서쪽 하늘(F3) + 공기 + 습도·시정 + 비, 0~100으로 자름. 낮은 구름 80% 이상이면 위쪽 구름 점수 0.
- 별(F4): 밤(천문박명 끝 ~ 04시) 1시간 단위로 (구름×0.5 + 공기×0.2 + 달×0.3) × 빛 공해 계수(도심 0.75 / 교외 0.9 / 시골 1.0). 구름 71~90%면 최대 30점, 91% 이상·비면 0점. 대표 점수 = 시간별 최고값.
- 등급 5단계: 85~ 꼭 보세요 / 70~ 볼 만해요 / 50~ 그럭저럭 / 30~ 아쉬워요 / ~29 쉬어 가요.
- 기능명세서 F2·F4의 예시 계산표를 그대로 JUnit 테스트 케이스로 쓴다.
- 점수는 1시간 캐시. 관측 기록에는 기록하는 순간 화면에 보이던 예측 점수를 저장한다.

## 로컬 실행
- MySQL은 9.6(서비스 이름 MySQL96, 포트 3306)을 쓴다. DB `sunset_star`는 접속 시 자동 생성된다.
- 백엔드: `backend`에서 `gradlew.bat bootRun` → http://localhost:8080/api/health
- 프런트: `frontend`에서 `npm run dev` → http://localhost:5173
