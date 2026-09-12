package br.com.finup.service;

import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.FinUpScoreInputs;
import br.com.finup.model.FinUpScoreResult;
import br.com.finup.model.User;
import br.com.finup.repository.FinUpScoreDataProvider;
import br.com.finup.repository.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orquestra o recalculo do FinUp Score. So persiste quando o resultado e {@link
 * FinUpScoreResult.Computed}; em {@link FinUpScoreResult.InsufficientData}, o score existente
 * permanece intocado.
 */
@Service
public class FinUpScoreService {

  private static final Logger log = LoggerFactory.getLogger(FinUpScoreService.class);

  private final UserRepository userRepository;
  private final FinUpScoreDataProvider dataProvider;
  private final FinUpScoreCalculator calculator;

  public FinUpScoreService(
      UserRepository userRepository,
      FinUpScoreDataProvider dataProvider,
      FinUpScoreCalculator calculator) {
    this.userRepository = userRepository;
    this.dataProvider = dataProvider;
    this.calculator = calculator;
  }

  /**
   * Recalcula o FinUp Score de um usuario.
   *
   * @throws ResourceNotFoundException se nao existir usuario com esse id
   */
  public FinUpScoreResult recalculate(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

    FinUpScoreInputs inputs = dataProvider.loadInputs(user);
    FinUpScoreResult result = calculator.calculate(inputs);

    if (result instanceof FinUpScoreResult.Computed computed) {
      userRepository.save(user.withFinUpScore(computed.score()));
      log.info("FinUp Score recalculado: userId={}, score={}", userId, computed.score());
    } else if (result instanceof FinUpScoreResult.InsufficientData insufficient) {
      log.info(
          "FinUp Score nao recalculado por dado insuficiente: userId={}, motivo={}",
          userId,
          insufficient.reason());
    }

    return result;
  }
}
