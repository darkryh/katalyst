package com.ead.katalyst.repositories

import com.ead.katalyst.core.persistence.Table
import com.ead.katalyst.database.DatabaseFactory
import com.ead.katalyst.repositories.model.PageInfo
import com.ead.katalyst.repositories.model.QueryFilter
import com.ead.katalyst.repositories.model.SortOrder
import com.ead.katalyst.testing.core.inMemoryDatabaseConfig
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

/**
 * Comprehensive tests for CrudRepository interface.
 *
 * Tests cover:
 * - Basic CRUD operations (save, findById, findAll, delete, count)
 * - Pagination with QueryFilter
 * - Sorting (ascending/descending, by different columns)
 * - Edge cases (null handling, empty results, invalid IDs)
 * - Insert vs Update logic
 */
class CrudRepositoryTest {

    // Test entity
    data class TestUser(
        override val id: Long? = null,
        val name: String,
        val email: String,
        val age: Int,
        val active: Boolean = true
    ) : Identifiable<Long>

    // Test table definition
    object TestUsersTable : LongIdTable("test_users"), Table<Long, TestUser> {
        val name = varchar("name", 255)
        val email = varchar("email", 255)
        val age = integer("age")
        val active = bool("active").default(true)

        override fun mapRow(row: ResultRow): TestUser = TestUser(
            id = row[id].value,
            name = row[name],
            email = row[email],
            age = row[age],
            active = row[active]
        )

        override fun assignEntity(
            statement: UpdateBuilder<*>,
            entity: TestUser,
            skipIdColumn: Boolean
        ) {
            if (!skipIdColumn && entity.id != null) {
                statement[id] = entity.id
            }
            statement[name] = entity.name
            statement[email] = entity.email
            statement[age] = entity.age
            statement[active] = entity.active
        }
    }

    // Test repository implementation
    class TestUserRepository(private val db: Database) : CrudRepository<Long, TestUser> {
        override val table = TestUsersTable
        override fun map(row: ResultRow): TestUser = table.mapRow(row)

        private fun <T> blockingTx(block: () -> T): T =
            transaction(db) { block() }

        private suspend fun <T> suspendedTx(block: suspend () -> T): T =
            newSuspendedTransaction(context = null, db = db) { block() }

        override fun save(entity: TestUser): TestUser =
            blockingTx { super<CrudRepository>.save(entity) }

        override fun findById(id: Long): TestUser? =
            blockingTx { super<CrudRepository>.findById(id) }

        override fun findAll(): List<TestUser> =
            blockingTx { super<CrudRepository>.findAll() }

        override fun findAll(filter: QueryFilter): Pair<List<TestUser>, PageInfo> =
            blockingTx { super<CrudRepository>.findAll(filter) }

        override suspend fun count(): Long =
            suspendedTx { super<CrudRepository>.count() }

        override suspend fun delete(id: Long) {
            suspendedTx { super<CrudRepository>.delete(id) }
        }
    }

    private lateinit var databaseFactory: DatabaseFactory
    private lateinit var database: Database
    private lateinit var repository: TestUserRepository

    @BeforeTest
    fun setup() {
        databaseFactory = DatabaseFactory.create(inMemoryDatabaseConfig())
        database = databaseFactory.database
        transaction(database) {
            SchemaUtils.create(TestUsersTable)
        }
        repository = TestUserRepository(database)
    }

    @AfterTest
    fun cleanup() {
        transaction(database) {
            SchemaUtils.drop(TestUsersTable)
        }
        databaseFactory.close()
    }

    // ========== BASIC CRUD OPERATIONS ==========

    @Test
    fun `save should persist new entity and return with generated ID`() = runTest {
        // Given
        val user = TestUser(name = "Alice", email = "alice@example.com", age = 30)

        // When
        val saved = repository.save(user)

        // Then
        assertNotNull(saved.id, "Saved entity should have generated ID")
        assertEquals("Alice", saved.name)
        assertEquals("alice@example.com", saved.email)
        assertEquals(30, saved.age)
        assertTrue(saved.active)
    }

    @Test
    fun `save should update existing entity when ID is provided`() = runTest {
        // Given
        val user = repository.save(TestUser(name = "Bob", email = "bob@example.com", age = 25))
        val userId = user.id!!

        // When
        val updated = repository.save(user.copy(name = "Bobby", age = 26))

        // Then
        assertEquals(userId, updated.id, "ID should remain the same")
        assertEquals("Bobby", updated.name)
        assertEquals(26, updated.age)

        // Verify only one record exists
        val all = repository.findAll()
        assertEquals(1, all.size)
    }

    @Test
    fun `findById should return entity when exists`() = runTest {
        // Given
        val saved = repository.save(TestUser(name = "Charlie", email = "charlie@example.com", age = 35))

        // When
        val found = repository.findById(saved.id!!)

        // Then
        assertNotNull(found)
        assertEquals(saved.id, found.id)
        assertEquals("Charlie", found.name)
        assertEquals("charlie@example.com", found.email)
    }

    @Test
    fun `findById should return null when entity does not exist`() = runTest {
        // When
        val found = repository.findById(999L)

        // Then
        assertNull(found)
    }

    @Test
    fun `findAll should return all entities ordered by ID descending`() = runTest {
        // Given
        repository.save(TestUser(name = "User1", email = "user1@example.com", age = 20))
        repository.save(TestUser(name = "User2", email = "user2@example.com", age = 30))
        repository.save(TestUser(name = "User3", email = "user3@example.com", age = 40))

        // When
        val all = repository.findAll()

        // Then
        assertEquals(3, all.size)
        // Should be ordered by ID DESC (most recent first)
        assertTrue(all[0].id!! > all[1].id!!)
        assertTrue(all[1].id!! > all[2].id!!)
    }

    @Test
    fun `findAll should return empty list when table is empty`() = runTest {
        // When
        val all = repository.findAll()

        // Then
        assertEquals(0, all.size)
    }

    @Test
    fun `count should return correct number of entities`() = runTest {
        // Given
        repository.save(TestUser(name = "User1", email = "user1@example.com", age = 20))
        repository.save(TestUser(name = "User2", email = "user2@example.com", age = 30))
        repository.save(TestUser(name = "User3", email = "user3@example.com", age = 40))

        // When
        val count = repository.count()

        // Then
        assertEquals(3, count)
    }

    @Test
    fun `count should return 0 when table is empty`() = runTest {
        // When
        val count = repository.count()

        // Then
        assertEquals(0, count)
    }

    @Test
    fun `delete should remove entity by ID`() = runTest {
        // Given
        val saved = repository.save(TestUser(name = "ToDelete", email = "delete@example.com", age = 50))
        val id = saved.id!!

        // When
        repository.delete(id)

        // Then
        val found = repository.findById(id)
        assertNull(found)
        assertEquals(0, repository.count())
    }

    @Test
    fun `delete should not throw when entity does not exist`() = runTest {
        // When/Then - should not throw
        repository.delete(999L)

        // Verify no side effects
        assertEquals(0, repository.count())
    }

    // ========== PAGINATION TESTS ==========

    @Test
    fun `findAll with pagination should return correct subset`() = runTest {
        // Given
        repeat(10) { i ->
            repository.save(TestUser(name = "User$i", email = "user$i@example.com", age = 20 + i))
        }

        // When
        val filter = QueryFilter(limit = 3, offset = 0)
        val (results, pageInfo) = repository.findAll(filter)

        // Then
        assertEquals(3, results.size)
        assertEquals(3, pageInfo.limit)
        assertEquals(0, pageInfo.offset)
        assertEquals(10, pageInfo.total)
        assertEquals(1, pageInfo.currentPage)
        assertEquals(4, pageInfo.totalPages)
        assertTrue(pageInfo.hasNextPage)
    }

    @Test
    fun `findAll with pagination should handle second page`() = runTest {
        // Given
        repeat(10) { i ->
            repository.save(TestUser(name = "User$i", email = "user$i@example.com", age = 20 + i))
        }

        // When
        val filter = QueryFilter(limit = 3, offset = 3)
        val (results, pageInfo) = repository.findAll(filter)

        // Then
        assertEquals(3, results.size)
        assertEquals(2, pageInfo.currentPage)
        assertTrue(pageInfo.hasNextPage)
    }

    @Test
    fun `findAll with pagination should handle last page`() = runTest {
        // Given
        repeat(10) { i ->
            repository.save(TestUser(name = "User$i", email = "user$i@example.com", age = 20 + i))
        }

        // When
        val filter = QueryFilter(limit = 3, offset = 9)
        val (results, pageInfo) = repository.findAll(filter)

        // Then
        assertEquals(1, results.size)
        assertEquals(4, pageInfo.currentPage)
        assertFalse(pageInfo.hasNextPage)
    }

    @Test
    fun `findAll with pagination beyond total should return empty list`() = runTest {
        // Given
        repository.save(TestUser(name = "User1", email = "user1@example.com", age = 20))

        // When
        val filter = QueryFilter(limit = 10, offset = 100)
        val (results, pageInfo) = repository.findAll(filter)

        // Then
        assertEquals(0, results.size)
        assertEquals(1, pageInfo.total)
    }

    @Test
    fun `findAll with zero limit should return empty list`() = runTest {
        // Given
        repository.save(TestUser(name = "User1", email = "user1@example.com", age = 20))

        // When
        val filter = QueryFilter(limit = 0, offset = 0)
        val (results, pageInfo) = repository.findAll(filter)

        // Then
        assertEquals(0, results.size)
    }

    // ========== SORTING TESTS ==========

    @Test
    fun `findAll should sort by name ascending`() = runTest {
        // Given
        repository.save(TestUser(name = "Charlie", email = "charlie@example.com", age = 30))
        repository.save(TestUser(name = "Alice", email = "alice@example.com", age = 25))
        repository.save(TestUser(name = "Bob", email = "bob@example.com", age = 35))

        // When
        val filter = QueryFilter(sortBy = "name", sortOrder = SortOrder.ASCENDING)
        val (results, _) = repository.findAll(filter)

        // Then
        assertEquals(3, results.size)
        assertEquals("Alice", results[0].name)
        assertEquals("Bob", results[1].name)
        assertEquals("Charlie", results[2].name)
    }

    @Test
    fun `findAll should sort by name descending`() = runTest {
        // Given
        repository.save(TestUser(name = "Charlie", email = "charlie@example.com", age = 30))
        repository.save(TestUser(name = "Alice", email = "alice@example.com", age = 25))
        repository.save(TestUser(name = "Bob", email = "bob@example.com", age = 35))

        // When
        val filter = QueryFilter(sortBy = "name", sortOrder = SortOrder.DESCENDING)
        val (results, _) = repository.findAll(filter)

        // Then
        assertEquals(3, results.size)
        assertEquals("Charlie", results[0].name)
        assertEquals("Bob", results[1].name)
        assertEquals("Alice", results[2].name)
    }

    @Test
    fun `findAll should sort by age ascending`() = runTest {
        // Given
        repository.save(TestUser(name = "Charlie", email = "charlie@example.com", age = 30))
        repository.save(TestUser(name = "Alice", email = "alice@example.com", age = 20))
        repository.save(TestUser(name = "Bob", email = "bob@example.com", age = 40))

        // When
        val filter = QueryFilter(sortBy = "age", sortOrder = SortOrder.ASCENDING)
        val (results, _) = repository.findAll(filter)

        // Then
        assertEquals(3, results.size)
        assertEquals(20, results[0].age)
        assertEquals(30, results[1].age)
        assertEquals(40, results[2].age)
    }

    @Test
    fun `findAll should sort by email descending`() = runTest {
        // Given
        repository.save(TestUser(name = "User1", email = "aaa@example.com", age = 20))
        repository.save(TestUser(name = "User2", email = "ccc@example.com", age = 30))
        repository.save(TestUser(name = "User3", email = "bbb@example.com", age = 40))

        // When
        val filter = QueryFilter(sortBy = "email", sortOrder = SortOrder.DESCENDING)
        val (results, _) = repository.findAll(filter)

        // Then
        assertEquals(3, results.size)
        assertEquals("ccc@example.com", results[0].email)
        assertEquals("bbb@example.com", results[1].email)
        assertEquals("aaa@example.com", results[2].email)
    }

    @Test
    fun `findAll should use default ID sort when sortBy is null`() = runTest {
        // Given
        val user1 = repository.save(TestUser(name = "First", email = "first@example.com", age = 20))
        val user2 = repository.save(TestUser(name = "Second", email = "second@example.com", age = 30))

        // When
        val filter = QueryFilter(sortBy = null, sortOrder = SortOrder.DESCENDING)
        val (results, _) = repository.findAll(filter)

        // Then
        assertEquals(2, results.size)
        // Should be sorted by ID DESC (default)
        assertEquals(user2.id, results[0].id)
        assertEquals(user1.id, results[1].id)
    }

    @Test
    fun `findAll should handle invalid sortBy column gracefully`() = runTest {
        // Given
        repository.save(TestUser(name = "User1", email = "user1@example.com", age = 20))

        // When
        val filter = QueryFilter(sortBy = "nonexistent_column", sortOrder = SortOrder.ASCENDING)
        val (results, _) = repository.findAll(filter)

        // Then - should fallback to ID sort
        assertEquals(1, results.size)
    }

    // ========== COMBINED PAGINATION + SORTING TESTS ==========

    @Test
    fun `findAll should combine pagination and sorting`() = runTest {
        // Given
        repository.save(TestUser(name = "Eve", email = "eve@example.com", age = 50))
        repository.save(TestUser(name = "Alice", email = "alice@example.com", age = 20))
        repository.save(TestUser(name = "Charlie", email = "charlie@example.com", age = 30))
        repository.save(TestUser(name = "Bob", email = "bob@example.com", age = 25))
        repository.save(TestUser(name = "Diana", email = "diana@example.com", age = 40))

        // When
        val filter = QueryFilter(
            limit = 2,
            offset = 1,
            sortBy = "age",
            sortOrder = SortOrder.ASCENDING
        )
        val (results, pageInfo) = repository.findAll(filter)

        // Then
        assertEquals(2, results.size)
        // Sorted by age: 20, 25, 30, 40, 50
        // Offset 1, Limit 2: should return age 25 and 30
        assertEquals(25, results[0].age)
        assertEquals(30, results[1].age)
        assertEquals(5, pageInfo.total)
    }

    // ========== BATCH OPERATIONS TESTS ==========

    @Test
    fun `save should handle multiple entities correctly`() = runTest {
        // Given/When
        val user1 = repository.save(TestUser(name = "User1", email = "user1@example.com", age = 20))
        val user2 = repository.save(TestUser(name = "User2", email = "user2@example.com", age = 30))
        val user3 = repository.save(TestUser(name = "User3", email = "user3@example.com", age = 40))

        // Then
        assertNotEquals(user1.id, user2.id)
        assertNotEquals(user2.id, user3.id)
        assertNotEquals(user1.id, user3.id)
        assertEquals(3, repository.count())
    }

    @Test
    fun `delete should handle multiple entities correctly`() = runTest {
        // Given
        val user1 = repository.save(TestUser(name = "User1", email = "user1@example.com", age = 20))
        val user2 = repository.save(TestUser(name = "User2", email = "user2@example.com", age = 30))
        val user3 = repository.save(TestUser(name = "User3", email = "user3@example.com", age = 40))

        // When
        repository.delete(user1.id!!)
        repository.delete(user3.id!!)

        // Then
        assertEquals(1, repository.count())
        assertNull(repository.findById(user1.id!!))
        assertNotNull(repository.findById(user2.id!!))
        assertNull(repository.findById(user3.id!!))
    }

    // ========== EDGE CASES TESTS ==========

    @Test
    fun `save should handle entity with all fields set`() = runTest {
        // Given
        val user = TestUser(
            id = null,
            name = "CompleteUser",
            email = "complete@example.com",
            age = 99,
            active = false
        )

        // When
        val saved = repository.save(user)

        // Then
        assertNotNull(saved.id)
        assertEquals("CompleteUser", saved.name)
        assertEquals("complete@example.com", saved.email)
        assertEquals(99, saved.age)
        assertFalse(saved.active)
    }

    @Test
    fun `save should handle entity with default active value`() = runTest {
        // Given
        val user = TestUser(name = "DefaultUser", email = "default@example.com", age = 25)

        // When
        val saved = repository.save(user)

        // Then
        assertTrue(saved.active, "Default active should be true")
    }

    @Test
    fun `findAll should handle large dataset pagination`() = runTest {
        // Given - Create 100 users
        repeat(100) { i ->
            repository.save(TestUser(name = "User$i", email = "user$i@example.com", age = i))
        }

        // When
        val filter = QueryFilter(limit = 10, offset = 50)
        val (results, pageInfo) = repository.findAll(filter)

        // Then
        assertEquals(10, results.size)
        assertEquals(100, pageInfo.total)
        assertEquals(10, pageInfo.totalPages)
        assertEquals(6, pageInfo.currentPage)
    }

    @Test
    fun `count should be consistent with findAll total`() = runTest {
        // Given
        repeat(15) { i ->
            repository.save(TestUser(name = "User$i", email = "user$i@example.com", age = i))
        }

        // When
        val count = repository.count()
        val (_, pageInfo) = repository.findAll(QueryFilter())

        // Then
        assertEquals(count, pageInfo.total.toLong())
    }

    @Test
    fun `findAll with sorting should handle duplicate values`() = runTest {
        // Given - Multiple users with same age
        repository.save(TestUser(name = "Alice", email = "alice@example.com", age = 30))
        repository.save(TestUser(name = "Bob", email = "bob@example.com", age = 30))
        repository.save(TestUser(name = "Charlie", email = "charlie@example.com", age = 30))

        // When
        val filter = QueryFilter(sortBy = "age", sortOrder = SortOrder.ASCENDING)
        val (results, _) = repository.findAll(filter)

        // Then
        assertEquals(3, results.size)
        // All should have same age
        assertTrue(results.all { it.age == 30 })
    }

    @Test
    fun `update should preserve other fields when updating specific fields`() = runTest {
        // Given
        val original = repository.save(
            TestUser(name = "Original", email = "original@example.com", age = 25, active = true)
        )

        // When - Update only name and age
        val updated = repository.save(original.copy(name = "Updated", age = 26))

        // Then
        assertEquals(original.id, updated.id)
        assertEquals("Updated", updated.name)
        assertEquals(26, updated.age)
        assertEquals("original@example.com", updated.email) // Should be preserved
        assertTrue(updated.active) // Should be preserved
    }
}
