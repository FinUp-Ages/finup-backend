package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Um interesse de investimento do usuário, na tabela {@code user_investment_interests}. */
@Entity
@Table(name = "user_investment_interests")
public class UserInvestmentInterest {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_financial_profile_id", nullable = false, updatable = false)
  private UUID userFinancialProfileId;

  @Column(name = "investment_type", nullable = false, length = 50, updatable = false)
  private String investmentType;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  protected UserInvestmentInterest() {}

  public UserInvestmentInterest(UUID userFinancialProfileId, String investmentType) {
    this.userFinancialProfileId = Objects.requireNonNull(userFinancialProfileId);
    this.investmentType = Objects.requireNonNull(investmentType);
    this.createdAt = Instant.now();
  }

  public UUID getUserFinancialProfileId() {
    return userFinancialProfileId;
  }

  public String getInvestmentType() {
    return investmentType;
  }
}
