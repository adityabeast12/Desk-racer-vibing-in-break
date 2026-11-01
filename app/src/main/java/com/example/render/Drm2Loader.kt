package com.example.render

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class Drm2PartMesh(
    val name: String,
    val texPath: String,
    val baseColorFactor: FloatArray, // [r, g, b, a]
    val metallic: Float,
    val roughness: Float,
    val alphaMode: Int,              // 0 = OPAQUE, 1 = MASK (0.5), 2 = BLEND
    val doubleSided: Boolean,
    val pivotX: Float,
    val pivotY: Float,
    val pivotZ: Float,
    val vertexBuffer: FloatBuffer,
    val vertexCount: Int,
    val indexBuffer: ShortBuffer,
    val indexCount: Int,
    val lodIndexBuffer: ShortBuffer,
    val lodIndexCount: Int
)

class Drm2Model(
    val assetPath: String,
    val aabbMeters: FloatArray, // [minX, minY, minZ, maxX, maxY, maxZ]
    val parts: List<Drm2PartMesh>
) {
    val sizeMetersX: Float get() = (aabbMeters[3] - aabbMeters[0]).coerceAtLeast(0.001f)
    val sizeMetersY: Float get() = (aabbMeters[4] - aabbMeters[1]).coerceAtLeast(0.001f)
    val sizeMetersZ: Float get() = (aabbMeters[5] - aabbMeters[2]).coerceAtLeast(0.001f)
}

object Drm2Loader {
    private const val TAG = "Drm2Loader"

    /**
     * Interleaved 12 floats per vertex: (px, py, pz, nx, ny, nz, u, v, r, g, b, tint)
     */
    const val DRM2_STRIDE_BYTES = 12 * 4

    fun loadFromAssets(context: Context?, assetPath: String): Drm2Model? {
        return try {
            val bytes = when {
                context != null -> context.assets.open(assetPath).use { it.readBytes() }
                else -> {
                    val file = resolveLocalAssetFile(assetPath) ?: return null
                    file.readBytes()
                }
            }
            parseDrm2Bytes(assetPath, bytes)
        } catch (e: Exception) {
            Log.w(TAG, "DRM2 model fallback for $assetPath: ${e.message}")
            null
        }
    }

    internal fun resolveLocalAssetFile(assetPath: String): File? {
        val candidates = arrayOf(
            File("app/src/main/assets/$assetPath"),
            File("src/main/assets/$assetPath"),
            File("../app/src/main/assets/$assetPath")
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
    }

    fun parseDrm2Bytes(assetPath: String, bytes: ByteArray): Drm2Model? {
        if (bytes.size < 32) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val m0 = bb.get().toInt().toChar()
        val m1 = bb.get().toInt().toChar()
        val m2 = bb.get().toInt().toChar()
        val m3 = bb.get().toInt().toChar()
        if (m0 != 'D' || m1 != 'R' || m2 != 'M' || m3 != '2') {
            Log.w(TAG, "Invalid DRM2 magic in $assetPath: $m0$m1$m2$m3")
            return null
        }

        val partCount = bb.int
        if (partCount <= 0 || partCount > 128) return null

        val aabb = FloatArray(6)
        for (i in 0 until 6) {
            aabb[i] = bb.float
        }

        val parts = ArrayList<Drm2PartMesh>(partCount)
        for (p in 0 until partCount) {
            if (bb.remaining() < 8) break
            val nameLen = bb.int
            if (nameLen < 0 || nameLen > 512 || bb.remaining() < nameLen + 4) break
            val nameBytes = ByteArray(nameLen)
            bb.get(nameBytes)
            val partName = String(nameBytes, Charsets.UTF_8)

            val texLen = bb.int
            if (texLen < 0 || texLen > 512 || bb.remaining() < texLen + 44) break
            val texBytes = ByteArray(texLen)
            bb.get(texBytes)
            val texPath = String(texBytes, Charsets.UTF_8)

            val baseColor = floatArrayOf(bb.float, bb.float, bb.float, bb.float)
            val metallic = bb.float
            val roughness = bb.float
            val alphaMode = bb.get().toInt() and 0xFF
            val doubleSided = (bb.get().toInt() and 0xFF) != 0
            bb.get() // pad0
            bb.get() // pad1

            val pivotX = bb.float
            val pivotY = bb.float
            val pivotZ = bb.float

            val vertexCount = bb.int
            if (vertexCount <= 0 || vertexCount > 65535 || bb.remaining() < vertexCount * DRM2_STRIDE_BYTES + 4) {
                break
            }

            val floatCount = vertexCount * 12
            val vertBuf = ByteBuffer.allocateDirect(floatCount * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            val tempFloats = FloatArray(floatCount)
            bb.asFloatBuffer().get(tempFloats, 0, floatCount)
            bb.position(bb.position() + floatCount * 4)
            vertBuf.put(tempFloats)
            vertBuf.position(0)

            val indexCount = bb.int
            if (indexCount <= 0 || indexCount > 300_000 || bb.remaining() < indexCount * 2) {
                break
            }

            val tempIndices = ShortArray(indexCount)
            bb.asShortBuffer().get(tempIndices, 0, indexCount)
            bb.position(bb.position() + indexCount * 2)

            // 4-byte alignment padding at end of part
            val alignedPos = (bb.position() + 3) and -4
            if (alignedPos <= bb.limit()) {
                bb.position(alignedPos)
            }

            val idxBuf = ByteBuffer.allocateDirect(indexCount * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply {
                    put(tempIndices)
                    position(0)
                }

            // Part C8: Generate simplified LOD index buffer for high-poly props (> 6,000 tris)
            val triCount = indexCount / 3
            val lodBuf: ShortBuffer
            val lodCount: Int
            if (triCount > 6000) {
                val keepStride = 2
                val keptTris = (triCount + keepStride - 1) / keepStride
                lodCount = keptTris * 3
                val lodArr = ShortArray(lodCount)
                var dst = 0
                var t = 0
                while (t < triCount && dst + 2 < lodCount) {
                    val src = t * 3
                    lodArr[dst++] = tempIndices[src]
                    lodArr[dst++] = tempIndices[src + 1]
                    lodArr[dst++] = tempIndices[src + 2]
                    t += keepStride
                }
                lodBuf = ByteBuffer.allocateDirect(dst * 2)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
                    .apply {
                        put(lodArr, 0, dst)
                        position(0)
                    }
            } else {
                lodBuf = idxBuf
                lodCount = indexCount
            }

            parts.add(
                Drm2PartMesh(
                    name = partName,
                    texPath = texPath,
                    baseColorFactor = baseColor,
                    metallic = metallic,
                    roughness = roughness,
                    alphaMode = alphaMode,
                    doubleSided = doubleSided,
                    pivotX = pivotX,
                    pivotY = pivotY,
                    pivotZ = pivotZ,
                    vertexBuffer = vertBuf,
                    vertexCount = vertexCount,
                    indexBuffer = idxBuf,
                    indexCount = indexCount,
                    lodIndexBuffer = lodBuf,
                    lodIndexCount = lodCount
                )
            )
        }

        return if (parts.isNotEmpty()) Drm2Model(assetPath, aabb, parts) else null
    }
}
