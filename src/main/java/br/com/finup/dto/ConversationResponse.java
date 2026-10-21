package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Conversa do histórico do assistente")
public record ConversationResponse(
    @Schema(description = "Identificador único da conversa") UUID id,
    @Schema(
            description =
                "Título exibido no menu de histórico. Conversas novas usam o recorte da primeira "
                    + "mensagem do usuário, com espaços normalizados e até 80 caracteres, incluindo "
                    + "reticências. Não há chamada extra ao modelo. Pode vir nulo em conversas "
                    + "antigas sem título ou sem texto utilizável para gerar o título; o app deve "
                    + "manter um texto de fallback nesses casos.",
            nullable = true)
        String title,
    @Schema(description = "Data da última atualização da conversa") Instant updatedAt,
    @Schema(description = "Quantidade de mensagens da conversa") int messageCount) {}
