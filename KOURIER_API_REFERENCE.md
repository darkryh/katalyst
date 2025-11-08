# Kourier AMQP Client - API Reference

## JAR Inspection Results

**Date**: November 7, 2025
**Kourier Version**: 0.2.8
**Main Modules**:
- `dev.kourier:amqp-client-jvm:0.2.8`
- `dev.kourier:amqp-client-robust-jvm:0.2.8`

---

## Package Structure

```
dev.kourier.amqp/                    (Core AMQP protocol)
├── AMQPException (sealed class)
├── AMQPMessage
├── AMQPResponse (hierarchical responses)
└── [other protocol classes]

dev.kourier.amqp.robust/             (Robust client with recovery)
├── RobustAMQPConnection (main class)
├── RobustAMQPChannel (main class)
├── ExtensionsKt (extension functions)
├── ChannelRestoreContextElementKt
└── states/ (internal state classes)
```

---

## Main Classes & Interfaces

### 1. **RobustAMQPConnection**
- **Location**: `dev.kourier.amqp.robust.RobustAMQPConnection`
- **Create via**: `dev.kourier.amqp.robust.createRobustAMQPConnection(scope, urlString)`
- **Key Methods**:
  - `channel(): RobustAMQPChannel` - Create a new channel (NOT `openChannel()`)
  - `close()` - Gracefully close connection

### 2. **RobustAMQPChannel**
- **Location**: `dev.kourier.amqp.robust.RobustAMQPChannel`
- **Key Methods** (all are suspend functions):
  - `exchangeDeclare(exchange: String, type: String, durable: Boolean, autoDelete: Boolean)`
  - `exchangeDelete(exchange: String)`
  - `queueDeclare(name: String, durable: Boolean, exclusive: Boolean, autoDelete: Boolean, arguments: Map<String, Field>): AMQPResponse.Channel.Queue.Declared`
  - `queueDeclarePassive(name: String): AMQPResponse.Channel.Queue.Declared`
  - `queueBind(queue: String, exchange: String, routingKey: String)`
  - `queueDelete(queue: String)`
  - `queuePurge(name: String): AMQPResponse.Channel.Queue.Purged`
  - `basicPublish(body: ByteArray, exchange: String, routingKey: String)` (NO properties support in basic version)
  - `basicConsume(queue: String, autoAck: Boolean = false)` - Returns Flow<Delivery>
  - `basicQos(prefetchCount: Int)`
  - `basicCancel(consumerTag: String)`

### 3. **Delivery Message Structure**
- **Type**: `AMQPResponse.Channel.Message.Delivery`
- **Properties**:
  - `message: AMQPMessage` (contains the actual message)
  - `consumerTag: String`
- **Message Properties**:
  - `properties: BasicProperties?` (message metadata)
  - `body: ByteArray` (message content)
- **Methods**:
  - `ack()` - Acknowledge message
  - `nack(requeue: Boolean = false)` - Reject message

### 4. **BasicProperties**
- **Location**: Not directly in simple package - check for message properties
- **Alternative**: Use response message properties directly

---

## Key Differences from RabbitMQ Java Client

| Aspect | Java Client | Kourier |
|--------|------------|---------|
| Channel creation | `connection.createChannel()` | `connection.channel()` |
| Message Publishing | `channel.basicPublish(exchange, routingKey, props, body)` | `channel.basicPublish(body, exchange, routingKey)` - NO properties! |
| Message Consumption | `channel.basicConsume(queue, callback)` | `channel.basicConsume(queue): Flow<Delivery>` |
| Properties | `BasicProperties` object | Message properties embedded in response |
| Connection Type | `Connection` | `RobustAMQPConnection` |
| API Style | Blocking I/O callbacks | Suspend functions + Flow |

---

## Known API Gaps

1. **No BasicProperties support** in `basicPublish()` - may need to use request/response pattern
2. **No direct property setting** - message metadata might need different handling
3. **Delivery structure** is nested: `Delivery -> Message -> (properties, body)`
4. **Filter/Map on basicConsume** - Flow is returned, so Flow operators work directly

---

## Compilation Status

- ✅ `RobustAMQPConnection` imports correctly
- ✅ `RobustAMQPChannel` imports correctly
- ✅ `createRobustAMQPConnection()` imports correctly
- ❌ `connection.openChannel()` - should be `channel()`
- ❌ `BasicProperties()` constructor - needs research
- ❌ Message acknowledgment properties - need to verify Delivery.ack() signature

---

## Next Steps

1. Check actual `BasicProperties` location or alternative for message metadata
2. Verify correct method signatures for all channel operations
3. Determine how to set message properties (correlation ID, message ID, etc.)
4. Test with actual Kourier implementation

