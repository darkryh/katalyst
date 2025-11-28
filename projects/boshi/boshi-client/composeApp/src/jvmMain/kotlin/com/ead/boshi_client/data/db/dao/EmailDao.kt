package com.ead.boshi_client.data.db.dao

import androidx.room.*
import com.ead.boshi_client.data.db.entities.EmailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {
    // ==================== BASIC CRUD ====================

    @Query("SELECT * FROM emails WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: String): EmailEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(email: EmailEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(emails: List<EmailEntity>): List<Long>

    @Update
    suspend fun update(email: EmailEntity)

    @Delete
    suspend fun delete(email: EmailEntity)

    // ==================== FLOW QUERIES (for reactive updates) ====================

    @Query("SELECT * FROM emails ORDER BY timestamp DESC")
    fun getAll(): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE type = :type ORDER BY timestamp DESC")
    fun getByTypeFlow(type: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE type = :type AND status = :status ORDER BY timestamp DESC")
    fun getByTypeAndStatusFlow(type: String, status: String): Flow<List<EmailEntity>>

    // ==================== PAGINATED QUERIES ====================

    @Query("SELECT * FROM emails WHERE type = :type ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getByTypePaginated(type: String, limit: Int, offset: Int): List<EmailEntity>

    // ==================== STATS ====================

    @Query("SELECT COUNT(*) FROM emails WHERE type = :type")
    suspend fun countByType(type: String): Long

    @Query("SELECT COUNT(*) FROM emails WHERE type = :type AND status = :status")
    suspend fun countByTypeAndStatus(type: String, status: String): Long

    @Query("SELECT COUNT(*) FROM emails WHERE expiresAtMillis > 0 AND expiresAtMillis <= :nowMillis")
    suspend fun countExpired(nowMillis: Long): Long

    // ==================== UPDATE OPERATIONS ====================

    @Query("UPDATE emails SET status = :newStatus WHERE messageId = :messageId")
    suspend fun updateStatusByMessageId(messageId: String, newStatus: String)

    @Query("UPDATE emails SET status = :newStatus, expiresAtMillis = :expiresAtMillis WHERE messageId = :messageId")
    suspend fun updateStatusAndExpirationByMessageId(
        messageId: String,
        newStatus: String,
        expiresAtMillis: Long
    )

    // ==================== CLEANUP ====================

    @Query("DELETE FROM emails WHERE expiresAtMillis > 0 AND expiresAtMillis <= :nowMillis")
    suspend fun deleteExpired(nowMillis: Long): Int

    @Query("DELETE FROM emails")
    suspend fun deleteAll()
}
