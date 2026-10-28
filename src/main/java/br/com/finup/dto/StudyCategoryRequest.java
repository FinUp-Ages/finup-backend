package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para criação ou atualização de uma categoria da trilha")
public record StudyCategoryRequest(
    @Schema(description = "Nome da categoria", example = "CDB")
        @NotBlank(message = "nao pode estar em branco")
        @Size(max = 100, message = "nao pode ter mais de 100 caracteres")
        String name,
    @Schema(description = "Descrição da categoria", example = "Conteúdos sobre CDB e renda fixa")
        @Size(max = 255, message = "nao pode ter mais de 255 caracteres")
        String description) {}
