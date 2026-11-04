package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Opcao de resposta de uma pergunta de perfil financeiro")
public record FinancialProfileAnswerOptionResponse(
    @Schema(description = "Identificador unico da opcao") UUID id,
    @Schema(description = "Codigo estavel da opcao", example = "CONTROLLED") String code,
    @Schema(description = "Texto exibido para o usuario") String text,
    @Schema(
            description =
                "Peso dessa resposta na formula do FinUp Score; nulo quando a pergunta ainda nao"
                    + " entra no calculo",
            nullable = true,
            example = "100")
        BigDecimal weight) {}
