package br.com.finup.dto;

import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Representacao publica de uma recorrencia de transacao cadastrada. */
@Schema(description = "Recorrencia de transacao cadastrada")
public record TransactionRecurrenceResponse(
    UUID id,
    UUID userId,
    UUID categoryId,
    UUID paymentMethodId,
    TransactionType type,
    String description,
    BigDecimal amount,
    RecurrenceFrequency frequency,
    int dayOfMonth,
    LocalDate startDate,
    LocalDate endDate,
    @Schema(description = "Proxima data em que uma transacao deve ser gerada, se ainda houver uma")
        LocalDate nextOccurrenceDate,
    Instant createdAt,
    Instant updatedAt) {}
