package com.example.game

import android.content.Context
import org.json.JSONObject
import java.io.File

data class ParsedTrackJson(
    val name: String,
    val slug: String,
    val roadWidth: Float,
    val splineSubdivisionsPerSegment: Int,
    val surfaceTexture: String,
    val surfaceTextureTileUnits: Float,
    val roadTexture: String,
    val roadTextureTileUnits: Float,
    val kerbTexture: String,
    val finishTexture: String,
    val backdropEquirect: String,
    val lightingPreset: LightingPreset,
    val music: String,
    val controlPoints: List<Vec3>,
    val props: List<PlacedTrackProp>
)

object TrackJsonLoader {

    @Volatile
    var appContext: Context? = null

    fun loadTrackJson(slug: String): ParsedTrackJson? {
        val assetPath = "tracks/$slug.json"
        val rawText = readAssetText(assetPath) ?: return null
        return try {
            parseJsonString(rawText, slug)
        } catch (e: Exception) {
            null
        }
    }

    private fun readAssetText(assetPath: String): String? {
        val ctx = appContext
        if (ctx != null) {
            try {
                return ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
            } catch (_: Exception) {
            }
        }
        val candidates = arrayOf(
            File("app/src/main/assets/$assetPath"),
            File("src/main/assets/$assetPath"),
            File("../app/src/main/assets/$assetPath")
        )
        val file = candidates.firstOrNull { it.exists() && it.isFile } ?: return null
        return try {
            file.readText()
        } catch (_: Exception) {
            null
        }
    }

    fun parseJsonString(rawJson: String, fallbackSlug: String): ParsedTrackJson? {
        val obj = JSONObject(rawJson)
        val name = obj.optString("name", fallbackSlug.uppercase().replace('_', ' '))
        val slug = obj.optString("slug", fallbackSlug)
        val roadWidth = obj.optDouble("roadWidth", 12.0).toFloat()
        val subdivs = obj.optInt("splineSubdivisionsPerSegment", 18).coerceIn(8, 36)
        val surfaceTex = obj.optString("surfaceTexture", "textures/surfaces/wood_walnut.jpg")
        val surfaceTile = obj.optDouble("surfaceTextureTileUnits", 60.0).toFloat()
        val roadTex = obj.optString("roadTexture", "textures/surfaces/road_asphalt.jpg")
        val roadTile = obj.optDouble("roadTextureTileUnits", 24.0).toFloat()
        val kerbTex = obj.optString("kerbTexture", "textures/surfaces/kerb_red_white.png")
        val finishTex = obj.optString("finishTexture", "textures/surfaces/checker_finish.png")
        val backdropTex = obj.optString("backdropEquirect", "textures/backdrops/room_apartment.jpg")
        val lightingStr = obj.optString("lightingPreset", "DUSK_LAMP")
        val music = obj.optString("music", "audio/music/race_study_desk_loop.ogg")

        val cpArr = obj.optJSONArray("controlPoints") ?: return null
        val controlPoints = ArrayList<Vec3>(cpArr.length())
        for (i in 0 until cpArr.length()) {
            val pt = cpArr.optJSONArray(i) ?: continue
            if (pt.length() >= 3) {
                controlPoints.add(
                    Vec3(
                        pt.optDouble(0, 0.0).toFloat(),
                        pt.optDouble(1, 0.0).toFloat(),
                        pt.optDouble(2, 0.0).toFloat()
                    )
                )
            }
        }
        if (controlPoints.size < 4) return null

        val propsArr = obj.optJSONArray("props")
        val placedProps = ArrayList<PlacedTrackProp>(propsArr?.length() ?: 0)
        if (propsArr != null) {
            for (i in 0 until propsArr.length()) {
                val pObj = propsArr.optJSONObject(i) ?: continue
                placedProps.add(
                    PlacedTrackProp(
                        id = i,
                        model = pObj.optString("model", "models/props/traffic_cone.drm"),
                        name = pObj.optString("name", "traffic_cone"),
                        role = pObj.optString("role", "pylon"),
                        x = pObj.optDouble("x", 0.0).toFloat(),
                        y = pObj.optDouble("y", 0.0).toFloat(),
                        z = pObj.optDouble("z", 0.0).toFloat(),
                        yawRad = pObj.optDouble("yawRad", 0.0).toFloat(),
                        scale = pObj.optDouble("scale", 100.0).toFloat(),
                        radius = pObj.optDouble("radius", 2.1).toFloat(),
                        solid = pObj.optBoolean("solid", true)
                    )
                )
            }
        }

        return ParsedTrackJson(
            name = name,
            slug = slug,
            roadWidth = roadWidth,
            splineSubdivisionsPerSegment = subdivs,
            surfaceTexture = surfaceTex,
            surfaceTextureTileUnits = surfaceTile,
            roadTexture = roadTex,
            roadTextureTileUnits = roadTile,
            kerbTexture = kerbTex,
            finishTexture = finishTex,
            backdropEquirect = backdropTex,
            lightingPreset = LightingPreset.fromString(lightingStr),
            music = music,
            controlPoints = controlPoints,
            props = placedProps
        )
    }
}
