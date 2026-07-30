package io.github.darkryh.katalyst.testing.core.schemapolicy

import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import io.github.darkryh.katalyst.repositories.Identifiable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

data class PolicyWidget(
    override val id: Long? = null,
    val name: String,
) : Identifiable<Long>

/**
 * Declares a `name` column. [SchemaPolicyBehaviourTest] pre-creates the table without it, so the
 * live schema is drifted before boot and each policy's reaction to that drift is observable.
 */
object PolicyWidgetsTable : LongIdTable("policy_widgets"), Table<Long, PolicyWidget> {
    val name = varchar("name", 128)

    override val mapping = mapping<Long, PolicyWidget> {
        generatedId(id, PolicyWidget::id)
        field(name, PolicyWidget::name)

        construct {
            PolicyWidget(id = this[id], name = this[name])
        }
    }
}
