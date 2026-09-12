package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.FinUpScoreInputs;
import br.com.finup.model.FinUpScoreResult;
import br.com.finup.model.User;
import br.com.finup.repository.FinUpScoreDataProvider;
import br.com.finup.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Testa so a orquestracao (buscar usuario, pedir inputs, decidir se persiste) — a formula em si e
 * responsabilidade do {@link FinUpScoreCalculatorTest}, por isso o calculador tambem e um duble
 * aqui.
 */
@ExtendWith(MockitoExtension.class)
class FinUpScoreServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private FinUpScoreDataProvider dataProvider;
  @Mock private FinUpScoreCalculator calculator;

  @InjectMocks private FinUpScoreService finUpScoreService;

  @Test
  @DisplayName("persiste o score no usuario quando o resultado e computado")
  void persistsScoreWhenComputed() {
    User user =
        User.register("Ana Souza", "ana@exemplo.com").withMonthlyIncome(new BigDecimal("5000"));
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            user.getMonthlyIncome(),
            null,
            null,
            null,
            BigDecimal.ZERO,
            false,
            BigDecimal.ZERO,
            null,
            null);
    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    when(dataProvider.loadInputs(user)).thenReturn(inputs);
    when(calculator.calculate(inputs)).thenReturn(new FinUpScoreResult.Computed(700));

    FinUpScoreResult result = finUpScoreService.recalculate(user.getId());

    assertThat(result).isEqualTo(new FinUpScoreResult.Computed(700));
    verify(userRepository).save(argThatHasScore(700));
  }

  @Test
  @DisplayName("nao persiste nada quando o resultado e dado insuficiente")
  void doesNotPersistWhenInsufficientData() {
    User user = User.register("Ana Souza", "ana@exemplo.com");
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            null, null, null, null, BigDecimal.ZERO, false, BigDecimal.ZERO, null, null);
    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    when(dataProvider.loadInputs(user)).thenReturn(inputs);
    when(calculator.calculate(inputs))
        .thenReturn(new FinUpScoreResult.InsufficientData("renda ausente"));

    FinUpScoreResult result = finUpScoreService.recalculate(user.getId());

    assertThat(result).isEqualTo(new FinUpScoreResult.InsufficientData("renda ausente"));
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("recalculo de usuario inexistente lanca ResourceNotFoundException")
  void throwsWhenUserDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(userRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> finUpScoreService.recalculate(id))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private User argThatHasScore(int score) {
    return org.mockito.ArgumentMatchers.argThat(
        user -> score == (user.getFinUpScore() == null ? -1 : user.getFinUpScore()));
  }
}
