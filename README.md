# 교내 중고거래 서비스 백엔드 기능 및 API 명세서

> 문서 버전: 1.0.0 · 작성일: 2026-09-07 · 상태: 구현 전 설계 초안
>
> 첨부 이미지의 기능 목록을 요구사항으로 정리했다. 실제 구현이나 기존 서버를 검증한 문서가 아니다. 이미지에 없는 정책·필드·경로는 아래 설계 가정에 따라 제안한다.

## 1. 목적 및 범위

교내 사용자가 판매글을 등록하고, 상품을 탐색하고, 판매자와 1:1 채팅으로 거래를 협의하는 서비스의 백엔드 계약을 정의한다.

### 1.1 원본 기능 범위

| 영역 | 요구 기능 |
| --- | --- |
| 회원 | 회원가입, 로그인, 로그아웃 |
| 판매글 | 등록, 목록 조회, 상세 조회, 수정, 삭제 |
| 판매글 부가 기능 | 이미지 업로드, 카테고리 분류, 검색 |
| 거래 | 판매중 / 예약중 / 거래완료 상태 관리 |
| 댓글 | 작성, 삭제 |
| 찜 | 찜하기, 찜 취소, 찜한 상품 목록 |
| 채팅 | 판매자와 1:1 채팅, 채팅방 목록, 이전 메시지 조회 |
| 교내 특화 | 거래 희망 장소 선택 |
| 기타 | 조회수 |

### 1.2 설계 가정 및 미확정 사항

- 단일 학교를 대상으로 한다. 학교·학과별 구분 및 학교 이메일 인증은 원본에 없어 범위에서 제외한다.
- 회원가입은 이메일, 비밀번호, 닉네임을 사용한다. 이메일은 공백 제거 및 소문자 정규화 후 고유해야 한다.
- 비회원은 카테고리·장소·판매글·댓글을 조회할 수 있다. 나머지는 로그인해야 한다.
- 웹 클라이언트를 기준으로 서버 세션과 보안 쿠키로 인증한다. 토큰 인증이 필요한 앱을 추가하면 별도 계약이 필요하다.
- 채팅은 REST 조회 및 전송을 기본으로 하며 클라이언트가 주기적으로 새 메시지를 조회한다. WebSocket, 읽음 표시, 푸시 알림은 이번 범위에 포함하지 않는다.
- 거래 희망 장소는 서버에 미리 등록된 교내 장소 중 하나를 선택한다. 장소 예약이나 지도 좌표 검색은 포함하지 않는다.
- 결제, 배송, 구매 확정, 구매자 지정, 신고, 관리자 화면, 비밀번호 재설정은 별도 요구사항이다.
- 페이지 크기, 입력 길이, 업로드 용량, 요청 제한 수치는 제안값이다. 운영 환경에 맞춰 확정해야 한다.

## 2. 공통 API 규약

### 2.1 기본 규칙

- 기본 경로: `/api/v1`
- 일반 요청·응답: `application/json; charset=utf-8`
- 이미지 요청: `multipart/form-data`
- 시간: UTC의 ISO 8601 문자열. 예: `2026-09-07T05:30:00Z`
- ID: 추측하기 어려운 UUID 문자열. 문서의 `{id}`는 실제 ID로 치환한다.
- 금액: 원 단위 정수. `0`은 무료 나눔이며 소수와 음수는 허용하지 않는다.
- 상세 객체 응답은 `data`, 목록 응답은 `data.items`와 `data.page`로 감싼다.
- `204 No Content`에는 응답 본문이 없다.
- 경로가 참조하는 자원이 없거나 삭제되었다면 `404`를 반환한다. 다른 사용자의 채팅방도 정보 노출을 막기 위해 `404`로 처리한다.

### 2.2 인증 및 보안

로그인 성공 시 `session_id` 쿠키를 발급한다. 쿠키 속성은 `HttpOnly; Secure; SameSite=Lax; Path=/`이며 전체 통신은 HTTPS를 사용한다. 세션 유효기간은 발급 시점부터 7일로 제안한다. 로그아웃하면 현재 세션을 서버에서 즉시 폐기하고 쿠키도 만료시킨다.

`GET /auth/csrf`는 익명 사용자도 호출 가능하며 CSRF 토큰을 반환하고 관련 쿠키를 설정한다. 모든 변경 요청(`POST`, `PATCH`, `PUT`, `DELETE`, 회원가입·로그인 포함)은 `X-CSRF-Token` 헤더를 전송해야 한다. 서버는 토큰과 요청 Origin을 검증한다. 로그인 시 세션 ID를 교체한다. 운영 배포는 동일 사이트 구성을 전제로 하며, 별도 Origin을 사용하면 허용 목록과 credential 설정을 명시해야 한다.

비밀번호는 Argon2id 등 적절한 비밀번호 해시로 저장하며 평문이나 복호화 가능한 형태로 저장하지 않는다. 모든 소유자·참여자 권한은 서버에서 검사한다. 클라이언트가 전송한 작성자 ID, 조회수, 찜 수를 신뢰하지 않는다.

### 2.3 성공 응답

```json
{
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

목록은 커서 페이지네이션을 사용한다. `limit` 기본값은 20, 최댓값은 100이다. `cursor`는 서버가 발급하는 불투명 문자열이다. 다음 페이지가 없으면 `nextCursor`는 `null`이다.

```json
{
  "data": {
    "items": [],
    "page": { "nextCursor": null, "hasNext": false }
  }
}
```

목록 정렬에는 항상 ID를 보조 정렬키로 사용한다. 정렬·필터가 달라지면 기존 커서를 재사용할 수 없으며 유효하지 않은 커서는 `400 INVALID_CURSOR`다. 판매글은 가격 정렬 시에도 ID로 동률을 처리한다. 변경 가능한 목록은 페이지 사이에 변동될 수 있으며 스냅샷 일관성을 보장하지 않는다.

### 2.4 오류 응답

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력값을 확인해 주세요.",
    "details": [{ "field": "price", "reason": "0 이상의 정수여야 합니다." }],
    "requestId": "req-example"
  }
}
```

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR / INVALID_CURSOR | 입력값 또는 커서 오류 |
| 401 | UNAUTHENTICATED / INVALID_CREDENTIALS | 로그인 필요, 세션 만료 또는 로그인 실패 |
| 403 | FORBIDDEN / CSRF_INVALID | 권한 없음 또는 CSRF 검증 실패 |
| 404 | RESOURCE_NOT_FOUND | 자원 없음 또는 접근할 수 없는 채팅 자원 |
| 409 | EMAIL_ALREADY_EXISTS / NICKNAME_ALREADY_EXISTS | 회원 고유값 충돌 |
| 409 | INVALID_STATUS_TRANSITION / VERSION_CONFLICT | 상태 전이 위반 또는 동시 수정 충돌 |
| 409 | IMAGE_ALREADY_ATTACHED / IMAGE_IN_USE / IDEMPOTENCY_CONFLICT | 이미지 연결 충돌 또는 메시지 재전송 내용 불일치 |
| 413 | PAYLOAD_TOO_LARGE | 업로드 크기 초과 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 허용되지 않은 실제 파일 형식 |
| 429 | RATE_LIMIT_EXCEEDED | 요청 제한 초과. `Retry-After` 초 단위 헤더 제공 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류. SQL, 스택, 비밀값은 응답에 포함하지 않음 |

## 3. 백엔드 기능 명세

| ID | 기능 | 권한 | 처리 및 핵심 규칙 |
| --- | --- | --- | --- |
| MEM-01 | 회원가입 | 비회원 | 이메일·닉네임 중복 검사, 비밀번호 해시 저장, 자동 로그인 없이 회원 생성 |
| MEM-02 | 로그인 | 비회원 | 자격 증명 확인 및 서버 세션 발급. 이메일 존재 여부와 무관하게 실패 메시지 통일 |
| MEM-03 | 로그아웃 | 누구나 | 현재 세션 및 쿠키 폐기. 이미 로그아웃 상태여도 성공 |
| POST-01 | 판매글 등록 | 회원 | 제목·본문·가격·카테고리·장소 검증, 자신의 업로드 이미지만 연결, 최초 상태 SELLING |
| POST-02 | 판매글 목록·검색 | 누구나 | 삭제 글 제외, 카테고리·상태·키워드 필터, 페이지네이션 |
| POST-03 | 판매글 상세 | 누구나 | 판매자 공개 정보, 이미지, 장소, 상태, 조회수·찜 수 제공 |
| POST-04 | 판매글 수정 | 작성자 | 허용 필드만 수정. 거래완료 글은 일반 정보 수정 불가 |
| POST-05 | 판매글 삭제 | 작성자 | 논리 삭제, 검색·찜 목록에서 제외. 관련 채팅 기록은 참여자에게 보존 |
| IMG-01 | 이미지 업로드 | 회원 | 실제 형식·크기 검증 후 안전한 이미지로 재인코딩하고 식별자 반환 |
| CAT-01 | 카테고리 분류 | 누구나 조회 | 활성 카테고리 목록 제공. 등록·수정 시 유효한 카테고리 참조 |
| TRADE-01 | 거래 상태 변경 | 작성자 | 허용된 전이 및 version 검사. 상태 변경은 결제나 실제 거래 성사를 검증하지 않음 |
| COMMENT-01 | 댓글 조회·작성 | 조회 공개, 작성 회원 | 상세 화면 표시를 위한 목록 제공. 삭제 글에는 작성 불가 |
| COMMENT-02 | 댓글 삭제 | 댓글 작성자 | 논리 삭제. 판매자는 타인의 댓글을 삭제할 수 없음 |
| LIKE-01 | 찜하기·취소 | 회원 | 중복 생성 방지. 자신의 글은 찜 불가. 재요청에 같은 효과 보장 |
| LIKE-02 | 찜한 상품 목록 | 본인 | 찜한 시각 최신순, 삭제 글 제외, 거래완료 글 포함 |
| CHAT-01 | 판매자와 1:1 채팅 | 회원 | 판매글별 판매자·구매자 조합당 방 하나. 자기 글에 채팅 개설 불가 |
| CHAT-02 | 채팅방 목록 | 참여자 | 마지막 활동 최신순, 마지막 메시지 및 상대방 공개 정보 제공 |
| CHAT-03 | 메시지 전송·조회 | 참여자 | 서버 순번으로 순서 보장, 중복 전송 방지, 과거·신규 메시지 조회 |
| PLACE-01 | 거래 희망 장소 | 누구나 조회 | 활성 교내 장소 선택. 실제 만남 시간·장소 확정은 채팅으로 협의 |
| VIEW-01 | 조회수 | 누구나 | 명시적인 조회 이벤트를 접수해 중복 기준에 따라 원자적으로 증가 |

### 3.1 입력값 정책

| 필드 | 제약 |
| --- | --- |
| email | 유효한 이메일 형식, 최대 254자 |
| password | 10~128자. 회원 응답·로그에 노출 금지 |
| nickname | 앞뒤 공백 제거 후 2~20자, 대소문자 구분 없이 고유 |
| title | 앞뒤 공백 제거 후 1~100자 |
| description | 앞뒤 공백 제거 후 1~5,000자, 일반 텍스트 |
| price | 0~100,000,000 정수 |
| imageIds | 고유한 이미지 ID 0~10개. 배열 순서로 표시하며 첫 이미지가 대표 이미지 |
| comment.content | 공백만 있는 값 금지, 1~1,000자 일반 텍스트 |
| message.content | 공백만 있는 값 금지, 1~2,000자 일반 텍스트 |
| q | 앞뒤 공백 제거 후 1~100자. 빈 검색은 파라미터 생략 |

### 3.2 거래 상태

| 현재 상태 | 변경 가능 상태 | 규칙 |
| --- | --- | --- |
| SELLING (판매중) | RESERVED, SOLD | 예약 또는 거래완료 처리 |
| RESERVED (예약중) | SELLING, SOLD | 예약 취소 또는 거래완료 처리 |
| SOLD (거래완료) | 없음 | 이번 설계에서는 종료 상태. 재판매는 새 글 등록 |

동일 상태 요청은 최신 `version`이 일치하면 성공하며 버전을 올리지 않는다. 예약자나 구매자 ID는 관리하지 않는다. 글 내용 변경·상태 변경·삭제는 동일한 `version`을 검사하고, 실제 변경마다 1 증가시켜 경합을 방지한다. 조회수·찜 수 변경은 글 버전을 바꾸지 않는다.

### 3.3 이미지 및 삭제 정책

- JPEG, PNG, WebP만 허용하고 파일당 최대 10MiB, 최대 4,096×4,096 픽셀로 제한한다. SVG, GIF 및 이미지로 위장한 파일은 거부한다.
- 확장자와 Content-Type만 신뢰하지 않고 디코딩으로 검증한다. 메타데이터를 제거하고 재인코딩한 이미지만 제공한다.
- 업로드 이미지의 소유자는 업로더다. 이미 다른 글에 연결된 이미지 또는 다른 사람의 이미지는 연결할 수 없다.
- 이미지 연결과 글 저장은 하나의 데이터베이스 트랜잭션으로 처리한다. 스토리지 정리는 재시도 가능한 후처리 작업으로 수행한다.
- 미연결 이미지는 24시간 후 정리한다. 수정으로 분리된 이미지도 분리 후 24시간이 지나면 정리한다. 연결된 이미지의 직접 삭제는 `409 IMAGE_IN_USE`다.
- 판매글 삭제 시 댓글·이미지·찜의 공개 접근을 차단하고 이미지 파일 삭제를 예약한다. 기존 CDN 캐시를 고려해 캐시 무효화 또는 짧은 만료 정책을 적용한다.
- 삭제된 글의 기존 채팅방은 제목 스냅샷과 `postDeleted: true`를 표시한다. 기존 참여자는 과거 메시지를 조회하고 대화를 이어갈 수 있으나 새 방은 생성할 수 없다.
- 개인정보 및 채팅 보존 기간은 운영 전 별도 확정한다. 이 문서는 무기한 보존을 운영 정책으로 승인하지 않는다.

## 4. 데이터 모델 및 응답 객체

### 4.1 주요 저장 모델

| 모델 | 주요 필드 | 제약 및 인덱스 |
| --- | --- | --- |
| User | id, email, passwordHash, nickname, createdAt | 정규화 email·nickname 각각 UNIQUE |
| Session | idHash, userId, expiresAt | 서버에서 폐기 가능, 만료 인덱스 |
| Category | id, name, sortOrder, isActive | 초기 데이터로 관리 |
| TradePlace | id, name, description, sortOrder, isActive | 초기 데이터로 관리 |
| Post | id, sellerId, title, description, price, categoryId, placeId, status, version, viewCount, createdAt, updatedAt, deletedAt | 상태·카테고리·생성일 인덱스 |
| Image | id, ownerId, postId?, storageKey, mimeType, size, width, height, position?, detachedAt?, createdAt | postId·position 및 미연결 정리 인덱스 |
| Comment | id, postId, authorId, content, createdAt, deletedAt | postId·createdAt·id |
| Favorite | userId, postId, createdAt | UNIQUE(userId, postId), userId·createdAt |
| ChatRoom | id, postId, sellerId, buyerId, postTitleSnapshot, lastMessageAt?, lastSequence, createdAt | UNIQUE(postId, buyerId), 참여자별 활동일 인덱스 |
| Message | id, roomId, senderId, sequence, clientMessageId, content, createdAt | UNIQUE(roomId, sequence), UNIQUE(roomId, senderId, clientMessageId) |
| PostViewDedup | postId, viewerKeyHash, expiresAt | UNIQUE(postId, viewerKeyHash), 만료 인덱스 |

외래키 무결성을 유지하고 논리 삭제한 글의 ID를 재사용하지 않는다. 찜 수는 Favorite에서 계산하거나 같은 트랜잭션으로 유지하는 카운터를 사용한다. 검색은 제목·본문의 대소문자 구분 없는 부분 일치를 기본 계약으로 하며, 데이터 규모에 따라 동일 검색 동작을 유지하는 인덱스나 검색 엔진을 선택한다.

### 4.2 응답 객체 정의

아래 표의 `?`는 값이 `null`일 수 있다는 뜻이다. 응답 필드는 별도 언급이 없다면 항상 존재한다.

| 객체 | 필드 |
| --- | --- |
| PublicUser | `id`, `nickname` |
| MyUser | `id`, `email`, `nickname`, `createdAt` |
| Category | `id`, `name`, `sortOrder` |
| TradePlace | `id`, `name`, `description`, `sortOrder` |
| Image | `id`, `url`, `mimeType`, `size`, `width`, `height` |
| PostSummary | `id`, `title`, `price`, `status`, `thumbnailUrl?`, `category: Category`, `tradePlace: TradePlace`, `seller: PublicUser`, `viewCount`, `favoriteCount`, `isFavorited`, `createdAt` |
| PostDetail | PostSummary의 모든 필드 + `description`, `images: Image[]`, `version`, `updatedAt` |
| Comment | `id`, `postId`, `author: PublicUser`, `content`, `createdAt` |
| FavoriteItem | `post: PostSummary`, `favoritedAt` |
| Message | `id`, `roomId`, `sender: PublicUser`, `sequence`, `clientMessageId`, `content`, `createdAt` |
| ChatRoom | `id`, `post: {id, title, status, postDeleted}`, `otherUser: PublicUser`, `lastMessage: Message?`, `createdAt`, `updatedAt` |

공개 응답에는 이메일을 포함하지 않는다. 비로그인 사용자의 `isFavorited`는 `false`다. 이미지 `url`은 조회 시 생성되는 접근 URL이며 영구 식별자로 저장하지 않는다. 비활성 카테고리·장소도 기존 글에서는 이름을 표시한다.

## 5. API 목록

모든 경로는 `/api/v1` 뒤에 붙인다. `공개`는 비로그인 접근 가능, `회원`은 인증 필요를 의미한다.

| 메서드 | 경로 | 기능 | 권한 | 성공 |
| --- | --- | --- | --- | --- |
| GET | /auth/csrf | CSRF 토큰 발급 | 공개 | 200 |
| POST | /auth/signup | 회원가입 | 공개 | 201 |
| POST | /auth/login | 로그인 | 공개 | 200 |
| POST | /auth/logout | 로그아웃 | 공개 | 204 |
| GET | /users/me | 내 정보 확인 | 회원 | 200 |
| GET | /categories | 카테고리 목록 | 공개 | 200 |
| GET | /trade-places | 거래 희망 장소 목록 | 공개 | 200 |
| POST | /images | 이미지 업로드 | 회원 | 201 |
| DELETE | /images/{imageId} | 미연결 이미지 삭제 | 소유자 | 204 |
| POST | /posts | 판매글 등록 | 회원 | 201 |
| GET | /posts | 판매글 목록·검색 | 공개 | 200 |
| GET | /posts/{postId} | 판매글 상세 | 공개 | 200 |
| PATCH | /posts/{postId} | 판매글 수정 | 작성자 | 200 |
| DELETE | /posts/{postId} | 판매글 삭제 | 작성자 | 204 |
| PATCH | /posts/{postId}/status | 거래 상태 변경 | 작성자 | 200 |
| POST | /posts/{postId}/views | 조회 이벤트 | 공개 | 200 |
| GET | /posts/{postId}/comments | 댓글 목록 | 공개 | 200 |
| POST | /posts/{postId}/comments | 댓글 작성 | 회원 | 201 |
| DELETE | /comments/{commentId} | 댓글 삭제 | 작성자 | 204 |
| PUT | /posts/{postId}/favorite | 찜하기 | 회원 | 204 |
| DELETE | /posts/{postId}/favorite | 찜 취소 | 회원 | 204 |
| GET | /users/me/favorites | 찜한 상품 목록 | 회원 | 200 |
| POST | /chat/rooms | 채팅방 개설·재사용 | 회원 | 201 또는 200 |
| GET | /chat/rooms | 내 채팅방 목록 | 회원 | 200 |
| GET | /chat/rooms/{roomId}/messages | 이전·신규 메시지 조회 | 참여자 | 200 |
| POST | /chat/rooms/{roomId}/messages | 메시지 전송 | 참여자 | 201 또는 200 |

## 6. API 상세 명세

이 절의 응답 객체는 4.2절과 공통 응답 래퍼를 함께 적용한다. 요청 필드는 `선택` 표시가 없으면 필수이며 정의되지 않은 변경 필드는 `400`으로 거부한다.

### 6.1 회원 및 인증

**`GET /auth/csrf`**

- 요청 본문 없음.
- 응답: `data: {csrfToken: string}`. 발급된 토큰을 변경 요청 헤더에 전송한다.

**`POST /auth/signup`**

```json
{ "email": "student@example.com", "password": "example-password-123!", "nickname": "캠퍼스판매자" }
```

- 응답: `201`, `data: MyUser`.
- 오류: `400` 입력 오류, `409` 이메일·닉네임 중복.

**`POST /auth/login`**

```json
{ "email": "student@example.com", "password": "example-password-123!" }
```

- 응답: `200`, `data: {user: MyUser, expiresAt: string}` 및 세션 쿠키.
- 오류: `401 INVALID_CREDENTIALS`, `429 RATE_LIMIT_EXCEEDED`.

**`POST /auth/logout`**

- 요청 본문 없음. 현재 세션 폐기 및 쿠키 만료, 응답 `204`.
- 세션이 없어도 CSRF 검증은 적용한다.

**`GET /users/me`**

- 응답: `200`, `data: MyUser`. 세션이 없거나 만료되면 `401`.

### 6.2 카테고리·장소

**`GET /categories`**, **`GET /trade-places`**

- 요청 파라미터 없음. 활성 데이터만 `sortOrder ASC, id ASC`로 반환한다.
- 응답: 각각 `data: {items: Category[]}`, `data: {items: TradePlace[]}`. 작은 기준정보 목록으로 페이지네이션은 사용하지 않는다.
- 카테고리 예시: 전자기기, 도서, 의류, 생활용품, 기타.
- 장소 예시: 정문, 도서관 앞, 학생회관. 실제 학교의 명칭과 ID는 초기 데이터로 확정한다.

### 6.3 이미지

**`POST /images`**

- `multipart/form-data`의 `file` 필드에 파일 하나를 전송한다.
- 응답: `201`, `data: Image`.
- 오류: `400` 손상된 이미지·해상도 초과, `413` 용량 초과, `415` 형식 오류.
- 응답의 `id`를 판매글 등록·수정 시 `imageIds`에 사용한다.

**`DELETE /images/{imageId}`**

- 자신의 미연결 이미지에 한해 삭제한다. 응답 `204`.
- 오류: `403` 타인 소유, `404` 이미지 없음, `409 IMAGE_IN_USE` 연결된 이미지.

### 6.4 판매글 등록

**`POST /posts`**

```json
{
  "title": "자료구조 전공책 판매합니다",
  "description": "필기 흔적이 조금 있습니다. 도서관 앞 거래 희망합니다.",
  "price": 15000,
  "categoryId": "11111111-1111-4111-8111-111111111111",
  "tradePlaceId": "22222222-2222-4222-8222-222222222222",
  "imageIds": ["33333333-3333-4333-8333-333333333333"]
}
```

- `imageIds`만 선택이며 생략 시 `[]`다. 카테고리와 장소는 활성 상태여야 한다.
- 작성자는 세션에서 결정한다. `status: SELLING`, `version: 1`, 조회수·찜 수는 0으로 생성한다.
- 응답: `201`, `data: PostDetail`, `Location: /api/v1/posts/{postId}`.
- 오류: `400` 잘못된 기준정보 또는 이미지 ID, `403` 타인 이미지 사용, `409 IMAGE_ALREADY_ATTACHED`.

### 6.5 판매글 목록·검색

**`GET /posts`**

| 쿼리 | 필수 | 의미 |
| --- | --- | --- |
| q | 아니요 | 제목 또는 본문에 포함되는 검색어 |
| categoryId | 아니요 | 카테고리 ID |
| status | 아니요 | SELLING, RESERVED, SOLD 중 하나. 생략 시 전체 |
| sort | 아니요 | newest(기본), priceAsc, priceDesc |
| cursor | 아니요 | 다음 페이지 커서 |
| limit | 아니요 | 기본 20, 1~100 |

- 모든 필터는 AND로 결합한다. `q`는 제목·본문 중 하나에 일치하면 된다.
- `newest`는 `createdAt DESC, id DESC`, `priceAsc`는 `price ASC, id ASC`, `priceDesc`는 `price DESC, id DESC`다.
- 응답: `200`, `data: {items: PostSummary[], page}`. 결과가 없으면 빈 배열이다.
- 잘못된 enum·ID 형식은 `400`, 형식은 유효하지만 일치하는 카테고리가 없는 검색은 빈 목록이다.

### 6.6 판매글 상세·수정·삭제

**`GET /posts/{postId}`**

- 응답: `200`, `data: PostDetail`. 조회 자체로 조회수를 증가시키지 않는다.

**`PATCH /posts/{postId}`**

```json
{ "version": 1, "price": 12000, "description": "가격 내립니다." }
```

- `version` 필수. 변경 가능 필드: `title`, `description`, `price`, `categoryId`, `tradePlaceId`, `imageIds`.
- 변경 필드는 최소 하나 필요하다. 생략한 값은 유지하며 `null`은 허용하지 않는다.
- `imageIds`는 전체 교체다. `[]`는 모두 제거한다. 유지할 기존 이미지 ID도 함께 전송한다.
- 응답: `200`, 최신 `data: PostDetail`.
- 오류: `403` 타인 글, `409 VERSION_CONFLICT` 버전 불일치, `409 INVALID_STATUS_TRANSITION` 거래완료 글 수정.

**`DELETE /posts/{postId}?version=2`**

- 쿼리 `version` 필수. 작성자가 현재 버전으로 요청하면 논리 삭제하고 `204`를 반환한다.
- 거래완료 글도 삭제할 수 있다. 이미 삭제된 글은 `404`, 버전 불일치는 `409`다.

### 6.7 거래 상태 변경

**`PATCH /posts/{postId}/status`**

```json
{ "status": "RESERVED", "version": 2 }
```

- 응답: `200`, 최신 `data: PostDetail`.
- 소유권, 버전, 3.2절 상태 전이를 원자적으로 검사한다.
- 오류: `403` 타인 글, `400` 알 수 없는 상태, `409` 버전 충돌 또는 허용되지 않은 전이.

### 6.8 조회수

**`POST /posts/{postId}/views`**

- 요청 본문 없음. 상세 화면이 표시된 뒤 클라이언트가 호출한다.
- 회원은 사용자 ID, 비회원은 서버가 발급한 서명된 `viewer_id` 쿠키로 중복을 판단한다. 쿠키는 `HttpOnly; Secure; SameSite=Lax; Path=/`로 설정한다.
- 같은 식별자의 같은 글 조회는 최초 집계 후 24시간 동안 추가 집계하지 않는다. 작성자 본인 조회는 집계하지 않는다.
- 중복 판정 기록과 카운터 증가는 원자적으로 처리한다. GET 캐싱이나 링크 미리보기로 조회수가 증가하지 않는다.
- 응답: `200`, `data: {viewCount: integer, counted: boolean}`.
- 비회원의 쿠키 초기화, 다른 기기 사용, 로그인 전후에 따른 중복을 완전히 방지하는 지표는 아니다.

### 6.9 댓글

**`GET /posts/{postId}/comments?limit=20&cursor=...`**

- 삭제되지 않은 댓글을 `createdAt ASC, id ASC`로 반환한다.
- 응답: `200`, `data: {items: Comment[], page}`.

**`POST /posts/{postId}/comments`**

```json
{ "content": "아직 구매 가능한가요?" }
```

- 응답: `201`, `data: Comment`. 거래완료 글에도 작성 가능하다.

**`DELETE /comments/{commentId}`**

- 댓글 작성자만 삭제할 수 있다. 응답 `204`. 삭제된 댓글은 목록에서 제외한다.
- 오류: `403` 타인 댓글, `404` 댓글 없음·이미 삭제됨. 원본 글이 삭제되어도 자신의 남아 있는 댓글 삭제는 허용한다.

### 6.10 찜

**`PUT /posts/{postId}/favorite`**, **`DELETE /posts/{postId}/favorite`**

- 요청 본문 없음. 각각 찜 관계를 생성·제거하며 응답은 `204`다.
- 중복 찜 및 존재하지 않는 찜 취소도 `204`다. 자기 글 찜은 `403`이다.
- 삭제된 글의 신규 찜은 `404`이며 찜 취소는 글 존재 여부와 무관하게 `204`다.
- 거래완료 글도 찜할 수 있다.

**`GET /users/me/favorites?limit=20&cursor=...`**

- `Favorite.createdAt DESC, postId DESC` 정렬.
- 응답: `200`, `data: {items: FavoriteItem[], page}`.

### 6.11 채팅방

**`POST /chat/rooms`**

```json
{ "postId": "44444444-4444-4444-8444-444444444444" }
```

- 구매자는 현재 사용자, 판매자는 글 작성자로 결정한다.
- 신규 개설은 `201`, 같은 판매글의 기존 구매자 방 재사용은 `200`. 응답은 `data: ChatRoom`이다.
- 자기 글은 `403`, 삭제된 글은 `404`. 거래완료 글은 기존 방 재사용만 허용하고 신규 개설은 `409 INVALID_STATUS_TRANSITION`이다.
- 동시 개설 요청도 유니크 제약으로 하나의 방만 생성한다.

**`GET /chat/rooms?limit=20&cursor=...`**

- 참여 중인 방만 `COALESCE(lastMessageAt, createdAt) DESC, id DESC`로 반환한다.
- 응답: `200`, `data: {items: ChatRoom[], page}`.
- 판매글 삭제·거래완료 이후에도 기존 방은 목록에 남는다.

### 6.12 메시지 전송 및 이력 조회

**`POST /chat/rooms/{roomId}/messages`**

```json
{
  "clientMessageId": "55555555-5555-4555-8555-555555555555",
  "content": "오늘 오후 5시에 도서관 앞에서 거래 가능할까요?"
}
```

- 텍스트만 허용한다. 클라이언트는 메시지 작성 시 UUID를 생성하고 네트워크 재시도 시 같은 `clientMessageId`를 사용한다.
- 서버는 방별 증가하는 `sequence`를 메시지 저장 및 마지막 활동 갱신과 같은 트랜잭션에서 할당한다. 시간만으로 순서를 결정하지 않는다.
- 신규 저장 시 `201`, 동일 키·동일 내용 재전송 시 기존 메시지를 `200`으로 반환한다. 응답은 `data: Message`다.
- 동일 키로 다른 내용을 전송하면 `409 IDEMPOTENCY_CONFLICT`, 비참여자는 `404`다.

**`GET /chat/rooms/{roomId}/messages`**

| 쿼리 | 의미 |
| --- | --- |
| limit | 기본 20, 1~100 |
| cursor | 과거 메시지 페이지 조회용 커서 |
| afterSequence | 지정한 순번보다 큰 새 메시지 조회용 0 이상의 정수. cursor와 함께 사용 불가 |

- 기본 조회: 최신 메시지부터 `sequence DESC`로 반환한다. `page.nextCursor`로 더 오래된 메시지를 조회한다.
- 신규 조회: `afterSequence`보다 큰 메시지를 `sequence ASC`로 반환한다. 다음 요청은 마지막으로 받은 순번을 사용한다.
- 기본 응답: `data: {items: Message[], page}`.
- 신규 조회 응답: `data: {items: Message[], nextAfterSequence: integer, hasMore: boolean}`. 빈 결과라면 `nextAfterSequence`는 요청값을 유지한다.
- 신규 조회에 미수신 메시지가 더 있으면 `hasMore: true`다. 클라이언트는 이어서 조회한 뒤 일반 폴링으로 복귀한다.
- 메시지 ID로 중복을 제거하고 화면 표시는 `sequence ASC`로 통일한다. 폴링 주기는 활성 방에서 3~5초로 제안한다.

## 7. 대표 처리 흐름

### 7.1 회원가입 및 판매글 작성

1. `GET /auth/csrf` → 변경 요청용 토큰 확보.
2. `POST /auth/signup` → 회원 생성.
3. `POST /auth/login` → 세션 쿠키 확보.
4. `GET /categories`, `GET /trade-places` → 선택지 조회.
5. `POST /images` → 필요한 이미지 업로드.
6. `POST /posts` → 업로드 ID와 선택한 카테고리·장소를 포함하여 등록.

### 7.2 탐색 및 거래 협의

1. `GET /posts?q=전공책` → 검색 목록 조회.
2. `GET /posts/{postId}` → 상세 조회.
3. `POST /posts/{postId}/views` → 조회 이벤트 접수.
4. `PUT /posts/{postId}/favorite` → 필요 시 찜.
5. `POST /chat/rooms` → 판매자와 방 생성 또는 재사용.
6. 메시지 조회·전송 API → 거래 시간 및 장소 협의.
7. 판매자가 최신 버전으로 상태 변경 → RESERVED → SOLD.

## 8. 정합성·운영 요구사항

- 글 수정 시 `UPDATE ... WHERE id = ? AND version = ?`와 동등한 낙관적 잠금으로 충돌을 감지한다. 충돌 시 자동 덮어쓰기 대신 최신 내용을 다시 조회한다.
- 댓글 작성·찜·채팅방 개설·이미지 연결은 판매글 삭제와의 경합을 고려해 트랜잭션 내에서 글의 유효성을 확인한다.
- 찜 중복, 채팅방 중복, 메시지 중복은 애플리케이션 검사와 별개로 데이터베이스 고유 제약을 둔다.
- 메시지 저장과 방의 마지막 메시지 갱신은 함께 커밋한다. 실패한 메시지를 성공으로 응답하지 않는다.
- 로그인 실패는 계정과 IP 기준 각각 15분당 10회, 회원가입은 IP당 시간당 20회, 이미지 업로드는 사용자당 분당 20회, 메시지 전송은 사용자당 분당 60회로 제한하는 안을 제안한다.
- 세션·CSRF 토큰·비밀번호·메시지 본문은 일반 요청 로그에 기록하지 않는다. 로그에는 requestId, 경로, HTTP 상태, 처리 시간 및 필요한 최소 식별자만 남긴다.
- 인증 관련 응답·채팅·내 정보·내 찜 목록은 `Cache-Control: no-store`를 적용한다. 사용자별 `isFavorited`를 포함한 응답이 공유 캐시에 섞이지 않도록 한다.
- DB 백업·복구, 이미지 후처리 재시도, 오류율·지연·스토리지 사용량 모니터링을 운영 전에 마련한다.

## 9. 인수 기준

아래는 구현 후 수행할 검증 항목이며, 이 문서 작성 과정에서 실행된 테스트가 아니다.

| 검증 항목 | 기대 결과 |
| --- | --- |
| 동일 이메일로 동시 회원가입 | 회원 1명만 생성, 나머지는 409 |
| 로그아웃 후 폐기된 세션 재사용 | 회원 전용 API가 401 반환 |
| CSRF 토큰 없이 변경 요청 | 403 반환, 데이터 변경 없음 |
| 타인의 판매글 수정·삭제·상태 변경 | 403 반환 |
| 잘못된 카테고리·장소 또는 타인 이미지 연결 | 등록 실패, 부분 저장 없음 |
| 같은 버전으로 동시 글 수정 | 하나만 성공, 다른 요청은 409 |
| SOLD에서 SELLING으로 전이 | 409, 상태 유지 |
| 중복 찜 요청 및 반복 취소 | 관계·찜 수 중복 없음, 204 |
| 자신이 작성하지 않은 댓글 삭제 | 403 반환 |
| 동일 사용자·글의 24시간 내 조회 재전송 | 최초 한 번만 증가 |
| 판매글 GET 반복 조회 | 조회수 변화 없음 |
| 같은 구매자의 동시 채팅방 개설 | 같은 방 하나만 반환 |
| 비참여자의 메시지 조회·전송 | 404 반환, 정보 미노출 |
| 같은 clientMessageId 재전송 | 메시지 하나만 저장, 같은 ID 반환 |
| 메시지 순번 이후 신규 조회 및 과거 페이지 이동 | 정렬 계약 유지, 재전송은 ID로 중복 제거 가능 |
| 판매글 삭제 후 검색·찜·상세 접근 | 목록에서 제외, 상세 404 |
| 판매글 삭제 후 기존 채팅 조회·전송 | 참여자는 가능, 삭제 안내 표시 |
| 위장 파일·용량 초과 이미지 업로드 | 각각 415 또는 디코딩 오류 400, 용량은 413 |

## 10. 구현 전 확정할 항목

1. 학교 이메일 인증 필요 여부와 대상 학교.
2. 실제 카테고리 및 교내 장소 초기 데이터.
3. 거래완료 취소 허용 여부와 예약자·구매자 지정 필요 여부.
4. REST 폴링으로 시작할지 WebSocket 및 알림을 추가할지 여부.
5. 채팅·회원정보 보존 및 탈퇴·삭제 정책.
6. 배포 도메인, 이미지 저장소, 데이터베이스, 요청 제한 및 세션 만료 수치.

위 항목이 확정되지 않아도 현재 가정으로 구현할 수 있으나, 정책이 바뀌면 관련 API 및 인수 기준을 함께 갱신한다.
