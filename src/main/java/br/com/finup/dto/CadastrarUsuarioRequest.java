package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo do POST de cadastro.
 *
 * <p>{@code record} por padrao: DTO nao tem comportamento, so transporta dado. As anotacoes de
 * validacao ficam aqui — o controller nao escreve {@code if} de formato, so anota {@code @Valid} e
 * deixa o {@code ApiExceptionHandler} montar o 400.
 *
 * <p>O {@code @Schema} nao e enfeite: e ele que descreve o campo no OpenAPI que finup-web e
 * finup-mobile consomem.
 */
@Schema(description = "Dados para cadastrar um usuario")
public record CadastrarUsuarioRequest(
    @Schema(description = "Nome completo", example = "Ana Souza")
        @NotBlank(message = "e obrigatorio")
        @Size(max = 120, message = "deve ter no maximo 120 caracteres")
        String nome,
    @Schema(description = "E-mail unico do usuario", example = "ana.souza@exemplo.com")
        @NotBlank(message = "e obrigatorio")
        @Email(message = "deve ser um e-mail valido")
        @Size(max = 180, message = "deve ter no maximo 180 caracteres")
        String email) {}
