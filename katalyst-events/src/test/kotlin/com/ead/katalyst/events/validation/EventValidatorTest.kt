package com.ead.katalyst.events.validation

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class EventValidatorTest {

    private data class SampleEvent(val id: String) : DomainEvent {
        private val metadata = EventMetadata(eventType = "sample.event")
        override fun getMetadata(): EventMetadata = metadata
    }

    @Test
    fun `composite validator aggregates errors`() = runBlocking {
        val failingValidator = object : EventValidator<SampleEvent> {
            override val eventType = SampleEvent::class
            override suspend fun validate(event: SampleEvent): ValidationResult =
                ValidationResult.Invalid(listOf("bad"))
        }
        val passingValidator = object : EventValidator<SampleEvent> {
            override val eventType = SampleEvent::class
            override suspend fun validate(event: SampleEvent): ValidationResult =
                ValidationResult.Valid
        }

        val composite = CompositeEventValidator(
            SampleEvent::class,
            listOf(failingValidator, passingValidator)
        )

        val result = composite.validate(SampleEvent("1"))
        assertTrue(result is ValidationResult.Invalid)
        assertEquals(listOf("bad"), result.errors())
    }

    @Test
    fun `noop validator always returns valid`() = runBlocking {
        val validator = NoOpEventValidator(SampleEvent::class)
        val result = validator.validate(SampleEvent("1"))
        assertTrue(result is ValidationResult.Valid)
    }
}
