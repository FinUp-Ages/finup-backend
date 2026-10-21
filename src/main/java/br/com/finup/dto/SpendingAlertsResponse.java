package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Resposta com o período analisado e os alertas de gastos de risco identificados. */
public record SpendingAlertsResponse(
    @Schema(description = "Período analisado em dias", example = "30") int periodDays,
    @Schema(description = "Lista de alertas identificados no período")
        List<SpendingAlertItemResponse> alerts) {}
