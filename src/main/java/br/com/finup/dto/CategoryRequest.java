package br.com.finup.dto;

import br.com.finup.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para criação ou atualização de uma categoria")
public record CategoryRequest(
    @Schema(description = "Nome da categoria", example = "Alimentação")
        @NotBlank(message = "nao pode estar em branco")
        @Size(max = 255, message = "nao pode ter mais de 255 caracteres")
        String name,
    @Schema(description = "Tipo da categoria") @NotNull(message = "nao pode ser nulo")
        TransactionType type) {}
