package br.com.finup.model;

/**
 * Resultado do calculo do FinUp Score.
 *
 * <p>Sealed em vez de excecao: "sem dados suficientes" e um resultado de negocio esperado, nao um
 * erro, e o compilador obriga a tratar os dois casos.
 */
public sealed interface FinUpScoreResult {

  /** Score calculado, pronto para persistir em {@code Users.FinUpScore}. */
  record Computed(int score) implements FinUpScoreResult {}

  /** {@code Users.FinUpScore} nao deve ser sobrescrito nesse caso. */
  record InsufficientData(String reason) implements FinUpScoreResult {}
}
