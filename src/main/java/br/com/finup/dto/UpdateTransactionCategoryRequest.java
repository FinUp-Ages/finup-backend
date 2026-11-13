package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Nova categoria de uma despesa existente")
public record UpdateTransactionCategoryRequest(
    @NotNull(message = "e obrigatorio") UUID categoryId) {}
