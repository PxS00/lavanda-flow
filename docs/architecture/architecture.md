# Arquitetura

## Estilo arquitetural

O Lavanda Flow será desenvolvido como um **monólito modular**.

O objetivo é manter simplicidade operacional e de deploy, sem abrir mão de separação de responsabilidades e fronteiras de domínio claras.

Não serão utilizados microsserviços na fase inicial.

## Stack

### Frontend

- Angular 22;
- TypeScript;
- Angular Router;
- Signals;
- Reactive Forms;
- cliente HTTP nativo do Angular.

A aplicação deve ser mobile-first e preparada para evolução para PWA.

### Backend

- Java 25 LTS;
- Spring Boot 4.1;
- Spring Modulith;
- Spring Web;
- Spring Validation;
- Spring Data JPA;
- Spring Security;
- Flyway.

### Dados

- PostgreSQL.

### Testes

- JUnit 5;
- Mockito;
- Testcontainers.

### Infraestrutura

- Docker;
- Docker Compose;
- GitHub Actions.

## Visão de alto nível

```text
┌─────────────────────────────┐
│          Angular 22         │
│        Web / Mobile         │
└──────────────┬──────────────┘
               │ HTTPS / REST / JSON
               ▼
┌─────────────────────────────┐
│       Spring Boot 4.1       │
│        Java 25 LTS          │
│                             │
│      Modular Monolith       │
├─────────────────────────────┤
│ catalog                     │
│ inventory                   │
│ suppliers                   │
│                             │
│ future:                     │
│ formulas                    │
│ production                  │
│ traceability                │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│         PostgreSQL          │
│          Flyway             │
└─────────────────────────────┘
```

## Organização do backend

A estrutura deve seguir **package by feature/domain**, evitando organização global baseada apenas em camada técnica.

Exemplo:

```text
com.ceudelavanda.lavandaflow
├── catalog
│   ├── domain
│   ├── application
│   └── infrastructure
├── inventory
│   ├── domain
│   ├── application
│   └── infrastructure
├── suppliers
│   ├── domain
│   ├── application
│   └── infrastructure
└── shared
```

Não utilizar como estrutura principal:

```text
controller/
service/
repository/
entity/
```

Essa organização tende a espalhar um único caso de negócio por todo o projeto e enfraquecer fronteiras entre módulos.

## Responsabilidades das camadas internas

### Domain

Contém regras e conceitos de negócio.

Deve evitar dependência desnecessária de detalhes HTTP ou infraestrutura.

### Application

Orquestra casos de uso e transações.

Exemplos:

- registrar entrada;
- registrar consumo;
- ajustar estoque;
- consultar vencimentos.

### Infrastructure

Contém detalhes técnicos, como:

- controllers REST;
- persistência JPA;
- configurações;
- integrações externas.

## Fronteiras de módulos

### catalog

Responsável pelo cadastro e classificação dos itens controlados.

### inventory

Responsável por:

- lotes;
- saldo;
- movimentações;
- FEFO;
- validade;
- estoque mínimo.

### suppliers

Responsável pelo cadastro e consulta de fornecedores.

### future: formulas

Responsável por fórmulas e versionamento.

### future: production

Responsável por ordens e lotes de produção e consumo automático de materiais.

### future: traceability

Responsável por consultas de rastreabilidade entre lotes de matéria-prima e produtos produzidos.

## API

A comunicação frontend/backend será feita por REST sobre JSON.

Princípios:

- DTOs específicos nas fronteiras HTTP;
- entidades JPA não devem ser expostas diretamente;
- validação de entrada no boundary;
- respostas de erro consistentes;
- versionamento inicial sob `/api/v1`;
- regras de negócio não devem residir em controllers.

## Persistência

- PostgreSQL será a fonte de verdade;
- Flyway será a única forma suportada de evolução de schema em ambientes controlados;
- Hibernate não deve alterar schema automaticamente em produção;
- operações de estoque devem respeitar transações ACID;
- restrições de integridade devem existir também no banco quando aplicável.

## Quantidades

Quantidades físicas não devem utilizar ponto flutuante binário.

Backend:

```text
BigDecimal
```

Banco:

```text
NUMERIC / DECIMAL
```

A escala será definida conforme a necessidade do domínio durante a modelagem física.

## Concorrência e consistência

Movimentações concorrentes não podem produzir saldo negativo ou perda silenciosa de atualização.

A implementação deverá definir estratégia explícita de concorrência antes da funcionalidade de baixa de estoque ser considerada concluída.

Possibilidades incluem locking otimista ou pessimista conforme comportamento observado e requisitos de uso.

Não otimizar prematuramente, mas não ignorar consistência.

## Segurança

A V1 deverá possuir autenticação antes de exposição pública.

Princípios:

- senhas nunca armazenadas em texto puro;
- secrets fora do repositório;
- princípio do menor privilégio;
- validação de payloads;
- CORS explícito;
- logs sem dados sensíveis;
- dependências mantidas atualizadas.

Autorização complexa por papéis não é requisito inicial enquanto houver apenas poucos usuários administrativos.

## Observabilidade

Inicialmente:

- logs estruturados suficientes para diagnosticar operações;
- health checks;
- identificação de erros de negócio e infraestrutura.

Métricas e tracing distribuído não são prioridade na V1.

## Estratégia de testes

### Unitários

Cobrir regras de domínio, principalmente:

- saldo;
- FEFO;
- validade;
- movimentações;
- validações de quantidade.

### Integração

Usar Testcontainers para validar comportamento real com PostgreSQL.

Evitar depender apenas de banco H2 para testar persistência quando o ambiente real utiliza PostgreSQL.

### API

Cobrir principais contratos HTTP e cenários de erro.

## Evolução

O monólito modular deverá permitir aumento gradual de funcionalidades sem distribuição prematura.

A adoção de microsserviços só deverá ser considerada caso existam requisitos concretos de escala, deploy independente ou isolamento operacional que justifiquem seu custo.
