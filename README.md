# finup-backend

API REST do projeto **FinUp** — AGES 2026/2.

Java 21 · Spring Boot 3.3 · Maven · Docker

> Esqueleto do projeto. As camadas estão criadas e vazias — nenhuma regra de negócio foi implementada ainda.

---

## Pré-requisitos

- JDK 21
- Docker (opcional, para rodar via container)

Maven **não** precisa estar instalado: use o wrapper `./mvnw` (`mvnw.cmd` no Windows), que baixa a
versão correta na primeira execução. É a mesma versão usada pelo CI e pelo Dockerfile.

## Como rodar

```bash
# via Maven Wrapper
./mvnw spring-boot:run

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
./mvnw clean verify
```

O mesmo comando roda no CI a cada Pull Request. Se falhar localmente, vai falhar lá.

`verify` inclui o **Spotless** (`google-java-format`). Se ele reprovar a formatação:

```bash
./mvnw spotless:apply
```

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
- Tratamento de erro centralizado no `exception/`, em formato único para toda a API — veja
  [Contrato de erro](#contrato-de-erro). Nunca monte resposta de erro dentro de um controller.
- Nada de credencial ou chave em código: use variáveis de ambiente (veja `.env.example`).

---

## Exemplo de referência: cadastro de usuário

O recurso `User` existe como **exemplo executável do padrão**. Ao criar uma feature nova, copie a
estrutura dele. Cada arquivo mostra a responsabilidade de uma camada:

| Arquivo | Papel |
|---|---|
| `controller/UserController.java` | recebe, `@Valid`, delega, escolhe o status. Sem `try/catch` |
| `service/UserService.java` | a regra (e-mail duplicado). Não conhece HTTP |
| `repository/UserRepository.java` | interface — é dela que o service depende |
| `repository/InMemoryUserRepository.java` | implementação temporária, **sai quando o JPA entrar** |
| `model/User.java` | entidade imutável, com as invariantes do domínio |
| `dto/RegisterUserRequest.java` | entrada + validação + `@Schema` do OpenAPI |
| `dto/UserResponse.java` | saída. A entidade nunca é exposta |
| `mapper/UserMapper.java` | conversão entidade ↔ DTO |
| `exception/EmailAlreadyRegisteredException.java` | erro de negócio com o status que lhe cabe (409) |

Testes correspondentes, também de referência:

- `service/UserServiceTest.java` — unitário, Mockito, sem contexto Spring. É o formato padrão.
- `controller/UserControllerTest.java` — `@WebMvcTest`, só a camada web.

```bash
curl -X POST http://localhost:8080/api/v1/users   -H 'Content-Type: application/json'   -d '{"name":"Ana Souza","email":"ana@exemplo.com"}'
```

Devolve `201` com `Location`. Repetir a mesma chamada devolve `409`; mandar `email` inválido
devolve `400` listando os campos.

### Idioma

**Identificador é código, em inglês. Prosa é conteúdo, em português.**

| | Idioma | Exemplo |
|---|---|---|
| Classes, métodos, variáveis, pacotes | inglês | `UserService.register()`, `existsByEmail` |
| Campos do JSON e caminhos da API | inglês | `/api/v1/users`, `{"name": …, "createdAt": …}` |
| Nome do método de teste | inglês | `rejectsDuplicateEmail()` |
| Javadoc e comentários | português | explicam a decisão para quem lê |
| `@DisplayName` e `@Schema` | português | o relatório de teste e o Swagger são para o time |
| **Mensagem que chega ao cliente** | **português** | `"Ja existe um usuario cadastrado…"`, `"deve ser um e-mail valido"` |

A regra é essa: se o texto vai ser lido por uma pessoa, português; se é um símbolo do código ou do
contrato, inglês. Em `record`, o nome do componente **é** o nome do campo JSON — por isso o
contrato acompanha o código.

### O que o exemplo estabelece

- **Versão no caminho desde o primeiro endpoint** (`/api/v1/...`). Adicionar versionamento depois
  que web e mobile já consomem a API custa muito mais caro.
- **Injeção por construtor com campo `final`** — nunca `@Autowired` em campo.
- **`record` para DTO**, classe para entidade.
- **Sufixo diz a camada**: `*Controller`, `*Service`, `*Repository`, `*Request`, `*Response`,
  `*Mapper`, `*Exception`. O nome do arquivo já responde onde ele mora.
- **Nada de `null` cruzando fronteira**: o repositório devolve `Optional`.
- **Erro é exceção**, não código de retorno. Quem traduz para HTTP é o handler global.

> O exemplo é deletável. Quando o cadastro real de usuário for implementado, ele substitui este —
> mas a estrutura permanece.

## Contrato de erro

Todo erro da API sai em **RFC 7807** (`application/problem+json`), montado pelo
`ApiExceptionHandler` (`exception/`). O controller não trata erro: o service lança
`BusinessException` (ou uma subclasse, como `ResourceNotFoundException`) e o handler traduz.

```json
{
  "type": "https://finup.com.br/erros/validacao",
  "title": "Requisicao invalida",
  "status": 400,
  "detail": "Um ou mais campos estao invalidos.",
  "timestamp": "2026-09-01T18:22:31.004Z",
  "traceId": "3f9c1e2a-8d47-4a91-9b0e-27c5d6f81a33",
  "campos": [
    { "campo": "valor", "mensagem": "deve ser maior que 0" }
  ]
}
```

`traceId` vai na resposta **e** no log. Erro inesperado nunca devolve a mensagem original ao
cliente — só o `traceId` para localizar no log.

## Configuração por ambiente

Nenhum valor de ambiente fica em código. O `application.yml` lê variáveis com default de
desenvolvimento (veja `.env.example`):

| Variável | Default | Para quê |
|---|---|---|
| `SERVER_PORT` | `8080` | porta HTTP |
| `SPRING_PROFILES_ACTIVE` | `dev` | perfil ativo; use `prod` no ambiente implantado |
| `LOG_LEVEL` | `DEBUG` no perfil `dev`, `INFO` fora dele | nível de log de `br.com.finup` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | origens do CORS, separadas por vírgula |

Spring Boot **não lê `.env` nativamente**. O arquivo existe para o `docker-compose` (`env_file`) e
como referência — rodando via `./mvnw`, exporte no shell ou confie nos defaults do perfil `dev`.

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
.mvn/ + mvnw + mvnw.cmd       Maven Wrapper — não precisa de Maven instalado
.github/
├── workflows/ci.yml          build + formatação + testes em todo PR
├── dependabot.yml            atualização semanal de dependências
├── PULL_REQUEST_TEMPLATE.md  template de descrição de PR
└── CODEOWNERS                revisão obrigatória em controller/, dto/, contracts/ e build
Dockerfile                    build multi-stage, runtime sem root
.dockerignore                 o que fica fora do contexto de build
.editorconfig                 alinha o editor com o google-java-format
.env.example                  variáveis de ambiente esperadas
```

O `CODEOWNERS` só tem efeito com **"Require review from Code Owners"** ligado na branch
protection — sem isso o GitHub ignora o arquivo em silêncio.
