package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Categoria exibida na trilha de estudos do app. */
@Schema(description = "Categoria da trilha de estudos")
public record StudyTrackCategoryResponse(
    @Schema(description = "Identificador único da categoria") UUID id,
    @Schema(description = "Nome da categoria", example = "Reserva de Emergência") String name,
    @Schema(description = "Descrição da categoria", nullable = true) String description,
    @Schema(description = "Quantidade de perguntas com versão publicada", example = "3")
        long publishedCount) {}
