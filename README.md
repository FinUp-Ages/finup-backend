# finup-backend

API REST do projeto **FinUp** — AGES 2026/2.

Java 21 · Spring Boot 3.3 · Maven · Docker

> Esqueleto do projeto. As camadas estão criadas e vazias — nenhuma regra de negócio foi implementada ainda.

---

## Pré-requisitos

- JDK 21
- Maven 3.9+ (ou use o wrapper `./mvnw` depois de gerá-lo)
- Docker (opcional, para rodar via container)

## Como rodar

```bash
# via Maven
mvn spring-boot:run

# ou via Docker
docker build -t finup-backend .
docker run -p 8080:8080 finup-backend
```

Depois de subir:

| O quê | URL |
|---|---|
| Health check | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Contrato OpenAPI | http://localhost:8080/v3/api-docs |

## Como validar antes de abrir um PR

```bash
mvn clean verify
```

O mesmo comando roda no CI a cada Pull Request. Se falhar localmente, vai falhar lá.

---

## Estrutura de pastas

```
src/main/java/br/com/finup/
├── FinUpApplication.java     ponto de entrada
├── config/                   classes @Configuration (Security, CORS, OpenAPI...)
├── controller/               @RestController — camada HTTP
├── service/                  @Service — regra de negócio
├── repository/               acesso a dados
├── model/                    entidades e enums do domínio
├── dto/                      objetos de entrada/saída da API
├── mapper/                   conversão entidade ↔ DTO
└── exception/                exceções de negócio e handler global
```

Cada pasta tem um `.gitkeep` com uma linha explicando o que vai dentro. **Apague o `.gitkeep` quando a pasta receber a primeira classe** — ele só existe porque o Git não versiona diretórios vazios.

### Responsabilidade de cada camada

- **controller** — recebe a requisição, valida o formato de entrada, delega ao service e devolve o DTO de resposta. Não contém regra de negócio nem acesso a banco.
- **service** — onde a regra de negócio vive. Não conhece HTTP: nada de `HttpServletRequest`, `ResponseEntity` ou código de status aqui.
- **repository** — acesso a dados, isolado do resto.
- **dto** — o que entra e o que sai da API. Entidade nunca é exposta diretamente no controller.
- **model** — o domínio propriamente dito.

### Convenções

- Todo endpoint recebe e devolve **DTO tipado**, nunca `Map<String, Object>` ou `ResponseEntity<?>`. O contrato OpenAPI só tem valor se os tipos forem concretos — é dele que web e mobile vão gerar seus clientes.
- Tratamento de erro centralizado no `exception/`, em formato único para toda a API.
- Nada de credencial ou chave em código: use variáveis de ambiente (veja `.env.example`).

---

## O que ainda não está aqui (e por quê)

Estas dependências estão **comentadas no `pom.xml`**, prontas para serem descomentadas quando o escopo avançar:

| Item | Quando habilitar |
|---|---|
| Banco de dados (JPA + PostgreSQL) | quando a modelagem de dados estiver definida |
| Spring Security | quando o fluxo de autenticação estiver definido |
| Migrations (Flyway) | junto com o banco |

Os pacotes por funcionalidade (transações, score, trilhas, IA...) serão criados conforme cada feature entrar em sprint — não foram criados antecipadamente porque o escopo ainda está em discussão.

---

## Estrutura de suporte

```
.github/
├── workflows/ci.yml          build + testes em todo PR
├── PULL_REQUEST_TEMPLATE.md  template de descrição de PR
└── CODEOWNERS                revisão obrigatória em controller/ e dto/
Dockerfile                    build multi-stage
.env.example                  variáveis de ambiente esperadas
```

**Antes do primeiro PR:** ajuste o `@arquitetura` no `CODEOWNERS` para o time ou usuário real da organização no GitHub.
