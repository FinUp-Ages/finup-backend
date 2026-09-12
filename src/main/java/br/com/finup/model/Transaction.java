package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Transacao financeira persistida exatamente na estrutura da tabela {@code transactions}. */
@Entity
@Table(name = "transactions")
public class Transaction {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(name = "payment_method_id")
  private UUID paymentMethodId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private TransactionType type;

  @Column(length = 255)
  private String description;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(name = "transaction_date", nullable = false)
  private LocalDate transactionDate;

  @Column(name = "is_recurring", nullable = false)
  private boolean recurring;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Transaction() {}

  private Transaction(
      UUID userId,
      UUID categoryId,
      UUID paymentMethodId,
      TransactionType type,
      String description,
      BigDecimal amount,
      LocalDate transactionDate,
      boolean recurring) {
    this.id = UUID.randomUUID();
    this.userId = Objects.requireNonNull(userId);
    this.categoryId = Objects.requireNonNull(categoryId);
    this.paymentMethodId = paymentMethodId;
    this.type = Objects.requireNonNull(type);
    this.description = description;
    this.amount = Objects.requireNonNull(amount);
    this.transactionDate = Objects.requireNonNull(transactionDate);
    this.recurring = recurring;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public static Transaction register(
      UUID userId,
      UUID categoryId,
      UUID paymentMethodId,
      TransactionType type,
      String description,
      BigDecimal amount,
      LocalDate transactionDate,
      boolean recurring) {
    return new Transaction(
        userId, categoryId, paymentMethodId, type, description, amount, transactionDate, recurring);
  }

  @PreUpdate
  void markAsUpdated() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public UUID getPaymentMethodId() {
    return paymentMethodId;
  }

  public TransactionType getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  public boolean isRecurring() {
    return recurring;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
