# ASKeep AI Server

ASKeep 프로젝트의 **자료 처리 및 RAG용 AI 서버**입니다.

FastAPI를 기반으로 사용자가 업로드한 PDF 자료를 텍스트로 추출하고, 일정 크기의 Chunk로 분할한 뒤 PostgreSQL에 저장합니다.

추후 `pgvector` 기반 Vector Search와 Gemini API를 연결하여 업로드된 자료를 기반으로 질문에 답변하는 RAG 구조로 확장할 예정입니다.

---

## Architecture

```text
Spring Boot
     │
     │ Material 정보 전달
     ▼
FastAPI
     │
     ├── PDF Text Extraction
     │
     ├── Chunking
     │
     └── PostgreSQL 저장
     │
     ▼
PostgreSQL + pgvector
```

향후 구조:

```text
사용자 질문
     ↓
Spring Boot
     ↓
FastAPI
     ↓
Query Embedding
     ↓
pgvector Similarity Search
     ↓
Relevant Chunks
     ↓
Gemini API
     ↓
AI Answer
```

---

# Tech Stack

- Python 3.13.13
- FastAPI
- PyMuPDF
- Pydantic
- PostgreSQL
- Psycopg 3
- pgvector
- ONNX Runtime + SentencePiece (INT8 multilingual-e5-small, 384차원)
- Uvicorn

향후 추가 예정:

- Gemini API

---

# Current Features

현재 구현된 기능입니다.

### FastAPI 서버

- FastAPI 기본 서버 구성
- Health Check API

### PDF Processing

- PDF 파일 읽기
- 페이지별 텍스트 추출
- 페이지 번호 유지

### Chunking

- 추출한 텍스트를 일정 크기로 분할
- Chunk 간 Overlap 적용
- Chunk Index 생성

기본 설정:

```text
chunk_size = 1000
overlap = 200
```

예:

```text
Chunk 0
0 ~ 1000

Chunk 1
800 ~ 1800

Chunk 2
1600 ~ 2600
```

### PostgreSQL

- FastAPI → PostgreSQL 연결
- `material_chunks` 테이블에 Chunk 저장
- pgvector extension 사용

---

# Project Structure

현재 기준 예시입니다.

```text
ai-server/
├── main.py
├── requirements.txt
├── .env
├── .env.example
├── .gitignore
└── README.md
```

추후 코드가 커지면 다음과 같이 분리할 예정입니다.

```text
ai-server/
├── app/
│   ├── main.py
│   │
│   ├── api/
│   │   ├── document.py
│   │   └── search.py
│   │
│   ├── service/
│   │   ├── document_service.py
│   │   ├── embedding_service.py
│   │   └── rag_service.py
│   │
│   └── database/
│       └── connection.py
│
├── requirements.txt
├── .env.example
└── README.md
```

---

# Environment Setup

## 1. Repository Clone

```bash
git clone <repository-url>
cd <project-directory>
```

작업 브랜치:

```bash
git checkout feature/material-ai
```

---

# 2. Python Environment

Python **3.13.13** 버전을 사용합니다. (`.python-version` 참고)

Python 가상환경 사용을 권장합니다.

```bash
python -m venv .venv
```

Mac / Linux:

```bash
source .venv/bin/activate
```

Windows:

```bash
.venv\Scripts\activate
```

---

# 3. Install Dependencies

```bash
pip install -r requirements.txt
```

현재 주요 dependency:

```text
fastapi
uvicorn
pymupdf
psycopg[binary]
pgvector
onnxruntime==1.30.0
sentencepiece
huggingface-hub
numpy
python-dotenv
```

`uv`를 사용하는 경우:

```bash
uv sync
```

또는 필요한 라이브러리를 직접 설치할 수 있습니다.

```bash
uv add fastapi python-multipart uvicorn pymupdf "psycopg[binary]" pgvector onnxruntime sentencepiece huggingface-hub numpy python-dotenv google-genai
```

---

# Environment Variables

실제 환경설정 값은 `.env`에서 관리합니다.

`.env` 파일은 Git에 업로드하지 않습니다.

`.env.example`을 복사합니다.

```bash
cp .env.example .env
```

`.env.example`

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=askeep
DB_USER=postgres
DB_PASSWORD=your_password

GEMINI_API_KEY=your_gemini_api_key
```

각자 자신의 환경에 맞게 `.env`를 수정합니다.

예:

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=askeep
DB_USER=postgres
DB_PASSWORD=password
```

`GEMINI_API_KEY`는 현재 구현에서는 사용하지 않으며 Gemini API 연동 단계에서 사용할 예정입니다.

---

# .gitignore

다음 파일은 Git에 업로드하지 않습니다.

```gitignore
.env
.venv/
__pycache__/
*.pyc
../.idea/
.DS_Store
```

특히 다음 정보는 절대로 Repository에 Commit하지 않습니다.

```text
DB Password
Gemini API Key
기타 Secret Key
```

---

# PostgreSQL Setup

PostgreSQL 서버가 실행 중이어야 합니다.

pgAdmin을 사용하는 경우 프로젝트 DB의 **Query Tool**에서 아래 SQL을 실행합니다.

## pgvector Extension

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

확인:

```sql
SELECT extname
FROM pg_extension;
```

결과에:

```text
vector
```

가 존재하면 정상입니다.

---

# material_chunks Table

```sql
CREATE TABLE IF NOT EXISTS material_chunks (
    id BIGSERIAL PRIMARY KEY,

    material_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,

    chunk_index INTEGER NOT NULL,

    content TEXT NOT NULL,

    page_number INTEGER,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Embedding 기능까지 적용하는 경우 다음 컬럼을 추가합니다.

```sql
ALTER TABLE material_chunks
ADD COLUMN embedding vector(384);
```

현재 사용할 Embedding 모델 기준 Vector Dimension은 `384`입니다.

---

# Database Configuration

DB 연결 정보는 코드에 직접 작성하지 않고 `.env`에서 읽습니다.

예:

```python
import os

import psycopg

from dotenv import load_dotenv
from pgvector.psycopg import register_vector


load_dotenv()


def get_connection():

    conn = psycopg.connect(
        host=os.getenv("DB_HOST"),
        port=int(os.getenv("DB_PORT", 5432)),
        dbname=os.getenv("DB_NAME"),
        user=os.getenv("DB_USER"),
        password=os.getenv("DB_PASSWORD")
    )

    register_vector(conn)

    return conn
```

---

# Run Server

FastAPI 서버 실행:

```bash
uvicorn main:app --reload --port 8000
```

또는:

```bash
python -m uvicorn main:app --reload --port 8000
```

정상적으로 실행되면:

```text
http://localhost:8000
```

에서 서버가 실행됩니다.

---

# Swagger

FastAPI Swagger UI:

```text
http://localhost:8000/docs
```

여기에서 API를 직접 테스트할 수 있습니다.

---

# API

## Health Check

```http
GET /health
```

Response:

```json
{
  "status": "ok"
}
```

FastAPI 서버가 정상적으로 실행되고 있는지 확인하는 API입니다.

---

# Document Processing

```http
POST /documents/process
```

PDF를 읽고 페이지별 텍스트를 추출한 뒤 Chunk로 분할하여 PostgreSQL에 저장합니다.

Request (`multipart/form-data`):

```bash
curl -X POST http://localhost:8000/documents/process \
  -F materialId=1 -F sessionId=1 -F file=@lecture.pdf
```

`materialId`와 `sessionId`는 양의 정수이며 `file`은 최대 50MB의 PDF입니다.
JSON `filePath` 요청은 더 이상 받지 않습니다. 확장자, PDF 헤더, 크기를 검증하고
수신한 파일 내용을 메모리에서 읽어 처리합니다. 텍스트가 없는 PDF는 422를 반환합니다.

처리 과정:

```text
PDF
 ↓
PyMuPDF
 ↓
페이지별 Text 추출
 ↓
Chunking
 ↓
material_chunks
 ↓
PostgreSQL 저장
```

Response 예:

```json
{
  "materialId": 1,
  "status": "success",
  "chunkCount": 24
}
```

---

# PDF Extraction

PDF 처리는 `PyMuPDF(fitz)`를 사용합니다.

```python
def extract_pdf(pdf: bytes):
    pages = []
    with fitz.open(stream=pdf, filetype="pdf") as doc:
        for page_number, page in enumerate(doc):
            pages.append({
                "pageNumber": page_number + 1,
                "text": page.get_text()
            })
    return pages
```

페이지 번호를 같이 저장하여 추후 AI 답변의 출처를 표시할 수 있도록 설계했습니다.

예:

```text
lecture.pdf p.7
```

---

# Chunking

PDF에서 추출한 텍스트는 다음 함수로 분할합니다.

```python
def split_text(
    text: str,
    chunk_size=1000,
    overlap=200
):

    chunks = []

    start = 0

    while start < len(text):

        end = start + chunk_size

        chunks.append(
            text[start:end]
        )

        start += chunk_size - overlap

    return chunks
```

Overlap을 적용하는 이유는 Chunk가 나뉘는 경계에서 문맥이 완전히 끊어지는 것을 줄이기 위해서입니다.

---

# material_chunks Data

저장되는 데이터 예:

```text
id
material_id
session_id
chunk_index
page_number
content
embedding
created_at
```

예:

```text
material_id : 1
session_id  : 3
chunk_index : 7
page_number : 4

content:
"Spring Boot에서는 자동 설정 기능을 통해..."
```

하나의 Material은 여러 개의 Chunk를 가질 수 있습니다.

```text
Material
   │
   ├── Chunk 0
   ├── Chunk 1
   ├── Chunk 2
   └── Chunk 3
```

---

# Spring Boot Integration

최종 구조에서는 Frontend가 FastAPI를 직접 호출하지 않습니다.

```text
Frontend
    ↓
Spring Boot
    ↓
FastAPI
```

Spring Boot가 Material을 저장한 후 `materialId`, `sessionId`, PDF 파일 `file`을
multipart로 전달합니다. 업로드와 실패 후 재시도가 같은 전송 경로를 사용합니다.

FastAPI는 해당 자료를 처리하여 `material_chunks`에 저장합니다.

---

# Important: File Transfer

FastAPI는 Spring의 파일 경로에 접근하지 않습니다. 두 서버가 서로 다른 머신이나
Render 서비스여도 PDF 파일을 HTTP로 전달하므로 공유 디스크 없이 처리할 수 있습니다.
재시도를 위해 Spring은 원본 PDF를 보관해야 합니다. Spring의 저장 파일이 재배포 시
사라지는 환경에서는 영속 디스크 또는 별도 파일 저장소가 필요합니다.

수신 검증 테스트(DB/Gemini/임베딩 모델 다운로드 불필요):

```bash
python test_document_upload.py
```

---

# Development Roadmap

현재:

```text
✅ FastAPI 기본 서버

✅ Health Check

✅ PostgreSQL 연결

✅ PDF Text Extraction

✅ Chunking

✅ material_chunks 저장

✅ pgvector 환경 설정
```

다음 단계:

```text
Embedding 생성
        ↓
pgvector 저장
        ↓
Query Embedding
        ↓
Vector Similarity Search
        ↓
Relevant Chunk TOP-K
        ↓
Gemini API
        ↓
RAG Answer 생성
```

그 이후:

```text
FastAPI Background Task

        ↓

Spring Callback

        ↓

PENDING
PROCESSING
COMPLETED
FAILED

        ↓

Frontend Polling

        ↓

추후 WebSocket
```

---

# Branch

현재 AI / Material 관련 개발은 다음 Feature Branch에서 진행합니다.

```text
feature/material-ai
```

작업 완료 후:

```text
feature/material-ai
        ↓
      develop
```

방향으로 Pull Request를 생성합니다.

---

# Commit Example

```bash
git add .

git commit -m "feat: implement PDF chunk processing and pgvector setup"

git push origin feature/material-ai
```

---

# Notes

현재 단계에서는 AI 답변 생성보다 먼저 다음 흐름을 안정적으로 동작시키는 것을 목표로 합니다.

```text
PDF
 ↓
Text Extraction
 ↓
Chunking
 ↓
PostgreSQL
```

이후:

```text
Chunk
 ↓
Embedding
 ↓
pgvector
 ↓
Gemini API
```

를 순차적으로 연결할 예정입니다.
