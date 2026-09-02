package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.exception.EmailAlreadyRegisteredException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.User;
import br.com.finup.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exemplo de referencia de teste de controller.
 *
 * <p>{@code @WebMvcTest} sobe so a camada web — sem banco, sem service real. O {@code
 * ApiExceptionHandler} entra junto porque {@code @RestControllerAdvice} faz parte da fatia, entao
 * estes testes verificam de verdade o contrato de erro, e nao uma simulacao dele.
 *
 * <p>{@code @MockitoBean} substituiu o antigo {@code @MockBean}, removido no Spring Boot 4.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;

  @Test
  @DisplayName("POST valido devolve 201 com Location e o corpo do usuario")
  void validRegistrationReturns201() throws Exception {
    User user = User.register("Ana Souza", "ana@exemplo.com");
    when(userService.register(eq("Ana Souza"), eq("ana@exemplo.com"))).thenReturn(user);

    mockMvc
        .perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ana Souza\",\"email\":\"ana@exemplo.com\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/users/" + user.getId()))
        .andExpect(jsonPath("$.id").value(user.getId().toString()))
        .andExpect(jsonPath("$.name").value("Ana Souza"))
        .andExpect(jsonPath("$.email").value("ana@exemplo.com"));
  }

  @Test
  @DisplayName("POST com campos invalidos devolve 400 em RFC 7807, sem chamar o service")
  void invalidRegistrationReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"email\":\"nao-e-email\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Requisicao invalida"))
        .andExpect(jsonPath("$.fields.length()").value(2))
        .andExpect(jsonPath("$.fields[0].field").value("email"))
        .andExpect(jsonPath("$.fields[1].field").value("name"));
  }

  @Test
  @DisplayName("e-mail duplicado vira 409, e nao 500")
  void duplicateEmailReturns409() throws Exception {
    when(userService.register(any(), any()))
        .thenThrow(new EmailAlreadyRegisteredException("ana@exemplo.com"));

    mockMvc
        .perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ana Souza\",\"email\":\"ana@exemplo.com\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail")
                .value("Ja existe um usuario cadastrado com o e-mail ana@exemplo.com"));
  }

  @Test
  @DisplayName("GET de id inexistente devolve 404 em RFC 7807")
  void unknownIdReturns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(userService.findById(id)).thenThrow(new ResourceNotFoundException("User", id));

    mockMvc
        .perform(get("/api/v1/users/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.traceId").exists());
  }
}
