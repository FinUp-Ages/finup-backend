package br.com.finup.controller;

import br.com.finup.dto.StudyTrackAnswersRequest;
import br.com.finup.dto.StudyTrackAnswersResponse;
import br.com.finup.dto.StudyTrackProgressResponse;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.StudyTrackAnswerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/study-tracks")
public class StudyTrackAnswerController {

  private final StudyTrackAnswerService service;
  private final AuthenticatedIdentityResolver identityResolver;

  public StudyTrackAnswerController(
      StudyTrackAnswerService service, AuthenticatedIdentityResolver identityResolver) {
    this.service = service;
    this.identityResolver = identityResolver;
  }

  @PostMapping("/answers")
  @Operation(
      summary = "Registra respostas da trilha",
      description =
          "Aceita uma ou mais perguntas publicadas da categoria (não precisa ser o conjunto"
              + " completo). Uma pergunta já respondida tem a resposta substituída. Estrelas e"
              + " acertos da resposta valem para todas as respostas atuais da categoria.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Respostas gravadas"),
    @ApiResponse(
        responseCode = "400",
        description = "Pergunta repetida, de outra categoria ou não publicada",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Categoria não encontrada",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<StudyTrackAnswersResponse> submit(
      @Valid @RequestBody StudyTrackAnswersRequest request) {
    return ResponseEntity.ok(service.submit(identityResolver.resolveCurrent(), request));
  }

  @GetMapping("/answers")
  public ResponseEntity<List<StudyTrackAnswersResponse>> findAnswers(
      @RequestParam(required = false) UUID categoryId) {
    return ResponseEntity.ok(service.findAnswers(identityResolver.resolveCurrent(), categoryId));
  }

  @GetMapping("/progress")
  @Operation(
      summary = "Progresso do usuário na trilha",
      description =
          "Por categoria e no total. Cada pergunta publicada é um nível; 'levels' traz a situação"
              + " de cada um e 'currentLevel' o primeiro não concluído. 'percentage' é o percentual"
              + " de acertos e 'completionPercentage' o de avanço.")
  @ApiResponse(responseCode = "200", description = "Progresso retornado com sucesso")
  public ResponseEntity<StudyTrackProgressResponse> findProgress() {
    return ResponseEntity.ok(service.findProgress(identityResolver.resolveCurrent()));
  }
}
