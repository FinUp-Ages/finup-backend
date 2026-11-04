package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Opcao de resposta de uma {@link FinancialProfileQuestion}.
 *
 * <p>{@code weight} e o peso dessa resposta na formula do FinUp Score (ver {@code
 * docs/finup-score.md}) — nulo quando a pergunta ainda nao entra no calculo (ex.: {@code
 * HAS_INVESTMENTS}, que hoje e so um portao para {@code approximate_invested_amount}, nao um valor
 * direto na formula).
 */
@Entity
@Table(name = "financial_profile_answer_options")
public class FinancialProfileAnswerOption {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false, updatable = false)
  private FinancialProfileQuestion question;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(nullable = false)
  private String text;

  @Column(precision = 6, scale = 2)
  private BigDecimal weight;

  @Column(name = "order_index")
  private Integer orderIndex;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected FinancialProfileAnswerOption() {}

  public FinancialProfileAnswerOption(
      FinancialProfileQuestion question,
      String code,
      String text,
      BigDecimal weight,
      Integer orderIndex) {
    this.question = Objects.requireNonNull(question, "question e obrigatoria");
    this.code = Objects.requireNonNull(code, "code e obrigatorio").strip();
    this.text = Objects.requireNonNull(text, "text e obrigatorio").strip();
    this.weight = weight;
    this.orderIndex = orderIndex;
    question.getOptions().add(this);
  }

  @PrePersist
  void prePersist() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public FinancialProfileQuestion getQuestion() {
    return question;
  }

  public String getCode() {
    return code;
  }

  public String getText() {
    return text;
  }

  public BigDecimal getWeight() {
    return weight;
  }

  public Integer getOrderIndex() {
    return orderIndex;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof FinancialProfileAnswerOption that && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
