package br.com.finup.dto;

import br.com.finup.model.AiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Texto livre enviado ao assistente. */
@Schema(description = "Mensagem em linguagem natural para o assistente de IA")
public record AiAssistantRequest(
    @Schema(description = "O que o usuario quer fazer", example = "gastei 7 reais na pucrs")
        @NotBlank(message = "e obrigatoria")
        @Size(max = 500, message = "deve ter no maximo 500 caracteres")
        String message,
    @Schema(
            description = "Modelo logico a usar; vazio usa o padrao configurado",
            example = "AMAZON_LITE")
        AiModel model,
    @Schema(
            description =
                "Conversa existente para continuar. Vazio inicia uma conversa nova, cujo id vem"
                    + " na resposta",
            nullable = true)
        UUID conversationId) {}
