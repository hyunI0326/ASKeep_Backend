"""Render Free의 512MiB 한도에서 시작 및 실제 임베딩 메모리를 확인한다."""
import resource
import sys

import main


def check_budget(stage):
    peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    peak_mib = peak / (1024 * 1024 if sys.platform == "darwin" else 1024)
    print(f"{stage}: peak RSS {peak_mib:.1f} MiB", flush=True)
    assert peak_mib < 512, f"{stage}: Render Free 512MiB memory limit exceeded"


check_budget("startup")
for text, expected in [
    ("query: 안녕하세요", [0, 41, 1294, 12, 107687, 2]),
    ("passage: Spring Boot와 FastAPI 연결", [0, 46692, 12, 38026, 58800, 2020, 25290, 74220, 70064, 2]),
    ("query: 😀 알 수 없는 문자 \u0378", [0, 41, 1294, 12, 21119, 15852, 1020, 19627, 149050, 6, 3, 2]),
]:
    assert main.tokenize_text(text)[0].tolist() == expected
assert main.tokenize_text("한국어 " * 1000).shape == (1, 512)
documents = main.create_document_embedding(["한국어 발표 자료의 핵심 내용을 설명합니다. " * 30] * 4)
query = main.create_query_embedding("발표 자료의 핵심 내용은 무엇인가요?")
assert len(documents) == 4 and all(len(vector.to_list()) == 384 for vector in documents)
assert len(query.to_list()) == 384
assert all(abs(sum(x * x for x in vector.to_list()) - 1) < 1e-5 for vector in [*documents, query])
check_budget("embedding")
