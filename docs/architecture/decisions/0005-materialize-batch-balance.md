# ADR 0005 — Materializar saldo atual por lote

- **Status:** Accepted
- **Data:** 2026-08-18

## Contexto

O estoque precisa ser simples e rápido de consultar no uso diário, mas também auditável. Manter apenas um histórico de movimentos e recalcular o saldo integralmente a cada leitura aumentaria custo e complexidade de consulta. Manter apenas um saldo atual destruiria a trilha de auditoria.

## Decisão

Cada lote manterá um campo de saldo atual materializado, por exemplo `current_quantity`, e toda alteração desse saldo deverá gerar uma `StockMovement` correspondente **na mesma transação**.

Regras:

- nenhuma movimentação pode produzir saldo negativo;
- ajustes devem ser registrados como movimentos explícitos;
- o histórico de movimentos não deve ser alterado destrutivamente para corrigir saldo;
- operações concorrentes devem usar estratégia de locking definida e testada;
- divergências entre saldo e histórico devem ser tratadas como erro de consistência.

## Consequências

### Positivas

- consultas de estoque simples e rápidas;
- histórico completo de entradas, saídas e ajustes;
- melhor experiência para a interface administrativa;
- auditoria sem precisar recalcular o saldo em todas as leituras.

### Negativas

- existe dado derivado materializado que precisa permanecer consistente;
- exige transação atômica entre movimento e atualização do lote;
- concorrência precisa ser tratada explicitamente.

## Alternativas consideradas

### Event sourcing completo

Rejeitado por complexidade desnecessária para a escala e o domínio atuais.

### Apenas saldo atual

Rejeitado por ausência de auditoria.

### Calcular saldo sempre a partir das movimentações

Rejeitado como padrão de leitura por aumentar custo e complexidade sem benefício proporcional para este sistema.
