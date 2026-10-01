package br.com.finup.controller;

import br.com.finup.dto.AssistantMessageRequest;
import br.com.finup.dto.AssistantMessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Contrato publicado antes da implementacao: o caminho e o formato ja valem para o App, mas o
 * metodo responde 501 ate a task de implementacao substituir este corpo.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Assistant", description = "Assistente financeiro do usuario autenticado")
public class AssistantController {

  @PostMapping("/messages")
  @Operation(
      summary = "Envia uma mensagem ao assistente financeiro e devolve a resposta",
      description =
          "Resposta sincrona. Na primeira mensagem omita conversationId e reenvie o valor devolvido"
              + " nas seguintes.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Resposta do assistente"),
    @ApiResponse(
        responseCode = "400",
        description = "Campos invalidos",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuario ainda nao criado (Etapa 1 nao foi feita)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "429",
        description = "Limite de mensagens excedido",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "503",
        description = "Assistente temporariamente indisponivel",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "501",
        description = "Contrato publicado, ainda nao implementado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public AssistantMessageResponse send(@Valid @RequestBody AssistantMessageRequest request) {
    throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
  }
}
