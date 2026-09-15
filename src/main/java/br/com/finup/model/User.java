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

/**
 * Usuario persistido exatamente na estrutura da tabela {@code users}.
 *
 * <p>Quem autentica e gerencia senha e o AWS Cognito — por isso nao existe campo de senha aqui.
 * {@code cognitoId} guarda o "sub" do token, o identificador imutavel da identidade no Cognito; e
 * ele que vincula este registro local a essa identidade, nao o e-mail (que pode ser alterado
 * diretamente no Cognito).
 */
@Entity
@Table(name = "users")
public class User {

  @Id private UUID id;

  @Column(name = "cognito_id", nullable = false, unique = true)
  private String cognitoId;

  @Column(length = 255)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "monthly_income", precision = 12, scale = 2)
  private BigDecimal monthlyIncome;

  @Column(name = "fin_up_score")
  private Integer finUpScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "financial_profile", length = 50)
  private FinancialProfile financialProfile;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected User() {}

  private User(String cognitoId, String name, String email) {
    this.id = UUID.randomUUID();
    this.cognitoId = Objects.requireNonNull(cognitoId).strip();
    this.name = name != null ? name.strip() : null;
    this.email = normalizeEmail(email);
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  /**
   * Cria o registro local a partir da identidade ja autenticada pelo Cognito (Etapa 1 do cadastro).
   * O identificador local e o instante de criacao sao responsabilidade do dominio, nunca do cliente
   * da API. As informacoes complementares (Etapa 2) ainda nao existem neste ponto. {@code name}
   * pode ser nulo — no Cognito real, alguns provedores (ex.: login com Apple) so mandam o nome no
   * primeiro acesso.
   */
  public static User createFromCognitoIdentity(String cognitoId, String name, String email) {
    return new User(cognitoId, name, email);
  }

  /**
   * Etapa 2 do cadastro: grava ou atualiza as informacoes complementares de um usuario que ja
   * existe, mutando a propria instancia gerenciada pelo JPA. Um parametro {@code null} significa
   * "nao informado nesta chamada", nao "apagar o valor atual" — por isso mantem o valor anterior
   * quando o parametro correspondente vem nulo. {@code updatedAt} e atualizado automaticamente pelo
   * {@link #markAsUpdated()} no proximo flush, nao precisa ser tocado aqui.
   */
  public void applyAdditionalInfo(
      LocalDate newBirthDate, BigDecimal newMonthlyIncome, FinancialProfile newFinancialProfile) {
    if (newBirthDate != null) {
      this.birthDate = newBirthDate;
    }
    if (newMonthlyIncome != null) {
      this.monthlyIncome = newMonthlyIncome;
    }
    if (newFinancialProfile != null) {
      this.financialProfile = newFinancialProfile;
    }
  }

  @PreUpdate
  void markAsUpdated() {
    updatedAt = Instant.now();
  }

  /**
   * E-mail e chave de unicidade: sem normalizar, "Ana@x.com" e "ana@x.com" viram dois cadastros. A
   * regra mora aqui, e nao no service, para valer em qualquer caminho de criacao.
   */
  private static String normalizeEmail(String email) {
    return email.strip().toLowerCase();
  }

  public UUID getId() {
    return id;
  }

  public String getCognitoId() {
    return cognitoId;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public BigDecimal getMonthlyIncome() {
    return monthlyIncome;
  }

  public Integer getFinUpScore() {
    return finUpScore;
  }

  public FinancialProfile getFinancialProfile() {
    return financialProfile;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /** Identidade de entidade e o id — dois usuarios com o mesmo id sao o mesmo usuario. */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof User user && Objects.equals(id, user.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "User[id=%s, cognitoId=%s, email=%s]".formatted(id, cognitoId, email);
  }
}
