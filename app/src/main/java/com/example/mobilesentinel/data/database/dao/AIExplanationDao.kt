package com.example.mobilesentinel.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mobilesentinel.data.database.entities.AIExplanationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AIExplanationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(explanation: AIExplanationEntity): Long

    @Query("SELECT * FROM ai_explanations WHERE findingId = :findingId ORDER BY generatedAt DESC LIMIT 1")
    fun getLatestForFinding(findingId: Long): Flow<AIExplanationEntity?>

    @Query("SELECT * FROM ai_explanations WHERE findingId = :findingId ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatestForFindingSync(findingId: Long): AIExplanationEntity?

    @Query("DELETE FROM ai_explanations WHERE findingId = :findingId")
    suspend fun deleteForFinding(findingId: Long)
}
