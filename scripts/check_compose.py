"""한 서버 구성의 내부 주소, 준비 순서, Spring PDF 저장소를 검증한다."""
import json
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
config = json.loads(subprocess.check_output(
    ["docker", "compose", "--env-file", "/dev/null", "config", "--format", "json"],
    cwd=root,
    env={**os.environ, "GEMINI_API_KEY": "config-check-only",
         "JWT_SECRET": "config-check-secret-at-least-32-bytes", "DB_PASSWORD": "config-check-only"},
    text=True,
))
services = config["services"]
backend, ai, postgres = (services[name] for name in ("backend", "ai", "postgres"))
assert backend["environment"]["AI_SERVER_BASE_URL"] == "http://ai:8000"
assert backend["environment"]["DB_URL"] == "jdbc:postgresql://postgres:5432/askeep"
assert ai["environment"]["DB_HOST"] == "postgres"
assert ai["environment"]["DB_NAME"] == postgres["environment"]["POSTGRES_DB"] == "askeep"
assert backend["environment"]["DB_PASSWORD"] == ai["environment"]["DB_PASSWORD"] == postgres["environment"]["POSTGRES_PASSWORD"]
assert backend["environment"]["UPLOAD_DIR"] == "/uploads"
backend_upload = next(v for v in backend["volumes"] if v["target"] == "/uploads")
assert backend_upload["source"] == "uploads" and not backend_upload.get("read_only", False)
assert all(v["target"] != "/uploads" for v in ai["volumes"])
assert not ai.get("ports")
assert all(p["host_ip"] == "127.0.0.1" for p in postgres["ports"])
assert backend["ports"][0]["target"] == 8080
assert ai["depends_on"]["postgres"]["condition"] == "service_healthy"
assert backend["depends_on"]["ai"]["condition"] == "service_healthy"
assert backend["depends_on"]["postgres"]["condition"] == "service_healthy"
assert "http://localhost:8000/health" in ai["healthcheck"]["test"][-1]
assert all(s["restart"] == "unless-stopped" for s in services.values())
print("Compose checks passed")
