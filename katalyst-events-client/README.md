# katalyst-events-client

**Public API for Event Operations**

## Purpose
High-level client API coordinating all event operations:
- EventClient: Main public interface
- PublishResult: Success/failure types with detailed status
- Retry policies with exponential backoff
- Interceptors for extensibility
- Remote event consumption
- DI/Koin integration

## Dependencies
- katalyst-events (DomainEvent)
- katalyst-events-bus (EventBus)
- katalyst-events-transport (EventSerializer, EventRouter)
- katalyst-messaging (Producer, Destination)
- Koin (dependency injection)

## Responsibilities
- Single coherent API for applications
- Coordinate local bus + remote transport
- Automatic retry on transient failures
- Detailed result reporting
- Extensibility via interceptors
- DI module and configuration

## What Applications Use
Applications only depend on EventClient - no direct bus, serializer, or router knowledge.

## Ready for Implementation
See `OPTIMAL_EVENTS_ARCHITECTURE_REDESIGN.md` for detailed specifications.
