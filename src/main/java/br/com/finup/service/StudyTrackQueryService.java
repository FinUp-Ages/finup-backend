package br.com.finup.service;

import br.com.finup.dto.StudyTrackCategoryResponse;
import br.com.finup.dto.StudyTrackQuestionResponse;
import br.com.finup.exception.CategoryNotFoundException;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas de leitura da trilha de estudos. */
@Service
public class StudyTrackQueryService {

  private final StudyCategoryService studyCategoryService;
  private final StudyCategoryRepository studyCategoryRepository;
  private final StudyQuestionRepository studyQuestionRepository;

  public StudyTrackQueryService(
      StudyCategoryService studyCategoryService,
      StudyCategoryRepository studyCategoryRepository,
      StudyQuestionRepository studyQuestionRepository) {
    this.studyCategoryService = studyCategoryService;
    this.studyCategoryRepository = studyCategoryRepository;
    this.studyQuestionRepository = studyQuestionRepository;
  }

  @Transactional(readOnly = true)
  public List<StudyTrackCategoryResponse> findCategories() {
    return studyCategoryService.listCategories().stream()
        .map(
            category ->
                new StudyTrackCategoryResponse(
                    category.id(),
                    category.name(),
                    category.description(),
                    category.publishedCount()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<StudyTrackQuestionResponse> findPublishedQuestions(UUID categoryId) {
    if (!studyCategoryRepository.existsById(categoryId)) {
      throw new CategoryNotFoundException("Categoria da trilha não encontrada: " + categoryId);
    }

    return studyQuestionRepository
        .findByCategoryIdAndPublishedIsNotNullOrderByCreatedAtAsc(categoryId)
        .stream()
        .map(
            question ->
                new StudyTrackQuestionResponse(
                    question.getId(),
                    question.getType(),
                    question.getPublished(),
                    question.getPublishedAt()))
        .toList();
  }
}
