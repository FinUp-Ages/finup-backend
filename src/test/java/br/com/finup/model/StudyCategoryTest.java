package br.com.finup.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudyCategoryTest {

  @Test
  @DisplayName("Deve inicializar e alterar propriedades de StudyCategory corretamente")
  void shouldInitializeAndModifyCategory() {
    UUID id = UUID.randomUUID();
    StudyCategory category = new StudyCategory(id, "Planejamento Financeiro", "Orçamento pessoal");

    assertThat(category.getId()).isEqualTo(id);
    assertThat(category.getName()).isEqualTo("Planejamento Financeiro");
    assertThat(category.getDescription()).isEqualTo("Orçamento pessoal");
    assertThat(category.getCreatedAt()).isNotNull();
    assertThat(category.getUpdatedAt()).isNotNull();

    category.setName("Novo Nome");
    category.setDescription("Nova Descrição");
    assertThat(category.getName()).isEqualTo("Novo Nome");
    assertThat(category.getDescription()).isEqualTo("Nova Descrição");
  }

  @Test
  @DisplayName("Deve verificar igualdade baseada no ID")
  void shouldVerifyEqualityBasedOnId() {
    UUID id = UUID.randomUUID();
    StudyCategory cat1 = new StudyCategory(id, "Cat 1", "Desc 1");
    StudyCategory cat2 = new StudyCategory(id, "Cat 2", "Desc 2");
    StudyCategory cat3 = new StudyCategory(UUID.randomUUID(), "Cat 1", "Desc 1");

    assertThat(cat1).isEqualTo(cat2);
    assertThat(cat1.hashCode()).isEqualTo(cat2.hashCode());
    assertThat(cat1).isNotEqualTo(cat3);
  }
}
