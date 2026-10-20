package br.com.finup.service;

import br.com.finup.dto.StudyTrackCategoryResponse;
import br.com.finup.dto.StudyTrackQuestionResponse;
import br.com.finup.exception.CategoryNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas de leitura da trilha de estudos. */
@Service
public class StudyTrackQueryService {

  @Transactional(readOnly = true)
  public List<StudyTrackCategoryResponse> findCategories() {
    return List.of();
  }

  @Transactional(readOnly = true)
  public List<StudyTrackQuestionResponse> findPublishedQuestions(UUID categoryId) {
    throw new CategoryNotFoundException("Categoria da trilha não encontrada: " + categoryId);
  }
}
