package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.FinUpScoreInputs;
import br.com.finup.model.FinUpScoreResult;
import br.com.finup.model.User;
import br.com.finup.repository.FinUpScoreDataProvider;
import br.com.finup.repository.UserRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.util.Optional;
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

  private final AuthenticatedIdentity identity =
      new AuthenticatedIdentity("mock-sub-ana", "Ana Souza", "ana@exemplo.com");

  @Test
  @DisplayName("persiste o score no usuario quando o resultado e computado")
  void persistsScoreWhenComputed() {
    User user =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    user.applyAdditionalInfo(null, new BigDecimal("5000"), null);
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
    when(userRepository.findByCognitoId(identity.cognitoId())).thenReturn(Optional.of(user));
    when(dataProvider.loadInputs(user)).thenReturn(inputs);
    when(calculator.calculate(inputs)).thenReturn(new FinUpScoreResult.Computed(700));

    FinUpScoreResult result = finUpScoreService.recalculate(identity);

    assertThat(result).isEqualTo(new FinUpScoreResult.Computed(700));
    assertThat(user.getFinUpScore()).isEqualTo(700);
  }

  @Test
  @DisplayName("nao persiste nada quando o resultado e dado insuficiente")
  void doesNotPersistWhenInsufficientData() {
    User user =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            null, null, null, null, BigDecimal.ZERO, false, BigDecimal.ZERO, null, null);
    when(userRepository.findByCognitoId(identity.cognitoId())).thenReturn(Optional.of(user));
    when(dataProvider.loadInputs(user)).thenReturn(inputs);
    when(calculator.calculate(inputs))
        .thenReturn(new FinUpScoreResult.InsufficientData("renda ausente"));

    FinUpScoreResult result = finUpScoreService.recalculate(identity);

    assertThat(result).isEqualTo(new FinUpScoreResult.InsufficientData("renda ausente"));
    assertThat(user.getFinUpScore()).isNull();
  }

  @Test
  @DisplayName("recalculo de identidade sem usuario local lanca ResourceNotFoundException")
  void throwsWhenUserDoesNotExist() {
    when(userRepository.findByCognitoId(identity.cognitoId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> finUpScoreService.recalculate(identity))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
