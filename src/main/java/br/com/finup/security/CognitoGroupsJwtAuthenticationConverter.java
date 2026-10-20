package br.com.finup.security;

import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Transforma o access token ja validado em autenticacao, com um {@code GROUP_<nome>} para cada
 * grupo do usuario no Cognito ({@code cognito:groups}).
 *
 * <p>O claim so existe quando o usuario esta em ao menos um grupo; sem ele, a autenticacao sai sem
 * nenhuma autoridade. O {@code scope} do token nao vira autoridade: nenhuma rota depende dele.
 *
 * <p>O token continua sendo um {@link JwtAuthenticationToken}, que e o que o {@link
 * CognitoAuthenticatedIdentityResolver} espera encontrar no contexto de seguranca. O nome do grupo
 * e mantido como veio: o Cognito diferencia maiusculas de minusculas, e {@code Admin} nao e {@code
 * admin}.
 */
public class CognitoGroupsJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  static final String GROUPS_CLAIM = "cognito:groups";
  private static final String AUTHORITY_PREFIX = "GROUP_";

  /** Autoridade que o usuario recebe por pertencer ao grupo informado. */
  public static String authorityOf(String group) {
    return AUTHORITY_PREFIX + group;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    List<String> groups = jwt.getClaimAsStringList(GROUPS_CLAIM);
    Collection<GrantedAuthority> authorities =
        groups == null
            ? List.of()
            : groups.stream()
                .<GrantedAuthority>map(group -> new SimpleGrantedAuthority(authorityOf(group)))
                .toList();
    return new JwtAuthenticationToken(jwt, authorities);
  }
}
