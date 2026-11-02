package com.example

import com.example.data.ShopCatalog
import com.example.game.KartBodyType
import com.example.game.TrackValidator
import com.example.game.Tracks
import com.example.render.AssetRepository
import com.example.render.Drm2Loader
import com.example.render.DrmModelLoader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `verify strings xml, 8 built-in tracks, DRM1, DRM2 and v4 assets offline`() {
        val stringsFile = listOf(
            File("app/src/main/res/values/strings.xml"),
            File("src/main/res/values/strings.xml")
        ).firstOrNull { it.exists() }
        assertNotNull("Expected strings.xml on disk", stringsFile)
        val stringsXml = stringsFile!!.readText()
        assertTrue(stringsXml.contains("<string name=\"app_name\">Desk Racer LAN</string>"))
        assertTrue(stringsXml.contains("hud_finished"))

        assertEquals(8, Tracks.all.size)
        assertTrue(Tracks.midnightOffice.name.equals("Midnight Office", ignoreCase = true))
        assertTrue(Tracks.studyDesk.samples.size >= 160)

        val validationResults = TrackValidator.validateAll()
        for (res in validationResults) {
            assertTrue("Track ${res.trackName} failed validation: ${res.issues}", res.isValid)
        }

        // Verify all 6 Kart bodies (5 DRM1 + 1 DRM2 Classic Toy) offline
        for (body in KartBodyType.entries) {
            if (body.isDrm2) {
                val drm2 = Drm2Loader.loadFromAssets(null, body.drmAssetPath)
                assertNotNull("Expected DRM2 model for ${body.name} at ${body.drmAssetPath}", drm2)
                assertTrue(drm2!!.parts.isNotEmpty())
            } else {
                val localFile = Drm2Loader.resolveLocalAssetFile(body.drmAssetPath)
                assertNotNull("Expected local asset file for ${body.drmAssetPath}", localFile)
                val drm1 = DrmModelLoader.parseDrmBytes(body.drmAssetPath, localFile!!.readBytes())
                assertNotNull("Expected DRM1 model for ${body.name} at ${body.drmAssetPath}", drm1)
                assertTrue(drm1!!.parts.isNotEmpty())
            }
        }

        // Verify asset_manifest.json and CREDITS.txt on disk
        val manifestFile = Drm2Loader.resolveLocalAssetFile("asset_manifest.json")
        assertNotNull("Expected asset_manifest.json on disk", manifestFile)
        val manifestText = manifestFile!!.readText()
        assertTrue("Expected props in asset_manifest.json", manifestText.contains("\"props\""))
        assertTrue("Expected items in asset_manifest.json", manifestText.contains("\"items\""))
        val credits = AssetRepository.readCreditsText(null)
        assertTrue("Expected non-empty CREDITS.txt", credits.contains("Poppins"))

        // Verify ShopCatalog SKUs
        assertTrue(ShopCatalog.iapOffers.any { it.sku == ShopCatalog.SKU_PRO_UPGRADE })
    }
}
