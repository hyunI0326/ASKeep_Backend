FROM python:3.13-slim
WORKDIR /app
ENV PYTHONUNBUFFERED=1
ENV HF_HOME=/opt/models
COPY requirements.txt .
RUN python -m pip install --no-cache-dir -r requirements.txt
# Download during the build, outside the Free instance's 512MiB runtime limit.
RUN python -c 'from huggingface_hub import snapshot_download; snapshot_download("intfloat/multilingual-e5-small", revision="614241f622f53c4eeff9890bdc4f31cfecc418b3", allow_patterns=["onnx/model_qint8_avx512_vnni.onnx", "sentencepiece.bpe.model"], cache_dir="/opt/models")'
ENV HF_HUB_OFFLINE=1
COPY main.py .
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
