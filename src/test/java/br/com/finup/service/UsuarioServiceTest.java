package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.exception.EmailJaCadastradoException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.Usuario;
import br.com.finup.repository.UsuarioRepository;
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
 * <p>Sem contexto Spring e sem banco: o repositorio e um dublê. Roda em milissegundos e falha por
 * um motivo so — a regra de negocio. Se um teste de service precisa de {@code @SpringBootTest},
 * quase sempre o problema e acoplamento no service, nao no teste.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

  @Mock private UsuarioRepository usuarioRepository;

  @InjectMocks private UsuarioService usuarioService;

  @Test
  @DisplayName("cadastra usuario quando o e-mail ainda nao existe")
  void cadastraQuandoEmailEhInedito() {
    when(usuarioRepository.existePorEmail("ana@exemplo.com")).thenReturn(false);
    when(usuarioRepository.salvar(any(Usuario.class)))
        .thenAnswer(chamada -> chamada.getArgument(0));

    Usuario usuario = usuarioService.cadastrar("Ana Souza", "ana@exemplo.com");

    assertThat(usuario.getId()).isNotNull();
    assertThat(usuario.getNome()).isEqualTo("Ana Souza");
    assertThat(usuario.getEmail()).isEqualTo("ana@exemplo.com");
    assertThat(usuario.getCriadoEm()).isNotNull();
  }

  @Test
  @DisplayName("normaliza o e-mail antes de gravar")
  void normalizaEmailAntesDeGravar() {
    when(usuarioRepository.existePorEmail(any())).thenReturn(false);
    when(usuarioRepository.salvar(any(Usuario.class)))
        .thenAnswer(chamada -> chamada.getArgument(0));

    Usuario usuario = usuarioService.cadastrar("  Ana Souza  ", "  Ana@Exemplo.COM ");

    assertThat(usuario.getEmail()).isEqualTo("ana@exemplo.com");
    assertThat(usuario.getNome()).isEqualTo("Ana Souza");
  }

  @Test
  @DisplayName("recusa cadastro com e-mail ja usado e nao chega a gravar")
  void recusaEmailDuplicado() {
    when(usuarioRepository.existePorEmail("ana@exemplo.com")).thenReturn(true);

    assertThatThrownBy(() -> usuarioService.cadastrar("Ana Souza", "ana@exemplo.com"))
        .isInstanceOf(EmailJaCadastradoException.class)
        .hasMessageContaining("ana@exemplo.com");

    verify(usuarioRepository, never()).salvar(any());
  }

  @Test
  @DisplayName("buscar por id inexistente lanca ResourceNotFoundException")
  void buscarPorIdInexistente() {
    UUID id = UUID.randomUUID();
    when(usuarioRepository.buscarPorId(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> usuarioService.buscarPorId(id))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
