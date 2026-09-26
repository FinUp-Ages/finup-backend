package br.com.finup.security;

/**
 * Contrato para obter a identidade autenticada da requisicao atual.
 *
 * <p>O controller e o service dependem desta interface, nunca da implementacao — mesmo principio do
 * {@link br.com.finup.repository.UserRepository}. E o que permite trocar a resolucao mockada por
 * uma leitura real do JWT do Cognito (via Spring Security OAuth2 Resource Server) sem tocar em
 * regra de negocio nem na camada HTTP.
 *
 * <p>Implementacoes: {@link CognitoAuthenticatedIdentityResolver} (padrao) e {@link
 * MockAuthenticatedIdentityResolver} (profile {@code mock-auth}, so em desenvolvimento).
 */
public interface AuthenticatedIdentityResolver {

  /**
   * Quem esta chamando. So o {@code cognitoId} e garantido — e o que basta para achar o usuario
   * local. Barato: nao faz chamada de rede.
   */
  AuthenticatedIdentity resolveCurrent();

  /**
   * Quem esta chamando, com e-mail (obrigatorio) e nome (opcional). Com o Cognito, custa uma
   * chamada a AWS por requisicao: use so onde esses atributos sao necessarios, como no cadastro.
   */
  AuthenticatedIdentity resolveCurrentWithAttributes();
}
