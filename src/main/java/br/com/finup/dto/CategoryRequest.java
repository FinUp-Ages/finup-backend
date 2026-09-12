package br.com.finup.dto;

import br.com.finup.model.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryRequest(@NotBlank String name, @NotNull CategoryType type) {}
