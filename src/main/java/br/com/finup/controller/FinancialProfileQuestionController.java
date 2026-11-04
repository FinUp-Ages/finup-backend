package br.com.finup.controller;

import br.com.finup.dto.FinancialProfileQuestionResponse;
import br.com.finup.service.FinancialProfileQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perguntas de perfil financeiro do onboarding, para qualquer usuario autenticado (nao e rota de
 * admin). Sem corpo de identidade: as perguntas sao as mesmas para todo mundo.
 */
@RestController
@RequestMapping("/api/v1/financial-profile/questions")
@Tag(name = "Financial Profile", description = "Perguntas de perfil financeiro do onboarding")
public class FinancialProfileQuestionController {

  private final FinancialProfileQuestionService financialProfileQuestionService;

  public FinancialProfileQuestionController(
      FinancialProfileQuestionService financialProfileQuestionService) {
    this.financialProfileQuestionService = financialProfileQuestionService;
  }

  @GetMapping
  @Operation(
      summary = "Lista as perguntas de perfil financeiro do onboarding",
      description =
          "Perguntas fixas, na ordem de exibicao, cada uma com suas opcoes de resposta. O campo"
              + " weight e o peso da resposta na formula do FinUp Score, quando ja definido"
              + " (docs/finup-score.md) — nulo nas perguntas que ainda nao entram no calculo.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Perguntas retornadas com sucesso"),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, invalido ou expirado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public List<FinancialProfileQuestionResponse> list() {
    return financialProfileQuestionService.findAll();
  }
}
