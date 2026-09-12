package br.com.finup.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.finup.model.User;

/** Acesso a dados de usuario. */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByCognitoId(String cognitoId);

  boolean existsByCognitoId(String cognitoId);

  boolean existsByEmail(String email);
}
