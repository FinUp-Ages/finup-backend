# finup-backend

API REST do projeto **FinUp** — AGES 2026/2.

Java 21 · Spring Boot 3.5 · Maven · PostgreSQL 16 · Docker

> O cadastro de usuário existe como **exemplo de referência** das convenções (veja a seção mais abaixo).
> O banco já está provisionado e populado, mas a **persistência ainda não está ligada**: o `User` é um POJO de
> domínio sem `@Entity` e o repositório em uso é o `InMemoryUserRepository`. Fazer essa ponte é o próximo passo.

---

## Pré-requisitos

- JDK 21
- Docker — **obrigatório**: o PostgreSQL sobe via `docker compose`, e a aplicação não inicia sem ele

Maven **não** precisa estar instalado: use o wrapper `./mvnw` (`mvnw.cmd` no Windows), que baixa a
versão correta na primeira execução. É a mesma versão usada pelo CI e pelo Dockerfile.

## Como rodar

São três passos, nesta ordem. Pular o primeiro ou o segundo faz a aplicação falhar no startup.

```bash
# 1. credenciais locais: copie o exemplo e defina DB_PASSWORD
cp .env.example .env

# 2. banco: PostgreSQL 16, com schema e carga inicial
docker compose up -d

# 3. aplicacao
./mvnw spring-boot:run
```

O `.env` **não** é opcional: `DB_PASSWORD` não tem valor padrão. Sem ele, ou com o banco fora do ar, a
aplicação morre com uma stack do Hibernate (`Unable to determine Dialect without JDBC metadata` ou
`password authentication failed`), nunca com uma mensagem dizendo que falta configuração.

Detalhes do banco — recriar, popular, ver logs — estão em [Banco de Dados com Docker](#banco-de-dados-com-docker).

Depois de subir:

| O quê | URL |
|---|---|
| Health check | http://localhost:8080/actuator/health |

## Swagger / OpenAPI

Com a aplicação em execução, a documentação da API fica disponível nestas URLs:

| O quê | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Contrato OpenAPI | http://localhost:8080/v3/api-docs |

O Swagger UI lê o contrato OpenAPI gerado automaticamente pelo `springdoc` a partir dos
controllers e DTOs da aplicação. Os metadados gerais da API ficam centralizados em
`config/OpenApiConfig.java`.

### Como documentar novos endpoints

O `springdoc` descobre automaticamente classes com `@RestController`. Use as anotações do pacote
`io.swagger.v3.oas.annotations` apenas para complementar o que não puder ser inferido pelos tipos
Java:

```java
@Operation(summary = "Lista as transações do usuário")
@ApiResponse(responseCode = "200", description = "Transações encontradas")
@GetMapping("/transactions")
public List<TransactionResponse> list() {
    // ...
}
```

Nos DTOs, `@Schema` pode esclarecer regras e fornecer exemplos de campos:

```java
public record TransactionResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "125.90") BigDecimal amount
) {}
```

Evite anotar tudo: nomes claros e DTOs tipados já produzem boa parte do contrato. Depois de criar
ou alterar um endpoint, confira o resultado no Swagger UI e rode `./mvnw clean verify`.

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

## Se o build falhar antes de compilar

### `PKIX path building failed` ao baixar dependências

```
Could not transfer artifact ... from/to central (https://repo.maven.apache.org/maven2):
PKIX path building failed: unable to find valid certification path to requested target
```

Alguma ferramenta de segurança na sua máquina está interceptando TLS: ela substitui o certificado
do Maven Central por um assinado por uma CA própria. O Windows confia nessa CA (por isso navegador
e `git` funcionam), mas o Java tem um *truststore* próprio, o `cacerts`, que não a conhece.

Para descobrir quem está interceptando:

```bash
openssl s_client -connect repo.maven.apache.org:443 -servername repo.maven.apache.org </dev/null 2>/dev/null | grep issuer=
```

**Correção (Windows)** — mandar o Java usar o truststore do Windows, que já confia na CA:

```powershell
setx MAVEN_OPTS "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Abra um terminal novo depois. Não é uma flag insegura: os certificados continuam sendo validados,
apenas contra a lista do sistema em vez da lista embutida no JDK.

> Isto **não** entra no `pom.xml` nem no `.mvn/jvm.config`: `Windows-ROOT` não existe em Linux nem
> em macOS e quebraria o CI e quem não usa Windows. É configuração de máquina, por isso vive aqui.

Em Linux/macOS, o equivalente é importar a CA com `keytool -importcert` num truststore próprio.

### Spotless reprova arquivos que você não editou

Era falta de `.gitattributes` — já corrigido. O arquivo força **LF em todo o repositório**. Sem ele,
o resultado dependia do `core.autocrlf` de cada pessoa, e o mesmo código passava para uns e
reprovava para outros. Se você clonou antes disso, rode uma vez:

```bash
git add --renormalize .
./mvnw spotless:apply
```

### No Windows, use `mvnw.cmd`

Rodar `./mvnw` pelo Git Bash pode falhar ao instalar o Maven (`fail to move MAVEN_HOME`). Use
`.\mvnw.cmd` no PowerShell — é o script feito para Windows.

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
| `DB_NAME` | `finup` | nome do banco |
| `DB_USER` | `finup_user` | usuário do banco |
| `DB_PASSWORD` | **sem default** | senha do banco; sem ela a aplicação não sobe |
| `DB_PORT` | `5432` | porta publicada pelo container do PostgreSQL |

O `application.yml` importa o `.env` da raiz (`spring.config.import: optional:file:.env[.properties]`),
então o mesmo arquivo serve para o `docker compose` e para a aplicação rodando via `./mvnw`. O `optional:`
faz o Boot não reclamar da ausência do arquivo — mas a falta de `DB_PASSWORD` derruba o startup mesmo assim,
porque essa é a única variável sem valor padrão.

## O que ainda não está aqui (e por quê)

Estas dependências estão **comentadas no `pom.xml`**, prontas para serem descomentadas quando o escopo avançar:

| Item | Quando habilitar |
|---|---|
| Spring Security | quando o fluxo de autenticação estiver definido |

Já **entraram**, e por isso saíram desta lista: JPA e o driver do PostgreSQL, com o banco em
`docker compose`. Duas ressalvas sobre esse estado:

- **A persistência não está ligada.** Não existe nenhuma `@Entity`; o `UserRepository` em uso é o
  `InMemoryUserRepository`, então o que a API grava se perde no restart e não chega ao PostgreSQL.
- **Não há ferramenta de migration.** Os scripts de `database/init/` só rodam quando o volume é criado,
  então hoje mudar o schema exige `docker compose down -v` e perder o banco local. Flyway continua
  pendente e vai precisar entrar antes de o schema começar a evoluir de verdade.

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

Sobre o `Dockerfile`: ele monta a imagem da aplicação e é usado pelo CI, mas **rodar essa imagem sozinha
não funciona para desenvolvimento local**. O `spring.datasource.url` aponta para `localhost`, e dentro do
container `localhost` é o próprio container, não o host onde o PostgreSQL está publicado. Enquanto a
aplicação não entrar no `docker-compose.yml` como serviço — com um `DB_HOST` apontando para `db` — o
caminho local é o da seção [Como rodar](#como-rodar): banco em container, aplicação na sua máquina.

## Banco de Dados com Docker

O projeto utiliza PostgreSQL 16 executado via Docker Compose para o ambiente local.

### Configuração

Crie um arquivo `.env` na raiz do projeto com base no `.env.example`.

Exemplo:

```env
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev
LOG_LEVEL=DEBUG
CORS_ALLOWED_ORIGINS=http://localhost:5173

DB_NAME=finup
DB_USER=finup_user
DB_PASSWORD=sua_senha_local
DB_PORT=5432
```

O arquivo `.env` não deve ser versionado, pois pode conter informações sensíveis.

### Subir o banco

```bash
docker compose up -d
```

### Verificar se o banco está funcionando

```bash
docker compose ps
```

O container do PostgreSQL deve aparecer em execução e com status `healthy`.

### Visualizar os logs do PostgreSQL

```bash
docker compose logs db
```

### Parar o ambiente

```bash
docker compose down
```

Os dados permanecem armazenados no volume Docker.

### Recriar o container

```bash
docker compose up -d --force-recreate
```

### Apagar o banco local e começar do zero

```bash
docker compose down -v
docker compose up -d
```

> Atenção: o comando `docker compose down -v` remove o volume do PostgreSQL e apaga os dados armazenados localmente.

### Configuração padrão do banco

- Banco: PostgreSQL 16
- Host: localhost
- Porta: 5432
- Nome do banco: finup
- Usuário: finup_user

A senha deve ser definida localmente através da variável `DB_PASSWORD`.

### Conexão com o backend

O Docker Compose utiliza as variáveis definidas no `.env`.

Ao executar o Spring Boot diretamente, o `.env` precisa existir na raiz: o `application.yml` o importa
via `spring.config.import`. `DB_NAME`, `DB_USER` e `DB_PORT` têm valor padrão no `application.yml`, mas
**`DB_PASSWORD` não tem** — sem ele a aplicação não sobe. O banco também precisa estar de pé
(`docker compose up -d`) antes de iniciar o backend, porque o JPA abre conexão durante o startup.

Nos dois casos a falha aparece como uma stack do Hibernate
(`Unable to determine Dialect without JDBC metadata`), e não como uma mensagem de configuração faltando.

A conexão local com o PostgreSQL utiliza o formato:

```text
jdbc:postgresql://localhost:5432/finup
```

## Inicialização e população do banco de dados

Os scripts SQL responsáveis pela criação e população do banco ficam em:

`database/init`

Eles são executados automaticamente pelo PostgreSQL durante a criação inicial do banco.

### Ordem de execução

1. `01-schema.sql` - cria a estrutura, as tabelas e os relacionamentos do banco.
2. `02-required-data.sql` - insere os dados obrigatórios da aplicação.
3. `03-test-data.sql` - insere dados fictícios para desenvolvimento e testes.

### Recriar e popular o banco novamente

Os scripts de inicialização são executados quando o PostgreSQL cria um novo volume.

Para remover o banco local e executar novamente todos os scripts:

```bash
docker compose down -v
docker compose up -d
```

Para acompanhar a execução dos scripts:

```bash
docker compose logs db
```

### Fluxo de inicialização

Ao criar o banco pela primeira vez, o processo ocorre na seguinte ordem:

1. O PostgreSQL é inicializado.
2. O arquivo `01-schema.sql` cria as tabelas e os relacionamentos.
3. O arquivo `02-required-data.sql` insere os dados obrigatórios.
4. O arquivo `03-test-data.sql` insere os dados fictícios de desenvolvimento e testes.
5. O banco fica disponível para utilização pela aplicação.
