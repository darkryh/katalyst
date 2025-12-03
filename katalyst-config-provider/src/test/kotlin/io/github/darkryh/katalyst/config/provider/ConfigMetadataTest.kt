package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import kotlin.test.*

/**
 * Comprehensive tests for ConfigMetadata object.
 *
 * Tests cover:
 * - getLoaderMetadata() - extracting metadata from loaders
 * - validateLoaders() - validation of loader list
 * - LoaderMetadata data class
 * - Error handling scenarios
 * - Practical usage patterns
 *
 * Note: discoverLoaders() testing is limited as it requires reflection scanning.
 * Focus is on validation and metadata extraction which are more testable.
 */
class ConfigMetadataTest {

    // ========== TEST DATA CLASSES ==========

    data class TestConfig(
        val value: String,
        val count: Int
    )

    data class DatabaseConfig(
        val url: String,
        val username: String
    )

    // ========== TEST CONFIG PROVIDER ==========

    class TestConfigProvider(private val data: Map<String, Any> = emptyMap()) : ConfigProvider, Component {
        override fun <T> get(key: String, defaultValue: T?): T? {
            @Suppress("UNCHECKED_CAST")
            return data[key] as? T ?: defaultValue
        }

        override fun getString(key: String, default: String): String {
            return data[key]?.toString() ?: default
        }

        override fun getInt(key: String, default: Int): Int {
            val value = data[key] ?: return default
            return when (value) {
                is Int -> value
                is String -> value.toIntOrNull() ?: default
                else -> default
            }
        }

        override fun getLong(key: String, default: Long): Long {
            val value = data[key] ?: return default
            return when (value) {
                is Long -> value
                is String -> value.toLongOrNull() ?: default
                else -> default
            }
        }

        override fun getBoolean(key: String, default: Boolean): Boolean {
            return data[key] as? Boolean ?: default
        }

        override fun getList(key: String, default: List<String>): List<String> {
            @Suppress("UNCHECKED_CAST")
            return data[key] as? List<String> ?: default
        }

        override fun hasKey(key: String): Boolean = data.containsKey(key)

        override fun getAllKeys(): Set<String> = data.keys
    }

    // ========== TEST LOADER IMPLEMENTATIONS ==========

    class ValidTestConfigLoader : ServiceConfigLoader<TestConfig> {
        override fun loadConfig(provider: ConfigProvider): TestConfig {
            return TestConfig(
                value = provider.getString("test.value", "default"),
                count = provider.getInt("test.count", 0)
            )
        }

        override fun validate(config: TestConfig) {
            if (config.count < 0) {
                throw ConfigException("Count cannot be negative")
            }
        }
    }

    class FailingLoadConfigLoader : ServiceConfigLoader<TestConfig> {
        override fun loadConfig(provider: ConfigProvider): TestConfig {
            throw ConfigException("Load failed")
        }
    }

    class FailingValidateConfigLoader : ServiceConfigLoader<TestConfig> {
        override fun loadConfig(provider: ConfigProvider): TestConfig {
            return TestConfig("value", 10)
        }

        override fun validate(config: TestConfig) {
            throw ConfigException("Validation failed")
        }
    }

    class DatabaseConfigLoader : ServiceConfigLoader<DatabaseConfig> {
        override fun loadConfig(provider: ConfigProvider): DatabaseConfig {
            return DatabaseConfig(
                url = provider.getString("database.url"),
                username = provider.getString("database.username")
            )
        }

        override fun validate(config: DatabaseConfig) {
            if (config.url.isBlank()) {
                throw ConfigException("Database URL cannot be blank")
            }
        }
    }

    // ========== getLoaderMetadata() TESTS ==========

    @Test
    fun `getLoaderMetadata should return metadata for loader`() {
        // Given
        val loader = ValidTestConfigLoader()

        // When
        val metadata = ConfigMetadata.getLoaderMetadata(loader)

        // Then
        assertEquals("ValidTestConfigLoader", metadata.className)
        assertTrue(metadata.packageName.contains("io.github.darkryh.katalyst.config.provider"))
        assertNotNull(metadata.loadedType)
    }

    @Test
    fun `getLoaderMetadata should work for different loader types`() {
        // Given
        val loader1 = ValidTestConfigLoader()
        val loader2 = DatabaseConfigLoader()

        // When
        val metadata1 = ConfigMetadata.getLoaderMetadata(loader1)
        val metadata2 = ConfigMetadata.getLoaderMetadata(loader2)

        // Then
        assertEquals("ValidTestConfigLoader", metadata1.className)
        assertEquals("DatabaseConfigLoader", metadata2.className)
    }

    // ========== LoaderMetadata DATA CLASS TESTS ==========

    @Test
    fun `LoaderMetadata should be a data class with expected properties`() {
        // Given
        val metadata = ConfigMetadata.LoaderMetadata(
            className = "TestLoader",
            packageName = "com.example",
            loadedType = "String"
        )

        // Then
        assertEquals("TestLoader", metadata.className)
        assertEquals("com.example", metadata.packageName)
        assertEquals("String", metadata.loadedType)
    }

    @Test
    fun `LoaderMetadata should support equality`() {
        // Given
        val metadata1 = ConfigMetadata.LoaderMetadata("Test", "com.example", "String")
        val metadata2 = ConfigMetadata.LoaderMetadata("Test", "com.example", "String")
        val metadata3 = ConfigMetadata.LoaderMetadata("Other", "com.example", "String")

        // Then
        assertEquals(metadata1, metadata2)
        assertNotEquals(metadata1, metadata3)
    }

    @Test
    fun `LoaderMetadata should support copy`() {
        // Given
        val original = ConfigMetadata.LoaderMetadata("Test", "com.example", "String")

        // When
        val copied = original.copy(className = "Modified")

        // Then
        assertEquals("Modified", copied.className)
        assertEquals("com.example", copied.packageName)
        assertEquals("String", copied.loadedType)
    }

    // ========== validateLoaders() TESTS ==========

    @Test
    fun `validateLoaders should pass for valid loader`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "test.value" to "hello",
                "test.count" to 5
            )
        )
        val loaders = listOf(ValidTestConfigLoader())

        // When/Then - Should not throw
        ConfigMetadata.validateLoaders(provider, loaders)
    }

    @Test
    fun `validateLoaders should pass for multiple valid loaders`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "test.value" to "hello",
                "test.count" to 5,
                "database.url" to "jdbc:postgresql://localhost/db",
                "database.username" to "admin"
            )
        )
        val loaders = listOf(
            ValidTestConfigLoader(),
            DatabaseConfigLoader()
        )

        // When/Then - Should not throw
        ConfigMetadata.validateLoaders(provider, loaders)
    }

    @Test
    fun `validateLoaders should pass for empty loader list`() {
        // Given
        val provider = TestConfigProvider()
        val loaders = emptyList<ServiceConfigLoader<*>>()

        // When/Then - Should not throw
        ConfigMetadata.validateLoaders(provider, loaders)
    }

    @Test
    fun `validateLoaders should fail when loadConfig throws`() {
        // Given
        val provider = TestConfigProvider()
        val loaders = listOf(FailingLoadConfigLoader())

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigMetadata.validateLoaders(provider, loaders)
        }
        assertTrue(exception.message?.contains("validation failed") == true)
        assertTrue(exception.message?.contains("FailingLoadConfigLoader") == true)
    }

    @Test
    fun `validateLoaders should fail when validate throws`() {
        // Given
        val provider = TestConfigProvider()
        val loaders = listOf(FailingValidateConfigLoader())

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigMetadata.validateLoaders(provider, loaders)
        }
        assertTrue(exception.message?.contains("validation failed") == true)
    }

    @Test
    fun `validateLoaders should collect all errors from multiple loaders`() {
        // Given
        val provider = TestConfigProvider()
        val loaders = listOf(
            FailingLoadConfigLoader(),
            FailingValidateConfigLoader()
        )

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigMetadata.validateLoaders(provider, loaders)
        }
        // Should contain errors from both loaders
        assertTrue(exception.message?.contains("FailingLoadConfigLoader") == true)
        assertTrue(exception.message?.contains("FailingValidateConfigLoader") == true)
    }

    @Test
    fun `validateLoaders should fail when validation constraint violated`() {
        // Given
        val provider = TestConfigProvider(
            mapOf(
                "test.value" to "hello",
                "test.count" to -5  // Invalid: negative
            )
        )
        val loaders = listOf(ValidTestConfigLoader())

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigMetadata.validateLoaders(provider, loaders)
        }
        assertTrue(exception.message?.contains("Count cannot be negative") == true)
    }

    @Test
    fun `validateLoaders should fail when required config is missing`() {
        // Given
        val provider = TestConfigProvider()  // Missing database.url
        val loaders = listOf(DatabaseConfigLoader())

        // Then
        val exception = assertFailsWith<ConfigException> {
            ConfigMetadata.validateLoaders(provider, loaders)
        }
        assertTrue(exception.message?.contains("validation failed") == true)
    }

    // ========== INTEGRATION SCENARIOS ==========

    @Test
    fun `typical workflow - validate all loaders at startup`() {
        // Given - Application startup scenario
        val provider = TestConfigProvider(
            mapOf(
                "test.value" to "production",
                "test.count" to 10,
                "database.url" to "jdbc:postgresql://prod-db/katalyst",
                "database.username" to "prod_user"
            )
        )

        val loaders = listOf(
            ValidTestConfigLoader(),
            DatabaseConfigLoader()
        )

        // When - Validate all loaders
        ConfigMetadata.validateLoaders(provider, loaders)

        // Then - All loaders validated successfully
        // Each loader can now be used safely
        val testConfig = ValidTestConfigLoader().loadConfig(provider)
        val dbConfig = DatabaseConfigLoader().loadConfig(provider)

        assertEquals("production", testConfig.value)
        assertEquals(10, testConfig.count)
        assertEquals("jdbc:postgresql://prod-db/katalyst", dbConfig.url)
    }

    @Test
    fun `fail fast at startup when configuration is invalid`() {
        // Given - Invalid configuration
        val provider = TestConfigProvider(
            mapOf(
                "test.value" to "value",
                "test.count" to 5,
                "database.url" to "",  // Invalid: blank
                "database.username" to "admin"
            )
        )

        val loaders = listOf(
            ValidTestConfigLoader(),
            DatabaseConfigLoader()
        )

        // When/Then - Should fail validation at startup
        assertFailsWith<ConfigException> {
            ConfigMetadata.validateLoaders(provider, loaders)
        }
    }

    @Test
    fun `get metadata for all loaders`() {
        // Given
        val loaders = listOf(
            ValidTestConfigLoader(),
            DatabaseConfigLoader(),
            FailingLoadConfigLoader()
        )

        // When
        val metadataList = loaders.map { ConfigMetadata.getLoaderMetadata(it) }

        // Then
        assertEquals(3, metadataList.size)
        assertEquals("ValidTestConfigLoader", metadataList[0].className)
        assertEquals("DatabaseConfigLoader", metadataList[1].className)
        assertEquals("FailingLoadConfigLoader", metadataList[2].className)
    }

    @Test
    fun `validate subset of loaders`() {
        // Given - Only validate critical loaders
        val provider = TestConfigProvider(
            mapOf(
                "database.url" to "jdbc:postgresql://localhost/db",
                "database.username" to "admin"
            )
        )

        val criticalLoaders = listOf(DatabaseConfigLoader())

        // When/Then - Should validate successfully
        ConfigMetadata.validateLoaders(provider, criticalLoaders)
    }

    // ========== ERROR MESSAGE TESTS ==========

    @Test
    fun `validation error message should be descriptive`() {
        // Given
        val provider = TestConfigProvider(
            mapOf("test.count" to -1)
        )
        val loaders = listOf(ValidTestConfigLoader())

        // When
        val exception = assertFailsWith<ConfigException> {
            ConfigMetadata.validateLoaders(provider, loaders)
        }

        // Then
        assertNotNull(exception.message)
        assertTrue(exception.message!!.contains("ServiceConfigLoader validation failed"))
        assertTrue(exception.message!!.contains("ValidTestConfigLoader"))
    }

    @Test
    fun `multiple validation errors should be collected`() {
        // Given
        val provider = TestConfigProvider()
        val loaders = listOf(
            FailingLoadConfigLoader(),
            FailingValidateConfigLoader(),
            DatabaseConfigLoader()  // Will also fail due to missing config
        )

        // When
        val exception = assertFailsWith<ConfigException> {
            ConfigMetadata.validateLoaders(provider, loaders)
        }

        // Then - Should contain multiple error messages
        val message = exception.message!!
        assertTrue(message.contains("FailingLoadConfigLoader"))
        assertTrue(message.contains("FailingValidateConfigLoader"))
        assertTrue(message.contains("DatabaseConfigLoader"))
    }
}
