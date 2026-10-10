package br.com.finup.dto;

import br.com.finup.model.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionListItemResponse(
    UUID id,
    UUID categoryId,
    UUID paymentMethodId,
    TransactionType type,
    String description,
    BigDecimal amount,
    LocalDate transactionDate,
    boolean isRecurring,
    Instant createdAt) {}
