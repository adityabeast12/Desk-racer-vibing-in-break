package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * Part H4: Saves a souvenir race photo to Pictures/DeskRacer via MediaStore (zero permission needed on API 29+).
 */
internal fun saveDeskRacerPhotoToMediaStore(
    context: Context,
    trackName: String,
    playerName: String
): String? {
    return try {
        val bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, 720f,
                intArrayOf(0xFF0B1020.toInt(), 0xFF1A2547.toInt(), 0xFF3B2416.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, 1280f, 720f, bgPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFB547.toInt()
            textSize = 48f
            isFakeBoldText = true
        }
        canvas.drawText("DESK RACER LAN • $trackName", 64f, 620f, textPaint)
        textPaint.color = 0xFFFFFFFF.toInt()
        textPaint.textSize = 28f
        canvas.drawText("Driver: $playerName", 64f, 664f, textPaint)

        val filename = "DeskRacer_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DeskRacer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        bmp.recycle()
        uri.toString()
    } catch (e: Exception) {
        null
    }
}
