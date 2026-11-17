# Контракт backend API для frontend

Базовый URL локально: `http://localhost:8081`.

Интерактивный Swagger: `http://localhost:8081/swagger-ui.html`. Машиночитаемый OpenAPI: `http://localhost:8081/v3/api-docs`.

## Авторизация

Backend принимает два варианта авторизации:

- browser session cookie `JSESSIONID` после OAuth2-login через Keycloak;
- `Authorization: Bearer <access-token>` для клиентов, которые сами получают JWT.

Для SPA рекомендуется backend-session flow:

1. Перенаправить браузер на `GET /oauth2/authorization/keycloak`.
2. Пользователь входит или регистрируется в Keycloak.
3. Keycloak вызывает backend callback `/login/oauth2/code/keycloak`.
4. Backend создаёт сессию и перенаправляет браузер на `FRONTEND_SUCCESS_URL` (по умолчанию `http://localhost:3000/auth/callback`).
5. Страница `/auth/callback` вызывает `GET /api/v1/auth/me` с `credentials: "include"`.

```ts
export function login(): void {
  window.location.assign(
    "http://localhost:8081/oauth2/authorization/keycloak",
  );
}

export async function getCurrentUser() {
  const response = await fetch("http://localhost:8081/api/v1/auth/me", {
    credentials: "include",
  });

  if (response.status === 401) return null;
  if (!response.ok) throw new Error(`auth/me: ${response.status}`);
  return response.json() as Promise<{
    id: string;
    username: string | null;
    email: string | null;
  }>;
}
```

Не передавайте OAuth-токены через query string. При cookie-авторизации добавляйте `credentials: "include"` ко всем API-запросам.

## Загрузка аудио

`POST /api/v1/audio`, `Content-Type: multipart/form-data`.

Frontend отправляет только две части:

| Часть | Тип | Обязательность |
| --- | --- | --- |
| `file` | binary | да |
| `options` | JSON object с `Content-Type: application/json` | да |

`requestId` и `recordId` создаёт backend. Frontend их не генерирует и не передаёт.

```ts
export async function uploadAudio(file: File) {
  const form = new FormData();
  form.append("file", file);
  form.append(
    "options",
    new Blob(
      [JSON.stringify({
        diarization: true,
        summary: true,
        tasks: true,
      })],
      { type: "application/json" },
    ),
  );

  const response = await fetch("http://localhost:8081/api/v1/audio", {
    method: "POST",
    credentials: "include",
    body: form,
  });

  if (!response.ok) throw new Error(`upload: ${response.status}`);
  return response.json() as Promise<AudioUploadAccepted>;
}
```

Не задавайте общий заголовок `Content-Type` вручную: браузер должен добавить multipart boundary.

Успех — `202 Accepted`:

```json
{
  "id": "7747f2c4-c6c0-4b48-9e80-724bb8a85938",
  "requestId": "3e983854-c2ba-4d12-af8b-a8e2086c1370",
  "recordId": "a96725b4-df5e-41bb-92c3-3ec3786f8621",
  "status": "FORWARDED"
}
```

`id` — основной идентификатор для последующих frontend-запросов. `requestId`, `recordId` и `processingJobId` полезны для диагностики, но не нужны для навигации UI.

## Статусы и polling

| Статус | Значение для UI |
| --- | --- |
| `RECEIVED` | файл сохранён backend |
| `FORWARDED` | ML-сервис принял задание |
| `PROCESSING` | транскрипция или анализ выполняются |
| `COMPLETED` | результат можно скачать |
| `FAILED` | обработка завершилась ошибкой; показать `errorMessage` |

Текущая версия не отправляет WebSocket/SSE-уведомления. После `202` опрашивайте `GET /api/v1/audio/{id}` с разумным интервалом, например 2–5 секунд, и остановитесь на `COMPLETED` или `FAILED`.

```ts
export async function getAudio(id: string): Promise<AudioFile> {
  const response = await fetch(`http://localhost:8081/api/v1/audio/${id}`, {
    credentials: "include",
  });
  if (!response.ok) throw new Error(`audio: ${response.status}`);
  return response.json();
}
```

## Аудиотека и фильтры

| Метод | URL | Назначение |
| --- | --- | --- |
| `GET` | `/api/v1/audio` | все файлы пользователя, новые сначала |
| `GET` | `/api/v1/audio?folderId={uuid}` | файлы папки |
| `GET` | `/api/v1/audio?tagId={uuid}` | файлы с тегом |
| `GET` | `/api/v1/audio?folderId={uuid}&tagId={uuid}` | пересечение двух фильтров |
| `GET` | `/api/v1/audio/{id}` | один файл |

Основные поля `AudioFile`:

```ts
type AudioStatus =
  | "RECEIVED"
  | "FORWARDED"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED";

interface AudioFile {
  id: string;
  requestId: string;
  recordId: string;
  processingJobId: string | null;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  status: AudioStatus;
  errorMessage: string | null;
  folder: Folder | null;
  tags: Tag[];
  resultAvailable: boolean;
  resultFilename: string | null;
  resultSizeBytes: number | null;
  createdAt: string;
  updatedAt: string;
}
```

## Результат

`GET /api/v1/audio/{id}/result` возвращает attachment с `Content-Type: application/json`.

- `200` — результат готов;
- `409` — обработка ещё не завершена;
- `404` — файл не существует или принадлежит другому пользователю.

```ts
export async function downloadResult(id: string): Promise<void> {
  const response = await fetch(
    `http://localhost:8081/api/v1/audio/${id}/result`,
    { credentials: "include" },
  );
  if (!response.ok) throw new Error(`result: ${response.status}`);

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "audio-result.json";
  link.click();
  URL.revokeObjectURL(url);
}
```

Структура JSON-файла:

```json
{
  "jobId": "7747f2c4-c6c0-4b48-9e80-724bb8a85938",
  "recordId": "a96725b4-df5e-41bb-92c3-3ec3786f8621",
  "status": "COMPLETED",
  "transcript": {
    "segments": [
      {
        "id": 1,
        "speaker": "Speaker 1",
        "start": 0.0,
        "end": 2.5,
        "text": "Hello"
      }
    ]
  },
  "analysis": {
    "summary": "...",
    "topics": ["..."],
    "keyPoints": ["..."],
    "decisions": ["..."],
    "tasks": [{ "text": "...", "assignee": null }]
  }
}
```

`transcript` или `analysis` могут быть `null`, если соответствующая обработка не запрашивалась или не вернула данные.

## Папки и теги

| Метод | URL | Тело | Результат |
| --- | --- | --- | --- |
| `GET` | `/api/v1/folders` | — | плоский список папок с `parentId`, по имени |
| `POST` | `/api/v1/folders` | `{ "name": "Встречи", "parentId": null }` | `201`, корневая папка |
| `POST` | `/api/v1/folders` | `{ "name": "Интервью", "parentId": "uuid" }` | `201`, дочерняя папка |
| `GET` | `/api/v1/tags` | — | теги по имени |
| `POST` | `/api/v1/tags` | `{ "name": "Важное" }` | `201`, созданный тег |
| `PUT` | `/api/v1/audio/{id}/folder` | `{ "folderId": "uuid" }` | обновлённый файл |
| `PUT` | `/api/v1/audio/{id}/folder` | `{ "folderId": null }` | файл перемещён в корень |
| `PUT` | `/api/v1/audio/folder` | `{ "audioIds": ["uuid"], "folderId": "uuid-or-null" }` | обновлённые файлы |
| `PUT` | `/api/v1/audio/{id}/tags` | `{ "tagIds": ["uuid"] }` | обновлённый файл |

Передача пустого `tagIds` удаляет все теги. Массовое перемещение атомарно для выбранного набора: если один ID недоступен, backend отвечает `404` и ничего не перемещает.

Ответ папки содержит `id`, `name`, `parentId`, `createdAt` и `updatedAt`.
`parentId = null` означает корневую папку. Вложенность не ограничена; дерево на
frontend собирается из плоского ответа `GET /api/v1/folders`. Имя папки должно
быть уникальным без учёта регистра только среди соседей одного родителя.

## Ошибки

Frontend должен в первую очередь обрабатывать HTTP status; стабильная JSON-схема тела ошибки пока не является частью контракта.

| Status | Действие frontend |
| --- | --- |
| `400` | показать ошибку валидации запроса |
| `401` | очистить локального пользователя и предложить login |
| `404` | объект отсутствует или недоступен текущему пользователю |
| `409` | конфликт имени либо результат ещё не готов |
| `413` | файл превышает лимит backend |
| `415` | неподдерживаемый `Content-Type` |
| `502` | ML-сервис недоступен; можно предложить повторить позже |
| `503` | circuit breaker временно остановил обращения к ML-сервису |

## CORS

Для cookie-flow origin frontend должен входить в `CORS_ALLOWED_ORIGINS`; wildcard с credentials не поддерживается. Во всех `fetch` используйте `credentials: "include"`.

