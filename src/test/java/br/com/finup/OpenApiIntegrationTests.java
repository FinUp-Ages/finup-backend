package br.com.finup;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("test")
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
        .andExpect(jsonPath("$.paths['/api/v1/assistant/messages']").doesNotExist())
        .andExpect(
            jsonPath("$.components.schemas.TransactionResponse.properties.userId").doesNotExist())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.schemas.CategoryListResponse.properties.id").exists())
        .andExpect(jsonPath("$.components.schemas.CategoryListResponse.properties.name").exists())
        .andExpect(jsonPath("$.components.schemas.CategoryListResponse.properties.type").exists())
        .andExpect(
            jsonPath("$.components.schemas.CategoryListResponse.properties.isDefault")
                .doesNotExist());
  }

  @Test
  @DisplayName("Swagger documenta 404 para recursos de outro usuario nas rotas afetadas")
  void documentsNotFoundForForeignResources() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}'].get.responses['404']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/transactions'].post.responses['404']").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/transactions'].post.responses['404'].description")
                .value(containsString("outro usuario")))
        .andExpect(jsonPath("$.paths['/api/v1/categories/{id}'].put.responses['404']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/categories/{id}'].delete.responses['404']").exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/assistant/conversations/{id}/messages'].get.responses['404']")
                .exists());
  }

  @Test
  @DisplayName("Swagger documenta GET /api/v1/admin/me com 200, 401 e 403 em problem+json")
  void documentsAdminMe() throws Exception {
    String adminMe = "$.paths['/api/v1/admin/me'].get.responses";
    String problemJson = ".content['application/problem+json'].schema['$ref']";
    String problemDetail = "#/components/schemas/ProblemDetail";
    String adminMeResponse = "$.components.schemas.AdminMeResponse.properties";

    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                adminMe + "['200'].content.*.schema['$ref']",
                org.hamcrest.Matchers.hasItem("#/components/schemas/AdminMeResponse")))
        .andExpect(jsonPath(adminMe + "['401']" + problemJson).value(problemDetail))
        .andExpect(jsonPath(adminMe + "['403']" + problemJson).value(problemDetail))
        .andExpect(jsonPath(adminMe + "['404']" + problemJson).value(problemDetail))
        .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.status").exists())
        .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.detail").exists())
        .andExpect(jsonPath(adminMeResponse + ".length()").value(4))
        .andExpect(jsonPath(adminMeResponse + ".id").exists())
        .andExpect(jsonPath(adminMeResponse + ".name").exists())
        .andExpect(jsonPath(adminMeResponse + ".email").exists())
        .andExpect(jsonPath(adminMeResponse + ".role.enum[0]").value("ADMIN"))
        .andExpect(
            jsonPath("$.paths['/api/v1/admin/dashboard/widgets'].get.responses['403']").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/admin/dashboard/widgets'].put.responses['403']").exists());
  }

  @Test
  @DisplayName("Swagger UI redireciona para a interface")
  void redirectsSwaggerUiToItsInterface() throws Exception {
    mockMvc
        .perform(get("/swagger-ui.html"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
  }

  @Test
  @DisplayName("Scalar disponibiliza a referencia da API")
  void scalarReferenceIsAvailable() throws Exception {
    mockMvc.perform(get("/docs")).andExpect(status().isOk());
  }
}
