package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Conversa do histórico do assistente")
public record ConversationResponse(
    @Schema(description = "Identificador único da conversa") UUID id,
    @Schema(description = "Título exibido no menu de histórico") String title,
    @Schema(description = "Data da última atualização da conversa") Instant updatedAt) {}
