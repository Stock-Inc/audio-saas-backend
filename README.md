# Sonora Audio SaaS Backend

Spring Boot backend для авторизации через Keycloak, загрузки аудио, постановки ML-заданий, получения результатов через Kafka и организации аудиотеки по папкам и тегам.

Актуальные документы:

- [контракт для frontend](docs/frontend-api.md);
- Swagger UI после запуска: `http://localhost:8081/swagger-ui.html`;
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`.

## Архитектура

```text
Frontend
   │ session cookie или Bearer JWT
   ▼
Backend :8081 ──► PostgreSQL :5432
   │ multipart + requestId/recordId
   ▼
ML service :8084 ──► Whisper :8000 (или mock-режим)
   │ Avro MlJobResultEvent
   ▼
Kafka :9092 ◄──► Schema Registry :8085
   │
   └────────────► Backend сохраняет JSON-результат

Keycloak :9090 обслуживает login/registration flow.
```

## Быстрый старт

Требования: Docker Desktop с Compose. Для запуска без Docker также нужны Java 25 и Maven; рекомендуемый способ — Maven Wrapper.

1. Создайте локальный env-файл:

```powershell
Copy-Item .env.example .env
```

2. Заполните пустые пароли/секреты в `.env`. Не коммитьте этот файл.

3. Запустите локальный стек:

```powershell
docker compose -f docker-compose.yaml up --build -d
```

4. Проверьте:

- backend health: `http://localhost:8081/actuator/health`;
- Swagger: `http://localhost:8081/swagger-ui.html`;
- Keycloak: `http://localhost:9090`;
- ML Swagger: `http://localhost:8084/swagger-ui.html`.

Остановить стек:

```powershell
docker compose -f docker-compose.yaml down
```

Volumes с данными при обычном `down` сохраняются.

## Основные настройки

| Переменная | Назначение |
| --- | --- |
| `DATABASE`, `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL backend |
| `AUDIO_PROCESSOR_URL` | `POST /api/v1/ml/jobs` внешнего ML-сервиса |
| `AUDIO_KAFKA_TOPIC_JOB_RESULTS` | входной topic результата, по умолчанию `ml-job-results` |
| `KEYCLOAK_CLIENT_SECRET` | secret confidential client `spring-app` |
| `KEYCLOAK_PUBLIC_URL` | issuer/browser URL realm |
| `KEYCLOAK_BACKCHANNEL_URL` | URL Keycloak, доступный backend |
| `FRONTEND_SUCCESS_URL` | SPA callback после успешного OAuth2-login |
| `CORS_ALLOWED_ORIGINS` | разрешённые frontend origins через запятую |
| `ML_PROCESSING_MOCK_ENABLED` | безопасный mock ML-обработки для локальной разработки |

В Docker Compose backend получает внутренние адреса `postgres`, `keycloak`, `kafka`, `schema-registry`; host-порты предназначены для запуска тестов и инструментов с машины разработчика.

## Тесты

```powershell
.\mvnw.cmd test
```

## API в двух словах

- `GET /oauth2/authorization/keycloak` — начать login/registration flow;
- `GET /api/v1/auth/me` — текущий пользователь;
- `POST /api/v1/audio` — загрузить `file` + JSON `options`;
- `GET /api/v1/audio` — аудиотека и фильтры;
- `GET /api/v1/audio/{id}/result` — скачать готовый JSON;
- `/api/v1/folders` и `/api/v1/tags` — организация библиотеки.

Полные тела запросов, ответы, статусы и TypeScript-примеры находятся в [frontend-api.md](docs/frontend-api.md).
