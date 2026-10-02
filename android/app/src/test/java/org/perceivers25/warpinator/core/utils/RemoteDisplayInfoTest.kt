package org.perceivers25.warpinator.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteDisplayInfoTest {
    private fun assertLabel(
        displayName: String? = null,
        userName: String? = null,
        hostname: String? = null,
        address: String? = null,
        expectedTitle: String,
        expectedSubtitle: String,
        expectedLabel: String?,
    ) {
        val result = RemoteDisplayInfo.fromValues(
            displayName,
            userName,
            hostname,
            address,
        )

        assertEquals("Title mismatch", expectedTitle, result.title)
        assertEquals("Subtitle mismatch", expectedSubtitle, result.subtitle)
        assertEquals("Label mismatch", expectedLabel, result.label)
    }

    // When all 4 values are present test case
    // title: My Phone
    // subtitle: admin@pixel-6
    // label: 192.168.0.50
    @Test
    fun `Case 1 - All values present returns DisplayName, User@Host, and Address`() {
        assertLabel(
            displayName = "My Phone",
            userName = "admin",
            hostname = "pixel-6",
            address = "192.168.0.50",
            expectedTitle = "My Phone",
            expectedSubtitle = "admin@pixel-6",
            expectedLabel = "192.168.0.50",
        )
    }

    // --- Combinations of Size 3 ---

    @Test
    fun `Case 2 - Missing Address returns DisplayName and User@Host`() {
        assertLabel(
            displayName = "My Phone",
            userName = "admin",
            hostname = "pixel-6",
            address = null,
            expectedTitle = "My Phone",
            expectedSubtitle = "admin@pixel-6",
            expectedLabel = null,
        )
    }

    @Test
    fun `Case 3 - Missing Hostname returns DisplayName and User@Address`() {
        assertLabel(
            displayName = "My Phone",
            userName = "admin",
            hostname = null,
            address = "192.168.0.50",
            expectedTitle = "My Phone",
            expectedSubtitle = "admin@192.168.0.50",
            expectedLabel = null,
        )
    }

    @Test
    fun `Case 4 - Missing UserName returns DisplayName, Hostname, and Address`() {
        assertLabel(
            displayName = "My Phone",
            userName = null,
            hostname = "pixel-6",
            address = "192.168.0.50",
            expectedTitle = "My Phone",
            expectedSubtitle = "pixel-6",
            expectedLabel = "192.168.0.50",
        )
    }

    @Test
    fun `Case 5 - Missing DisplayName returns UserName, Hostname, and Address`() {
        assertLabel(
            displayName = null,
            userName = "admin",
            hostname = "pixel-6",
            address = "192.168.0.50",
            expectedTitle = "admin",
            expectedSubtitle = "pixel-6",
            expectedLabel = "192.168.0.50",
        )
    }

    // --- Combinations of Size 2 ---

    @Test
    fun `Case 6 - DisplayName and UserName present returns DisplayName and UserName`() {
        assertLabel(
            displayName = "My Phone",
            userName = "admin",
            hostname = null,
            address = null,
            expectedTitle = "My Phone",
            expectedSubtitle = "admin",
            expectedLabel = null,
        )
    }

    @Test
    fun `Case 7 - DisplayName and Address present returns DisplayName and Address`() {
        assertLabel(
            displayName = "My Phone",
            userName = null,
            hostname = null,
            address = "192.168.0.50",
            expectedTitle = "My Phone",
            expectedSubtitle = "192.168.0.50",
            expectedLabel = null,
        )
    }

    @Test
    fun `Case 8 - DisplayName and Hostname present returns DisplayName and Hostname`() {
        assertLabel(
            displayName = "My Phone",
            userName = null,
            hostname = "pixel-6",
            address = null,
            expectedTitle = "My Phone",
            expectedSubtitle = "pixel-6",
            expectedLabel = null,
        )
    }

    @Test
    fun `Case 9 - UserName and Hostname present returns UserName and Hostname`() {
        assertLabel(
            displayName = null,
            userName = "admin",
            hostname = "pixel-6",
            address = null,
            expectedTitle = "admin",
            expectedSubtitle = "pixel-6",
            expectedLabel = null,
        )
    }

    @Test
    fun `Case 10 - UserName and Address present returns UserName and Address`() {
        assertLabel(
            displayName = null,
            userName = "admin",
            hostname = null,
            address = "192.168.0.50",
            expectedTitle = "admin",
            expectedSubtitle = "192.168.0.50",
            expectedLabel = null,
        )
    }

    @Test
    fun `Case 11 - Hostname and Address present returns Hostname and Address`() {
        // This is a special fallback where Host becomes the Title
        assertLabel(
            displayName = null,
            userName = null,
            hostname = "pixel-6",
            address = "192.168.0.50",
            expectedTitle = "pixel-6",
            expectedSubtitle = "192.168.0.50",
            expectedLabel = null,
        )
    }

    @Test
    fun `Case 12 - Only DisplayName present repeats DisplayName as Subtitle`() {
        assertLabel(
            displayName = "My Phone",
            userName = null,
            hostname = null,
            address = null,
            expectedTitle = "My Phone",
            expectedSubtitle = "My Phone",
            expectedLabel = null,
        )
    }

    @Test
    fun `Case 13 - Only UserName present repeats UserName as Subtitle`() {
        assertLabel(
            displayName = null,
            userName = "admin",
            hostname = null,
            address = null,
            expectedTitle = "admin",
            expectedSubtitle = "admin",
            expectedLabel = null,
        )
    }

    @Test
    fun `Case 14 - Only Hostname present returns Unknown Title and Hostname Subtitle`() {
        assertLabel(
            displayName = null,
            userName = null,
            hostname = "pixel-6",
            address = null,
            expectedTitle = "Unknown",
            expectedSubtitle = "pixel-6",
            expectedLabel = null,
        )
    }

    @Test
    fun `Case 15 - Only Address present returns Unknown Title and Address Subtitle`() {
        assertLabel(
            displayName = null,
            userName = null,
            hostname = null,
            address = "192.168.0.50",
            expectedTitle = "Unknown",
            expectedSubtitle = "192.168.0.50",
            expectedLabel = null,
        )
    }
}