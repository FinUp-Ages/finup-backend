package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Categoria e contagem de perguntas publicadas disponíveis para o aluno. */
@Schema(description = "Categoria disponível na trilha de estudos")
public record StudyTrackCategoryResponse(
    @Schema(description = "Identificador único da categoria") UUID id,
    @Schema(description = "Nome da categoria", example = "Investimentos") String name,
    @Schema(description = "Descrição da categoria") String description,
    @Schema(description = "Quantidade de perguntas com conteúdo publicado") long publishedCount) {}
