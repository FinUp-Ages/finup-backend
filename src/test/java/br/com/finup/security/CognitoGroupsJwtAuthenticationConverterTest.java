package br.com.finup.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CognitoGroupsJwtAuthenticationConverterTest {

  private final CognitoGroupsJwtAuthenticationConverter converter =
      new CognitoGroupsJwtAuthenticationConverter();

  private static Jwt.Builder token() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("cognito-sub-123")
        .issuedAt(Instant.parse("2026-10-15T14:00:00Z"))
        .expiresAt(Instant.parse("2026-10-15T15:00:00Z"));
  }

  @Test
  @DisplayName("cada grupo do cognito:groups vira uma autoridade GROUP_<nome>")
  void mapsEachGroupToAnAuthority() {
    Jwt jwt = token().claim("cognito:groups", List.of("admin", "beta-testers")).build();

    assertThat(converter.convert(jwt).getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("GROUP_admin", "GROUP_beta-testers");
  }

  @Test
  @DisplayName("token sem o claim cognito:groups fica sem nenhuma autoridade")
  void tokenWithoutGroupsHasNoAuthorities() {
    assertThat(converter.convert(token().build()).getAuthorities()).isEmpty();
  }

  @Test
  @DisplayName("o nome do grupo e mantido como veio: Admin nao vira admin")
  void keepsGroupNameAsIs() {
    Jwt jwt = token().claim("cognito:groups", List.of("Admin")).build();

    assertThat(converter.convert(jwt).getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("GROUP_Admin")
        .doesNotContain(CognitoGroupsJwtAuthenticationConverter.authorityOf("admin"));
  }

  @Test
  @DisplayName("o scope do token nao vira autoridade")
  void scopeIsNotMappedToAuthorities() {
    Jwt jwt = token().claim("scope", "aws.cognito.signin.user.admin").build();

    assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
  }

  @Test
  @DisplayName("continua sendo JwtAuthenticationToken, com o sub como nome")
  void staysAJwtAuthenticationTokenNamedBySubject() {
    Jwt jwt = token().claim("cognito:groups", List.of("admin")).build();

    var authentication = converter.convert(jwt);

    assertThat(authentication).isInstanceOf(JwtAuthenticationToken.class);
    assertThat(authentication.getName()).isEqualTo("cognito-sub-123");
    assertThat(((JwtAuthenticationToken) authentication).getToken()).isSameAs(jwt);
  }
}
