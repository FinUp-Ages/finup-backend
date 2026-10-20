package br.com.finup.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.config.SecurityConfig;
import br.com.finup.repository.TransactionRepository;
import br.com.finup.repository.UserRepository;
import br.com.finup.security.ProblemDetailAccessDeniedHandler;
import br.com.finup.security.ProblemDetailAuthenticationEntryPoint;
import br.com.finup.service.AdminMetricsService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Os dois endpoints de metricas, de ponta a ponta na camada web: filtro de seguranca, controller,
 * service real e handler de erros. So o que fica fora e o banco (repositorios simulados) e a
 * validacao criptografica do token (o {@link JwtDecoder} e simulado).
 *
 * <p>O token chega pelo header {@code Authorization}, passa pelo decoder simulado e pelo {@code
 * CognitoGroupsJwtAuthenticationConverter} de verdade: assim o teste confirma que o claim {@code
 * cognito:groups} vira a autoridade que a regra do {@code SecurityConfig} exige. O {@code jwt()} do
 * spring-security-test nao serviria para isso, porque monta as autoridades por conta propria.
 *
 * <p>{@code @ActiveProfiles("test")}: garante que {@code mock-auth} nao esta ativo.
 */
@WebMvcTest(AdminMetricsController.class)
@Import({
  SecurityConfig.class,
  ProblemDetailAuthenticationEntryPoint.class,
  ProblemDetailAccessDeniedHandler.class,
  AdminMetricsService.class
})
@ActiveProfiles("test")
@TestPropertySource(properties = "finup.cors.allowed-origins=http://localhost:5173")
class AdminMetricsControllerTest {

  private static final String SUMMARY = "/api/v1/admin/metrics/summary";
  private static final String USER_GROWTH = "/api/v1/admin/metrics/user-growth";
  private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private TransactionRepository transactionRepository;

  /** Registra o token no decoder simulado e devolve o header que o apresenta. */
  private String bearerWithGroups(String tokenValue, List<String> groups) {
    Jwt.Builder token =
        Jwt.withTokenValue(tokenValue)
            .header("alg", "RS256")
            .subject("cognito-sub-123")
            .issuedAt(NOW.minusSeconds(60))
            .expiresAt(NOW.plusSeconds(3_600));
    if (groups != null) {
      token.claim("cognito:groups", groups);
    }
    when(jwtDecoder.decode(tokenValue)).thenReturn(token.build());
    return "Bearer " + tokenValue;
  }

  private MockHttpServletRequestBuilder asAdmin(String url) {
    return get(url).header(HttpHeaders.AUTHORIZATION, bearerWithGroups("admin", List.of("admin")));
  }

  // ----------------------------------------------------------------- 200

  @Test
  @DisplayName("summary: admin recebe 200 no formato exato do contrato C3")
  void summaryReturnsContractShapeForAdmin() throws Exception {
    Instant currentStart = Instant.parse("2026-09-15T15:00:00Z");
    Instant previousStart = Instant.parse("2026-08-16T15:00:00Z");
    when(transactionRepository.countActiveUsersBetween(currentStart, NOW)).thenReturn(320L);
    when(transactionRepository.countActiveUsersBetween(previousStart, currentStart))
        .thenReturn(302L);
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(currentStart, NOW))
        .thenReturn(24L);
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            previousStart, currentStart))
        .thenReturn(25L);

    mockMvc
        .perform(asAdmin(SUMMARY).param("period", "30d"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "activeUsers": {"value": 320.0, "previousValue": 302.0, "changePct": 6.0},
                      "newUsers":    {"value": 24.0,  "previousValue": 25.0,  "changePct": -4.0}
                    }
                    """,
                    true));
  }

  @Test
  @DisplayName("summary: periodo anterior zerado responde 200 com changePct 0.0")
  void summaryWithEmptyPreviousPeriodReturns200() throws Exception {
    Instant currentStart = Instant.parse("2026-10-08T15:00:00Z");
    when(transactionRepository.countActiveUsersBetween(currentStart, NOW)).thenReturn(4L);

    mockMvc
        .perform(asAdmin(SUMMARY).param("period", "7d"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeUsers.value").value(4.0))
        .andExpect(jsonPath("$.activeUsers.previousValue").value(0.0))
        .andExpect(jsonPath("$.activeUsers.changePct").value(0.0));
  }

  @Test
  @DisplayName("summary: quem esta em varios grupos, incluindo admin, passa")
  void summaryAllowsAdminAmongOtherGroups() throws Exception {
    mockMvc
        .perform(
            get(SUMMARY)
                .param("period", "90d")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    bearerWithGroups("multi", List.of("beta-testers", "admin"))))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("user-growth: admin recebe 200 com 6 meses por padrao, em ordem, inclusive vazios")
  void userGrowthDefaultsToSixMonthsIncludingEmptyOnes() throws Exception {
    mockMvc
        .perform(asAdmin(USER_GROWTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.growthPct").value(0.0))
        .andExpect(jsonPath("$.points", hasSize(6)))
        .andExpect(jsonPath("$.points[0].month").value("2026-05"))
        .andExpect(jsonPath("$.points[0].newUsers").value(0))
        .andExpect(jsonPath("$.points[0].totalUsers").value(0))
        .andExpect(jsonPath("$.points[5].month").value("2026-10"));
  }

  @Test
  @DisplayName("user-growth: months informado define o tamanho da serie")
  void userGrowthHonorsMonths() throws Exception {
    mockMvc
        .perform(asAdmin(USER_GROWTH).param("months", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.points", hasSize(3)))
        .andExpect(jsonPath("$.points[0].month").value("2026-08"));
  }

  // ----------------------------------------------------------------- 400

  @Test
  @DisplayName("summary: period fora do enum devolve 400 dizendo o que e aceito")
  void summaryRejectsPeriodOutsideTheEnum() throws Exception {
    mockMvc
        .perform(asAdmin(SUMMARY).param("period", "semana"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail", containsString("'period'")))
        .andExpect(jsonPath("$.detail", containsString("7d, 30d, 90d")))
        .andExpect(jsonPath("$.traceId").exists());

    verifyNoInteractions(userRepository, transactionRepository);
  }

  @Test
  @DisplayName("summary: period ausente devolve 400")
  void summaryRejectsMissingPeriod() throws Exception {
    mockMvc
        .perform(asAdmin(SUMMARY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("obrigatorio")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "-1", "0", "25", "6.5"})
  @DisplayName("user-growth: months nao numerico, negativo ou fora de 1 a 24 devolve 400")
  void userGrowthRejectsInvalidMonths(String months) throws Exception {
    mockMvc
        .perform(asAdmin(USER_GROWTH).param("months", months))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", containsString("'months'")))
        .andExpect(jsonPath("$.detail", containsString("1 a 24")));

    verifyNoInteractions(userRepository, transactionRepository);
  }

  // ----------------------------------------------------------------- 401

  @ParameterizedTest
  @ValueSource(strings = {SUMMARY + "?period=30d", USER_GROWTH})
  @DisplayName("sem token devolve 401 em RFC 7807, sem chegar ao banco")
  void withoutTokenReturns401(String url) throws Exception {
    mockMvc
        .perform(get(url))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.type").value("https://finup.com.br/errors/unauthorized"));

    verifyNoInteractions(userRepository, transactionRepository);
  }

  // ----------------------------------------------------------------- 403

  @ParameterizedTest
  @ValueSource(strings = {SUMMARY + "?period=30d", USER_GROWTH})
  @DisplayName("autenticado fora do grupo admin recebe 403 em RFC 7807, sem chegar ao banco")
  void authenticatedWithoutAdminGroupReturns403(String url) throws Exception {
    mockMvc
        .perform(
            get(url)
                .header(HttpHeaders.AUTHORIZATION, bearerWithGroups("comum", List.of("clientes"))))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.type").value("https://finup.com.br/errors/forbidden"))
        .andExpect(jsonPath("$.traceId").exists())
        .andExpect(jsonPath("$.timestamp").exists());

    verifyNoInteractions(userRepository, transactionRepository);
  }

  @Test
  @DisplayName("403 tambem para quem nao esta em nenhum grupo (token sem o claim)")
  void tokenWithoutGroupsClaimReturns403() throws Exception {
    mockMvc
        .perform(
            get(USER_GROWTH).header(HttpHeaders.AUTHORIZATION, bearerWithGroups("sem-grupo", null)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(userRepository, transactionRepository);
  }

  @Test
  @DisplayName(
      "403 para grupo com nome parecido: o Cognito diferencia maiusculas, entao Admin != admin")
  void groupNameIsCaseSensitive() throws Exception {
    mockMvc
        .perform(
            get(SUMMARY)
                .param("period", "30d")
                .header(HttpHeaders.AUTHORIZATION, bearerWithGroups("maiuscula", List.of("Admin"))))
        .andExpect(status().isForbidden());
  }
}
