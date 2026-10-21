package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumo de usuarios do dashboard admin")
public record MetricsSummaryResponse(
    @Schema(
            description =
                "Usuarios distintos que registraram ao menos uma transacao no periodo."
                    + " Ocorrencias geradas por recorrencia nao contam")
        MetricValueResponse activeUsers,
    @Schema(description = "Usuarios cadastrados no periodo") MetricValueResponse newUsers) {}
