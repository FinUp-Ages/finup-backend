package br.com.finup.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Garante o estado de recorrencia no cadastro e o calculo da proxima ocorrencia mensal. */
class TransactionTest {

  @Test
  @DisplayName("transacao avulsa nao tem dados de recorrencia nem proxima ocorrencia")
  void nonRecurringHasNoRecurrenceData() {
    Transaction transaction = register(LocalDate.of(2026, 9, 5), null);

    assertThat(transaction.isRecurring()).isFalse();
    assertThat(transaction.getRecurrenceFrequency()).isNull();
    assertThat(transaction.getLastOccurrenceDate()).isNull();
    assertThat(transaction.nextOccurrenceDate()).isEmpty();
  }

  @Test
  @DisplayName("serie recorrente conta o proprio cadastro como a ultima ocorrencia")
  void recurringStartsWithTransactionDateAsLastOccurrence() {
    Transaction transaction = monthly(LocalDate.of(2026, 9, 5));

    assertThat(transaction.isRecurring()).isTrue();
    assertThat(transaction.getRecurrenceFrequency()).isEqualTo(RecurrenceFrequency.MONTHLY);
    assertThat(transaction.getLastOccurrenceDate()).isEqualTo(LocalDate.of(2026, 9, 5));
  }

  @Test
  @DisplayName("mensal cai no mesmo dia do mes seguinte")
  void monthlyFallsOnSameDayOfNextMonth() {
    assertThat(monthly(LocalDate.of(2026, 9, 5)).nextOccurrenceDate())
        .contains(LocalDate.of(2026, 10, 5));
  }

  @Test
  @DisplayName("mensal atravessa a virada do ano")
  void monthlyCrossesYearBoundary() {
    assertThat(monthly(LocalDate.of(2026, 12, 5)).nextOccurrenceDate())
        .contains(LocalDate.of(2027, 1, 5));
  }

  @Test
  @DisplayName("usa o ultimo dia do mes quando o dia de referencia nao existe nele")
  void clampsToLastDayOfShorterMonth() {
    assertThat(monthly(LocalDate.of(2026, 1, 31)).nextOccurrenceDate())
        .contains(LocalDate.of(2026, 2, 28));
    assertThat(monthly(LocalDate.of(2028, 1, 31)).nextOccurrenceDate())
        .contains(LocalDate.of(2028, 2, 29));
  }

  @Test
  @DisplayName("volta ao dia de referencia depois de um mes mais curto")
  void returnsToReferenceDayAfterShorterMonth() {
    Transaction transaction = monthly(LocalDate.of(2026, 1, 31));
    // Simula a serie depois de gerar a ocorrencia de fevereiro, que caiu no dia 28.
    ReflectionTestUtils.setField(transaction, "lastOccurrenceDate", LocalDate.of(2026, 2, 28));

    assertThat(transaction.nextOccurrenceDate()).contains(LocalDate.of(2026, 3, 31));
  }

  private Transaction monthly(LocalDate transactionDate) {
    return register(transactionDate, RecurrenceFrequency.MONTHLY);
  }

  private Transaction register(LocalDate transactionDate, RecurrenceFrequency frequency) {
    return Transaction.register(
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        TransactionType.EXPENSE,
        "Conta de luz",
        new BigDecimal("250.00"),
        transactionDate,
        frequency);
  }
}
