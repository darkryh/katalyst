# Explanation

Explanation is for understanding. These pages discuss how Katalyst works under the hood and
why it is built the way it is — the reasoning, the trade-offs, and the alternatives that
were considered. Read them away from the keyboard; for step-by-step instructions, see the
[how-to guides](../how-to/index.md).

- **[Architecture & bootstrap lifecycle](architecture.md)** — how a Katalyst application
  starts: the discovery scan, dependency analysis and validation, ordered instantiation,
  configuration loading, and route registration.
- **[Design decisions](design-decisions.md)** — why discovery is interface-driven instead
  of annotation-driven, why bootstrap is explicit, why a bean engine must be chosen, and how
  Katalyst relates to Spring Boot.

