package br.com.finup.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Pergunta de perfil financeiro do onboarding (ver {@code GET
 * /api/v1/financial-profile/questions}).
 *
 * <p>{@code code} e o identificador estavel da pergunta (ex.: {@code SPENDING_HABIT}), usado para
 * casar com os enums Java equivalentes ({@link IncomeExpenseRelation}, {@link SpendingHabit}) e, no
 * futuro, ligar o calculo do FinUp Score ao peso de cada resposta em vez do valor hoje hardcoded em
 * {@code FinUpScoreCalculator} (ver {@code docs/finup-score.md}).
 *
 * <p>Sem endpoint de escrita ainda: o conteudo e gravado direto no banco, por {@code
 * database/init/02-required-data.sql}. O construtor publico existe para os testes montarem dados de
 * verdade via repositorio.
 */
@Entity
@Table(name = "financial_profile_questions")
public class FinancialProfileQuestion {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false)
  private String text;

  @Column(name = "order_index")
  private Integer orderIndex;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  private List<FinancialProfileAnswerOption> options = new ArrayList<>();

  protected FinancialProfileQuestion() {}

  public FinancialProfileQuestion(String code, String text, Integer orderIndex) {
    this.code = Objects.requireNonNull(code, "code e obrigatorio").strip();
    this.text = Objects.requireNonNull(text, "text e obrigatorio").strip();
    this.orderIndex = orderIndex;
  }

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getText() {
    return text;
  }

  public Integer getOrderIndex() {
    return orderIndex;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public List<FinancialProfileAnswerOption> getOptions() {
    return options;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof FinancialProfileQuestion that && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
