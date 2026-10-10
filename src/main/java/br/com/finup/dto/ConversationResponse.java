package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Conversa do histórico do assistente")
public record ConversationResponse(
    @Schema(description = "Identificador único da conversa") UUID id,
    @Schema(
            description =
                "Título exibido no menu de histórico. Pode vir nulo: a geração de título "
                    + "(a partir da primeira mensagem ou de um resumo) ainda não existe, então "
                    + "conversas sem título gravado chegam aqui como null — quem consome precisa "
                    + "de um texto de fallback para esse caso.",
            nullable = true)
        String title,
    @Schema(description = "Data da última atualização da conversa") Instant updatedAt,
    @Schema(description = "Quantidade de mensagens da conversa") int messageCount) {}
