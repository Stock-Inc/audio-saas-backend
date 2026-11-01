Sonora Keycloak 25.0.5 SAFE CSS-ONLY theme

Why this archive is different:
* No login.ftl override: it inherits the official Keycloak login.ftl (fixes custom FreeMarker template failures).
* Only CSS, logo SVG, and UI labels are customized.

1. Unzip next to docker-compose.yml, so keycloak-themes/sonora/login/theme.properties exists.
2. In the keycloak service add volume:
   volumes:
     - ./keycloak-themes:/opt/keycloak/themes:ro
3. For development disable theme caches (optional):
   command:
     - start-dev
     - --spi-theme--static-max-age=-1
     - --spi-theme--cache-themes=false
     - --spi-theme--cache-templates=false
4. docker compose up -d --force-recreate keycloak
5. In Keycloak admin: Realm demo -> Realm settings -> Themes -> Login theme = sonora -> Save.
6. Visit http://localhost:8081/oauth2/authorization/keycloak in private browser window.

IMPORTANT: This safe first version does NOT inject a direct React registration link.
When User registration is turned off in Realm settings, the native registration link is hidden. Your React registration page works independently of this theme.
The design uses Newsreader / IBM Plex Mono fallbacks and no font files.
