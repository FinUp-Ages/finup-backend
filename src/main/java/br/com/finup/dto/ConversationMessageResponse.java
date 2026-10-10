package br.com.finup.dto;

import br.com.finup.model.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Uma mensagem de uma conversa do assistente. */
@Schema(description = "Mensagem de uma conversa do assistente")
public record ConversationMessageResponse(
    @Schema(description = "Quem escreveu: USER ou ASSISTANT") ChatMessage.Role role,
    @Schema(description = "Texto da mensagem", example = "gastei 7 reais na pucrs") String text,
    @Schema(
            description = "Acao executada pelo assistente. So vem nas mensagens do ASSISTANT",
            example = "REGISTER_TRANSACTION",
            nullable = true)
        String action,
    @Schema(description = "Quando a mensagem foi enviada") Instant createdAt) {}
