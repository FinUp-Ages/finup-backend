package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.AdminUserResponse;
import br.com.finup.dto.PageResponse;
import br.com.finup.exception.InvalidAdminUserQueryException;
import br.com.finup.model.User;
import br.com.finup.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Testes unitarios de service, sem contexto Spring e sem banco: o repositorio e um duble. Roda em
 * milissegundos e falha por um motivo so — a regra de negocio.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

  @Mock private UserRepository userRepository;

  private AdminUserService service;

  private AdminUserService service() {
    Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    return new AdminUserService(userRepository, clock);
  }

  @Test
  @DisplayName("sem parametros usa page 0, size 20, sem filtro de status nem busca")
  void defaultsPageSizeAndNoFilters() {
    service = service();
    when(userRepository.search(isNull(), isNull(), eq(User.activeSince(NOW)), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    service.listUsers(null, null, null, null);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(userRepository)
        .search(isNull(), isNull(), eq(User.activeSince(NOW)), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
  }

  @Test
  @DisplayName("status ACTIVE vira true, INACTIVE vira false, no filtro do repositorio")
  void mapsStatusParamToBoolean() {
    service = service();
    when(userRepository.search(any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    service.listUsers(null, null, "ACTIVE", null);
    verify(userRepository).search(isNull(), eq(true), eq(User.activeSince(NOW)), any());

    service.listUsers(null, null, "INACTIVE", null);
    verify(userRepository).search(isNull(), eq(false), eq(User.activeSince(NOW)), any());
  }

  @Test
  @DisplayName("status invalido lanca excecao dizendo os valores aceitos")
  void rejectsInvalidStatus() {
    service = service();

    assertThatThrownBy(() -> service.listUsers(null, null, "banido", null))
        .isInstanceOf(InvalidAdminUserQueryException.class)
        .hasMessageContaining("'status'")
        .hasMessageContaining("ACTIVE, INACTIVE");
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "-1", "1.5"})
  @DisplayName("page nao numerico ou negativo lanca excecao")
  void rejectsInvalidPage(String page) {
    service = service();

    assertThatThrownBy(() -> service.listUsers(page, null, null, null))
        .isInstanceOf(InvalidAdminUserQueryException.class)
        .hasMessageContaining("'page'");
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "0", "101", "-5"})
  @DisplayName("size nao numerico ou fora de 1 a 100 lanca excecao")
  void rejectsInvalidSize(String size) {
    service = service();

    assertThatThrownBy(() -> service.listUsers(null, size, null, null))
        .isInstanceOf(InvalidAdminUserQueryException.class)
        .hasMessageContaining("'size'");
  }

  @Test
  @DisplayName("busca em branco e tratada como ausente, sem filtrar nada")
  void blankSearchIsTreatedAsAbsent() {
    service = service();
    when(userRepository.search(isNull(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    service.listUsers(null, null, null, "   ");

    verify(userRepository).search(isNull(), isNull(), any(), any());
  }

  @Test
  @DisplayName("mapeia o Page devolvido pelo repositorio para o contrato de pagina")
  void mapsRepositoryPageToPageResponse() {
    service = service();
    User user = User.createFromCognitoIdentity("sub-1", "Carlos Jose", "carlos.j@email.com");
    Page<User> repositoryPage = new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1);
    when(userRepository.search(any(), any(), any(), any())).thenReturn(repositoryPage);

    PageResponse<AdminUserResponse> result = service.listUsers(null, null, null, null);

    assertThat(result.content()).hasSize(1);
    assertThat(result.content().get(0).id()).isEqualTo(user.getId());
    assertThat(result.content().get(0).status()).isEqualTo(br.com.finup.dto.UserStatus.INACTIVE);
    assertThat(result.page()).isZero();
    assertThat(result.size()).isEqualTo(20);
    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.totalPages()).isEqualTo(1);
  }
}
