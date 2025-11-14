package com.ead.katalyst.migrations.sql

import kotlin.test.*

/**
 * Comprehensive tests for SqlMigration.
 *
 * Tests cover:
 * - Abstract class implementation
 * - Checksum calculation
 * - Statement retrieval
 * - Checksum stability
 * - Multiple statements
 * - Empty statements
 * - Complex SQL statements
 * - Checksum consistency
 */
class SqlMigrationTest {

    // ========== TEST IMPLEMENTATIONS ==========

    private class CreateUserTableMigration : SqlMigration() {
        override fun statements(): List<String> = listOf(
            "CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100), email VARCHAR(255))"
        )
    }

    private class CreateOrdersTableMigration : SqlMigration() {
        override fun statements(): List<String> = listOf(
            "CREATE TABLE orders (id SERIAL PRIMARY KEY, user_id INT REFERENCES users(id))"
        )
    }

    private class MultiStatementMigration : SqlMigration() {
        override fun statements(): List<String> = listOf(
            "CREATE TABLE products (id SERIAL PRIMARY KEY)",
            "CREATE INDEX idx_products_id ON products(id)",
            "INSERT INTO products (id) VALUES (1)"
        )
    }

    private class EmptyMigration : SqlMigration() {
        override fun statements(): List<String> = emptyList()
    }

    private class ComplexMigration : SqlMigration() {
        override fun statements(): List<String> = listOf(
            """
            CREATE TABLE users (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                email VARCHAR(255) UNIQUE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
            "CREATE INDEX idx_users_email ON users(email)",
            "CREATE INDEX idx_users_created_at ON users(created_at)"
        )
    }

    // ========== BASIC IMPLEMENTATION TESTS ==========

    @Test
    fun `SqlMigration should be instantiable via concrete implementation`() {
        val migration = CreateUserTableMigration()
        assertNotNull(migration)
    }

    @Test
    fun `SqlMigration should return statements from abstract method`() {
        val migration = CreateUserTableMigration()
        val statements = migration.statements()

        assertEquals(1, statements.size)
        assertTrue(statements[0].contains("CREATE TABLE users"))
    }

    @Test
    fun `SqlMigration should support multiple statements`() {
        val migration = MultiStatementMigration()
        val statements = migration.statements()

        assertEquals(3, statements.size)
    }

    @Test
    fun `SqlMigration should support empty statement list`() {
        val migration = EmptyMigration()
        val statements = migration.statements()

        assertTrue(statements.isEmpty())
    }

    // ========== CHECKSUM CALCULATION TESTS ==========

    @Test
    fun `SqlMigration should calculate checksum from statements`() {
        val migration = CreateUserTableMigration()
        val checksum = migration.checksum

        assertEquals(64, checksum.length) // SHA-256 produces 64 hex characters
        assertTrue(checksum.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `SqlMigration checksum should be stable across multiple calls`() {
        val migration = CreateUserTableMigration()
        val checksum1 = migration.checksum
        val checksum2 = migration.checksum
        val checksum3 = migration.checksum

        assertEquals(checksum1, checksum2)
        assertEquals(checksum2, checksum3)
    }

    @Test
    fun `different migrations should have different checksums`() {
        val migration1 = CreateUserTableMigration()
        val migration2 = CreateOrdersTableMigration()

        assertNotEquals(migration1.checksum, migration2.checksum)
    }

    @Test
    fun `identical migrations should have identical checksums`() {
        val migration1 = CreateUserTableMigration()
        val migration2 = CreateUserTableMigration()

        assertEquals(migration1.checksum, migration2.checksum)
    }

    @Test
    fun `multi-statement migration should have stable checksum`() {
        val migration = MultiStatementMigration()
        val checksum1 = migration.checksum
        val checksum2 = migration.checksum

        assertEquals(checksum1, checksum2)
        assertEquals(64, checksum1.length)
    }

    @Test
    fun `empty migration should have valid checksum`() {
        val migration = EmptyMigration()
        val checksum = migration.checksum

        assertEquals(64, checksum.length)
        assertNotNull(checksum)
    }

    @Test
    fun `complex migration should have stable checksum`() {
        val migration = ComplexMigration()
        val checksum1 = migration.checksum
        val checksum2 = migration.checksum

        assertEquals(checksum1, checksum2)
    }

    // ========== CHECKSUM CONSISTENCY TESTS ==========

    @Test
    fun `checksum should reflect statement changes`() {
        class MutableMigration(private var content: String) : SqlMigration() {
            override fun statements() = listOf(content)
            fun updateContent(newContent: String) {
                content = newContent
            }
        }

        val migration = MutableMigration("CREATE TABLE users (id INT)")
        val checksum1 = migration.checksum

        migration.updateContent("CREATE TABLE users (id INT PRIMARY KEY)")
        val checksum2 = migration.checksum

        assertNotEquals(checksum1, checksum2)
    }

    @Test
    fun `checksum should be consistent for same statements`() {
        class ImmutableMigration : SqlMigration() {
            override fun statements() = listOf("CREATE TABLE users (id INT)")
        }

        val migration1 = ImmutableMigration()
        val migration2 = ImmutableMigration()

        assertEquals(migration1.checksum, migration2.checksum)
    }

    // ========== STATEMENT ORDERING TESTS ==========

    @Test
    fun `statement order should affect checksum`() {
        class OrderedMigration1 : SqlMigration() {
            override fun statements() = listOf(
                "CREATE TABLE users (id INT)",
                "CREATE TABLE orders (id INT)"
            )
        }

        class OrderedMigration2 : SqlMigration() {
            override fun statements() = listOf(
                "CREATE TABLE orders (id INT)",
                "CREATE TABLE users (id INT)"
            )
        }

        val migration1 = OrderedMigration1()
        val migration2 = OrderedMigration2()

        assertNotEquals(migration1.checksum, migration2.checksum)
    }

    @Test
    fun `statement order should be preserved`() {
        val migration = MultiStatementMigration()
        val statements = migration.statements()

        assertEquals("CREATE TABLE products (id SERIAL PRIMARY KEY)", statements[0])
        assertEquals("CREATE INDEX idx_products_id ON products(id)", statements[1])
        assertEquals("INSERT INTO products (id) VALUES (1)", statements[2])
    }

    // ========== COMPLEX SQL TESTS ==========

    @Test
    fun `migration with complex multi-line SQL`() {
        class ComplexSqlMigration : SqlMigration() {
            override fun statements() = listOf(
                """
                CREATE TABLE users (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(100),
                    email VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent()
            )
        }

        val migration = ComplexSqlMigration()
        val checksum = migration.checksum

        assertEquals(64, checksum.length)
        assertNotNull(migration.statements()[0])
    }

    @Test
    fun `migration with SQL comments`() {
        class CommentedMigration : SqlMigration() {
            override fun statements() = listOf(
                "-- Create users table",
                "CREATE TABLE users (id INT)",
                "-- Create index",
                "CREATE INDEX idx_users ON users(id)"
            )
        }

        val migration = CommentedMigration()
        val checksum = migration.checksum

        assertEquals(64, checksum.length)
        assertEquals(4, migration.statements().size)
    }

    @Test
    fun `migration with special characters in SQL`() {
        class SpecialCharMigration : SqlMigration() {
            override fun statements() = listOf(
                "INSERT INTO users (name) VALUES ('O''Brien')",
                "INSERT INTO users (name) VALUES ('José García')"
            )
        }

        val migration = SpecialCharMigration()
        val checksum = migration.checksum

        assertEquals(64, checksum.length)
    }

    // ========== PRACTICAL USAGE SCENARIOS ==========

    @Test
    fun `initial database schema migration`() {
        class InitialSchemaMigration : SqlMigration() {
            override fun statements() = listOf(
                "CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100), email VARCHAR(255))",
                "CREATE TABLE roles (id SERIAL PRIMARY KEY, name VARCHAR(50))",
                "CREATE TABLE user_roles (user_id INT REFERENCES users(id), role_id INT REFERENCES roles(id))",
                "CREATE INDEX idx_user_roles_user_id ON user_roles(user_id)"
            )
        }

        val migration = InitialSchemaMigration()
        assertEquals(4, migration.statements().size)
        assertEquals(64, migration.checksum.length)
    }

    @Test
    fun `add column migration`() {
        class AddColumnMigration : SqlMigration() {
            override fun statements() = listOf(
                "ALTER TABLE users ADD COLUMN phone VARCHAR(20)",
                "ALTER TABLE users ADD COLUMN address TEXT"
            )
        }

        val migration = AddColumnMigration()
        assertEquals(2, migration.statements().size)
        assertEquals(64, migration.checksum.length)
    }

    @Test
    fun `create index migration`() {
        class CreateIndexMigration : SqlMigration() {
            override fun statements() = listOf(
                "CREATE INDEX idx_users_email ON users(email)",
                "CREATE INDEX idx_users_name ON users(name)"
            )
        }

        val migration = CreateIndexMigration()
        assertEquals(2, migration.statements().size)
        assertEquals(64, migration.checksum.length)
    }

    @Test
    fun `data seeding migration`() {
        class SeedDataMigration : SqlMigration() {
            override fun statements() = listOf(
                "INSERT INTO roles (name) VALUES ('ADMIN')",
                "INSERT INTO roles (name) VALUES ('USER')",
                "INSERT INTO roles (name) VALUES ('GUEST')"
            )
        }

        val migration = SeedDataMigration()
        assertEquals(3, migration.statements().size)
        assertEquals(64, migration.checksum.length)
    }

    @Test
    fun `drop table migration`() {
        class DropTableMigration : SqlMigration() {
            override fun statements() = listOf(
                "DROP INDEX IF EXISTS idx_old_table",
                "DROP TABLE IF EXISTS old_table"
            )
        }

        val migration = DropTableMigration()
        assertEquals(2, migration.statements().size)
        assertEquals(64, migration.checksum.length)
    }

    @Test
    fun `foreign key constraint migration`() {
        class AddForeignKeyMigration : SqlMigration() {
            override fun statements() = listOf(
                "ALTER TABLE orders ADD CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)",
                "ALTER TABLE order_items ADD CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id)"
            )
        }

        val migration = AddForeignKeyMigration()
        assertEquals(2, migration.statements().size)
        assertEquals(64, migration.checksum.length)
    }

    @Test
    fun `rename column migration`() {
        class RenameColumnMigration : SqlMigration() {
            override fun statements() = listOf(
                "ALTER TABLE users RENAME COLUMN old_name TO new_name"
            )
        }

        val migration = RenameColumnMigration()
        assertEquals(1, migration.statements().size)
        assertEquals(64, migration.checksum.length)
    }

    @Test
    fun `checksum consistency for version control`() {
        // This test simulates checking migration integrity across different environments
        class V001_CreateUsers : SqlMigration() {
            override fun statements() = listOf(
                "CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100))"
            )
        }

        val devMigration = V001_CreateUsers()
        val prodMigration = V001_CreateUsers()

        // Both environments should have identical checksums
        assertEquals(devMigration.checksum, prodMigration.checksum)
    }

    @Test
    fun `checksum detects tampered migrations`() {
        class OriginalMigration : SqlMigration() {
            override fun statements() = listOf("CREATE TABLE users (id INT)")
        }

        class TamperedMigration : SqlMigration() {
            override fun statements() = listOf("CREATE TABLE users (id INT PRIMARY KEY)")
        }

        val original = OriginalMigration()
        val tampered = TamperedMigration()

        // Tampering should be detected via different checksums
        assertNotEquals(original.checksum, tampered.checksum)
    }

    @Test
    fun `migrations with whitespace differences have different checksums`() {
        class Migration1 : SqlMigration() {
            override fun statements() = listOf("CREATE TABLE users (id INT)")
        }

        class Migration2 : SqlMigration() {
            override fun statements() = listOf("CREATE  TABLE  users  (id  INT)")
        }

        assertNotEquals(Migration1().checksum, Migration2().checksum)
    }
}
