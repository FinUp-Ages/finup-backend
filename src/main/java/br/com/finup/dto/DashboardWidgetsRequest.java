package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Configuração completa dos widgets do dashboard; substitui a anterior")
public record DashboardWidgetsRequest(
    @Schema(description = "Widgets configurados, na ordem desejada")
        @NotNull(message = "nao pode ser nulo")
        @Size(max = 50, message = "nao pode ter mais de 50 itens")
        List<@NotNull(message = "nao pode ter item nulo") @Valid DashboardWidgetItem> widgets) {}
