package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.exception.EmailAlreadyRegisteredException;
import br.com.finup.exception.IdentityProviderUnavailableException;
import br.com.finup.exception.MissingAuthenticatedIdentityException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.exception.UserAlreadyRegisteredException;
import br.com.finup.model.User;
import br.com.finup.model.UserFinancialProfile;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
 *
 * <p>{@code addFilters = false} desliga o Spring Security nesta fatia: aqui se testa o controller.
 * Token ausente, invalido e aceito ficam no {@code SecurityConfigTest}.
 */
@AutoConfigureMockMvc(addFilters = false)
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
    when(authenticatedIdentityResolver.resolveCurrentWithAttributes()).thenReturn(identity);
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
    when(authenticatedIdentityResolver.resolveCurrentWithAttributes())
        .thenThrow(new MissingAuthenticatedIdentityException());

    mockMvc.perform(post("/api/v1/users")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Cognito indisponivel ao buscar os atributos vira 503")
  void identityProviderUnavailableReturns503() throws Exception {
    when(authenticatedIdentityResolver.resolveCurrentWithAttributes())
        .thenThrow(new IdentityProviderUnavailableException());

    mockMvc
        .perform(post("/api/v1/users"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value(503));
  }

  @Test
  @DisplayName("identidade ja cadastrada vira 409, e nao 500")
  void duplicateIdentityReturns409() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    when(authenticatedIdentityResolver.resolveCurrentWithAttributes()).thenReturn(identity);
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
    when(authenticatedIdentityResolver.resolveCurrentWithAttributes()).thenReturn(identity);
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
  @DisplayName("PATCH de informacoes complementares devolve 200 com o contrato da task")
  void validAdditionalInfoUpdateReturns200() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User user =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    UserFinancialProfile profile = UserFinancialProfile.createFor(user);
    profile.apply("+5511999998888", "Analista", LocalDate.of(2000, 1, 31), new BigDecimal("3500"));
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.updateAdditionalInfo(
            eq(identity),
            eq("+5511999998888"),
            eq("Analista"),
            eq(LocalDate.of(2000, 1, 31)),
            eq(new BigDecimal("3500.00"))))
        .thenReturn(profile);

    mockMvc
        .perform(
            patch("/api/v1/users/me/additional-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"phone\":\"+5511999998888\",\"profession\":\"Analista\","
                        + "\"birthDate\":\"2000-01-31\",\"monthlyIncome\":3500.00}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(user.getId().toString()))
        .andExpect(jsonPath("$.name").value("Ana Souza"))
        .andExpect(jsonPath("$.email").value("ana@exemplo.com"))
        .andExpect(jsonPath("$.phone").value("+5511999998888"))
        .andExpect(jsonPath("$.profession").value("Analista"))
        .andExpect(jsonPath("$.birthDate").value("2000-01-31"))
        .andExpect(jsonPath("$.monthlyIncome").value(3500));
  }

  @Test
  @DisplayName("PATCH com corpo vazio e valido e nao informa nenhum campo ao service")
  void emptyBodyIsAcceptedAsPartialUpdate() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User user =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.updateAdditionalInfo(identity, null, null, null, null))
        .thenReturn(UserFinancialProfile.createFor(user));

    mockMvc
        .perform(
            patch("/api/v1/users/me/additional-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"birthDate\":\"2999-01-01\"}",
        "{\"birthDate\":\"31/01/2000\"}",
        "{\"phone\":\"11999998888\"}",
        "{\"phone\":\"+0123456\"}",
        "{\"profession\":\"   \"}",
        "{\"monthlyIncome\":-100}",
        "{\"monthlyIncome\":\"abc\"}",
        "{\"monthlyIncome\":1.234}"
      })
  @DisplayName("PATCH com formato invalido devolve 400 sem chegar ao service")
  void invalidFormatReturns400(String body) throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/me/additional-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService);
  }

  @Test
  @DisplayName("PATCH antes da Etapa 1 devolve 404, e nao 500")
  void additionalInfoUpdateBeforeCreationReturns404() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-999", "Ana Souza", "ana@exemplo.com");
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.updateAdditionalInfo(any(), any(), any(), any(), any()))
        .thenThrow(
            new ResourceNotFoundException("Usuario nao encontrado para a identidade autenticada."));

    mockMvc
        .perform(
            patch("/api/v1/users/me/additional-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profession\":\"Analista\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET /{id} devolve as informacoes complementares gravadas")
  void getByIdReturnsAdditionalInfo() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User user =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    UserFinancialProfile profile = UserFinancialProfile.createFor(user);
    profile.apply("+5511999998888", "Analista", LocalDate.of(2000, 1, 31), new BigDecimal("3500"));
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.findByIdForAuthenticatedIdentity(identity, user.getId())).thenReturn(user);
    when(userService.findFinancialProfile(user)).thenReturn(Optional.of(profile));

    mockMvc
        .perform(get("/api/v1/users/" + user.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(user.getId().toString()))
        .andExpect(jsonPath("$.phone").value("+5511999998888"))
        .andExpect(jsonPath("$.profession").value("Analista"))
        .andExpect(jsonPath("$.birthDate").value("2000-01-31"))
        .andExpect(jsonPath("$.monthlyIncome").value(3500));
  }

  @Test
  @DisplayName("GET /{id} de outra pessoa devolve 404")
  void getByIdOfAnotherUserReturns404() throws Exception {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    UUID otherId = UUID.randomUUID();
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(userService.findByIdForAuthenticatedIdentity(identity, otherId))
        .thenThrow(new ResourceNotFoundException("Usuario nao encontrado."));

    mockMvc.perform(get("/api/v1/users/" + otherId)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("consulta de e-mail livre devolve 200 com available true, sem usar a identidade")
  void freeEmailReturnsAvailableTrue() throws Exception {
    when(userService.isEmailAvailable("ana@exemplo.com")).thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/users/email-availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@exemplo.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true));

    verifyNoInteractions(authenticatedIdentityResolver);
  }

  @Test
  @DisplayName("consulta de e-mail ja cadastrado devolve 200 com available false")
  void takenEmailReturnsAvailableFalse() throws Exception {
    when(userService.isEmailAvailable("ana@exemplo.com")).thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/users/email-availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@exemplo.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false));
  }

  @Test
  @DisplayName("consulta com espacos nas pontas do e-mail consulta o e-mail sem eles, e nao da 400")
  void emailWithSurroundingSpacesIsTrimmed() throws Exception {
    when(userService.isEmailAvailable("ana@exemplo.com")).thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/users/email-availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"  ana@exemplo.com  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false));

    verify(userService).isEmailAvailable("ana@exemplo.com");
  }

  @Test
  @DisplayName("consulta com e-mail sem formato valido devolve 400 apontando o campo")
  void malformedEmailReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/email-availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"isso-nao-e-email\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0].field").value("email"));

    verifyNoInteractions(userService);
  }

  @Test
  @DisplayName("consulta com e-mail em branco ou ausente devolve 400")
  void blankOrMissingEmailReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/email-availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"   \"}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/users/email-availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService);
  }

  @Test
  @DisplayName("consulta com e-mail de mais de 255 caracteres devolve 400")
  void tooLongEmailReturns400() throws Exception {
    // Formato valido (local de 64, tres rotulos de 63), mas com 260 caracteres: so o limite barra.
    String longEmail = "a".repeat(64) + "@" + ("b".repeat(63) + ".").repeat(3) + "com";

    mockMvc
        .perform(
            post("/api/v1/users/email-availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + longEmail + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0].field").value("email"));

    verifyNoInteractions(userService);
  }
}
