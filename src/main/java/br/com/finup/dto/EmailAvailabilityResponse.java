package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado da consulta de e-mail. E so um aviso para o formulario: nao reserva o e-mail, entao o
 * cadastro ainda pode falhar com 409 se outra pessoa o usar entre a consulta e o {@code POST
 * /api/v1/users}.
 */
@Schema(description = "Resultado da consulta de disponibilidade de e-mail")
public record EmailAvailabilityResponse(
    @Schema(
            description =
                "true quando nenhum usuario usa o e-mail; false quando ele ja esta cadastrado")
        boolean available) {}
