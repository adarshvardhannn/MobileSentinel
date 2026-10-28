package com.example.mobilesentinel.utils

import com.example.mobilesentinel.data.database.entities.Finding
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityExplainer @Inject constructor() {

    fun generateSummary(findings: List<Finding>): String {
        if (findings.isEmpty()) return "No significant security risks were detected. Your device follows basic security best practices."

        val criticalCount = findings.count { it.severity == "CRITICAL" }
        val highCount = findings.count { it.severity == "HIGH" }
        
        return when {
            criticalCount > 0 -> "Action required: $criticalCount critical security threats detected that could lead to full device compromise."
            highCount > 0 -> "Warning: $highCount high-risk findings detected. These apps or configurations significantly increase your attack surface."
            else -> "Review recommended: Some low to medium risks were found. These are generally privacy concerns or non-optimal security settings."
        }
    }

    fun getAIStyleExplanation(finding: Finding): String {
        return buildString {
            appendLine("### Security Analysis: ${finding.title}")
            appendLine("**Risk Level:** ${finding.severity}")
            appendLine("**Category:** ${finding.category}")
            appendLine()
            appendLine("#### What was detected?")
            appendLine(finding.description)
            appendLine("*Evidence:* ${finding.evidence}")
            appendLine()
            appendLine("#### Why is this a risk?")
            appendLine(finding.impact)
            appendLine()
            appendLine("#### Potential Impact")
            appendLine("If exploited, an attacker could potentially achieve the following:")
            appendLine("- Access unauthorized data associated with the ${finding.category.lowercase()} category.")
            appendLine("- Persist on the device or bypass standard Android security boundaries.")
            appendLine()
            appendLine("#### Recommended Action")
            appendLine("**User:** ${finding.remediation}")
            appendLine("**Confidence:** ${(finding.confidence * 100).toInt()}%")
        }
    }
}
