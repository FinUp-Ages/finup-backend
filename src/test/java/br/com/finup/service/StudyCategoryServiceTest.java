package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.StudyCategoryRequest;
import br.com.finup.dto.StudyCategoryResponse;
import br.com.finup.exception.ConflictException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.mapper.StudyCategoryMapper;
import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudyCategoryServiceTest {

  @Mock private StudyCategoryRepository categoryRepository;

  @Mock private StudyQuestionRepository questionRepository;

  @Spy private StudyCategoryMapper categoryMapper = new StudyCategoryMapper();

  @InjectMocks private StudyCategoryService categoryService;

  @Test
  @DisplayName("Deve listar categorias com contagens agregadas ordenadas por nome")
  void shouldListCategoriesWithCounts() {
    StudyCategory cat1 = new StudyCategory(UUID.randomUUID(), "Categoria A", "Desc A");
    StudyCategory cat2 = new StudyCategory(UUID.randomUUID(), "Categoria B", "Desc B");

    StudyQuestion q1 =
        new StudyQuestion(
            cat1, QuestionType.FILL_BLANK, Map.of("statement", "Draft q1"), null, null);

    StudyQuestion q2 =
        new StudyQuestion(
            cat1,
            QuestionType.MULTIPLE_CHOICE,
            Map.of("statement", "Pub q2"),
            Map.of("statement", "Pub q2"),
            Instant.now());

    when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(cat1, cat2));
    when(questionRepository.findByCategoryId(cat1.getId())).thenReturn(List.of(q1, q2));
    when(questionRepository.findByCategoryId(cat2.getId())).thenReturn(List.of());

    List<StudyCategoryResponse> result = categoryService.listCategories();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).name()).isEqualTo("Categoria A");
    assertThat(result.get(0).draftCount()).isEqualTo(1);
    assertThat(result.get(0).publishedCount()).isEqualTo(1);

    assertThat(result.get(1).name()).isEqualTo("Categoria B");
    assertThat(result.get(1).draftCount()).isZero();
    assertThat(result.get(1).publishedCount()).isZero();

    verify(categoryRepository).findAllByOrderByNameAsc();
    verify(questionRepository).findByCategoryId(cat1.getId());
    verify(questionRepository).findByCategoryId(cat2.getId());
  }

  @Test
  @DisplayName("Deve buscar categoria por ID com suas contagens agregadas")
  void shouldGetCategoryByIdWithCounts() {
    UUID id = UUID.randomUUID();
    StudyCategory cat = new StudyCategory(id, "Reserva de Emergência", "Liquidez diária");

    StudyQuestion q =
        new StudyQuestion(
            cat,
            QuestionType.LEARNING,
            Map.of("title", "Novo rascunho"),
            Map.of("title", "Versão publicada"),
            Instant.now());

    when(categoryRepository.findById(id)).thenReturn(Optional.of(cat));
    when(questionRepository.findByCategoryId(id)).thenReturn(List.of(q));

    StudyCategoryResponse response = categoryService.getCategoryById(id);

    assertThat(response.id()).isEqualTo(id);
    assertThat(response.name()).isEqualTo("Reserva de Emergência");
    // PUBLISHED_WITH_CHANGES conta tanto em published quanto em draft
    assertThat(response.publishedCount()).isEqualTo(1);
    assertThat(response.draftCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("Deve lançar ResourceNotFoundException quando categoria não existir")
  void shouldThrowWhenCategoryNotFound() {
    UUID id = UUID.randomUUID();
    when(categoryRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> categoryService.getCategoryById(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Categoria da trilha não encontrada");
  }

  @Test
  @DisplayName("Deve criar categoria com nome e descrição")
  void shouldCreateCategory() {
    StudyCategory category = new StudyCategory("CDB", "Renda fixa");
    when(categoryRepository.save(org.mockito.ArgumentMatchers.any(StudyCategory.class)))
        .thenReturn(category);
    when(questionRepository.findByCategoryId(category.getId())).thenReturn(List.of());

    StudyCategoryResponse response =
        categoryService.create(new StudyCategoryRequest(" CDB ", " Renda fixa "));

    assertThat(response.name()).isEqualTo("CDB");
    assertThat(response.description()).isEqualTo("Renda fixa");
  }

  @Test
  @DisplayName("Deve atualizar categoria existente")
  void shouldUpdateCategory() {
    UUID id = UUID.randomUUID();
    StudyCategory category = new StudyCategory(id, "CDB", null);
    when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
    when(categoryRepository.save(category)).thenReturn(category);
    when(questionRepository.findByCategoryId(id)).thenReturn(List.of());

    StudyCategoryResponse response =
        categoryService.update(id, new StudyCategoryRequest("Tesouro", null));

    assertThat(response.name()).isEqualTo("Tesouro");
    assertThat(response.description()).isEmpty();
  }

  @Test
  @DisplayName("Não deve remover categoria que possui perguntas")
  void shouldRejectDeleteWhenCategoryHasQuestions() {
    UUID id = UUID.randomUUID();
    when(categoryRepository.findById(id))
        .thenReturn(Optional.of(new StudyCategory(id, "CDB", null)));
    when(questionRepository.existsByCategoryId(id)).thenReturn(true);

    assertThatThrownBy(() -> categoryService.delete(id))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("A categoria possui perguntas");
  }

  @Test
  @DisplayName("Deve remover categoria sem perguntas")
  void shouldDeleteCategoryWithoutQuestions() {
    UUID id = UUID.randomUUID();
    when(categoryRepository.findById(id))
        .thenReturn(Optional.of(new StudyCategory(id, "CDB", null)));
    when(questionRepository.existsByCategoryId(id)).thenReturn(false);

    categoryService.delete(id);

    verify(categoryRepository).deleteById(id);
    verify(categoryRepository).flush();
  }
}
