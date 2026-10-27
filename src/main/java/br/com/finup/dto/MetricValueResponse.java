package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Uma metrica no periodo atual comparada com o periodo imediatamente anterior")
public record MetricValueResponse(
    @Schema(description = "Valor no periodo atual", example = "320.0") double value,
    @Schema(
            description = "Valor no periodo anterior, de mesmo tamanho e imediatamente antes",
            example = "302.0")
        double previousValue,
    @Schema(
            description =
                "Variacao percentual em relacao ao periodo anterior, com 1 casa decimal; 0.0 quando"
                    + " o periodo anterior e zero",
            example = "6.0")
        double changePct) {}
