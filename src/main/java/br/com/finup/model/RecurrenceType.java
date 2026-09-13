package br.com.finup.model;

/**
 * Tipo da transacao gerada pela recorrencia.
 *
 * <p>Duplica {@code TransactionType} (PR #9, ainda nao integrado) de proposito: esta feature nao
 * depende de uma PR aberta. Quando as duas branches forem integradas, unificar em um so enum.
 */
public enum RecurrenceType {
  INCOME,
  EXPENSE
}
