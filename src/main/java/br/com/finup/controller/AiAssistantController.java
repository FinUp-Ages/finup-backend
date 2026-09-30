package br.com.finup.controller;

import br.com.finup.dto.AiAssistantRequest;
import br.com.finup.dto.AiAssistantResponse;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.AiAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assistente de IA: recebe uma frase do usuario autenticado e executa a acao correspondente. A
 * identidade vem sempre do {@link AuthenticatedIdentityResolver}, nunca do corpo.
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI", description = "Assistente de IA (Amazon Bedrock)")
public class AiAssistantController {

  private final AiAssistantService aiAssistantService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public AiAssistantController(
      AiAssistantService aiAssistantService,
      AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.aiAssistantService = aiAssistantService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @PostMapping("/assistant")
  @Operation(
      summary = "Interpreta um texto livre e executa a acao (ex.: registrar uma transacao)",
      description =
          "Exemplo: \"gastei 7 reais na pucrs\" registra uma despesa de 7,00 em Educação.")
  @Parameters({
    @Parameter(
        name = "X-Mock-Cognito-Sub",
        in = ParameterIn.HEADER,
        required = true,
        description = "Identificador (sub) da identidade autenticada — mock do Cognito real."),
    @Parameter(
        name = "X-Mock-Cognito-Email",
        in = ParameterIn.HEADER,
        required = true,
        description = "E-mail da identidade autenticada — mock do Cognito real."),
    @Parameter(
        name = "X-Mock-Cognito-Name",
        in = ParameterIn.HEADER,
        required = false,
        description = "Nome da identidade autenticada — mock do Cognito real. Opcional.")
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Acao executada"),
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
        description = "Usuario nao encontrado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description = "Texto nao entendido ou dados insuficientes (categoria, valor)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "429",
        description = "Limite de chamadas excedido no Bedrock",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "502",
        description = "Falha ou resposta invalida do modelo",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "503",
        description = "Bedrock indisponivel",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "504",
        description = "Tempo limite ao aguardar o modelo",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public AiAssistantResponse assistant(@Valid @RequestBody AiAssistantRequest request) {
    return aiAssistantService.handle(authenticatedIdentityResolver.resolveCurrent(), request);
  }
}
