package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
