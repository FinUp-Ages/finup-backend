package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.exception.EmailAlreadyRegisteredException;
import br.com.finup.exception.MissingAuthenticatedIdentityException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.exception.UserAlreadyRegisteredException;
import br.com.finup.model.FinancialProfile;
import br.com.finup.model.User;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} sobe so a camada web — sem banco, sem service real. O {@code
 * ApiExceptionHandler} entra junto porque {@code @RestControllerAdvice} faz parte da fatia, entao
 * estes testes verificam de verdade o contrato de erro, e nao uma simulacao dele.
 *
 * <p>{@link AuthenticatedIdentityResolver} tambem e mockado aqui: o controller depende da
 * interface, nao da implementacao mockada real (que le headers de request) — entao o teste nao
 * precisa saber nada sobre esses headers.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;

  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  @Test
  @DisplayName("POST com identidade resolvida devolve 201 com Location /me e o corpo do usuario")
  void validCreationReturns201() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User user =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.createFromAuthenticatedIdentity(identity)).thenReturn(user);

    mockMvc
        .perform(post("/api/v1/users"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/users/me"))
        .andExpect(jsonPath("$.id").value(user.getId().toString()))
        .andExpect(jsonPath("$.name").value("Ana Souza"))
        .andExpect(jsonPath("$.email").value("ana@exemplo.com"));
  }

  @Test
  @DisplayName("POST sem identidade autenticada devolve 401 em RFC 7807")
  void missingIdentityReturns401() throws Exception {
    when(authenticatedIdentityResolver.resolveCurrent())
        .thenThrow(new MissingAuthenticatedIdentityException());

    mockMvc.perform(post("/api/v1/users")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("identidade ja cadastrada vira 409, e nao 500")
  void duplicateIdentityReturns409() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.createFromAuthenticatedIdentity(any()))
        .thenThrow(new UserAlreadyRegisteredException());

    mockMvc
        .perform(post("/api/v1/users"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  @DisplayName("e-mail ja usado por outra identidade vira 409")
  void duplicateEmailReturns409() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-456", "Outra Ana", "ana@exemplo.com");
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.createFromAuthenticatedIdentity(any()))
        .thenThrow(new EmailAlreadyRegisteredException("ana@exemplo.com"));

    mockMvc
        .perform(post("/api/v1/users"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("Ja existe um usuario cadastrado com o e-mail ana@exemplo.com"));
  }

  @Test
  @DisplayName("GET /me devolve 200 com o usuario da identidade autenticada")
  void meReturns200() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User user =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);

    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ana@exemplo.com"));
  }

  @Test
  @DisplayName("GET /me antes da Etapa 1 devolve 404, e nao 500")
  void meBeforeCreationReturns404() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-999", "Ana Souza", "ana@exemplo.com");
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.findByAuthenticatedIdentity(identity))
        .thenThrow(
            new ResourceNotFoundException("Usuario nao encontrado para a identidade autenticada."));

    mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PATCH de informacoes complementares devolve 200 com o usuario atualizado")
  void validAdditionalInfoUpdateReturns200() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User user =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    user.applyAdditionalInfo(
        LocalDate.of(1998, 4, 12), new BigDecimal("3500.00"), FinancialProfile.MODERATE);
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.updateAdditionalInfo(
            eq(identity),
            eq(LocalDate.of(1998, 4, 12)),
            eq(new BigDecimal("3500.00")),
            eq(FinancialProfile.MODERATE)))
        .thenReturn(user);

    mockMvc
        .perform(
            patch("/api/v1/users/me/additional-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"birthDate\":\"1998-04-12\",\"monthlyIncome\":3500.00,"
                        + "\"financialProfile\":\"MODERATE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.birthDate").value("1998-04-12"))
        .andExpect(jsonPath("$.monthlyIncome").value(3500.00))
        .andExpect(jsonPath("$.financialProfile").value("MODERATE"));
  }

  @Test
  @DisplayName("PATCH com data de nascimento no futuro devolve 400")
  void invalidAdditionalInfoUpdateReturns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/me/additional-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"birthDate\":\"2999-01-01\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PATCH com perfil financeiro invalido devolve 400")
  void invalidFinancialProfileReturns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/me/additional-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"financialProfile\":\"NAO_EXISTE\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PATCH com renda negativa devolve 400")
  void negativeMonthlyIncomeReturns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/me/additional-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"monthlyIncome\":-100}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PATCH antes da Etapa 1 devolve 404, e nao 500")
  void additionalInfoUpdateBeforeCreationReturns404() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-999", "Ana Souza", "ana@exemplo.com");
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.updateAdditionalInfo(any(), any(), any(), any()))
        .thenThrow(
            new ResourceNotFoundException("Usuario nao encontrado para a identidade autenticada."));

    mockMvc
        .perform(
            patch("/api/v1/users/me/additional-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"financialProfile\":\"MODERATE\"}"))
        .andExpect(status().isNotFound());
  }
}
