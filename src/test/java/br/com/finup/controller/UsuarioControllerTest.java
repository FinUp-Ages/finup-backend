package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.exception.EmailJaCadastradoException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.Usuario;
import br.com.finup.service.UsuarioService;
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
 * <p>{@code @WebMvcTest} sobe so a camada web — sem banco, sem service real. O
 * {@code ApiExceptionHandler} entra junto porque {@code @RestControllerAdvice} faz parte da fatia,
 * entao estes testes verificam de verdade o contrato de erro, e nao uma simulacao dele.
 *
 * <p>{@code @MockitoBean} substituiu o antigo {@code @MockBean}, removido no Spring Boot 4.
 */
@WebMvcTest(UsuarioController.class)
class UsuarioControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UsuarioService usuarioService;

  @Test
  @DisplayName("POST valido devolve 201 com Location e o corpo do usuario")
  void cadastroValidoRetorna201() throws Exception {
    Usuario usuario = Usuario.cadastrar("Ana Souza", "ana@exemplo.com");
    when(usuarioService.cadastrar(eq("Ana Souza"), eq("ana@exemplo.com"))).thenReturn(usuario);

    mockMvc
        .perform(
            post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana Souza\",\"email\":\"ana@exemplo.com\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/usuarios/" + usuario.getId()))
        .andExpect(jsonPath("$.id").value(usuario.getId().toString()))
        .andExpect(jsonPath("$.nome").value("Ana Souza"))
        .andExpect(jsonPath("$.email").value("ana@exemplo.com"));
  }

  @Test
  @DisplayName("POST com campos invalidos devolve 400 em RFC 7807, sem chamar o service")
  void cadastroInvalidoRetorna400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\",\"email\":\"nao-e-email\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Requisicao invalida"))
        .andExpect(jsonPath("$.campos.length()").value(2))
        .andExpect(jsonPath("$.campos[0].campo").value("email"))
        .andExpect(jsonPath("$.campos[1].campo").value("nome"));
  }

  @Test
  @DisplayName("e-mail duplicado vira 409, e nao 500")
  void emailDuplicadoRetorna409() throws Exception {
    when(usuarioService.cadastrar(any(), any()))
        .thenThrow(new EmailJaCadastradoException("ana@exemplo.com"));

    mockMvc
        .perform(
            post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana Souza\",\"email\":\"ana@exemplo.com\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail")
                .value("Ja existe um usuario cadastrado com o e-mail ana@exemplo.com"));
  }

  @Test
  @DisplayName("GET de id inexistente devolve 404 em RFC 7807")
  void buscaInexistenteRetorna404() throws Exception {
    UUID id = UUID.randomUUID();
    when(usuarioService.buscarPorId(id)).thenThrow(new ResourceNotFoundException("Usuario", id));

    mockMvc
        .perform(get("/api/v1/usuarios/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.traceId").exists());
  }
}
