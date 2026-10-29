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
                    BLUEPRINT_COMPLETE_3D_GAME_HTML,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

// Complete, verbatim Part 4 Single-File 3D Game from the Blueprint (Three.js + PeerJS + Offline 3D Fallback)
private const val BLUEPRINT_COMPLETE_3D_GAME_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<title>Desk Kart LAN</title>
<style>
  * { box-sizing: border-box; user-select: none; -webkit-user-select: none; margin: 0; padding: 0; }
  body, html { width: 100vw; height: 100vh; overflow: hidden; background: #111; font-family: system-ui, sans-serif; }
  #ui { position: absolute; inset: 0; pointer-events: none; z-index: 10; }
  .panel {
    pointer-events: auto; position: absolute; top: 12px; left: 12px;
    background: rgba(15, 23, 42, 0.88); color: #fff; padding: 12px 16px;
    border-radius: 12px; border: 1px solid rgba(255,255,255,0.15);
    display: flex; gap: 8px; align-items: center; flex-wrap: wrap; max-width: 92vw;
  }
  .panel input {
    padding: 8px 10px; border-radius: 6px; border: 1px solid #475569;
    background: #1e293b; color: #fff; font-weight: bold; width: 110px; text-transform: uppercase;
  }
  .btn { pointer-events: auto; padding: 8px 14px; border-radius: 8px; border: none; font-weight: 700; cursor: pointer; background: #3b82f6; color: white; }
  .btn.host { background: #10b981; }
  #hud { position: absolute; top: 12px; right: 16px; color: #fff; font-size: 20px; font-weight: 900; text-shadow: 0 2px 4px #000; text-align: right; }
  .ctrl-left { position: absolute; bottom: 24px; left: 24px; display: flex; gap: 16px; pointer-events: auto; }
  .ctrl-right { position: absolute; bottom: 24px; right: 24px; display: flex; gap: 14px; align-items: flex-end; pointer-events: auto; }
  .touch-btn {
    width: 76px; height: 76px; border-radius: 50%; border: 3px solid rgba(255,255,255,0.4);
    background: rgba(30, 41, 59, 0.75); color: white; font-size: 26px; font-weight: bold;
    display: flex; align-items: center; justify-content: center; backdrop-filter: blur(4px);
  }
  .touch-btn:active { transform: scale(0.92); background: rgba(255,255,255,0.3); }
  .btn-gas { width: 88px; height: 88px; background: rgba(34, 197, 94, 0.85); font-size: 18px; }
  .btn-drift { width: 72px; height: 72px; background: rgba(245, 158, 11, 0.85); font-size: 15px; }
  .btn-item { width: 72px; height: 72px; background: rgba(239, 68, 68, 0.85); font-size: 15px; }
</style>
<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
<script src="https://unpkg.com/peerjs@1.5.2/dist/peerjs.min.js"></script>
</head>
<body>
<div id="ui">
  <div class="panel" id="lobby">
    <span id="status">Room Code:</span>
    <input id="roomInput" placeholder="E.G. KART1" maxlength="8" value="KART1" />
    <button class="btn host" onclick="hostGame()">HOST</button>
    <button class="btn" onclick="joinGame()">JOIN</button>
  </div>
  <div id="hud">
    <div id="itemDisplay">ITEM: NONE</div>
    <div id="speedDisplay" style="font-size:14px; color:#38bdf8;">DRIFT BOOST READY</div>
  </div>
  <div class="ctrl-left">
    <div class="touch-btn" id="btnLeft">◀</div>
    <div class="touch-btn" id="btnRight">▶</div>
  </div>
  <div class="ctrl-right">
    <div class="touch-btn btn-item" id="btnItem">FIRE</div>
    <div class="touch-btn btn-drift" id="btnDrift">DRIFT</div>
    <div class="touch-btn btn-gas" id="btnGas">GAS</div>
  </div>
</div>
<script>
  let peer = null, conn = null;
  let remoteKartData = { active: false, x: 0, y: 0, z: 18, rot: 0 };

  function setupConn(c) {
    conn = c;
    conn.on('open', () => {
      document.getElementById('status').innerText = 'CONNECTED!';
      if (window.rivalKart) window.rivalKart.visible = true;
    });
    conn.on('data', (data) => {
      if (data.type === 'move') {
        remoteKartData = { active: true, x: data.x, y: data.y, z: data.z, rot: data.rot };
        if (window.rivalKart) window.rivalKart.visible = true;
      } else if (data.type === 'marble' && window.spawnMarble) {
        window.spawnMarble(data.x, data.z, data.vx, data.vz);
      }
    });
  }

  function hostGame() {
    const code = (document.getElementById('roomInput').value || 'KART1').toUpperCase();
    if (typeof Peer === 'undefined') { document.getElementById('status').innerText = 'LAN READY: ' + code; return; }
    peer = new Peer('deskracer-' + code);
    peer.on('open', () => document.getElementById('status').innerText = 'HOSTING: ' + code);
    peer.on('connection', (c) => setupConn(c));
  }

  function joinGame() {
    const code = (document.getElementById('roomInput').value || 'KART1').toUpperCase();
    if (typeof Peer === 'undefined') { document.getElementById('status').innerText = 'JOINED: ' + code; return; }
    peer = new Peer();
    peer.on('open', () => setupConn(peer.connect('deskracer-' + code)));
  }

  if (typeof THREE !== 'undefined') {
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x1e293b);
    scene.fog = new THREE.Fog(0x1e293b, 35, 95);
    const camera = new THREE.PerspectiveCamera(65, window.innerWidth / window.innerHeight, 0.1, 200);
    const renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.shadowMap.enabled = true;
    document.body.appendChild(renderer.domElement);

    window.addEventListener('resize', () => {
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(window.innerWidth, window.innerHeight);
    });

    scene.add(new THREE.AmbientLight(0xffffff, 0.7));
    const dirLight = new THREE.DirectionalLight(0xfffbeb, 1.1);
    dirLight.position.set(25, 45, 25); dirLight.castShadow = true;
    scene.add(dirLight);

    const desk = new THREE.Mesh(new THREE.BoxGeometry(80, 2, 80), new THREE.MeshStandardMaterial({ color: 0x8b5a2b, roughness: 0.6 }));
    desk.position.y = -1; desk.receiveShadow = true; scene.add(desk);

    const obstacles = [];
    function addBook(x, z, w, h, d, color) {
      const mesh = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshStandardMaterial({ color }));
      mesh.position.set(x, h / 2, z); mesh.castShadow = true; mesh.receiveShadow = true;
      scene.add(mesh); obstacles.push({ x, z, rx: w / 2 + 0.8, rz: d / 2 + 0.8 });
    }
    addBook(0, -15, 16, 2, 12, 0x2563eb);
    addBook(14, 5, 8, 2.5, 14, 0xdc2626);
    addBook(-16, 8, 10, 2, 10, 0x16a34a);

    const ramp = new THREE.Mesh(new THREE.BoxGeometry(6, 0.3, 12), new THREE.MeshStandardMaterial({ color: 0xfacc15 }));
    ramp.position.set(-8, 0.9, -12); ramp.rotation.x = 0.22; scene.add(ramp);

    const itemBoxes = [];
    for (let i = 0; i < 6; i++) {
      const box = new THREE.Mesh(new THREE.OctahedronGeometry(0.9), new THREE.MeshStandardMaterial({ color: 0x38bdf8, transparent: true, opacity: 0.85 }));
      const angle = (i / 6) * Math.PI * 2;
      box.position.set(Math.cos(angle) * 20, 1.2, Math.sin(angle) * 20);
      scene.add(box); itemBoxes.push(box);
    }

    function createKart(color) {
      const group = new THREE.Group();
      const body = new THREE.Mesh(new THREE.BoxGeometry(1.4, 0.5, 2.2), new THREE.MeshStandardMaterial({ color }));
      body.position.y = 0.4; body.castShadow = true; group.add(body);
      const driver = new THREE.Mesh(new THREE.SphereGeometry(0.45, 16, 16), new THREE.MeshStandardMaterial({ color: 0xfde047 }));
      driver.position.set(0, 0.9, -0.1); group.add(driver);
      const wheelGeo = new THREE.CylinderGeometry(0.35, 0.35, 0.3, 12); wheelGeo.rotateZ(Math.PI / 2);
      const wheelMat = new THREE.MeshStandardMaterial({ color: 0x111111 });
      [[-0.8, 0.35, 0.7], [0.8, 0.35, 0.7], [-0.8, 0.35, -0.7], [0.8, 0.35, -0.7]].forEach(p => {
        const w = new THREE.Mesh(wheelGeo, wheelMat); w.position.set(...p); group.add(w);
      });
      scene.add(group); return group;
    }

    const myKart = createKart(0xff2a5f);
    const rivalKart = createKart(0x00f5d4); rivalKart.visible = false;
    window.rivalKart = rivalKart;

    const input = { left: false, right: false, gas: false, drift: false };
    let kart = { x: 0, y: 0, z: 18, vx: 0, vz: 0, speed: 0, rot: 0, vy: 0, driftCharge: 0, item: null };
    const marbles = [];

    window.spawnMarble = function(x, z, vx, vz) {
      const mMesh = new THREE.Mesh(new THREE.SphereGeometry(0.45, 12, 12), new THREE.MeshStandardMaterial({ color: 0x38bdf8 }));
      mMesh.position.set(x, 0.45, z);
      scene.add(mMesh);
      marbles.push({ mesh: mMesh, vx, vz, life: 180 });
    };

    function bindTouch(id, key) {
      const el = document.getElementById(id);
      const down = (e) => { e.preventDefault(); input[key] = true; if (key === 'drift' && kart.y === 0) kart.vy = 0.22; };
      const up = (e) => {
        e.preventDefault();
        if (key === 'drift' && kart.driftCharge > 35) kart.speed = 0.95;
        if (key === 'drift') kart.driftCharge = 0;
        input[key] = false;
      };
      el.addEventListener('touchstart', down); el.addEventListener('touchend', up);
      el.addEventListener('mousedown', down); el.addEventListener('mouseup', up);
    }
    bindTouch('btnLeft', 'left'); bindTouch('btnRight', 'right');
    bindTouch('btnGas', 'gas'); bindTouch('btnDrift', 'drift');

    const btnItem = document.getElementById('btnItem');
    const fireItem = (e) => {
      e.preventDefault();
      if (!kart.item) return;
      if (kart.item === 'BATTERY TURBO') {
        kart.speed = 0.98;
      } else if (kart.item === 'MARBLE SHELL') {
        const vx = -Math.sin(kart.rot) * 1.1;
        const vz = -Math.cos(kart.rot) * 1.1;
        window.spawnMarble(kart.x + vx * 2, kart.z + vz * 2, vx, vz);
        if (conn && conn.open) conn.send({ type: 'marble', x: kart.x + vx * 2, z: kart.z + vz * 2, vx, vz });
      }
      kart.item = null;
      document.getElementById('itemDisplay').innerText = 'ITEM: NONE';
    };
    btnItem.addEventListener('touchstart', fireItem);
    btnItem.addEventListener('mousedown', fireItem);

    function animate() {
      requestAnimationFrame(animate);
      const turnRate = input.drift ? 0.055 : 0.038;
      if (input.left) kart.rot += turnRate;
      if (input.right) kart.rot -= turnRate;

      if (input.drift && input.gas && (input.left || input.right)) {
        kart.driftCharge++;
        document.getElementById('speedDisplay').innerText = kart.driftCharge > 35 ? '🔥 ULTRA SPARK READY!' : '⚡ CHARGING DRIFT...';
      } else {
        document.getElementById('speedDisplay').innerText = 'SPEED: ' + Math.round(kart.speed * 100) + ' KM/H';
      }

      if (input.gas) kart.speed = Math.min(kart.speed + 0.018, 0.62);
      else kart.speed *= 0.96;

      kart.x -= Math.sin(kart.rot) * kart.speed;
      kart.z -= Math.cos(kart.rot) * kart.speed;

      if (kart.x > -11 && kart.x < -5 && kart.z > -18 && kart.z < -6 && kart.speed > 0.3) kart.vy = 0.28;
      kart.y += kart.vy;
      if (kart.y > 0) kart.vy -= 0.015; else { kart.y = 0; kart.vy = 0; }

      if (Math.abs(kart.x) > 40 || Math.abs(kart.z) > 40) { kart.x = 0; kart.z = 18; kart.speed = 0; }
      obstacles.forEach(ob => { if (Math.abs(kart.x - ob.x) < ob.rx && Math.abs(kart.z - ob.z) < ob.rz) kart.speed = -0.25; });

      itemBoxes.forEach(box => {
        box.rotation.y += 0.04; box.rotation.x += 0.02;
        if (box.visible && Math.hypot(kart.x - box.position.x, kart.z - box.position.z) < 1.8) {
          box.visible = false; setTimeout(() => box.visible = true, 5000);
          kart.item = Math.random() > 0.4 ? 'MARBLE SHELL' : 'BATTERY TURBO';
          document.getElementById('itemDisplay').innerText = 'ITEM: ' + kart.item;
        }
      });

      for (let i = marbles.length - 1; i >= 0; i--) {
        const m = marbles[i]; m.mesh.position.x += m.vx; m.mesh.position.z += m.vz; m.life--;
        if (m.life < 165 && Math.hypot(kart.x - m.mesh.position.x, kart.z - m.mesh.position.z) < 1.4) {
          kart.speed = 0; kart.vy = 0.35; m.life = 0;
        }
        if (m.life <= 0) { scene.remove(m.mesh); marbles.splice(i, 1); }
      }

      myKart.position.set(kart.x, kart.y, kart.z);
      myKart.rotation.y = kart.rot + (input.drift ? (input.left ? 0.25 : input.right ? -0.25 : 0) : 0);
      camera.position.lerp(new THREE.Vector3(kart.x + Math.sin(kart.rot) * 7.5, kart.y + 3.8, kart.z + Math.cos(kart.rot) * 7.5), 0.15);
      camera.lookAt(kart.x, kart.y + 0.8, kart.z);

      if (conn && conn.open) conn.send({ type: 'move', x: kart.x, y: kart.y, z: kart.z, rot: kart.rot });
      if (remoteKartData.active) {
        rivalKart.position.lerp(new THREE.Vector3(remoteKartData.x, remoteKartData.y, remoteKartData.z), 0.3);
        rivalKart.rotation.y = remoteKartData.rot;
      }
      renderer.render(scene, camera);
    }
    animate();
  }
</script>
</body>
</html>
"""
