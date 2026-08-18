# Estrutura do backend Spring

O backend do Lavanda Flow segue **package by feature/domain** dentro de um **monólito modular** validado com Spring Modulith.

A estrutura não deve ser organizada globalmente apenas por camada técnica (`controller`, `service`, `repository`, `entity`). Cada módulo de negócio deve concentrar suas próprias regras, casos de uso e adapters.

## Estrutura inicial

```text
backend/
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/
    │   │       └── ceudelavanda/
    │   │           └── lavandaflow/
    │   │               ├── LavandaFlowApplication.java
    │   │               │
    │   │               ├── catalog/
    │   │               │   ├── domain/
    │   │               │   ├── application/
    │   │               │   └── infrastructure/
    │   │               │
    │   │               ├── inventory/
    │   │               │   ├── domain/
    │   │               │   ├── application/
    │   │               │   └── infrastructure/
    │   │               │
    │   │               ├── suppliers/
    │   │               │   ├── domain/
    │   │               │   ├── application/
    │   │               │   └── infrastructure/
    │   │               │
    │   │               └── shared/
    │   │                   ├── config/
    │   │                   ├── error/
    │   │                   └── security/
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       └── db/
    │           └── migration/
    │
    └── test/
        └── java/
            └── com/
                └── ceudelavanda/
                    └── lavandaflow/
                        ├── architecture/
                        ├── catalog/
                        ├── inventory/
                        └── suppliers/
```

## Estrutura interna de um módulo

Exemplo para `inventory`:

```text
inventory/
├── domain/
│   ├── Batch.java
│   ├── StockMovement.java
│   ├── MovementType.java
│   ├── StockPolicy.java
│   ├── BatchRepository.java
│   ├── StockMovementRepository.java
│   └── exception/
│       ├── InsufficientStockException.java
│       └── InvalidStockMovementException.java
│
├── application/
│   ├── RegisterStockEntry.java
│   ├── RegisterStockWithdrawal.java
│   ├── AdjustStock.java
│   ├── ListExpiringBatches.java
│   ├── command/
│   │   ├── RegisterStockEntryCommand.java
│   │   └── RegisterStockWithdrawalCommand.java
│   └── result/
│       └── StockMovementResult.java
│
└── infrastructure/
    ├── web/
    │   ├── InventoryController.java
    │   ├── request/
    │   └── response/
    │
    └── persistence/
        ├── JpaBatchRepository.java
        ├── JpaStockMovementRepository.java
        ├── entity/
        └── mapper/
```

Os nomes acima são referências estruturais. Não criar classes apenas para preencher a árvore. Cada artefato deve existir porque há um caso de uso ou uma necessidade técnica real.

## `domain`

Responsável por regras e conceitos do negócio.

Pode conter:

- aggregates;
- entities de domínio;
- value objects;
- enums de negócio;
- policies;
- domain services quando uma regra não pertence naturalmente a uma única entidade;
- interfaces de repository necessárias pelo domínio/aplicação;
- exceções de negócio.

### Regras

- não depender de controllers ou DTOs HTTP;
- não conhecer detalhes de JSON;
- evitar dependência direta de implementações técnicas;
- regras como saldo negativo, FEFO e validade devem estar aqui ou em serviços de aplicação apropriados, não no controller.

## `application`

Responsável por executar os casos de uso do módulo.

Exemplos:

```text
RegisterStockEntry
RegisterStockWithdrawal
AdjustStock
ListExpiringBatches
```

### Responsabilidades

- orquestrar objetos de domínio;
- controlar fronteiras transacionais;
- acessar portas/repositories necessários;
- publicar eventos de aplicação/domínio quando necessário;
- retornar resultados apropriados ao adapter chamador.

### Regras

- não conter conhecimento de HTTP;
- evitar regras de domínio complexas espalhadas por use cases;
- um caso de uso deve representar uma intenção de negócio clara.

## `infrastructure`

Implementa detalhes externos ao núcleo do módulo.

### `infrastructure/web`

Contém:

- REST controllers;
- request DTOs;
- response DTOs;
- mapeamento entre HTTP e aplicação.

Controller deve:

1. receber a requisição;
2. validar o boundary;
3. transformar em comando/query;
4. chamar o caso de uso;
5. devolver a resposta HTTP.

Controller não deve calcular estoque, selecionar FEFO ou alterar entidades diretamente.

### `infrastructure/persistence`

Contém detalhes JPA/PostgreSQL.

Pode conter:

- adapters de repository;
- entidades JPA quando o domínio for mantido separado da persistência;
- mappers;
- specifications/queries técnicas.

A decisão entre entidade de domínio separada da entidade JPA deve ser tomada pragmaticamente. Não duplicar modelos sem benefício claro, mas também não permitir que conveniências do ORM ditem as regras do domínio.

## Módulo `catalog`

Responsabilidades iniciais:

```text
catalog
├── cadastro do item
├── nome e descrição
├── categoria
├── unidade de medida
├── estado ativo/inativo
└── informações de classificação
```

Itens podem representar essência, insumo químico, base, embalagem ou outros materiais controlados.

## Módulo `inventory`

Responsabilidades iniciais:

```text
inventory
├── lotes
├── quantidade inicial
├── saldo atual
├── validade
├── entradas
├── saídas
├── ajustes
├── histórico de movimentações
├── FEFO
└── alertas de estoque/validade
```

`inventory` pode depender da API pública de `catalog`, mas não deve acessar classes internas do módulo diretamente.

## Módulo `suppliers`

Responsabilidades iniciais:

```text
suppliers
├── cadastro
├── identificação
├── contato básico
└── associação de origem dos lotes
```

Não transformar o módulo em CRM na V1.

## `shared`

Deve ser pequeno.

Usos permitidos incluem infraestrutura realmente transversal:

```text
shared/
├── config
├── error
└── security
```

Não mover regras de negócio para `shared` apenas porque mais de um módulo as utiliza. Primeiro avaliar ownership e API entre módulos.

## Módulos futuros

Quando aprovados no escopo:

```text
com.ceudelavanda.lavandaflow
├── catalog
├── inventory
├── suppliers
├── formulas
├── production
├── traceability
└── shared
```

### `formulas`

- fórmulas;
- ingredientes;
- versionamento de fórmula.

### `production`

- ordem de produção;
- lote produzido;
- consumo automático de materiais;
- geração de item/base produzida.

### `traceability`

- navegação entre lote de matéria-prima, produção intermediária e produto final;
- consultas de impacto de lote.

Esses módulos não devem ser criados fisicamente antes de entrarem no escopo aprovado.

## Dependências entre módulos

Direção inicial esperada:

```text
suppliers ─────┐
               ▼
catalog ───► inventory
```

A dependência deve ocorrer apenas através de APIs públicas do módulo.

Evitar:

```text
inventory -> catalog.infrastructure.persistence.*
```

Preferir:

```text
inventory -> catalog.<public API>
```

Spring Modulith deverá ser utilizado para verificar fronteiras e ciclos entre módulos.

## Eventos entre módulos

Não introduzir eventos apenas para evitar chamadas Java simples.

Usar eventos quando houver benefício real de desacoplamento, por exemplo futuramente:

```text
ProductionCompleted
        ↓
Inventory consumes materials
        ↓
Traceability records relationships
```

Na V1, interações síncronas simples são aceitáveis quando preservam as fronteiras dos módulos.

## Transações

O boundary transacional deve, em regra, estar no caso de uso da camada `application`.

Uma movimentação de estoque deve persistir de forma atômica:

```text
validar operação
      ↓
atualizar saldo do lote
      ↓
registrar stock_movement
      ↓
commit
```

Se qualquer etapa falhar, toda a operação deve sofrer rollback.

## Testes

A estrutura de testes deve espelhar os módulos de produção.

Exemplo:

```text
src/test/java/com/ceudelavanda/lavandaflow/
├── architecture/
│   └── ModularityTest.java
├── catalog/
├── inventory/
└── suppliers/
```

### Teste arquitetural mínimo

Deve existir teste que execute a verificação de módulos do Spring Modulith para detectar:

- ciclos;
- acesso indevido aos internals de outro módulo;
- violações das dependências permitidas.

## Convenções

- pacote base: `com.ceudelavanda.lavandaflow`;
- nomes de classes e código em inglês;
- documentação de negócio pode permanecer em português;
- interfaces devem representar contratos reais, não abstrações especulativas;
- evitar sufixo `Impl` quando um nome de adapter mais específico puder explicar seu papel;
- não expor entidade JPA diretamente na API;
- não criar camada `util` genérica para acumular responsabilidades indefinidas.
