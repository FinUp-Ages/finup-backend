package br.com.finup.config;

import br.com.finup.security.CognitoGroupsAuthoritiesConverter;
import br.com.finup.security.ProblemDetailAccessDeniedHandler;
import br.com.finup.security.ProblemDetailAuthenticationEntryPoint;
import br.com.finup.security.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Quem pode chamar o que.
 *
 * <p>Com o Cognito (padrao): toda rota exige {@code Authorization: Bearer <access token>}, exceto
 * Swagger, contrato OpenAPI, health check e a consulta de e-mail do cadastro ({@code POST
 * /api/v1/users/email-availability}). Token ausente ou invalido vira 401 em RFC 7807, no mesmo
 * formato do {@code ApiExceptionHandler}.
 *
 * <p>{@code /api/v1/admin/**} exige, alem do token, o papel {@link Role#ADMIN}: o usuario precisa
 * estar no grupo {@code admin} do User Pool, lido do claim {@code cognito:groups} pelo {@link
 * CognitoGroupsAuthoritiesConverter}. Token valido sem o grupo vira 403, tambem em RFC 7807. A
 * regra fica so aqui — nenhum controller de admin confere papel por conta propria.
 *
 * <p>Com o profile {@code mock-auth}: nada e barrado aqui — a identidade vem dos headers {@code
 * X-Mock-Cognito-*}, e quem devolve 401 quando eles faltam e o {@code
 * MockAuthenticatedIdentityResolver}. Nao ha grupo nesses headers, entao as rotas de admin tambem
 * ficam abertas. Esse profile nunca vale junto de {@code prod}: nenhuma das duas cadeias e criada,
 * e a aplicacao nao sobe.
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

  /** Consulta de e-mail do cadastro: publica so no POST, porque a pessoa ainda nao tem token. */
  private static final String EMAIL_AVAILABILITY_PATH = "/api/v1/users/email-availability";

  private static final String ADMIN_PATHS = "/api/v1/admin/**";

  @Bean
  @Profile("!mock-auth")
  public SecurityFilterChain cognitoSecurityFilterChain(
      HttpSecurity http,
      ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
      ProblemDetailAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    applyCommonSettings(http);
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, EMAIL_AVAILABILITY_PATH)
                    .permitAll()
                    .requestMatchers(ADMIN_PATHS)
                    .hasRole(Role.ADMIN.name())
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(cognitoAuthenticationConverter()))
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  @Bean
  @Profile("mock-auth & !prod")
  public SecurityFilterChain mockSecurityFilterChain(HttpSecurity http) throws Exception {
    applyCommonSettings(http);
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  /** Os papeis do usuario vem dos grupos do Cognito, e nao do claim {@code scope}. */
  private static JwtAuthenticationConverter cognitoAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new CognitoGroupsAuthoritiesConverter());
    return converter;
  }

  private static void applyCommonSettings(HttpSecurity http) throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
  }
}
