# ADR 0001 — Adotar monólito modular

- **Status:** Accepted
- **Data:** 2026-08-18

## Contexto

O Lavanda Flow inicia como um sistema administrativo de estoque e produção para a Céu de Lavanda. O domínio já possui fronteiras distintas, como catálogo, estoque e fornecedores, e deverá evoluir futuramente para fórmulas, produção e rastreabilidade.

Distribuir o sistema em microsserviços desde o início aumentaria custo operacional, complexidade de deploy, observabilidade, comunicação e consistência transacional sem requisito concreto que justifique essa distribuição.

## Decisão

Adotar um **monólito modular** com Spring Modulith, organizado por domínio/feature.

Módulos iniciais:

- `catalog`;
- `inventory`;
- `suppliers`;
- `shared` apenas para conceitos realmente transversais.

Módulos futuros só serão criados quando entrarem no escopo aprovado.

## Consequências

### Positivas

- deploy simples;
- transações locais e consistentes;
- menor custo operacional;
- fronteiras de domínio explícitas;
- possibilidade de validar dependências entre módulos com Spring Modulith;
- evolução futura sem distribuição prematura.

### Negativas

- exige disciplina para evitar acoplamento entre módulos;
- todos os módulos compartilham o mesmo processo e ciclo de deploy;
- crescimento indiscriminado pode degradar modularidade se as fronteiras não forem verificadas.

## Alternativas consideradas

### Microsserviços

Rejeitado por complexidade prematura e ausência de requisitos de escala/deploy independente.

### Monólito em camadas técnicas globais

Rejeitado porque estruturas globais como `controller/service/repository/entity` tendem a espalhar um único caso de negócio pelo projeto e enfraquecer fronteiras de domínio.
