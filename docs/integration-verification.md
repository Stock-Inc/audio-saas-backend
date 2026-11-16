# Проверка интеграции с ML-сервисом

Проверено 16 ноября 2026 года на локальном `docker-compose.yaml` с образом `ghcr.io/stock-inc/audio-saas-ml:dev` и `ML_PROCESSING_MOCK_ENABLED=true`.

## Проверенный поток

1. Backend сохраняет запись `audio_uploads` в PostgreSQL.
2. Backend отправляет `POST /api/v1/ml/jobs` как multipart (`file`, `options`) и передаёт `requestId`, `recordId` в query string согласно OpenAPI ML-сервиса.
3. ML-сервис отвечает `202` с `jobId`, `recordId`, `status=QUEUED`.
4. Backend проверяет наличие обоих ID и совпадение возвращённого `recordId`.
5. ML-сервис публикует `MlJobResultEvent` в `ml-job-results` через Schema Registry.
6. Backend читает specific Avro record, сопоставляет его по `recordId`, сохраняет JSON-результат и переводит запись в `COMPLETED`.
7. Результат доступен через `GET /api/v1/audio/{id}/result`.

Фактическая схема subject `ml-job-results-value` в Schema Registry совпала с `src/main/avro/ml-job-result-event.avsc`.

## Автоматические проверки

Обычный набор тестов не требует внешних сервисов:

```powershell
.\mvnw.cmd test
```

Сквозной тест является opt-in, потому что пишет временную запись в локальную PostgreSQL и вызывает локальный ML-сервис:

```powershell
docker compose -f docker-compose.yaml up -d `
  postgres keycloak-db keycloak kafka schema-registry postgres-ml backend-ml

Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
    [Environment]::SetEnvironmentVariable(
      $matches[1].Trim(), $matches[2], 'Process'
    )
  }
}
$env:RUN_EXTERNAL_INTEGRATION_TESTS = 'true'
$env:AUDIO_PROCESSOR_URL = 'http://localhost:8084/api/v1/ml/jobs'
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = 'localhost:9092'
$env:SPRING_KAFKA_PROPERTIES_SCHEMA_REGISTRY_URL = 'http://localhost:8085'
$env:AUDIO_KAFKA_CONSUMER_GROUP = 'audio-saas-backend-integration-test'

.\mvnw.cmd '-Dtest=AudioPipelineIntegrationTests' test
```

Тест ждёт `COMPLETED` не более 30 секунд, проверяет `jobId`, `recordId`, JSON-результат и удаляет созданную backend-запись после завершения.

## Границы проверки

- Проверен реальный HTTP/Kafka/Avro/PostgreSQL обмен, но ML-вычисление выполнялось встроенным mock-режимом внешнего сервиса.
- Реальный Whisper по `ML_WHISPER_URL` не вызывался.
- Ошибки соединения и circuit breaker покрыты локальными тестами; отказ реального ML/Whisper в этом прогоне не инжектировался.

