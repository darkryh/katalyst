# katalyst-events-transport

**Event Serialization and Routing**

## Purpose
Transforms events for external transport:
- EventSerializer: Serialize DomainEvent to wire format
- EventDeserializer: Reconstruct DomainEvent from wire format
- EventRouter: Determine destination for each event
- Pre-built routing strategies (prefixed, package-based, custom)

## Dependencies
- katalyst-events (DomainEvent)
- katalyst-messaging (Destination, Message types)

## Responsibilities
- Format conversion (JSON, Protocol Buffers, etc.)
- Destination selection logic
- No async code, no network calls
- Fully testable in isolation

## Ready for Implementation
See `OPTIMAL_EVENTS_ARCHITECTURE_REDESIGN.md` for detailed specifications.
