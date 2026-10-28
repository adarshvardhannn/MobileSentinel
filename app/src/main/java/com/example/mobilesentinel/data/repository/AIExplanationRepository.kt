package com.example.mobilesentinel.data.repository

import com.example.mobilesentinel.data.database.dao.AIExplanationDao
import com.example.mobilesentinel.data.database.entities.AIExplanationEntity
import com.example.mobilesentinel.domain.models.AIExplanationRequest
import com.example.mobilesentinel.domain.models.AIExplanationResult
import com.example.mobilesentinel.utils.AIProvider
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AIExplanationRepository @Inject constructor(
    private val aiExplanationDao: AIExplanationDao,
    private val aiProvider: AIProvider
) {

    open fun getLatestForFinding(findingId: Long): Flow<AIExplanationEntity?> {
        return aiExplanationDao.getLatestForFinding(findingId)
    }

    open suspend fun generateAndSave(findingId: Long, request: AIExplanationRequest): AIExplanationResult {
        try {
            val result = aiProvider.generateExplanation(request)
            
            val entity = AIExplanationEntity(
                findingId = findingId,
                summary = result.summary,
                riskExplanation = result.riskExplanation,
                potentialImpact = result.potentialImpact,
                severityReason = result.severityReason,
                scoreExplanation = result.scoreExplanation,
                mitigation = result.mitigation,
                userActions = result.userActions.joinToString("\n"),
                technicalRemediation = result.technicalRemediation,
                confidenceExplanation = result.confidenceExplanation,
                limitations = result.limitations,
                generatedAt = System.currentTimeMillis(),
                provider = aiProvider.getProviderName(),
                model = aiProvider.getModelName(),
                status = "SUCCESS"
            )
            
            aiExplanationDao.insert(entity)
            return result
        } catch (e: Exception) {
            // Fallback to error status entry or just rethrow
            throw e
        }
    }
}
