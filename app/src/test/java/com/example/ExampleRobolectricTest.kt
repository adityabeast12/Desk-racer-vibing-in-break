package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.game.Tracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context and verify built-in tracks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Desk Racer LAN", appName)
        assertEquals(3, Tracks.all.size)
        assertTrue(Tracks.studyDesk.samples.size >= 100)
    }
}
