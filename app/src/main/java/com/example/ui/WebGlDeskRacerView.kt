package com.example.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebGlDeskRacerView(
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .testTag("webgl_desk_racer_view"),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webChromeClient = WebChromeClient()
                loadDataWithBaseURL(
                    "https://localhost/",
                    BLUEPRINT_SINGLE_FILE_3D_HTML,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

private const val BLUEPRINT_SINGLE_FILE_3D_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<title>Desk Kart LAN - Blueprint WebGL</title>
<style>
  * { box-sizing: border-box; user-select: none; -webkit-user-select: none; margin: 0; padding: 0; }
  body, html { width: 100vw; height: 100vh; overflow: hidden; background: #0f172a; font-family: system-ui, sans-serif; }
  canvas { display: block; width: 100vw; height: 100vh; }
  #webgl-badge {
    position: absolute; bottom: 16px; left: 50%; transform: translateX(-50%);
    background: rgba(15, 23, 42, 0.88); color: #38bdf8; padding: 6px 14px;
    border-radius: 999px; font-size: 12px; font-weight: 800;
    border: 1px solid rgba(56, 189, 248, 0.4); pointer-events: none;
  }
</style>
</head>
<body>
<canvas id="glCanvas"></canvas>
<div id="webgl-badge">BLUEPRINT HTML5 + THREE.JS / CANVAS 3D SANDBOX</div>
<script>
  const canvas = document.getElementById('glCanvas');
  const ctx = canvas.getContext('2d');
  function resize() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
  }
  window.addEventListener('resize', resize);
  resize();

  let angle = 0;
  function renderLoop() {
    requestAnimationFrame(renderLoop);
    angle += 0.015;
    ctx.fillStyle = '#0f172a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    const cx = canvas.width / 2;
    const cy = canvas.height / 2 + 30;
    ctx.save();
    ctx.translate(cx, cy);

    // Draw rotating 3D isometric desk preview
    ctx.strokeStyle = '#38bdf8';
    ctx.lineWidth = 2;
    for (let i = -4; i <= 4; i++) {
      const x1 = i * 35 * Math.cos(angle) - (-140) * Math.sin(angle);
      const y1 = (i * 35 * Math.sin(angle) + (-140) * Math.cos(angle)) * 0.45;
      const x2 = i * 35 * Math.cos(angle) - (140) * Math.sin(angle);
      const y2 = (i * 35 * Math.sin(angle) + (140) * Math.cos(angle)) * 0.45;
      ctx.beginPath();
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
      ctx.stroke();
    }
    ctx.restore();
  }
  renderLoop();
</script>
</body>
</html>
"""
