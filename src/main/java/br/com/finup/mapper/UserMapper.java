package br.com.finup.mapper;

import br.com.finup.dto.UserResponse;
import br.com.finup.model.User;
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
    return new UserResponse(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getBirthDate(),
        user.getMonthlyIncome(),
        user.getFinancialProfile(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }

  public static List<UserResponse> toResponses(List<User> users) {
    return users.stream().map(UserMapper::toResponse).toList();
  }
}
