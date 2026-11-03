package br.com.finup;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.model.User;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Acesso ao painel administrativo de ponta a ponta na aplicacao: o token entra pelo header {@code
 * Authorization}, passa pelo filtro do Spring Security e os grupos do Cognito decidem quem chega a
 * {@code /api/v1/admin/**}.
 *
 * <p>So o {@link JwtDecoder} e trocado: ele devolve o token ja decodificado, sem chave nem rede. O
 * que o decoder real exige do token (assinatura, issuer, {@code client_id}) esta no {@code
 * CognitoConfigTest}. Daqui para frente e tudo real — inclusive a leitura de {@code
 * cognito:groups}, que o {@code jwt()} do {@code SecurityConfigTest} nao exercita.
 *
 * <p>{@code @ActiveProfiles("test")}: garante que {@code mock-auth} nao esta ativo, mesmo que a
 * maquina de quem roda tenha {@code SPRING_PROFILES_ACTIVE=dev,mock-auth}.
 */
@SpringBootTest(
    properties = {
      "finup.cognito.user-pool-id=us-east-1_TestPool",
      "finup.cognito.client-id=test-client",
      "finup.cognito.admin-client-id=test-admin-client",
      "finup.cors.allowed-origins=https://painel.finup.test"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthIntegrationTests {

  private static final String ADMIN_ME = "/api/v1/admin/me";
  private static final String PANEL_ORIGIN = "https://painel.finup.test";
  private static final String TOKEN = "access-token";
  private static final String SUB = "cognito-sub-123";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  /** O H2 destes testes nao tem schema: o que se testa aqui e quem chega ao service. */
  @MockitoBean private UserService userService;

  @Test
  @DisplayName("token com o grupo admin devolve 200 com id, name, email e role ADMIN")
  void adminGroupReturnsAdminIdentity() throws Exception {
    User admin = User.createFromCognitoIdentity(SUB, "Ana Souza", "ana@exemplo.com");
    when(jwtDecoder.decode(TOKEN)).thenReturn(tokenWithGroups(List.of("admin")));
    when(userService.findByAuthenticatedIdentity(new AuthenticatedIdentity(SUB, null, null)))
        .thenReturn(admin);

    mockMvc
        .perform(withToken(get(ADMIN_ME)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(admin.getId().toString()))
        .andExpect(jsonPath("$.name").value("Ana Souza"))
        .andExpect(jsonPath("$.email").value("ana@exemplo.com"))
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.*", hasSize(4)));
  }

  @Test
  @DisplayName("sem token devolve 401 em problem+json, sem chegar ao service")
  void missingTokenReturns401() throws Exception {
    mockMvc
        .perform(get(ADMIN_ME))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").value("Token de acesso ausente, invalido ou expirado."));

    verifyNoInteractions(userService);
  }

  @Test
  @DisplayName("token recusado pelo decoder devolve 401 em problem+json, sem expor o motivo")
  void invalidTokenReturns401() throws Exception {
    when(jwtDecoder.decode(TOKEN)).thenThrow(new BadJwtException("Signed JWT rejected"));

    mockMvc
        .perform(withToken(get(ADMIN_ME)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").value("Token de acesso ausente, invalido ou expirado."));

    verifyNoInteractions(userService);
  }

  @Test
  @DisplayName("token valido sem o claim cognito:groups devolve 403 em problem+json")
  void tokenWithoutGroupsClaimReturns403() throws Exception {
    when(jwtDecoder.decode(TOKEN)).thenReturn(accessToken().build());

    mockMvc
        .perform(withToken(get(ADMIN_ME)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.detail").value("Voce nao tem permissao para acessar este recurso."))
        .andExpect(jsonPath("$.type").value("https://finup.com.br/errors/forbidden"))
        .andExpect(jsonPath("$.traceId").exists())
        .andExpect(jsonPath("$.timestamp").exists());

    verifyNoInteractions(userService);
  }

  @ParameterizedTest(name = "cognito:groups = {0}")
  @MethodSource("groupsWithoutAdmin")
  @DisplayName("token valido de quem nao esta no grupo admin devolve 403")
  void tokenWithoutAdminGroupReturns403(List<String> groups) throws Exception {
    when(jwtDecoder.decode(TOKEN)).thenReturn(tokenWithGroups(groups));

    mockMvc
        .perform(withToken(get(ADMIN_ME)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403));

    verifyNoInteractions(userService);
  }

  static Stream<List<String>> groupsWithoutAdmin() {
    return Stream.of(List.of(), List.of("clientes"), List.of("admins"), List.of("ADMIN"));
  }

  @Test
  @DisplayName("a regra vale para todo /api/v1/admin/**, inclusive widgets e categorias")
  void otherAdminRoutesAlsoRequireAdminGroup() throws Exception {
    when(jwtDecoder.decode(TOKEN)).thenReturn(accessToken().build());

    mockMvc
        .perform(withToken(get("/api/v1/admin/dashboard/widgets")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));

    mockMvc
        .perform(withToken(get("/api/v1/admin/study-tracks/categories")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));

    mockMvc
        .perform(
            withToken(
                post("/api/v1/admin/study-tracks/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"CDB\"}")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));

    mockMvc
        .perform(
            withToken(
                put("/api/v1/admin/study-tracks/categories/" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"CDB\"}")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));

    mockMvc
        .perform(withToken(delete("/api/v1/admin/study-tracks/categories/" + UUID.randomUUID())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));

    verifyNoInteractions(userService);
  }

  @Test
  @DisplayName("exigir admin no painel nao fecha o resto: usuario comum segue acessando o app")
  void regularUserStillReachesNonAdminRoutes() throws Exception {
    User user = User.createFromCognitoIdentity(SUB, "Ana Souza", "ana@exemplo.com");
    when(jwtDecoder.decode(TOKEN)).thenReturn(accessToken().build());
    when(userService.recordAccess(new AuthenticatedIdentity(SUB, null, null))).thenReturn(user);

    mockMvc
        .perform(withToken(get("/api/v1/users/me")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ana@exemplo.com"));
  }

  @Test
  @DisplayName("preflight da origem do painel e liberado, sem token, com o header Authorization")
  void preflightFromPanelOriginIsAllowed() throws Exception {
    mockMvc
        .perform(preflight(PANEL_ORIGIN, "authorization, content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PANEL_ORIGIN))
        .andExpect(
            header()
                .string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    containsStringIgnoringCase("authorization")));
  }

  @Test
  @DisplayName("preflight de origem fora da lista e recusado, sem liberar a origem")
  void preflightFromUnknownOriginIsRejected() throws Exception {
    mockMvc
        .perform(preflight("https://outro-site.test", "authorization"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  @DisplayName("preflight com header fora da lista e recusado, mesmo vindo do painel")
  void preflightWithUnlistedHeaderIsRejected() throws Exception {
    mockMvc
        .perform(preflight(PANEL_ORIGIN, "x-mock-cognito-sub"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  @DisplayName("o erro tambem sai com o CORS do painel, para o navegador entregar o corpo")
  void errorResponseCarriesCorsHeadersForThePanel() throws Exception {
    mockMvc
        .perform(get(ADMIN_ME).header(HttpHeaders.ORIGIN, PANEL_ORIGIN))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PANEL_ORIGIN))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
  }

  private static MockHttpServletRequestBuilder preflight(String origin, String requestHeaders) {
    return options(ADMIN_ME)
        .header(HttpHeaders.ORIGIN, origin)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestHeaders);
  }

  private static Jwt tokenWithGroups(List<String> groups) {
    return accessToken().claim("cognito:groups", groups).build();
  }

  /**
   * Access token como o Cognito emite para um usuario sem grupo. O scope {@code
   * aws.cognito.signin.user.admin} vem em todo token e nao tem relacao com o grupo admin.
   */
  private static Jwt.Builder accessToken() {
    Instant now = Instant.now();
    return Jwt.withTokenValue(TOKEN)
        .header("alg", "RS256")
        .subject(SUB)
        .claim("token_use", "access")
        .claim("client_id", "test-admin-client")
        .claim("scope", "aws.cognito.signin.user.admin")
        .issuedAt(now.minusSeconds(60))
        .expiresAt(now.plusSeconds(3600));
  }
}
