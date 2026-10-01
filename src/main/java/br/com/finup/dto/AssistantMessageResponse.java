package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Resposta do assistente financeiro a uma mensagem do usuario. */
@Schema(description = "Resposta do assistente financeiro")
public record AssistantMessageResponse(
    @Schema(description = "Conversa a que a resposta pertence; reenvie nas proximas mensagens")
        UUID conversationId,
    @Schema(description = "Identificador da resposta") UUID messageId,
    @Schema(
            description = "Texto da resposta",
            example = "Neste mes voce gastou R$ 820,00 com alimentacao.")
        String reply,
    @Schema(description = "Instante da resposta, em UTC") Instant createdAt) {}
