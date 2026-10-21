package br.com.finup.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.User;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * So o controller: {@code addFilters = false} desliga o Spring Security nesta fatia. Quem pode
 * chegar aqui (401 sem token, 403 sem o grupo admin) esta no {@code AdminAuthIntegrationTests}.
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminController.class)
class AdminControllerTest {

  private static final String URL = "/api/v1/admin/me";
  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("cognito-sub-admin", null, null);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;
  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  @BeforeEach
  void mockIdentity() {
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(IDENTITY);
  }

  @Test
  @DisplayName("GET devolve 200 com id, name, email e role ADMIN, e nada alem disso")
  void meReturnsAdminIdentity() throws Exception {
    User admin =
        User.createFromCognitoIdentity("cognito-sub-admin", "Ana Souza", "Ana@Exemplo.com");
    when(userService.findByAuthenticatedIdentity(IDENTITY)).thenReturn(admin);

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(admin.getId().toString()))
        .andExpect(jsonPath("$.name").value("Ana Souza"))
        .andExpect(jsonPath("$.email").value("ana@exemplo.com"))
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.*", hasSize(4)));
  }

  @Test
  @DisplayName("GET de administrador sem usuario local devolve 404 em RFC 7807")
  void meWithoutLocalUserReturns404() throws Exception {
    when(userService.findByAuthenticatedIdentity(IDENTITY))
        .thenThrow(
            new ResourceNotFoundException("Usuario nao encontrado para a identidade autenticada."));

    mockMvc
        .perform(get(URL))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(
            jsonPath("$.detail").value("Usuario nao encontrado para a identidade autenticada."));
  }
}
