package br.com.finup.model;

/**
 * Periodicidades suportadas por uma recorrencia.
 *
 * <p>So existe {@code MONTHLY} porque e o unico caso do escopo atual (ex.: conta de luz todo dia
 * 5). Um valor novo precisa de regra propria em {@link Transaction#nextOccurrenceDate()} — o switch
 * de la deixa de compilar ate ela existir.
 */
public enum RecurrenceFrequency {
  MONTHLY
}
