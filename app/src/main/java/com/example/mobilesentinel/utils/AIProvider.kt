package com.example.mobilesentinel.utils

import com.example.mobilesentinel.domain.models.AIExplanationRequest
import com.example.mobilesentinel.domain.models.AIExplanationResult

interface AIProvider {
    suspend fun generateExplanation(request: AIExplanationRequest): AIExplanationResult
    fun getProviderName(): String
    fun getModelName(): String
}
