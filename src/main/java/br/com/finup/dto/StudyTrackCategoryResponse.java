package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Categoria exibida na trilha de estudos. */
@Schema(description = "Categoria da trilha de estudos")
public record StudyTrackCategoryResponse(
    @Schema(description = "Identificador único da categoria") UUID id,
    @Schema(description = "Nome da categoria", example = "Investimentos") String name) {}
