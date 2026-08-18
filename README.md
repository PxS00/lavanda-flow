# Lavanda Flow

Sistema de estoque e produção para a Céu de Lavanda.

O Lavanda Flow nasce para resolver o controle de essências, insumos, lotes, validade e movimentações de estoque, com evolução planejada para fórmulas, produção automatizada, rastreabilidade e custos.

## Objetivos

- evitar compras duplicadas por falta de visibilidade do estoque;
- reduzir perdas por vencimento;
- registrar entradas, saídas e ajustes de estoque;
- controlar lotes e fornecedores;
- permitir evolução para fórmulas e produção;
- manter rastreabilidade entre insumos e produtos produzidos.

## Stack planejada

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

## Arquitetura

O backend será desenvolvido como um monólito modular, organizado por domínio e com fronteiras explícitas entre módulos.

Módulos previstos:

- `catalog`
- `inventory`
- `suppliers`
- `formulas`
- `production`
- `traceability`

A V1 começa apenas com os módulos necessários ao controle de estoque.

## Documentação

- [`docs/product/scope-v1.md`](docs/product/scope-v1.md)
- [`docs/domain/domain-model.md`](docs/domain/domain-model.md)
- [`docs/architecture/architecture.md`](docs/architecture/architecture.md)
- [`docs/architecture/data-model.md`](docs/architecture/data-model.md)

## Status

Projeto em fase inicial de definição de domínio, arquitetura e escopo.
