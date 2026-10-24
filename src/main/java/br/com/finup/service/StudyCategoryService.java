package br.com.finup.service;

import br.com.finup.dto.StudyCategoryResponse;
import br.com.finup.exception.CategoryNotFoundException;
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

  private StudyCategoryResponse mapWithQuestionCounts(StudyCategory category) {
    List<StudyQuestion> questions = questionRepository.findByCategoryId(category.getId());
    return categoryMapper.toResponse(category, questions);
  }
}
