package com.example.mobilesentinel.utils

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityRiskEngine @Inject constructor() {

    data class RiskMetric(
        val title: String,
        val category: String,
        val baseScore: Int,
        val description: String,
        val impact: String,
        val mitigation: String
    )

    data class ScoreBreakdown(
        val finalScore: Int,
        val contributors: List<ScoreContributor>
    )

    data class ScoreContributor(
        val key: String,
        val title: String,
        val score: Int,
        val reason: String
    )

    companion object {
        private val PERMISSION_METRICS = mapOf(
            "READ_CONTACTS" to RiskMetric(
                "Contact Access", "PRIVACY", 15,
                "Allows the app to read all contact details.",
                "Personal details, relationship mapping, and social engineering data could be harvested.",
                "Revoke permission if the app doesn't need to find friends or manage contacts."
            ),
            "WRITE_CONTACTS" to RiskMetric(
                "Modify Contacts", "PRIVACY", 10,
                "Allows the app to add, change, or delete contacts.",
                "Malicious apps could delete important contacts or inject spam entries.",
                "Only allow for trusted dialer or contact management apps."
            ),
            "RECORD_AUDIO" to RiskMetric(
                "Microphone Access", "PRIVACY", 20,
                "Allows the app to record audio at any time.",
                "Could be used for unauthorized eavesdropping and voice profiling.",
                "Revoke if the app has no legitimate recording or voice-command features."
            ),
            "CAMERA" to RiskMetric(
                "Camera Access", "PRIVACY", 20,
                "Allows the app to take photos and videos.",
                "Unauthorized surveillance and capture of sensitive documents or surroundings.",
                "Revoke if the app has no reason to take pictures."
            ),
            "ACCESS_FINE_LOCATION" to RiskMetric(
                "Precise Location", "PRIVACY", 15,
                "Allows the app to track your exact coordinates.",
                "Constant tracking of movements, home/work addresses, and habits.",
                "Use 'Approximate' location if possible, or revoke for non-location-based apps."
            ),
            "READ_SMS" to RiskMetric(
                "Read SMS Messages", "PRIVACY", 25,
                "Allows reading all incoming and stored SMS messages.",
                "Could be used to steal 2FA codes, read private conversations, and financial alerts.",
                "Revoke immediately if not a primary SMS handler."
            ),
            "SEND_SMS" to RiskMetric(
                "Send SMS Messages", "PRIVACY", 25,
                "Allows the app to send SMS messages without your intervention.",
                "Can be used for premium-rate SMS fraud or sending phishing links to contacts.",
                "Revoke immediately if the app should not be sending messages."
            ),
            "READ_CALL_LOG" to RiskMetric(
                "Call Log Access", "PRIVACY", 15,
                "Allows the app to see who you call and when.",
                "Exposes social circles, frequency of contact, and communication patterns.",
                "Revoke for apps that are not phone or utility related."
            ),
            "READ_PHONE_STATE" to RiskMetric(
                "Phone Identity", "PRIVACY", 10,
                "Allows access to device identifiers like IMEI and phone number.",
                "Persistent tracking across app reinstalls and identification of the user.",
                "Revoke if the app doesn't need to handle phone calls or network status."
            ),
            "GET_ACCOUNTS" to RiskMetric(
                "Account Discovery", "PRIVACY", 10,
                "Allows the app to see the list of accounts (Google, etc.) on the device.",
                "User email addresses can be harvested for spam or targeted attacks.",
                "Modern apps should use Google Sign-In instead of this permission."
            )
        )

        private val CONFIG_METRICS = mapOf(
            "DEBUGGABLE" to RiskMetric(
                "Debuggable Flag Enabled", "CONFIG", 40,
                "The app is configured for debugging in a production-like state.",
                "Attackers can attach a debugger, inspect memory, and bypass security checks.",
                "Developer must disable android:debuggable in the manifest for release builds."
            ),
            "ALLOW_BACKUP" to RiskMetric(
                "Backup Enabled", "CONFIG", 15,
                "The app allows its data to be backed up via ADB.",
                "Sensitive app data could be extracted by anyone with physical access to the device.",
                "Set android:allowBackup=\"false\" in the manifest if the app handles sensitive data."
            ),
            "EXPORTED_COMPONENT" to RiskMetric(
                "Exposed Component", "CONFIG", 20,
                "An internal component (Activity/Service) is accessible to other apps.",
                "Malicious apps can launch these components to bypass login screens or trigger actions.",
                "Set android:exported=\"false\" or require a custom permission."
            ),
            "SIDELOADED" to RiskMetric(
                "Unknown Source", "CONFIG", 5,
                "App was not installed from a verified store (e.g. Play Store).",
                "Sideloaded apps have not been scanned for malware by the platform's security systems.",
                "Verify the app source or uninstall if you didn't manually install it."
            ),
            "COMB_SMS_NET" to RiskMetric(
                "SMS + Internet", "PRIVACY", 15,
                "App can read SMS messages and access the internet.",
                "Highly suspicious: often used by malware to exfiltrate 2FA codes or private messages.",
                "Review if the app genuinely needs both SMS and Internet access."
            ),
            "COMB_AUDIO_NET" to RiskMetric(
                "Audio + Internet", "PRIVACY", 15,
                "App can record audio and access the internet.",
                "Potential spyware: could be used to stream or upload ambient recordings.",
                "Review microphone usage in system privacy dashboard."
            ),
            "COMB_LOC_BOOT" to RiskMetric(
                "Location + Boot", "PRIVACY", 10,
                "App tracks location and starts automatically on boot.",
                "Persistent tracking: app can monitor your movements in the background from the moment device starts.",
                "Disable 'Run at startup' or 'Location' permission."
            )
        )
    }

    fun getMetric(key: String): RiskMetric? {
        return PERMISSION_METRICS[key] ?: CONFIG_METRICS[key]
    }

    fun calculateScore(
        permissions: List<String>,
        configs: List<String>,
        isSideloaded: Boolean
    ): ScoreBreakdown {
        val contributors = mutableListOf<ScoreContributor>()
        var scoreSum = 0

        val shortPerms = permissions.map { it.substringAfterLast(".") }.toSet()

        // 1. Permissions
        shortPerms.forEach { perm ->
            PERMISSION_METRICS[perm]?.let { metric ->
                // Deduce only for non-normal permissions
                contributors.add(ScoreContributor(perm, metric.title, metric.baseScore, "Dangerous permission detected"))
                scoreSum += metric.baseScore
            }
        }

        // 2. Suspicious Combinations (Add bonus risk)
        if (shortPerms.contains("READ_SMS") && shortPerms.contains("INTERNET")) {
            val comboScore = CONFIG_METRICS["COMB_SMS_NET"]?.baseScore ?: 15
            contributors.add(ScoreContributor("COMB_SMS_NET", "SMS + Internet", comboScore, "Combination allows exfiltration of SMS data"))
            scoreSum += comboScore
        }
        if (shortPerms.contains("RECORD_AUDIO") && shortPerms.contains("INTERNET")) {
            val comboScore = CONFIG_METRICS["COMB_AUDIO_NET"]?.baseScore ?: 15
            contributors.add(ScoreContributor("COMB_AUDIO_NET", "Audio + Internet", comboScore, "Combination allows streaming microphone data"))
            scoreSum += comboScore
        }
        if (shortPerms.contains("ACCESS_FINE_LOCATION") && shortPerms.contains("RECEIVE_BOOT_COMPLETED")) {
            val comboScore = CONFIG_METRICS["COMB_LOC_BOOT"]?.baseScore ?: 10
            contributors.add(ScoreContributor("COMB_LOC_BOOT", "Location + Boot", comboScore, "Allows persistent location tracking from startup"))
            scoreSum += comboScore
        }

        // 3. Configurations
        configs.forEach { conf ->
            CONFIG_METRICS[conf]?.let { metric ->
                contributors.add(ScoreContributor(conf, metric.title, metric.baseScore, "Insecure configuration detected"))
                scoreSum += metric.baseScore
            }
        }

        // 4. Sideloading
        if (isSideloaded) {
            contributors.add(ScoreContributor("SIDELOADED", "Unknown Source", 5, "App was not installed from a verified store"))
            scoreSum += 5
        }

        val finalScore = (100 - scoreSum).coerceIn(0, 100)
        return ScoreBreakdown(finalScore, contributors)
    }

    fun getSeverity(score: Int): String {
        return when {
            score >= 80 -> "LOW"
            score >= 60 -> "MEDIUM"
            score >= 40 -> "HIGH"
            else -> "CRITICAL"
        }
    }
}
