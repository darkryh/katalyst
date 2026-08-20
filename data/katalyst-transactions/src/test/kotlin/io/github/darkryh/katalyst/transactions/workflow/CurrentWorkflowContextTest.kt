package io.github.darkryh.katalyst.transactions.workflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentWorkflowContextTest {
    @AfterTest
    fun cleanUp() {
        CurrentWorkflowContext.clear()
    }

    @Test
    fun `workflow id follows coroutine across dispatcher changes`() = runTest {
        CurrentWorkflowContext.withContext("workflow-1") {
            assertEquals("workflow-1", CurrentWorkflowContext.get())
            withContext(Dispatchers.Default) {
                assertEquals("workflow-1", CurrentWorkflowContext.get())
            }
        }

        assertNull(CurrentWorkflowContext.get())
    }

    @Test
    fun `nested workflow context restores outer id`() = runTest {
        CurrentWorkflowContext.withContext("outer") {
            CurrentWorkflowContext.withContext("inner") {
                assertEquals("inner", CurrentWorkflowContext.get())
            }
            assertEquals("outer", CurrentWorkflowContext.get())
        }
    }
}
