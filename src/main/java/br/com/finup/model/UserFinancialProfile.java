package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Informacoes complementares do cadastro (Etapas 2 e 3), na tabela {@code user_financial_profiles}
 * — um registro por usuario, criado na primeira vez que {@code PATCH
 * /api/v1/users/me/additional-info} e chamado.
 *
 * <p>Mapeia so as colunas que o cadastro usa; as demais da tabela (estimativas de gasto, habitos,
 * investimentos) ficam para quem for consumi-las.
 */
@Entity
@Table(name = "user_financial_profiles")
public class UserFinancialProfile {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @Column(length = 20)
  private String phone;

  @Column(length = 255)
  private String profession;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "monthly_income", precision = 12, scale = 2)
  private BigDecimal monthlyIncome;

  @Column(name = "monthly_expenses_estimate", precision = 12, scale = 2)
  private BigDecimal monthlyExpensesEstimate;

  @Enumerated(EnumType.STRING)
  @Column(name = "income_expense_relation", length = 50)
  private IncomeExpenseRelation incomeExpenseRelation;

  @Enumerated(EnumType.STRING)
  @Column(name = "spending_habit", length = 50)
  private SpendingHabit spendingHabit;

  @Column(name = "has_investments")
  private Boolean hasInvestments;

  @Column(name = "approximate_invested_amount", precision = 12, scale = 2)
  private BigDecimal approximateInvestedAmount;

  @Column(name = "investment_experience_time", length = 50)
  private String investmentExperienceTime;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserFinancialProfile() {}

  private UserFinancialProfile(User user) {
    this.user = Objects.requireNonNull(user);
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  /** Perfil vazio de um usuario que ainda nao informou nada alem da Etapa 1. */
  public static UserFinancialProfile createFor(User user) {
    return new UserFinancialProfile(user);
  }

  /**
   * Atualizacao parcial: parametro {@code null} significa "nao informado nesta chamada", nao
   * "apagar o valor atual" — o valor anterior e mantido. {@code updatedAt} e atualizado pelo {@link
   * #markAsUpdated()} no proximo flush.
   */
  public void apply(
      String newPhone, String newProfession, LocalDate newBirthDate, BigDecimal newMonthlyIncome) {
    if (newPhone != null) {
      this.phone = newPhone;
    }
    if (newProfession != null) {
      this.profession = newProfession.strip();
    }
    if (newBirthDate != null) {
      this.birthDate = newBirthDate;
    }
    if (newMonthlyIncome != null) {
      this.monthlyIncome = newMonthlyIncome;
    }
  }

  /**
   * Triagem: regrava os campos que o questionário cobre, inclusive limpando os de investimento
   * quando a pessoa passou a responder que não investe. Diferente de {@link #apply}, aqui um envio
   * é sempre completo, então {@code null} significa "não se aplica", não "não informado".
   */
  public void applyTriage(
      BigDecimal expensesEstimate,
      IncomeExpenseRelation relation,
      SpendingHabit habit,
      boolean invests,
      BigDecimal investedAmount,
      String investmentTime) {
    this.monthlyExpensesEstimate = expensesEstimate;
    this.incomeExpenseRelation = relation;
    this.spendingHabit = habit;
    this.hasInvestments = invests;
    this.approximateInvestedAmount = invested(invests, investedAmount);
    this.investmentExperienceTime = invests ? investmentTime : null;
  }

  private static BigDecimal invested(boolean invests, BigDecimal amount) {
    return invests ? amount : null;
  }

  @PreUpdate
  void markAsUpdated() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getPhone() {
    return phone;
  }

  public String getProfession() {
    return profession;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public BigDecimal getMonthlyIncome() {
    return monthlyIncome;
  }

  public BigDecimal getMonthlyExpensesEstimate() {
    return monthlyExpensesEstimate;
  }

  public IncomeExpenseRelation getIncomeExpenseRelation() {
    return incomeExpenseRelation;
  }

  public SpendingHabit getSpendingHabit() {
    return spendingHabit;
  }

  public Boolean getHasInvestments() {
    return hasInvestments;
  }

  public BigDecimal getApproximateInvestedAmount() {
    return approximateInvestedAmount;
  }

  public String getInvestmentExperienceTime() {
    return investmentExperienceTime;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
