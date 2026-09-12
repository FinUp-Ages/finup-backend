package br.com.finup.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Usuario do FinUp.
 *
 * <p>Entidade de dominio: guarda o estado e as invariantes que valem para qualquer usuario, venha
 * ele da API, de uma carga ou de um teste. O DTO valida o <em>formato</em> da entrada; o que esta
 * aqui vale sempre.
 *
 * <p>Imutavel de proposito: nao ha setter. Mudanca de estado vira metodo com nome de negocio, que
 * devolve uma nova instancia.
 *
 * <p>Quando o banco entrar, esta classe recebe {@code @Entity} e o {@code id} recebe {@code @Id}.
 * Nada aqui muda por causa disso — a entidade nao conhece HTTP nem persistencia.
 *
 * <p>{@code monthlyIncome} e {@code finUpScore} sao opcionais: podem nao existir ainda.
 */
public class User {

  private final UUID id;
  private final String name;
  private final String email;
  private final Instant createdAt;
  private final BigDecimal monthlyIncome;
  private final Integer finUpScore;

  private User(
      UUID id,
      String name,
      String email,
      Instant createdAt,
      BigDecimal monthlyIncome,
      Integer finUpScore) {
    this.id = id;
    this.name = name;
    this.email = email;
    this.createdAt = createdAt;
    this.monthlyIncome = monthlyIncome;
    this.finUpScore = finUpScore;
  }

  /**
   * Cadastra um usuario novo. O identificador e o instante de criacao sao responsabilidade do
   * dominio, nunca do cliente da API.
   */
  public static User register(String name, String email) {
    return new User(
        UUID.randomUUID(), name.strip(), normalizeEmail(email), Instant.now(), null, null);
  }

  /** Devolve uma copia com o nome trocado. A instancia original continua valida. */
  public User withName(String newName) {
    return new User(
        this.id, newName.strip(), this.email, this.createdAt, this.monthlyIncome, this.finUpScore);
  }

  /** Devolve uma copia com a renda mensal atualizada. */
  public User withMonthlyIncome(BigDecimal newMonthlyIncome) {
    return new User(
        this.id, this.name, this.email, this.createdAt, newMonthlyIncome, this.finUpScore);
  }

  /**
   * Devolve uma copia com o FinUp Score atualizado. Quem decide o valor e {@code
   * FinUpScoreCalculator}; esta classe so guarda o resultado.
   */
  public User withFinUpScore(Integer newFinUpScore) {
    return new User(
        this.id, this.name, this.email, this.createdAt, this.monthlyIncome, newFinUpScore);
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

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public BigDecimal getMonthlyIncome() {
    return monthlyIncome;
  }

  public Integer getFinUpScore() {
    return finUpScore;
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
    return "User[id=%s, email=%s]".formatted(id, email);
  }
}
