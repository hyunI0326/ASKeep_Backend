"""PDF 처리와 JSON 응답 계약을 검증한다. DB 저장과 외부 AI만 대체한다."""
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import pymupdf as fitz
from fastapi.testclient import TestClient

spec = importlib.util.spec_from_file_location("ai_upload_test", Path(__file__).with_name("main.py"))
module = importlib.util.module_from_spec(spec)
with patch("huggingface_hub.snapshot_download", return_value="/unused-model"), \
     patch("onnxruntime.InferenceSession"), patch("sentencepiece.SentencePieceProcessor"), \
     patch("openai.OpenAI"), patch("dotenv.load_dotenv"):
    spec.loader.exec_module(module)


class DocumentUploadTest(unittest.TestCase):
    def test_evidence_threshold_skips_openai_and_filters_context(self):
        with TestClient(module.app) as client, patch.object(module, "search_similar_chunks") as search, \
             patch.object(module, "generate_answer", return_value="자료 기반 답변") as generate:
            for rows in ([], [(1, 7, 0, 2, "무관한 내용", 0.799999)]):
                search.return_value = rows
                response = client.post("/ai/answer", json={"sessionId": 3, "question": "핵심 내용은?"})
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json(), {"sessionId": 3, "question": "핵심 내용은?",
                    "status": "NO_EVIDENCE", "answer": "자료에서 질문에 대한 근거를 찾을 수 없습니다.", "sources": []})
                generate.assert_not_called()
            search.return_value = [(1, 7, 0, 2, "근거가 되는 내용", 0.8),
                                   (2, 8, 0, 3, "제외할 내용", 0.79)]
            response = client.post("/ai/answer", json={"sessionId": 3, "question": "핵심 내용은?"})
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json()["status"], "COMPLETED")
            self.assertEqual(response.json()["answer"], "자료 기반 답변")
            self.assertEqual([s["chunkId"] for s in response.json()["sources"]], [1])
            generate.assert_called_once()
            self.assertIn("근거가 되는 내용", generate.call_args.kwargs["context"])
            self.assertNotIn("제외할 내용", generate.call_args.kwargs["context"])
            search.side_effect = RuntimeError("DB unavailable")
            with self.assertRaises(RuntimeError):
                client.post("/ai/answer", json={"sessionId": 3, "question": "핵심 내용은?"})

    def test_multipart_pdf_and_validation(self):
        with fitz.open() as doc:
            for text in ("PDF transfer test", "Second page"):
                doc.new_page().insert_text((72, 72), text)
            pdf = doc.tobytes()
        with TestClient(module.app) as client, patch.object(module, "save_chunks") as save:
            response = client.post("/documents/process", data={"materialId": "7", "sessionId": "3"},
                                   files={"file": ("lecture.pdf", pdf, "application/pdf")})
            self.assertEqual(response.status_code, 200, response.text)
            self.assertEqual(response.json(), {"materialId": 7, "status": "success", "chunkCount": 2, "pageCount": 2})
            chunks, material_id = save.call_args.args
            self.assertEqual(material_id, 7)
            self.assertEqual([c["pageNumber"] for c in chunks], [1, 2])
            self.assertEqual([c["chunkIndex"] for c in chunks], [0, 1])
            self.assertTrue(all(c["materialId"] == 7 and c["sessionId"] == 3 for c in chunks))
            self.assertIn("PDF transfer test", chunks[0]["text"])
            save.reset_mock()
            with fitz.open() as blank:
                blank.new_page()
                blank_pdf = blank.tobytes()
            invalid = [
                ("lecture.txt", pdf, 415),
                ("lecture.pdf", b"not a PDF", 415),
                ("lecture.pdf", b"", 415),
                ("lecture.pdf", b"%PDF-1.7\nbroken", 400),
                ("lecture.pdf", blank_pdf, 422),
                ("lecture.pdf", b"%PDF-" + b"x" * (50 * 1024 * 1024 - 4), 413),
            ]
            for filename, content, status in invalid:
                with self.subTest(filename=filename, status=status):
                    result = client.post("/documents/process", data={"materialId": "7", "sessionId": "3"},
                                         files={"file": (filename, content, "application/pdf")})
                    self.assertEqual(result.status_code, status, result.text)
            self.assertEqual(client.post("/documents/process", json={
                "materialId": 7, "sessionId": 3, "filePath": "/etc/passwd"}).status_code, 422)
            self.assertEqual(client.post("/documents/process", data={"materialId": "0", "sessionId": "3"},
                                         files={"file": ("lecture.pdf", pdf)}).status_code, 422)
            self.assertEqual(client.post("/documents/process", data={"materialId": "7", "sessionId": "3"}).status_code, 422)
            save.assert_not_called()
            save.side_effect = RuntimeError("storage unavailable")
            self.assertEqual(client.post("/documents/process", data={"materialId": "7", "sessionId": "3"},
                                         files={"file": ("lecture.pdf", pdf)}).status_code, 500)

    def test_slide_images_include_blank_pages_and_bound_resolution(self):
        with fitz.open() as doc:
            doc.new_page(width=10000, height=5000).insert_text((72, 72), "First slide")
            doc.new_page(width=800, height=600)
            pdf = doc.tobytes()
        with TestClient(module.app) as client, patch.object(module, "save_chunks") as save:
            response = client.post("/documents/page-count", files={"file": ("slides.pdf", pdf)})
            self.assertEqual(response.json(), {"pageCount": 2})
            images = []
            for number in (1, 2):
                response = client.post("/documents/render-page", data={"pageNumber": number},
                                       files={"file": ("slides.pdf", pdf)})
                self.assertEqual(response.status_code, 200, response.text)
                self.assertEqual(response.headers["content-type"], "image/png")
                self.assertTrue(response.content.startswith(b"\x89PNG\r\n\x1a\n"))
                with fitz.open(stream=response.content, filetype="png") as image:
                    pixmap = image[0].get_pixmap()
                    self.assertLessEqual(max(pixmap.width, pixmap.height), 1600)
                images.append(response.content)
            self.assertNotEqual(images[0], images[1])
            for number, status in ((0, 422), (-1, 422), (3, 404)):
                response = client.post("/documents/render-page", data={"pageNumber": number},
                                       files={"file": ("slides.pdf", pdf)})
                self.assertEqual(response.status_code, status)
            for route in ("/documents/page-count", "/documents/render-page"):
                for content, expected in ((b"not a PDF", 415), (b"%PDF-1.7\nbroken", 400)):
                    response = client.post(route, data={"pageNumber": 1}, files={"file": ("slides.pdf", content)})
                    self.assertEqual(response.status_code, expected)
            save.assert_not_called()


class MarkdownResponseTest(unittest.TestCase):
    def test_markdown_text_stays_inside_json_fields(self):
        markdown = '## 핵심 내용\n\n- **중요**: "답변"\n\n```python\nprint("hello")\n```'
        generate = module.openai_client.responses.create
        with TestClient(module.app) as client, patch.object(module, "search_similar_chunks", return_value=[
            (1, 7, 0, 2, "자료 내용", 0.9)
        ]):
            generate.return_value = SimpleNamespace(output_text=markdown)
            response = client.post("/ai/answer", json={"sessionId": 3, "question": "핵심 내용은?"})
            self.assertEqual(response.status_code, 200)
            self.assertIn("application/json", response.headers["content-type"])
            body = response.json()
            self.assertEqual(set(body), {"sessionId", "question", "answer", "sources", "status"})
            self.assertEqual(body["status"], "COMPLETED")
            self.assertEqual(body["answer"], markdown)
            self.assertEqual(body["sources"][0]["materialId"], 7)

            sections = [{"title": '핵심 "내용"', "description": markdown},
                        {"title": "다음 단계", "description": "추가 질문을 정리합니다."}]
            generate.return_value = SimpleNamespace(output_text=json.dumps({"sections": sections, "tags": ["AI", "PDF"]}))
            response = client.post("/sessions/summary", json={"sessionId": 3, "title": "테스트 세션"})
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json(), {"sessionId": 3,
                "summary": '\n\n'.join(f"## {s['title']}\n\n{s['description']}" for s in sections),
                "sections": sections, "tags": ["AI", "PDF"]})
            self.assertEqual(generate.call_args.kwargs["text"], {"format" : {"type": "json_object"}})

    def test_invalid_summary_sections_fail_instead_of_saving_incomplete_summary(self):
        generate = module.openai_client.responses.create
        invalid = [[], None, ["text"], [{"title": " ", "description": "설명"}],
                   [{"title": "제목", "description": None}], [{"title": "x" * 201, "description": "설명"}]]
        with TestClient(module.app) as client:
            for sections in invalid:
                with self.subTest(sections=sections):
                    generate.return_value = SimpleNamespace(output_text=json.dumps({"sections": sections, "tags": []}))
                    response = client.post("/sessions/summary", json={"sessionId": 3, "title": "테스트"})
                    self.assertEqual(response.status_code, 500)


if __name__ == "__main__":
    unittest.main()
