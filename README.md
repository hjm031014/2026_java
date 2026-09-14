# 교내 중고거래 웹 플랫폼

> 학생들이 교내에서 중고거래를 용이하게 할 수 있도록 돕는 웹 플랫폼

[![React](https://img.shields.io/badge/React-Frontend-61DAFB?logo=react&logoColor=white)](#기술-스택)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-Backend-6DB33F?logo=springboot&logoColor=white)](#기술-스택)
[![Java](https://img.shields.io/badge/Java-Backend-ED8B00?logo=openjdk&logoColor=white)](#기술-스택)
[![JWT](https://img.shields.io/badge/JWT-Auth-000000?logo=jsonwebtokens&logoColor=white)](#기술-스택)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-DB-4169E1?logo=postgresql&logoColor=white)](#기술-스택)
[![Render](https://img.shields.io/badge/Render-Server-000000?logo=render&logoColor=white)](#기술-스택)
[![Vercel](https://img.shields.io/badge/Vercel-Frontend-000000?logo=vercel&logoColor=white)](#기술-스택)
[![Neon](https://img.shields.io/badge/Neon-Database-00E599?logo=neon&logoColor=white)](#기술-스택)

**개발 마감: 10월 5일**

## 팀 구성

| 담당 | 팀원 |
|---|---|
| 프론트엔드 (2명) | 임동하 · 이선호 |
| 백엔드 (3명) | 홍정민 · 박종호 · 홍태겸 |
| DB & 서버 (1명) | 박시연 |

## 기술 스택

| 영역 | 기술 및 호스팅 |
|---|---|
| 프론트엔드 | React → Vercel |
| 백엔드 | Java · Spring Boot → Render |
| DB | PostgreSQL → Neon |
| 인증 | JWT 액세스 토큰 + 리프레시 토큰 |
| 이미지 저장 | Cloudinary |

## 주요 기능

- **회원:** 회원가입, 로그인, 로그아웃
- **판매글:** 등록·조회·수정·삭제, 이미지, 카테고리, 검색·정렬, 조회수
- **거래:** 판매중 ↔ 예약중 → 거래완료, 교내 거래 희망 장소 선택
- **소통:** 댓글 작성·삭제, 찜·취소·목록, 판매자와 1:1 채팅

## 문서

- [기능 명세서](FEATURES.md): 회원·판매글·거래·소통 기능과 처리 규칙
- [API 명세서](API.md): 인증 방식, API 목록, 요청 필드 및 처리 규칙
