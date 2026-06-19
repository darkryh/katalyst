package io.github.darkryh.katalyst.analysis

import io.github.darkryh.katalyst.conventions.KatalystConventions
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards against drift between [KatalystConventions] and the real framework types.
 *
 * If a marker interface or DSL owner is ever renamed/moved, the corresponding constant must move
 * with it; this test fails loudly otherwise, keeping analysis/runtime/IDE aligned.
 */
class ConventionsContractTest {

    @Test
    fun `every marker interface FQN resolves to a real type`() {
        val markers = KatalystConventions.markerInterfaces +
            KatalystConventions.SCHEDULER_JOB_HANDLE +
            KatalystConventions.EXPOSED_TABLE +
            KatalystConventions.IDENTIFIABLE +
            KatalystConventions.ktorReceiverTypes

        val missing = markers.filter { fqName ->
            runCatching { Class.forName(fqName, false, javaClass.classLoader) }.isFailure
        }
        if (missing.isNotEmpty()) fail("These KatalystConventions FQNs no longer resolve: $missing")
    }

    @Test
    fun `dsl owner internal names convert to dotted form consistently`() {
        assertTrue(KatalystConventions.dslOwnerInternalNames.all { '/' in it })
        assertTrue(KatalystConventions.dslOwnerQualifiedNames.all { '.' in it && '/' !in it })
        assertTrue(KatalystConventions.dslMethodNames.contains("katalystRouting"))
    }
}
