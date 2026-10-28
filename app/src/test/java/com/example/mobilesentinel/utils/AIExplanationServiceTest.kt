package com.example.mobilesentinel.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.example.mobilesentinel.data.database.dao.AIExplanationDao
import com.example.mobilesentinel.data.database.entities.Finding
import com.example.mobilesentinel.data.repository.AIExplanationRepository
import com.example.mobilesentinel.domain.models.AIExplanationRequest
import com.example.mobilesentinel.domain.models.AIExplanationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class AIExplanationServiceTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var pm: PackageManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(context.packageManager).thenReturn(pm)
    }

    @Test
    fun `test explainFinding success`() = runBlocking {
        val finding = Finding(
            id = 1,
            sessionId = 1,
            module = "VULNERABILITY",
            category = "PRIVACY",
            title = "SMS Access",
            description = "Reads SMS",
            evidence = "READ_SMS",
            severity = "HIGH",
            affectedComponent = "com.example.app",
            impact = "Steal codes",
            remediation = "Revoke",
            confidence = 1.0f,
            scoreContribution = 25,
            timestamp = System.currentTimeMillis()
        )

        val pkgInfo = PackageInfo().apply {
            packageName = "com.example.app"
            versionName = "1.0"
            applicationInfo = ApplicationInfo().apply {
                targetSdkVersion = 33
                flags = 0
            }
            requestedPermissions = arrayOf("android.permission.READ_SMS")
        }

        `when`(pm.getPackageInfo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenReturn(pkgInfo)
        `when`(pm.getApplicationLabel(ArgumentMatchers.any())).thenReturn("Test App")

        // Manual mock for repository
        val mockRepo = object : AIExplanationRepository(Mockito.mock(AIExplanationDao::class.java), Mockito.mock(AIProvider::class.java)) {
            override suspend fun generateAndSave(findingId: Long, request: AIExplanationRequest): AIExplanationResult {
                return AIExplanationResult(
                    summary = "AI Summary",
                    riskExplanation = "...",
                    evidenceAnalysis = listOf("READ_SMS"),
                    potentialImpact = "...",
                    severityReason = "...",
                    scoreExplanation = "...",
                    mitigation = "...",
                    userActions = listOf("..."),
                    technicalRemediation = "...",
                    confidenceExplanation = "...",
                    limitations = "..."
                )
            }
        }

        val service = AIExplanationService(context, mockRepo)
        val result = service.explainFinding(finding)

        assertEquals("AI Summary", result?.summary)
    }
}
