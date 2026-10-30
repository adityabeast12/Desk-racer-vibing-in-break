package com.example.render

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class DrmPartMesh(
    val name: String,
    val pivotX: Float,
    val pivotY: Float,
    val pivotZ: Float,
    val buffer: FloatBuffer,
    val vertexCount: Int
) {
    val isWheel: Boolean = name.startsWith("wheel")
    val isFrontSteered: Boolean = name == "wheel-front-right" || name == "wheel-front-left" || name == "wheel-front" || name == "fork"
}

class DrmModel(
    val assetPath: String,
    val parts: List<DrmPartMesh>
)

object DrmModelLoader {
    private const val TAG = "DrmModelLoader"

    /**
     * Interleaved 10 floats per vertex: (px, py, pz, nx, ny, nz, r, g, b, paintMask)
     */
    const val DRM_STRIDE_BYTES = 10 * 4

    fun loadFromAssets(context: Context, assetPath: String): DrmModel? {
        return try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            parseDrmBytes(assetPath, bytes)
        } catch (e: Exception) {
            Log.w(TAG, "DRM model fallback to procedural for $assetPath: ${e.message}")
            null
        }
    }

    fun parseDrmBytes(assetPath: String, bytes: ByteArray): DrmModel? {
        if (bytes.size < 8) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val m0 = bb.get().toInt().toChar()
        val m1 = bb.get().toInt().toChar()
        val m2 = bb.get().toInt().toChar()
        val m3 = bb.get().toInt().toChar()
        if (m0 != 'D' || m1 != 'R' || m2 != 'M' || m3 != '1') {
            Log.w(TAG, "Invalid DRM header in $assetPath")
            return null
        }

        val numParts = bb.int
        if (numParts <= 0 || numParts > 64) return null

        val parts = ArrayList<DrmPartMesh>(numParts)
        for (p in 0 until numParts) {
            if (bb.remaining() < 4) break
            val nameLen = bb.int
            if (nameLen < 0 || nameLen > 256 || bb.remaining() < nameLen + 16) break
            val nameBytes = ByteArray(nameLen)
            bb.get(nameBytes)
            val partName = String(nameBytes, Charsets.UTF_8)

            val pivotX = bb.float
            val pivotY = bb.float
            val pivotZ = bb.float
            val vertexCount = bb.int
            if (vertexCount <= 0 || vertexCount > 65535 || bb.remaining() < vertexCount * 40 + 4) break

            val rawVerts = FloatArray(vertexCount * 10)
            for (i in 0 until vertexCount * 10) {
                rawVerts[i] = bb.float
            }

            val indexCount = bb.int
            if (indexCount <= 0 || indexCount > 200000 || bb.remaining() < indexCount * 2) break

            val unrolled = FloatArray(indexCount * 10)
            var outCursor = 0
            for (i in 0 until indexCount) {
                val idx = (bb.short.toInt() and 0xFFFF).coerceIn(0, vertexCount - 1)
                val srcBase = idx * 10
                for (f in 0 until 10) {
                    unrolled[outCursor++] = rawVerts[srcBase + f]
                }
            }

            // Align stream position to 4-byte boundary at end of part
            val alignedPos = (bb.position() + 3) and -4
            if (alignedPos <= bb.limit()) {
                bb.position(alignedPos)
            }

            val floatBuf = ByteBuffer.allocateDirect(unrolled.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(unrolled)
                    position(0)
                }

            parts.add(
                DrmPartMesh(
                    name = partName,
                    pivotX = pivotX,
                    pivotY = pivotY,
                    pivotZ = pivotZ,
                    buffer = floatBuf,
                    vertexCount = indexCount
                )
            )
        }

        return if (parts.isNotEmpty()) DrmModel(assetPath, parts) else null
    }
}
