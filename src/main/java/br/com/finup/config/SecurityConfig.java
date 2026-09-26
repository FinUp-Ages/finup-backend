package br.com.finup.config;

import br.com.finup.security.ProblemDetailAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Quem pode chamar o que.
 *
 * <p>Com o Cognito (padrao): toda rota exige {@code Authorization: Bearer <access token>}, exceto
 * Swagger, contrato OpenAPI e health check. Token ausente ou invalido vira 401 em RFC 7807, no
 * mesmo formato do {@code ApiExceptionHandler}.
 *
 * <p>Com o profile {@code mock-auth}: nada e barrado aqui — a identidade vem dos headers {@code
 * X-Mock-Cognito-*}, e quem devolve 401 quando eles faltam e o {@code
 * MockAuthenticatedIdentityResolver}. Esse profile nunca vale junto de {@code prod}: nenhuma das
 * duas cadeias e criada, e a aplicacao nao sobe.
 *
 * <p>Nos dois casos: sem sessao (cada requisicao se autentica sozinha), sem CSRF (nao ha cookie de
 * sessao para ser explorado) e com o CORS do {@link CorsConfig} — inclusive no preflight, que o
 * navegador manda sem token.
 */
@Configuration
public class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health", "/error"
  };

  @Bean
  @Profile("!mock-auth")
  public SecurityFilterChain cognitoSecurityFilterChain(
      HttpSecurity http, ProblemDetailAuthenticationEntryPoint authenticationEntryPoint)
      throws Exception {
    applyCommonSettings(http);
    http.authorizeHttpRequests(
            auth -> auth.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(authenticationEntryPoint))
        .exceptionHandling(
            exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint));
    return http.build();
  }

  @Bean
  @Profile("mock-auth & !prod")
  public SecurityFilterChain mockSecurityFilterChain(HttpSecurity http) throws Exception {
    applyCommonSettings(http);
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  private static void applyCommonSettings(HttpSecurity http) throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
  }
}
