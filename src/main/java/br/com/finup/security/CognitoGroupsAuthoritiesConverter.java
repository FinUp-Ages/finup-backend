package br.com.finup.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Traduz os grupos do Cognito ({@code cognito:groups}) nos papeis da API ({@link Role}).
 *
 * <p>Substitui o conversor padrao do Spring, que le o claim {@code scope}. De proposito: o access
 * token do Cognito traz o scope {@code aws.cognito.signin.user.admin} para todo usuario, e ele nao
 * tem nada a ver com ser administrador — so autoriza o usuario a ler os proprios atributos.
 *
 * <p>Usuario sem grupo nao tem o claim. Claim ausente, nulo, vazio ou fora do formato de lista
 * resulta em nenhum papel, nunca em excecao: a requisicao segue autenticada e as rotas restritas
 * respondem 403.
 */
public class CognitoGroupsAuthoritiesConverter
    implements Converter<Jwt, Collection<GrantedAuthority>> {

  private static final String GROUPS_CLAIM = "cognito:groups";

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    if (!(jwt.getClaims().get(GROUPS_CLAIM) instanceof Collection<?> groups)) {
      return List.of();
    }
    return Arrays.stream(Role.values())
        .filter(role -> groups.contains(role.cognitoGroup()))
        .map(Role::authority)
        .toList();
  }
}
