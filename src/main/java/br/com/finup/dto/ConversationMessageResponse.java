package br.com.finup.dto;

import br.com.finup.model.MessageRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Mensagem de uma conversa do assistente")
public record ConversationMessageResponse(
    @Schema(description = "Identificador único da mensagem") UUID id,
    @Schema(description = "Autor da mensagem: USER (usuário) ou ASSISTANT (assistente)")
        MessageRole role,
    @Schema(description = "Texto da mensagem") String content,
    @Schema(description = "Instante em que a mensagem foi gravada, em UTC") Instant createdAt) {}
