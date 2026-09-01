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
        MockMvcBuilders.standaloneSetup(new ControllerDeTeste())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("corpo invalido vira 400 em RFC 7807 listando os campos reprovados")
  void corpoInvalidoRetorna400ComCampos() throws Exception {
    mockMvc
        .perform(
            post("/teste")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\",\"valor\":-1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Requisicao invalida"))
        .andExpect(jsonPath("$.traceId").exists())
        .andExpect(jsonPath("$.campos.length()").value(2))
        .andExpect(jsonPath("$.campos[0].campo").value("nome"))
        .andExpect(jsonPath("$.campos[1].campo").value("valor"));
  }

  @Test
  @DisplayName("excecao de negocio usa o status que ela carrega")
  void excecaoDeNegocioUsaStatusDaExcecao() throws Exception {
    mockMvc
        .perform(
            post("/teste")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"inexistente\",\"valor\":1}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Conta nao encontrado: inexistente"));
  }

  /** Controller minimo, existe so dentro deste teste. */
  @RestController
  static class ControllerDeTeste {

    @PostMapping("/teste")
    void receber(@Valid @RequestBody Entrada entrada) {
      if ("inexistente".equals(entrada.nome())) {
        throw new ResourceNotFoundException("Conta", entrada.nome());
      }
    }
  }

  /** DTO de entrada do controller de teste. */
  record Entrada(@NotBlank String nome, @Positive Integer valor) {}
}
