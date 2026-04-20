# Arka — Architecture & Standards Reference

When creating or modifying specs, implementations, or any code in this monorepo, always consider the following normative documents as the source of truth:

## Normative Documents

### Code Patterns & Standards

#[[file:docs/06-patrones-y-estandares.md]]

### System Architecture Design

#[[file:docs/01-arquitectura.md]] #[[file:docs/02-microservicios.md]]

### Business Context

#[[file:docs/09-contexto-negocio.md]]

## Key Rules

- Use Scaffold Skill to generate properly modules with plugin commands
- All specs must align with the architecture defined in the design document
- All implementations must follow the patterns and conventions in 06-patrones-y-estandares.md
- Business context in 09-contexto-negocio.md defines the boundaries of each microservice
- When creating a new spec for any ms-\*, verify it aligns with the phase delivery strategy and bounded context responsibilities
- PostgreSQL services use R2DBC (reactive) except ms-reporter (JDBC + Virtual Threads)
- MongoDB services use Reactive Mongo drivers
- All inter-service communication follows the patterns defined: gRPC for sync, Kafka for async
- Kafka topics follow the one-topic-per-bounded-context convention
- Spring Profiles: `local` (default for IntelliJ) and `docker` (injected by Compose)

## Microservice-Specific Design (Specs)

The documents above define cross-cutting standards for the entire monorepo. For the internal design of each individual microservice (domain entities, use cases, DB schema, events, implementation tasks), always consult the spec files at `.kiro/specs/ms-<name>/`:

- `requirements.md` — Functional requirements, user stories, acceptance criteria
- `design.md` — Domain model, component architecture, sequence diagrams, data schema
- `tasks.md` — Implementation plan with traceable task list

When implementing a specific microservice, read its spec files first to understand the domain-specific decisions that complement the global standards above.
