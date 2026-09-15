# FinUp Score — regra de cálculo

> Define a fórmula usada por `FinUpScoreCalculator` para preencher `users.fin_up_score`. Qualquer
> mudança de peso/fórmula neste documento precisa ser replicada no código, e vice-versa — o teste
> `FinUpScoreCalculatorTest` (com os exemplos da seção 6) e o `InMemoryFinUpScoreDataProviderTest`
> existem justamente para travar essa correspondência.

## 1. Escala

Inteiro de **0 a 1000**, quanto maior melhor. Escolhida por analogia com score de crédito (ex.
Serasa), familiar ao usuário brasileiro, e compatível com o valor fictício (`650`) já usado no
seed de teste do banco (`database/init/03-test-data.sql`) — aquele valor é só um placeholder do
fixture, não foi calculado por esta fórmula.

## 2. Dado obrigatório (gate global)

O cálculo exige `users.monthly_income > 0`. Quase todo pilar normaliza um valor pela renda mensal;
sem ela não há base para nenhuma conta. Se a renda estiver ausente, zerada ou negativa, o resultado
é **dado insuficiente**: `users.fin_up_score` **não é sobrescrito** (permanece como estava, `null`
se nunca calculado). Não existe "score parcial" nesse caso.

## 3. Pilares e pesos

| # | Pilar | Peso | Tabelas/colunas de origem |
|---|-------|-----:|-----------------|
| 1 | Equilíbrio orçamentário (renda × gastos) | 350 | `users.monthly_income`, `transactions` (30 dias), `user_financial_profiles.monthly_expenses_estimate`, `.income_expense_relation` |
| 2 | Endividamento | 250 | `debts.total_amount`, `.paid_amount`, `.status` |
| 3 | Reserva / investimentos | 200 | `investments.current_amount`, `user_financial_profiles.has_investments`, `.approximate_invested_amount` |
| 4 | Metas financeiras | 100 | `goals.target_amount`, `.current_amount`, `.status` |
| 5 | Hábito financeiro autodeclarado | 100 | `user_financial_profiles.spending_habit` |

`FinUpScore = pilar1 + pilar2 + pilar3 + pilar4 + pilar5`, arredondado uma única vez no final (cada
pilar já é limitado ao seu próprio intervalo, então a soma nunca sai de 0–1000).

### 3.1 Pilar 1 — Equilíbrio orçamentário (0–350)

```
savingsRate = (renda - gastos) / renda
score1 = 350 * (clamp(savingsRate, -0.30, 0.30) + 0.30) / 0.60
```

Fonte de "gastos", nesta ordem de prioridade:

1. Soma de `transactions` do tipo `EXPENSE` dos últimos 30 dias (dado mais objetivo e atual).
2. `user_financial_profiles.monthly_expenses_estimate`, se não houver histórico de transação
   suficiente.
3. `user_financial_profiles.income_expense_relation`, convertida numa taxa assumida, se nenhuma
   das anteriores existir: `SPENDS_LESS` → +0,25, `BALANCED` → 0, `SPENDS_MORE` → -0,25.
4. Se nada disso existir, o pilar usa o ponto neutro (175 = metade de 350).

Poupar 30% ou mais da renda satura a nota máxima; gastar 30% a mais que a renda satura a nota
mínima (zero) — faixa escolhida para que "gastar exatamente a renda" (savingsRate = 0) caia no meio
da escala (175), nem bom nem ruim.

### 3.2 Pilar 2 — Endividamento (0–250)

```
saldoDevedor = soma(total_amount - paid_amount) das dívidas com status ativo ou atrasado
ratio = clamp(saldoDevedor / (rendaMensal * 12), 0, 1)
score2 = 250 * (1 - ratio)
se alguma dívida tiver status = LATE: score2 = max(0, score2 - 75)
```

Sem dívida nenhuma, `ratio = 0` e o pilar atinge a nota máxima — ausência de dívida é um resultado
real e positivo, não "dado faltando". A penalidade de atraso é fixa (75 pontos) e independente do
valor devido: pesa o comportamento de pagamento, não só o tamanho da dívida.

### 3.3 Pilar 3 — Reserva / investimentos (0–200)

```
valorInvestido = soma(investments.current_amount); se não houver linhas em investments mas
                 user_financial_profiles.has_investments = true, usa .approximate_invested_amount
mesesCobertos = valorInvestido / rendaMensal
score3 = 200 * clamp(mesesCobertos / 6, 0, 1)
```

O teto de 6 meses segue a referência clássica de reserva de emergência. Sem investimento algum,
`valorInvestido = 0` e o pilar zera — de novo, um resultado real, não uma lacuna de dado.

**Não usados nesta versão:** `user_financial_profiles.investment_experience_time` e
`user_investment_interests` são coletados mas não entram na fórmula — são indicadores de perfil de
investidor, não de saúde financeira atual. Candidatos a uma versão futura (ex.: pequeno bônus por
experiência), registrados aqui para não serem esquecidos.

### 3.4 Pilar 4 — Metas financeiras (0–100)

```
para cada goal com status IN_PROGRESS: progresso = min(current_amount / target_amount, 1)
para cada goal com status COMPLETED: progresso = 1
goals com status CANCELLED são ignoradas
score4 = 100 * média(progresso)
```

Sem nenhuma meta elegível (nenhuma meta cadastrada, ou só canceladas), o pilar usa o ponto neutro
(50) — não ter meta não é penalizado.

### 3.5 Pilar 5 — Hábito financeiro autodeclarado (0–100)

| `spending_habit` | Pontos |
|---|---:|
| `CONTROLLED` | 100 |
| `MODERATE` | 60 |
| `IMPULSIVE` | 20 |
| *(não informado)* | 60 (neutro, igual a `MODERATE`) |

**Não usado nesta versão:** `users.financial_profile` (conservador/moderado/arrojado) reflete
apetite a risco de investimento, não saúde financeira — um perfil "arrojado" não é pior que um
"conservador". Por isso não entra na fórmula, mesmo `model/User.java` já modelando essa coluna
(usada em `PATCH /api/v1/users/me/additional-info`, fora do escopo do FinUp Score): nada no v1 do
FinUp Score depende dela.

Também não usados, pelo mesmo motivo de não terem relação direta com saúde financeira: `categories`
e `payment_methods` (metadados de classificação/meio de pagamento, não de volume ou comportamento).

## 4. Quando recalcular

Disparado sob demanda por `FinUpScoreService.recalculate(identity)`, exposto via:

```
POST /api/v1/users/me/finup-score/recalculate
```

O usuário é sempre o dono da identidade autenticada (resolvida por
`AuthenticatedIdentityResolver`, hoje mockada pelos headers `X-Mock-Cognito-Sub`,
`X-Mock-Cognito-Email` e `X-Mock-Cognito-Name`) — nenhum endpoint recebe um id de usuário do
cliente.

Gatilhos pretendidos (a implementar quando os respectivos fluxos existirem, hoje fora do escopo
desta tarefa): atualização de renda/perfil financeiro, e criação/edição/exclusão de transação,
dívida, meta ou investimento. Nenhum desses endpoints de escrita existe ainda no código — quando
existirem, devem chamar este mesmo `FinUpScoreService.recalculate`. Não há recálculo agendado
(cron) nesta versão.

## 5. Estado atual da persistência (importante)

JPA e PostgreSQL já estão integrados ao projeto: `User` é `@Entity`, mapeado 1:1 na tabela `users`
(`cognito_id`, `monthly_income`, `fin_up_score` etc.), e o schema completo — incluindo
`user_financial_profiles`, `transactions`, `debts`, `goals` e `investments` — já existe em
`database/init/`. Não há mais `InMemoryUserRepository`: `UserRepository` é um `JpaRepository` de
verdade.

O que falta é só a leitura dessas 5 tabelas para o FinUp Score: as entidades JPA de dívida, meta e
investimento ainda não existem (escopo de tarefa futura), então `FinUpScoreDataProvider` continua
sendo uma porta (interface) com uma única implementação mock em memória
(`InMemoryFinUpScoreDataProvider`), que devolve fixtures fixos para os 3 usuários de demonstração
semeados em `database/init/03-test-data.sql` e "sem dado nenhum" para qualquer outro usuário. Quando
essas entidades existirem, basta escrever um novo adapter que implemente `FinUpScoreDataProvider`
com consultas reais — `FinUpScoreCalculator` e `FinUpScoreService` não mudam.

## 6. Exemplos para validação manual

| Exemplo | Renda | Gastos | Dívida ativa | Investido | Meta | Hábito | Pilares (1 / 2 / 3 / 4 / 5) | Total |
|---|---:|---|---|---|---|---|---|---:|
| A — fallback de estimativa | 5000 | 3000 (estimativa; sem histórico de transação suficiente) | 2000, em dia | 2150 | 1500/5000 em andamento | MODERATE | 350 / 241,67 / 14,33 / 30 / 60 | **696** |
| B — sem renda cadastrada | — | — | — | — | — | — | — | `INSUFFICIENT_DATA` |
| C — usuário saudável | 8000 | 5000 (estimativa) | nenhuma | 50000 | nenhuma | CONTROLLED | 350 / 250 / 200 / 50 / 100 | **950** |
| D — usuário em dificuldade | 3000 | 3900 (estimativa) | 20000, com atraso | 0 | 1 cancelada (ignorada) | IMPULSIVE | 0 / 36,11 / 0 / 50 / 20 | **106** |

### Conta por extenso

**Exemplo A** (reproduz o seed de teste `03-test-data.sql`, exceto pelo Score em si, que é
recalculado por esta fórmula, não copiado do fixture):
- `savingsRate = (5000 - 3000) / 5000 = 0,40` → clamp em `0,30` → `score1 = 350 * (0,30+0,30)/0,60 = 350`
- `ratio = 2000 / (5000*12) = 0,0333` → `score2 = 250 * (1 - 0,0333) = 241,67` (sem atraso)
- `mesesCobertos = 2150 / 5000 = 0,43` → `score3 = 200 * (0,43/6) = 14,33`
- `score4 = 100 * (1500/5000) = 30`
- `score5 = 60` (`MODERATE`)
- **Total = 350 + 241,67 + 14,33 + 30 + 60 = 696**

**Exemplo B:** `users.monthly_income` nulo → `FinUpScoreCalculator` devolve
`InsufficientData("Renda mensal (Users.MonthlyIncome) nao informada ou invalida.")` antes de
calcular qualquer pilar.

**Exemplo C:**
- `savingsRate = (8000-5000)/8000 = 0,375` → clamp em `0,30` → `score1 = 350`
- sem dívida → `ratio = 0` → `score2 = 250`
- `mesesCobertos = 50000/8000 = 6,25` → clamp em `1` (após dividir por 6) → `score3 = 200`
- sem meta elegível → `score4 = 50` (neutro)
- `score5 = 100` (`CONTROLLED`)
- **Total = 350 + 250 + 200 + 50 + 100 = 950**

**Exemplo D:**
- `savingsRate = (3000-3900)/3000 = -0,30` → já no limite → `score1 = 0`
- `ratio = 20000/(3000*12) = 0,5556` → `score2 = 250*(1-0,5556) = 111,11`; há dívida em atraso →
  `score2 = max(0, 111,11 - 75) = 36,11`
- sem investimento → `score3 = 0`
- única meta está `CANCELLED` (ignorada) → sem meta elegível → `score4 = 50` (neutro)
- `score5 = 20` (`IMPULSIVE`)
- **Total = 0 + 36,11 + 0 + 50 + 20 = 106,11 → arredonda para 106**

Estes 4 casos são exatamente os usados em `FinUpScoreCalculatorTest` e nos fixtures de
`InMemoryFinUpScoreDataProvider` (Exemplos A/C/D, ligados via e-mail aos usuários de demonstração
semeados em `database/init/03-test-data.sql`). Para testar pelo Swagger, chame
`POST /api/v1/users/me/finup-score/recalculate` com o header `X-Mock-Cognito-Sub` igual ao
`cognito_id` do usuário desejado (ex.: `mock-sub-exemplo-a`).
