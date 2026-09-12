package br.com.finup.dto;

import br.com.finup.model.CategoryType;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, CategoryType type, Boolean isDefault) {}
