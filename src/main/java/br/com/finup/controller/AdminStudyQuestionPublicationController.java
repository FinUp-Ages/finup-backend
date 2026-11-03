package br.com.finup.controller;

import br.com.finup.dto.StudyQuestionResponse;
import br.com.finup.service.StudyQuestionPublicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publicação das perguntas da trilha, restrita ao grupo admin do Cognito.
 *
 * <p>Fica separado do {@link AdminStudyQuestionController} (CRUD do rascunho) de propósito: é outra
 * responsabilidade e outra task do mesmo contrato. As duas classes dividem o prefixo, e o Spring
 * aceita desde que os caminhos não se repitam. As três ações devolvem a pergunta como ela ficou,
 * com o status derivado já recalculado.
 */
@RestController
@RequestMapping("/api/v1/admin/study-tracks")
@Tag(name = "Admin Study Questions", description = "Perguntas da trilha de estudos — contrato C6")
@ApiResponses({
  @ApiResponse(
      responseCode = "401",
      description = "Token ausente ou inválido",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class))),
  @ApiResponse(
      responseCode = "403",
      description = "Usuário fora do grupo admin",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class))),
  @ApiResponse(
      responseCode = "404",
      description = "Pergunta não encontrada",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
})
public class AdminStudyQuestionPublicationController {

  private final StudyQuestionPublicationService service;

  public AdminStudyQuestionPublicationController(StudyQuestionPublicationService service) {
    this.service = service;
  }

  @PostMapping("/questions/{id}/publish")
  @Operation(
      summary = "Publica o rascunho da pergunta",
      description =
          "Valida o rascunho com as mesmas regras do POST e do PUT e o copia para published,"
              + " atualizando publishedAt. Rascunho inválido não publica nada. Se a pergunta já"
              + " está publicada e sem edições pendentes, nada muda (publishedAt é mantido)."
              + " Status depois: PUBLISHED. Exige grupo admin.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Pergunta publicada"),
    @ApiResponse(
        responseCode = "400",
        description = "UUID inválido ou rascunho que não passa na validação do tipo",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public StudyQuestionResponse publish(@PathVariable UUID id) {
    return service.publish(id);
  }

  @PostMapping("/questions/{id}/unpublish")
  @Operation(
      summary = "Despublica a pergunta",
      description =
          "Zera published e publishedAt, e a pergunta volta a DRAFT. O rascunho é mantido,"
              + " inclusive com edições pendentes. Se a pergunta nunca foi publicada, nada muda."
              + " Exige grupo admin.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Pergunta despublicada"),
    @ApiResponse(
        responseCode = "400",
        description = "UUID inválido",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public StudyQuestionResponse unpublish(@PathVariable UUID id) {
    return service.unpublish(id);
  }

  @DeleteMapping("/questions/{id}/draft")
  @Operation(
      summary = "Descarta as edições pendentes do rascunho",
      description =
          "Restaura o rascunho a partir da versão publicada, e a pergunta volta a PUBLISHED."
              + " Só vale para pergunta que já foi publicada. Sem edições pendentes, nada muda."
              + " Devolve a pergunta restaurada, e não 204, para o cliente atualizar a tela sem"
              + " outra chamada. Exige grupo admin.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Rascunho restaurado"),
    @ApiResponse(
        responseCode = "400",
        description = "UUID inválido",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "A pergunta nunca foi publicada: não há versão para restaurar",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public StudyQuestionResponse discardDraft(@PathVariable UUID id) {
    return service.discardDraft(id);
  }
}
