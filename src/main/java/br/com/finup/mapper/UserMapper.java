package br.com.finup.mapper;

import br.com.finup.dto.AdminMeResponse;
import br.com.finup.dto.AdminUserResponse;
import br.com.finup.dto.UserResponse;
import br.com.finup.dto.UserStatus;
import br.com.finup.model.User;
import br.com.finup.model.UserFinancialProfile;
import br.com.finup.security.Role;
import java.time.Instant;
import java.util.List;

/**
 * Conversao entre {@link User} e os DTOs da API.
 *
 * <p>Fica isolado para que a decisao "o que sai no contrato" tenha um lugar so. Sem isto, cada
 * controller monta a resposta do seu jeito e o contrato deixa de ser previsivel.
 *
 * <p>Metodos estaticos porque a conversao e pura — nao tem estado nem dependencia. Se um dia a
 * conversao passar a precisar de colaborador, vire {@code @Component} com injecao por construtor.
 */
public final class UserMapper {

  private UserMapper() {}

  public static UserResponse toResponse(User user) {
    return toResponse(user, null);
  }

  /** {@code profile} e nulo enquanto o usuario nao informou nada alem da Etapa 1. */
  public static UserResponse toResponse(User user, UserFinancialProfile profile) {
    return new UserResponse(
        user.getId(),
        user.getName(),
        user.getEmail(),
        profile != null ? profile.getPhone() : null,
        profile != null ? profile.getProfession() : null,
        profile != null ? profile.getBirthDate() : null,
        profile != null ? profile.getMonthlyIncome() : null,
        user.getFinancialProfile(),
        user.getCreatedAt(),
        latestUpdate(user, profile));
  }

  /** O PATCH so altera o perfil, entao a ultima atualizacao e a mais recente entre os dois. */
  private static Instant latestUpdate(User user, UserFinancialProfile profile) {
    if (profile == null || profile.getUpdatedAt().isBefore(user.getUpdatedAt())) {
      return user.getUpdatedAt();
    }
    return profile.getUpdatedAt();
  }

  public static List<UserResponse> toResponses(List<User> users) {
    return users.stream().map(UserMapper::toResponse).toList();
  }

  /**
   * O papel e fixo porque a rota que usa esta resposta so e alcancada por administrador — veja o
   * {@code SecurityConfig}.
   */
  public static AdminMeResponse toAdminMeResponse(User user) {
    return new AdminMeResponse(user.getId(), user.getName(), user.getEmail(), Role.ADMIN);
  }

  /** {@code status} e derivado de {@code lastSeenAt} em relacao a {@code now}, nunca persistido. */
  public static AdminUserResponse toAdminUserResponse(User user, Instant now) {
    return new AdminUserResponse(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getCreatedAt(),
        user.getLastSeenAt(),
        user.isActive(now) ? UserStatus.ACTIVE : UserStatus.INACTIVE);
  }
}
