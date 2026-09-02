package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * O que a API devolve sobre um usuario.
 *
 * <p>Existe separado da entidade de proposito: assim um campo novo no dominio nao vaza para o
 * contrato sem alguem decidir. Nunca devolva {@code Usuario} direto de um controller.
 */
@Schema(description = "Usuario cadastrado")
public record UsuarioResponse(
    @Schema(description = "Identificador gerado pelo servidor") UUID id,
    @Schema(description = "Nome completo") String nome,
    @Schema(description = "E-mail, sempre em minusculas") String email,
    @Schema(description = "Instante do cadastro, em UTC") Instant criadoEm) {}
