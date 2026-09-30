package br.com.finup.dto;

import br.com.finup.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Categoria disponível para seleção no formulário de transação. */
@Schema(description = "Categoria disponível para uma transação")
public record CategoryListResponse(
    @Schema(description = "Identificador único da categoria") UUID id,
    @Schema(description = "Nome da categoria", example = "Alimentação") String name,
    @Schema(description = "Tipo da categoria", example = "EXPENSE") TransactionType type) {}
