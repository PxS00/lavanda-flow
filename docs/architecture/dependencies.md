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
| `org.projectlombok:lombok` | redução de boilerplate em classes adequadas |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | geração OpenAPI 3 e Swagger UI |

### Lombok

Lombok está aprovado no projeto, mas deve ser usado de forma controlada.

Uso recomendado:

- `@Getter` e `@Setter` quando realmente necessários;
- `@RequiredArgsConstructor` para injeção por construtor;
- `@Builder` em DTOs/resultados quando melhorar legibilidade;
- `@Slf4j` em classes que realmente precisam de logging.

Evitar:

- `@Data` em entidades JPA e agregados de domínio;
- `@EqualsAndHashCode` automático em entidades JPA sem avaliar identidade e proxies;
- `@ToString` automático sobre relacionamentos JPA;
- setters indiscriminados em objetos que possuem invariantes de negócio.

Lombok deve reduzir código repetitivo, não esconder regras de domínio.

### OpenAPI / Swagger

A API REST deverá publicar contrato OpenAPI e Swagger UI desde o início da implementação dos endpoints.

Para Spring Boot 4.x deve ser utilizada uma versão compatível da linha `springdoc-openapi 3.x`.

Dependência conceitual:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

Endpoints padrão esperados em desenvolvimento:

```text
/v3/api-docs
/v3/api-docs.yaml
/swagger-ui.html
```

O OpenAPI deve descrever contratos, códigos de resposta e autenticação de forma consistente. Evitar excesso de annotations quando o contrato já puder ser inferido corretamente.

### Desenvolvimento

| Dependência | Finalidade |
|---|---|
| `spring-boot-devtools` | reinício automático e melhorias de experiência local; somente desenvolvimento |
| `spring-boot-configuration-processor` | geração de metadata para `@ConfigurationProperties`, autocomplete e documentação de chaves de configuração |
| `spring-boot-docker-compose` | integração do Spring Boot com Docker Compose no ambiente local e criação automática de service connections quando suportado |

Regras:

- `spring-boot-devtools` não deve fazer parte de imagens ou runtime de produção;
- `spring-boot-configuration-processor` deve ser usado quando houver propriedades customizadas tipadas, preferencialmente com `@ConfigurationProperties` em vez de espalhar `@Value` pelo código;
- `spring-boot-docker-compose` é recurso de desenvolvimento local; o deploy não deve depender do ciclo de vida automático do Compose fornecido pelo Spring Boot.

Exemplos conceituais:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <optional>true</optional>
</dependency>
```

### Testes

| Dependência | Finalidade |
|---|---|
| `spring-boot-starter-test` | JUnit, Mockito, AssertJ e infraestrutura de testes Spring |
| `spring-security-test` | testes de autenticação e autorização |
| `spring-modulith-starter-test` | testes isolados por módulo e verificação das fronteiras |
| `testcontainers` | infraestrutura efêmera para testes de integração |
| `testcontainers-postgresql` | PostgreSQL real em testes |

### Dependências deliberadamente não adotadas no bootstrap

- MapStruct: será avaliado apenas se o volume de mapeamentos justificar a dependência.
- Redis: não existe requisito de cache ou estado distribuído na V1.
- Kafka/RabbitMQ: não existe requisito de mensageria externa na V1.
- H2: não deve substituir PostgreSQL nos testes de integração.
- Resilience4j: não existem integrações externas críticas que justifiquem circuit breaker na V1.
- bibliotecas JWT específicas: a estratégia de autenticação será decidida antes de adicionar implementação concreta de token.

## Gerenciamento de versões Java

O backend usa:

```text
Java 25 LTS
Spring Boot 4.1.x
Spring Modulith 2.1.x
springdoc-openapi 3.x
```

As versões transitivas do ecossistema Spring devem ser gerenciadas preferencialmente pelos BOMs oficiais, evitando pinagem manual sem necessidade.

Exemplo conceitual de `pom.xml`:

```xml
<properties>
    <java.version>25</java.version>
    <spring-modulith.version>2.1.0</spring-modulith.version>
    <springdoc.version>3.x</springdoc.version>
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

Ao gerar o projeto, substituir `3.x` pela última versão estável compatível com Spring Boot 4.1.x, validando a matriz oficial do springdoc.

Não copiar versões individuais de starters Spring Boot para cada dependência quando o parent/BOM já fizer esse gerenciamento.

## Ferramentas de qualidade do backend

Estas ferramentas são preferencialmente configuradas como plugins de build, não como dependências de runtime:

| Ferramenta | Finalidade |
|---|---|
| Maven Wrapper | build reproduzível sem depender de Maven global |
| JaCoCo | cobertura de testes |
| Spotless | formatação consistente de Java e arquivos auxiliares |
| Maven Enforcer | validar Java/Maven e regras de build |

A adoção de SonarQube/SonarCloud pode ocorrer posteriormente se houver benefício real para CI e análise contínua.

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
- bibliotecas de datas: `Date`, Intl e recursos Angular são suficientes até existir requisito mais complexo.
- bibliotecas de gráficos: não existe dashboard analítico que justifique dependência adicional na V1.

## Política de atualização

- manter versões compatíveis com Angular 22 e Spring Boot 4.1;
- evitar `latest` em arquivos de automação e imagens de produção;
- atualizações devem passar por build e testes;
- dependências de segurança devem receber prioridade;
- uma nova biblioteca relevante deve ter justificativa técnica clara.
