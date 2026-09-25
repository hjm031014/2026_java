# Campus Marketplace Backend

Spring Boot 3 · Java 21 · Gradle. [API 명세서](../API.md) · [백엔드 파트 분담](../BACKEND_TASKS.md)

## 실행 방법

DB(Neon)는 아직 팀원이 URL을 공유하기 전까지는 로컬 PostgreSQL 또는 아래 환경변수로 대체하세요.

```bash
./gradlew bootRun
```

`src/main/resources/application-local.yml.sample` 을 복사해 `application-local.yml` 로 만들고
(gitignore 되어 커밋되지 않습니다) 값을 채운 뒤 `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` 로 실행해도 됩니다.

## 환경변수

| 변수 | 설명 | 기본값 |
|---|---|---|
| `DB_URL` | Neon PostgreSQL JDBC URL (`?sslmode=require` 포함) | `jdbc:postgresql://localhost:5432/campus_marketplace` |
| `DB_USERNAME` / `DB_PASSWORD` | Neon 접속 정보 | `postgres` / `postgres` |
| `JWT_SECRET` | 액세스 토큰 서명 키 (32바이트 이상) | 개발용 기본값(운영 배포 전 반드시 교체) |
| `JWT_ACCESS_TOKEN_VALIDITY_SECONDS` | 액세스 토큰 유효 기간(초) | `900`(15분) |
| `CORS_ALLOWED_ORIGINS` | 프론트(Vercel) 배포 도메인, 콤마로 여러 개 지정 가능 | `http://localhost:3000` |
| `COOKIE_SECURE` | 리프레시/CSRF 쿠키 Secure 속성 (배포는 true 필수) | `true` |
| `COOKIE_SAME_SITE` | `None`(크로스 도메인) / `Lax`(로컬) | `None` |
| `COOKIE_DOMAIN` | 쿠키 Domain (비워두면 미지정) | (없음) |
| `CLOUDINARY_URL` | `cloudinary://<key>:<secret>@<cloud_name>` | (없음, 이미지 업로드 시에만 필요) |
| `PORT` | 서버 포트 (Render가 자동 주입) | `8080` |
| `JPA_DDL_AUTO` | 로컬 개발 편의용. 운영에서는 신중히 사용 | `update` |

## 구현 현황 (파트 A: 회원·인증·이미지 + 공통 인프라)

- 공통: `{ data }` / `{ error }` 응답 포맷, 전역 예외 처리(`GlobalExceptionHandler`), 요청 ID 부여,
  커서 페이지네이션 유틸(`CursorCodec`, `PageResponse`) — B/C 파트에서 그대로 재사용 가능
- 인증: JWT 액세스 토큰(15분) + 리프레시 토큰(7일, 세션 로테이션 방식) + 더블 서브밋 CSRF 쿠키
- API: `GET /auth/csrf`, `POST /auth/signup`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`,
  `GET /users/me`, `POST /images`, `DELETE /images/{imageId}`
- 검증: `AuthFlowIntegrationTest` 가 회원가입→로그인→내 정보 조회→토큰 갱신(로테이션)→재사용 탐지→로그아웃
  전체 흐름과 CSRF 거부 케이스를 H2 기반으로 검증합니다. `./gradlew test` 로 실행하세요.

### B, C 파트 참고사항

- `SecurityConfig` 의 `authorizeHttpRequests` 에 공개 GET API(판매글 목록/상세/검색, 카테고리, 거래 장소,
  댓글 목록 등) permitAll 규칙을 추가해주세요. 지금은 `/api/v1/auth/**` 외 전부 인증 필요 상태입니다.
- `PostImage.postId` 는 아직 `SALE_POSTS` 엔티티가 없어 일반 컬럼(Long)입니다. Post 엔티티를 추가하면
  `@ManyToOne` 연관관계로 바꿔주세요.
- 조회 이벤트(`POST /posts/{postId}/views`)는 CSRF 보호 대상 경로로 이미 등록해뒀습니다
  (`CsrfProtectionFilter`).
- `ErrorCode` 에 모든 오류 코드가 이미 정의되어 있으니 새로 추가하지 말고 재사용하세요.
