package br.com.finup.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Quais grupos do Cognito viram papel na API, e o que acontece quando o claim nao ajuda. */
class CognitoGroupsAuthoritiesConverterTest {

  private final CognitoGroupsAuthoritiesConverter converter =
      new CognitoGroupsAuthoritiesConverter();

  @Test
  @DisplayName("grupo admin vira ROLE_ADMIN")
  void adminGroupBecomesAdminRole() {
    Jwt token = accessToken().claim("cognito:groups", List.of("admin")).build();

    assertThat(converter.convert(token))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN");
  }

  @Test
  @DisplayName("admin entre outros grupos continua valendo, e os outros nao viram papel")
  void otherGroupsAreIgnored() {
    Jwt token =
        accessToken()
            .claim("cognito:groups", List.of("clientes", "admin", "us-east-1_TestPool_Google"))
            .build();

    assertThat(converter.convert(token))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN");
  }

  @Test
  @DisplayName("token sem o claim cognito:groups nao tem papel nenhum")
  void missingClaimGrantsNothing() {
    assertThat(converter.convert(accessToken().build())).isEmpty();
  }

  @Test
  @DisplayName("o scope aws.cognito.signin.user.admin nao e o grupo admin")
  void cognitoAdminScopeIsNotTheAdminGroup() {
    Jwt token = accessToken().claim("scope", "aws.cognito.signin.user.admin").build();

    assertThat(converter.convert(token)).isEmpty();
  }

  @ParameterizedTest(name = "cognito:groups = {0}")
  @MethodSource("claimsWithoutAdmin")
  @DisplayName("claim vazio, de outro grupo ou fora do formato nao da papel nem lanca excecao")
  void claimWithoutAdminGrantsNothing(Object groups) {
    Jwt token = accessToken().claim("cognito:groups", groups).build();

    assertThat(converter.convert(token)).isEmpty();
  }

  static Stream<Object> claimsWithoutAdmin() {
    return Stream.of(
        List.of(), List.of("clientes"), List.of("admins"), List.of("ADMIN"), "admin", 42);
  }

  private static Jwt.Builder accessToken() {
    return Jwt.withTokenValue("token").header("alg", "RS256").subject("cognito-sub-123");
  }
}
