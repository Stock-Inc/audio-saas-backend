# Sonora API для Android на Kotlin и Jetpack Compose

Приложение получает токены у Keycloak через Authorization Code + PKCE и передаёт access token в заголовке `Authorization: Bearer <access_token>` при вызове backend API. Схемы JSON и примеры ответов общие для клиентов. [Контракт API](frontend-api.md)

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

### Обновление токена и выход

- Сериализованный `AuthState` содержит токены. Храните его в приватном зашифрованном хранилище с ключом из Android Keystore. Исключите токены из логов, аналитики и резервных копий.
- Перед запросом получайте свежий access token через `AuthState.performActionWithFreshTokens` и сохраняйте состояние после refresh. Синхронизируйте refresh для параллельных запросов. Access token уходит только на origin API. Refresh token используется при обмене с Keycloak, ID token может использоваться при выходе из Keycloak.
- Если API вернул `401`, обновите токен и повторите безопасный запрос один раз. При просроченной сессии или `invalid_grant` очистите состояние и предложите вход. При временной сетевой ошибке сохраните сессию и покажите возможность повторить запрос. Для загрузки после обрыва соединения сначала проверьте библиотеку, поскольку сервер мог принять файл.
- Для локального выхода очистите состояние и кэш аккаунта. Для завершения браузерной SSO-сессии используйте `end_session_endpoint` из discovery и RP-Initiated Logout. Если нужен возврат из браузера в приложение после выхода, отдельно зарегистрируйте точный `post_logout_redirect_uri` для `sonora-android` и добавьте обработчик URI в Android. Экспорт realm задаёт URI возврата для входа. [Выход через Keycloak](https://www.keycloak.org/securing-apps/oidc-layers#_logout_endpoint)

## Аудиотека и навигация

| Экран или действие | Запрос |
| --- | --- |
| Все аудиофайлы | `GET /api/v1/audio` |
| Папки в корне | `GET /api/v1/folders/null/children` |
| Файлы в корне | `GET /api/v1/audio?folderId=null` |
| Открыть папку | `GET /api/v1/folders/{id}/children` и `GET /api/v1/audio?folderId={id}` |
| Фильтр по тегу | `GET /api/v1/audio?tagId={id}`. Можно добавить `folderId=null` или UUID папки |
| Поиск | `GET /api/v1/audio/search?q={text}&offset={offset}`, до 50 результатов за запрос |
| Карточка аудио | `GET /api/v1/audio/{id}` |
| Результат | `GET /api/v1/audio/{id}/result` |

Для Retrofit различайте Kotlin-значение `null`, которое убирает query-параметр, и строку `"null"`, которая выбирает файлы в корне. UUID передавайте строками. К корневому экрану можно вернуться через обычную навигацию Compose, сохраняя идентификатор выбранной папки в состоянии маршрута.

```kotlin
interface LibraryApi {
    @GET("api/v1/audio")
    suspend fun audio(
        @Query("folderId") folderId: String? = null,
        @Query("tagId") tagId: String? = null,
    ): List<AudioFileDto>

    @GET("api/v1/folders/{id}/children")
    suspend fun folders(@Path("id") id: String): List<FolderDto>
}

// Корень библиотеки
val rootFiles = api.audio(folderId = "null")
val rootFolders = api.folders("null")
```

DTO берите из [контракта API](frontend-api.md#аудиотека-и-фильтры) или из `GET /v3/api-docs`. Мутации папок, перемещение файлов и назначение тегов описаны вместе с телами запросов. [Папки и теги](frontend-api.md#папки-и-теги)

## Загрузка, обработка и результат

1. Выберите файл через Storage Access Framework, например `ActivityResultContracts.OpenDocument()` с `audio/*`. Используйте `ContentResolver` для чтения `content://` URI. Для фоновой загрузки после перезапуска сохраните разрешение на чтение URI или скопируйте файл в приватный кэш.
2. Отправьте `POST /api/v1/audio` с multipart-частью `file` и JSON-частью `options` с типом `application/json`. Например, `{"diarization":true,"summary":true,"tasks":true}`. Передавайте файл потоком или через временный файл, чтобы не загружать его целиком в память. Multipart boundary формирует HTTP-клиент. [Точный контракт загрузки](frontend-api.md#загрузка-аудио)
3. После `202 Accepted` сохраните `id` из ответа и периодически вызывайте `GET /api/v1/audio/{id}`, например каждые 2–5 секунд, пока экран видим. Остановитесь при `COMPLETED` или `FAILED`. Для `FAILED` покажите `errorMessage`. [Статусы обработки](frontend-api.md#статусы-и-polling)
4. После `COMPLETED` получите результат авторизованным запросом. Для сохранения вложения используйте `Content-Disposition` и Android Storage Access Framework. [Формат результата](frontend-api.md#результат)

Для экрана библиотеки показывайте пустой список как обычное состояние. Для `400` проверьте параметры, для `401` обработайте авторизацию, для `404` покажите, что файл или папка недоступны. Сетевые ошибки и `5xx` показывайте с возможностью повтора. [Коды API](frontend-api.md#ошибки)
