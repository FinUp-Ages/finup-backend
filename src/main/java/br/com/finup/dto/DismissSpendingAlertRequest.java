package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Corpo da dispensa do alerta de gastos de uma categoria. */
public record DismissSpendingAlertRequest(
    @Schema(
            description = "Identificador da categoria do alerta dispensado",
            example = "d3b07384-d113-4e4e-b5f7-9b2f6b8c9d10")
        @NotNull(message = "é obrigatório")
        UUID categoryId) {}
