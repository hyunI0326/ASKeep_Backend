# 업로드 경로 검증 및 청크 중복 저장 방지

작성일: 2026-09-27

`/documents/process` 엔드포인트의 보안 문제와 데이터 중복 문제를 수정한 내용과, 그 검증 결과를 정리한다.

---

## 1. 수정 내용

### 1-1. filePath 경로 검증

**문제**

`filePath`를 요청에서 받은 그대로 `fitz.open()`에 넘기고 있었다. 요청을 보낼 수 있는 사람이면 서버 내부의 임의 경로(`/etc/passwd`, `../.env` 등)를 열도록 시도할 수 있었다(path traversal). 에러 메시지도 그대로 응답에 담겨 파일 존재 여부가 노출될 수 있었다.

**수정**

`resolve_upload_path()`를 추가하고, PDF를 열기 전에 반드시 거치도록 했다.

- `UPLOAD_DIR`(환경변수, 기본값 `./uploads`) 밖의 경로는 400 `허용되지 않은 경로입니다.`
- 확장자가 `.pdf`가 아니거나 존재하지 않는 파일은 400 `PDF 파일이 아닙니다.`
- `Path.resolve()`로 `../` 등을 실제 경로로 풀어낸 뒤 비교하므로 상대경로 우회가 통하지 않는다.
- `process_document`의 `except Exception`이 400을 500으로 바꾸지 않도록 `except HTTPException: raise`를 추가했다.

**설정**

`.env`에 `UPLOAD_DIR`을 지정해야 한다. 값은 Spring 서버가 파일을 저장하는 위치와 같아야 한다. `.env.example`에 항목을 추가했다.

### 1-2. 같은 자료 재처리 시 청크 중복 저장 방지

**문제**

`/documents/process`가 호출될 때마다 `material_chunks`에 행을 추가만 했다. 같은 `materialId`로 재호출(재시도, 더블 클릭, 재처리)되면 청크가 중복 저장되어, 검색 결과 상위를 같은 내용이 차지하고 LLM에 반복된 문맥이 들어가며 데이터가 계속 쌓인다.

**수정**

`save_chunks(chunks, material_id)`가 INSERT 전에 같은 `material_id`의 기존 청크를 삭제한다. 삭제와 삽입이 같은 트랜잭션이라 중간에 실패하면 둘 다 롤백되어 기존 데이터가 유지된다.

---

## 2. 변경 파일

| 파일 | 내용 |
|---|---|
| `main.py` | `resolve_upload_path` 추가, `save_chunks` 시그니처 변경(`material_id` 추가) 및 삭제 로직, `process_document` 예외 처리 |
| `.env.example` | `UPLOAD_DIR=./uploads` 추가 |

---

## 3. 테스트 결과

테스트 환경: 로컬, Python 3.13.5, Docker의 `pgvector/pgvector:pg16`, README의 `material_chunks` 스키마 적용.
테스트 자료: PDF 1개 (약 1.2MB).

| 테스트 | 결과 |
|---|---|
| `/health` | 200 `{"status":"ok"}` |
| 경로 조작 `filePath=/etc/passwd` | 400 `허용되지 않은 경로입니다.` |
| 상대경로 우회 `<UPLOAD_DIR>/../.env` | 400 `허용되지 않은 경로입니다.` |
| 경로 검증 함수 단위 확인 | 정상 PDF 허용, `.txt`/없는 파일/폴더 밖 경로 거부 |
| PDF 처리 1회차 | 200, 76개 청크 저장, 76개 모두 임베딩 저장 |
| 같은 `materialId` 2회차 | 200, DB 행 수 76개 유지(`chunk_index` 0~75), 중복 없음 |
| 벡터 검색 (질문: "클래스란 무엇인가") | 관련 페이지가 유사도 순으로 반환 (0.851, 0.844, 0.841) |
| 다른 `sessionId`(999) 검색 | 결과 0건, 세션 분리 정상 |

---

## 4. 확인하지 못한 것 / 남은 일

- **`/ai/answer` (Gemini 답변)는 테스트하지 못했다.** 사용한 API 키의 프로젝트가 선불 크레딧 소진 상태(402)였다.
- 모델명 `gemini-3.8-flash`는 모델 목록에 존재하는 것을 확인했다. `gemini-2.5-flash`, `gemini-2.5-flash-lite`는 신규 사용자에게 제공되지 않아 404였다. 이 PR은 모델명을 변경하지 않는다.
- **`UPLOAD_DIR`의 실제 값은 Spring 서버의 파일 저장 위치와 맞춰야 한다.** 값이 다르면 모든 요청이 400이 된다.
- **파일 전달 방식은 배포 방식에 따라 재검토가 필요하다.** 현재 구조는 두 서버가 같은 디스크를 본다는 전제이다. Docker 컨테이너를 분리하거나 S3 등 Object Storage를 쓰면 공유 볼륨 설정 또는 다운로드 방식으로 바꿔야 한다(README의 `Important: File Path` 참고).
- `UNIQUE (material_id, chunk_index)` 제약은 추가하지 않았다. 스키마 변경이라 팀 논의가 필요하다.

---

## 5. 추가로 검토할 만한 개선 (이번 PR 범위 밖)

- 청크 INSERT 배치화(`executemany`), DB 커넥션 풀
- 유사도 하한(threshold) 도입
- 문장 단위 청킹
- 429 등 Gemini 에러 처리와 타임아웃, 모델명 환경변수화
- `session_id` 및 HNSW 벡터 인덱스
