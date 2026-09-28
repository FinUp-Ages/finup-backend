package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo da consulta de disponibilidade de e-mail, feita pelo formulario de cadastro. Vai no corpo,
 * e nao na URL, porque o e-mail e dado pessoal e a URL aparece em log de acesso.
 */
@Schema(description = "E-mail a consultar")
public record EmailAvailabilityRequest(
    @Schema(description = "E-mail a consultar", example = "ana@exemplo.com")
        @NotBlank(message = "nao pode estar em branco")
        @Email(message = "deve ser um e-mail valido")
        @Size(max = 255, message = "nao pode ter mais de 255 caracteres")
        String email) {

  /**
   * Tira os espacos das pontas antes da validacao: o cadastro grava o e-mail assim, e um " ana@..."
   * nao deve virar 400 so por causa do espaco (o {@code @Email} recusa espaco).
   */
  public EmailAvailabilityRequest {
    email = email == null ? null : email.strip();
  }
}
