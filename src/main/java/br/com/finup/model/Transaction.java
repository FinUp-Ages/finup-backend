package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transacao financeira persistida exatamente na estrutura da tabela {@code transactions}.
 *
 * <p>A recorrencia mora na propria transacao: a transacao original de uma serie tem {@code
 * recurring = true}, a periodicidade e a data da ultima ocorrencia gerada. O dia do seu {@code
 * transactionDate} e o dia de referencia da serie. Cada ocorrencia gerada depois e uma transacao
 * nova, nao recorrente, que aponta para a original por {@code recurrenceOriginId} — no maximo uma
 * por data.
 */
@Entity
@Table(
    name = "transactions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_transactions_recurrence_occurrence",
            columnNames = {"recurrence_origin_id", "transaction_date"}))
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

  @Enumerated(EnumType.STRING)
  @Column(name = "recurrence_frequency", length = 50)
  private RecurrenceFrequency recurrenceFrequency;

  @Column(name = "last_occurrence_date")
  private LocalDate lastOccurrenceDate;

  @Column(name = "recurrence_origin_id")
  private UUID recurrenceOriginId;

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
      RecurrenceFrequency recurrenceFrequency) {
    this.id = UUID.randomUUID();
    this.userId = Objects.requireNonNull(userId);
    this.categoryId = Objects.requireNonNull(categoryId);
    this.paymentMethodId = paymentMethodId;
    this.type = Objects.requireNonNull(type);
    this.description = description;
    this.amount = Objects.requireNonNull(amount);
    this.transactionDate = Objects.requireNonNull(transactionDate);
    this.recurring = recurrenceFrequency != null;
    this.recurrenceFrequency = recurrenceFrequency;
    this.lastOccurrenceDate = this.recurring ? transactionDate : null;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  /**
   * Registra uma transacao. Com {@code recurrenceFrequency} informada, ela abre uma serie
   * recorrente e conta como a primeira ocorrencia; com {@code null}, e uma transacao avulsa.
   */
  public static Transaction register(
      UUID userId,
      UUID categoryId,
      UUID paymentMethodId,
      TransactionType type,
      String description,
      BigDecimal amount,
      LocalDate transactionDate,
      RecurrenceFrequency recurrenceFrequency) {
    return new Transaction(
        userId,
        categoryId,
        paymentMethodId,
        type,
        description,
        amount,
        transactionDate,
        recurrenceFrequency);
  }

  /**
   * Data da proxima ocorrencia da serie, a partir da ultima gerada. Vazio para transacao nao
   * recorrente.
   *
   * <p>Mensal: o mes seguinte ao da ultima ocorrencia, no dia de referencia da serie (o dia do
   * {@code transactionDate} original) — ou no ultimo dia do mes, quando esse dia nao existe nele.
   * Por partir do dia de referencia, e nao do dia da ultima ocorrencia, uma serie do dia 31 volta
   * ao dia 31 depois de fevereiro.
   */
  public Optional<LocalDate> nextOccurrenceDate() {
    if (!recurring) {
      return Optional.empty();
    }
    return Optional.of(
        switch (recurrenceFrequency) {
          case MONTHLY -> {
            YearMonth nextMonth = YearMonth.from(lastOccurrenceDate).plusMonths(1);
            int day = Math.min(transactionDate.getDayOfMonth(), nextMonth.lengthOfMonth());
            yield nextMonth.atDay(day);
          }
        });
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

  public RecurrenceFrequency getRecurrenceFrequency() {
    return recurrenceFrequency;
  }

  public LocalDate getLastOccurrenceDate() {
    return lastOccurrenceDate;
  }

  public UUID getRecurrenceOriginId() {
    return recurrenceOriginId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
