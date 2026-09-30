package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** O que o assistente entendeu e fez. */
@Schema(description = "Resultado da acao executada pelo assistente")
public record AiAssistantResponse(
    @Schema(description = "Acao executada", example = "REGISTER_TRANSACTION") String action,
    @Schema(
            description = "Resumo legivel do que foi feito",
            example = "Despesa de R$ 7.00 registrada em Educação.")
        String message,
    @Schema(description = "Transacao criada, quando a acao cria uma")
        TransactionResponse transaction) {}
