package br.com.finup.service;

import br.com.finup.dto.StudyTrackCategoryResponse;
import br.com.finup.dto.StudyTrackQuestionResponse;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import br.com.finup.repository.StudyQuestionRepository.PublishedCountProjection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de leitura da trilha de estudos para o app (Contrato C6).
 *
 * <p>Só expõe o conteúdo {@code published}: perguntas em {@code DRAFT} não aparecem e as em {@code
 * PUBLISHED_WITH_CHANGES} mostram a versão que está no ar, nunca o rascunho.
 */
@Service
public class StudyTrackQueryService {

  private final StudyCategoryRepository categoryRepository;
  private final StudyQuestionRepository questionRepository;

  public StudyTrackQueryService(
      StudyCategoryRepository categoryRepository, StudyQuestionRepository questionRepository) {
    this.categoryRepository = categoryRepository;
    this.questionRepository = questionRepository;
  }

  @Transactional(readOnly = true)
  public List<StudyTrackCategoryResponse> findCategories() {
    Map<UUID, Long> publishedCounts =
        questionRepository.countPublishedGroupedByCategory().stream()
            .collect(
                Collectors.toMap(
                    PublishedCountProjection::getCategoryId,
                    PublishedCountProjection::getPublishedCount));
    return categoryRepository.findAllByOrderByNameAsc().stream()
        .map(
            category ->
                new StudyTrackCategoryResponse(
                    category.getId(),
                    category.getName(),
                    category.getDescription(),
                    publishedCounts.getOrDefault(category.getId(), 0L)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<StudyTrackQuestionResponse> findPublishedQuestions(UUID categoryId) {
    if (!categoryRepository.existsById(categoryId)) {
      throw new ResourceNotFoundException("Categoria da trilha não encontrada: " + categoryId);
    }
    return questionRepository
        .findByCategoryIdAndPublishedIsNotNullOrderByCreatedAtAsc(categoryId)
        .stream()
        .map(question -> new StudyTrackQuestionResponse(question.getId(), question.getPublished()))
        .toList();
  }
}
