package br.com.finup.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpendingAlertProjectionResponseTest {

  @Test
  @DisplayName("calcula projecao para 3000.00 exatamente como no contrato C1 (4831.53)")
  void calculatesExactContractProjection() {
    SpendingAlertProjectionResponse projection =
        SpendingAlertProjectionResponse.calculate(new BigDecimal("3000.00"));

    assertThat(projection.years()).isEqualTo(5);
    assertThat(projection.annualRatePct()).isEqualTo(10);
    assertThat(projection.futureValue()).isEqualTo(new BigDecimal("4831.53"));
  }

  @ParameterizedTest
  @CsvSource({"100.00, 161.05", "50.00, 80.53", "1000.00, 1610.51", "0.00, 0.00"})
  @DisplayName("calcula valor * 1,10^5 com arredondamento em 2 casas decimais")
  void calculatesProjectionWithRounding(String inputAmount, String expectedFutureValue) {
    SpendingAlertProjectionResponse projection =
        SpendingAlertProjectionResponse.calculate(new BigDecimal(inputAmount));

    assertThat(projection.futureValue()).isEqualTo(new BigDecimal(expectedFutureValue));
  }

  @Test
  @DisplayName("trata valor nulo retornando zero")
  void handlesNullAmountGracefully() {
    SpendingAlertProjectionResponse projection = SpendingAlertProjectionResponse.calculate(null);

    assertThat(projection.futureValue()).isEqualTo(new BigDecimal("0.00"));
    assertThat(projection.years()).isEqualTo(5);
    assertThat(projection.annualRatePct()).isEqualTo(10);
  }
}
