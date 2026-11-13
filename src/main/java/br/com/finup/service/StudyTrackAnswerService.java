package br.com.finup.service;

import br.com.finup.dto.StudyTrackAnswersRequest;
import br.com.finup.dto.StudyTrackAnswersResponse;
import br.com.finup.dto.StudyTrackProgressResponse;
import br.com.finup.exception.InvalidStudyTrackAnswersException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyQuestion;
import br.com.finup.model.StudyTrackAnswer;
import br.com.finup.model.User;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import br.com.finup.repository.StudyTrackAnswerRepository;
import br.com.finup.repository.UserRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Respostas e progresso da trilha (Contrato C6). Cada pergunta publicada é um nível, na ordem de
 * {@code created_at} e {@code id}. As respostas podem chegar uma a uma ou em lote; o progresso só
 * considera as perguntas publicadas hoje.
 */
@Service
public class StudyTrackAnswerService {

  private final UserRepository userRepository;
  private final StudyCategoryRepository categoryRepository;
  private final StudyQuestionRepository questionRepository;
  private final StudyTrackAnswerRepository answerRepository;
  private final Clock clock;

  public StudyTrackAnswerService(
      UserRepository userRepository,
      StudyCategoryRepository categoryRepository,
      StudyQuestionRepository questionRepository,
      StudyTrackAnswerRepository answerRepository,
      Clock clock) {
    this.userRepository = userRepository;
    this.categoryRepository = categoryRepository;
    this.questionRepository = questionRepository;
    this.answerRepository = answerRepository;
    this.clock = clock;
  }

  /**
   * Grava as respostas enviadas, que podem ser só parte das perguntas publicadas da categoria. As
   * já respondidas são substituídas pela nova resposta. O resultado vale para todas as respostas
   * atuais da categoria, não só para o lote.
   */
  @Transactional
  public StudyTrackAnswersResponse submit(
      AuthenticatedIdentity identity, StudyTrackAnswersRequest request) {
    User user = findUser(identity);
    if (!categoryRepository.existsById(request.categoryId())) {
      throw new ResourceNotFoundException("Categoria da trilha não encontrada.");
    }

    List<StudyQuestion> questions =
        questionRepository.findByCategoryIdAndPublishedIsNotNullOrderByCreatedAtAscIdAsc(
            request.categoryId());
    Map<UUID, StudyTrackAnswersRequest.Answer> submitted = validateRequest(request, questions);
    Instant submittedAt = clock.instant();
    for (StudyQuestion question : questions) {
      StudyTrackAnswersRequest.Answer submittedAnswer = submitted.get(question.getId());
      if (submittedAnswer == null) {
        continue;
      }
      Boolean correct = correctAnswer(question, submittedAnswer.answer());
      Integer stars = levelStars(correct);
      answerRepository
          .findByUserIdAndQuestionId(user.getId(), question.getId())
          .ifPresentOrElse(
              saved -> saved.replace(submittedAnswer.answer(), correct, stars, submittedAt),
              () ->
                  answerRepository.save(
                      new StudyTrackAnswer(
                          user.getId(),
                          request.categoryId(),
                          question.getId(),
                          submittedAnswer.answer(),
                          correct,
                          stars,
                          submittedAt)));
    }
    answerRepository.flush();

    Set<UUID> publishedIds =
        questions.stream().map(StudyQuestion::getId).collect(Collectors.toSet());
    List<StudyTrackAnswer> current =
        answerRepository
            .findByUserIdAndCategoryIdOrderByQuestionId(user.getId(), request.categoryId())
            .stream()
            .filter(answer -> publishedIds.contains(answer.getQuestionId()))
            .toList();
    int correctCount = countCorrect(current);
    int gradableCount = countGradable(current);
    return new StudyTrackAnswersResponse(
        request.categoryId(),
        stars(correctCount, gradableCount),
        correctCount,
        gradableCount,
        current.stream()
            .map(
                answer ->
                    new StudyTrackAnswersResponse.Answer(
                        answer.getQuestionId(),
                        answer.getAnswer(),
                        answer.getCorrect(),
                        answer.getSubmittedAt()))
            .toList());
  }

  @Transactional(readOnly = true)
  public List<StudyTrackAnswersResponse> findAnswers(
      AuthenticatedIdentity identity, UUID categoryId) {
    User user = findUser(identity);
    List<StudyTrackAnswer> answers =
        categoryId == null
            ? answerRepository.findByUserIdOrderBySubmittedAtAsc(user.getId())
            : answerRepository.findByUserIdAndCategoryIdOrderByQuestionId(user.getId(), categoryId);
    Map<UUID, List<StudyTrackAnswer>> byCategory =
        answers.stream().collect(Collectors.groupingBy(StudyTrackAnswer::getCategoryId));
    return byCategory.entrySet().stream()
        .map(
            entry ->
                new StudyTrackAnswersResponse(
                    entry.getKey(),
                    stars(countCorrect(entry.getValue()), countGradable(entry.getValue())),
                    countCorrect(entry.getValue()),
                    countGradable(entry.getValue()),
                    entry.getValue().stream()
                        .map(
                            answer ->
                                new StudyTrackAnswersResponse.Answer(
                                    answer.getQuestionId(),
                                    answer.getAnswer(),
                                    answer.getCorrect(),
                                    answer.getSubmittedAt()))
                        .toList()))
        .toList();
  }

  @Transactional(readOnly = true)
  public StudyTrackProgressResponse findProgress(AuthenticatedIdentity identity) {
    User user = findUser(identity);
    Map<UUID, StudyTrackAnswer> answersByQuestion =
        answerRepository.findByUserIdOrderBySubmittedAtAsc(user.getId()).stream()
            .collect(
                Collectors.toMap(
                    StudyTrackAnswer::getQuestionId,
                    Function.identity(),
                    (first, latest) -> latest));
    Map<UUID, List<StudyQuestion>> questionsByCategory =
        questionRepository.findByPublishedIsNotNullOrderByCreatedAtAscIdAsc().stream()
            .collect(
                Collectors.groupingBy(
                    question -> question.getCategory().getId(),
                    LinkedHashMap::new,
                    Collectors.toList()));
    List<StudyTrackProgressResponse.CategoryProgress> categories =
        categoryRepository.findAllByOrderByNameAsc().stream()
            .map(
                category ->
                    categoryProgress(
                        category.getId(),
                        questionsByCategory.getOrDefault(category.getId(), List.of()),
                        answersByQuestion))
            .toList();
    int totalQuestions =
        categories.stream()
            .mapToInt(StudyTrackProgressResponse.CategoryProgress::totalQuestions)
            .sum();
    int answeredQuestions =
        categories.stream()
            .mapToInt(StudyTrackProgressResponse.CategoryProgress::answeredQuestions)
            .sum();
    int correctAnswers =
        categories.stream()
            .mapToInt(StudyTrackProgressResponse.CategoryProgress::correctAnswers)
            .sum();
    int gradableQuestions =
        categories.stream()
            .mapToInt(StudyTrackProgressResponse.CategoryProgress::gradableQuestions)
            .sum();
    return new StudyTrackProgressResponse(
        categories,
        new StudyTrackProgressResponse.Progress(
            totalQuestions,
            answeredQuestions,
            correctAnswers,
            gradableQuestions,
            stars(correctAnswers, gradableQuestions),
            percentage(correctAnswers, gradableQuestions),
            percentage(answeredQuestions, totalQuestions)));
  }

  /**
   * Monta os níveis da categoria. Concluído é ter resposta, certa ou errada; o nível atual é o
   * primeiro não concluído, então sem respostas o nível 1 continua alcançado.
   */
  private StudyTrackProgressResponse.CategoryProgress categoryProgress(
      UUID categoryId, List<StudyQuestion> questions, Map<UUID, StudyTrackAnswer> answers) {
    List<StudyTrackProgressResponse.LevelProgress> levels = new ArrayList<>();
    int answered = 0;
    int gradable = 0;
    int correct = 0;
    int firstPending = 0;
    for (int index = 0; index < questions.size(); index++) {
      StudyTrackAnswer answer = answers.get(questions.get(index).getId());
      boolean completed = answer != null;
      Boolean result = completed ? answer.getCorrect() : null;
      if (completed) {
        answered++;
        if (result != null) {
          gradable++;
          if (result) {
            correct++;
          }
        }
      } else if (firstPending == 0) {
        firstPending = index + 1;
      }
      levels.add(
          new StudyTrackProgressResponse.LevelProgress(
              index + 1, questions.get(index).getId(), completed, result, levelStars(result)));
    }
    int currentLevel = firstPending == 0 ? questions.size() : firstPending;
    return new StudyTrackProgressResponse.CategoryProgress(
        categoryId,
        questions.size(),
        answered,
        correct,
        gradable,
        stars(correct, gradable),
        percentage(correct, gradable),
        percentage(answered, questions.size()),
        currentLevel,
        levels);
  }

  /** Aceita qualquer subconjunto não vazio das perguntas publicadas da categoria, sem repetição. */
  private Map<UUID, StudyTrackAnswersRequest.Answer> validateRequest(
      StudyTrackAnswersRequest request, List<StudyQuestion> questions) {
    Map<UUID, StudyTrackAnswersRequest.Answer> submitted = new HashMap<>();
    for (StudyTrackAnswersRequest.Answer answer : request.answers()) {
      if (submitted.put(answer.questionId(), answer) != null) {
        throw new InvalidStudyTrackAnswersException("A pergunta foi enviada mais de uma vez.");
      }
    }
    Set<UUID> published = questions.stream().map(StudyQuestion::getId).collect(Collectors.toSet());
    if (!published.containsAll(submitted.keySet())) {
      throw new InvalidStudyTrackAnswersException(
          "O lote só pode conter perguntas publicadas da categoria.");
    }
    return submitted;
  }

  private Boolean correctAnswer(StudyQuestion question, String answer) {
    if (question.getType() == QuestionType.LEARNING) {
      return null;
    }
    String normalized = normalize(answer);
    Map<String, Object> published = question.getPublished();
    if (question.getType() == QuestionType.FILL_BLANK) {
      Object answers = published.get("answers");
      return answers instanceof List<?> accepted
          && accepted.stream()
              .anyMatch(value -> normalize(String.valueOf(value)).equals(normalized));
    }
    Object options = published.get("options");
    if (!(options instanceof List<?> choices)) {
      return false;
    }
    return choices.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .filter(option -> Boolean.TRUE.equals(option.get("correct")))
        .anyMatch(option -> normalize(String.valueOf(option.get("text"))).equals(normalized));
  }

  private User findUser(AuthenticatedIdentity identity) {
    return userRepository
        .findByCognitoId(identity.cognitoId())
        .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.strip().toLowerCase();
  }

  private static int countCorrect(List<StudyTrackAnswer> answers) {
    return (int)
        answers.stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect())).count();
  }

  private static int countGradable(List<StudyTrackAnswer> answers) {
    return (int) answers.stream().filter(answer -> answer.getCorrect() != null).count();
  }

  /** Estrelas de um nível: 3 no acerto, 1 no erro, sem estrelas quando não há nota. */
  private static Integer levelStars(Boolean correct) {
    if (correct == null) return null;
    return correct ? 3 : 1;
  }

  /** Estrelas de um conjunto: menos de 50% = 1, de 50% a menos de 80% = 2, a partir de 80% = 3. */
  private static Integer stars(int correct, int gradable) {
    if (gradable == 0) return null;
    double percentage = percentage(correct, gradable);
    return percentage < 50 ? 1 : percentage < 80 ? 2 : 3;
  }

  private static double percentage(int part, int whole) {
    return whole == 0 ? 0 : part * 100.0 / whole;
  }
}
