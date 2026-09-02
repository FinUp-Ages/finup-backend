package br.com.finup.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Garante o contrato de erro da API: todo erro sai em RFC 7807, com os mesmos campos.
 *
 * <p>Usa {@code standaloneSetup} — monta so o MVC necessario, sem subir contexto Spring. Quando
 * existir um controller de verdade, o teste dele deve usar {@code @WebMvcTest(SeuController.class)}
 * com {@code @Import(ApiExceptionHandler.class)}.
 */
class ApiExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("corpo invalido vira 400 em RFC 7807 listando os campos reprovados")
  void invalidBodyReturns400WithFields() throws Exception {
    mockMvc
        .perform(
            post("/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"amount\":-1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Requisicao invalida"))
        .andExpect(jsonPath("$.traceId").exists())
        .andExpect(jsonPath("$.fields.length()").value(2))
        .andExpect(jsonPath("$.fields[0].field").value("amount"))
        .andExpect(jsonPath("$.fields[1].field").value("name"));
  }

  @Test
  @DisplayName("excecao de negocio usa o status que ela carrega")
  void businessExceptionUsesItsOwnStatus() throws Exception {
    mockMvc
        .perform(
            post("/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"inexistente\",\"amount\":1}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Account nao encontrado: inexistente"));
  }

  /** Controller minimo, existe so dentro deste teste. */
  @RestController
  static class TestController {

    @PostMapping("/test")
    void receive(@Valid @RequestBody Input input) {
      if ("inexistente".equals(input.name())) {
        throw new ResourceNotFoundException("Account", input.name());
      }
    }
  }

  /** DTO de entrada do controller de teste. */
  record Input(@NotBlank String name, @Positive Integer amount) {}
}
