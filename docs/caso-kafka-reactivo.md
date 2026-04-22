---
sidebar_position: 13
title: "Referencia: Kafka Reactivo en Spring Boot 4.x"
---

# Estado de Kafka Reactivo en Spring Boot 4.x

## 1. El Problema Central: Fin de Vida de `reactor-kafka`

El proyecto oficial para la implementación puramente reactiva de Kafka, `reactor-kafka`, ha sido descontinuado. El equipo de Project Reactor y Spring anunció oficialmente el 20 de mayo de 2025 que no mantendrán más el proyecto debido a la evolución de su portafolio.

Como consecuencia directa:

- La versión 1.3 de `reactor-kafka` fue declarada como la última versión menor.
- El componente `ReactiveKafkaTemplate` (y similares) integrados históricamente en `spring-kafka` fueron marcados para su remoción.
- En la generación actual de **Spring Boot 4.x** (lanzada inicialmente en noviembre de 2025 y actualmente en su versión 4.0.4 lanzada en marzo de 2026), el soporte reactivo oficial dentro de _Spring for Apache Kafka 4.0_ ha sido removido de su núcleo.

## 2. Análisis de `reactive-commons` (Scaffold)

Existe la concepción de que implementaciones de terceros, como el _Scaffold_ basado en `reactive-commons`, han abandonado `reactor-kafka` en favor de un enfoque imperativo. Tras verificar los repositorios oficiales de artefactos:

- **Esta afirmación es inexacta.** El módulo `org.reactivecommons:async-kafka` (incluso en sus versiones modernas como la 7.1.0) **sigue declarando** a `io.projectreactor.kafka:reactor-kafka` como una dependencia central bajo el capó.
- Específicamente, las versiones recientes de `reactive-commons` utilizan `reactor-kafka` versión `1.3.25`.

## 3. Resolución de Errores: `NoSuchMethodError` en Kafka 8 / Kafka Clients

Durante la transición a entornos modernos se identificó un error crítico en el arranque del contenedor.

- **Síntoma:** Lanzamiento de la excepción `NoSuchMethodError` al invocar el constructor de `ConsumerRecord`.
- **Causa Raíz:** Este error se produce por un conflicto de compatibilidad binaria entre versiones. Las versiones más recientes de Spring Boot introducen versiones actualizadas de `kafka-clients` (las librerías nativas de Apache Kafka). La versión `1.3.23` de `reactor-kafka` no era compatible con la firma de los métodos de estas nuevas versiones del cliente.
- **Solución Técnica Aplicada:** Al actualizar explícitamente a `reactor-kafka` versión `1.3.25` (publicada a finales de 2025), se resolvió la brecha de compatibilidad con los nuevos clientes de Kafka, permitiendo que el proyecto compile y levante exitosamente.

## 4. Opciones Arquitectónicas Actuales (2026)

Para proyectos que se ejecutan sobre Spring Boot 4.x, las alternativas reales verificadas son:

1. **Downgrade a Spring Boot 3.3.x / 3.4.x:** Para proyectos en fase de aprendizaje o que no requieren las novedades del framework 4.0, utilizar Spring Boot 3.x garantiza estabilidad temporal con los clientes anteriores, aunque asumiendo deuda técnica futura.
2. **Utilizar `spring-kafka` Estándar + Wrappers Propios:** Al no existir `ReactiveKafkaTemplate`, la recomendación actual es utilizar el cliente de Spring estándar (que incluye soporte de hilos asíncronos nativo) y devolver promesas (`CompletableFuture` o envolver en un `Mono`) en la capa de negocio, sabiendo que el driver subyacente de red será bloqueante/imperativo.
3. **Continuar usando `reactive-commons` o `reactor-kafka` puro:** Aceptando que el soporte a nivel de la comunidad Open Source finalizará a corto plazo y que en el futuro los parches de seguridad dependerán exclusivamente de forks de la comunidad o contratos de soporte comercial.

---

### Evidencias y Fuentes Consultadas

- Blog Oficial de Spring (20 de mayo de 2025): Reactor Kafka Project Will Be Discontinued. (spring.io)
- Repositorio Central de Maven: io.projectreactor.kafka » reactor-kafka » 1.3.25. (mvnrepository.com)
- Repositorio Central de Maven: org.reactivecommons:async-kafka:7.1.0 (Muestra la dependencia de `reactor-kafka 1.3.25`). (central.sonatype.com)
- Blog Oficial de Spring (18 de noviembre de 2025): Spring for Apache Kafka 4.0.0 goes GA. (spring.io)
- Blog Oficial de Spring (19 de marzo de 2026): Spring Boot 4.0.4 available now. (spring.io)
- Foros y Comunidad de Desarrollo (Stack Overflow): Discusiones sobre incompatibilidad histórica entre versiones de `kafka-clients` y `reactor-kafka` generando `NoSuchMethodError`.
