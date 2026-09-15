package br.com.finup.service;

import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.FinUpScoreInputs;
import br.com.finup.model.FinUpScoreResult;
import br.com.finup.model.User;
import br.com.finup.repository.FinUpScoreDataProvider;
import br.com.finup.repository.UserRepository;
import br.com.finup.security.AuthenticatedIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
   * Recalcula o FinUp Score do usuario correspondente a identidade autenticada.
   *
   * @throws ResourceNotFoundException se essa identidade ainda nao tiver usuario local
   */
  @Transactional
  public FinUpScoreResult recalculate(AuthenticatedIdentity identity) {
    User user =
        userRepository
            .findByCognitoId(identity.cognitoId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Usuario nao encontrado para a identidade autenticada."));

    FinUpScoreInputs inputs = dataProvider.loadInputs(user);
    FinUpScoreResult result = calculator.calculate(inputs);

    if (result instanceof FinUpScoreResult.Computed computed) {
      user.updateFinUpScore(computed.score());
      log.info("FinUp Score recalculado: userId={}, score={}", user.getId(), computed.score());
    } else if (result instanceof FinUpScoreResult.InsufficientData insufficient) {
      log.info(
          "FinUp Score nao recalculado por dado insuficiente: userId={}, motivo={}",
          user.getId(),
          insufficient.reason());
    }

    return result;
  }
}
