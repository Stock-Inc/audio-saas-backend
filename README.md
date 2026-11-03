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
| Открыть регистрацию напрямую | `GET /api/v1/auth/register` | Не реализовано |
| Вернуться после входа на страницу SPA | Например, `/auth/callback` | Не реализовано |
| Явно завершить frontend/Keycloak-сессию | Отдельный logout-flow | Не настроено |
