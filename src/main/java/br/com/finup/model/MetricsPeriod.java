package br.com.finup.model;

import java.util.Arrays;
import java.util.Optional;

/** Janela do resumo de metricas do dashboard admin, como o cliente a escreve em {@code period}. */
public enum MetricsPeriod {
  DAYS_7("7d", 7),
  DAYS_30("30d", 30),
  DAYS_90("90d", 90);

  private final String param;
  private final int days;

  MetricsPeriod(String param, int days) {
    this.param = param;
    this.days = days;
  }

  public String param() {
    return param;
  }

  public int days() {
    return days;
  }

  /** Exato e sem maiusculas: o contrato aceita so {@code 7d}, {@code 30d} e {@code 90d}. */
  public static Optional<MetricsPeriod> fromParam(String value) {
    return Arrays.stream(values()).filter(period -> period.param.equals(value)).findFirst();
  }
}
