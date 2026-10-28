package com.example.mobilesentinel.utils

import com.example.mobilesentinel.BuildConfig
import com.example.mobilesentinel.domain.models.AIExplanationRequest
import com.example.mobilesentinel.domain.models.AIExplanationResult
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiAIProvider @Inject constructor() : AIProvider {

    private val json = Json { ignoreUnknownKeys = true }

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    override suspend fun generateExplanation(request: AIExplanationRequest): AIExplanationResult {
        val prompt = constructPrompt(request)
        val response = generativeModel.generateContent(prompt)
        val text = response.text ?: throw IllegalStateException("AI returned no text")
        
        // Robust JSON extraction
        val cleanJson = extractJson(text)
        return json.decodeFromString<AIExplanationResult>(cleanJson)
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf("{")
        val end = text.lastIndexOf("}")
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1)
        }
        return text.trim()
    }

    override fun getProviderName(): String = "Google"

    override fun getModelName(): String = "gemini-1.5-flash"

    private fun constructPrompt(request: AIExplanationRequest): String {
        return """
            You are a mobile application security analyst.
            
            Analyze ONLY the security evidence supplied in the input.
            Do not invent permissions, components, network behavior, malware families, attack techniques, or application behavior that is not present in the evidence.
            
            Explain the security significance of each finding in simple language.
            Distinguish between: security weakness, privacy concern, suspicious configuration, potential risk, and confirmed malicious behavior.
            Do NOT call an application malware unless the supplied evidence actually supports that conclusion.
            
            Explain why the finding is risky, what the potential impact is, and how the user/developer can mitigate it.
            If evidence is insufficient to conclude malicious intent, say so.
            If a field in the evidence is empty or N/A, do not invent data for it.
            
            Input Evidence:
            AppName: ${request.appName}
            PackageName: ${request.packageName}
            Version: ${request.version} (Target SDK: ${request.targetSdk})
            Installer: ${request.installerSource}
            Detected Risk Score: ${request.riskScore}/100 (Severity: ${request.severity})
            Dangerous Permissions: ${if (request.dangerousPermissions.isEmpty()) "None detected" else request.dangerousPermissions.joinToString(", ")}
            Suspicious Combinations: ${if (request.suspiciousPermissionCombinations.isEmpty()) "None detected" else request.suspiciousPermissionCombinations.joinToString(", ")}
            Debuggable: ${request.debuggableStatus}
            Backup Allowed: ${request.backupStatus}
            Exposed Components Count: ${request.exportedComponentsCount}
            Rule-based Analysis: ${request.ruleBasedDescription}
            Existing Impact: ${request.ruleBasedImpact}
            Existing Remediation: ${request.ruleBasedRemediation}
            Analysis Confidence: ${request.confidence}
            Score Contribution: ${request.scoreContribution}
            
            Return ONLY a structured JSON object in the following format:
            {
              "summary": "Short summary of the risk",
              "riskExplanation": "Detailed explanation of why this app is flagged",
              "evidenceAnalysis": ["List of evidence items interpreted"],
              "potentialImpact": "What an attacker could gain",
              "severityReason": "Why this severity was chosen",
              "scoreExplanation": "Explanation of the risk score contributors",
              "mitigation": "Overall mitigation strategy",
              "userActions": ["Step 1", "Step 2"],
              "technicalRemediation": "For developers: how to fix",
              "confidenceExplanation": "Why the analysis has this confidence level",
              "limitations": "Any limitations of this analysis"
            }
        """.trimIndent()
    }
}
