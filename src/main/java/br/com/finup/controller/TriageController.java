package br.com.finup.controller;

import br.com.finup.dto.SubmitTriageRequest;
import br.com.finup.dto.TriageAnswersResponse;
import br.com.finup.dto.TriageAssessmentResponse;
import br.com.finup.dto.TriageQuestionnaireResponse;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.TriageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Triagem financeira. Nenhuma rota recebe identificador de usuário: quem chama vem da identidade
 * autenticada, então cada um só enxerga as próprias respostas e avaliação.
 */
@RestController
@RequestMapping("/api/v1/triage")
@Tag(name = "Triage", description = "Triagem financeira: questionário, respostas e avaliação")
public class TriageController {

  private final TriageService triageService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public TriageController(
      TriageService triageService, AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.triageService = triageService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @GetMapping("/questions")
  @Operation(
      summary = "Devolve o questionário de triagem, versionado",
      description = "O questionário fica no back para o app só renderizar.")
  @ApiResponse(responseCode = "200", description = "Questionário atual")
  @ApiResponse(
      responseCode = "401",
      description = "Identidade autenticada ausente ou inválida",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TriageQuestionnaireResponse questions() {
    return triageService.questionnaire();
  }

  @PostMapping("/answers")
  @Operation(
      summary = "Envia as respostas da triagem",
      description =
          "Valida contra a versão do questionário, grava tudo ou nada, atualiza o perfil"
              + " financeiro, recalcula o FinUp Score e devolve a avaliação de riscos. Reenviar"
              + " cria um novo envio e o mais recente vale.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Respostas gravadas e avaliação gerada"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Pergunta desconhecida, obrigatória ausente, opção inválida, valor fora de faixa ou"
                + " versão antiga; o campo vem em fields",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou inválida",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuário ainda não criado (Etapa 1 não foi feita)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "413",
        description = "Corpo maior que o limite",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<TriageAssessmentResponse> submit(
      @Valid @RequestBody SubmitTriageRequest request) {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    return ResponseEntity.status(HttpStatus.CREATED).body(triageService.submit(identity, request));
  }

  @GetMapping("/assessment")
  @Operation(summary = "Devolve a última avaliação de riscos do usuário autenticado")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Última avaliação"),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou inválida",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuário sem cadastro local, ou que nunca respondeu a triagem",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public TriageAssessmentResponse assessment() {
    return triageService.latestAssessment(authenticatedIdentityResolver.resolveCurrent());
  }

  @GetMapping("/answers")
  @Operation(
      summary = "Devolve as últimas respostas do usuário autenticado",
      description = "Para pré-preencher o questionário quando o usuário refaz a triagem.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Últimas respostas"),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou inválida",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuário sem cadastro local, ou que nunca respondeu a triagem",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public TriageAnswersResponse answers() {
    return triageService.latestAnswers(authenticatedIdentityResolver.resolveCurrent());
  }
}
