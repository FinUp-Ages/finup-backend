package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Dado insuficiente nao e erro HTTP: sai como 200 com {@code status = INSUFFICIENT_DATA}. */
@Schema(description = "Resultado do recalculo do FinUp Score")
public record FinUpScoreResponse(
    @Schema(description = "Identificador do usuario") UUID userId,
    @Schema(
            description =
                "COMPUTED quando o score foi calculado e persistido, "
                    + "INSUFFICIENT_DATA quando faltou dado obrigatorio",
            example = "COMPUTED")
        String status,
    @Schema(description = "Score de 0 a 1000; nulo quando status e INSUFFICIENT_DATA")
        Integer score,
    @Schema(description = "Detalhe legivel do resultado") String message) {}
