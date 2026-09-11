package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * O que a API devolve sobre um usuario.
 *
 * <p>Existe separado da entidade de proposito: assim um campo novo no dominio nao vaza para o
 * contrato sem alguem decidir. Nunca devolva {@code User} direto de um controller.
 *
 * <p>O nome do componente do {@code record} e o nome do campo no JSON — por isso o contrato tambem
 * sai em ingles. {@code cognitoId} nao aparece aqui de proposito: e um detalhe de integracao, nao
 * algo que o cliente precisa ler de volta.
 *
 * <p>{@code birthDate}, {@code monthlyIncome} e {@code financialProfile} vem nulos ate a Etapa 2
 * (informacoes complementares) ser preenchida.
 */
@Schema(description = "Usuario cadastrado")
public record UserResponse(
    @Schema(description = "Identificador gerado pelo servidor") UUID id,
    @Schema(description = "Nome completo") String name,
    @Schema(description = "E-mail, sempre em minusculas") String email,
    @Schema(description = "Data de nascimento, se ja preenchida na Etapa 2") LocalDate birthDate,
    @Schema(description = "Renda mensal, se ja preenchida na Etapa 2") BigDecimal monthlyIncome,
    @Schema(description = "Perfil financeiro, se ja preenchido na Etapa 2") String financialProfile,
    @Schema(description = "Instante do cadastro, em UTC") Instant createdAt,
    @Schema(description = "Instante da ultima atualizacao, em UTC") Instant updatedAt) {}
