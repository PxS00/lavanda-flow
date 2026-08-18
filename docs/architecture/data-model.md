# Modelo de Dados

## Objetivo

Este documento descreve o modelo relacional inicial da V1. Ele é deliberadamente simples e deve evoluir junto com o domínio antes da implementação física definitiva.

## ERD inicial

```text
supplier
   │
   │ 1:N
   ▼
inventory_batch
   ▲
   │ N:1
   │
inventory_item
   │
   │ 1:N
   ▼
inventory_batch
   │
   │ 1:N
   ▼
stock_movement
```

## Tabelas

### inventory_item

Representa um item controlado no estoque.

Campos previstos:

```text
id                  UUID / BIGINT
name                VARCHAR
category            VARCHAR
default_unit         VARCHAR
minimum_stock       NUMERIC nullable
active              BOOLEAN
notes               TEXT nullable
created_at          TIMESTAMPTZ
updated_at          TIMESTAMPTZ
```

Restrições iniciais:

- `name` obrigatório;
- `category` obrigatório;
- `default_unit` obrigatório;
- `minimum_stock >= 0` quando informado.

### supplier

Representa um fornecedor.

```text
id                  UUID / BIGINT
name                VARCHAR
identifier          VARCHAR nullable
contact             VARCHAR nullable
notes               TEXT nullable
active              BOOLEAN
created_at          TIMESTAMPTZ
updated_at          TIMESTAMPTZ
```

### inventory_batch

Representa um lote físico de um item.

```text
id                  UUID / BIGINT
inventory_item_id   FK -> inventory_item
supplier_id         FK -> supplier nullable
lot_code            VARCHAR nullable
initial_quantity    NUMERIC
current_quantity    NUMERIC
received_at         DATE
expires_at          DATE nullable
notes               TEXT nullable
created_at          TIMESTAMPTZ
updated_at          TIMESTAMPTZ
version             BIGINT
```

Restrições iniciais:

- `inventory_item_id` obrigatório;
- `initial_quantity > 0`;
- `current_quantity >= 0`;
- lote vencido pode permanecer registrado, mas não deve ser selecionado automaticamente para consumo;
- `version` pode ser utilizado para controle otimista de concorrência.

`current_quantity` pode ser mantido como saldo materializado para eficiência, desde que toda alteração seja acompanhada por `stock_movement` na mesma transação.

O histórico de movimentações permanece a trilha de auditoria da quantidade.

### stock_movement

Representa uma alteração de estoque.

```text
id                  UUID / BIGINT
batch_id            FK -> inventory_batch
movement_type       VARCHAR
quantity            NUMERIC
reason              TEXT nullable
occurred_at         TIMESTAMPTZ
created_at          TIMESTAMPTZ
```

Tipos iniciais:

```text
ENTRY
CONSUMPTION
ADJUSTMENT_IN
ADJUSTMENT_OUT
LOSS
EXPIRED_DISPOSAL
```

Restrições:

- `batch_id` obrigatório;
- `movement_type` obrigatório;
- `quantity > 0`;
- `occurred_at` obrigatório.

## Sobre saldo calculado versus saldo materializado

Existem duas alternativas principais.

### Saldo derivado exclusivamente de movimentações

Vantagens:

- uma única fonte histórica;
- menor risco de divergência entre saldo e movimentos.

Desvantagens:

- consultas de saldo exigem agregação recorrente;
- lógica de concorrência pode ficar menos direta.

### Saldo materializado no lote + histórico de movimentações

Vantagens:

- consulta rápida;
- validação de saldo disponível simples;
- controle de concorrência explícito sobre o lote.

Desvantagens:

- exige garantir atomicidade entre atualização de saldo e criação da movimentação.

### Decisão inicial

A proposta inicial da V1 é utilizar `current_quantity` em `inventory_batch` e persistir toda alteração também em `stock_movement`, obrigatoriamente dentro da mesma transação.

Essa decisão deve ser registrada em ADR antes da implementação definitiva.

## Unidade de medida

A unidade padrão pertence ao `inventory_item`.

Na V1, um lote deve utilizar a mesma unidade definida para seu item. Conversões automáticas entre litro/mililitro ou quilograma/grama podem ser introduzidas posteriormente caso tragam benefício real.

Quantidades devem usar `NUMERIC`, nunca tipos de ponto flutuante.

## Índices previstos

Avaliar na implementação:

```text
inventory_item(name)
inventory_item(category)
inventory_batch(inventory_item_id)
inventory_batch(expires_at)
inventory_batch(supplier_id)
stock_movement(batch_id, occurred_at)
```

Índices devem ser confirmados com os padrões reais de consulta e não criados indiscriminadamente.

## Exclusão

Preferir desativação para itens e fornecedores com histórico.

Lotes e movimentações utilizados operacionalmente não devem ser removidos apenas para "limpar" o sistema, pois isso quebra rastreabilidade.

## Evolução futura

Entidades futuras esperadas:

```text
formula
formula_version
formula_component
production_order
production_batch
production_consumption
finished_product
```

O modelo futuro deverá permitir relacionar o lote consumido em uma produção ao lote gerado como saída, formando a cadeia de rastreabilidade.
