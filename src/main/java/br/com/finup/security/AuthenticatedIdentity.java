package br.com.finup.security;

/**
 * Identidade autenticada pelo AWS Cognito, ja resolvida a partir do token da requisicao.
 *
 * <p>{@code cognitoId} e o "sub" do token — o identificador imutavel da identidade no Cognito, e o
 * que vincula um {@link br.com.finup.model.User} local a essa identidade. O backend nunca gera nem
 * valida senha: essa responsabilidade e inteira do Cognito.
 */
public record AuthenticatedIdentity(String cognitoId, String name, String email) {}
