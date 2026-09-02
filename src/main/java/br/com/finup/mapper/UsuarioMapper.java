package br.com.finup.mapper;

import br.com.finup.dto.UsuarioResponse;
import br.com.finup.model.Usuario;
import java.util.List;

/**
 * Conversao entre {@link Usuario} e os DTOs da API.
 *
 * <p>Fica isolado para que a decisao "o que sai no contrato" tenha um lugar so. Sem isto, cada
 * controller monta a resposta do seu jeito e o contrato deixa de ser previsivel.
 *
 * <p>Metodos estaticos porque a conversao e pura — nao tem estado nem dependencia. Se um dia a
 * conversao passar a precisar de colaborador, vire {@code @Component} com injecao por construtor.
 */
public final class UsuarioMapper {

  private UsuarioMapper() {}

  public static UsuarioResponse paraResposta(Usuario usuario) {
    return new UsuarioResponse(
        usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getCriadoEm());
  }

  public static List<UsuarioResponse> paraRespostas(List<Usuario> usuarios) {
    return usuarios.stream().map(UsuarioMapper::paraResposta).toList();
  }
}
