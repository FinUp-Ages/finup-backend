package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * O que a API devolve sobre um usuario.
 *
 * <p>Existe separado da entidade de proposito: assim um campo novo no dominio nao vaza para o
 * contrato sem alguem decidir. Nunca devolva {@code User} direto de um controller.
 *
 * <p>O nome do componente do {@code record} e o nome do campo no JSON — por isso o contrato tambem
 * sai em ingles.
 */
@Schema(description = "Usuario cadastrado")
public record UserResponse(
    @Schema(description = "Identificador gerado pelo servidor") UUID id,
    @Schema(description = "Nome completo") String name,
    @Schema(description = "E-mail, sempre em minusculas") String email,
    @Schema(description = "Instante do cadastro, em UTC") Instant createdAt) {}
