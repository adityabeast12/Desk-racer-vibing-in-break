package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.game.KartBodyType
import com.example.game.TrackValidator
import com.example.game.Tracks
import com.example.render.DrmModelLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context and verify 8 built-in tracks and DRM assets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Desk Racer LAN", appName)
        assertEquals(8, Tracks.all.size)
        assertTrue(Tracks.studyDesk.samples.size >= 160)

        val validationResults = TrackValidator.validateAll()
        for (res in validationResults) {
            assertTrue("Track ${res.trackName} failed validation: ${res.issues}", res.isValid)
        }

        for (body in KartBodyType.entries) {
            val drm = DrmModelLoader.loadFromAssets(context, body.drmAssetPath)
            assertNotNull("Expected DRM model for ${body.name} at ${body.drmAssetPath}", drm)
            assertTrue(drm!!.parts.isNotEmpty())
        }
    }
}
