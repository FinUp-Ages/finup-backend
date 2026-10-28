package br.com.finup.service;

import br.com.finup.dto.StudyQuestionRequest;
import br.com.finup.dto.StudyQuestionResponse;
import br.com.finup.exception.InvalidStudyQuestionException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.mapper.StudyQuestionMapper;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operações administrativas de perguntas. A autorização é feita pelo SecurityConfig. */
@Service
public class StudyQuestionService {

  private final StudyCategoryRepository categoryRepository;
  private final StudyQuestionRepository questionRepository;
  private final StudyQuestionValidator validator;

  public StudyQuestionService(
      StudyCategoryRepository categoryRepository,
      StudyQuestionRepository questionRepository,
      StudyQuestionValidator validator) {
    this.categoryRepository = categoryRepository;
    this.questionRepository = questionRepository;
    this.validator = validator;
  }

  @Transactional
  public StudyQuestionResponse create(UUID categoryId, StudyQuestionRequest request) {
    StudyCategory category =
        categoryRepository
            .findById(categoryId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Categoria da trilha não encontrada."));
    validator.validate(request);
    StudyQuestion question =
        new StudyQuestion(
            category, request.type(), StudyQuestionMapper.toDraft(request), null, null);
    return StudyQuestionMapper.toResponse(questionRepository.saveAndFlush(question));
  }

  @Transactional(readOnly = true)
  public List<StudyQuestionResponse> findByCategory(UUID categoryId) {
    if (!categoryRepository.existsById(categoryId)) {
      throw new ResourceNotFoundException("Categoria da trilha não encontrada.");
    }
    return questionRepository.findByCategoryIdOrderByCreatedAtAsc(categoryId).stream()
        .map(StudyQuestionMapper::toResponse)
        .toList();
  }

  @Transactional
  public StudyQuestionResponse update(UUID questionId, StudyQuestionRequest request) {
    StudyQuestion question = findQuestion(questionId);
    validator.validate(request);
    if (request.type() != question.getType()) {
      throw new InvalidStudyQuestionException("O tipo da pergunta não pode ser alterado.");
    }
    // Substitui o mapa inteiro: nunca modifica o mapa publicado, mesmo se os conteúdos eram iguais.
    question.setDraft(StudyQuestionMapper.toDraft(request));
    // O flush executa o @PreUpdate antes de montar a resposta com updatedAt.
    return StudyQuestionMapper.toResponse(questionRepository.saveAndFlush(question));
  }

  @Transactional
  public void delete(UUID questionId) {
    questionRepository.delete(findQuestion(questionId));
    questionRepository.flush();
  }

  private StudyQuestion findQuestion(UUID questionId) {
    return questionRepository
        .findById(questionId)
        .orElseThrow(() -> new ResourceNotFoundException("Pergunta da trilha não encontrada."));
  }
}
