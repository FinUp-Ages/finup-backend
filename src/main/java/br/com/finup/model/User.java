package br.com.finup.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Usuario do FinUp.
 *
 * <p>Entidade de dominio: guarda o estado e as invariantes que valem para qualquer usuario. O DTO
 * valida o <em>formato</em> da entrada; o que esta aqui vale sempre.
 *
 * <p>Imutavel de proposito: nao ha setter. Mudanca de estado vira metodo com nome de negocio, que
 * devolve uma nova instancia.
 *
 * <p>Quem autentica e gerencia senha e o AWS Cognito — por isso nao existe campo de senha aqui.
 * {@code cognitoId} guarda o "sub" do token, o identificador imutavel da identidade no Cognito; e
 * ele que vincula este registro local a essa identidade, nao o e-mail (que pode ser alterado
 * diretamente no Cognito).
 *
 * <p>Quando o banco entrar, esta classe recebe {@code @Entity} e o {@code id} recebe {@code @Id}.
 * Nada aqui muda por causa disso — a entidade nao conhece HTTP nem persistencia.
 */
public class User {

  private final UUID id;
  private final String cognitoId;
  private final String name;
  private final String email;
  private final LocalDate birthDate;
  private final BigDecimal monthlyIncome;
  private final String financialProfile;
  private final Instant createdAt;
  private final Instant updatedAt;

  private User(
      UUID id,
      String cognitoId,
      String name,
      String email,
      LocalDate birthDate,
      BigDecimal monthlyIncome,
      String financialProfile,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.cognitoId = cognitoId;
    this.name = name;
    this.email = email;
    this.birthDate = birthDate;
    this.monthlyIncome = monthlyIncome;
    this.financialProfile = financialProfile;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Cria o registro local a partir da identidade ja autenticada pelo Cognito (Etapa 1 do cadastro).
   * O identificador local e o instante de criacao sao responsabilidade do dominio, nunca do cliente
   * da API. As informacoes complementares (Etapa 2) ainda nao existem neste ponto.
   */
  public static User createFromCognitoIdentity(String cognitoId, String name, String email) {
    Instant now = Instant.now();
    return new User(
        UUID.randomUUID(),
        cognitoId.strip(),
        name.strip(),
        normalizeEmail(email),
        null,
        null,
        null,
        now,
        now);
  }

  /**
   * Etapa 2 do cadastro: grava ou atualiza as informacoes complementares de um usuario que ja
   * existe. Um campo {@code null} significa "nao informado nesta chamada", nao "apagar o valor
   * atual" — por isso mantem o valor anterior quando o parametro correspondente vem nulo.
   */
  public User withAdditionalInfo(
      LocalDate newBirthDate, BigDecimal newMonthlyIncome, String newFinancialProfile) {
    return new User(
        this.id,
        this.cognitoId,
        this.name,
        this.email,
        newBirthDate != null ? newBirthDate : this.birthDate,
        newMonthlyIncome != null ? newMonthlyIncome : this.monthlyIncome,
        newFinancialProfile != null ? newFinancialProfile.strip() : this.financialProfile,
        this.createdAt,
        Instant.now());
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

  public String getFinancialProfile() {
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
