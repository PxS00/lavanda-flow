# ADR 0006 — Adotar métricas Prometheus antes de tracing distribuído

- **Status:** Accepted
- **Data:** 2026-08-18

## Contexto

A V1 será um monólito modular com uma aplicação Angular, uma API Spring Boot e PostgreSQL. Ainda não existe arquitetura distribuída que justifique tracing completo como requisito inicial, mas health checks e métricas operacionais são úteis desde o início.

## Decisão

Adotar na V1:

- Spring Boot Actuator;
- Micrometer;
- `micrometer-registry-prometheus`;
- endpoint `/actuator/prometheus` quando habilitado por configuração.

OpenTelemetry, OTLP, Jaeger ou Tempo não serão requisitos iniciais.

## Consequências

### Positivas

- baixo custo de adoção;
- métricas JVM, HTTP e datasource disponíveis cedo;
- integração futura simples com Prometheus/Grafana;
- evita infraestrutura de tracing sem necessidade real.

### Negativas

- não haverá tracing distribuído na V1;
- investigações complexas dependerão inicialmente de logs e métricas.

## Alternativas consideradas

### OpenTelemetry desde o bootstrap

Rejeitado porque o sistema ainda não é distribuído e o custo operacional adicional não se justifica.

### Apenas logs

Rejeitado porque métricas e health checks oferecem visibilidade operacional útil com baixo custo adicional.
