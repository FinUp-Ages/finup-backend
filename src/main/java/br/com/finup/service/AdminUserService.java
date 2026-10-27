package br.com.finup.service;

import br.com.finup.dto.AdminUserResponse;
import br.com.finup.dto.PageResponse;
import br.com.finup.exception.InvalidAdminUserQueryException;
import br.com.finup.mapper.UserMapper;
import br.com.finup.model.User;
import br.com.finup.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listagem paginada de usuarios do painel administrativo.
 *
 * <p>Filtro e paginacao sao feitos inteiramente no banco ({@link UserRepository#search}); nenhum
 * usuario e carregado em memoria para filtrar. {@code status} e derivado de {@code lastSeenAt} (ver
 * {@link User#isActive}), nao e uma coluna.
 */
@Service
public class AdminUserService {

  static final int DEFAULT_PAGE = 0;
  static final int DEFAULT_SIZE = 20;
  static final int MAX_SIZE = 100;

  private static final int MAX_ECHOED_LENGTH = 40;

  private final UserRepository userRepository;
  private final Clock clock;

  public AdminUserService(UserRepository userRepository, Clock clock) {
    this.userRepository = userRepository;
    this.clock = clock;
  }

  /**
   * @param pageParam pagina, baseada em zero; ausente vale {@value #DEFAULT_PAGE}
   * @param sizeParam tamanho da pagina, de 1 a {@value #MAX_SIZE}; ausente vale {@value
   *     #DEFAULT_SIZE}
   * @param statusParam {@code ACTIVE}, {@code INACTIVE} ou ausente (sem filtro)
   * @param searchParam busca parcial em nome ou e-mail, sem diferenciar maiusculas de minusculas;
   *     ausente ou em branco nao filtra
   * @throws InvalidAdminUserQueryException se algum parametro nao for aceito
   */
  @Transactional(readOnly = true)
  public PageResponse<AdminUserResponse> listUsers(
      String pageParam, String sizeParam, String statusParam, String searchParam) {
    int page = parsePage(pageParam);
    int size = parseSize(sizeParam);
    Boolean active = parseStatus(statusParam);
    String search = normalizeSearch(searchParam);
    Instant now = clock.instant();

    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<User> result = userRepository.search(search, active, User.activeSince(now), pageable);

    return PageResponse.from(result.map(user -> UserMapper.toAdminUserResponse(user, now)));
  }

  private static int parsePage(String value) {
    if (value == null) {
      return DEFAULT_PAGE;
    }
    int page;
    try {
      page = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new InvalidAdminUserQueryException(
          "Parametro 'page' invalido: '%s' nao e um numero inteiro.".formatted(echoed(value)));
    }
    if (page < 0) {
      throw new InvalidAdminUserQueryException(
          "Parametro 'page' nao pode ser negativo: %d.".formatted(page));
    }
    return page;
  }

  private static int parseSize(String value) {
    if (value == null) {
      return DEFAULT_SIZE;
    }
    int size;
    try {
      size = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new InvalidAdminUserQueryException(
          "Parametro 'size' invalido: '%s' nao e um numero inteiro.".formatted(echoed(value)));
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new InvalidAdminUserQueryException(
          "Parametro 'size' fora do intervalo: %d. Informe de 1 a %d.".formatted(size, MAX_SIZE));
    }
    return size;
  }

  /** {@code true} para ACTIVE, {@code false} para INACTIVE, {@code null} sem filtro. */
  private static Boolean parseStatus(String value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case "ACTIVE" -> true;
      case "INACTIVE" -> false;
      default ->
          throw new InvalidAdminUserQueryException(
              "Parametro 'status' invalido: '%s'. Valores aceitos: ACTIVE, INACTIVE."
                  .formatted(echoed(value)));
    };
  }

  /** Em branco conta como ausente: evita um {@code LIKE '%%'} que nao filtraria nada mesmo. */
  private static String normalizeSearch(String search) {
    if (search == null) {
      return null;
    }
    String trimmed = search.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** O valor volta na mensagem de erro; corta o que for longo para nao inflar a resposta. */
  private static String echoed(String value) {
    return value.length() <= MAX_ECHOED_LENGTH
        ? value
        : value.substring(0, MAX_ECHOED_LENGTH) + "...";
  }
}
