package br.com.finup.dto;

import br.com.finup.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Corpo do cadastro de uma transacao financeira. */
@Schema(description = "Dados para registrar uma transacao financeira")
public record CreateTransactionRequest(
    @Schema(description = "Identificador do usuario responsavel")
        @NotNull(message = "e obrigatorio")
        UUID userId,
    @Schema(description = "Identificador de uma categoria existente")
        @NotNull(message = "e obrigatorio")
        UUID categoryId,
    @Schema(description = "Identificador de um meio de pagamento existente, quando utilizado")
        UUID paymentMethodId,
    @Schema(description = "Tipo da transacao", example = "EXPENSE")
        @NotNull(message = "e obrigatorio")
        TransactionType type,
    @Schema(description = "Descricao opcional", example = "Supermercado")
        @Size(max = 255, message = "deve ter no maximo 255 caracteres")
        String description,
    @Schema(description = "Valor da transacao", example = "320.00")
        @NotNull(message = "e obrigatorio")
        @Digits(
            integer = 10,
            fraction = 2,
            message = "deve ter no maximo 10 digitos inteiros e 2 decimais")
        BigDecimal amount,
    @Schema(description = "Data da transacao", example = "2026-09-12")
        @NotNull(message = "e obrigatoria")
        LocalDate transactionDate,
    @Schema(description = "Indica se a transacao e recorrente", example = "false")
        @NotNull(message = "e obrigatorio")
        Boolean isRecurring) {}
