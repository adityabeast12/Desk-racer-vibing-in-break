package com.example.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PropManifestEntry(
    val key: String,
    val file: String,
    val sizeMetersX: Float,
    val sizeMetersY: Float,
    val sizeMetersZ: Float,
    val triangles: Int,
    val source: String
)

data class ItemManifestEntry(
    val key: String,
    val file: String,
    val sizeMetersX: Float,
    val sizeMetersY: Float,
    val sizeMetersZ: Float
)

data class AssetManifestIndex(
    val unitsPerMetre: Float = 100f,
    val karts: Map<String, String> = emptyMap(),
    val pickups: Map<String, String> = emptyMap(),
    val props: Map<String, PropManifestEntry> = emptyMap(),
    val items: Map<String, ItemManifestEntry> = emptyMap(),
    val surfaces: List<String> = emptyList(),
    val backdrops: List<String> = emptyList(),
    val tracks: List<String> = emptyList(),
    val sfx: Map<String, String> = emptyMap(),
    val music: Map<String, String> = emptyMap()
)

class AssetRepository(private val context: Context?) {
    companion object {
        private const val TAG = "AssetRepository"
        private const val GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
        private const val GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF

        fun readCreditsText(context: Context?): String {
            return try {
                when {
                    context != null -> context.assets.open("CREDITS.txt").bufferedReader().use { it.readText() }
                    else -> Drm2Loader.resolveLocalAssetFile("CREDITS.txt")?.readText()
                        ?: defaultCreditsFallback()
                }
            } catch (e: Exception) {
                defaultCreditsFallback()
            }
        }

        private fun defaultCreditsFallback(): String = """
            Desk Racer LAN — Asset Credits
            ==============================
            3D Models: Khronos glTF Sample Assets & Kenney Car Kit / Racing Kit (CC0 / CC-BY 4.0)
            Surface & Backdrop Textures: Poly Haven (CC0 1.0)
            Audio: Kenney Digital / UI / Impact / RPG Audio (CC0 1.0)
            Font: Poppins by Indian Type Foundry & Jonny Pinhorn (SIL OFL 1.1)
        """.trimIndent()
    }

    val manifest: AssetManifestIndex by lazy { loadManifest() }

    private val textureCache = HashMap<String, Int>()
    private val drm1Cache = HashMap<String, DrmModel?>()
    private val drm2Cache = HashMap<String, Drm2Model?>()

    var whiteTextureId: Int = 0
        private set
    private var maxAnisotropy: Float = 1f
    private var anisotropyChecked = false

    fun onGlContextRecreated() {
        textureCache.clear()
        whiteTextureId = createSolidColorTexture(255, 255, 255, 255)
        anisotropyChecked = false
    }

    private fun checkAnisotropySupport() {
        if (anisotropyChecked) return
        anisotropyChecked = true
        try {
            val ext = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
            if (ext.contains("GL_EXT_texture_filter_anisotropic")) {
                val buf = FloatArray(1)
                GLES20.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, buf, 0)
                maxAnisotropy = buf[0].coerceIn(1f, 4f)
            }
        } catch (_: Exception) {
            maxAnisotropy = 1f
        }
    }

    fun getWhiteTexture(): Int {
        if (whiteTextureId == 0) {
            whiteTextureId = createSolidColorTexture(255, 255, 255, 255)
        }
        return whiteTextureId
    }

    fun getTextureId(assetPath: String, repeatWrap: Boolean = assetPath.startsWith("textures/surfaces/")): Int {
        if (assetPath.isBlank()) return getWhiteTexture()
        textureCache[assetPath]?.let { return it }

        val loadedId = loadTextureFromAsset(assetPath, repeatWrap)
        textureCache[assetPath] = loadedId
        return loadedId
    }

    fun getDrm1Model(assetPath: String): DrmModel? {
        if (assetPath.isBlank()) return null
        if (drm1Cache.containsKey(assetPath)) return drm1Cache[assetPath]
        val loaded = if (context != null) {
            DrmModelLoader.loadFromAssets(context, assetPath)
        } else {
            val f = Drm2Loader.resolveLocalAssetFile(assetPath)
            if (f != null) DrmModelLoader.parseDrmBytes(assetPath, f.readBytes()) else null
        }
        drm1Cache[assetPath] = loaded
        return loaded
    }

    fun getDrm2Model(assetPath: String): Drm2Model? {
        if (assetPath.isBlank()) return null
        if (drm2Cache.containsKey(assetPath)) return drm2Cache[assetPath]
        val loaded = Drm2Loader.loadFromAssets(context, assetPath)
        drm2Cache[assetPath] = loaded
        return loaded
    }

    /**
     * Warm up textures used by a track's props & surfaces so there is zero frame hitch during racing.
     */
    fun preloadModelTextures(model: Drm2Model?) {
        if (model == null) return
        for (part in model.parts) {
            if (part.texPath.isNotEmpty()) {
                getTextureId(part.texPath, repeatWrap = true)
            }
        }
    }

    private fun loadTextureFromAsset(assetPath: String, repeatWrap: Boolean): Int {
        checkAnisotropySupport()
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap: Bitmap? = try {
            when {
                context != null -> context.assets.open(assetPath).use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                else -> {
                    val file = Drm2Loader.resolveLocalAssetFile(assetPath)
                    if (file != null) BitmapFactory.decodeFile(file.absolutePath, opts) else null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Texture fallback for $assetPath: ${e.message}")
            null
        }

        if (bitmap == null) {
            return createFallbackProceduralTexture(assetPath, repeatWrap)
        }

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        if (texId == 0) {
            bitmap.recycle()
            return getWhiteTexture()
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        val wrapMode = if (repeatWrap || isPowerOfTwo(bitmap.width) && isPowerOfTwo(bitmap.height)) {
            if (repeatWrap) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE
        } else {
            GLES20.GL_CLAMP_TO_EDGE
        }
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrapMode)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrapMode)

        val pot = isPowerOfTwo(bitmap.width) && isPowerOfTwo(bitmap.height)
        if (pot) {
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR_MIPMAP_LINEAR
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        } else {
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        }

        if (maxAnisotropy > 1.0f) {
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, maxAnisotropy)
        }

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        if (pot) {
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        }
        bitmap.recycle()
        return texId
    }

    private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    private fun createSolidColorTexture(r: Int, g: Int, b: Int, a: Int): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        if (texId == 0) return 0

        val pixels = ByteBuffer.allocateDirect(4 * 4 * 4).order(ByteOrder.nativeOrder())
        repeat(16) {
            pixels.put(r.toByte())
            pixels.put(g.toByte())
            pixels.put(b.toByte())
            pixels.put(a.toByte())
        }
        pixels.position(0)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            4,
            4,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            pixels
        )
        return texId
    }

    private fun createFallbackProceduralTexture(assetPath: String, repeatWrap: Boolean): Int {
        val size = 64
        val buf = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder())
        val isChecker = assetPath.contains("checker") || assetPath.contains("chess")
        val isKerb = assetPath.contains("kerb")
        val isWood = assetPath.contains("wood") || assetPath.contains("cutting_board")
        for (y in 0 until size) {
            for (x in 0 until size) {
                val (r, g, b) = when {
                    isChecker -> {
                        val c = ((x / 8) + (y / 8)) % 2 == 0
                        if (c) Triple(245, 247, 250) else Triple(25, 30, 40)
                    }
                    isKerb -> {
                        val c = (y / 16) % 2 == 0
                        if (c) Triple(239, 68, 68) else Triple(248, 250, 252)
                    }
                    isWood -> {
                        val grain = ((kotlin.math.sin(x * 0.25) * 10).toInt())
                        Triple((120 + grain).coerceIn(0, 255), (78 + grain).coerceIn(0, 255), (46 + grain).coerceIn(0, 255))
                    }
                    else -> Triple(210, 214, 222)
                }
                buf.put(r.toByte())
                buf.put(g.toByte())
                buf.put(b.toByte())
                buf.put(255.toByte())
            }
        }
        buf.position(0)
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        if (texId == 0) return getWhiteTexture()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        val wrap = if (repeatWrap) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            size, size, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
        )
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        return texId
    }

    private fun loadManifest(): AssetManifestIndex {
        return try {
            val rawJson = when {
                context != null -> context.assets.open("asset_manifest.json").bufferedReader().use { it.readText() }
                else -> Drm2Loader.resolveLocalAssetFile("asset_manifest.json")?.readText() ?: return AssetManifestIndex()
            }
            val root = JSONObject(rawJson)
            val unitsPerMetre = root.optDouble("unitsPerMetre", 100.0).toFloat()

            fun parseStringMap(obj: JSONObject?): Map<String, String> {
                if (obj == null) return emptyMap()
                val map = LinkedHashMap<String, String>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = obj.optString(k, "")
                }
                return map
            }

            fun parseStringList(arr: org.json.JSONArray?): List<String> {
                if (arr == null) return emptyList()
                return List(arr.length()) { arr.optString(it, "") }.filter { it.isNotEmpty() }
            }

            val propsMap = LinkedHashMap<String, PropManifestEntry>()
            val propsObj = root.optJSONObject("props")
            if (propsObj != null) {
                val keys = propsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val o = propsObj.optJSONObject(k) ?: continue
                    val sz = o.optJSONArray("sizeMeters")
                    propsMap[k] = PropManifestEntry(
                        key = k,
                        file = o.optString("file", ""),
                        sizeMetersX = sz?.optDouble(0, 0.1)?.toFloat() ?: 0.1f,
                        sizeMetersY = sz?.optDouble(1, 0.1)?.toFloat() ?: 0.1f,
                        sizeMetersZ = sz?.optDouble(2, 0.1)?.toFloat() ?: 0.1f,
                        triangles = o.optInt("triangles", 0),
                        source = o.optString("source", k)
                    )
                }
            }

            val itemsMap = LinkedHashMap<String, ItemManifestEntry>()
            val itemsObj = root.optJSONObject("items")
            if (itemsObj != null) {
                val keys = itemsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val o = itemsObj.optJSONObject(k) ?: continue
                    val sz = o.optJSONArray("sizeMeters")
                    itemsMap[k] = ItemManifestEntry(
                        key = k,
                        file = o.optString("file", ""),
                        sizeMetersX = sz?.optDouble(0, 0.015)?.toFloat() ?: 0.015f,
                        sizeMetersY = sz?.optDouble(1, 0.015)?.toFloat() ?: 0.015f,
                        sizeMetersZ = sz?.optDouble(2, 0.015)?.toFloat() ?: 0.015f
                    )
                }
            }

            AssetManifestIndex(
                unitsPerMetre = unitsPerMetre,
                karts = parseStringMap(root.optJSONObject("karts")),
                pickups = parseStringMap(root.optJSONObject("pickups")),
                props = propsMap,
                items = itemsMap,
                surfaces = parseStringList(root.optJSONArray("surfaces")),
                backdrops = parseStringList(root.optJSONArray("backdrops")),
                tracks = parseStringList(root.optJSONArray("tracks")),
                sfx = parseStringMap(root.optJSONObject("sfx")),
                music = parseStringMap(root.optJSONObject("music"))
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse asset_manifest.json: ${e.message}")
            AssetManifestIndex()
        }
    }
}
