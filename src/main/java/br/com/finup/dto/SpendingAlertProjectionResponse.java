package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Projeção do valor acumulado caso o montante fosse investido a 10% ao ano por 5 anos. */
public record SpendingAlertProjectionResponse(
    @Schema(description = "Prazo da projeção em anos", example = "5") int years,
    @Schema(description = "Taxa anual estimada em percentual", example = "10") int annualRatePct,
    @Schema(
            description = "Valor futuro projetado corrigido com juros compostos",
            example = "4831.53")
        BigDecimal futureValue) {
  private static final BigDecimal ANNUAL_RATE = new BigDecimal("1.10");

  /** Calcula a projeção com a fórmula: totalAmount * 1,10^5 arredondado em 2 casas decimais. */
  public static SpendingAlertProjectionResponse calculate(BigDecimal totalAmount) {
    BigDecimal base = totalAmount != null ? totalAmount : BigDecimal.ZERO;
    BigDecimal futureValue = base.multiply(ANNUAL_RATE.pow(5)).setScale(2, RoundingMode.HALF_UP);
    return new SpendingAlertProjectionResponse(5, 10, futureValue);
  }
}
