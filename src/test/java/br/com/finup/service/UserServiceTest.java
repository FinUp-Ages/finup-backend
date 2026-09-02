package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.exception.EmailAlreadyRegisteredException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.User;
import br.com.finup.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Exemplo de referencia de teste unitario de service.
 *
 * <p>Sem contexto Spring e sem banco: o repositorio e um duble. Roda em milissegundos e falha por
 * um motivo so — a regra de negocio. Se um teste de service precisa de {@code @SpringBootTest},
 * quase sempre o problema e acoplamento no service, nao no teste.
 *
 * <p>Nome do metodo em ingles, {@code @DisplayName} em portugues: o relatorio de teste e para o
 * time ler.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private UserService userService;

  @Test
  @DisplayName("cadastra usuario quando o e-mail ainda nao existe")
  void registersWhenEmailIsNew() {
    when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User user = userService.register("Ana Souza", "ana@exemplo.com");

    assertThat(user.getId()).isNotNull();
    assertThat(user.getName()).isEqualTo("Ana Souza");
    assertThat(user.getEmail()).isEqualTo("ana@exemplo.com");
    assertThat(user.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("normaliza o e-mail antes de gravar")
  void normalizesEmailBeforeSaving() {
    when(userRepository.existsByEmail(any())).thenReturn(false);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User user = userService.register("  Ana Souza  ", "  Ana@Exemplo.COM ");

    assertThat(user.getEmail()).isEqualTo("ana@exemplo.com");
    assertThat(user.getName()).isEqualTo("Ana Souza");
  }

  @Test
  @DisplayName("recusa cadastro com e-mail ja usado e nao chega a gravar")
  void rejectsDuplicateEmail() {
    when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(true);

    assertThatThrownBy(() -> userService.register("Ana Souza", "ana@exemplo.com"))
        .isInstanceOf(EmailAlreadyRegisteredException.class)
        .hasMessageContaining("ana@exemplo.com");

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("buscar por id inexistente lanca ResourceNotFoundException")
  void throwsWhenIdDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(userRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.findById(id))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
