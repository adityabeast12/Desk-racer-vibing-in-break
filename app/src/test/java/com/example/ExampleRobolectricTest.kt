package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FakeBillingGateway
import com.example.data.Prefs
import com.example.data.ShopCatalog
import com.example.game.KartBodyType
import com.example.game.TrackValidator
import com.example.game.Tracks
import com.example.render.AssetRepository
import com.example.render.Drm2Loader
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
    fun `read string from context and verify 8 built-in tracks, DRM1, DRM2 and v3 assets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Desk Racer LAN", appName)
        assertEquals(8, Tracks.all.size)
        assertTrue(Tracks.midnightOffice.name.equals("Midnight Office", ignoreCase = true))
        assertTrue(Tracks.studyDesk.samples.size >= 160)

        val validationResults = TrackValidator.validateAll()
        for (res in validationResults) {
            assertTrue("Track ${res.trackName} failed validation: ${res.issues}", res.isValid)
        }

        // Verify all 6 Kart bodies (5 DRM1 + 1 DRM2 Classic Toy)
        for (body in KartBodyType.entries) {
            if (body.isDrm2) {
                val drm2 = Drm2Loader.loadFromAssets(context, body.drmAssetPath)
                assertNotNull("Expected DRM2 model for ${body.name} at ${body.drmAssetPath}", drm2)
                assertTrue(drm2!!.parts.isNotEmpty())
            } else {
                val drm1 = DrmModelLoader.loadFromAssets(context, body.drmAssetPath)
                assertNotNull("Expected DRM1 model for ${body.name} at ${body.drmAssetPath}", drm1)
                assertTrue(drm1!!.parts.isNotEmpty())
            }
        }

        // Verify AssetRepository manifest and CREDITS.txt
        val repo = AssetRepository(context)
        assertTrue("Expected props in asset_manifest.json", repo.manifest.props.isNotEmpty())
        assertTrue("Expected items in asset_manifest.json", repo.manifest.items.isNotEmpty())
        val credits = AssetRepository.readCreditsText(context)
        assertTrue("Expected non-empty CREDITS.txt", credits.contains("Poppins"))

        // Verify HMAC wallet & FakeBillingGateway
        val prefs = Prefs(context)
        val billing = FakeBillingGateway()
        var purchaseOk = false
        billing.purchaseProduct(ShopCatalog.SKU_PRO_UPGRADE, prefs) { ok, _ ->
            purchaseOk = ok
        }
        assertTrue(purchaseOk)
        assertTrue(prefs.state.value.hasProUpgrade)
    }
}
