package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Confirmação da dispensa do alerta de gastos de uma categoria. */
public record DismissSpendingAlertResponse(
    @Schema(description = "Identificador da categoria dispensada") UUID categoryId,
    @Schema(description = "Instante da dispensa, em UTC") Instant dismissedAt) {}
