from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import fitz
import psycopg
from sentence_transformers import SentenceTransformer
from pgvector.psycopg import register_vector
from pgvector import Vector
import os
from dotenv import load_dotenv

class DocumentRequest(BaseModel) :
    materialId : int
    sessionId : int
    filePath : str

class SearchRequest(BaseModel):
    sessionId: int
    query: str
    limit: int = 5

# page별 text 분할
def extract_pdf(file_path: str):
    doc = fitz.open(file_path)
    pages = []
    for page_number, page in enumerate(doc):
        text = page.get_text()
        pages.append({
            "pageNumber" : page_number+1,
            "text" : text
        })
    doc.close()
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

#chunk 저장
def save_chunks(chunks):
    texts = [
        chunk["text"]
        for chunk in chunks
    ]

    embeddings = create_document_embedding(texts)

    with get_connection() as conn:
        with conn.cursor() as cursor:
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

#embedding 함수
def create_document_embedding(texts:list[str]):
    passages = [
        f"passage : {text}"
        for text in texts
    ]
    embeddings = model.encode(
        passages,
        normalize_embeddings=True
    )
    return [
        Vector(embedding.tolist())
        for embedding in embeddings
    ]

#질문용 embedding 함수
def create_query_embedding(query:str):
    embedding = model.encode(
        f"query:{query}",
        normalize_embeddings=True
    )
    return Vector(embedding.tolist())

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
                    1-(embedding <=> %s) as similarity,
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



app = FastAPI()
model = SentenceTransformer("intfloat/multilingual-e5-small")
load_dotenv()

#db 연결
def get_connection():
    conn = psycopg.connect(
        host=os.getenv("DB_HOST"),
        port=os.getenv("DB_PORT", 5432),
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
def process_document(request: DocumentRequest):
    try:
        pages = extract_pdf(request.filePath)
        result_chunk = []
        chunk_index = 0
        for page in pages:
            chunks = split_text(page["text"])
            for chunk in chunks:
                result_chunk.append({
                    "materialId": request.materialId,
                    "sessionId": request.sessionId,
                    "pageNumber": page["pageNumber"],
                    "chunkIndex": chunk_index,
                    "text": chunk
                })
                chunk_index+=1

        save_chunks(result_chunk)

        return {
            "status": "success",
            "chunkCount": len(result_chunk)
        }

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