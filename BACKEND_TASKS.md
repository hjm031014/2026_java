# 백엔드 파트 분담

백엔드 3명(홍정민 · 박종호 · 홍태겸)이 나눠서 개발할 파트입니다. 이름은 예시 순서(A·B·C)이며, 팀 내에서 조정해도 됩니다.

[프로젝트 소개로 돌아가기](README.md) · [기능 명세서](FEATURES.md) · [API 명세서](API.md)

## 0. 공통 선행 작업 (전원 착수 전 필요, A가 우선 완료)

다른 파트가 이 위에서 작업하므로 **가장 먼저** 끝나야 합니다.

| 작업 | 내용 |
|---|---|
| 프로젝트 초기 세팅 | Spring Boot 프로젝트 생성, 패키지 구조, `application.yml`, Neon PostgreSQL 연결, Render 배포 설정 |
| 공통 응답/예외 처리 | `{ "data": ... }` / `{ "error": { code, message, details, requestId } }` 포맷, `@ControllerAdvice` 전역 예외 핸들러, API.md의 오류 코드 표 구현 |
| 인증/보안 골격 | Spring Security 설정, JWT 액세스 토큰(15분) 발급·검증 필터, 리프레시 토큰(7일, HttpOnly 쿠키) 발급·교체·폐기, CSRF 토큰(`X-CSRF-Token`) 처리 |
| 페이지네이션 공통 모듈 | 커서 기반 페이지네이션 유틸(`nextCursor`, `hasNext`), `limit` 기본 20/최대 100 검증 |
| 낙관적 잠금 공통 처리 | `version` 필드 충돌 시 `409 VERSION_CONFLICT` 반환하는 공통 로직 |

### 현재 진행 상황 (2026-09-29)

- 파트 A·B·C의 `API.md` 공개 API에 대응하는 Controller, Service, Repository가 구현되어 있습니다.
- `GET /api/v1/posts` 목록과 상세, 카테고리, 거래 장소는 비로그인 사용자에게 공개되어 있습니다.
- PR #8·#9가 `main`에 병합되어 Neon MIME 값 호환, Cloudinary `secure_url`·`public_id` 저장과 원본 삭제,
  조회 이벤트 해시 저장, `GET /api/v1/posts`의 null 파라미터 바인딩 문제 수정, 관련 API 문서 갱신이 반영되었습니다.
- Java 17 환경에서 `backend/gradlew.bat test`를 실행해 전체 스위트(42개 테스트)가 통과하는 것을 확인했습니다.
- `GET /api/v1/posts`의 PostgreSQL 500 문제는 임베디드 실제 PostgreSQL(H2가 아님, `PostListPostgresCompatibilityTest`)로
  파라미터 없음/`?limit=20`/`?q=&limit=20`/카테고리·상태 필터/정렬·커서 페이지네이션 조합을 재검증해
  모두 200을 반환함을 확인했습니다. 테스트에는 `io.zonky.test:embedded-postgres`(Docker 불필요)를 사용합니다.
- 판매글 CRUD, 소유권·버전 충돌, 판매완료 후 수정·상태복구 금지, 이미지 소유권·중복 첨부 제한,
  조회수 중복 방지(24시간·작성자 제외) 규칙을 MockMvc 기반 HTTP 통합 테스트(`PostApiHttpIntegrationTest`)로
  추가 검증했습니다. Cloudinary 호출은 전부 mock 처리했습니다.
- 배포 환경에는 실제 `CLOUDINARY_URL`을 설정해야 이미지 업로드·삭제를 검증할 수 있습니다. 이 작업 환경에는
  실제 Cloudinary 자격증명과 외부 PostgreSQL(Neon) 접속 정보가 없어 실제 Cloudinary API·Neon 연동은 코드
  리뷰와 mock 기반 테스트로만 확인했으며, 배포 환경에서 직접 재확인이 필요합니다. API 키와
  API Secret은 저장소에 커밋하지 않습니다.
- ERD의 `transactions` 테이블을 사용하는 별도 거래 API는 현재 `API.md`에 정의되어 있지 않습니다.
  판매글 거래 상태는 `PATCH /posts/{postId}/status`로 변경합니다.

## 담당자 A — 회원·인증·이미지

| 기능 | 엔드포인트 |
|---|---|
| CSRF 토큰 발급 | `GET /auth/csrf` |
| 회원가입 | `POST /auth/signup` |
| 로그인 | `POST /auth/login` |
| 토큰 갱신 | `POST /auth/refresh` |
| 로그아웃 | `POST /auth/logout` |
| 내 정보 확인 | `GET /users/me` |
| 이미지 업로드 | `POST /images` |
| 미연결 이미지 삭제 | `DELETE /images/{imageId}` |

**핵심 규칙**
- 이메일·닉네임 중복 검증 (`EMAIL_ALREADY_EXISTS` / `NICKNAME_ALREADY_EXISTS`)
- 리프레시 토큰 재사용 탐지 (`REFRESH_TOKEN_REUSED`) 및 토큰 계열(rotation) 관리
- 이미지: JPEG/PNG/WebP만 허용, 파일당 최대 10MiB → Cloudinary 업로드 후 보안 URL과 삭제용 public ID를 `Image` 테이블에 저장
- 본인 이미지만 첨부 가능, 이미 다른 글에 첨부된 이미지는 `IMAGE_ALREADY_ATTACHED`, 글에 첨부된 이미지 삭제 시도는 `IMAGE_IN_USE`
- 위 "공통 선행 작업" 담당 겸임(우선순위 최상단)

## 담당자 B — 판매글·거래

| 기능 | 엔드포인트 |
|---|---|
| 카테고리 목록 | `GET /categories` |
| 거래 희망 장소 목록 | `GET /trade-places` |
| 판매글 등록 | `POST /posts` |
| 판매글 목록·검색 | `GET /posts` |
| 판매글 상세 | `GET /posts/{postId}` |
| 판매글 수정 | `PATCH /posts/{postId}` |
| 판매글 삭제 | `DELETE /posts/{postId}` |
| 거래 상태 변경 | `PATCH /posts/{postId}/status` |
| 조회 이벤트 | `POST /posts/{postId}/views` |

**핵심 규칙**
- 검색: `q`, `categoryId`, `status`, `sort`, `cursor`, `limit` 쿼리 처리
- 수정 시 `version` 필수, `imageIds`는 전체 교체, 빠진 이미지는 "미연결"로 남김(삭제 안 함)
- 삭제 시 `version` 쿼리 필수, 삭제된 글은 목록·상세에서 제외하되 기존 채팅은 유지
- 상태: `SELLING` ↔ `RESERVED` 자유 전환, 둘 다 `SOLD`로 전환 가능. `SOLD` 이후 되돌리기·수정 불가 (`INVALID_STATUS_TRANSITION`)
- 조회수: 상세 조회와 별개 이벤트, 회원은 계정/비회원은 IP 기준 글당 24시간 1회, 작성자 본인 제외

## 담당자 C — 소통(댓글·찜·채팅)

| 기능 | 엔드포인트 |
|---|---|
| 댓글 목록 | `GET /posts/{postId}/comments` |
| 댓글 작성 | `POST /posts/{postId}/comments` |
| 댓글 삭제 | `DELETE /comments/{commentId}` |
| 찜하기 | `PUT /posts/{postId}/favorite` |
| 찜 취소 | `DELETE /posts/{postId}/favorite` |
| 찜한 상품 목록 | `GET /users/me/favorites` |
| 채팅방 개설·재사용 | `POST /chat/rooms` |
| 내 채팅방 목록 | `GET /chat/rooms` |
| 메시지 조회 | `GET /chat/rooms/{roomId}/messages` |
| 메시지 전송 | `POST /chat/rooms/{roomId}/messages` |

**핵심 규칙**
- 댓글 삭제는 작성자만 가능
- 찜/찜 취소는 멱등 처리(이미 찜한 글에 `PUT`, 안 한 글에 `DELETE`해도 `204`)
- 채팅방: 동일 구매자·판매글 조합은 기존 방 재사용(재사용 시 `200`, 신규 생성 시 `201`), 본인 글에는 개설 불가(`403 FORBIDDEN`)
- 메시지: `clientMessageId` 기준 재전송 방지(중복 저장 없이 기존 메시지 `200` 반환), 과거 조회(`cursor`)와 신규 조회(`afterSequence`)는 동시 사용 불가
- 판매글 삭제되어도 채팅 이력은 유지

## 협업 시 주의사항

- **엔티티 선점**: `User`, `Post`, `Image`는 여러 파트가 공유하므로 A·B가 먼저 스키마를 확정하고 공유
- **인증 의존성**: B·C의 회원 전용 API는 A의 JWT 필터가 먼저 동작해야 테스트 가능 → A를 최우선으로 진행
- **버전 충돌 규칙**은 B가 구현하지만 공통 유틸로 빼서 재사용 가능하게 작성 권장
- API.md의 오류 코드·응답 포맷은 전원 동일하게 따를 것 (프론트와의 계약이므로 임의 변경 금지)
