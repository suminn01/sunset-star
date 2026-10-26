# 노을별 (sunset-star)

날씨 데이터를 분석해 노을과 별 관측 점수를 알려주고, 더 잘 보이는 근처 장소를 추천하는 웹앱입니다.

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 프론트엔드 | React, Vite |
| 백엔드 | Spring Boot 4.1, Java 21, Gradle |
| 데이터베이스 | MySQL |
| 외부 API | [Open-Meteo](https://open-meteo.com/) 날씨·대기질·지명 검색 |

## 폴더 구조

```
sunset-star/
├── backend/    Spring Boot API 서버
├── frontend/   React 웹 화면
└── README.md
```

## 실행 방법

### 1. 준비물

- JDK 17 이상 (프로젝트는 Java 21로 빌드하며, 없으면 Gradle이 자동으로 내려받습니다)
- MySQL 8
- Node.js 20.19 이상

### 2. 데이터베이스 만들기

MySQL에 접속해서 한 번만 실행합니다.

```sql
CREATE DATABASE sunset_star DEFAULT CHARACTER SET utf8mb4;
```

### 3. 비밀 설정 파일 채우기

`backend/src/main/resources/application-secret.yml`에 내 MySQL 아이디와 비밀번호를 적습니다.
이 파일은 `.gitignore`에 들어 있어서 GitHub에 올라가지 않습니다.
처음 내려받은 경우에는 같은 폴더의 `application-secret.example.yml`을 복사해서 만듭니다.

### 4. 서버 실행

```bash
cd backend
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

브라우저에서 http://localhost:8080/api/health 를 열었을 때 `{"status":"ok"}`가 나오면 성공입니다.

### 5. 화면 실행

```bash
cd frontend
npm install
npm run dev
```

http://localhost:5173 에서 "서버와 연결됐어요"가 보이면 프론트와 서버가 잘 연결된 것입니다.

## 데이터 출처

Weather data by [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0)
