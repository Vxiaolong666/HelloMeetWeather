package com.zhisheng.weather.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityGroupTest {
    @Test fun `join fallback retains the selected group`() {
        val selected = CommunityGroups.last()
        val attempts = mutableListOf<Pair<String, String?>>()
        assertTrue(openCommunityGroupLink(selected) { url, target ->
            attempts += url to target
            target == null
        })
        assertEquals(listOf(selected.joinUrl to "com.tencent.mobileqq", selected.joinUrl to null), attempts)
    }

    @Test fun `successful QQ launch does not also open a browser`() {
        var attempts = 0
        assertTrue(openCommunityGroupLink(CommunityGroups.first()) { _, _ -> attempts++; true })
        assertEquals(1, attempts)
    }

    @Test fun `unavailable handlers report a failed launch`() {
        assertFalse(openCommunityGroupLink(CommunityGroups.first()) { _, _ -> false })
    }
}
