package br.com.finup.dto;

import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.RecurrenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Corpo do cadastro de uma recorrencia de transacao. */
@Schema(description = "Dados para cadastrar uma recorrencia de transacao")
public record CreateTransactionRecurrenceRequest(
    @Schema(description = "Identificador do usuario responsavel")
        @NotNull(message = "e obrigatorio")
        UUID userId,
    @Schema(description = "Identificador de uma categoria existente")
        @NotNull(message = "e obrigatorio")
        UUID categoryId,
    @Schema(description = "Identificador de um meio de pagamento existente, quando utilizado")
        UUID paymentMethodId,
    @Schema(description = "Tipo da transacao gerada", example = "EXPENSE")
        @NotNull(message = "e obrigatorio")
        RecurrenceType type,
    @Schema(description = "Descricao opcional", example = "Conta de luz")
        @Size(max = 255, message = "deve ter no maximo 255 caracteres")
        String description,
    @Schema(description = "Valor de cada ocorrencia", example = "320.00")
        @NotNull(message = "e obrigatorio")
        @Positive(message = "deve ser maior que zero")
        @Digits(
            integer = 10,
            fraction = 2,
            message = "deve ter no maximo 10 digitos inteiros e 2 decimais")
        BigDecimal amount,
    @Schema(description = "Periodicidade da recorrencia", example = "MONTHLY")
        @NotNull(message = "e obrigatorio")
        RecurrenceFrequency frequency,
    @Schema(description = "Dia do mes em que a ocorrencia cai", example = "5")
        @NotNull(message = "e obrigatorio")
        @Min(value = 1, message = "deve estar entre 1 e 31")
        @Max(value = 31, message = "deve estar entre 1 e 31")
        Integer dayOfMonth,
    @Schema(
            description = "Data a partir da qual a recorrencia passa a valer",
            example = "2026-09-05")
        @NotNull(message = "e obrigatoria")
        LocalDate startDate,
    @Schema(description = "Data opcional em que a recorrencia deixa de gerar ocorrencias")
        LocalDate endDate) {}
