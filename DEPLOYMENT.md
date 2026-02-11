# MatQuiz Deployment Guide (Coolify)

This project is prepared for Coolify auto-deploy from Git using `docker-compose.prod.yml`.

The stack includes:
- `frontend` (React static app on port `3000`)
- `backend` (Spring Boot API + WebSocket on port `8080`)
- `ai-worker` (Python worker)
- `postgres-db`
- `rabbitmq`

## 1. Create a Coolify Application

1. In Coolify, click `New Resource` -> `Application`.
2. Connect/select your Git repository.
3. Choose `Docker Compose` build pack.
4. Set compose file path to `docker-compose.prod.yml`.
5. Enable `Auto Deploy` on push.

## 2. Configure Domains in Coolify

Use two domains:
- Frontend domain -> `frontend` service (port `3000`)
- API domain -> `backend` service (port `8080`)

Example:
- Frontend: `https://matquiz.example.com`
- Backend: `https://api-matquiz.example.com`

## 3. Configure Environment Variables

Use `.env.prod.example` as reference and set these in Coolify:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `RABBITMQ_USER`
- `RABBITMQ_PASSWORD`
- `SECURITY_SECRET`
- `SECURITY_VALIDITY`
- `APP_CORS_ALLOWED_ORIGINS` (frontend domain, ex: `https://matquiz.example.com`)
- `GEMINI_API_KEY`
- `MAX_PROCESS_RETRIES`
- `VITE_API_URL` (ex: `https://api-matquiz.example.com/api`)
- `VITE_WS_URL` (ex: `https://api-matquiz.example.com/ws`)

Important:
- `VITE_*` variables are used at frontend build time.
- If you change frontend/backend domain values, trigger a new deploy.

## 4. Persistent Data

This compose file defines persistent volumes:
- `pg_data` for PostgreSQL
- `rabbitmq_data` for RabbitMQ

Keep them attached between deploys to preserve data.

## 5. Deploy and Verify

After deploy:
1. Open frontend domain and test login/register.
2. Confirm backend endpoint responds:
   - `GET https://api-matquiz.example.com/actuator/health` (with current security setup this can return `401`, which is expected).
3. Start a game and confirm real-time updates work (WebSocket path `/ws`).

## 6. Optional Local Validation

If you want to validate locally before pushing:

```bash
cp .env.prod.example .env.prod
docker compose --env-file .env.prod -f docker-compose.prod.yml config
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
docker compose --env-file .env.prod -f docker-compose.prod.yml down
```
