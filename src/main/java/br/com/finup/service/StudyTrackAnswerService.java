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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  @Transactional
  public StudyTrackAnswersResponse submit(
      AuthenticatedIdentity identity, StudyTrackAnswersRequest request) {
    User user = findUser(identity);
    if (!categoryRepository.existsById(request.categoryId())) {
      throw new ResourceNotFoundException("Categoria da trilha não encontrada.");
    }

    List<StudyQuestion> questions =
        questionRepository.findByCategoryIdAndPublishedIsNotNullOrderByCreatedAtAsc(
            request.categoryId());
    Map<UUID, StudyTrackAnswersRequest.Answer> submitted = validateRequest(request, questions);
    int gradableCount = 0;
    int correctCount = 0;
    Map<UUID, Boolean> results = new HashMap<>();
    for (StudyQuestion question : questions) {
      Boolean correct = correctAnswer(question, submitted.get(question.getId()).answer());
      results.put(question.getId(), correct);
      if (correct != null) {
        gradableCount++;
        if (correct) {
          correctCount++;
        }
      }
    }
    Integer stars = stars(correctCount, gradableCount);
    Instant submittedAt = clock.instant();
    for (StudyQuestion question : questions) {
      StudyTrackAnswersRequest.Answer submittedAnswer = submitted.get(question.getId());
      answerRepository
          .findByUserIdAndQuestionId(user.getId(), question.getId())
          .ifPresentOrElse(
              saved ->
                  saved.replace(
                      submittedAnswer.answer(), results.get(question.getId()), stars, submittedAt),
              () ->
                  answerRepository.save(
                      new StudyTrackAnswer(
                          user.getId(),
                          request.categoryId(),
                          question.getId(),
                          submittedAnswer.answer(),
                          results.get(question.getId()),
                          stars,
                          submittedAt)));
    }
    answerRepository.flush();
    return new StudyTrackAnswersResponse(
        request.categoryId(),
        stars,
        correctCount,
        gradableCount,
        answerRepository
            .findByUserIdAndCategoryIdOrderByQuestionId(user.getId(), request.categoryId())
            .stream()
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
        answers.stream()
            .collect(java.util.stream.Collectors.groupingBy(StudyTrackAnswer::getCategoryId));
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
    List<StudyTrackAnswer> answers =
        answerRepository.findByUserIdOrderBySubmittedAtAsc(user.getId());
    List<StudyTrackProgressResponse.CategoryProgress> categories =
        categoryRepository.findAllByOrderByNameAsc().stream()
            .map(
                category -> {
                  List<StudyQuestion> questions =
                      questionRepository.findByCategoryIdAndPublishedIsNotNullOrderByCreatedAtAsc(
                          category.getId());
                  List<StudyTrackAnswer> categoryAnswers =
                      answers.stream()
                          .filter(answer -> answer.getCategoryId().equals(category.getId()))
                          .toList();
                  return categoryProgress(category.getId(), questions.size(), categoryAnswers);
                })
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
        categories, progress(totalQuestions, answeredQuestions, correctAnswers, gradableQuestions));
  }

  private StudyTrackProgressResponse.CategoryProgress categoryProgress(
      UUID categoryId, int totalQuestions, List<StudyTrackAnswer> answers) {
    int gradable = countGradable(answers);
    int correct = countCorrect(answers);
    return new StudyTrackProgressResponse.CategoryProgress(
        categoryId,
        totalQuestions,
        answers.size(),
        correct,
        gradable,
        stars(correct, gradable),
        percentage(correct, gradable));
  }

  private StudyTrackProgressResponse.Progress progress(
      int totalQuestions, int answered, int correct, int gradable) {
    return new StudyTrackProgressResponse.Progress(
        totalQuestions,
        answered,
        correct,
        gradable,
        stars(correct, gradable),
        percentage(correct, gradable));
  }

  private Map<UUID, StudyTrackAnswersRequest.Answer> validateRequest(
      StudyTrackAnswersRequest request, List<StudyQuestion> questions) {
    Map<UUID, StudyTrackAnswersRequest.Answer> submitted = new HashMap<>();
    for (StudyTrackAnswersRequest.Answer answer : request.answers()) {
      if (submitted.put(answer.questionId(), answer) != null) {
        throw new InvalidStudyTrackAnswersException("A pergunta foi enviada mais de uma vez.");
      }
    }
    Set<UUID> expected =
        questions.stream().map(StudyQuestion::getId).collect(java.util.stream.Collectors.toSet());
    if (!submitted.keySet().equals(expected)) {
      throw new InvalidStudyTrackAnswersException(
          "O lote deve conter exatamente todas as perguntas publicadas da categoria.");
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

  private static Integer stars(int correct, int gradable) {
    if (gradable == 0) return null;
    double percentage = percentage(correct, gradable);
    return percentage < 50 ? 1 : percentage < 80 ? 2 : 3;
  }

  private static double percentage(int correct, int gradable) {
    return gradable == 0 ? 0 : correct * 100.0 / gradable;
  }
}
