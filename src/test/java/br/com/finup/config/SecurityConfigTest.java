package br.com.finup.config;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.controller.UserController;
import br.com.finup.model.User;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.security.ProblemDetailAuthenticationEntryPoint;
import br.com.finup.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regras de acesso com o Cognito ativo, exercitadas pelo {@link UserController} — os testes de
 * controller desligam os filtros de seguranca, entao e aqui que se confirma que eles existem.
 *
 * <p>{@code jwt()} coloca um token ja validado no contexto, sem precisar de chave nem de rede. A
 * validacao dos claims do token em si esta no {@link CognitoConfigTest}.
 *
 * <p>{@code @ActiveProfiles("test")}: garante que {@code mock-auth} nao esta ativo, mesmo que a
 * maquina de quem roda tenha {@code SPRING_PROFILES_ACTIVE=dev,mock-auth}.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, CognitoConfig.class, ProblemDetailAuthenticationEntryPoint.class})
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "finup.cognito.user-pool-id=us-east-1_TestPool",
      "finup.cognito.client-id=test-client",
      "finup.cors.allowed-origins=http://localhost:5173"
    })
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;

  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  @Test
  @DisplayName("sem token devolve 401 em RFC 7807, sem chegar ao controller")
  void missingTokenReturns401ProblemDetail() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.type").value("https://finup.com.br/errors/unauthorized"))
        .andExpect(jsonPath("$.traceId").exists())
        .andExpect(jsonPath("$.timestamp").exists());

    verifyNoInteractions(authenticatedIdentityResolver, userService);
  }

  @Test
  @DisplayName("token que nao e JWT devolve 401")
  void malformedTokenReturns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("https://finup.com.br/errors/unauthorized"));

    verifyNoInteractions(authenticatedIdentityResolver, userService);
  }

  @Test
  @DisplayName("token valido chega ao controller")
  void validTokenReachesController() throws Exception {
    AuthenticatedIdentity identity = new AuthenticatedIdentity("cognito-sub-123", null, null);
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);

    mockMvc
        .perform(get("/api/v1/users/me").with(jwt().jwt(token -> token.subject("cognito-sub-123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ana@exemplo.com"));
  }

  @Test
  @DisplayName("POST com token e sem CSRF e aceito: a API e stateless")
  void postWithTokenDoesNotRequireCsrf() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    when(authenticatedIdentityResolver.resolveCurrentWithAttributes()).thenReturn(identity);
    when(userService.createFromAuthenticatedIdentity(identity)).thenReturn(user);

    mockMvc.perform(post("/api/v1/users").with(jwt())).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("preflight de CORS passa sem token")
  void corsPreflightDoesNotRequireToken() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
  }
}
