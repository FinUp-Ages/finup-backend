package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Configuração atual dos widgets do dashboard")
public record DashboardWidgetsResponse(
    @Schema(description = "Widgets configurados, ordenados por position")
        List<DashboardWidgetItem> widgets) {}
