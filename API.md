# API 명세서

교내 중고거래 웹 플랫폼의 백엔드 API 요약입니다.

[프로젝트 소개로 돌아가기](README.md)

## 인증 및 공통 규칙

- 기본 경로: `/api/v1` · JSON 요청·응답 · 금액은 원 단위 정수
- **액세스 토큰:** JWT, 15분. 메모리에 보관하고 `Authorization: Bearer <token>`으로 전송
- **리프레시 토큰:** HttpOnly 쿠키, 7일. 갱신 시 교체하고 로그아웃 시 현재 토큰 계열 폐기
- **CSRF:** 일반 Bearer API에는 불필요. 회원가입·로그인·갱신·로그아웃·조회 이벤트에는 `X-CSRF-Token` 사용
- 응답: `{ "data": ... }` · 오류: `{ "error": { "code", "message", "details", "requestId" } }` · `204`는 본문 없음
- 목록: `data.items` + `data.page` 커서 페이지네이션. `limit` 기본 20, 최대 100. 카테고리·장소는 `data.items`만 반환
- 글 변경은 작성자, 댓글 삭제는 댓글 작성자, 채팅은 참여자만 가능

> 토큰 수명은 설계 제안입니다. Vercel·Render 간 쿠키 전달 및 CORS 설정은 배포 도메인에 맞춰 확정합니다.

## API 목록

모든 경로 앞에 `/api/v1`을 붙입니다. 공개는 비로그인 접근 가능입니다. **반환 결과는 응답 본문의 `data` 안에 들어갑니다.** `204`는 본문이 없습니다.

| 메서드 | 경로 | 기능 | 권한 | 성공 | 반환 결과 (`data`) |
|---|---|---|---|---|---|
| GET | /test | 서버 상태 확인 | 공개 | 200 | `{ status: "ok", date: string, time: string, timezone: "Asia/Seoul" }` |
| GET | /auth/csrf | CSRF 토큰 발급 | 공개 | 200 | `{ csrfToken: string }` |
| POST | /auth/signup | 회원가입 | 공개 | 201 | `MyUser` |
| POST | /auth/login | 로그인 | 공개 | 200 | `{ user: MyUser, accessToken: string, tokenType: "Bearer", expiresIn: 900 }` + 리프레시 쿠키 |
| POST | /auth/refresh | 토큰 갱신 | 리프레시 쿠키 | 200 | `{ accessToken: string, tokenType: "Bearer", expiresIn: 900 }` + 교체된 리프레시 쿠키 |
| POST | /auth/logout | 로그아웃 | 공개 | 204 | 본문 없음 + 리프레시 쿠키 만료 |
| GET | /users/me | 내 정보 확인 | 회원 | 200 | `MyUser` |
| GET | /categories | 카테고리 목록 | 공개 | 200 | `{ items: Category[] }` |
| GET | /trade-places | 거래 희망 장소 목록 | 공개 | 200 | `{ items: TradePlace[] }` |
| POST | /images | 이미지 업로드 | 회원 | 201 | `Image` |
| DELETE | /images/{imageId} | 미연결 이미지 삭제 | 소유자 | 204 | 본문 없음 |
| POST | /posts | 판매글 등록 | 회원 | 201 | 생성된 `PostDetail` |
| GET | /posts | 판매글 목록·검색 | 공개 | 200 | `{ items: PostSummary[], page: Page }` |
| GET | /posts/{postId} | 판매글 상세 | 공개 | 200 | `PostDetail` |
| PATCH | /posts/{postId} | 판매글 수정 | 작성자 | 200 | 수정된 `PostDetail` |
| DELETE | /posts/{postId} | 판매글 삭제 | 작성자 | 204 | 본문 없음 |
| PATCH | /posts/{postId}/status | 거래 상태 변경 | 작성자 | 200 | 상태가 변경된 `PostDetail` |
| POST | /posts/{postId}/views | 조회 이벤트 | 공개 | 200 | `{ viewCount: integer, counted: boolean }` |
| GET | /posts/{postId}/comments | 댓글 목록 | 공개 | 200 | `{ items: Comment[], page: Page }` |
| POST | /posts/{postId}/comments | 댓글 작성 | 회원 | 201 | 생성된 `Comment` |
| DELETE | /comments/{commentId} | 댓글 삭제 | 작성자 | 204 | 본문 없음 |
| PUT | /posts/{postId}/favorite | 찜하기 | 회원 | 204 | 본문 없음 |
| DELETE | /posts/{postId}/favorite | 찜 취소 | 회원 | 204 | 본문 없음 |
| GET | /users/me/favorites | 찜한 상품 목록 | 회원 | 200 | `{ items: FavoriteItem[], page: Page }` |
| POST | /chat/rooms | 채팅방 개설·재사용 | 회원 | 201 또는 200 | 생성 또는 재사용한 `ChatRoom` |
| GET | /chat/rooms | 내 채팅방 목록 | 회원 | 200 | `{ items: ChatRoom[], page: Page }` |
| GET | /chat/rooms/{roomId}/messages | 이전·신규 메시지 조회 | 참여자 | 200 | 과거: `{ items: Message[], page: Page }` / 신규: `{ items: Message[], nextAfterSequence: integer, hasMore: boolean }` |
| POST | /chat/rooms/{roomId}/messages | 메시지 전송 | 참여자 | 201 또는 200 | 저장 또는 재전송된 기존 `Message` |

## 응답 데이터 정의

`string`은 문자열, `integer`는 정수, `boolean`은 참/거짓, `[]`는 배열입니다. `?`는 `null`을 허용한다는 뜻입니다. DB 식별자는 10진수 문자열이고 `clientMessageId`는 UUID 문자열입니다. 시간은 UTC ISO 8601 문자열입니다.

| 이름 | 반환 필드 |
|---|---|
| `Page` | `nextCursor: string?`, `hasNext: boolean` |
| `PublicUser` | `id: string`, `nickname: string` |
| `MyUser` | `id: string`, `email: string`, `nickname: string`, `createdAt: string` |
| `Category` | `id: string`, `name: string`, `sortOrder: integer` |
| `TradePlace` | `id: string`, `name: string`, `description: string`, `sortOrder: integer` |
| `Image` | `id: string`, `url: string`, `mimeType: "image/jpeg" | "image/png" | "image/webp"`, `size: integer`(바이트), `width: integer`, `height: integer` |
| `PostSummary` | `id: string`, `title: string`, `price: integer`, `status: string`, `thumbnailUrl: string?`, `category: Category`, `tradePlace: TradePlace`, `seller: PublicUser`, `viewCount: integer`, `favoriteCount: integer`, `isFavorited: boolean`, `createdAt: string` |
| `PostDetail` | `PostSummary`의 모든 필드 + `description: string`, `images: Image[]`, `version: integer`, `updatedAt: string` |
| `Comment` | `id: string`, `postId: string`, `author: PublicUser`, `content: string`, `createdAt: string` |
| `FavoriteItem` | `post: PostSummary`, `favoritedAt: string` |
| `Message` | `id: string`, `roomId: string`, `sender: PublicUser`, `sequence: integer`, `clientMessageId: string`, `content: string`, `createdAt: string` |
| `ChatRoom` | `id: string`, `post: { id: string, title: string, status: string, postDeleted: boolean }`, `otherUser: PublicUser`, `lastMessage: Message?`, `createdAt: string`, `updatedAt: string` |

- `status`는 `SELLING`, `RESERVED`, `SOLD` 중 하나입니다. 비회원의 `isFavorited`는 `false`입니다.
- 마지막 페이지의 `nextCursor`는 `null`입니다. 카테고리·장소에는 `page`가 없습니다.
- 신규 메시지 조회는 `sequence` 오름차순이며, 빈 결과의 `nextAfterSequence`는 요청값을 유지합니다. 과거 조회는 내림차순입니다.
- 채팅방·메시지 최초 생성은 `201`, 기존 방 재사용·동일 메시지 재전송은 `200`입니다.

## 응답 예시

**로그인 성공 — `200 OK`** (`accessToken`은 예시 문자열)

```json
{
  "data": {
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "student@example.com",
      "nickname": "학생",
      "createdAt": "2026-09-13T10:00:00Z"
    },
    "accessToken": "example.jwt.token",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

리프레시 토큰은 JSON이 아닌 `Set-Cookie` 헤더로 전달합니다. `expiresIn`은 초 단위입니다.

**조회수 반영 — `200 OK`**

```json
{ "data": { "viewCount": 12, "counted": true } }
```

**빈 목록 — `200 OK`**

```json
{ "data": { "items": [], "page": { "nextCursor": null, "hasNext": false } } }
```

**실패 — `401 Unauthorized`**

```json
{
  "error": {
    "code": "ACCESS_TOKEN_EXPIRED",
    "message": "액세스 토큰이 만료되었습니다.",
    "details": [],
    "requestId": "req-example"
  }
}
```

| 상태 | 의미 / 대표 오류 코드 |
|---|---|
| `400` | 입력 오류 `VALIDATION_ERROR`, 커서 오류 `INVALID_CURSOR` |
| `401` | 인증 누락 `UNAUTHENTICATED`, 로그인 실패 `INVALID_CREDENTIALS`, 만료 `ACCESS_TOKEN_EXPIRED`, 잘못된 토큰 `INVALID_TOKEN`, 폐기 `TOKEN_REVOKED`, 갱신 실패 `INVALID_REFRESH_TOKEN`, 재사용 `REFRESH_TOKEN_REUSED` |
| `403` | 권한 없음 `FORBIDDEN`, CSRF 실패 `CSRF_INVALID` |
| `404` | 자원 없음 또는 비참여자의 채팅 접근 `RESOURCE_NOT_FOUND` |
| `409` | 버전 충돌 `VERSION_CONFLICT`, 상태 전이 오류 `INVALID_STATUS_TRANSITION`, 이메일·닉네임 중복 `EMAIL_ALREADY_EXISTS` / `NICKNAME_ALREADY_EXISTS`, 이미지 충돌 `IMAGE_ALREADY_ATTACHED` / `IMAGE_IN_USE`, 메시지 키 충돌 `IDEMPOTENCY_CONFLICT` |
| `413` / `415` | 파일 크기 초과 `PAYLOAD_TOO_LARGE` / 형식 오류 `UNSUPPORTED_MEDIA_TYPE` |
| `429` / `500` | 요청 제한 `RATE_LIMIT_EXCEEDED` / 서버 오류 `INTERNAL_SERVER_ERROR` |

- `IMAGE_ALREADY_ATTACHED`: 이미 다른 글에 첨부된 이미지를 재사용하려 할 때
- `IMAGE_IN_USE`: 글에 첨부된 이미지를 `DELETE /images/{imageId}`로 삭제하려 할 때

## 주요 요청 필드

| 요청 | 필드 |
|---|---|
| 회원가입 | `email`, `password`, `nickname` |
| 로그인 | `email`, `password` |
| 판매글 등록 | `title`, `description`, `price`, `categoryId`, `tradePlaceId`, `imageIds`(선택) |
| 판매글 검색 | 쿼리: `q`, `categoryId`, `status`, `sort`, `cursor`, `limit` |
| 판매글 수정 | `version` + 변경할 필드. `imageIds`는 전체 교체 |
| 판매글 삭제 | 쿼리: `version` 필수 |
| 거래 상태 변경 | `status`, `version` |
| 이미지 업로드 | multipart의 `file` 필드 |
| 댓글 작성 | `content` |
| 채팅방 생성 | `postId` |
| 메시지 전송 | `clientMessageId`(재시도 시 동일 UUID), `content` |
| 메시지 조회 | 과거: `cursor` / 신규: `afterSequence` — 함께 사용 불가 |

로그인 응답은 `data: { user, accessToken, tokenType, expiresIn }`이며 리프레시 토큰은 쿠키로 발급합니다.

## 핵심 처리 규칙

- **상태:** `SELLING` ↔ `RESERVED`, 두 상태에서 `SOLD`로 변경 가능. 거래완료 후 되돌리기·글 수정 불가
- **수정 충돌:** 최신 `version`으로 수정·상태 변경·삭제. 충돌하면 `409`
- **조회수:** 상세 GET과 별도로 조회 이벤트 호출. 회원은 사용자 ID, 비회원은 IP 기준으로 글당 24시간에 한 번 집계하고 작성자는 제외. 중복 판정 키는 SHA-256으로 저장하며 원본 IP는 저장하지 않음
- **이미지:** JPEG·PNG·WebP, 파일당 최대 10MiB, 글당 최대 10개. 본인 이미지로만 등록
- **이미지 저장:** 업로드된 파일은 Cloudinary에 저장하고, 반환된 보안 URL과 삭제용 public ID를 DB에 저장. public ID는 API 응답에 노출하지 않음
- **이미지 정리:** 글 수정으로 `imageIds`에서 빠진 이미지는 자동으로 "미연결" 상태가 되며 삭제되지 않음. 소유자가 `DELETE /images/{imageId}`를 호출하면 DB 기록과 Cloudinary 원본을 함께 삭제. 글 삭제 시 첨부 이미지는 유지(고아 이미지 정리는 별도 배치로 처리)
- **찜:** 이미 찜한 글에 `PUT`, 찜 안 한 글에 `DELETE` 호출 시 상태 변화 없이 `204` (멱등)
- **채팅:** 판매글별 구매자당 방 하나, REST 폴링 사용. 메시지 재전송은 중복 저장 방지. 작성자 본인 글에는 채팅방 개설 불가 (`403 FORBIDDEN`)
- **삭제:** 삭제 글은 목록·상세에서 제외하고 기존 참여자의 채팅은 유지

> 현재 백엔드 구현과 프론트 연동 계약을 함께 관리하는 명세입니다. 공개 API를 변경할 때 코드와 이 문서를 함께 수정합니다.
> 서버 상태 확인은 `/api/v1/test` 외에 루트 경로 `/test`도 같은 응답을 제공합니다.
