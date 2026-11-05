package br.com.finup.dto;

import br.com.finup.model.FinancialProfile;
import br.com.finup.model.TriageRisk;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Avaliação de riscos da triagem: devolvida no envio (201) e na consulta da última (200). */
@Schema(description = "Avaliação de riscos da triagem")
public record TriageAssessmentResponse(
    Assessment assessment,
    @Schema(description = "Instante em que a triagem foi concluída, em UTC") Instant completedAt) {

  public record Assessment(
      @Schema(description = "Perfil de investidor definido pela reação a uma queda de 20%")
          FinancialProfile profile,
      @Schema(description = "Resumo em texto da avaliação") String summary,
      @Schema(description = "Pontos de atenção, dos mais graves para os menos graves")
          List<TriageRisk> risks) {}
}
