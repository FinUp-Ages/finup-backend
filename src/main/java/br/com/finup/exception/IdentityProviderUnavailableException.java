package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/**
 * O Cognito nao respondeu (fora do ar, timeout, limite de requisicoes).
 *
 * <p>503 e nao 401: o token do cliente pode estar perfeitamente valido — quem falhou fomos nos ao
 * consultar o provedor de identidade. O cliente pode tentar de novo.
 */
public class IdentityProviderUnavailableException extends BusinessException {

  public IdentityProviderUnavailableException() {
    super(
        "Nao foi possivel consultar o provedor de identidade. Tente novamente em instantes.",
        HttpStatus.SERVICE_UNAVAILABLE);
  }
}
