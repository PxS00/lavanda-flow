# ADR 0004 — Adotar PostgreSQL e Flyway

- **Status:** Accepted
- **Data:** 2026-08-18

## Contexto

O domínio possui relações fortes entre itens, lotes, fornecedores e movimentações. Consistência transacional, constraints, histórico e consultas relacionais são requisitos centrais.

A evolução de schema precisa ser reproduzível entre desenvolvimento, testes e produção.

## Decisão

Adotar:

- PostgreSQL como banco relacional principal e fonte de verdade;
- Flyway como mecanismo oficial de migrations;
- Testcontainers com PostgreSQL para testes de integração relevantes.

Hibernate/JPA será usado para persistência, mas não será responsável por evoluir o schema em ambientes controlados.

## Consequências

### Positivas

- transações ACID;
- constraints e integridade relacional;
- boa aderência ao domínio;
- migrations versionadas e reproduzíveis;
- maior fidelidade dos testes de integração ao ambiente real.

### Negativas

- exige serviço PostgreSQL local ou container;
- migrations precisam ser mantidas com disciplina;
- alterações destrutivas de schema exigem planejamento.

## Alternativas consideradas

### SQLite

Rejeitado como banco principal por diferenças operacionais e de concorrência em relação ao ambiente pretendido.

### MongoDB

Rejeitado porque o domínio é fortemente relacional e não existe requisito que justifique banco documental.

### H2 em integração

Não será usado como substituto principal do PostgreSQL devido a diferenças de dialeto e comportamento que podem mascarar erros.
