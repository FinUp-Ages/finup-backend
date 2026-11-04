package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Pergunta de perfil financeiro do onboarding")
public record FinancialProfileQuestionResponse(
    @Schema(description = "Identificador unico da pergunta") UUID id,
    @Schema(description = "Codigo estavel da pergunta", example = "SPENDING_HABIT") String code,
    @Schema(description = "Texto exibido para o usuario") String text,
    @Schema(description = "Opcoes de resposta, na ordem de exibicao")
        List<FinancialProfileAnswerOptionResponse> options) {}
