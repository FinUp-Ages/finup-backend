package br.com.finup.dto;

import java.util.UUID;

import br.com.finup.model.CategoryType;

public record CategoryResponse(
        UUID id,
        String name,
        CategoryType type,
        Boolean isDefault) {
}
