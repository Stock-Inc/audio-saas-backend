# Sonora API для Android на Kotlin и Jetpack Compose

Приложение получает токены у Keycloak через Authorization Code + PKCE и передаёт access token в заголовке `Authorization: Bearer <access_token>` при вызове backend API. Ниже приведены маршруты, тела запросов, ответы, коды ошибок и Kotlin-примеры для интеграции. [Контракт для браузерного клиента](frontend-api.md)

- [Вход и возврат в Compose](#вход-и-возврат-в-compose)
- [Маршруты и методы Retrofit](#маршруты-и-методы-retrofit)
- [Kotlin-модели ответов](#kotlin-модели-ответов)
- [Аудиотека и поиск](#аудиотека-и-поиск)
- [Папки, теги и перемещение файлов](#папки-теги-и-перемещение-файлов)
- [Загрузка, обработка и результат](#загрузка-обработка-и-результат)
- [Коды ошибок](#коды-ошибок)

## Адреса и настройки Keycloak

| Параметр | Значение |
| --- | --- |
| Базовый URL API | URL бэкенда с завершающим `/`. Локальный порт — `8081` |
| Issuer | Публичный URL realm `demo`, который задан в `KEYCLOAK_PUBLIC_URL` |
| Client ID | `sonora-android` |
| Redirect URI | `dev.alnoer.audiosaas:/oauth2redirect` |
| Scope | `openid profile email` |
| OAuth flow | Authorization Code, PKCE `S256`, public client |

Эти параметры клиента заданы в [экспорте realm](../keycloak-import/demo-realm.json). В существующем окружении проверьте, что клиент создан и его Valid Redirect URIs содержит этот URI целиком. Android-сборке достаточно публичных параметров из таблицы.

Для эмулятора, когда оба сервиса работают на компьютере разработчика, используйте API `http://10.0.2.2:8081/`. В локальном Compose по умолчанию Keycloak публикует `localhost`. Для Android-окружения переопределите `KC_HOSTNAME=http://10.0.2.2:9090` у Keycloak и задайте `KEYCLOAK_PUBLIC_URL=http://10.0.2.2:9090/realms/demo` у бэкенда. Issuer в discovery, поле `iss` токена и `KEYCLOAK_PUBLIC_URL` должны совпадать. `KEYCLOAK_BACKCHANNEL_URL` может указывать на отдельный внутренний адрес Keycloak для получения ключей. Для физического устройства используйте адреса, доступные с телефона. В релизе используйте HTTPS, а для локального HTTP настройте отдельную debug-сборку.

## Вход и возврат в Compose

Используйте AppAuth for Android с системным браузером или Custom Tabs. Добавьте разрешение `android.permission.INTERNET` в манифест и настройте схему редиректа в Gradle. AppAuth добавит обработчик возврата через объединение манифестов. [AppAuth for Android](https://github.com/openid/AppAuth-Android)

```kotlin
android {
    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "dev.alnoer.audiosaas"
    }
}
```

1. Загрузите конфигурацию через `AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(issuer), ...)`. AppAuth читает discovery по адресу `{issuer}/.well-known/openid-configuration`. [Keycloak OIDC endpoints](https://www.keycloak.org/securing-apps/oidc-layers)
2. Соберите запрос с `ResponseTypeValues.CODE` и точным redirect URI. AppAuth генерирует `state` и PKCE verifier. Для регистрации пользователь может перейти по ссылке регистрации на странице Keycloak, если она включена в realm.
3. Передайте intent из `AuthorizationService.getAuthorizationRequestIntent(request)` в Compose launcher. Keycloak вернёт браузер на `dev.alnoer.audiosaas:/oauth2redirect`, затем AppAuth доставит результат launcher. В callback обработайте отмену и ошибку протокола, а при успехе обменяйте код.
4. Сохраните обновлённый `AuthState` и вызовите `GET /api/v1/auth/me` с access token. Ответ содержит `id`, `username` и `email`. Используйте `401` как сигнал проверить сессию. Android начинает вход у Keycloak напрямую, поэтому callback бэкенда и `FRONTEND_SUCCESS_URL` относятся к браузерному клиенту.

```kotlin
val request = AuthorizationRequest.Builder(
    configuration,
    "sonora-android",
    ResponseTypeValues.CODE,
    Uri.parse("dev.alnoer.audiosaas:/oauth2redirect"),
).setScope("openid profile email").build()

val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
) { result ->
    val data = result.data
    if (data == null) {
        viewModel.onLoginCancelled()
    } else {
        viewModel.finishLogin(
            AuthorizationResponse.fromIntent(data),
            AuthorizationException.fromIntent(data),
        )
    }
}

Button(onClick = {
    launcher.launch(authorizationService.getAuthorizationRequestIntent(request))
}) {
    Text("Войти")
}
```

В примере `configuration` уже загружена, а `authorizationService` и `viewModel` предоставлены экрану. В `finishLogin` при наличии `response` вызовите `authState.update(response, exception)`, затем `authorizationService.performTokenRequest(response.createTokenExchangeRequest(), ...)`. После успешного обмена обновите состояние через `authState.update(tokenResponse, tokenException)`. Храните состояние входа в репозитории авторизации и отдавайте экрану через `StateFlow`.

### Как сменить адрес возврата

1. Добавьте новый полный URI в **Clients → sonora-android → Settings → Valid Redirect URIs** в активном realm. Для новых окружений обновите `redirectUris` клиента в [экспорте realm](../keycloak-import/demo-realm.json). Если развёрнуты старые сборки, держите оба URI до завершения перехода. [Настройки клиентов Keycloak](https://www.keycloak.org/docs/latest/server_admin/index.html)
2. В Android передайте тот же полный URI в `AuthorizationRequest.Builder`. Для пользовательской схемы укажите часть до двоеточия в `appAuthRedirectScheme`. Для HTTPS App Link настройте intent filter AppAuth для нужных host и path, включите `android:autoVerify` и разместите `/.well-known/assetlinks.json` с идентификатором пакета и отпечатком сертификата приложения. [Обработка возврата в AppAuth](https://github.com/openid/AppAuth-Android#capturing-the-authorization-redirect) [Android App Links](https://developer.android.com/training/app-links/add-applinks)
3. В других нативных клиентах зарегистрируйте URI у платформы и в OAuth-библиотеке. Для сборки с отдельным Client ID внесите адрес в настройки именно этого клиента в Keycloak.
4. Проверьте вход на эмуляторе и устройстве. Браузер должен вернуть управление приложению, обмен кода должен пройти, а `GET /api/v1/auth/me` с access token должен вернуть пользователя.

Для мобильной версии сайта браузерный вход использует `spring-app`. При смене адреса сайта задайте `FRONTEND_SUCCESS_URL` на бэкенде и добавьте origin сайта в `CORS_ALLOWED_ORIGINS`. Если меняется публичный адрес самого бэкенда, внесите полный `https://<backend>/login/oauth2/code/keycloak` в **Valid Redirect URIs** клиента `spring-app`.

### Обновление токена и выход

- Сериализованный `AuthState` содержит токены. Храните его в приватном зашифрованном хранилище с ключом из Android Keystore. Исключите токены из логов, аналитики и резервных копий.
- Перед запросом получайте свежий access token через `AuthState.performActionWithFreshTokens` и сохраняйте состояние после refresh. Синхронизируйте refresh для параллельных запросов. Access token уходит только на origin API. Refresh token используется при обмене с Keycloak, ID token может использоваться при выходе из Keycloak.
- Если API вернул `401`, обновите токен и повторите безопасный запрос один раз. При просроченной сессии или `invalid_grant` очистите состояние и предложите вход. При временной сетевой ошибке сохраните сессию и покажите возможность повторить запрос. Для загрузки после обрыва соединения сначала проверьте библиотеку, поскольку сервер мог принять файл.
- Для локального выхода очистите состояние и кэш аккаунта. Для завершения браузерной SSO-сессии используйте `end_session_endpoint` из discovery и RP-Initiated Logout. Если нужен возврат из браузера в приложение после выхода, отдельно зарегистрируйте точный `post_logout_redirect_uri` для `sonora-android` и добавьте обработчик URI в Android. Экспорт realm задаёт URI возврата для входа. [Выход через Keycloak](https://www.keycloak.org/securing-apps/oidc-layers#_logout_endpoint)

## Маршруты и методы Retrofit

Все запросы из таблицы требуют Bearer access token. Для `POST`, `PATCH` и `PUT` с JSON отправляйте `Content-Type: application/json`. Загрузка аудио использует `multipart/form-data`. Идентификаторы в путях и фильтрах передаются как UUID.

| Метод и путь | Успешный ответ |
| --- | --- |
| `GET /api/v1/auth/me` | `200`, текущий пользователь |
| `GET /api/v1/audio?folderId=…&tagId=…&sort=…` | `200`, массив файлов |
| `GET /api/v1/audio/search?q=…&offset=…` | `200`, массив файлов по релевантности |
| `GET /api/v1/audio/{id}` | `200`, метаданные файла |
| `POST /api/v1/audio` | `202`, идентификаторы и статус загрузки |
| `GET /api/v1/audio/{id}/result` | `200`, JSON-вложение |
| `GET /api/v1/folders` | `200`, плоский список папок по имени |
| `GET /api/v1/folders/null/children` | `200`, папки верхнего уровня по имени |
| `GET /api/v1/folders/{id}/children` | `200`, непосредственные подпапки по имени |
| `POST /api/v1/folders` | `201`, созданная папка |
| `PATCH /api/v1/folders/{id}` | `200`, переименованная папка |
| `DELETE /api/v1/folders/{id}` | `204`, без тела |
| `GET /api/v1/tags` | `200`, массив тегов по имени |
| `POST /api/v1/tags` | `201`, созданный тег |
| `PATCH /api/v1/tags/{id}` | `200`, переименованный тег |
| `DELETE /api/v1/tags/{id}` | `204`, без тела |
| `PUT /api/v1/audio/{id}/folder` | `200`, обновлённый файл |
| `PUT /api/v1/audio/folder` | `200`, массив обновлённых файлов |
| `PUT /api/v1/audio/{id}/tags` | `200`, обновлённый файл |

Интерфейс использует Retrofit, OkHttp и JSON-конвертер. При создании Retrofit укажите базовый URL с завершающим `/` и подключите конвертер для Kotlin DTO, например Moshi с `KotlinJsonAdapterFactory`. Передайте свежий access token через OkHttp interceptor, который добавляет заголовок `Authorization` для origin API. Для методов, возвращающих DTO или `Unit`, HTTP-ошибка приходит как `HttpException`. Метод загрузки результата возвращает `Response<ResponseBody>`, чтобы можно было прочитать заголовки и проверить код до чтения потока. [Retrofit](https://github.com/square/retrofit) · [Настройка Kotlin-моделей в Moshi](https://github.com/square/moshi#kotlin)

```kotlin
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface SonoraApi {
    @GET("api/v1/auth/me")
    suspend fun me(): UserDto

    @GET("api/v1/audio")
    suspend fun audio(
        @Query("folderId") folderId: String? = null,
        @Query("tagId") tagId: String? = null,
        @Query("sort") sort: AudioSort = AudioSort.NEWEST,
    ): List<AudioFileDto>

    @GET("api/v1/audio/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("offset") offset: Int = 0,
    ): List<AudioFileDto>

    @GET("api/v1/audio/{id}")
    suspend fun audioDetails(@Path("id") id: String): AudioFileDto

    @Multipart
    @POST("api/v1/audio")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("options") options: RequestBody,
    ): AudioUploadDto

    @Streaming
    @GET("api/v1/audio/{id}/result")
    suspend fun result(@Path("id") id: String): Response<ResponseBody>

    @GET("api/v1/folders")
    suspend fun folders(): List<FolderDto>

    @GET("api/v1/folders/{id}/children")
    suspend fun children(@Path("id") id: String): List<FolderDto>

    @POST("api/v1/folders")
    suspend fun createFolder(@Body body: CreateFolderBody): FolderDto

    @PATCH("api/v1/folders/{id}")
    suspend fun renameFolder(@Path("id") id: String, @Body body: NameBody): FolderDto

    @DELETE("api/v1/folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String)

    @GET("api/v1/tags")
    suspend fun tags(): List<TagDto>

    @POST("api/v1/tags")
    suspend fun createTag(@Body body: NameBody): TagDto

    @PATCH("api/v1/tags/{id}")
    suspend fun renameTag(@Path("id") id: String, @Body body: NameBody): TagDto

    @DELETE("api/v1/tags/{id}")
    suspend fun deleteTag(@Path("id") id: String)

    @PUT("api/v1/audio/{id}/folder")
    suspend fun moveAudio(@Path("id") id: String, @Body body: MoveAudioBody): AudioFileDto

    @PUT("api/v1/audio/folder")
    suspend fun moveAudioBatch(@Body body: BulkMoveAudioBody): List<AudioFileDto>

    @PUT("api/v1/audio/{id}/tags")
    suspend fun replaceTags(@Path("id") id: String, @Body body: SetTagsBody): AudioFileDto
}

data class CreateFolderBody(val name: String, val parentId: String? = null)
data class NameBody(val name: String)
data class MoveAudioBody(val folderId: String?)
data class BulkMoveAudioBody(val audioIds: List<String>, val folderId: String?)
data class SetTagsBody(val tagIds: List<String>)

enum class AudioSort { NEWEST, OLDEST, NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC }
```

## Kotlin-модели ответов

UUID и даты ISO 8601 можно принять как строки. Размеры передаются в байтах и помещаются в Kotlin `Long`.

```kotlin
data class UserDto(val id: String, val username: String?, val email: String?)

data class AudioUploadDto(
    val id: String,
    val requestId: String,
    val recordId: String,
    val status: AudioStatus,
)

enum class AudioStatus { RECEIVED, FORWARDED, PROCESSING, COMPLETED, FAILED }

data class AudioFileDto(
    val id: String,
    val requestId: String,
    val recordId: String,
    val processingJobId: String?,
    val originalFilename: String,
    val contentType: String,
    val sizeBytes: Long,
    val status: AudioStatus,
    val errorMessage: String?,
    val folder: FolderDto?,
    val tags: List<TagDto>,
    val resultAvailable: Boolean,
    val resultFilename: String?,
    val resultSizeBytes: Long?,
    val createdAt: String,
    val updatedAt: String,
)

data class FolderDto(
    val id: String,
    val name: String,
    val parentId: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class TagDto(val id: String, val name: String, val createdAt: String)
```

Пример ответа `200` для `GET /api/v1/auth/me`. Поля `username` и `email` могут быть `null`, если соответствующих claims нет в OIDC-сессии или токене.

```json
{
  "id": "8f02cd17-f1ef-4c0f-a6fd-9eb57a6f7281",
  "username": "user123",
  "email": "user@example.com"
}
```

## Аудиотека и поиск

| Параметр `GET /api/v1/audio` | Значение |
| --- | --- |
| `folderId` опущен | Все папки и корень |
| `folderId=null` | Только файлы в корне |
| `folderId=<uuid>` | Только файлы указанной папки |
| `tagId=<uuid>` | Только файлы с тегом. Можно сочетать с любым значением `folderId` |
| `sort=NEWEST` или `OLDEST` | По времени загрузки. По умолчанию `NEWEST` |
| `sort=NAME_ASC` или `NAME_DESC` | По имени без учёта регистра |
| `sort=SIZE_ASC` или `SIZE_DESC` | По размеру в байтах |

При равных значениях сортировки порядок определяется UUID файла. Некорректный UUID или неизвестный `sort` дают `400`. Если фильтры не нашли файлов, сервер возвращает `200` и `[]`.

Для экрана папки запросите её непосредственные подпапки и файлы. Используйте строку `"null"` в Retrofit для корня. Kotlin-значение `null` в `@Query` убирает параметр из URL.

```kotlin
val rootFolders = api.children("null")
val rootFiles = api.audio(folderId = "null", sort = AudioSort.NAME_ASC)
val nestedFolders = api.children(openedFolderId)
val folderFiles = api.audio(folderId = openedFolderId, tagId = selectedTagId)
```

`GET /api/v1/audio/search` ищет по транскриптам и анализу во всей аудиотеке пользователя. Все значимые слова должны встречаться в одном результате. Сервер возвращает до 50 файлов по релевантности. Передавайте `q` длиной 1–200 символов после удаления пробелов по краям и неотрицательный `offset` (по умолчанию `0`). Для следующей страницы прибавляйте 50, при изменении запроса начинайте с 0. Retrofit кодирует `q` при передаче через `@Query`. Историю поисковых строк при необходимости хранит клиент.

```kotlin
val firstPage = api.search("план релиза")
val secondPage = api.search("план релиза", offset = 50)
```

Пример ответа `GET /api/v1/audio/{id}`. Список, поиск и массовое перемещение возвращают массив объектов той же формы.

```json
{
  "id": "7747f2c4-c6c0-4b48-9e80-724bb8a85938",
  "requestId": "3e983854-c2ba-4d12-af8b-a8e2086c1370",
  "recordId": "a96725b4-df5e-41bb-92c3-3ec3786f8621",
  "processingJobId": "99237d90-40b0-46a4-a4bf-1604eb07c56f",
  "originalFilename": "interview.m4a",
  "contentType": "audio/mp4",
  "sizeBytes": 248000,
  "status": "FORWARDED",
  "errorMessage": null,
  "folder": null,
  "tags": [],
  "resultAvailable": false,
  "resultFilename": null,
  "resultSizeBytes": null,
  "createdAt": "2026-11-18T09:00:00Z",
  "updatedAt": "2026-11-18T09:00:01Z"
}
```

## Папки, теги и перемещение файлов

### Папки

Пример ответа `GET /api/v1/folders` с корневой папкой и её дочерней папкой. `GET /api/v1/folders/null/children` из этого набора вернёт только «Встречи», а `GET /api/v1/folders/6de80c36-6c03-4a2e-8c03-57f8f1bf1b6a/children` вернёт только «Интервью». Если подходящих папок нет, ответ содержит `[]`.

```json
[
  {
    "id": "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6a",
    "name": "Встречи",
    "parentId": null,
    "createdAt": "2026-11-18T08:00:00Z",
    "updatedAt": "2026-11-18T08:00:00Z"
  },
  {
    "id": "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6b",
    "name": "Интервью",
    "parentId": "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6a",
    "createdAt": "2026-11-18T09:00:00Z",
    "updatedAt": "2026-11-18T09:00:00Z"
  }
]
```

Отправьте `POST /api/v1/folders` для создания дочерней папки. Для корневой опустите `parentId` или передайте JSON `null`. Ответ `201` содержит объект папки.

```json
{
  "name": "Интервью",
  "parentId": "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6a"
}
```

Для переименования отправьте `PATCH /api/v1/folders/6de80c36-6c03-4a2e-8c03-57f8f1bf1b6b`.

```json
{"name":"Интервью с клиентами"}
```

Пример ответа `200`. UUID, родитель и содержимое папки сохраняются. Методы чтения папок возвращают массив объектов этой же формы. У папок верхнего уровня `parentId` равен `null`.

```json
{
  "id": "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6b",
  "name": "Интервью с клиентами",
  "parentId": "6de80c36-6c03-4a2e-8c03-57f8f1bf1b6a",
  "createdAt": "2026-11-18T09:00:00Z",
  "updatedAt": "2026-11-18T10:00:00Z"
}
```

`DELETE /api/v1/folders/{id}` возвращает `204` без тела и перемещает файлы из папки в корень. Если есть подпапки, сервер вернёт `409`. После удаления открытой папки вернитесь к её родителю или в корень и обновите списки.

### Теги

Для `POST /api/v1/tags` отправьте тело ниже. Ответ `201` содержит созданный тег.

```json
{"name":"Важное"}
```

Для переименования отправьте `PATCH /api/v1/tags/0fc4a6d0-00c8-4fed-81bb-2c9f0639525e`.

```json
{"name":"Срочное"}
```

Пример ответа `200`. UUID тега и привязки к файлам сохраняются. `GET /api/v1/tags` возвращает массив объектов этой же формы.

```json
{
  "id": "0fc4a6d0-00c8-4fed-81bb-2c9f0639525e",
  "name": "Срочное",
  "createdAt": "2026-11-18T08:00:00Z"
}
```

`DELETE /api/v1/tags/{id}` возвращает `204` без тела и удаляет привязки тега. Аудиофайлы остаются в библиотеке. Если удалённый тег был выбран в фильтре, сбросьте фильтр и обновите список.

Для создания и переименования передавайте непустое имя длиной до 100 символов. Сервер убирает пробелы по краям и сравнивает имена без учёта регистра среди папок с тем же родителем или среди тегов пользователя. Для недоступного или уже удалённого объекта сервер возвращает `404`, для занятого имени — `409`.

### Перемещение и назначение тегов

`PUT /api/v1/audio/{id}/folder` принимает UUID целевой папки. Чтобы переместить файл в корень, передайте `{"folderId":null}` или пустой объект `{}`.

```json
{"folderId":"6de80c36-6c03-4a2e-8c03-57f8f1bf1b6b"}
```

`PUT /api/v1/audio/folder` перемещает непустой набор файлов. В примере файлы перемещаются в корень. Если хотя бы один файл или целевая папка недоступны, сервер отвечает `404` и сохраняет прежнее расположение всех файлов. Сопоставляйте объекты в ответе по `id`.

```json
{
  "audioIds": ["7747f2c4-c6c0-4b48-9e80-724bb8a85938"],
  "folderId": null
}
```

`PUT /api/v1/audio/{id}/tags` полностью заменяет набор тегов. Для очистки отправьте `{"tagIds":[]}`. Поле `tagIds` обязательно. Если какой-либо тег недоступен, сервер вернёт `404`.

```json
{"tagIds":["0fc4a6d0-00c8-4fed-81bb-2c9f0639525e"]}
```

```kotlin
api.renameFolder(folderId, NameBody("Интервью с клиентами"))
api.renameTag(tagId, NameBody("Срочное"))
api.moveAudio(audioId, MoveAudioBody(folderId = null))
api.moveAudioBatch(BulkMoveAudioBody(listOf(audioId), folderId))
api.replaceTags(audioId, SetTagsBody(listOf(tagId)))
api.replaceTags(audioId, SetTagsBody(emptyList()))
```

## Загрузка, обработка и результат

1. Выберите файл через Storage Access Framework, например `ActivityResultContracts.OpenDocument()` с `audio/*`. Используйте `ContentResolver` для чтения `content://` URI. Для фоновой загрузки после перезапуска сохраните разрешение на чтение URI или скопируйте файл в приватный кэш.
2. Отправьте `POST /api/v1/audio` с обязательными частями `file` и `options`. Передайте `file` потоком или через временный файл. Часть `options` содержит JSON-объект с типом `application/json`. Multipart boundary формирует HTTP-клиент. Бэкенд сам создаёт `requestId` и `recordId`. Текущая конфигурация Spring ограничивает файл до `1000MB`, весь multipart-запрос до `1100MB`.
3. После `202 Accepted` сохраните `id` из ответа и опрашивайте `GET /api/v1/audio/{id}`, например каждые 2–5 секунд, пока экран видим. Остановитесь при `COMPLETED` или `FAILED`.
4. После `COMPLETED` загрузите результат авторизованным запросом `GET /api/v1/audio/{id}/result`.

### Multipart-запрос из Kotlin

В примере `cachedAudioFile` содержит копию файла, полученного через `ContentResolver`. Передайте имя исходного файла из метаданных URI и MIME-тип провайдера, если он доступен.

```kotlin
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

val filePart = MultipartBody.Part.createFormData(
    "file",
    displayName,
    cachedAudioFile.asRequestBody(
        mimeType?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType(),
    ),
)
val optionsPart = """{"diarization":true,"summary":true,"tasks":true}"""
    .toRequestBody("application/json".toMediaType())
val accepted = api.upload(filePart, optionsPart)
```

Пример ответа `202 Accepted`.

```json
{
  "id": "7747f2c4-c6c0-4b48-9e80-724bb8a85938",
  "requestId": "3e983854-c2ba-4d12-af8b-a8e2086c1370",
  "recordId": "a96725b4-df5e-41bb-92c3-3ec3786f8621",
  "status": "FORWARDED"
}
```

| Статус | Состояние |
| --- | --- |
| `RECEIVED` | Бэкенд сохранил файл |
| `FORWARDED` | ML-сервис принял задание |
| `PROCESSING` | Обработка продолжается |
| `COMPLETED` | Результат можно скачать |
| `FAILED` | Обработка завершилась ошибкой. Покажите `errorMessage`, если оно есть |

### Получение результата

`GET /api/v1/audio/{id}/result` возвращает `200` с `Content-Type: application/json` и заголовком `Content-Disposition: attachment` с именем файла. При `409` результат ещё недоступен. Для сохранения через Storage Access Framework используйте имя из `Content-Disposition` или `resultFilename` из карточки. Читайте поток в `Dispatchers.IO` и закрывайте `ResponseBody` после чтения. Поля `transcript` и `analysis` могут быть `null`.

```kotlin
import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

suspend fun saveResult(
    api: SonoraApi,
    audioId: String,
    resolver: ContentResolver,
    destination: Uri,
) = withContext<Unit>(Dispatchers.IO) {
    val response = api.result(audioId)
    if (!response.isSuccessful) {
        response.errorBody()?.close()
        throw HttpException(response)
    }
    checkNotNull(response.body()).use { body ->
        checkNotNull(resolver.openOutputStream(destination)).use { output ->
            body.byteStream().copyTo(output)
        }
    }
}
```

```json
{
  "jobId": "99237d90-40b0-46a4-a4bf-1604eb07c56f",
  "recordId": "a96725b4-df5e-41bb-92c3-3ec3786f8621",
  "status": "COMPLETED",
  "transcript": {
    "segments": [
      {"id":1,"speaker":"Speaker 1","start":0.0,"end":2.5,"text":"Обсудим план релиза"}
    ]
  },
  "analysis": {
    "summary": "Участники обсудили план релиза.",
    "topics": ["Релиз"],
    "keyPoints": ["Подготовить план"],
    "decisions": [],
    "tasks": [{"text":"Подготовить план","assignee":null}]
  }
}
```

## Коды ошибок

Используйте HTTP-статус для обработки ошибок. Формат тела ошибки может меняться.

| Код | Причина или действие |
| --- | --- |
| `400` | Проверьте UUID, параметры поиска, `sort`, имя или тело запроса |
| `401` | Проверьте сессию и обновите токен. При завершённой сессии предложите войти снова |
| `404` | Файл, папка или тег отсутствуют либо недоступны текущему пользователю |
| `409` | Различайте по операции занятое имя, подпапки при удалении и недоступный результат |
| `413` | Размер файла или multipart-запроса превышает лимит |
| `415` | Проверьте тип содержимого запроса и его частей, в том числе `application/json` для `options` |
| `502` | Сервис обработки недоступен |
| `503` | Circuit breaker временно остановил обращения к сервису обработки |
