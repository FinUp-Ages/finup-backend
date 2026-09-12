package br.com.finup.mapper;

import br.com.finup.dto.CategoryRequest;
import br.com.finup.dto.CategoryResponse;
import br.com.finup.model.Category;
import br.com.finup.model.User;

public class CategoryMapper {

  public static Category toEntity(CategoryRequest request, User user) {
    Category category = new Category();
    category.setUser(user);
    category.setName(request.name());
    category.setType(request.type());
    category.setIsDefault(false);
    return category;
  }

  public static CategoryResponse toResponse(Category category) {
    return new CategoryResponse(
        category.getId(), category.getName(), category.getType(), category.getIsDefault());
  }
}
