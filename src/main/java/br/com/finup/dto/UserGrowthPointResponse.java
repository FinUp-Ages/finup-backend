package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Um mes da serie de crescimento de usuarios")
public record UserGrowthPointResponse(
    @Schema(description = "Mes no formato YYYY-MM, no fuso de Brasilia", example = "2026-05")
        String month,
    @Schema(
            description = "Usuarios cadastrados no mes; 0 quando ninguem se cadastrou",
            example = "12")
        long newUsers,
    @Schema(description = "Usuarios cadastrados ate o fim do mes, acumulado", example = "300")
        long totalUsers) {}
