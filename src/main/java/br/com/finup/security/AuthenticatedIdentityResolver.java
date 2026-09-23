package br.com.finup.security;

/**
 * Contrato para obter a identidade autenticada da requisicao atual.
 *
 * <p>O controller e o service dependem desta interface, nunca da implementacao — mesmo principio do
 * {@link br.com.finup.repository.UserRepository}. E o que permite trocar a resolucao mockada por
 * uma leitura real do JWT do Cognito (via Spring Security OAuth2 Resource Server) sem tocar em
 * regra de negocio nem na camada HTTP.
 */
public interface AuthenticatedIdentityResolver {

  AuthenticatedIdentity resolveCurrent();
}
