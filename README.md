<div align="center">
  <h1> COMEBACK ROOM - Backend</h1>
  <p>EFUB 6기 AWA 1팀 COMEBACK ROOM 백엔드 레포지토리입니다.</p>
  <img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/cddf7699-799d-4727-9a1d-ecdd6ad7551e" />
</div>


## 🌐 COMEBACK ROOM
> THE COMEBACK STARTS TOGETHER
> 같이 보는 순간, 컴백은 더 커진다.

COMEBACK ROOM은 아이돌 팬들이 공식 유튜브 영상을 함께 보며 실시간으로 소통하는 팬 참여형 동시 시청 플랫폼입니다.
설정한 시작 시각에 맞춰 모든 참여자의 영상이 함께 재생되며, 채팅, 리액션, 투표 같은 참여 기능으로 팬들이 같은 순간을 함께 즐길 수 있습니다.



## 🗓️ 개발 기간
- 2026.09.01 - ing



## 🔨 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Database | PostgreSQL 17 |
| Migration | Flyway |
| Build | Gradle |
| Test | JUnit 6, Testcontainers, JaCoCo |


## 🌱 환경변수 설정

예시 파일을 복사해 본인 로컬 값으로 채워 사용합니다. <br />
이 `.env` 파일은 DB 컨테이너(docker-compose)와 애플리케이션이 공통으로 참조합니다.

```bash
cp .env.example .env
# .env 파일을 열어 본인의 DB 접속 정보 등을 채웁니다.
```

## 📚 데이터베이스 준비

### 1) DB 컨테이너 실행

환경변수 파일(`.env`)이 준비되었으면 docker-compose로 PostgreSQL을 띄웁니다.

```bash
docker compose up -d
```

### 2) 테이블 생성 (Flyway 자동 실행)

서버가 처음 뜰 때 Flyway가 `src/main/resources/db/migration/`의 마이그레이션 스크립트를 자동으로 실행해 스키마를 생성합니다.



## 🏁 서버 실행

```bash
./gradlew bootRun
```
기본 포트는 **8080**입니다.

- 정상 구동 확인 (Actuator health check):

```bash
curl http://localhost:8080/actuator/health
# 기대 출력: {"status":"UP"}
```



## 🧪 테스트

```bash
./gradlew test
```

> **실행 전 Docker가 켜져 있어야 합니다.** 
> 테스트는 Testcontainers로 실제 PostgreSQL 컨테이너를 띄워 검증합니다.

- 테스트가 끝나면 커버리지 리포트가 자동 생성됩니다.
- 리포트 위치: `build/reports/jacoco/test/html/index.html` (브라우저로 열어 확인)



## 💡 빌드

```bash
./gradlew build
```

컴파일, 테스트, 패키징이 함께 수행됩니다. <br />
결과물(JAR): `build/libs/` 아래에 생성됩니다.  <br />

- 생성된 JAR 직접 실행:

```bash
java -jar build/libs/comeback-room-0.0.1-SNAPSHOT.jar
```