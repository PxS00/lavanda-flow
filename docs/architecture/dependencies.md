# Dependências do projeto

Este documento define as dependências iniciais aprovadas para o Lavanda Flow.

A regra é simples: adicionar dependências somente quando elas resolvem uma necessidade concreta do produto ou da arquitetura. Evitar bibliotecas redundantes ou que dupliquem capacidades já fornecidas pelo Spring Boot ou Angular.

## Backend

### Runtime principal

| Dependência | Finalidade |
|---|---|
| `spring-boot-starter-web` | API REST, MVC e serialização HTTP |
| `spring-boot-starter-validation` | validação declarativa de DTOs e comandos |
| `spring-boot-starter-data-jpa` | persistência relacional com JPA/Hibernate |
| `spring-boot-starter-security` | autenticação, autorização e proteção da API |
| `spring-boot-starter-actuator` | health checks e endpoints operacionais |
| `spring-modulith-starter-core` | modelagem e verificação do monólito modular |
| `flyway-core` | migrations de schema |
| `flyway-database-postgresql` | suporte específico do Flyway para PostgreSQL |
| `postgresql` | driver JDBC do PostgreSQL |

### Testes

| Dependência | Finalidade |
|---|---|
| `spring-boot-starter-test` | JUnit, Mockito, AssertJ e infraestrutura de testes Spring |
| `spring-security-test` | testes de autenticação e autorização |
| `spring-modulith-starter-test` | testes isolados por módulo e verificação das fronteiras |
| `testcontainers` | infraestrutura efêmera para testes de integração |
| `testcontainers-postgresql` | PostgreSQL real em testes |

### Dependências deliberadamente não adotadas no bootstrap

- Lombok: preferimos código explícito enquanto o domínio ainda está sendo estabilizado.
- MapStruct: será avaliado apenas se o volume de mapeamentos justificar a dependência.
- Redis: não existe requisito de cache ou estado distribuído na V1.
- Kafka/RabbitMQ: não existe requisito de mensageria externa na V1.
- H2: não deve substituir PostgreSQL nos testes de integração.
- Swagger/OpenAPI adicional: pode ser incluído quando os contratos HTTP começarem a ser implementados; não é necessário no bootstrap vazio.

## Gerenciamento de versões Java

O backend usa:

```text
Java 25 LTS
Spring Boot 4.1.x
Spring Modulith 2.1.x
```

As versões transitivas do ecossistema Spring devem ser gerenciadas preferencialmente pelos BOMs oficiais, evitando pinagem manual sem necessidade.

Exemplo conceitual de `pom.xml`:

```xml
<properties>
    <java.version>25</java.version>
    <spring-modulith.version>2.1.0</spring-modulith.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>${spring-modulith.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Não copiar versões individuais de starters Spring Boot para cada dependência quando o parent/BOM já fizer esse gerenciamento.

## Frontend

O frontend será criado pelo Angular CLI e deve permanecer próximo do ecossistema oficial Angular.

### Runtime principal

| Pacote | Finalidade |
|---|---|
| `@angular/core` | núcleo do framework |
| `@angular/common` | recursos comuns e cliente HTTP |
| `@angular/router` | navegação SPA e guards |
| `@angular/forms` | Reactive Forms |
| `@angular/platform-browser` | bootstrap da aplicação no navegador |
| `rxjs` | composição de fluxos assíncronos onde necessário |
| `@angular/material` | componentes de UI consistentes para aplicação administrativa |
| `@angular/cdk` | primitives e infraestrutura usada pelo Material |

Angular Signals devem ser usados para estado local e derivado quando fizer sentido. RxJS permanece apropriado para fluxos assíncronos, principalmente HTTP e composição de eventos.

### Desenvolvimento e testes

| Pacote | Finalidade |
|---|---|
| `@angular/cli` | geração, build, serve e manutenção do workspace |
| `@angular/compiler-cli` | compilação Angular |
| `typescript` | linguagem do frontend |
| `vitest` | runner padrão de testes unitários em novos projetos Angular |
| `jsdom` | simulação de DOM para testes executados em Node.js |

### Dependências futuras opcionais

#### PWA

Não é requisito para o primeiro bootstrap, mas é evolução prevista:

```bash
ng add @angular/pwa
```

Isso adicionará o suporte de service worker e manifest da aplicação.

#### Angular Material

A instalação deverá ser feita pelo schematic oficial:

```bash
ng add @angular/material
```

### Bibliotecas que não devem ser adicionadas inicialmente

- NgRx: Signals e serviços são suficientes para o estado esperado na V1; avaliar somente diante de complexidade real.
- Axios: usar o cliente HTTP do Angular.
- bibliotecas externas de forms: usar Reactive Forms.
- bibliotecas externas de routing: usar Angular Router.
- Tailwind: não será dependência inicial enquanto Angular Material for o design system principal.

## Política de atualização

- manter versões compatíveis com Angular 22 e Spring Boot 4.1;
- evitar `latest` em arquivos de automação e imagens de produção;
- atualizações devem passar por build e testes;
- dependências de segurança devem receber prioridade;
- uma nova biblioteca relevante deve ter justificativa técnica clara.
