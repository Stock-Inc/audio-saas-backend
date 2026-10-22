
package backend.app.api.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${KEYCLOAK_CLIENT_SECRET}") String secret
    ) {
        String publicUrl = "http://localhost:9090/realms/demo";
        String dockerUrl = "http://keycloak:8080/realms/demo";

        ClientRegistration client = ClientRegistration
                .withRegistrationId("keycloak")
                .clientId("spring-app")
                .clientSecret(secret)
                .clientAuthenticationMethod(
                        ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                )
                .authorizationGrantType(
                        AuthorizationGrantType.AUTHORIZATION_CODE
                )
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri(publicUrl)
                .authorizationUri(
                        publicUrl + "/protocol/openid-connect/auth"
                )
                .tokenUri(
                        dockerUrl + "/protocol/openid-connect/token"
                )
                .jwkSetUri(
                        dockerUrl + "/protocol/openid-connect/certs"
                )
                .userInfoUri(
                        dockerUrl + "/protocol/openid-connect/userinfo"
                )
                .userNameAttributeName("sub")
                .clientName("Keycloak")
                .build();

        return new InMemoryClientRegistrationRepository(client);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        return http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/v1/auth/register")
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(Customizer.withDefaults())
                .build();
    }
}
