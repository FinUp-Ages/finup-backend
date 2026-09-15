package br.com.finup.mapper;

import br.com.finup.dto.CategoryRequest;
import br.com.finup.dto.CategoryResponse;
import br.com.finup.model.Category;
import br.com.finup.model.User;

/** Converte entre a entidade {@link Category} e os DTOs de entrada e saída. */
public final class CategoryMapper {

  private CategoryMapper() {}

  public static Category toEntity(CategoryRequest request, User user) {
    return Category.createForUser(user, request.name(), request.type());
  }

  public static CategoryResponse toResponse(Category category) {
    return new CategoryResponse(
        category.getId(), category.getName(), category.getType(), category.isDefault());
  }
}
