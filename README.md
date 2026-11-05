# Audio SaaS Backend

Spring Boot backend с авторизацией через Keycloak по протоколу OpenID Connect.

## Авторизация для frontend

Backend работает как OAuth2/OIDC client и использует серверную HTTP-сессию. Frontend не получает и не хранит access token или refresh token: после успешной авторизации браузер получает session cookie, а токены остаются на backend.

### Адреса локального окружения

| Компонент | URL |
| --- | --- |
| Backend | `http://localhost:8081` |
| Keycloak | `http://localhost:9090` |
| Realm | `demo` |
| OIDC client | `spring-app` |

## Логин

Для начала авторизации frontend должен выполнить переход браузера на backend:

```text
GET http://localhost:8081/oauth2/authorization/keycloak
```

Это должна быть навигация браузера, а не AJAX-запрос:

```javascript
function login() {
  window.location.assign(
    "http://localhost:8081/oauth2/authorization/keycloak"
  );
}
```

Дальнейший процесс:

1. Backend создаёт OAuth2-запрос и сохраняет его состояние.
2. Backend перенаправляет браузер на страницу входа Keycloak.
3. Пользователь вводит логин и пароль в Keycloak.
4. Keycloak возвращает браузер на callback backend:

   ```text
   http://localhost:8081/login/oauth2/code/keycloak
   ```

5. Backend обменивает authorization code на токены и создаёт HTTP-сессию.
6. Браузер получает session cookie.
7. В текущей реализации backend перенаправляет браузер на `/api/v1/auth/me`.

Frontend не должен самостоятельно обращаться к raw authorization URL Keycloak. Иначе backend не сможет корректно проверить сохранённый OAuth2 `state` и завершить авторизацию.

## Регистрация

Отдельного backend endpoint для регистрации сейчас нет. Frontend начинает тот же OAuth2-flow:

```javascript
function register() {
  window.location.assign(
    "http://localhost:8081/oauth2/authorization/keycloak"
  );
}
```

На открывшейся странице Keycloak пользователь нажимает ссылку регистрации и заполняет форму.

При текущих настройках realm:

- самостоятельная регистрация разрешена;
- подтверждение email отключено;
- обязательные действия после регистрации по умолчанию не назначены;
- после регистрации пользователь продолжает текущий OAuth2-flow и повторно вводить логин и пароль не должен.

Если frontend должен открывать форму регистрации сразу, на backend потребуется отдельная точка входа, например `GET /api/v1/auth/register`, которая начнёт OAuth2-flow с Keycloak-параметром `kc_action=register`. Такой endpoint пока не реализован.

## Получение текущего пользователя

Frontend может проверить текущую backend-сессию через endpoint:

```text
GET http://localhost:8081/api/v1/auth/me
```

При использовании разных origins браузер должен отправлять session cookie:

```javascript
async function loadCurrentUser() {
  const response = await fetch(
    "http://localhost:8081/api/v1/auth/me",
    {
      credentials: "include"
    }
  );

  if (response.status === 401) {
    return null;
  }

  if (!response.ok) {
    throw new Error("Не удалось получить текущего пользователя");
  }

  return response.json();
}
```

Endpoint не запускает OAuth2-flow автоматически:

- `200 OK` — в браузере есть активная backend-сессия; ответ содержит пользователя;
- `401 Unauthorized` — активной сессии нет, независимо от того, зарегистрирован ли пользователь в Keycloak;
- для начала входа frontend явно переходит на `GET /oauth2/authorization/keycloak`.

Таким образом, `/api/v1/auth/me` определяет состояние текущей сессии, но намеренно не позволяет анонимно проверять существование конкретной учётной записи.

Успешный ответ:

```json
{
  "id": "keycloak-user-sub",
  "username": "user123",
  "email": "user@example.com"
}
```

Поля ответа:

| Поле | Источник | Описание |
| --- | --- | --- |
| `id` | OIDC claim `sub` | Стабильный идентификатор пользователя в Keycloak |
| `username` | OIDC claim `preferred_username` | Имя пользователя |
| `email` | OIDC claim `email` | Email пользователя |

Email является обязательным полем регистрации, но при текущем `verifyEmail=false` backend не может считать его подтверждённым.

## Загрузка аудиофайла

Авторизованный пользователь может отправить аудиофайл на обработку:

```text
POST http://localhost:8081/api/v1/audio
Content-Type: multipart/form-data
```

Endpoint использует backend-сессию, поэтому браузер должен отправлять session cookie. Тело запроса содержит четыре multipart-поля:

| Поле | Тип | Описание |
| --- | --- | --- |
| `requestId` | UUID | Идентификатор запроса во внешней системе |
| `recordId` | UUID | Идентификатор аудиозаписи во внешней системе |
| `file` | binary | Загружаемый аудиофайл |
| `options` | JSON object | Параметры обработки аудио; part должен иметь `Content-Type: application/json` |

Пример запроса через `curl` с ранее полученной session cookie:

```bash
curl --request POST "http://localhost:8081/api/v1/audio" \
  --cookie "JSESSIONID=<session-id>" \
  --form "requestId=3e983854-c2ba-4d12-af8b-a8e2086c1370" \
  --form "recordId=a96725b4-df5e-41bb-92c3-3ec3786f8621" \
  --form "file=@./voice.mp3;type=audio/mpeg" \
  --form 'options={"diarization":true,"summary":true,"tasks":true};type=application/json'
```

Пример отправки с frontend:

```javascript
async function uploadAudio(file) {
  const formData = new FormData();
  formData.append(
    "requestId",
    "3e983854-c2ba-4d12-af8b-a8e2086c1370"
  );
  formData.append(
    "recordId",
    "a96725b4-df5e-41bb-92c3-3ec3786f8621"
  );
  formData.append("file", file);
  formData.append(
    "options",
    new Blob(
      [JSON.stringify({
        diarization: true,
        summary: true,
        tasks: true
      })],
      { type: "application/json" }
    )
  );
  const response = await fetch(
    "http://localhost:8081/api/v1/audio",
    {
      method: "POST",
      credentials: "include",
      body: formData
    }
  );

  if (!response.ok) {
    throw new Error(`Не удалось загрузить файл: ${response.status}`);
  }

  return response.json();
}
```

Не нужно вручную задавать общий заголовок `Content-Type` при использовании `FormData`: браузер сам добавляет корректный multipart boundary.

Успешный запрос возвращает `202 Accepted`:

```json
{
  "id": "7747f2c4-c6c0-4b48-9e80-724bb8a85938",
  "requestId": "3e983854-c2ba-4d12-af8b-a8e2086c1370",
  "recordId": "a96725b4-df5e-41bb-92c3-3ec3786f8621",
  "status": "FORWARDED"
}
```

Возможные ответы:

| Статус | Значение |
| --- | --- |
| `202 Accepted` | Файл принят в обработку |
| `400 Bad Request` | Файл пуст, UUID некорректен или `options` не является JSON-объектом |
| `401 Unauthorized` / redirect | Backend-сессия отсутствует; необходимо пройти OAuth2-вход |
| `413 Payload Too Large` | Превышен лимит файла `1000MB` или всего запроса `1100MB` |
| `415 Unsupported Media Type` | Некорректный Content-Type файла или multipart-запроса |
| `502 Bad Gateway` | Сервис обработки аудио недоступен |

Интерактивная документация доступна по адресу `http://localhost:8081/swagger-ui.html`.

## Где хранятся данные

| Компонент | Данные |
| --- | --- |
| Keycloak | Учётная запись, пароль, username, email, роли и остальные атрибуты |
| Backend | Авторизованная Spring Security сессия, `OidcUser` и OAuth2-токены |
| Браузер | Session cookie; OAuth2-токены frontend не получает |
| База backend | Пользователь сейчас автоматически не создаётся и не сохраняется |

Если приложению нужна собственная запись пользователя, её следует создавать или обновлять после успешной авторизации, используя OIDC `sub` как внешний стабильный идентификатор.

## Текущее поведение после авторизации

Сейчас success URL настроен на:

```text
/api/v1/auth/me
```

Поэтому после логина или регистрации браузер открывает JSON с данными пользователя. Данные уже возвращаются, но предыдущий JavaScript-контекст SPA не может непосредственно обработать ответ от такой навигации.

Для SPA рекомендуется следующий процесс:

```text
Frontend
   -> backend /oauth2/authorization/keycloak
   -> Keycloak login или registration
   -> backend /login/oauth2/code/keycloak
   -> frontend /auth/callback
   -> backend /api/v1/auth/me
```

Для этого backend должен после успешной авторизации перенаправлять браузер на frontend, например:

```text
http://localhost:3000/auth/callback
```

Страница frontend `/auth/callback` вызывает `/api/v1/auth/me`, сохраняет пользователя в состоянии приложения и перенаправляет его на нужный экран. Токены и данные пользователя не следует передавать в query-параметрах redirect URL.

Этот рекомендуемый redirect на frontend пока не реализован: текущий success URL по-прежнему ведёт на `/api/v1/auth/me`.

## CORS и cookie

Если frontend и backend работают на разных origins, frontend должен использовать `credentials: "include"`, а backend должен разрешать конкретный frontend origin и credentials.

Разрешённые frontend origins задаются переменной окружения `CORS_ALLOWED_ORIGINS`. Для одного origin:

```dotenv
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

Несколько origins перечисляются через запятую без завершающего `/`:

```dotenv
CORS_ALLOWED_ORIGINS=http://localhost:3000,https://app.example.com
```

Backend разрешает запросы с credentials только с перечисленных origins. На frontend для запросов с session cookie нужно указывать:

```javascript
fetch("http://localhost:8081/api/v1/auth/me", {
  credentials: "include"
});
```

## Текущий frontend-контракт

| Действие | Метод и адрес | Статус |
| --- | --- | --- |
| Начать логин | `GET /oauth2/authorization/keycloak` | Реализовано |
| Открыть регистрацию | Тот же OAuth2 endpoint, затем ссылка регистрации в Keycloak | Реализовано |
| OAuth2 callback | `GET /login/oauth2/code/keycloak` | Вызывается Keycloak, не frontend |
| Проверить сессию / получить пользователя | `GET /api/v1/auth/me` | `200` с пользователем или `401`, без OAuth2-редиректа |
| Загрузить аудиофайл | `POST /api/v1/audio` | `202` с идентификатором и статусом загрузки; требуется backend-сессия |
| Открыть регистрацию напрямую | `GET /api/v1/auth/register` | Не реализовано |
| Вернуться после входа на страницу SPA | Например, `/auth/callback` | Не реализовано |
| Явно завершить frontend/Keycloak-сессию | Отдельный logout-flow | Не настроено |
