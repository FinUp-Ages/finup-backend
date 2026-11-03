package br.com.finup.dto;

import br.com.finup.model.AlertSource;
import br.com.finup.model.RiskKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Alerta consolidado por categoria de gasto de risco. */
public record SpendingAlertItemResponse(
    @Schema(
            description = "Identificador da categoria",
            example = "d3b07384-d113-4e4e-b5f7-9b2f6b8c9d10")
        UUID categoryId,
    @Schema(description = "Nome da categoria", example = "Apostas") String categoryName,
    @Schema(description = "Tipo de risco identificado", example = "BETS") RiskKind riskKind,
    @Schema(description = "Valor total gasto na categoria no período", example = "3000.00")
        BigDecimal totalAmount,
    @Schema(description = "Quantidade de transações do tipo no período", example = "4")
        int transactionCount,
    @Schema(
            description = "Mensagem amigável de alerta para o usuário",
            example = "R$ 3.000,00 em apostas nos últimos 30 dias.")
        String message,
    @Schema(description = "Projeção de rendimento caso o montante fosse investido")
        SpendingAlertProjectionResponse projection,
    @Schema(description = "Origem da identificação (RULE ou AI)", example = "RULE")
        AlertSource source,
    @Schema(
            description =
                "Instante em que o usuário dispensou o alerta desta categoria, em UTC; nulo se"
                    + " nunca dispensado",
            nullable = true)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        Instant dismissedAt) {}
