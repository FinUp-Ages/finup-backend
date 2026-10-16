package br.com.finup.service;

import br.com.finup.dto.MetricValueResponse;
import br.com.finup.dto.MetricsSummaryResponse;
import br.com.finup.dto.UserGrowthPointResponse;
import br.com.finup.dto.UserGrowthResponse;
import br.com.finup.exception.InvalidMetricsParameterException;
import br.com.finup.model.MetricsPeriod;
import br.com.finup.repository.TransactionRepository;
import br.com.finup.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metricas de usuarios do dashboard admin.
 *
 * <p>Toda contagem e feita no banco ({@code COUNT}); nenhum usuario ou transacao e carregado em
 * memoria. Os limites de cada janela sao calculados aqui, a partir do {@link Clock} da aplicacao
 * (fuso de Brasilia), e entram nas consultas como intervalos {@code [inicio, fim)}.
 */
@Service
public class AdminMetricsService {

  static final int DEFAULT_MONTHS = 6;
  static final int MAX_MONTHS = 24;

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int MAX_ECHOED_LENGTH = 40;

  private final UserRepository userRepository;
  private final TransactionRepository transactionRepository;
  private final Clock clock;

  public AdminMetricsService(
      UserRepository userRepository, TransactionRepository transactionRepository, Clock clock) {
    this.userRepository = userRepository;
    this.transactionRepository = transactionRepository;
    this.clock = clock;
  }

  /**
   * Usuarios ativos e novos nos ultimos {@code N} dias, comparados com os {@code N} dias
   * imediatamente anteriores. As duas janelas sao moveis e terminam em "agora": a atual e {@code
   * [agora - N, agora)} e a anterior, {@code [agora - 2N, agora - N)}.
   *
   * @param periodParam {@code 7d}, {@code 30d} ou {@code 90d}
   * @throws InvalidMetricsParameterException se o periodo faltar ou for outro valor
   */
  @Transactional(readOnly = true)
  public MetricsSummaryResponse summary(String periodParam) {
    MetricsPeriod period = parsePeriod(periodParam);
    Instant now = clock.instant();
    Instant currentStart = now.minus(period.days(), ChronoUnit.DAYS);
    Instant previousStart = currentStart.minus(period.days(), ChronoUnit.DAYS);

    MetricValueResponse activeUsers =
        metric(
            transactionRepository.countActiveUsersBetween(currentStart, now),
            transactionRepository.countActiveUsersBetween(previousStart, currentStart));
    MetricValueResponse newUsers =
        metric(
            userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(currentStart, now),
            userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                previousStart, currentStart));
    return new MetricsSummaryResponse(activeUsers, newUsers);
  }

  /**
   * Cadastros por mes, do mais antigo ao mais recente, terminando no mes corrente (ainda parcial).
   * Meses sem cadastro entram com {@code newUsers = 0}: a serie nunca pula um mes.
   *
   * <p>{@code growthPct} compara o total de usuarios no fim do ultimo mes com o total antes do
   * primeiro. Sao {@code months + 1} contagens, no maximo {@value #MAX_MONTHS} + 1.
   *
   * @param monthsParam quantidade de meses, de 1 a {@value #MAX_MONTHS}; ausente vale {@value
   *     #DEFAULT_MONTHS}
   * @throws InvalidMetricsParameterException se nao for um inteiro dentro desse intervalo
   */
  @Transactional(readOnly = true)
  public UserGrowthResponse userGrowth(String monthsParam) {
    int months = parseMonths(monthsParam);
    YearMonth firstMonth = YearMonth.now(clock).minusMonths(months - 1L);

    long totalBeforeWindow = userRepository.countByCreatedAtLessThan(startOf(firstMonth));
    long runningTotal = totalBeforeWindow;
    List<UserGrowthPointResponse> points = new ArrayList<>(months);
    for (int i = 0; i < months; i++) {
      YearMonth month = firstMonth.plusMonths(i);
      long newUsers =
          userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
              startOf(month), startOf(month.plusMonths(1)));
      runningTotal += newUsers;
      points.add(new UserGrowthPointResponse(month.toString(), newUsers, runningTotal));
    }
    return new UserGrowthResponse(changePct(runningTotal, totalBeforeWindow), points);
  }

  private Instant startOf(YearMonth month) {
    return month.atDay(1).atStartOfDay(clock.getZone()).toInstant();
  }

  private static MetricValueResponse metric(long value, long previousValue) {
    return new MetricValueResponse(value, previousValue, changePct(value, previousValue));
  }

  /**
   * Variacao percentual com 1 casa decimal, arredondada para cima na metade ({@code HALF_UP}). Sem
   * base de comparacao ({@code previous == 0}) devolve {@code 0.0}: nao ha como dividir, e omitir o
   * campo quebraria o contrato. A conta e feita em {@link BigDecimal} para o arredondamento nao
   * depender da representacao binaria do {@code double}.
   */
  static double changePct(long value, long previous) {
    if (previous == 0) {
      return 0.0;
    }
    return BigDecimal.valueOf(value - previous)
        .multiply(HUNDRED)
        .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
        .doubleValue();
  }

  private static MetricsPeriod parsePeriod(String value) {
    if (value == null) {
      throw new InvalidMetricsParameterException(
          "Parametro 'period' e obrigatorio. Valores aceitos: 7d, 30d, 90d.");
    }
    return MetricsPeriod.fromParam(value)
        .orElseThrow(
            () ->
                new InvalidMetricsParameterException(
                    "Parametro 'period' invalido: '%s'. Valores aceitos: 7d, 30d, 90d."
                        .formatted(echoed(value))));
  }

  private static int parseMonths(String value) {
    if (value == null) {
      return DEFAULT_MONTHS;
    }
    int months;
    try {
      months = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new InvalidMetricsParameterException(
          "Parametro 'months' invalido: '%s' nao e um numero inteiro. Informe de 1 a %d."
              .formatted(echoed(value), MAX_MONTHS));
    }
    if (months < 1 || months > MAX_MONTHS) {
      throw new InvalidMetricsParameterException(
          "Parametro 'months' fora do intervalo: %d. Informe de 1 a %d."
              .formatted(months, MAX_MONTHS));
    }
    return months;
  }

  /** O valor volta na mensagem de erro; corta o que for longo para nao inflar a resposta. */
  private static String echoed(String value) {
    return value.length() <= MAX_ECHOED_LENGTH
        ? value
        : value.substring(0, MAX_ECHOED_LENGTH) + "...";
  }
}
