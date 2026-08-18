# Dependências do projeto

Este documento define as dependências iniciais aprovadas para o Lavanda Flow.

A regra é simples: adicionar dependências somente quando elas resolvem uma necessidade concreta do produto ou da arquitetura. Evitar bibliotecas redundantes ou que dupliquem capacidades já fornecidas pelo Spring Boot ou Angular.

## Backend

### Runtime principal

| Dependência | Finalidade |
|---|---|
| `spring-boot-starter-webmvc` | API REST, Spring MVC e serialização HTTP |
| `spring-boot-starter-validation` | validação declarativa de DTOs e comandos |
| `spring-boot-starter-data-jpa` | persistência relacional com JPA/Hibernate |
| `spring-boot-starter-security` | autenticação, autorização e proteção da API |
| `spring-boot-starter-actuator` | health checks, métricas e endpoints operacionais |
| `io.micrometer:micrometer-registry-prometheus` | exposição de métricas Micrometer no formato Prometheus |
| `spring-modulith-starter-core` | modelagem, runtime e verificação do monólito modular |
| `spring-modulith-starter-insight` | informações arquiteturais via Actuator e observabilidade das interações entre módulos |
| `spring-modulith-starter-jpa` | infraestrutura para publicação persistente de eventos de aplicação via JPA |
| `spring-boot-starter-flyway` | integração oficial do Spring Boot 4 com Flyway |
| `flyway-database-postgresql` | suporte específico do Flyway para PostgreSQL |
| `postgresql` | driver JDBC do PostgreSQL |
| `org.projectlombok:lombok` | redução de boilerplate em classes adequadas |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | geração OpenAPI 3 e Swagger UI |

No Spring Boot 4, `spring-boot-starter-webmvc` substitui o antigo `spring-boot-starter-web` para aplicações Spring MVC. Da mesma forma, a integração com Flyway deve utilizar `spring-boot-starter-flyway` em vez de depender diretamente de `flyway-core`.

### Spring Modulith

O backend utiliza Spring Modulith para preservar e verificar as fronteiras do monólito modular.

Dependências aprovadas:

- `spring-modulith-starter-core`: fornece a infraestrutura principal do Modulith e inclui `spring-modulith-runtime` transitivamente;
- `spring-modulith-starter-insight`: integra o modelo modular com recursos production-ready, incluindo informações arquiteturais via Actuator e observabilidade de interações entre módulos;
- `spring-modulith-starter-jpa`: disponibiliza o Event Publication Registry persistido via JPA para fluxos que realmente precisem de eventos de aplicação confiáveis;
- `spring-modulith-starter-test`: fornece verificação das fronteiras e suporte a testes por módulo.

`spring-modulith-runtime` não deve ser declarado diretamente no `pom.xml`, pois já é fornecido transitivamente por `spring-modulith-starter-core`.

A presença de `spring-modulith-starter-jpa` não obriga o sistema a utilizar eventos para toda comunicação entre módulos. Na V1, chamadas síncronas continuam sendo apropriadas quando preservam as APIs públicas e as fronteiras modulares. Eventos devem ser introduzidos quando houver benefício concreto de desacoplamento, consistência ou evolução do fluxo, especialmente em cenários futuros envolvendo produção, estoque e rastreabilidade.

### Observabilidade

A V1 adota observabilidade simples e operacionalmente útil:

- logs via SLF4J/Logback fornecidos pelo Spring Boot;
- health checks e métricas via Actuator;
- instrumentação Micrometer fornecida pelo ecossistema Spring Boot;
- exportação de métricas via `micrometer-registry-prometheus`;
- informações arquiteturais e observabilidade entre módulos via Spring Modulith Insight.

Endpoint esperado em desenvolvimento, quando explicitamente exposto pela configuração de management endpoints:

```text
/actuator/prometheus
```

Tracing distribuído, OpenTelemetry, Jaeger e Grafana Tempo não fazem parte do bootstrap inicial. Podem ser adicionados caso o sistema passe a possuir integrações ou fluxos distribuídos que justifiquem esse custo operacional.

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

No bootstrap atual, utilizar `3.0.1` como pin temporário de compatibilidade com Spring Boot 4.1.0. As versões `3.0.2` e `3.0.3` possuem regressão conhecida na geração de valores default/schema que afeta exemplos do Swagger UI. O pin deve ser revisto quando uma versão posterior com a correção estiver disponível.

Dependência:

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
- `spring-boot-configuration-processor` deve ser configurado como annotation processor quando usado para gerar metadata de propriedades tipadas;
- propriedades customizadas devem preferir `@ConfigurationProperties` em vez de espalhar `@Value` pelo código;
- `spring-boot-docker-compose` é recurso de desenvolvimento local; o deploy não deve depender do ciclo de vida automático do Compose fornecido pelo Spring Boot.

### Testes

O Spring Boot 4 modularizou sua infraestrutura de testes. Os test starters específicos devem ser preferidos às dependências de teste isoladas; eles já trazem `spring-boot-starter-test` transitivamente.

| Dependência | Finalidade |
|---|---|
| `spring-boot-starter-webmvc-test` | testes da camada MVC e suporte a `@WebMvcTest` |
| `spring-boot-starter-validation-test` | infraestrutura de teste para Jakarta Validation |
| `spring-boot-starter-data-jpa-test` | testes de persistência JPA |
| `spring-boot-starter-flyway-test` | infraestrutura de testes da integração Flyway |
| `spring-boot-starter-security-test` | testes de autenticação e autorização com Spring Security |
| `spring-boot-starter-actuator-test` | testes dos recursos operacionais do Actuator |
| `spring-modulith-starter-test` | testes isolados por módulo e verificação das fronteiras |
| `spring-boot-testcontainers` | integração do Spring Boot com Testcontainers e service connections |
| `testcontainers-junit-jupiter` | integração Testcontainers com JUnit Jupiter |
| `testcontainers-postgresql` | PostgreSQL real em testes de integração |

Não declarar `spring-boot-starter-test` separadamente quando os test starters específicos já estiverem presentes.

### Dependências deliberadamente não adotadas no bootstrap

- MapStruct: será avaliado apenas se o volume de mapeamentos justificar a dependência.
- Redis: não existe requisito de cache ou estado distribuído na V1.
- Kafka/RabbitMQ: não existe requisito de mensageria externa na V1.
- H2: não deve substituir PostgreSQL nos testes de integração.
- Resilience4j: não existem integrações externas críticas que justifiquem circuit breaker na V1.
- bibliotecas JWT específicas: a estratégia de autenticação será decidida antes de adicionar implementação concreta de token.
- OpenTelemetry/tracing distribuído: não existe arquitetura distribuída na V1.

## Gerenciamento de versões Java

O backend usa:

```text
Java 25 LTS
Spring Boot 4.1.x
Spring Modulith 2.1.x
springdoc-openapi 3.0.1 (pin temporário)
```

As versões transitivas do ecossistema Spring, Micrometer, Flyway, PostgreSQL e Testcontainers devem ser gerenciadas preferencialmente pelo parent/BOM oficial do Spring Boot. Spring Modulith utiliza seu BOM oficial. Evitar pinagem manual sem necessidade.

Exemplo conceitual de `pom.xml`:

```xml
<properties>
    <java.version>25</java.version>
    <spring-modulith.version>2.1.0</spring-modulith.version>
    <springdoc.version>3.0.1</springdoc.version>
</properties>
```

Não copiar versões individuais de starters Spring Boot, Micrometer ou dependências gerenciadas quando o parent/BOM já fizer esse gerenciamento.

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

O frontend será criado pelo Angular CLI 22 e deve permanecer próximo do ecossistema oficial Angular.

### Runtime principal

| Pacote | Finalidade |
|---|---|
| `@angular/core` | núcleo do framework, DI e Signals |
| `@angular/common` | recursos comuns; `HttpClient` é utilizado via `@angular/common/http` |
| `@angular/router` | navegação SPA, lazy loading e guards |
| `@angular/forms` | Reactive Forms; Signal Forms não serão adotados inicialmente |
| `@angular/platform-browser` | bootstrap da aplicação no navegador |
| `rxjs` | composição de fluxos assíncronos quando necessário |
| `@angular/material` | design system e componentes da aplicação administrativa |
| `@angular/cdk` | primitives e infraestrutura utilizada pelo Angular Material |

Regras:

- Signals são parte do Angular e não exigem pacote adicional;
- `HttpClient` deve ser usado em vez de Axios ou outro cliente HTTP paralelo;
- Reactive Forms permanecem como abordagem padrão para formulários da V1;
- RxJS deve ser usado quando houver composição assíncrona real, sem transformar todo estado local em Observable por padrão;
- Angular Material será instalado pelo schematic oficial, mantendo versões compatíveis com Angular 22.

### Desenvolvimento, lint e testes

| Pacote/Ferramenta | Finalidade |
|---|---|
| `@angular/cli` | geração, build, serve e manutenção do workspace |
| `@angular/compiler-cli` | compilação Angular |
| `typescript` | linguagem do frontend; versão compatível gerenciada pelo Angular CLI |
| `vitest` | runner padrão de testes unitários em novos projetos Angular CLI |
| `jsdom` | emulação de DOM usada pelo setup padrão de testes em Node.js |
| `angular-eslint` / `@angular-eslint/*` | lint de TypeScript e templates Angular com ESLint |
| `eslint` | motor de lint, usando flat config |

O Angular CLI atual já prepara novos projetos para Vitest e `jsdom`; não adicionar runners paralelos sem necessidade.

O lint deverá ser configurado com:

```bash
ng add angular-eslint
```

Angular ESLint deve acompanhar o major do Angular CLI e usar configuração flat (`eslint.config.js`).

### Angular Material

Instalação:

```bash
ng add @angular/material
```

Não adotar `@angular/animations` em código novo. A API legada de animations está deprecated; novas animações devem preferir CSS e os mecanismos `animate.enter` / `animate.leave` suportados pelo Angular.

### Dependências futuras opcionais

#### PWA

Não é requisito para o primeiro bootstrap, mas é evolução prevista:

```bash
ng add @angular/pwa
```

Isso adicionará service worker e manifest quando o modo instalável/offline realmente entrar no escopo.

### Bibliotecas que não devem ser adicionadas inicialmente

- NgRx: Signals e serviços são suficientes para o estado esperado na V1; avaliar somente diante de complexidade real.
- Axios: usar `HttpClient` do Angular.
- bibliotecas externas de forms: usar Reactive Forms.
- bibliotecas externas de routing: usar Angular Router.
- Tailwind: não será dependência inicial enquanto Angular Material for o design system principal.
- `@angular/animations`: deprecated para código novo.
- bibliotecas de datas: `Date`, `Intl` e recursos Angular são suficientes até existir requisito mais complexo.
- bibliotecas de gráficos: não existe dashboard analítico que justifique dependência adicional na V1.

## Política de atualização

- manter versões compatíveis com Angular 22 e Spring Boot 4.1;
- manter `angular-eslint` no mesmo major do Angular CLI;
- evitar `latest` em arquivos de automação e imagens de produção;
- atualizações devem passar por build, lint e testes;
- dependências de segurança devem receber prioridade;
- uma nova biblioteca relevante deve ter justificativa técnica clara.
