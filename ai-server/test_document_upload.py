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
     patch("google.genai.Client"), patch("dotenv.load_dotenv"):
    spec.loader.exec_module(module)


class DocumentUploadTest(unittest.TestCase):
    def test_multipart_pdf_and_validation(self):
        with fitz.open() as doc:
            for text in ("PDF transfer test", "Second page"):
                doc.new_page().insert_text((72, 72), text)
            pdf = doc.tobytes()
        with TestClient(module.app) as client, patch.object(module, "save_chunks") as save:
            response = client.post("/documents/process", data={"materialId": "7", "sessionId": "3"},
                                   files={"file": ("lecture.pdf", pdf, "application/pdf")})
            self.assertEqual(response.status_code, 200, response.text)
            self.assertEqual(response.json(), {"materialId": 7, "status": "success", "chunkCount": 2})
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


class MarkdownResponseTest(unittest.TestCase):
    def test_markdown_text_stays_inside_json_fields(self):
        markdown = '## 핵심 내용\n\n- **중요**: "답변"\n\n```python\nprint("hello")\n```'
        generate = module.gemini_client.models.generate_content
        with TestClient(module.app) as client, patch.object(module, "search_similar_chunks", return_value=[
            (1, 7, 0, 2, "자료 내용", 0.9)
        ]):
            generate.return_value = SimpleNamespace(text=markdown)
            response = client.post("/ai/answer", json={"sessionId": 3, "question": "핵심 내용은?"})
            self.assertEqual(response.status_code, 200)
            self.assertIn("application/json", response.headers["content-type"])
            body = response.json()
            self.assertEqual(set(body), {"sessionId", "question", "answer", "sources"})
            self.assertEqual(body["answer"], markdown)
            self.assertEqual(body["sources"][0]["materialId"], 7)

            generate.return_value = SimpleNamespace(text=json.dumps({"summary": markdown, "tags": ["AI", "PDF"]}))
            response = client.post("/sessions/summary", json={"sessionId": 3, "title": "테스트 세션"})
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json(), {"sessionId": 3, "summary": markdown, "tags": ["AI", "PDF"]})
            self.assertEqual(generate.call_args.kwargs["config"]["response_mime_type"], "application/json")


if __name__ == "__main__":
    unittest.main()
