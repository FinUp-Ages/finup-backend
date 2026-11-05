package br.com.finup.service;

import br.com.finup.dto.SubmitTriageRequest;
import br.com.finup.dto.TriageAnswersResponse;
import br.com.finup.dto.TriageAssessmentResponse;
import br.com.finup.dto.TriageQuestionnaireResponse;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.FinancialProfile;
import br.com.finup.model.RiskAssessment;
import br.com.finup.model.RiskSeverity;
import br.com.finup.model.TriageAnswers;
import br.com.finup.model.TriageResponse;
import br.com.finup.model.TriageRisk;
import br.com.finup.model.User;
import br.com.finup.model.UserFinancialProfile;
import br.com.finup.model.UserInvestmentInterest;
import br.com.finup.repository.RiskAssessmentRepository;
import br.com.finup.repository.TriageResponseRepository;
import br.com.finup.repository.UserFinancialProfileRepository;
import br.com.finup.repository.UserInvestmentInterestRepository;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.service.TriageAnswersValidator.Validated;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triagem financeira: valida e grava as respostas, atualiza o perfil financeiro, recalcula o FinUp
 * Score e devolve a avaliação de riscos. O envio é tudo ou nada: a validação roda antes de qualquer
 * escrita e as escritas dividem uma transação.
 *
 * <p>As respostas são dado financeiro pessoal e nunca entram em log: só ids e contagens.
 */
@Service
public class TriageService {

  private static final Logger log = LoggerFactory.getLogger(TriageService.class);

  private final UserService userService;
  private final TriageAnswersValidator validator;
  private final TriageRiskEvaluator evaluator;
  private final TriageResponseRepository responseRepository;
  private final RiskAssessmentRepository assessmentRepository;
  private final UserFinancialProfileRepository profileRepository;
  private final UserInvestmentInterestRepository interestRepository;
  private final FinUpScoreService finUpScoreService;
  private final Clock clock;

  public TriageService(
      UserService userService,
      TriageAnswersValidator validator,
      TriageRiskEvaluator evaluator,
      TriageResponseRepository responseRepository,
      RiskAssessmentRepository assessmentRepository,
      UserFinancialProfileRepository profileRepository,
      UserInvestmentInterestRepository interestRepository,
      FinUpScoreService finUpScoreService,
      Clock clock) {
    this.userService = userService;
    this.validator = validator;
    this.evaluator = evaluator;
    this.responseRepository = responseRepository;
    this.assessmentRepository = assessmentRepository;
    this.profileRepository = profileRepository;
    this.interestRepository = interestRepository;
    this.finUpScoreService = finUpScoreService;
    this.clock = clock;
  }

  public TriageQuestionnaireResponse questionnaire() {
    return new TriageQuestionnaireResponse(
        TriageQuestionnaire.VERSION, TriageQuestionnaire.questions());
  }

  /**
   * Registra um envio. Reenviar cria uma linha nova e a mais recente vale.
   *
   * @throws br.com.finup.exception.InvalidTriageAnswersException se as respostas forem inválidas
   * @throws ResourceNotFoundException se a identidade ainda não tiver usuário local
   */
  @Transactional
  public TriageAssessmentResponse submit(
      AuthenticatedIdentity identity, SubmitTriageRequest request) {
    User user = userService.findByAuthenticatedIdentity(identity);
    Validated validated = validator.validate(request.version(), request.answers());
    TriageAnswers answers = validated.typed();

    UserFinancialProfile profile =
        profileRepository
            .findByUserId(user.getId())
            .orElseGet(() -> UserFinancialProfile.createFor(user));
    List<TriageRisk> risks = evaluator.evaluate(answers, profile.getMonthlyIncome());
    FinancialProfile investorProfile = evaluator.profileOf(answers);
    Instant now = Instant.now(clock);

    TriageResponse response =
        responseRepository.save(
            new TriageResponse(
                user.getId(), TriageQuestionnaire.VERSION, validated.normalized(), now));
    String summary = summarize(investorProfile, risks);
    assessmentRepository.save(
        new RiskAssessment(user.getId(), response.getId(), investorProfile, summary, risks, now));

    profile.applyTriage(
        answers.monthlyExpenses(),
        answers.incomeExpenseRelation(),
        answers.spendingHabit(),
        answers.invests(),
        answers.investedAmount(),
        answers.investmentTime());
    UserFinancialProfile savedProfile = profileRepository.save(profile);
    interestRepository.deleteByUserFinancialProfileId(savedProfile.getId());
    answers
        .investmentInterests()
        .forEach(
            type ->
                interestRepository.save(new UserInvestmentInterest(savedProfile.getId(), type)));
    user.applyAdditionalInfo(null, null, investorProfile);

    finUpScoreService.recalculate(identity);

    log.info(
        "Triagem registrada: userId={}, responseId={}, riscos={}",
        user.getId(),
        response.getId(),
        risks.size());
    return assessmentOf(investorProfile, summary, risks, now);
  }

  /** Última avaliação do usuário autenticado. */
  @Transactional(readOnly = true)
  public TriageAssessmentResponse latestAssessment(AuthenticatedIdentity identity) {
    User user = userService.findByAuthenticatedIdentity(identity);
    RiskAssessment assessment =
        assessmentRepository
            .findFirstByUserIdOrderByCreatedAtDesc(user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Triagem ainda nao respondida."));
    return assessmentOf(
        assessment.getProfile(),
        assessment.getSummary(),
        assessment.getRisks(),
        assessment.getCreatedAt());
  }

  /** Últimas respostas do usuário autenticado, para pré-preencher o questionário. */
  @Transactional(readOnly = true)
  public TriageAnswersResponse latestAnswers(AuthenticatedIdentity identity) {
    User user = userService.findByAuthenticatedIdentity(identity);
    TriageResponse response =
        responseRepository
            .findFirstByUserIdOrderByCreatedAtDesc(user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Triagem ainda nao respondida."));
    return new TriageAnswersResponse(
        response.getQuestionnaireVersion(), response.getAnswers(), response.getCreatedAt());
  }

  private static TriageAssessmentResponse assessmentOf(
      FinancialProfile profile, String summary, List<TriageRisk> risks, Instant completedAt) {
    return new TriageAssessmentResponse(
        new TriageAssessmentResponse.Assessment(profile, summary, risks), completedAt);
  }

  private static String summarize(FinancialProfile profile, List<TriageRisk> risks) {
    String investor = "Perfil de investidor " + investorLabel(profile) + ". ";
    if (risks.isEmpty()) {
      return investor + "Nenhum ponto de atenção identificado.";
    }
    long high = count(risks, RiskSeverity.HIGH);
    long medium = count(risks, RiskSeverity.MEDIUM);
    long low = count(risks, RiskSeverity.LOW);
    return investor
        + "Identificamos %d %s de atenção (%d alta, %d média, %d baixa)."
            .formatted(risks.size(), risks.size() == 1 ? "ponto" : "pontos", high, medium, low);
  }

  private static String investorLabel(FinancialProfile profile) {
    return switch (profile) {
      case CONSERVATIVE -> "conservador";
      case MODERATE -> "moderado";
      case AGGRESSIVE -> "arrojado";
    };
  }

  private static long count(List<TriageRisk> risks, RiskSeverity severity) {
    return risks.stream().filter(r -> r.severity() == severity).count();
  }
}
