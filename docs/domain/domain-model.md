# Modelo de Domínio

## Visão geral

O domínio inicial do Lavanda Flow representa estoque por item, lote e movimentação.

O saldo não deve ser tratado como um número isolado sem contexto. Todo item pode possuir múltiplos lotes e toda alteração relevante de quantidade deve ser auditável por meio de movimentações.

## Conceitos principais

### InventoryItem

Representa qualquer item controlado pelo estoque.

Exemplos:

- essência Good Girl;
- álcool de cereais;
- base para body splash;
- corante;
- frasco de 200 ml;
- válvula;
- rótulo.

Responsabilidades:

- identificar o item;
- definir sua categoria;
- definir sua unidade de medida padrão;
- definir se está ativo;
- opcionalmente definir estoque mínimo.

Um `InventoryItem` não representa uma compra específica.

### Batch

Representa um lote físico recebido ou existente de determinado item.

Um mesmo `InventoryItem` pode possuir vários `Batch`.

Exemplo:

```text
Essência Good Girl
├── lote GG-2026-01 — validade 10/2027
└── lote GG-2026-02 — validade 05/2028
```

Responsabilidades:

- relacionar item e fornecedor;
- guardar identificação do lote;
- guardar data de entrada;
- guardar validade;
- guardar quantidade inicial;
- permitir apuração da quantidade disponível.

### StockMovement

Representa qualquer alteração auditável de estoque.

Tipos iniciais:

- `ENTRY`;
- `CONSUMPTION`;
- `ADJUSTMENT_IN`;
- `ADJUSTMENT_OUT`;
- `LOSS`;
- `EXPIRED_DISPOSAL`.

Responsabilidades:

- identificar o lote afetado;
- registrar tipo e quantidade;
- registrar data/hora;
- registrar motivo quando necessário.

Movimentações são históricas e não devem ser apagadas para corrigir saldo. Correções devem gerar novas movimentações de ajuste.

### Supplier

Representa a origem comercial de um lote.

Responsabilidades:

- identificar fornecedor;
- armazenar dados básicos de contato quando necessários;
- permitir localizar quais lotes foram fornecidos por determinada empresa.

### Category

Classifica um item de estoque.

Categorias iniciais:

- `ESSENCE`;
- `CHEMICAL_INPUT`;
- `BASE`;
- `ALCOHOL`;
- `COLORANT`;
- `FIXATIVE`;
- `BOTTLE`;
- `VALVE`;
- `CAP`;
- `LABEL`;
- `PACKAGING`;
- `OTHER`.

A classificação não deve determinar toda a regra de negócio. Ela serve principalmente para organização, filtros e comportamentos específicos quando necessários.

### UnitOfMeasure

Representa a unidade usada para controlar quantidade.

Unidades iniciais:

- `MILLILITER`;
- `LITER`;
- `GRAM`;
- `KILOGRAM`;
- `UNIT`.

Quantidade deve usar representação decimal exata. No backend, utilizar `BigDecimal`; no banco, utilizar tipo `NUMERIC`/`DECIMAL` apropriado.

## Relacionamentos

```text
Supplier
   │
   │ 1:N
   ▼
 Batch ◄──────── InventoryItem
   │                 │
   │ 1:N             │ 1:N
   ▼                 ▼
StockMovement      Batch
```

Formalmente:

- um `InventoryItem` possui zero ou muitos `Batch`;
- um `Batch` pertence a exatamente um `InventoryItem`;
- um `Supplier` pode fornecer zero ou muitos `Batch`;
- um `Batch` pode possuir um fornecedor associado;
- um `Batch` possui muitas `StockMovement`;
- uma `StockMovement` pertence a exatamente um `Batch`.

## Invariantes iniciais

### Quantidade

- quantidades devem ser maiores que zero em movimentações normais;
- o estoque disponível nunca deve ficar negativo;
- `double` e `float` não devem ser usados para quantidades;
- unidade da movimentação deve ser compatível com a unidade do item/lote.

### Lotes

- compras diferentes do mesmo item não devem sobrescrever lotes anteriores;
- um lote vencido não deve ser selecionado automaticamente para consumo;
- código de lote pode ser desconhecido em dados legados, mas o modelo deve suportá-lo quando informado.

### Movimentações

- toda entrada ou retirada de estoque gera histórico;
- movimentação confirmada é imutável do ponto de vista de negócio;
- correções são realizadas através de nova movimentação de ajuste;
- operações que alteram saldo devem ser transacionais.

### Validade e FEFO

Quando houver validade informada:

1. lotes vencidos são excluídos da seleção normal de consumo;
2. entre lotes válidos, deve ser priorizado o de vencimento mais próximo;
3. lotes sem validade exigem regra explícita de ordenação e não devem invalidar os lotes com validade conhecida.

A estratégia é FEFO (*First Expired, First Out*).

## Casos de uso da V1

### Cadastrar item

Cria um novo item controlável pelo estoque.

### Registrar entrada

Cria ou seleciona um lote e registra entrada de quantidade.

### Registrar consumo

Retira uma quantidade do estoque, respeitando as regras de saldo e seleção de lote.

Quando a quantidade necessária exceder o saldo de um único lote, o caso de uso poderá consumir múltiplos lotes válidos seguindo FEFO.

### Registrar ajuste

Corrige divergências físicas através de movimentação explícita e justificativa.

### Consultar estoque

Retorna saldo agregado por item e detalhamento por lote.

### Consultar vencimentos

Retorna lotes vencidos e próximos do vencimento.

### Consultar histórico

Retorna todas as movimentações relacionadas a um item ou lote.

## Evolução futura

O modelo deve permitir incorporar posteriormente:

```text
Formula
FormulaVersion
ProductionOrder
ProductionBatch
ProductionConsumption
FinishedProduct
Cost
```

Fluxo futuro esperado:

```text
Matéria-prima / lote
        ↓
     Fórmula
        ↓
Produção da base
        ↓
   Lote da base
        ↓
Produção do produto
        ↓
Lote do produto final
```

A V1 não implementará esse fluxo, mas as decisões atuais não devem impedir sua introdução.
