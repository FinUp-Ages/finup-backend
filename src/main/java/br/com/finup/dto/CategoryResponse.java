package br.com.finup.dto;

import br.com.finup.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Categoria retornada pela API")
public record CategoryResponse(
    @Schema(description = "Identificador único da categoria") UUID id,
    @Schema(description = "Nome da categoria", example = "Alimentação") String name,
    @Schema(description = "Tipo da categoria") TransactionType type,
    @Schema(description = "Indica se é uma categoria padrão do sistema") boolean isDefault) {}
