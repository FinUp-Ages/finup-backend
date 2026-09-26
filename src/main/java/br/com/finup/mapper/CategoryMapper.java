package br.com.finup.mapper;

import br.com.finup.dto.CategoryResponse;
import br.com.finup.model.Category;

/**
 * Converte a entidade {@link Category} para o DTO de saída.
 *
 * <p>Não existe caminho inverso: quem cria a entidade é a própria fábrica {@link
 * Category#createForUser}, que garante as invariantes do domínio. Um {@code toEntity} aqui seria só
 * um desvio para contorná-la.
 */
public final class CategoryMapper {

  private CategoryMapper() {}

  public static CategoryResponse toResponse(Category category) {
    return new CategoryResponse(
        category.getId(), category.getName(), category.getType(), category.isDefault());
  }
}
