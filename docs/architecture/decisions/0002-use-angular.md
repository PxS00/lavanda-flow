# ADR 0002 — Adotar Angular no frontend

- **Status:** Accepted
- **Data:** 2026-08-18

## Contexto

O Lavanda Flow será uma aplicação administrativa autenticada, utilizada principalmente em desktop e celular. Não há requisito relevante de SEO, SSR ou conteúdo público indexável.

O projeto também será usado para ampliar repertório técnico além de React.

## Decisão

Adotar **Angular 22** com TypeScript como frontend principal.

Padrões iniciais:

- Angular Router;
- Reactive Forms;
- Signals para estado local/derivado;
- RxJS para fluxos assíncronos quando apropriado;
- Angular Material/CDK como design system inicial;
- Vitest para testes;
- angular-eslint para lint.

## Consequências

### Positivas

- framework estruturado e coeso;
- DI, routing e forms integrados ao ecossistema;
- boa aderência a aplicações administrativas;
- aprendizado técnico fora do ecossistema React;
- menor necessidade de bibliotecas externas para funcionalidades básicas.

### Negativas

- curva de aprendizado maior que React para quem ainda não domina Angular;
- maior rigidez estrutural;
- bundle e abstrações de framework mais amplos que soluções minimalistas.

## Alternativas consideradas

### React + Vite

Rejeitado porque já é uma stack conhecida e oferece menor ganho de aprendizado neste projeto.

### Next.js

Rejeitado porque SSR, SEO e Server Components não são requisitos importantes para o sistema administrativo e o aprendizado continuaria concentrado no ecossistema React.
