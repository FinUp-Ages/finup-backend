package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Crescimento mensal da base de usuarios")
public record UserGrowthResponse(
    @Schema(
            description =
                "Crescimento do total de usuarios ao longo da janela, em %, com 1 casa decimal:"
                    + " do total antes do primeiro mes ate o total do ultimo mes. 0.0 quando nao"
                    + " havia usuarios antes da janela",
            example = "278.0")
        double growthPct,
    @Schema(description = "Um ponto por mes, do mais antigo ao mais recente, sem pular meses")
        List<UserGrowthPointResponse> points) {}
