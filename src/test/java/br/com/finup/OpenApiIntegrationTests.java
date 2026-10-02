package br.com.finup;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Garante que o contrato OpenAPI publicado continua identificando o projeto e expondo o recurso de
 * usuarios, transacoes e categorias.
 *
 * <p>E deste contrato que finup-web e finup-mobile geram cliente: se alguem apagar o {@code
 * OpenApiConfig} ou mudar o path do controller, o CI reprova aqui em vez de a quebra aparecer no
 * consumidor.
 */
@SpringBootTest(
    properties = {
      "finup.cognito.user-pool-id=us-east-1_TestPool",
      "finup.cognito.client-id=test-client"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@AutoConfigureMockMvc
class OpenApiIntegrationTests {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName(
      "contrato OpenAPI expoe os metadados, o esquema Bearer e os recursos de usuarios e"
          + " transacoes e categorias")
  void exposesOpenApiContractWithProjectMetadata() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.openapi", startsWith("3.")))
        .andExpect(jsonPath("$.info.title").value("FinUp API"))
        .andExpect(jsonPath("$.info.version").value("v0.0.1"))
        .andExpect(jsonPath("$.info.contact.name").value("AGES 2026/2 - FinUp"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.security[0].bearerAuth").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/transactions'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/transactions'].get").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/transactions'].get.parameters[*].name")
                .value(org.hamcrest.Matchers.containsInAnyOrder("from", "to")))
        .andExpect(jsonPath("$.paths['/api/v1/categories'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/assistant/conversations'].get").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/assistant/conversations/{id}/messages'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/ai/assistant'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/ai/assistant'].post.parameters").doesNotExist())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.schemas.CategoryListResponse.properties.id").exists())
        .andExpect(jsonPath("$.components.schemas.CategoryListResponse.properties.name").exists())
        .andExpect(jsonPath("$.components.schemas.CategoryListResponse.properties.type").exists())
        .andExpect(
            jsonPath("$.components.schemas.CategoryListResponse.properties.isDefault")
                .doesNotExist());
  }

  @Test
  @DisplayName("Scalar disponibiliza a referencia da API")
  void scalarReferenceIsAvailable() throws Exception {
    mockMvc.perform(get("/docs")).andExpect(status().isOk());
  }
}
