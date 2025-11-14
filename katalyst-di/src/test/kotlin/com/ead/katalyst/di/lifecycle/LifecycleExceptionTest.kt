package com.ead.katalyst.di.lifecycle

import kotlin.test.*

/**
 * Comprehensive tests for LifecycleException and related classes.
 *
 * Tests cover:
 * - Base LifecycleException
 * - LifecyclePhase enum
 * - Specific exception types (Database, Component, Service, Schema, Initializer, TransactionAdapter)
 * - Exception hierarchy
 * - Message and cause handling
 * - Phase association
 */
class LifecycleExceptionTest {

    // ========== LIFECYCLE PHASE ENUM TESTS ==========

    @Test
    fun `LifecyclePhase should have correct display names`() {
        assertEquals("Unknown", LifecyclePhase.UNKNOWN.displayName)
        assertEquals("Database Validation", LifecyclePhase.DATABASE_VALIDATION.displayName)
        assertEquals("Component Discovery", LifecyclePhase.COMPONENT_DISCOVERY.displayName)
        assertEquals("Service Instantiation", LifecyclePhase.SERVICE_INSTANTIATION.displayName)
        assertEquals("Database Schema", LifecyclePhase.SCHEMA_INITIALIZATION.displayName)
        assertEquals("Transaction Adapter Registration", LifecyclePhase.TRANSACTION_ADAPTER_REGISTRATION.displayName)
        assertEquals("Pre-Initialization", LifecyclePhase.PRE_INITIALIZATION.displayName)
        assertEquals("Initialization", LifecyclePhase.INITIALIZATION.displayName)
        assertEquals("Post-Initialization", LifecyclePhase.POST_INITIALIZATION.displayName)
    }

    @Test
    fun `LifecyclePhase should support valueOf`() {
        val phase = LifecyclePhase.valueOf("DATABASE_VALIDATION")
        assertEquals(LifecyclePhase.DATABASE_VALIDATION, phase)
    }

    @Test
    fun `LifecyclePhase should support values()`() {
        val phases = LifecyclePhase.entries.toTypedArray()
        assertTrue(phases.contains(LifecyclePhase.UNKNOWN))
        assertTrue(phases.contains(LifecyclePhase.INITIALIZATION))
    }

    // ========== BASE LIFECYCLE EXCEPTION TESTS ==========

    @Test
    fun `LifecycleException should have message`() {
        val exception = LifecycleException("Test error")
        assertEquals("Test error", exception.message)
    }

    @Test
    fun `LifecycleException should support cause`() {
        val cause = RuntimeException("Root cause")
        val exception = LifecycleException("Test error", cause)

        assertEquals("Test error", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `LifecycleException should default to UNKNOWN phase`() {
        val exception = LifecycleException("Test error")
        assertEquals(LifecyclePhase.UNKNOWN, exception.phase)
    }

    @Test
    fun `LifecycleException should be RuntimeException`() {
        val exception = LifecycleException("Test error")
        assertTrue(exception is RuntimeException)
    }

    // ========== DATABASE VALIDATION EXCEPTION TESTS ==========

    @Test
    fun `DatabaseValidationException should have DATABASE_VALIDATION phase`() {
        val exception = DatabaseValidationException("Connection failed")
        assertEquals(LifecyclePhase.DATABASE_VALIDATION, exception.phase)
    }

    @Test
    fun `DatabaseValidationException should preserve message`() {
        val exception = DatabaseValidationException("Connection timeout")
        assertEquals("Connection timeout", exception.message)
    }

    @Test
    fun `DatabaseValidationException should preserve cause`() {
        val cause = RuntimeException("Network error")
        val exception = DatabaseValidationException("Connection failed", cause)

        assertEquals("Connection failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `DatabaseValidationException should be LifecycleException`() {
        val exception = DatabaseValidationException("Test")
        assertTrue(exception is LifecycleException)
    }

    // ========== COMPONENT DISCOVERY EXCEPTION TESTS ==========

    @Test
    fun `ComponentDiscoveryException should have COMPONENT_DISCOVERY phase`() {
        val exception = ComponentDiscoveryException("Discovery failed")
        assertEquals(LifecyclePhase.COMPONENT_DISCOVERY, exception.phase)
    }

    @Test
    fun `ComponentDiscoveryException should preserve message`() {
        val exception = ComponentDiscoveryException("Failed to scan packages")
        assertEquals("Failed to scan packages", exception.message)
    }

    @Test
    fun `ComponentDiscoveryException should preserve cause`() {
        val cause = ClassNotFoundException("Service not found")
        val exception = ComponentDiscoveryException("Discovery failed", cause)

        assertEquals("Discovery failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `ComponentDiscoveryException should be LifecycleException`() {
        val exception = ComponentDiscoveryException("Test")
        assertTrue(exception is LifecycleException)
    }

    // ========== SERVICE INITIALIZATION EXCEPTION TESTS ==========

    @Test
    fun `ServiceInitializationException should have SERVICE_INSTANTIATION phase`() {
        val exception = ServiceInitializationException("UserService", "Missing dependency")
        assertEquals(LifecyclePhase.SERVICE_INSTANTIATION, exception.phase)
    }

    @Test
    fun `ServiceInitializationException should include service name in message`() {
        val exception = ServiceInitializationException("UserService", "Constructor failed")
        assertTrue(exception.message!!.contains("UserService"))
        assertTrue(exception.message!!.contains("Constructor failed"))
    }

    @Test
    fun `ServiceInitializationException should preserve serviceName property`() {
        val exception = ServiceInitializationException("OrderService", "Test error")
        assertEquals("OrderService", exception.serviceName)
    }

    @Test
    fun `ServiceInitializationException should preserve cause`() {
        val cause = NullPointerException("Null dependency")
        val exception = ServiceInitializationException("ProductService", "Failed", cause)

        assertEquals(cause, exception.cause)
        assertEquals("ProductService", exception.serviceName)
    }

    @Test
    fun `ServiceInitializationException should be LifecycleException`() {
        val exception = ServiceInitializationException("Service", "Test")
        assertTrue(exception is LifecycleException)
    }

    // ========== SCHEMA INITIALIZATION EXCEPTION TESTS ==========

    @Test
    fun `SchemaInitializationException should have SCHEMA_INITIALIZATION phase`() {
        val exception = SchemaInitializationException("Table creation failed")
        assertEquals(LifecyclePhase.SCHEMA_INITIALIZATION, exception.phase)
    }

    @Test
    fun `SchemaInitializationException should preserve message`() {
        val exception = SchemaInitializationException("Migration failed")
        assertEquals("Migration failed", exception.message)
    }

    @Test
    fun `SchemaInitializationException should preserve cause`() {
        val cause = RuntimeException("SQL error")
        val exception = SchemaInitializationException("Schema error", cause)

        assertEquals("Schema error", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `SchemaInitializationException should be LifecycleException`() {
        val exception = SchemaInitializationException("Test")
        assertTrue(exception is LifecycleException)
    }

    // ========== INITIALIZER FAILED EXCEPTION TESTS ==========

    @Test
    fun `InitializerFailedException should have INITIALIZATION phase`() {
        val exception = InitializerFailedException("SchedulerInit", "Failed to start")
        assertEquals(LifecyclePhase.INITIALIZATION, exception.phase)
    }

    @Test
    fun `InitializerFailedException should include initializer name in message`() {
        val exception = InitializerFailedException("EventBusInit", "Connection timeout")
        assertTrue(exception.message!!.contains("EventBusInit"))
        assertTrue(exception.message!!.contains("Connection timeout"))
    }

    @Test
    fun `InitializerFailedException should preserve initializerName property`() {
        val exception = InitializerFailedException("CacheInit", "Test error")
        assertEquals("CacheInit", exception.initializerName)
    }

    @Test
    fun `InitializerFailedException should preserve cause`() {
        val cause = IllegalStateException("Invalid config")
        val exception = InitializerFailedException("ConfigInit", "Failed", cause)

        assertEquals(cause, exception.cause)
        assertEquals("ConfigInit", exception.initializerName)
    }

    @Test
    fun `InitializerFailedException should be LifecycleException`() {
        val exception = InitializerFailedException("Init", "Test")
        assertTrue(exception is LifecycleException)
    }

    // ========== TRANSACTION ADAPTER EXCEPTION TESTS ==========

    @Test
    fun `TransactionAdapterException should have TRANSACTION_ADAPTER_REGISTRATION phase`() {
        val exception = TransactionAdapterException("EventsAdapter", "Registration failed")
        assertEquals(LifecyclePhase.TRANSACTION_ADAPTER_REGISTRATION, exception.phase)
    }

    @Test
    fun `TransactionAdapterException should include adapter name in message`() {
        val exception = TransactionAdapterException("PersistenceAdapter", "Adapter error")
        assertTrue(exception.message!!.contains("PersistenceAdapter"))
        assertTrue(exception.message!!.contains("Adapter error"))
    }

    @Test
    fun `TransactionAdapterException should preserve adapterName property`() {
        val exception = TransactionAdapterException("CustomAdapter", "Test error")
        assertEquals("CustomAdapter", exception.adapterName)
    }

    @Test
    fun `TransactionAdapterException should preserve cause`() {
        val cause = RuntimeException("DI error")
        val exception = TransactionAdapterException("MessagingAdapter", "Failed", cause)

        assertEquals(cause, exception.cause)
        assertEquals("MessagingAdapter", exception.adapterName)
    }

    @Test
    fun `TransactionAdapterException should be LifecycleException`() {
        val exception = TransactionAdapterException("Adapter", "Test")
        assertTrue(exception is LifecycleException)
    }

    // ========== EXCEPTION HIERARCHY TESTS ==========

    @Test
    fun `all lifecycle exceptions should extend LifecycleException`() {
        val exceptions = listOf(
            DatabaseValidationException("test"),
            ComponentDiscoveryException("test"),
            ServiceInitializationException("service", "test"),
            SchemaInitializationException("test"),
            InitializerFailedException("init", "test"),
            TransactionAdapterException("adapter", "test")
        )

        exceptions.forEach { exception ->
            assertTrue(exception is LifecycleException)
        }
    }

    @Test
    fun `all lifecycle exceptions should be RuntimeException`() {
        val exceptions = listOf(
            DatabaseValidationException("test"),
            ComponentDiscoveryException("test"),
            ServiceInitializationException("service", "test"),
            SchemaInitializationException("test"),
            InitializerFailedException("init", "test"),
            TransactionAdapterException("adapter", "test")
        )

        exceptions.forEach { exception ->
            assertTrue(exception is RuntimeException)
        }
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `database connection failure scenario`() {
        val cause = RuntimeException("Connection refused")
        val exception = DatabaseValidationException(
            "Failed to connect to database at localhost:5432",
            cause
        )

        assertEquals(LifecyclePhase.DATABASE_VALIDATION, exception.phase)
        assertNotNull(exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `service dependency injection failure scenario`() {
        val cause = NullPointerException("Required dependency not found")
        val exception = ServiceInitializationException(
            serviceName = "UserAuthenticationService",
            message = "Missing required dependency: PasswordEncoder",
            cause = cause
        )

        assertEquals("UserAuthenticationService", exception.serviceName)
        assertEquals(LifecyclePhase.SERVICE_INSTANTIATION, exception.phase)
        assertTrue(exception.message!!.contains("UserAuthenticationService"))
    }

    @Test
    fun `scheduler initialization failure scenario`() {
        val cause = IllegalArgumentException("Thread count must be positive")
        val exception = InitializerFailedException(
            initializerName = "SchedulerInitializer",
            message = "Invalid thread pool configuration",
            cause = cause
        )

        assertEquals("SchedulerInitializer", exception.initializerName)
        assertEquals(LifecyclePhase.INITIALIZATION, exception.phase)
        assertNotNull(exception.cause)
    }

    @Test
    fun `component scanning failure scenario`() {
        val cause = ClassNotFoundException("com.example.service.UserService")
        val exception = ComponentDiscoveryException(
            "Failed to load class during component scanning",
            cause
        )

        assertEquals(LifecyclePhase.COMPONENT_DISCOVERY, exception.phase)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `migration execution failure scenario`() {
        val cause = RuntimeException("Syntax error in SQL: CREAT TABLE")
        val exception = SchemaInitializationException(
            "Migration V001__create_users failed: SQL syntax error",
            cause
        )

        assertEquals(LifecyclePhase.SCHEMA_INITIALIZATION, exception.phase)
        assertTrue(exception.message!!.contains("V001"))
    }

    @Test
    fun `event adapter registration failure scenario`() {
        val cause = RuntimeException("ApplicationEventBus not available")
        val exception = TransactionAdapterException(
            adapterName = "EventsTransactionAdapter",
            message = "Event bus not found in DI container",
            cause = cause
        )

        assertEquals("EventsTransactionAdapter", exception.adapterName)
        assertEquals(LifecyclePhase.TRANSACTION_ADAPTER_REGISTRATION, exception.phase)
    }

    @Test
    fun `exception chaining scenario`() {
        val rootCause = RuntimeException("Network timeout")
        val databaseCause = DatabaseValidationException("Connection pool exhausted", rootCause)
        val topLevel = ServiceInitializationException(
            "DatabaseService",
            "Database not available",
            databaseCause
        )

        assertEquals(databaseCause, topLevel.cause)
        assertEquals(rootCause, topLevel.cause?.cause)
        assertEquals("DatabaseService", topLevel.serviceName)
    }

    @Test
    fun `lifecycle phases should be ordered logically`() {
        // Verify the typical startup order
        val expectedOrder = listOf(
            LifecyclePhase.DATABASE_VALIDATION,
            LifecyclePhase.COMPONENT_DISCOVERY,
            LifecyclePhase.SERVICE_INSTANTIATION,
            LifecyclePhase.SCHEMA_INITIALIZATION,
            LifecyclePhase.TRANSACTION_ADAPTER_REGISTRATION,
            LifecyclePhase.PRE_INITIALIZATION,
            LifecyclePhase.INITIALIZATION,
            LifecyclePhase.POST_INITIALIZATION
        )

        // This just verifies they all exist and can be referenced
        expectedOrder.forEach { phase ->
            assertNotNull(phase.displayName)
        }
    }
}
