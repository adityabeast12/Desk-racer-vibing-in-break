"""v4 texture additions: normal maps for every surface, decals with alpha, garage turntable, track preview cards."""
import numpy as np, os, sys, json, math, importlib.util
from PIL import Image, ImageDraw, ImageFilter, ImageFont
ROOT = sys.argv[1]
SURF = f'{ROOT}/textures/surfaces'; DEC = f'{ROOT}/textures/decals'; UI = f'{ROOT}/textures/ui'
for d in (DEC, UI, f'{UI}/tracks'): os.makedirs(d, exist_ok=True)
FONT = '/home/claude/assets_src/gfonts/ofl/poppins/Poppins-Bold.ttf'
FONTB = '/home/claude/assets_src/gfonts/ofl/poppins/Poppins-Black.ttf'

def normal_from(img_path, out, strength=2.5, invert=False, blur=0.8):
    a = np.asarray(Image.open(img_path).convert('L').filter(ImageFilter.GaussianBlur(blur)), np.float32) / 255.0
    if invert: a = 1 - a
    gx = (np.roll(a, -1, 1) - np.roll(a, 1, 1)) * strength; gy = (np.roll(a, -1, 0) - np.roll(a, 1, 0)) * strength
    n = np.stack([-gx, -gy, np.ones_like(a)], -1); n /= np.linalg.norm(n, axis=-1, keepdims=True)
    Image.fromarray(((n * 0.5 + 0.5) * 255).astype(np.uint8)).resize((512, 512), Image.LANCZOS).save(out, quality=90)

# 1) normal maps for every tiling surface (height ~ darker = lower)
spec = {'wood_walnut': 2.0, 'wood_oak_light': 2.0, 'marble_white': 1.2, 'tiles_bath': 6.0, 'paper_ruled': 0.8, 'paper_paint': 1.5,
        'cardboard_kraft': 3.0, 'desk_mat_dark': 4.0, 'rug_playroom': 5.0, 'road_asphalt': 6.0, 'cutting_board': 2.5, 'chessboard': 1.5}
for k, st in spec.items():
    normal_from(f'{SURF}/{k}.jpg', f'{SURF}/{k}_n.jpg', st)

rng = np.random.default_rng(5)
def save_rgba(arr, path):
    Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8), 'RGBA').save(path, optimize=True)

# 2) decals
N = 512; y, x = (np.mgrid[0:N, 0:N] - N / 2) / (N / 2)
r = np.hypot(x, y)
# coffee ring stain
ring = np.exp(-((r - 0.78) / 0.05) ** 2) * 0.85 + np.exp(-((r - 0.72) / 0.12) ** 2) * 0.25 + (r < 0.75) * 0.08
noise = np.asarray(Image.fromarray((rng.random((64, 64)) * 255).astype(np.uint8)).resize((N, N), Image.BICUBIC), np.float32) / 255
ring *= 0.75 + 0.5 * noise
save_rgba(np.dstack([np.full_like(r, 0.36), np.full_like(r, 0.20), np.full_like(r, 0.08), np.clip(ring, 0, 0.9)]), f'{DEC}/coffee_ring.png')
# tyre skid strip (tileable along v)
W, H = 64, 512; sk = np.zeros((H, W, 4), np.float32)
tread = (np.sin(np.arange(H) / H * np.pi * 2 * 40)[:, None] * 0.5 + 0.5) * 0.35 + 0.45
edge = np.clip(1 - np.abs(np.linspace(-1, 1, W)) ** 4, 0, 1)[None, :]
sk[..., 3] = tread * edge * 0.85; sk[..., :3] = 0.05
save_rgba(sk, f'{DEC}/skid_mark.png')
# scuff / dust patch
sc = np.clip((1 - r) * 1.4, 0, 1) * (0.4 + 0.6 * noise) * 0.55
save_rgba(np.dstack([np.full_like(r, 0.55), np.full_like(r, 0.5), np.full_like(r, 0.45), sc]), f'{DEC}/scuff_dust.png')
# sticky notes with doodles
def sticky(col, name, doodle):
    im = Image.new('RGBA', (512, 512), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rectangle([30, 36, 492, 498], fill=(0, 0, 0, 60))  # soft shadow
    im = im.filter(ImageFilter.GaussianBlur(10)); d = ImageDraw.Draw(im)
    d.rectangle([20, 20, 480, 480], fill=col + (255,))
    d.rectangle([20, 20, 480, 80], fill=tuple(max(0, c - 18) for c in col) + (255,))
    f = ImageFont.truetype(FONT, 54)
    d.text((60, 140), doodle[0], fill=(40, 40, 60, 255), font=f); d.text((60, 230), doodle[1], fill=(40, 40, 60, 255), font=f)
    d.line([(60, 360), (420, 345)], fill=(200, 40, 60, 255), width=8)
    im.save(f'{DEC}/{name}.png', optimize=True)
sticky((255, 230, 102), 'sticky_yellow', ('TURN', 'LEFT!'))
sticky((255, 153, 194), 'sticky_pink', ('BOOST', '>>>'))
sticky((140, 214, 255), 'sticky_blue', ('LAP 3', 'GO GO'))
# paint splats (3 colours)
for col, name in (((231, 76, 60), 'paint_splat_red'), ((52, 152, 219), 'paint_splat_blue'), ((241, 196, 15), 'paint_splat_yellow')):
    im = Image.new('RGBA', (512, 512), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.ellipse([136, 136, 376, 376], fill=col + (255,))
    for k in range(26):
        a = rng.uniform(0, 6.283); dd = rng.uniform(110, 230); rr = rng.uniform(8, 38)
        cx, cy = 256 + math.cos(a) * dd, 256 + math.sin(a) * dd
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=col + (255,))
        d.line([(256, 256), (cx, cy)], fill=col + (255,), width=int(rr * 0.8))
    im.filter(ImageFilter.GaussianBlur(1.2)).save(f'{DEC}/{name}.png', optimize=True)
# boost chevrons pad
im = Image.new('RGBA', (256, 512), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
for k in range(3):
    y0 = 360 - k * 150
    d.polygon([(28, y0 + 90), (128, y0), (228, y0 + 90), (228, y0 + 140), (128, y0 + 50), (28, y0 + 140)], fill=(45, 226, 230, 235))
im.save(f'{DEC}/boost_chevrons.png', optimize=True)
# start grid box markings
im = Image.new('RGBA', (256, 128), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.rectangle([8, 8, 248, 120], outline=(255, 255, 255, 230), width=10); im.save(f'{DEC}/grid_box.png')

# 3) garage turntable floor (radial studio platform)
N = 1024; y, x = (np.mgrid[0:N, 0:N] - N / 2) / (N / 2); r = np.hypot(x, y)
base = 0.10 + 0.05 * (1 - r)
rings = (np.abs(((r * 12) % 1) - 0.5) < 0.012) * 0.10 * (r < 0.92)
rim = np.exp(-((r - 0.93) / 0.012) ** 2) * 0.9
col = np.dstack([base + rings + rim * 1.0, base + rings + rim * 0.71, base * 1.2 + rings + rim * 0.28])
alpha = np.clip((1.0 - r) * 12, 0, 1)
save_rgba(np.dstack([col, alpha]), f'{UI}/garage_turntable.png')

# 4) track preview cards: surface texture + road ribbon + start/finish + name, 640x360
spec_t = importlib.util.spec_from_file_location('t', '/home/claude/tools/tracks_design.py'); T = importlib.util.module_from_spec(spec_t); spec_t.loader.exec_module(T)
for tf in sorted(os.listdir(f'{ROOT}/tracks')):
    tj = json.load(open(f'{ROOT}/tracks/{tf}')); pts = T.build(tj['controlPoints'])
    W, H = 640, 360
    bg = Image.open(f"{ROOT}/{tj['surfaceTexture']}").convert('RGB').resize((W, W)).crop((0, (W - H) // 2, W, (W + H) // 2))
    bg = Image.blend(bg, Image.new('RGB', (W, H), (11, 16, 32)), 0.45)
    xs, zs = pts[:, 0], pts[:, 2]; mnx, mxx, mnz, mxz = xs.min(), xs.max(), zs.min(), zs.max()
    sc = min((W * 0.62) / (mxx - mnx), (H * 0.80) / (mxz - mnz)); ox = W * 0.62 - (mxx + mnx) / 2 * sc; oz = H / 2 + (mxz + mnz) / 2 * sc
    P = [(ox + px * sc, oz - pz * sc) for px, pz in zip(xs, zs)]; P.append(P[0])
    d = ImageDraw.Draw(bg, 'RGBA')
    d.line(P, fill=(0, 0, 0, 120), width=int(tj['roadWidth'] * sc * 1.0) + 8, joint='curve')
    d.line(P, fill=(245, 245, 250, 255), width=int(tj['roadWidth'] * sc * 0.75), joint='curve')
    d.line(P, fill=(255, 181, 71, 255), width=3, joint='curve')
    for pr in tj['props']:
        if pr['role'] == 'landmark':
            cx, cy = ox + pr['x'] * sc, oz - pr['z'] * sc; rr = max(3, pr['radius'] * sc)
            d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(255, 61, 127, 200))
    sx, sy = P[0]; d.rectangle([sx - 7, sy - 7, sx + 7, sy + 7], fill=(255, 255, 255, 255), outline=(17, 17, 17, 255), width=3)
    d.rectangle([0, H - 64, W, H], fill=(11, 16, 32, 200))
    d.text((20, H - 58), tj['name'], font=ImageFont.truetype(FONTB, 30), fill=(255, 255, 255, 255))
    d.text((W - 210, H - 50), f"≈ {round(tj['lengthApprox'] / 24)} s lap", font=ImageFont.truetype(FONT, 20), fill=(45, 226, 230, 255))
    bg.save(f"{UI}/tracks/{tj['slug']}.jpg", quality=88)
print('ok', sorted(os.listdir(DEC)), sorted(os.listdir(f'{UI}/tracks')))
