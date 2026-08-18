# AGENTS.md

## Projeto

Lavanda Flow é um sistema de estoque e produção para a Céu de Lavanda.

A V1 é focada em estoque geral, lotes, movimentações, validade, fornecedores e alertas. Fórmulas, produção automatizada, custos e rastreabilidade completa são evoluções posteriores.

Antes de implementar qualquer funcionalidade, consulte:

- `docs/product/scope-v1.md`;
- `docs/domain/domain-model.md`;
- `docs/architecture/architecture.md`;
- `docs/architecture/data-model.md`.

## Stack oficial

### Frontend

- Angular 22
- TypeScript

### Backend

- Java 25 LTS
- Spring Boot 4.1
- Spring Modulith
- Spring Security
- Spring Data JPA
- Flyway

### Dados e infraestrutura

- PostgreSQL
- Docker
- Docker Compose
- GitHub Actions

### Testes

- JUnit 5
- Mockito
- Testcontainers

## Princípios arquiteturais

- modular monolith;
- package by feature/domain;
- módulos com fronteiras explícitas;
- regras de negócio fora de controllers;
- detalhes de infraestrutura não devem dominar o modelo de domínio;
- evitar microsserviços sem necessidade concreta;
- evitar abstrações especulativas.

## Backend

### Organização

Preferir:

```text
feature/
├── domain/
├── application/
└── infrastructure/
```

Evitar estrutura global baseada exclusivamente em:

```text
controller/
service/
repository/
entity/
```

### API

- utilizar DTOs nos boundaries HTTP;
- nunca expor entidades JPA diretamente;
- validar entradas;
- manter contratos REST consistentes;
- utilizar `/api/v1` como prefixo inicial;
- controllers devem ser finos.

### Domínio

- quantidades usam `BigDecimal`;
- nunca usar `double` ou `float` para estoque;
- movimentações de estoque devem ser auditáveis;
- não permitir saldo negativo;
- correções devem gerar movimentos de ajuste, não alteração destrutiva do histórico;
- respeitar FEFO quando aplicável;
- operações de saldo devem ser transacionais.

### Persistência

- PostgreSQL é a fonte de verdade;
- alterações de schema devem usar Flyway;
- não depender de `ddl-auto` para evolução de schema;
- constraints importantes devem existir também no banco;
- não usar H2 como substituto principal de PostgreSQL em testes de integração.

### Testes

Toda regra de negócio relevante deve possuir teste.

Prioridades:

- saldo;
- FEFO;
- validade;
- concorrência de movimentações;
- validações de quantidade;
- contratos principais da API.

Usar Testcontainers para integração com PostgreSQL.

## Frontend

- Angular deve permanecer desacoplado do backend por contrato REST;
- priorizar mobile-first;
- componentes devem ser pequenos e orientados à responsabilidade;
- lógica de acesso HTTP deve ficar fora de componentes de apresentação;
- usar Reactive Forms para formulários com regras relevantes;
- tratar estados de loading, erro e vazio;
- evitar adicionar bibliotecas quando Angular já fornece solução adequada.

## Segurança

- nunca versionar secrets;
- nunca armazenar senha em texto puro;
- validar payloads no backend;
- configurar CORS explicitamente;
- não registrar credenciais ou dados sensíveis em logs;
- aplicar princípio do menor privilégio.

## Qualidade

Priorizar:

- Clean Code;
- SOLID quando aplicável;
- nomes explícitos;
- métodos pequenos;
- baixa duplicação;
- regras de negócio testáveis;
- simplicidade sobre padrões desnecessários.

Não introduzir design patterns apenas para aumentar abstração.

## Git

Utilizar Conventional Commits.

Exemplos:

```text
feat: add inventory item registration
fix: prevent negative stock balance
docs: define inventory domain model
test: cover FEFO batch selection
refactor: isolate stock movement policy
chore: configure local postgres
```

Durante a fase inicial de documentação e configuração, alterações diretas na `main` podem ocorrer de forma intencional.

Quando o desenvolvimento funcional começar, preferir branches curtas, pull requests e squash merge.

## Escopo

Não implementar funcionalidades fora da V1 apenas porque a arquitetura prevê sua existência futura.

Antes de adicionar fórmula, produção, custos ou rastreabilidade completa, o escopo correspondente deve ser aprovado e documentado.

## Regra para agentes

Antes de alterar código:

1. leia documentação relevante;
2. identifique o módulo responsável;
3. preserve as invariantes de estoque;
4. implemente a menor mudança completa possível;
5. adicione ou atualize testes;
6. execute validações relevantes;
7. revise `git diff` antes de finalizar.

Se uma decisão estrutural não estiver documentada e puder afetar várias partes do sistema, não presuma silenciosamente. Registre ou proponha uma decisão arquitetural primeiro.
