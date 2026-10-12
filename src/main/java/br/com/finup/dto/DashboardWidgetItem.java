package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Um widget do dashboard na requisição e na resposta de {@code /admin/dashboard/widgets}. */
@Schema(description = "Configuração de um widget do dashboard")
public record DashboardWidgetItem(
    @Schema(description = "Identificador do widget", example = "NEW_CLIENTS")
        @NotBlank(message = "nao pode estar em branco")
        @Size(max = 100, message = "nao pode ter mais de 100 caracteres")
        String id,
    @Schema(description = "Se o widget aparece no dashboard", example = "true")
        @NotNull(message = "nao pode ser nulo")
        Boolean enabled,
    @Schema(description = "Posição do widget, começando em 0", example = "0")
        @NotNull(message = "nao pode ser nulo")
        @Min(value = 0, message = "deve ser maior ou igual a 0")
        Integer position) {}
