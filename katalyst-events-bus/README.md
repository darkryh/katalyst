# katalyst-events-bus

**In-Memory Event Bus and Handler Registry**

## Purpose
Local pub/sub implementation for in-memory event distribution:
- ApplicationEventBus: Core bus implementation
- EventHandlerRegistry: Injectable handler registry
- EventBusInterceptor: Extension points for logging, metrics, etc.
- EventTopology: Wiring handlers to the bus

## Dependencies
- katalyst-events (DomainEvent interfaces)

## Responsibilities
- Local event publishing to registered handlers
- Async execution with proper error handling
- Handler registration and discovery
- Interceptor support for cross-cutting concerns

## Ready for Implementation
See `OPTIMAL_EVENTS_ARCHITECTURE_REDESIGN.md` for detailed specifications.
