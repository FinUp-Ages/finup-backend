package br.com.finup.dto;

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
    UUID userId,
    UUID categoryId,
    UUID paymentMethodId,
    TransactionType type,
    String description,
    BigDecimal amount,
    LocalDate transactionDate,
    boolean isRecurring,
    Instant createdAt,
    Instant updatedAt) {}
