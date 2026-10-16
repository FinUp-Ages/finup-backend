package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.finup.dto.MetricsSummaryResponse;
import br.com.finup.dto.UserGrowthPointResponse;
import br.com.finup.dto.UserGrowthResponse;
import br.com.finup.exception.InvalidMetricsParameterException;
import br.com.finup.repository.TransactionRepository;
import br.com.finup.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminMetricsServiceTest {

  private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

  /** 15/10/2026 12:00 em Brasilia. */
  private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private TransactionRepository transactionRepository;

  private AdminMetricsService service;

  @BeforeEach
  void setUp() {
    service =
        new AdminMetricsService(userRepository, transactionRepository, Clock.fixed(NOW, BRASILIA));
  }

  // ---------------------------------------------------------------- summary

  @Test
  @DisplayName("summary 30d compara os ultimos 30 dias com os 30 imediatamente anteriores")
  void summaryComparesWithThePreviousPeriodOfTheSameSize() {
    Instant currentStart = Instant.parse("2026-09-15T15:00:00Z");
    Instant previousStart = Instant.parse("2026-08-16T15:00:00Z");
    when(transactionRepository.countActiveUsersBetween(currentStart, NOW)).thenReturn(320L);
    when(transactionRepository.countActiveUsersBetween(previousStart, currentStart))
        .thenReturn(302L);
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(currentStart, NOW))
        .thenReturn(24L);
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            previousStart, currentStart))
        .thenReturn(25L);

    MetricsSummaryResponse summary = service.summary("30d");

    assertThat(summary.activeUsers().value()).isEqualTo(320.0);
    assertThat(summary.activeUsers().previousValue()).isEqualTo(302.0);
    assertThat(summary.activeUsers().changePct()).isEqualTo(6.0);
    assertThat(summary.newUsers().value()).isEqualTo(24.0);
    assertThat(summary.newUsers().previousValue()).isEqualTo(25.0);
    assertThat(summary.newUsers().changePct()).isEqualTo(-4.0);
  }

  @Test
  @DisplayName("summary 7d usa janelas de 7 dias")
  void summaryUsesSevenDayWindows() {
    Instant currentStart = Instant.parse("2026-10-08T15:00:00Z");
    Instant previousStart = Instant.parse("2026-10-01T15:00:00Z");
    when(transactionRepository.countActiveUsersBetween(currentStart, NOW)).thenReturn(10L);
    when(transactionRepository.countActiveUsersBetween(previousStart, currentStart)).thenReturn(5L);
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(currentStart, NOW))
        .thenReturn(3L);
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            previousStart, currentStart))
        .thenReturn(4L);

    MetricsSummaryResponse summary = service.summary("7d");

    assertThat(summary.activeUsers().changePct()).isEqualTo(100.0);
    assertThat(summary.newUsers().changePct()).isEqualTo(-25.0);
  }

  @Test
  @DisplayName("summary 90d usa janelas de 90 dias")
  void summaryUsesNinetyDayWindows() {
    Instant currentStart = Instant.parse("2026-07-17T15:00:00Z");
    Instant previousStart = Instant.parse("2026-04-18T15:00:00Z");
    when(transactionRepository.countActiveUsersBetween(currentStart, NOW)).thenReturn(1L);
    when(transactionRepository.countActiveUsersBetween(previousStart, currentStart)).thenReturn(1L);
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(currentStart, NOW))
        .thenReturn(2L);
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            previousStart, currentStart))
        .thenReturn(2L);

    MetricsSummaryResponse summary = service.summary("90d");

    assertThat(summary.activeUsers().changePct()).isZero();
    assertThat(summary.newUsers().changePct()).isZero();
  }

  @Test
  @DisplayName("summary com periodo anterior zerado nao quebra e devolve changePct 0.0")
  void summaryWithEmptyPreviousPeriodDoesNotDivideByZero() {
    Instant currentStart = Instant.parse("2026-10-08T15:00:00Z");
    Instant previousStart = Instant.parse("2026-10-01T15:00:00Z");
    when(transactionRepository.countActiveUsersBetween(currentStart, NOW)).thenReturn(7L);
    when(transactionRepository.countActiveUsersBetween(previousStart, currentStart)).thenReturn(0L);
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(currentStart, NOW))
        .thenReturn(0L);
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            previousStart, currentStart))
        .thenReturn(0L);

    MetricsSummaryResponse summary = service.summary("7d");

    assertThat(summary.activeUsers().value()).isEqualTo(7.0);
    assertThat(summary.activeUsers().previousValue()).isZero();
    assertThat(summary.activeUsers().changePct()).isZero();
    assertThat(summary.newUsers().changePct()).isZero();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "1d", "14d", "7", "7D", " 7d", "30 d", "semana"})
  @DisplayName("summary recusa period ausente ou fora de 7d, 30d e 90d com 400 e mensagem clara")
  void summaryRejectsInvalidPeriod(String period) {
    assertThatThrownBy(() -> service.summary(period))
        .isInstanceOf(InvalidMetricsParameterException.class)
        .hasMessageContaining("'period'")
        .hasMessageContaining("7d, 30d, 90d");

    verifyNoInteractions(userRepository, transactionRepository);
  }

  @Test
  @DisplayName("summary corta o valor invalido que devolve na mensagem")
  void summaryTruncatesLongInvalidValueInMessage() {
    String huge = "x".repeat(5_000);

    assertThatThrownBy(() -> service.summary(huge))
        .isInstanceOf(InvalidMetricsParameterException.class)
        .satisfies(e -> assertThat(e.getMessage().length()).isLessThan(200));
  }

  // -------------------------------------------------------------- changePct

  @ParameterizedTest(name = "changePct({0}, {1}) = {2}")
  @CsvSource({
    "320, 302, 6.0",
    "24, 25, -4.0",
    "10, 10, 0.0",
    "0, 5, -100.0",
    "1, 3, -66.7",
    "2, 3, -33.3",
    "17, 16, 6.3",
    "15, 16, -6.3",
    "5, 0, 0.0",
    "0, 0, 0.0",
    "378, 100, 278.0"
  })
  @DisplayName(
      "changePct tem 1 casa decimal, arredonda a metade para longe do zero e nao divide por"
          + " zero")
  void changePctRoundsToOneDecimal(long value, long previous, double expected) {
    assertThat(AdminMetricsService.changePct(value, previous)).isEqualTo(expected);
  }

  // ------------------------------------------------------------ userGrowth

  private static Instant startOfMonth(String yearMonth) {
    return YearMonth.parse(yearMonth).atDay(1).atStartOfDay(BRASILIA).toInstant();
  }

  private void stubMonth(String month, String nextMonth, long newUsers) {
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            startOfMonth(month), startOfMonth(nextMonth)))
        .thenReturn(newUsers);
  }

  @Test
  @DisplayName("user-growth devolve 6 meses por padrao, em ordem, com acumulado e growthPct")
  void userGrowthDefaultsToSixMonthsInOrder() {
    when(userRepository.countByCreatedAtLessThan(startOfMonth("2026-05"))).thenReturn(100L);
    stubMonth("2026-05", "2026-06", 100);
    stubMonth("2026-06", "2026-07", 0);
    stubMonth("2026-07", "2026-08", 50);
    stubMonth("2026-08", "2026-09", 0);
    stubMonth("2026-09", "2026-10", 28);
    stubMonth("2026-10", "2026-11", 100);

    UserGrowthResponse growth = service.userGrowth(null);

    assertThat(growth.growthPct()).isEqualTo(278.0);
    assertThat(growth.points())
        .containsExactly(
            new UserGrowthPointResponse("2026-05", 100, 200),
            new UserGrowthPointResponse("2026-06", 0, 200),
            new UserGrowthPointResponse("2026-07", 50, 250),
            new UserGrowthPointResponse("2026-08", 0, 250),
            new UserGrowthPointResponse("2026-09", 28, 278),
            new UserGrowthPointResponse("2026-10", 100, 378));
  }

  @Test
  @DisplayName("user-growth inclui os meses sem cadastro com newUsers 0 e nao pula nenhum")
  void userGrowthKeepsEmptyMonths() {
    when(userRepository.countByCreatedAtLessThan(startOfMonth("2026-08"))).thenReturn(40L);
    stubMonth("2026-08", "2026-09", 0);
    stubMonth("2026-09", "2026-10", 0);
    stubMonth("2026-10", "2026-11", 0);

    UserGrowthResponse growth = service.userGrowth("3");

    assertThat(growth.points())
        .extracting(UserGrowthPointResponse::month, UserGrowthPointResponse::newUsers)
        .containsExactly(tuple("2026-08", 0L), tuple("2026-09", 0L), tuple("2026-10", 0L));
    assertThat(growth.points()).extracting(UserGrowthPointResponse::totalUsers).containsOnly(40L);
    assertThat(growth.growthPct()).isZero();
  }

  @Test
  @DisplayName("user-growth atravessa a virada de ano em ordem cronologica")
  void userGrowthCrossesYearBoundary() {
    AdminMetricsService february =
        new AdminMetricsService(
            userRepository,
            transactionRepository,
            Clock.fixed(Instant.parse("2026-02-10T15:00:00Z"), BRASILIA));
    when(userRepository.countByCreatedAtLessThan(startOfMonth("2025-11"))).thenReturn(10L);
    stubMonth("2025-11", "2025-12", 1);
    stubMonth("2025-12", "2026-01", 2);
    stubMonth("2026-01", "2026-02", 3);
    stubMonth("2026-02", "2026-03", 4);

    UserGrowthResponse growth = february.userGrowth("4");

    assertThat(growth.points())
        .extracting(UserGrowthPointResponse::month)
        .containsExactly("2025-11", "2025-12", "2026-01", "2026-02");
    assertThat(growth.points().get(3).totalUsers()).isEqualTo(20L);
    assertThat(growth.growthPct()).isEqualTo(100.0);
  }

  @Test
  @DisplayName("user-growth sem usuarios antes da janela devolve growthPct 0.0")
  void userGrowthWithoutUsersBeforeWindowDoesNotDivideByZero() {
    when(userRepository.countByCreatedAtLessThan(startOfMonth("2026-10"))).thenReturn(0L);
    stubMonth("2026-10", "2026-11", 5);

    UserGrowthResponse growth = service.userGrowth("1");

    assertThat(growth.growthPct()).isZero();
    assertThat(growth.points()).containsExactly(new UserGrowthPointResponse("2026-10", 5, 5));
  }

  @Test
  @DisplayName("user-growth aceita o maximo de 24 meses")
  void userGrowthAcceptsTwentyFourMonths() {
    when(userRepository.countByCreatedAtLessThan(startOfMonth("2024-11"))).thenReturn(0L);
    for (YearMonth month = YearMonth.of(2024, 11);
        !month.isAfter(YearMonth.of(2026, 10));
        month = month.plusMonths(1)) {
      stubMonth(month.toString(), month.plusMonths(1).toString(), 1);
    }

    UserGrowthResponse growth = service.userGrowth("24");

    assertThat(growth.points()).hasSize(24);
    assertThat(growth.points().get(0).month()).isEqualTo("2024-11");
    assertThat(growth.points().get(23).totalUsers()).isEqualTo(24L);
  }

  @Test
  @DisplayName("os meses seguem o fuso de Brasilia: outubro comeca as 03:00Z, nao as 00:00Z")
  void monthsStartAtMidnightInBrasilia() {
    assertThat(startOfMonth("2026-10")).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
    when(userRepository.countByCreatedAtLessThan(Instant.parse("2026-10-01T03:00:00Z")))
        .thenReturn(7L);
    when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Instant.parse("2026-10-01T03:00:00Z"), Instant.parse("2026-11-01T03:00:00Z")))
        .thenReturn(1L);

    List<UserGrowthPointResponse> points = service.userGrowth("1").points();

    assertThat(points).containsExactly(new UserGrowthPointResponse("2026-10", 1, 8));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "", "6.5", "-1", "0", "25", "999999999999", " 6"})
  @DisplayName("user-growth recusa months nao numerico, negativo ou fora de 1 a 24 com 400")
  void userGrowthRejectsInvalidMonths(String months) {
    assertThatThrownBy(() -> service.userGrowth(months))
        .isInstanceOf(InvalidMetricsParameterException.class)
        .hasMessageContaining("'months'")
        .hasMessageContaining("1 a 24");

    verifyNoInteractions(userRepository, transactionRepository);
  }
}
