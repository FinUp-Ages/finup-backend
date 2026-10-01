package br.com.finup.dto;

import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Representacao publica de uma transacao cadastrada. */
@Schema(description = "Transacao financeira cadastrada")
public record TransactionResponse(
    UUID id,
    UUID categoryId,
    UUID paymentMethodId,
    TransactionType type,
    String description,
    BigDecimal amount,
    LocalDate transactionDate,
    boolean isRecurring,
    RecurrenceFrequency recurrenceFrequency,
    @Schema(description = "Data da ultima ocorrencia gerada da serie, quando recorrente")
        LocalDate lastOccurrenceDate,
    @Schema(description = "Data da proxima ocorrencia da serie, quando recorrente")
        LocalDate nextOccurrenceDate,
    @Schema(description = "Transacao recorrente que originou esta ocorrencia, quando gerada")
        UUID recurrenceOriginId,
    Instant createdAt,
    Instant updatedAt) {}
