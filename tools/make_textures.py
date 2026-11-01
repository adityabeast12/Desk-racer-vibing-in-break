"""Procedural, tileable surface textures for Desk Racer tracks (original, CC0 by the project).
Outputs 1024x1024 JPGs (albedo) + 512 normal maps for key surfaces."""
import numpy as np, os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
N = 1024
rng = np.random.default_rng(42)

def tile_noise(n, scale, octaves=5, seed=0):
    r = np.random.default_rng(seed); acc = np.zeros((n, n)); amp = 1.0; tot = 0
    for o in range(octaves):
        g = max(2, int(scale * 2 ** o))
        base = r.random((g, g))
        img = Image.fromarray((base * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC)
        # make tileable by sampling a wrapped grid
        big = np.tile(base, (3, 3)); bi = Image.fromarray((big * 255).astype(np.uint8)).resize((n * 3, n * 3), Image.BICUBIC)
        a = np.asarray(bi, float)[n:2 * n, n:2 * n] / 255.0
        acc += a * amp; tot += amp; amp *= 0.5
    return acc / tot

def save(name, arr):
    Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8)).save(os.path.join(OUT, name), quality=88)

def normal_from_height(h, strength=2.0):
    gx = np.roll(h, -1, 1) - np.roll(h, 1, 1); gy = np.roll(h, -1, 0) - np.roll(h, 1, 0)
    nx, ny, nz = -gx * strength, -gy * strength, np.ones_like(h)
    l = np.sqrt(nx ** 2 + ny ** 2 + nz ** 2)
    return np.stack([nx / l * 0.5 + 0.5, ny / l * 0.5 + 0.5, nz / l * 0.5 + 0.5], -1)

y, x = np.mgrid[0:N, 0:N] / N

# 1. Walnut desk planks
def wood(base, dark, plank_count=6, seed=1, name='wood_walnut.jpg'):
    n1 = tile_noise(N, 3, 5, seed); n2 = tile_noise(N, 24, 3, seed + 7)
    plank = np.floor(x * plank_count)
    offs = np.random.default_rng(seed).random(plank_count + 1)[plank.astype(int)]
    rings = np.sin((y * 9 + offs * 13 + n1 * 3.5) * np.pi * 2 * 2.2) * 0.5 + 0.5
    grain = rings ** 3 * 0.55 + n2 * 0.25 + (np.sin(y * 340 + n1 * 30) * 0.5 + 0.5) * 0.12
    seam = (np.abs((x * plank_count) % 1 - 0.0) < 0.004) | (np.abs((x * plank_count) % 1 - 1.0) < 0.004)
    tone = (offs * 0.18 - 0.09)[..., None]
    col = base[None, None] * (1 - grain[..., None]) + dark[None, None] * grain[..., None] + tone
    col[seam] *= 0.45
    save(name, col); return grain

g = wood(np.array([0.42, 0.26, 0.15]), np.array([0.20, 0.11, 0.06]))
save('wood_walnut_n.jpg', np.asarray(Image.fromarray((normal_from_height(g, 1.5) * 255).astype(np.uint8)).resize((512, 512))) / 255.0)
wood(np.array([0.78, 0.62, 0.42]), np.array([0.55, 0.40, 0.24]), 5, 3, 'wood_oak_light.jpg')
wood(np.array([0.70, 0.52, 0.33]), np.array([0.48, 0.32, 0.18]), 1, 9, 'cutting_board.jpg')

# 2. White marble
n1 = tile_noise(N, 2, 6, 11); n2 = tile_noise(N, 6, 5, 12)
veins = np.abs(np.sin((x * 3 + y * 2 + n1 * 4.5) * np.pi)) ** 0.18
veins2 = np.abs(np.sin((x * -2 + y * 5 + n2 * 6) * np.pi)) ** 0.35
m = 0.93 - (1 - veins) * 0.35 - (1 - veins2) * 0.12 + (n2 - 0.5) * 0.05
save('marble_white.jpg', np.stack([m * 0.98, m * 0.97, m * 1.0], -1))

# 3. Ceramic bathroom tiles (8x8) with grout
t = 8; gx = (x * t) % 1; gy = (y * t) % 1
grout = (gx < 0.035) | (gx > 0.965) | (gy < 0.035) | (gy > 0.965)
glaze = 0.90 + (tile_noise(N, 16, 3, 21) - 0.5) * 0.06
edge = np.minimum(np.minimum(gx, 1 - gx), np.minimum(gy, 1 - gy))
bevel = np.clip(edge * 25, 0, 1) * 0.08
col = np.stack([glaze * 0.92 + bevel, glaze * 0.97 + bevel, glaze * 1.0 + bevel], -1)
col[grout] = [0.62, 0.62, 0.60]
save('tiles_bath.jpg', col)

# 4. Notebook ruled paper (road surface for Study Desk)
p = 0.96 + (tile_noise(N, 40, 3, 31) - 0.5) * 0.04
col = np.stack([p * 0.99, p * 0.98, p * 0.93], -1)
lines = (np.abs((y * 24) % 1 - 0.5) < 0.02)
col[lines] = [0.62, 0.74, 0.90]
col[(np.abs(x - 0.12) < 0.0025)] = [0.86, 0.35, 0.35]
save('paper_ruled.jpg', col)

# 5. Kraft cardboard
n = tile_noise(N, 30, 4, 41); fib = tile_noise(N, 120, 2, 42)
k = 0.62 + (n - 0.5) * 0.12 + (fib - 0.5) * 0.08
save('cardboard_kraft.jpg', np.stack([k * 1.0, k * 0.78, k * 0.52], -1))

# 6. Gamer desk mat (dark fabric weave + subtle RGB edge)
w = (np.sin(x * N * 0.9) * np.sin(y * N * 0.9)) * 0.5 + 0.5
d = 0.08 + w * 0.03 + (tile_noise(N, 50, 3, 51) - 0.5) * 0.02
save('desk_mat_dark.jpg', np.stack([d, d * 1.02, d * 1.12], -1))

# 7. Kid rug (soft pile with soft stripes)
nr = tile_noise(N, 80, 3, 61)
band = (np.floor(y * 6) % 3)
pal = np.array([[0.29, 0.56, 0.85], [0.95, 0.77, 0.27], [0.91, 0.40, 0.42]])
col = pal[band.astype(int)] * (0.85 + nr[..., None] * 0.2)
save('rug_playroom.jpg', col)

# 8. Asphalt-like road paper (dark, fine grain) for generic roads
a = 0.20 + (tile_noise(N, 160, 3, 71) - 0.5) * 0.10 + (tile_noise(N, 8, 4, 72) - 0.5) * 0.04
speck = np.random.default_rng(73).random((N, N)) > 0.995
col = np.stack([a, a, a * 1.04], -1); col[speck] = 0.55
save('road_asphalt.jpg', col)

# 9. Chessboard (walnut + maple squares, lacquer)
cb = ((np.floor(x * 8) + np.floor(y * 8)) % 2).astype(bool)
dark = np.asarray(Image.open(os.path.join(OUT, 'wood_walnut.jpg')), float) / 255
light = np.asarray(Image.open(os.path.join(OUT, 'wood_oak_light.jpg')), float) / 255
col = np.where(cb[..., None], dark, light * 1.05)
save('chessboard.jpg', col)

# 10. Paint-splatter art paper
r2 = np.random.default_rng(81); img = Image.new('RGB', (N, N), (238, 232, 220)); dr = ImageDraw.Draw(img)
for _ in range(140):
    cx, cy, rad = r2.integers(0, N), r2.integers(0, N), r2.integers(6, 60)
    c = tuple(int(v) for v in r2.choice([[231, 76, 60], [52, 152, 219], [241, 196, 15], [46, 204, 113], [155, 89, 182], [230, 126, 34]]))
    for k in range(6):
        ox, oy = r2.normal(0, rad * 0.6, 2); rr = rad * r2.uniform(0.2, 0.7)
        for dx in (-N, 0, N):
            for dy in (-N, 0, N):
                dr.ellipse([cx + ox - rr + dx, cy + oy - rr + dy, cx + ox + rr + dx, cy + oy + rr + dy], fill=c)
img = img.filter(ImageFilter.GaussianBlur(0.8)); img.save(os.path.join(OUT, 'paper_paint.jpg'), quality=88)

# 11. Ruler strip (yellow plastic with cm/mm ticks) 1024x128
R = Image.new('RGB', (1024, 128), (250, 204, 21)); d = ImageDraw.Draw(R)
for i in range(0, 1024, 8):
    hh = 50 if i % 80 == 0 else (32 if i % 40 == 0 else 18)
    d.line([(i, 0), (i, hh)], fill=(30, 30, 30), width=2)
    if i % 80 == 0:
        d.text((i + 4, 56), str(i // 80), fill=(30, 30, 30))
R.save(os.path.join(OUT, 'ruler_strip.png'))

# 12. Kerb strip red/white 256x32
K = Image.new('RGB', (256, 32), (255, 255, 255)); d = ImageDraw.Draw(K)
for i in range(0, 256, 64): d.rectangle([i, 0, i + 31, 31], fill=(220, 38, 38))
K.save(os.path.join(OUT, 'kerb_red_white.png'))

# 13. Start/finish checker 256x64
C = Image.new('RGB', (256, 64), (255, 255, 255)); d = ImageDraw.Draw(C)
for i in range(0, 256, 16):
    for j in range(0, 64, 16):
        if (i // 16 + j // 16) % 2: d.rectangle([i, j, i + 15, j + 15], fill=(17, 17, 17))
C.save(os.path.join(OUT, 'checker_finish.png'))
print(sorted(os.listdir(OUT)))
