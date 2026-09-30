package br.com.finup.model;

/**
 * Natureza de um movimento financeiro: entrada ou saida de dinheiro.
 *
 * <p>Vale para transacao, recorrencia de transacao e categoria — as tres colunas guardam o nome
 * desta constante. Ate 2026-09-15 existiam tres enums separados ({@code TransactionType}, {@code
 * RecurrenceType} e {@code CategoryType}) com exatamente as mesmas constantes, porque as features
 * foram desenvolvidas em branches paralelas; a duplicacao foi removida na integracao.
 */
public enum TransactionType {
  INCOME,
  EXPENSE
}
