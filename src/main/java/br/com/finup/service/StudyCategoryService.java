package br.com.finup.service;

import br.com.finup.dto.StudyCategoryRequest;
import br.com.finup.dto.StudyCategoryResponse;
import br.com.finup.exception.CategoryNotFoundException;
import br.com.finup.exception.ConflictException;
import br.com.finup.mapper.StudyCategoryMapper;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serviço de gerenciamento de categorias da trilha de estudos (Contrato C6). */
@Service
public class StudyCategoryService {

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

  @Transactional(readOnly = true)
  public List<StudyCategoryResponse> listCategories() {
    return categoryRepository.findAllByOrderByNameAsc().stream()
        .map(this::mapWithQuestionCounts)
        .toList();
  }

  @Transactional(readOnly = true)
  public StudyCategoryResponse getCategoryById(UUID id) {
    StudyCategory category =
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new CategoryNotFoundException("Categoria da trilha não encontrada"));
    return mapWithQuestionCounts(category);
  }

  @Transactional
  public StudyCategoryResponse create(StudyCategoryRequest request) {
    StudyCategory category = new StudyCategory(request.name(), request.description());
    StudyCategory saved = categoryRepository.save(category);
    return mapWithQuestionCounts(saved);
  }

  @Transactional
  public StudyCategoryResponse update(UUID id, StudyCategoryRequest request) {
    StudyCategory category = findByIdOrThrow(id);
    category.setName(request.name());
    category.setDescription(request.description());
    return mapWithQuestionCounts(categoryRepository.save(category));
  }

  @Transactional
  public void delete(UUID id) {
    findByIdOrThrow(id);
    if (questionRepository.existsByCategoryId(id)) {
      throw new ConflictException(
          "A categoria possui perguntas e não pode ser removida. Remova as perguntas antes.");
    }
    categoryRepository.deleteById(id);
    categoryRepository.flush();
  }

  private StudyCategoryResponse mapWithQuestionCounts(StudyCategory category) {
    StudyQuestionRepository.CategoryCountProjection counts =
        questionRepository.countAggregatedByCategoryId(category.getId());
    if (counts != null) {
      return categoryMapper.toResponse(
          category, counts.getDraftCount(), counts.getPublishedCount());
    }
    List<StudyQuestion> questions = questionRepository.findByCategoryId(category.getId());
    return categoryMapper.toResponse(category, questions);
  }

  private StudyCategory findByIdOrThrow(UUID id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(() -> new CategoryNotFoundException("Categoria da trilha não encontrada"));
  }
}
