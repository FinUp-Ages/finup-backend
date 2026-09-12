package br.com.finup;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class FinUpApplicationTests {

  @Test
  void contextLoads() {
    // Garante que a aplicacao sobe. Mantenha este teste passando.
  }
}
