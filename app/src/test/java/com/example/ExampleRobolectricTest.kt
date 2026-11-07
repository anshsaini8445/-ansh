package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.helpers.CompanionBreed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Hybrid Player", appName)
    }

    @Test
    fun `mainActivity launches without crash and displays saved 3D fluffy puppy and teddy mascots in Me tab`() {
        val fluffyMascots = CompanionBreed.FLUFFY_CASSETTE_MASCOTS
        assertEquals(10, fluffyMascots.size)
        assertTrue(fluffyMascots.any { it.isTeddyBear && it.cassetteLabel == "HONEY TAPE" })
        assertTrue(fluffyMascots.any { !it.isTeddyBear && it.cassetteLabel == "SKY PUPPY" })
        assertEquals(10, fluffyMascots.map { it.cassetteLabel }.distinct().size)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_tab_me").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("me_saved_mascots_section").assertExists()
    }
}
