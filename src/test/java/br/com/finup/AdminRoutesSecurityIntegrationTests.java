package br.com.finup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.DashboardWidgetsResponse;
import br.com.finup.dto.MetricValueResponse;
import br.com.finup.dto.MetricsSummaryResponse;
import br.com.finup.dto.StudyCategoryResponse;
import br.com.finup.dto.StudyQuestionResponse;
import br.com.finup.dto.UserGrowthResponse;
import br.com.finup.model.QuestionStatus;
import br.com.finup.model.User;
import br.com.finup.service.AdminMetricsService;
import br.com.finup.service.DashboardWidgetService;
import br.com.finup.service.StudyCategoryService;
import br.com.finup.service.StudyQuestionService;
import br.com.finup.service.UserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Seguranca de <b>todas</b> as rotas de {@code /api/v1/admin/**}, de ponta a ponta: o token entra
 * pelo header {@code Authorization}, passa pelo filtro do Spring Security e os grupos do Cognito
 * decidem quem chega ao controller.
 *
 * <p>Para cada rota, em cada metodo HTTP, o contrato e:
 *
 * <ul>
 *   <li>sem token: 401;
 *   <li>token invalido ou expirado: 401;
 *   <li>token valido de usuario comum (sem o grupo {@code admin}): 403;
 *   <li>token do grupo {@code admin}: o status de sucesso da rota.
 * </ul>
 *
 * <p><b>Rota nova entra sozinha.</b> As rotas vem do {@link RequestMappingHandlerMapping} — o mesmo
 * mapa que o Spring usa para despachar — e nao de uma lista escrita a mao. Os cenarios de 401 e 403
 * valem para ela sem tocar neste arquivo. Para o cenario de sucesso, que precisa saber o corpo e o
 * status esperados, {@code everyAdminRouteHasASuccessCase} falha apontando a rota que falta:
 * acrescente uma linha em {@link #SUCCESS_CASES} (e, se a rota usar um service novo, um
 * {@code @MockitoBean} e um stub em {@link #stubServices()}, porque o H2 destes testes nao tem
 * schema).
 *
 * <p>So o {@link JwtDecoder} e trocado: ele devolve o token ja decodificado, sem chave nem rede. O
 * que o decoder real exige do token (assinatura, issuer, {@code client_id}) esta no {@code
 * CognitoConfigTest}. Daqui para frente e tudo real, inclusive a leitura de {@code cognito:groups}.
 *
 * <p>Independente da maquina: {@code @ActiveProfiles("test")} garante que {@code mock-auth} nao
 * esta ativo e as propriedades do Cognito e do CORS vao na propria anotacao, sem depender do {@code
 * .env} local nem de variaveis de ambiente.
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminRoutesSecurityIntegrationTests {

  private static final String ADMIN_PREFIX = "/api/v1/admin";
  private static final String TOKEN = "access-token";
  private static final String SUB = "cognito-sub-123";
  private static final String UNAUTHORIZED_DETAIL =
      "Token de acesso ausente, invalido ou expirado.";

  /** Valor que entra no lugar de {@code {id}} nas rotas com variavel de caminho. */
  private static final String PATH_VARIABLE_VALUE = "00000000-0000-0000-0000-000000000001";

  private static final String QUESTION_BODY =
      "{\"type\":\"LEARNING\",\"title\":\"Reserva\",\"body\":\"## Reserva de emergencia\"}";

  private static final String WIDGETS_BODY =
      "{\"widgets\":[{\"id\":\"NEW_CLIENTS\",\"enabled\":true,\"position\":0}]}";

  private static final String STUDY_CATEGORY_BODY =
      "{\"name\":\"CDB\",\"description\":\"Renda fixa\"}";

  /**
   * O que o admin recebe em cada rota: {@code "METODO /caminho"} para o status de sucesso e o corpo
   * da requisicao ({@code null} quando nao ha). Rota admin nova precisa de uma linha aqui. Usa
   * {@code Map.ofEntries} porque {@code Map.of} aceita no maximo 10 pares.
   */
  private static final Map<String, SuccessCase> SUCCESS_CASES =
      Map.ofEntries(
          Map.entry("GET /api/v1/admin/me", new SuccessCase(HttpStatus.OK, null)),
          Map.entry("GET /api/v1/admin/dashboard/widgets", new SuccessCase(HttpStatus.OK, null)),
          Map.entry(
              "PUT /api/v1/admin/dashboard/widgets", new SuccessCase(HttpStatus.OK, WIDGETS_BODY)),
          Map.entry("GET /api/v1/admin/metrics/summary", new SuccessCase(HttpStatus.OK, null)),
          Map.entry("GET /api/v1/admin/metrics/user-growth", new SuccessCase(HttpStatus.OK, null)),
          Map.entry(
              "GET /api/v1/admin/study-tracks/categories", new SuccessCase(HttpStatus.OK, null)),
          Map.entry(
              "POST /api/v1/admin/study-tracks/categories",
              new SuccessCase(HttpStatus.CREATED, STUDY_CATEGORY_BODY)),
          Map.entry(
              "PUT /api/v1/admin/study-tracks/categories/{id}",
              new SuccessCase(HttpStatus.OK, STUDY_CATEGORY_BODY)),
          Map.entry(
              "DELETE /api/v1/admin/study-tracks/categories/{id}",
              new SuccessCase(HttpStatus.NO_CONTENT, null)),
          Map.entry(
              "POST /api/v1/admin/study-tracks/categories/{categoryId}/questions",
              new SuccessCase(HttpStatus.CREATED, QUESTION_BODY)),
          Map.entry(
              "GET /api/v1/admin/study-tracks/categories/{categoryId}/questions",
              new SuccessCase(HttpStatus.OK, null)),
          Map.entry(
              "PUT /api/v1/admin/study-tracks/questions/{id}",
              new SuccessCase(HttpStatus.OK, QUESTION_BODY)),
          Map.entry(
              "DELETE /api/v1/admin/study-tracks/questions/{id}",
              new SuccessCase(HttpStatus.NO_CONTENT, null)));

  @Autowired private MockMvc mockMvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @MockitoBean private JwtDecoder jwtDecoder;

  /** O H2 destes testes nao tem schema: o que se testa aqui e quem chega ao service. */
  @MockitoBean private UserService userService;

  @MockitoBean private DashboardWidgetService dashboardWidgetService;

  @MockitoBean private AdminMetricsService adminMetricsService;

  @MockitoBean private StudyCategoryService studyCategoryService;

  @MockitoBean private StudyQuestionService studyQuestionService;

  @BeforeEach
  void stubServices() {
    when(userService.findByAuthenticatedIdentity(any()))
        .thenReturn(User.createFromCognitoIdentity(SUB, "Ana Souza", "ana@exemplo.com"));
    when(dashboardWidgetService.find(any())).thenReturn(new DashboardWidgetsResponse(List.of()));
    when(dashboardWidgetService.replace(any(), any()))
        .thenReturn(new DashboardWidgetsResponse(List.of()));
    MetricValueResponse zero = new MetricValueResponse(0.0, 0.0, 0.0);
    when(adminMetricsService.summary(any())).thenReturn(new MetricsSummaryResponse(zero, zero));
    when(adminMetricsService.userGrowth(any())).thenReturn(new UserGrowthResponse(0.0, List.of()));
    StudyCategoryResponse category =
        new StudyCategoryResponse(
            UUID.randomUUID(), "CDB", "Renda fixa", 0, 0, Instant.now(), Instant.now());
    when(studyCategoryService.listCategories()).thenReturn(List.of(category));
    when(studyCategoryService.create(any())).thenReturn(category);
    when(studyCategoryService.update(any(), any())).thenReturn(category);
    StudyQuestionResponse question =
        new StudyQuestionResponse(
            UUID.randomUUID(), QuestionStatus.DRAFT, Map.of(), null, Instant.now(), null);
    when(studyQuestionService.create(any(), any())).thenReturn(question);
    when(studyQuestionService.findByCategory(any())).thenReturn(List.of(question));
    when(studyQuestionService.update(any(), any())).thenReturn(question);
  }

  @Test
  @DisplayName("toda rota admin existente tem um caso de sucesso declarado (e nenhum sobra)")
  void everyAdminRouteHasASuccessCase() {
    Set<String> discovered = new HashSet<>();
    adminRoutes().forEach(route -> discovered.add(route.key()));

    assertThat(discovered).as("rotas admin descobertas em /api/v1/admin/**").isNotEmpty();

    Set<String> missing = new HashSet<>(discovered);
    missing.removeAll(SUCCESS_CASES.keySet());
    assertThat(missing)
        .as("rota admin sem caso de sucesso: acrescente uma linha em SUCCESS_CASES")
        .isEmpty();

    Set<String> stale = new HashSet<>(SUCCESS_CASES.keySet());
    stale.removeAll(discovered);
    assertThat(stale)
        .as("caso de sucesso de rota que nao existe mais: remova de SUCCESS_CASES")
        .isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("adminRoutes")
  @DisplayName("sem token devolve 401 em problem+json, sem chegar ao controller")
  void withoutTokenReturns401(AdminRoute route) throws Exception {
    mockMvc
        .perform(requestFor(route))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").value(UNAUTHORIZED_DETAIL));

    verifyNoInteractions(
        jwtDecoder,
        userService,
        dashboardWidgetService,
        adminMetricsService,
        studyCategoryService,
        studyQuestionService);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("adminRoutes")
  @DisplayName("token invalido devolve 401 em problem+json, sem expor o motivo")
  void invalidTokenReturns401(AdminRoute route) throws Exception {
    when(jwtDecoder.decode(TOKEN)).thenThrow(new BadJwtException("Signed JWT rejected"));

    mockMvc
        .perform(withToken(requestFor(route)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").value(UNAUTHORIZED_DETAIL));

    verifyNoInteractions(
        userService,
        dashboardWidgetService,
        adminMetricsService,
        studyCategoryService,
        studyQuestionService);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("adminRoutes")
  @DisplayName("token expirado devolve 401 em problem+json, sem expor o motivo")
  void expiredTokenReturns401(AdminRoute route) throws Exception {
    when(jwtDecoder.decode(TOKEN))
        .thenThrow(
            new JwtValidationException(
                "Jwt expired",
                List.of(
                    new OAuth2Error(
                        "invalid_token", "Jwt expired at 2026-01-01T00:00:00Z", null))));

    mockMvc
        .perform(withToken(requestFor(route)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").value(UNAUTHORIZED_DETAIL));

    verifyNoInteractions(
        userService,
        dashboardWidgetService,
        adminMetricsService,
        studyCategoryService,
        studyQuestionService);
  }

  @ParameterizedTest(name = "{0} | cognito:groups = {1}")
  @MethodSource("adminRoutesWithoutAdminGroup")
  @DisplayName("usuario comum (sem o grupo admin) devolve 403 em problem+json")
  void regularUserReturns403(AdminRoute route, String groupsLabel, List<String> groups)
      throws Exception {
    when(jwtDecoder.decode(TOKEN)).thenReturn(tokenWithGroups(groups));

    mockMvc
        .perform(withToken(requestFor(route)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403));

    verifyNoInteractions(
        userService,
        dashboardWidgetService,
        adminMetricsService,
        studyCategoryService,
        studyQuestionService);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("adminRoutes")
  @DisplayName("admin (grupo admin) recebe o status de sucesso da rota")
  void adminReturnsSuccessStatus(AdminRoute route) throws Exception {
    SuccessCase success = SUCCESS_CASES.get(route.key());
    assertThat(success)
        .as("rota admin sem caso de sucesso: acrescente uma linha em SUCCESS_CASES")
        .isNotNull();
    when(jwtDecoder.decode(TOKEN)).thenReturn(tokenWithGroups(List.of("admin")));

    MockHttpServletRequestBuilder request = withToken(requestFor(route));
    if (success.body() != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(success.body());
    }

    mockMvc.perform(request).andExpect(status().is(success.status().value()));
  }

  /** Todas as rotas de {@code /api/v1/admin/**} que o Spring mapeou, uma por metodo HTTP. */
  Stream<AdminRoute> adminRoutes() {
    List<AdminRoute> routes = new ArrayList<>();
    handlerMapping
        .getHandlerMethods()
        .keySet()
        .forEach(
            info -> {
              Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
              for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.equals(ADMIN_PREFIX) && !pattern.startsWith(ADMIN_PREFIX + "/")) {
                  continue;
                }
                if (methods.isEmpty()) {
                  // Mapeamento sem metodo responde a todos; o GET basta para provar o bloqueio.
                  routes.add(new AdminRoute(HttpMethod.GET, pattern));
                }
                methods.forEach(
                    method ->
                        routes.add(new AdminRoute(HttpMethod.valueOf(method.name()), pattern)));
              }
            });
    routes.sort(Comparator.comparing(AdminRoute::key));
    return routes.stream();
  }

  /** Cada rota combinada com cada tipo de token de usuario comum. */
  Stream<Arguments> adminRoutesWithoutAdminGroup() {
    return adminRoutes()
        .flatMap(
            route ->
                Stream.of(
                    Arguments.of(route, "sem o claim", null),
                    Arguments.of(route, "[]", List.of()),
                    Arguments.of(route, "[clientes]", List.of("clientes")),
                    Arguments.of(route, "[admins]", List.of("admins")),
                    Arguments.of(route, "[ADMIN]", List.of("ADMIN"))));
  }

  private static MockHttpServletRequestBuilder requestFor(AdminRoute route) {
    return request(route.method(), route.path());
  }

  private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
  }

  private static Jwt tokenWithGroups(List<String> groups) {
    Jwt.Builder token = accessToken();
    if (groups != null) {
      token.claim("cognito:groups", groups);
    }
    return token.build();
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

  /** Rota admin descoberta: metodo HTTP e o padrao de caminho como o Spring o mapeou. */
  record AdminRoute(HttpMethod method, String pattern) {

    /** Chave usada em {@link #SUCCESS_CASES}, ex.: {@code GET /api/v1/admin/me}. */
    String key() {
      return method + " " + pattern;
    }

    /** Caminho chamavel: variaveis como {@code {id}} recebem um valor qualquer. */
    String path() {
      return pattern.replaceAll("\\{[^}/]+}", PATH_VARIABLE_VALUE);
    }

    @Override
    public String toString() {
      return key();
    }
  }

  /** Status esperado do admin e corpo a enviar ({@code null} quando a rota nao tem corpo). */
  record SuccessCase(HttpStatus status, String body) {}
}
