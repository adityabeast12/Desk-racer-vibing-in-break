"""Render DRM models with three.js in headless Chromium. Usage: shoot.py jobs.json
job: {"m": "models/props/x.drm[,..]", "out": "path.png", "w":512, "h":512, "bg":"none"|"", "yaw":35, "pitch":22, "rot":0, "tint":"#ff3d7f", "zoom":1.08, "floor":1, "root":"/assetpack_v3/"}"""
import json, sys, subprocess, time, urllib.parse, os, socket
from playwright.sync_api import sync_playwright

def free_port():
    s = socket.socket(); s.bind(('127.0.0.1', 0)); p = s.getsockname()[1]; s.close(); return p

def run(jobs):
    port = free_port()
    srv = subprocess.Popen([sys.executable, '-m', 'http.server', str(port), '--bind', '127.0.0.1'], cwd='/home/claude', stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.0)
    try:
        with sync_playwright() as p:
            b = p.chromium.launch(args=['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
            for j in jobs:
                w, h = j.get('w', 512), j.get('h', 512)
                pg = b.new_page(viewport={'width': w, 'height': h})
                errs = []; pg.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None); pg.on('pageerror', lambda e: errs.append(str(e)))
                qs = urllib.parse.urlencode({k: v for k, v in j.items() if k != 'out'})
                pg.goto(f'http://127.0.0.1:{port}/render3d/view.html?{qs}')
                try:
                    pg.wait_for_function("document.title==='done'", timeout=120000)
                except Exception:
                    print('TIMEOUT', j['out'], errs[:3]); pg.close(); continue
                os.makedirs(os.path.dirname(j['out']), exist_ok=True)
                pg.locator('canvas').screenshot(path=j['out'], omit_background=(j.get('bg') == 'none'))
                if errs: print('warn', j['out'], errs[:2])
                pg.close()
            b.close()
    finally:
        srv.terminate()

if __name__ == '__main__':
    run(json.load(open(sys.argv[1])))
