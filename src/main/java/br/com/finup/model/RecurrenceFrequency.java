package br.com.finup.model;

/**
 * Periodicidades suportadas por uma recorrencia.
 *
 * <p>So existe {@code MONTHLY} porque e o unico caso do escopo (ex.: conta de luz todo dia 5). Nao
 * adicione outro valor sem uma regra de calculo de proxima ocorrencia definida para ele — {@link
 * TransactionRecurrence#nextOccurrenceOnOrAfter} so sabe calcular esta.
 */
public enum RecurrenceFrequency {
  MONTHLY
}
