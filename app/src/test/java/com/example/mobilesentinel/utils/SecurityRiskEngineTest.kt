package com.example.mobilesentinel.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecurityRiskEngineTest {

    private lateinit var engine: SecurityRiskEngine

    @Before
    fun setup() {
        engine = SecurityRiskEngine()
    }

    @Test
    fun `test initial score is 100`() {
        val result = engine.calculateScore(emptyList(), emptyList(), false)
        assertEquals(100, result.finalScore)
        assertTrue(result.contributors.isEmpty())
    }

    @Test
    fun `test dangerous permission deduction`() {
        val result = engine.calculateScore(listOf("READ_SMS"), emptyList(), false)
        // READ_SMS is 25 points
        assertEquals(75, result.finalScore)
        assertEquals(1, result.contributors.size)
        assertEquals("Read SMS Messages", result.contributors[0].title)
    }

    @Test
    fun `test suspicious combination deduction`() {
        val result = engine.calculateScore(listOf("READ_SMS", "INTERNET"), emptyList(), false)
        // READ_SMS (25) + INTERNET (0) + Combination (15) = 40
        assertEquals(60, result.finalScore)
        assertTrue(result.contributors.any { it.title == "SMS + Internet" })
    }

    @Test
    fun `test configuration deduction`() {
        val result = engine.calculateScore(emptyList(), listOf("DEBUGGABLE"), false)
        // DEBUGGABLE is 40 points
        assertEquals(60, result.finalScore)
    }

    @Test
    fun `test sideloading deduction`() {
        val result = engine.calculateScore(emptyList(), emptyList(), true)
        // Sideloading is 5 points
        assertEquals(95, result.finalScore)
    }

    @Test
    fun `test maximum deduction capped at 0`() {
        val perms = listOf("READ_SMS", "SEND_SMS", "RECORD_AUDIO", "CAMERA", "ACCESS_FINE_LOCATION")
        val result = engine.calculateScore(perms, listOf("DEBUGGABLE"), true)
        assertEquals(0, result.finalScore)
    }

    @Test
    fun `test severity mapping`() {
        assertEquals("LOW", engine.getSeverity(90))
        assertEquals("MEDIUM", engine.getSeverity(70))
        assertEquals("HIGH", engine.getSeverity(50))
        assertEquals("CRITICAL", engine.getSeverity(20))
    }
}
