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
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Template de uma transacao que se repete periodicamente (ex.: conta de luz todo dia 5).
 *
 * <p>Esta entidade so guarda a definicao da recorrencia e sabe calcular sua proxima ocorrencia —
 * ela nao gera a transacao em si. Materializar a transacao de fato depende de {@code Transaction}
 * (PR #9, ainda nao integrada), por isso fica fora do escopo desta feature.
 */
@Entity
@Table(name = "transaction_recurrences")
public class TransactionRecurrence {

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

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private RecurrenceFrequency frequency;

  @Column(name = "day_of_month", nullable = false)
  private int dayOfMonth;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TransactionRecurrence() {}

  private TransactionRecurrence(
      UUID userId,
      UUID categoryId,
      UUID paymentMethodId,
      TransactionType type,
      String description,
      BigDecimal amount,
      RecurrenceFrequency frequency,
      int dayOfMonth,
      LocalDate startDate,
      LocalDate endDate) {
    this.id = UUID.randomUUID();
    this.userId = Objects.requireNonNull(userId);
    this.categoryId = Objects.requireNonNull(categoryId);
    this.paymentMethodId = paymentMethodId;
    this.type = Objects.requireNonNull(type);
    this.description = description;
    this.amount = Objects.requireNonNull(amount);
    this.frequency = Objects.requireNonNull(frequency);
    this.dayOfMonth = dayOfMonth;
    this.startDate = Objects.requireNonNull(startDate);
    this.endDate = endDate;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public static TransactionRecurrence register(
      UUID userId,
      UUID categoryId,
      UUID paymentMethodId,
      TransactionType type,
      String description,
      BigDecimal amount,
      RecurrenceFrequency frequency,
      int dayOfMonth,
      LocalDate startDate,
      LocalDate endDate) {
    return new TransactionRecurrence(
        userId,
        categoryId,
        paymentMethodId,
        type,
        description,
        amount,
        frequency,
        dayOfMonth,
        startDate,
        endDate);
  }

  @PreUpdate
  void markAsUpdated() {
    updatedAt = Instant.now();
  }

  /**
   * Proxima data, a partir de {@code reference} (inclusive), em que esta recorrencia deve gerar uma
   * transacao. Vazio quando a recorrencia ja terminou ({@code endDate} ultrapassado).
   *
   * <p>Meses mais curtos que {@code dayOfMonth} usam o ultimo dia do mes (ex.: dia 31 em fevereiro
   * cai no dia 28 ou 29).
   */
  public Optional<LocalDate> nextOccurrenceOnOrAfter(LocalDate reference) {
    LocalDate effectiveFrom = reference.isBefore(startDate) ? startDate : reference;
    YearMonth month = YearMonth.from(effectiveFrom);
    LocalDate occurrence = occurrenceIn(month);
    if (occurrence.isBefore(effectiveFrom)) {
      month = month.plusMonths(1);
      occurrence = occurrenceIn(month);
    }
    if (endDate != null && occurrence.isAfter(endDate)) {
      return Optional.empty();
    }
    return Optional.of(occurrence);
  }

  private LocalDate occurrenceIn(YearMonth month) {
    return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
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

  public RecurrenceFrequency getFrequency() {
    return frequency;
  }

  public int getDayOfMonth() {
    return dayOfMonth;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
