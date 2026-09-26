package br.com.finup.dto;

import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Representacao publica de uma transacao cadastrada. */
@Schema(description = "Transacao financeira cadastrada")
public record TransactionResponse(
    UUID id,
    UUID userId,
    UUID categoryId,
    UUID paymentMethodId,
    TransactionType type,
    String description,
    BigDecimal amount,
    LocalDate transactionDate,
    boolean isRecurring,
    RecurrenceFrequency recurrenceFrequency,
    LocalDateTime lastOccurrenceDateTime,
    Instant createdAt,
    Instant updatedAt) {}
