package br.com.finup.service;

import br.com.finup.dto.StudyCategoryRequest;
import br.com.finup.dto.StudyCategoryResponse;
import br.com.finup.exception.ConflictException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.mapper.StudyCategoryMapper;
import br.com.finup.model.StudyCategory;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import br.com.finup.repository.StudyQuestionRepository.CategoryCountProjection;
import br.com.finup.repository.StudyQuestionRepository.CategoryIdCountProjection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serviço de gerenciamento de categorias da trilha de estudos (Contrato C6). */
@Service
public class StudyCategoryService {

  private static final String CATEGORY_HAS_QUESTIONS =
      "A categoria possui perguntas e não pode ser removida. Remova as perguntas antes.";

  private final StudyCategoryRepository categoryRepository;
  private final StudyQuestionRepository questionRepository;
  private final StudyCategoryMapper categoryMapper;

  public StudyCategoryService(
      StudyCategoryRepository categoryRepository,
      StudyQuestionRepository questionRepository,
      StudyCategoryMapper categoryMapper) {
    this.categoryRepository = categoryRepository;
    this.questionRepository = questionRepository;
    this.categoryMapper = categoryMapper;
  }

  /** Lista as categorias com as contagens calculadas numa única consulta agrupada, sem N+1. */
  @Transactional(readOnly = true)
  public List<StudyCategoryResponse> listCategories() {
    Map<UUID, CategoryIdCountProjection> counts =
        questionRepository.countAggregatedGroupedByCategory().stream()
            .collect(
                Collectors.toMap(CategoryIdCountProjection::getCategoryId, Function.identity()));
    return categoryRepository.findAllByOrderByNameAsc().stream()
        .map(
            category -> {
              CategoryCountProjection count = counts.get(category.getId());
              return count == null
                  ? categoryMapper.toResponse(category, 0, 0)
                  : categoryMapper.toResponse(
                      category, count.getDraftCount(), count.getPublishedCount());
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public StudyCategoryResponse getCategoryById(UUID id) {
    return mapWithQuestionCounts(findByIdOrThrow(id));
  }

  /** Categoria recém-criada ainda não tem perguntas, então as contagens são zero. */
  @Transactional
  public StudyCategoryResponse create(StudyCategoryRequest request) {
    StudyCategory category = new StudyCategory(request.name(), request.description());
    return categoryMapper.toResponse(categoryRepository.save(category), 0, 0);
  }

  @Transactional
  public StudyCategoryResponse update(UUID id, StudyCategoryRequest request) {
    StudyCategory category = findByIdOrThrow(id);
    category.setName(request.name());
    category.setDescription(request.description());
    return mapWithQuestionCounts(categoryRepository.save(category));
  }

  /**
   * Remove a categoria sem perguntas. A FK de {@code study_questions} não tem {@code ON DELETE}: se
   * uma pergunta for criada entre a verificação e a remoção, o banco recusa e a resposta também é
   * 409.
   */
  @Transactional
  public void delete(UUID id) {
    findByIdOrThrow(id);
    if (questionRepository.existsByCategoryId(id)) {
      throw new ConflictException(CATEGORY_HAS_QUESTIONS);
    }
    try {
      categoryRepository.deleteById(id);
      categoryRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(CATEGORY_HAS_QUESTIONS);
    }
  }

  private StudyCategoryResponse mapWithQuestionCounts(StudyCategory category) {
    CategoryCountProjection counts =
        questionRepository.countAggregatedByCategoryId(category.getId());
    return categoryMapper.toResponse(category, counts.getDraftCount(), counts.getPublishedCount());
  }

  private StudyCategory findByIdOrThrow(UUID id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Categoria da trilha não encontrada"));
  }
}
