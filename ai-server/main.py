import numpy as np
import onnxruntime as ort
import sentencepiece as spm
from huggingface_hub import snapshot_download
import os
from dotenv import load_dotenv
from pathlib import Path
from threading import Lock

# Load ONNX before the HTTP/PDF clients to avoid overlapping startup allocations.
load_dotenv()
model_dir = Path(snapshot_download(
    "intfloat/multilingual-e5-small",
    revision="614241f622f53c4eeff9890bdc4f31cfecc418b3",
    allow_patterns=["onnx/model_qint8_avx512_vnni.onnx", "sentencepiece.bpe.model"],
    cache_dir=os.getenv("HF_HOME"), local_files_only=os.getenv("HF_HUB_OFFLINE") == "1",
))
session_options = ort.SessionOptions()
session_options.intra_op_num_threads = 1
session_options.inter_op_num_threads = 1
session_options.enable_cpu_mem_arena = False
model = ort.InferenceSession(str(model_dir / "onnx/model_qint8_avx512_vnni.onnx"),
                             sess_options=session_options, providers=["CPUExecutionProvider"])
tokenizer = spm.SentencePieceProcessor(model_file=str(model_dir / "sentencepiece.bpe.model"))
# ponytail: serialize CPU embedding to cap memory; use a larger AI instance for concurrent inference.
embedding_lock = Lock()

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from pydantic import BaseModel
import pymupdf as fitz
import psycopg
from pgvector.psycopg import register_vector
from pgvector import Vector
from google import genai
from google.genai.errors import ServerError
import time
import json
import re
from typing import List, Optional


class QuestionRequest(BaseModel):
    sessionId : int
    question : str

class SearchRequest(BaseModel):
    sessionId: int
    query: str
    limit: int = 5

#세션 요약 요청 (Spring이 세션 종료 시 호출)
class SummaryQuestion(BaseModel):
    question: str
    answers: List[str] = []

class SummaryRequest(BaseModel):
    sessionId: int
    title: str
    description: Optional[str] = None
    questions: List[SummaryQuestion] = []

# page별 text 분할
def extract_pdf(pdf: bytes):
    pages = []
    with fitz.open(stream=pdf, filetype="pdf") as doc:
        for page_number, page in enumerate(doc):
            pages.append({
                "pageNumber": page_number + 1,
                "text": page.get_text()
            })
    return pages

#text를 chunk로 분할
def split_text(text:str, chunk_size=1000, overlap=200):
    chunks = []
    start = 0
    while start<len(text):
        end = start + chunk_size
        chunks.append(text[start:end])
        start+=chunk_size-overlap
    return chunks

#chunk 저장 (같은 material_id의 기존 chunk는 삭제 후 다시 저장)
def save_chunks(chunks, material_id: int):
    texts = [
        chunk["text"]
        for chunk in chunks
    ]

    embeddings = create_document_embedding(texts)

    with get_connection() as conn:
        with conn.cursor() as cursor:
            cursor.execute(
                "DELETE FROM material_chunks WHERE material_id = %s",
                (material_id,)
            )
            for chunk, embedding in zip(chunks, embeddings):
                cursor.execute(
                    """
                    INSERT INTO material_chunks (
                        material_id,
                        session_id,
                        chunk_index,
                        content,
                        page_number,
                        embedding
                    )
                    VALUES (%s, %s, %s, %s, %s, %s)
                    """,
                    (
                        chunk["materialId"],
                        chunk["sessionId"],
                        chunk["chunkIndex"],
                        chunk["text"],
                        chunk["pageNumber"],
                        embedding
                    )
                )
        conn.commit()

# XLM-R의 SentencePiece ID를 E5 vocabulary에 맞추고 512토큰으로 제한한다.
def tokenize_text(text: str):
    special = {"<s>": 0, "<pad>": 1, "</s>": 2, "<unk>": 3, "<mask>": tokenizer.vocab_size() + 1}
    ids = []
    for part in re.split(r"(<s>|</s>|<pad>|<unk>|<mask>)", text):
        if part in special:
            ids.append(special[part])
        else:
            ids.extend(piece + 1 if piece else 3 for piece in tokenizer.encode(part))
    return np.array([[0, *ids[:510], 2]], dtype=np.int64)


def embed_text(text: str):
    with embedding_lock:
        ids = tokenize_text(text)
        hidden = model.run(None, {"input_ids": ids, "attention_mask": np.ones_like(ids),
                                  "token_type_ids": np.zeros_like(ids)})[0]
        embedding = hidden[0].mean(axis=0)
        return Vector((embedding / max(float(np.linalg.norm(embedding)), 1e-12)).tolist())


#embedding 함수
def create_document_embedding(texts:list[str]):
    return [embed_text(f"passage: {text}") for text in texts]

#질문용 embedding 함수
def create_query_embedding(query:str):
    return embed_text(f"query: {query}")

#vector search 함수
def search_similar_chunks(
        session_id: int,
        query: str,
        limit: int=5
):
    query_embedding = create_query_embedding(query)
    with get_connection() as conn:
        with conn.cursor() as cursor:
            cursor.execute(
                """
                SELECT
                    id,
                    material_id,
                    chunk_index,
                    page_number,
                    content,
                    1-(embedding <=> %s) as similarity
                FROM material_chunks
                where session_id = %s
                    AND embedding IS NOT NULL
                ORDER BY embedding <=> %s
                LIMIT %s
                """,
                (
                    query_embedding,
                    session_id,
                    query_embedding,
                    limit
                )
            )
            rows = cursor.fetchall()

    return rows

#context 생성
def build_context(rows):
    contexts = []
    for row in rows:
        material_id = row[1]
        page_number=row[3]
        content=row[4]
        context = f"""
[자료]
material_id : {material_id}
page : {page_number}

{content}
"""
        contexts.append(context)

    return "\n".join(contexts)

#Gemini에 context + 질문 전달
def generate_answer(question:str, context:str):
    prompt = f"""
너는 사용자가 업로드한 자료를 기반으로 질문에 답변하는 AI야.

아래 [자료]만을 근거로 [질문]에 답변해.

규칙:
1. 제공된 자료에 있는 내용을 우선 사용한다.
2. 사용자가 이해하기 쉽게 설명한다.
3. 필요하지 않은 내용을 과도하게 추가하지 않는다.
4. 답변 본문은 Markdown 형식으로 작성하고, 내용에 맞게 제목, 목록, 굵은 강조를 사용한다.
5. 답변 본문만 출력하며, 본문 전체를 Markdown 코드 블록으로 감싸지 않는다.

[자료]
{context}

[질문]
{question}
"""
    for attempt in range(4):
        try:
            response = gemini_client.models.generate_content(
                model="gemini-3.8-flash",
                contents=prompt
            )

            return response.text
        except ServerError as e:
            if e.code == 503 and attempt < 3:
                delay = 2**attempt
                print(f"Gemini 서버 혼잡, {delay}초 후 재시도...")
                time.sleep(delay)
            else:
                raise

#rag 전체 함수
def rag_answer(session_id:int, question:str):
    rows = search_similar_chunks(session_id=session_id, query=question, limit=5)
    if not rows:
        return {
            "answer": "관련 자료를 찾을 수 없습니다.",
            "sources": []
        }
    context = build_context(rows)
    answer = generate_answer(question=question, context=context)
    sources=[]
    for row in rows:
        sources.append({
            "chunkId":row[0],
            "materialId": row[1],
            "chunkIndex": row[2],
            "pageNumber": row[3],
            "similarity": float(row[5])
        })
    return {
        "answer":answer,
        "sources":sources
    }


app = FastAPI()
gemini_client=genai.Client(
    api_key=os.getenv("GEMINI_API_KEY")
)

#db 연결
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

# 확인
@app.get("/health")
def health() :
    return {
        "status" : "ok"
    }


@app.post("/documents/process")
def process_document(
    materialId: int = Form(..., gt=0),
    sessionId: int = Form(..., gt=0),
    file: UploadFile = File(...),
):
    try:
        if not file.filename or not file.filename.lower().endswith(".pdf"):
            raise HTTPException(status_code=415, detail="PDF 파일만 업로드할 수 있습니다.")
        pdf = file.file.read(50 * 1024 * 1024 + 1)
        if len(pdf) > 50 * 1024 * 1024:
            raise HTTPException(status_code=413, detail="파일은 50MB 이하여야 합니다.")
        if not pdf.startswith(b"%PDF-"):
            raise HTTPException(status_code=415, detail="유효한 PDF 파일이 아닙니다.")
        pages = extract_pdf(pdf)
        result_chunk = []
        chunk_index = 0
        for page in pages:
            text = page["text"]
            if not text.strip():
                continue
            chunks = split_text(text)
            for chunk in chunks:
                result_chunk.append({
                    "materialId": materialId,
                    "sessionId": sessionId,
                    "pageNumber": page["pageNumber"],
                    "chunkIndex": chunk_index,
                    "text": chunk
                })
                chunk_index+=1

        if not result_chunk:
            raise HTTPException(status_code=422, detail="PDF에서 추출할 텍스트가 없습니다.")
        save_chunks(result_chunk, materialId)

        return {
            "materialId": materialId,
            "status": "success",
            "chunkCount": len(result_chunk)
        }

    except HTTPException:
        raise
    except fitz.FileDataError as e:
        raise HTTPException(status_code=400, detail="PDF 파일을 읽을 수 없습니다.") from e
    except Exception as e:
        print("ERROR:", type(e).__name__, str(e))
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/search")
def search(request:SearchRequest):
    rows = search_similar_chunks(
        request.sessionId,
        request.query,
        request.limit
    )
    results = []

    for row in rows:
        results.append({
            "chunkId": row[0],
            "materialId":row[1],
            "chunkIndex":row[2],
            "pageNumber":row[3],
            "content":row[4],
            "similarity":float(row[5])
        })

    return  {
        "query": request.query,
        "results": results
    }


# Gemini test
# @app.get("/gemini-test")
# def gemini_test():
#     response=gemini_client.models.generate_content(
#         model='gemini-3.8-flash',
#         contents="Spring boot를 한 문장으로 설명해줘"
#     )
#     return {
#         "answer":response.text
#     }

@app.post("/ai/answer")
def ai_answer(request:QuestionRequest):
    result = rag_answer(session_id=request.sessionId, question=request.question)
    return {
        "sessionId": request.sessionId,
        "question" : request.question,
        "answer" : result["answer"],
        "sources" : result["sources"]
    }


#세션 요약 + 태그 생성 (Gemini, JSON 응답)
def generate_summary(request: SummaryRequest):
    qa_lines = []
    for i, item in enumerate(request.questions, start=1):
        qa_lines.append(f"Q{i}. {item.question}")
        for answer in item.answers:
            qa_lines.append(f"  - {answer}")
    qa_text = "\n".join(qa_lines) if qa_lines else "(질문 없음)"

    prompt = f"""
너는 발표/스터디 세션이 끝난 뒤 내용을 정리하는 AI야.

아래 [세션 정보]와 [질문과 답변]만을 근거로 세션을 요약하고 태그를 만들어.

규칙:
1. summary: 세션에서 다룬 핵심 내용과 주요 질문을 한국어 3~6문장으로 요약한다.
2. 질문이 없으면 세션 제목과 설명만으로 1~2문장으로 짧게 요약한다.
3. tags: 세션 주제를 나타내는 키워드 3~7개 (각 20자 이내, '#' 없이).
4. 주어진 내용에 없는 사실을 지어내지 않는다.
5. 반드시 {{"summary": "...", "tags": ["...", "..."]}} 형식의 JSON만 출력한다.
6. summary 문자열의 본문은 Markdown 형식으로 작성하고, 내용에 맞게 제목, 목록, 굵은 강조를 사용한다.
7. JSON 전체를 코드 블록으로 감싸지 않고, summary의 줄바꿈은 JSON 규칙에 맞게 이스케이프한다. tags는 일반 문자열로 유지한다.

[세션 정보]
제목: {request.title}
설명: {request.description or "(없음)"}

[질문과 답변]
{qa_text}
"""
    for attempt in range(4):
        try:
            response = gemini_client.models.generate_content(
                model="gemini-3.8-flash",
                contents=prompt,
                config={"response_mime_type": "application/json"}
            )
            data = json.loads(response.text)
            summary = str(data.get("summary", "")).strip()
            tags = [str(tag).strip() for tag in data.get("tags", []) if str(tag).strip()]
            if not summary:
                raise ValueError("요약이 비어 있습니다.")
            return {"summary": summary, "tags": tags}
        except ServerError as e:
            if e.code == 503 and attempt < 3:
                delay = 2**attempt
                print(f"Gemini 서버 혼잡, {delay}초 후 재시도...")
                time.sleep(delay)
            else:
                raise


@app.post("/sessions/summary")
def session_summary(request: SummaryRequest):
    try:
        result = generate_summary(request)
        return {
            "sessionId": request.sessionId,
            "summary": result["summary"],
            "tags": result["tags"]
        }
    except Exception as e:
        print("ERROR:", type(e).__name__, str(e))
        raise HTTPException(status_code=500, detail=str(e))
