package br.com.finup.controller;

import br.com.finup.dto.StudyTrackCategoryResponse;
import br.com.finup.dto.StudyTrackQuestionResponse;
import br.com.finup.service.StudyTrackQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consultas da trilha de estudos disponíveis para usuários autenticados. */
@RestController
@RequestMapping("/api/v1/study-tracks")
@Tag(name = "Study Tracks", description = "Leitura da trilha de estudos")
public class StudyTrackController {

  private final StudyTrackQueryService studyTrackQueryService;

  public StudyTrackController(StudyTrackQueryService studyTrackQueryService) {
    this.studyTrackQueryService = studyTrackQueryService;
  }

  @GetMapping("/categories")
  @Operation(
    summary = "Lista as categorias da trilha de estudos",
    description = "Retorna as categorias da trilha de estudos do usuário autenticado"
  )
  @ApiResponse(responseCode = "200", description = "Categorias retornadas com sucesso")
  public ResponseEntity<List<StudyTrackCategoryResponse>> findCategories() {
    return ResponseEntity.ok(studyTrackQueryService.findCategories());
  }

  @GetMapping("/categories/{id}/questions")
  @Operation(
      summary = "Lista as perguntas publicadas de uma categoria",
      description =
          "Retorna somente o conteúdo publicado de cada pergunta; conteúdo draft não é exposto.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Perguntas publicadas retornadas com sucesso"),
    @ApiResponse(
        responseCode = "404",
        description = "Categoria não encontrada",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<List<StudyTrackQuestionResponse>> findPublishedQuestions(
      @PathVariable UUID id) {
    return ResponseEntity.ok(studyTrackQueryService.findPublishedQuestions(id));
  }
}
