package br.com.finup.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.config.SecurityConfig;
import br.com.finup.model.User;
import br.com.finup.repository.UserRepository;
import br.com.finup.security.ProblemDetailAccessDeniedHandler;
import br.com.finup.security.ProblemDetailAuthenticationEntryPoint;
import br.com.finup.service.AdminUserService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
 * {@code GET /api/v1/admin/users}, de ponta a ponta na camada web: filtro de seguranca, controller,
 * service real e handler de erros. So o repositorio e simulado.
 *
 * <p>O token chega pelo header {@code Authorization}, passa pelo decoder simulado e pelo {@code
 * CognitoGroupsJwtAuthenticationConverter} de verdade — mesmo padrao do {@code
 * AdminMetricsControllerTest}.
 */
@WebMvcTest(AdminUserController.class)
@Import({
  SecurityConfig.class,
  ProblemDetailAuthenticationEntryPoint.class,
  ProblemDetailAccessDeniedHandler.class,
  AdminUserService.class
})
@ActiveProfiles("test")
@TestPropertySource(properties = "finup.cors.allowed-origins=http://localhost:5173")
class AdminUserControllerTest {

  private static final String USERS = "/api/v1/admin/users";
  private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneId.of("UTC"));
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private UserRepository userRepository;

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
  @DisplayName("admin recebe 200 no formato exato do contrato C2")
  void returnsContractShapeForAdmin() throws Exception {
    User user = User.createFromCognitoIdentity("sub-1", "Carlos Jose", "carlos.j@email.com");
    when(userRepository.search(any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(asAdmin(USERS))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(user.getId().toString()))
        .andExpect(jsonPath("$.content[0].name").value("Carlos Jose"))
        .andExpect(jsonPath("$.content[0].email").value("carlos.j@email.com"))
        .andExpect(jsonPath("$.content[0].status").value("INACTIVE"))
        .andExpect(jsonPath("$.content[0].lastSeenAt").doesNotExist())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  @DisplayName("status ACTIVE chega ao repositorio como filtro true")
  void statusActiveIsForwardedAsTrue() throws Exception {
    when(userRepository.search(any(), org.mockito.ArgumentMatchers.eq(true), any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc.perform(asAdmin(USERS).param("status", "ACTIVE")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("page e size informados chegam ao repositorio")
  void pageAndSizeAreForwarded() throws Exception {
    when(userRepository.search(any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

    mockMvc
        .perform(asAdmin(USERS).param("page", "2").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.size").value(5));
  }

  // ----------------------------------------------------------------- 400

  @Test
  @DisplayName("status fora de ACTIVE/INACTIVE devolve 400 dizendo o que e aceito")
  void rejectsInvalidStatus() throws Exception {
    mockMvc
        .perform(asAdmin(USERS).param("status", "banido"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail", containsString("'status'")))
        .andExpect(jsonPath("$.detail", containsString("ACTIVE, INACTIVE")));

    verifyNoInteractions(userRepository);
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "-1"})
  @DisplayName("page invalido devolve 400")
  void rejectsInvalidPage(String page) throws Exception {
    mockMvc
        .perform(asAdmin(USERS).param("page", page))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("'page'")));

    verifyNoInteractions(userRepository);
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "0", "101"})
  @DisplayName("size invalido devolve 400")
  void rejectsInvalidSize(String size) throws Exception {
    mockMvc
        .perform(asAdmin(USERS).param("size", size))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("'size'")));

    verifyNoInteractions(userRepository);
  }

  // ----------------------------------------------------------------- 401

  @Test
  @DisplayName("sem token devolve 401 em RFC 7807, sem chegar ao banco")
  void withoutTokenReturns401() throws Exception {
    mockMvc
        .perform(get(USERS))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.type").value("https://finup.com.br/errors/unauthorized"));

    verifyNoInteractions(userRepository);
  }

  // ----------------------------------------------------------------- 403

  @Test
  @DisplayName("autenticado fora do grupo admin recebe 403 em RFC 7807, sem chegar ao banco")
  void authenticatedWithoutAdminGroupReturns403() throws Exception {
    mockMvc
        .perform(
            get(USERS)
                .header(HttpHeaders.AUTHORIZATION, bearerWithGroups("comum", List.of("clientes"))))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.type").value("https://finup.com.br/errors/forbidden"));

    verifyNoInteractions(userRepository);
  }
}
