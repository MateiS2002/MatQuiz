FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app

COPY requirements.txt ./
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

COPY app/ /app/app/

USER app

HEALTHCHECK --interval=20s --timeout=5s --start-period=20s --retries=5 \
  CMD python -c "import os,socket,sys; h=os.getenv('RABBITMQ_HOST','rabbitmq'); p=int(os.getenv('RABBITMQ_PORT','5672')); s=socket.socket(); s.settimeout(3); s.connect((h,p)); s.close()"

CMD ["python", "-m", "app.main"]
