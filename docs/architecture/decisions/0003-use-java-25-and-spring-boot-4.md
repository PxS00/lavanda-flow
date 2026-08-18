# ADR 0003 — Adotar Java 25 LTS e Spring Boot 4.1

- **Status:** Accepted
- **Data:** 2026-08-18

## Contexto

O backend será greenfield e não possui restrições de compatibilidade com versões antigas da JVM. O projeto deve priorizar uma versão moderna, estável e de suporte prolongado.

## Decisão

Adotar:

- Java 25 LTS;
- Spring Boot 4.1.x;
- Maven com Maven Wrapper;
- Spring Modulith 2.1.x.

## Consequências

### Positivas

- base moderna e LTS;
- compatibilidade com o ecossistema Spring atual;
- possibilidade de usar recursos recentes da plataforma Java;
- bom valor de aprendizado sem usar uma release non-LTS.

### Negativas

- algumas bibliotecas de terceiros podem demorar mais a testar oficialmente versões muito recentes da JVM;
- ambientes locais e CI precisam disponibilizar Java 25.

## Alternativas consideradas

### Java 17

Rejeitado para greenfield por ser uma LTS anterior sem benefício concreto neste projeto.

### Java 21

Tecnologicamente excelente, mas não escolhido porque Java 25 é a LTS mais recente adotada para o projeto.

### Java 26

Rejeitado por ser non-LTS e exigir cadência de atualização mais agressiva.

### Gradle

Considerado válido, mas Maven foi mantido para reduzir variáveis de build e priorizar previsibilidade no ecossistema Spring.
