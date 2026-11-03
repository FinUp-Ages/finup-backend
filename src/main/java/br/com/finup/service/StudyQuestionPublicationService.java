package br.com.finup.service;

import br.com.finup.dto.StudyQuestionRequest;
import br.com.finup.dto.StudyQuestionResponse;
import br.com.finup.exception.ConflictException;
import br.com.finup.exception.InvalidStudyQuestionException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.mapper.StudyQuestionMapper;
import br.com.finup.model.QuestionStatus;
import br.com.finup.model.StudyQuestion;
import br.com.finup.repository.StudyQuestionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicação, despublicação e descarte de edições de uma pergunta da trilha (Contrato C6). A
 * autorização é feita pelo SecurityConfig.
 *
 * <p>O status nunca é gravado: depois de cada ação ele sai da regra da entidade, que compara {@code
 * draft} e {@code published}. Por isso as contagens de rascunho e publicadas da categoria
 * acompanham sem nenhum código a mais.
 *
 * <p>Cada ação trava a linha da pergunta ({@code SELECT ... FOR UPDATE}). Sem isso, uma publicação
 * e um descarte simultâneos leriam o mesmo estado e o último a gravar desfaria o outro.
 *
 * <p>As ações são idempotentes quando não há o que fazer, e nesses casos nada é gravado (nem {@code
 * updatedAt} nem {@code publishedAt}): publicar o que já está publicado sem alterações, despublicar
 * o que nunca foi publicado e descartar quando não há edição pendente.
 */
@Service
public class StudyQuestionPublicationService {

  private static final TypeReference<Map<String, Object>> CONTENT = new TypeReference<>() {};

  private final EntityManager entityManager;
  private final StudyQuestionRepository questionRepository;
  private final StudyQuestionValidator validator;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public StudyQuestionPublicationService(
      EntityManager entityManager,
      StudyQuestionRepository questionRepository,
      StudyQuestionValidator validator,
      ObjectMapper objectMapper,
      Clock clock) {
    this.entityManager = entityManager;
    this.questionRepository = questionRepository;
    this.validator = validator;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /**
   * Valida o rascunho e o copia para {@code published}, gravando o instante em {@code publishedAt}.
   * Rascunho inválido não publica nada. O instante é truncado para microssegundos, a precisão da
   * coluna: assim a resposta traz exatamente o valor que as leituras seguintes vão devolver.
   *
   * @throws InvalidStudyQuestionException se o rascunho não passa nas regras do tipo (400)
   */
  @Transactional
  public StudyQuestionResponse publish(UUID questionId) {
    StudyQuestion question = findForUpdate(questionId);
    if (question.getStatus() == QuestionStatus.PUBLISHED) {
      return StudyQuestionMapper.toResponse(question);
    }
    validateDraft(question);
    question.setPublished(copyOf(question.getDraft()));
    question.setPublishedAt(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
    return saveAndRespond(question);
  }

  /**
   * Remove a versão publicada, e a pergunta volta a {@code DRAFT}. O rascunho fica como está,
   * inclusive com edições pendentes, para o autor poder republicar.
   */
  @Transactional
  public StudyQuestionResponse unpublish(UUID questionId) {
    StudyQuestion question = findForUpdate(questionId);
    if (question.getPublished() == null) {
      return StudyQuestionMapper.toResponse(question);
    }
    question.setPublished(null);
    question.setPublishedAt(null);
    return saveAndRespond(question);
  }

  /**
   * Descarta as edições pendentes, restaurando o rascunho a partir da versão publicada.
   *
   * @throws ConflictException se a pergunta nunca foi publicada (409): não há o que restaurar
   */
  @Transactional
  public StudyQuestionResponse discardDraft(UUID questionId) {
    StudyQuestion question = findForUpdate(questionId);
    if (question.getPublished() == null) {
      throw new ConflictException(
          "A pergunta não tem versão publicada, então não há edição a descartar.");
    }
    if (question.getStatus() == QuestionStatus.PUBLISHED) {
      return StudyQuestionMapper.toResponse(question);
    }
    question.setDraft(copyOf(question.getPublished()));
    return saveAndRespond(question);
  }

  private StudyQuestion findForUpdate(UUID questionId) {
    StudyQuestion question =
        entityManager.find(StudyQuestion.class, questionId, LockModeType.PESSIMISTIC_WRITE);
    if (question == null) {
      throw new ResourceNotFoundException("Pergunta da trilha não encontrada.");
    }
    return question;
  }

  /**
   * Aplica ao conteúdo já gravado as mesmas regras do POST e do PUT. O rascunho vem do banco como
   * JSON e pode estar em um formato antigo, então a conversão também pode falhar.
   */
  private void validateDraft(StudyQuestion question) {
    StudyQuestionRequest content;
    try {
      content = objectMapper.convertValue(question.getDraft(), StudyQuestionRequest.class);
    } catch (IllegalArgumentException e) {
      throw new InvalidStudyQuestionException(
          "O rascunho está em um formato inválido e não foi publicado.");
    }
    try {
      validator.validate(content);
    } catch (InvalidStudyQuestionException e) {
      throw new InvalidStudyQuestionException(
          "Rascunho inválido, não foi publicado: " + e.getMessage());
    }
    if (content.type() != question.getType()) {
      throw new InvalidStudyQuestionException(
          "O tipo do rascunho difere do tipo da pergunta, e a pergunta não foi publicada.");
    }
  }

  /** Cópia profunda: {@code draft} e {@code published} nunca compartilham listas nem mapas. */
  private Map<String, Object> copyOf(Map<String, Object> content) {
    return objectMapper.convertValue(content, CONTENT);
  }

  /** O flush roda o {@code @PreUpdate} antes de montar a resposta, para ela trazer o updatedAt. */
  private StudyQuestionResponse saveAndRespond(StudyQuestion question) {
    return StudyQuestionMapper.toResponse(questionRepository.saveAndFlush(question));
  }
}
