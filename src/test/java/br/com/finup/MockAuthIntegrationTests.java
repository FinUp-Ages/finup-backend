package br.com.finup;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.model.User;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A aplicacao sobe com o profile {@code mock-auth} sem nenhuma configuracao do Cognito, e a
 * identidade passa a vir dos headers {@code X-Mock-Cognito-*}. E o modo de quem desenvolve sem
 * usuario no User Pool.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@AutoConfigureMockMvc
@ActiveProfiles("mock-auth")
class MockAuthIntegrationTests {

  @Autowired private MockMvc mockMvc;

  /** O H2 destes testes nao tem schema: o que se testa aqui e o caminho da identidade. */
  @MockitoBean private UserService userService;

  @Test
  @DisplayName("sem os headers de mock a resposta e 401, vinda do resolver e nao do token")
  void missingMockHeadersReturns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Identidade autenticada ausente ou incompleta"));
  }

  @Test
  @DisplayName("com os headers de mock a identidade chega ao service")
  void mockHeadersReachService() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("mock-sub-ana", null, "ana@exemplo.com");
    when(userService.findByAuthenticatedIdentity(identity))
        .thenReturn(User.createFromCognitoIdentity("mock-sub-ana", null, "ana@exemplo.com"));

    mockMvc
        .perform(
            get("/api/v1/users/me")
                .header("X-Mock-Cognito-Sub", "mock-sub-ana")
                .header("X-Mock-Cognito-Email", "ana@exemplo.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ana@exemplo.com"));
  }

  @Test
  @DisplayName("o Swagger documenta os headers de mock no lugar do Bearer")
  void openApiDocumentsMockHeaders() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.components.securitySchemes.mockCognitoSub.name")
                .value("X-Mock-Cognito-Sub"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").doesNotExist());
  }
}
