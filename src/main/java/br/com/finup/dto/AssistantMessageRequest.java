package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Mensagem do usuario para o assistente financeiro. */
@Schema(description = "Mensagem enviada ao assistente financeiro")
public record AssistantMessageRequest(
    @Schema(
            description =
                "Conversa em andamento. Omitido na primeira mensagem; o servidor devolve o"
                    + " identificador a ser reenviado nas seguintes")
        UUID conversationId,
    @Schema(description = "Texto da mensagem", example = "Quanto gastei com alimentacao este mes?")
        @NotBlank(message = "nao pode estar em branco")
        @Size(max = 2000, message = "deve ter no maximo 2000 caracteres")
        String message) {}
