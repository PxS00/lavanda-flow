# AGENTS.md

## Projeto

Lavanda Flow é um sistema de estoque e produção para a Céu de Lavanda.

A V1 é focada em estoque geral, lotes, movimentações, validade, fornecedores e alertas. Fórmulas, produção automatizada, custos e rastreabilidade completa são evoluções posteriores.

Antes de implementar qualquer funcionalidade, consulte:

- `docs/product/scope-v1.md`;
- `docs/domain/domain-model.md`;
- `docs/architecture/architecture.md`;
- `docs/architecture/data-model.md`;
- `docs/architecture/dependencies.md`;
- `docs/architecture/backend-structure.md`.

## Stack oficial

### Frontend

- Angular 22;
- TypeScript;
- Angular Router;
- Reactive Forms;
- Signals;
- Angular Material/CDK;
- Vitest.

### Backend

- Java 25 LTS;
- Spring Boot 4.1;
- Spring Modulith;
- Spring Web;
- Spring Validation;
- Spring Security;
- Spring Data JPA;
- Flyway;
- PostgreSQL JDBC Driver;
- Lombok;
- springdoc-openapi / Swagger UI;
- Spring Boot Actuator;
- Spring Boot DevTools;
- Spring Configuration Processor;
- Spring Boot Docker Compose Support.

### Dados e infraestrutura

- PostgreSQL;
- Docker;
- Docker Compose;
- GitHub Actions.

### Testes

- JUnit 5;
- Mockito;
- Spring Boot Test;
- Spring Security Test;
- Spring Modulith Test;
- Testcontainers com PostgreSQL.

A lista aprovada e as dependências deliberadamente evitadas no bootstrap estão em `docs/architecture/dependencies.md`.

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

A referência completa está em `docs/architecture/backend-structure.md`.

Pacote base:

```text
com.ceudelavanda.lavandaflow
```

Estrutura principal:

```text
com.ceudelavanda.lavandaflow
├── catalog/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
├── inventory/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
├── suppliers/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
└── shared/
```

Evitar estrutura global baseada exclusivamente em:

```text
controller/
service/
repository/
entity/
```

Não criar módulos futuros (`formulas`, `production`, `traceability`) antes de entrarem no escopo aprovado.

### Dependências entre módulos

- acessar outros módulos apenas por APIs públicas;
- nunca importar internals de `infrastructure` de outro módulo;
- evitar ciclos;
- usar Spring Modulith para validar fronteiras;
- eventos devem ter motivação de negócio/arquitetura, não serem adicionados por padrão.

### API

- utilizar DTOs nos boundaries HTTP;
- nunca expor entidades JPA diretamente;
- validar entradas;
- manter contratos REST consistentes;
- utilizar `/api/v1` como prefixo inicial;
- controllers devem ser finos;
- publicar OpenAPI e Swagger UI para os contratos HTTP;
- documentar códigos de resposta, autenticação e erros relevantes;
- evitar annotations OpenAPI redundantes quando o contrato já for inferido corretamente.

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

### Dependências backend

O bootstrap deve partir do conjunto aprovado em `docs/architecture/dependencies.md`.

Lombok está aprovado, com uso controlado:

- preferir `@RequiredArgsConstructor` para injeção por construtor;
- `@Getter`, `@Setter`, `@Builder` e `@Slf4j` apenas quando agregarem clareza;
- evitar `@Data` em entidades JPA e agregados de domínio;
- não gerar `equals/hashCode/toString` indiscriminadamente em entidades JPA;
- Lombok não deve enfraquecer encapsulamento nem esconder invariantes.

Configuração e ambiente local:

- preferir `@ConfigurationProperties` para grupos de configuração tipados;
- manter `spring-boot-configuration-processor` habilitado para metadata e autocomplete;
- usar `spring-boot-docker-compose` apenas para facilitar o ambiente local;
- não depender de Docker Compose Support para comportamento de produção;
- DevTools deve permanecer restrito ao desenvolvimento.

Não adicionar inicialmente sem justificativa:

- MapStruct;
- Redis;
- Kafka/RabbitMQ;
- H2;
- Resilience4j;
- bibliotecas adicionais de estado/cache/mensageria.

Dependências Spring devem usar gerenciamento de versões por BOM/parent sempre que possível.

### Testes

Toda regra de negócio relevante deve possuir teste.

Prioridades:

- saldo;
- FEFO;
- validade;
- concorrência de movimentações;
- validações de quantidade;
- contratos principais da API;
- fronteiras do Spring Modulith.

Usar Testcontainers para integração com PostgreSQL.

## Frontend

- Angular deve permanecer desacoplado do backend por contrato REST;
- priorizar mobile-first;
- componentes devem ser pequenos e orientados à responsabilidade;
- lógica de acesso HTTP deve ficar fora de componentes de apresentação;
- usar Reactive Forms para formulários com regras relevantes;
- usar Signals para estado local/derivado quando apropriado;
- usar RxJS quando houver fluxo assíncrono/composição que justifique;
- tratar estados de loading, erro e vazio;
- Angular Material é o design system inicial;
- evitar adicionar bibliotecas quando Angular já fornece solução adequada.

Não adicionar inicialmente sem necessidade concreta:

- NgRx;
- Axios;
- bibliotecas externas de forms;
- bibliotecas externas de routing;
- Tailwind em paralelo ao Angular Material.

PWA é evolução prevista, não requisito do bootstrap inicial.

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
3. confirme se a dependência necessária já está aprovada;
4. preserve as invariantes de estoque;
5. implemente a menor mudança completa possível;
6. adicione ou atualize testes;
7. execute validações relevantes;
8. revise `git diff` antes de finalizar.

Se uma decisão estrutural não estiver documentada e puder afetar várias partes do sistema, não presuma silenciosamente. Registre ou proponha uma decisão arquitetural primeiro.
