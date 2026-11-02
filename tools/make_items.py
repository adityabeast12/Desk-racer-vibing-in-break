"""Procedural, detailed item / pickup models for Desk Racer -> DRM2 (original, CC0 by the project).
Sizes are in metres at game scale (1 game unit = 1 cm => 0.01 m)."""
import numpy as np, struct, os, sys, math
from PIL import Image, ImageDraw
OUT = sys.argv[1]
os.makedirs(f'{OUT}/models/items', exist_ok=True); os.makedirs(f'{OUT}/textures/items', exist_ok=True)

class Part:
    def __init__(s, name, tex='', base=(1, 1, 1, 1), metal=0.0, rough=0.6, alpha=0, double=0):
        s.name, s.tex, s.base, s.metal, s.rough, s.alpha, s.double = name, tex, base, metal, rough, alpha, double
        s.v, s.n, s.uv, s.c, s.f = [], [], [], [], []
    def add(s, v, n, uv, c, f):
        o = sum(len(x) for x in s.v); s.v.append(v); s.n.append(n); s.uv.append(uv); s.c.append(c); s.f.append(np.asarray(f) + o)

def lathe(profile, seg=48, color=(1, 1, 1), u0=0.0, u1=1.0):
    """profile: list of (r, y). returns v, n, uv, c, f"""
    P = np.array(profile, float); m = len(P)
    ang = np.linspace(0, 2 * np.pi, seg + 1)
    V, N, UV = [], [], []
    # profile normals in (r,y)
    d = np.gradient(P, axis=0); pn = np.stack([d[:, 1], -d[:, 0]], 1); pn /= np.linalg.norm(pn, axis=1, keepdims=True) + 1e-9
    L = np.concatenate([[0], np.cumsum(np.linalg.norm(np.diff(P, axis=0), axis=1))]); L /= max(L[-1], 1e-9)
    for i, a in enumerate(ang):
        ca, sa = math.cos(a), math.sin(a)
        for j in range(m):
            r, y = P[j]; V.append([r * ca, y, r * sa]); N.append([pn[j, 0] * ca, pn[j, 1], pn[j, 0] * sa]); UV.append([u0 + (u1 - u0) * i / seg, L[j]])
    F = []
    for i in range(seg):
        for j in range(m - 1):
            a = i * m + j; b = (i + 1) * m + j
            F += [[a, a + 1, b], [b, a + 1, b + 1]]
    V = np.array(V); C = np.tile(color, (len(V), 1))
    return V, np.array(N), np.array(UV), C, np.array(F)

def box(sx, sy, sz, color, center=(0, 0, 0), uvscale=1.0):
    V, N, UV, F = [], [], [], []
    hx, hy, hz = sx / 2, sy / 2, sz / 2
    faces = [((1, 0, 0), [(hx, -hy, -hz), (hx, hy, -hz), (hx, hy, hz), (hx, -hy, hz)]),
             ((-1, 0, 0), [(-hx, -hy, hz), (-hx, hy, hz), (-hx, hy, -hz), (-hx, -hy, -hz)]),
             ((0, 1, 0), [(-hx, hy, -hz), (-hx, hy, hz), (hx, hy, hz), (hx, hy, -hz)]),
             ((0, -1, 0), [(-hx, -hy, hz), (-hx, -hy, -hz), (hx, -hy, -hz), (hx, -hy, hz)]),
             ((0, 0, 1), [(hx, -hy, hz), (hx, hy, hz), (-hx, hy, hz), (-hx, -hy, hz)]),
             ((0, 0, -1), [(-hx, -hy, -hz), (-hx, hy, -hz), (hx, hy, -hz), (hx, -hy, -hz)])]
    for nrm, q in faces:
        o = len(V)
        for k, p in enumerate(q):
            V.append(np.array(p) + center); N.append(nrm); UV.append([(k in (2, 3)) * uvscale, (k in (1, 2)) * uvscale])
        F += [[o, o + 1, o + 2], [o, o + 2, o + 3]]
    V = np.array(V); return V, np.array(N, float), np.array(UV, float), np.tile(color, (len(V), 1)), np.array(F)

def rounded_box(sx, sy, sz, r, color, seg=4):
    """box with rounded edges via sphere-swept superellipsoid sampling"""
    us = np.linspace(-np.pi / 2, np.pi / 2, 4 * seg + 1); vs = np.linspace(-np.pi, np.pi, 8 * seg + 1)
    e = 0.18
    def sgnpow(t, p): return np.sign(t) * np.abs(t) ** p
    V, N, UV = [], [], []
    for i, u in enumerate(us):
        for j, v in enumerate(vs):
            x = sgnpow(np.cos(u), e) * sgnpow(np.cos(v), e); y = sgnpow(np.sin(u), e); z = sgnpow(np.cos(u), e) * sgnpow(np.sin(v), e)
            V.append([x * sx / 2, y * sy / 2, z * sz / 2]); UV.append([j / (len(vs) - 1), i / (len(us) - 1)])
    V = np.array(V); m = len(vs); F = []
    for i in range(len(us) - 1):
        for j in range(m - 1):
            a = i * m + j; b = a + m
            F += [[a, b, a + 1], [a + 1, b, b + 1]]
    F = np.array(F)
    N = np.zeros_like(V)
    tri = V[F]; fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    for k in range(3): np.add.at(N, F[:, k], fn)
    N /= np.linalg.norm(N, axis=1, keepdims=True) + 1e-12
    return V, N, np.array(UV), np.tile(color, (len(V), 1)), F

def write(name, parts):
    allv = np.vstack([np.vstack(p.v) for p in parts]); lo, hi = allv.min(0), allv.max(0)
    buf = bytearray(b'DRM2') + struct.pack('<i', len(parts)) + struct.pack('<6f', *lo, *hi)
    tris = 0
    for p in parts:
        v = np.vstack(p.v); n = np.vstack(p.n); uv = np.vstack(p.uv); c = np.vstack(p.c); f = np.vstack(p.f)
        nb = p.name.encode(); tb = p.tex.encode()
        buf += struct.pack('<i', len(nb)) + nb + struct.pack('<i', len(tb)) + tb
        buf += struct.pack('<4f', *p.base) + struct.pack('<2f', p.metal, p.rough) + struct.pack('<4B', p.alpha, p.double, 0, 0) + struct.pack('<3f', 0, 0, 0)
        uvtl = np.column_stack([uv[:, 0], 1.0 - uv[:, 1]])  # top-left origin
        data = np.hstack([v, n, uvtl, c, np.zeros((len(v), 1))]).astype('<f4')
        buf += struct.pack('<i', len(v)) + data.tobytes()
        idx = f.astype('<u2').ravel(); tris += len(f)
        buf += struct.pack('<i', len(idx)) + idx.tobytes()
        if len(buf) % 4: buf += b'\0' * (4 - len(buf) % 4)
    open(f'{OUT}/models/items/{name}.drm', 'wb').write(buf)
    print(f'{name:16s} parts={len(parts)} tris={tris} size_m={(hi - lo).round(4).tolist()}')

S = 0.01  # 1 game unit in metres

# ---------- textures
def battery_label():
    W, H = 512, 256; im = Image.new('RGB', (W, H), (20, 20, 22)); d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W, 70], fill=(205, 127, 50))           # copper band (top of cylinder)
    for i in range(0, W, 128):
        d.text((i + 20, 120), 'DESK  POWER  1.5V', fill=(240, 240, 240))
        d.text((i + 40, 160), '+  AA  LR6', fill=(205, 127, 50))
    d.line([(0, 70), (W, 70)], fill=(255, 220, 160), width=3)
    im.save(f'{OUT}/textures/items/battery_label.jpg', quality=90)
def marble_tex(base, swirl, name):
    W, H = 512, 256; yy, xx = np.mgrid[0:H, 0:W]
    t = np.sin(xx / W * 2 * np.pi * 3 + np.sin(yy / H * np.pi * 4) * 2.5) * 0.5 + 0.5
    col = np.array(base)[None, None] * (1 - t[..., None] ** 6) + np.array(swirl)[None, None] * (t[..., None] ** 6)
    Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8)).save(f'{OUT}/textures/items/{name}.jpg', quality=90)
def paper_tex():
    im = Image.new('RGB', (256, 256), (245, 244, 238)); d = ImageDraw.Draw(im)
    for y in range(0, 256, 16): d.line([(0, y), (256, y)], fill=(170, 195, 230), width=1)
    im.save(f'{OUT}/textures/items/paper_plane.jpg', quality=90)
battery_label(); marble_tex((0.55, 0.85, 0.95), (0.05, 0.45, 0.85), 'marble_blue'); marble_tex((0.95, 0.35, 0.35), (0.6, 0.02, 0.05), 'marble_red'); paper_tex()

# ---------- BATTERY (AA style, lying along Z when used as pickup; authored upright)
p = Part('body', 'textures/items/battery_label.jpg', rough=0.35, metal=0.2)
prof = [(0.0, 0), (0.48, 0), (0.5, 0.03), (0.5, 1.6), (0.48, 1.65), (0.0, 1.65)]
v, n, uv, c, f = lathe([(r * S * 0.9, y * S * 0.9) for r, y in prof], 40); uv[:, 0] = 1.0 - uv[:, 0]; p.add(v, n, uv, c, f)
t = Part('terminal', base=(0.85, 0.85, 0.88, 1), metal=1.0, rough=0.25)
v, n, uv, c, f = lathe([(0, 1.65 * S * 0.9), (0.18 * S, 1.65 * S * 0.9), (0.18 * S, 1.78 * S * 0.9), (0.0, 1.8 * S * 0.9)], 24); t.add(v, n, uv, c, f)
write('battery', [p, t])

# ---------- THUMBTACK
head = Part('head', base=(0.70, 0.03, 0.06, 1), rough=0.25)
v, n, uv, c, f = lathe([(0, 0.55 * S), (0.45 * S, 0.55 * S), (0.5 * S, 0.62 * S), (0.42 * S, 0.75 * S), (0.2 * S, 0.95 * S), (0.18 * S, 1.2 * S), (0.3 * S, 1.3 * S), (0.0, 1.38 * S)], 32); head.add(v, n, uv, c, f)
pin = Part('pin', base=(0.8, 0.8, 0.82, 1), metal=1.0, rough=0.2)
v, n, uv, c, f = lathe([(0.0, 0.0), (0.05 * S, 0.12 * S), (0.05 * S, 0.55 * S), (0.0, 0.56 * S)], 12); pin.add(v, n, uv, c, f)
write('thumbtack', [head, pin])

# ---------- ERASER (pink rubber + blue paper sleeve)
er = Part('rubber', base=(1, 1, 1, 1), rough=0.9)
v, n, uv, c, f = rounded_box(2.2 * S, 0.8 * S, 1.2 * S, 0.1, (0.93, 0.36, 0.50)); v[:, 1] += 0.4 * S; er.add(v, n, uv, c, f)
sl = Part('sleeve', base=(1, 1, 1, 1), rough=0.7)
v, n, uv, c, f = box(1.3 * S, 0.84 * S, 1.24 * S, (0.06, 0.20, 0.62)); v[:, 0] += 0.45 * S; v[:, 1] += 0.4 * S; sl.add(v, n, uv, c, f)
write('eraser', [er, sl])

# ---------- MARBLES (glass sphere with swirl)
def sphere(r, seg=32):
    prof = [(r * math.sin(a), r - r * math.cos(a)) for a in np.linspace(0, np.pi, seg // 2 + 1)]
    return lathe(prof, seg)
for nm, tex in (('marble', 'marble_blue'), ('marble_homing', 'marble_red')):
    m = Part('glass', f'textures/items/{tex}.jpg', rough=0.05, metal=0.0)
    v, n, uv, c, f = sphere(0.6 * S, 36); m.add(v, n, uv, c, f)
    write(nm, [m])

# ---------- PAPER PLANE (folded)
pp = Part('paper', 'textures/items/paper_plane.jpg', rough=0.9, double=1)
L, W, H = 2.6 * S, 1.1 * S, 0.35 * S
P = np.array([[0, H, L / 2], [0, 0.05 * S, -L / 2], [0, H, -L / 2], [-W, H * 1.35, -L / 2], [W, H * 1.35, -L / 2],
              [-W * 1.02, H * 1.35 + 0.35 * S, -L / 2], [W * 1.02, H * 1.35 + 0.35 * S, -L / 2], [-W * 0.55, H * 1.2, L * 0.05], [W * 0.55, H * 1.2, L * 0.05]])
F = [[0, 7, 3], [0, 3, 2], [0, 2, 4], [0, 4, 8], [0, 1, 2], [3, 7, 5], [4, 6, 8]]
for tri in F:
    a, b, c3 = P[tri]; nn = np.cross(b - a, c3 - a); nn /= np.linalg.norm(nn) + 1e-12
    uvs = np.array([[0.5, 1], [0, 0], [1, 0]])
    pp.add(np.array([a, b, c3]), np.tile(nn, (3, 1)), uvs, np.ones((3, 3)), [[0, 1, 2]])
write('paper_plane', [pp])

# ---------- MAGNET (horseshoe)
mag = Part('body', base=(0.80, 0.04, 0.06, 1), rough=0.3, metal=0.2)
tips = Part('tips', base=(0.82, 0.82, 0.85, 1), rough=0.2, metal=1.0)
R, r = 0.7 * S, 0.22 * S
def torus_arc(R, r, a0, a1, seg=32, tseg=16, color=(1, 1, 1)):
    V, N, UV, F = [], [], [], []
    for i, a in enumerate(np.linspace(a0, a1, seg + 1)):
        cx, cy = R * math.cos(a), R * math.sin(a)
        for j, b in enumerate(np.linspace(0, 2 * np.pi, tseg + 1)):
            nx, ny, nz = math.cos(b) * math.cos(a), math.cos(b) * math.sin(a), math.sin(b)
            V.append([cx + r * nx, cy + r * ny, r * nz]); N.append([nx, ny, nz]); UV.append([i / seg, j / tseg])
    m = tseg + 1
    for i in range(seg):
        for j in range(tseg):
            a = i * m + j; b = a + m; F += [[a, b, a + 1], [a + 1, b, b + 1]]
    V = np.array(V); return V, np.array(N), np.array(UV), np.tile(color, (len(V), 1)), np.array(F)
v, n, uv, c, f = torus_arc(R, r, 0, np.pi); v[:, 1] += 0.9 * S; mag.add(v, n, uv, c, f)
for sx in (-1, 1):
    v, n, uv, c, f = lathe([(0, 0), (r, 0), (r, 0.9 * S), (0, 0.9 * S)], 20)
    v[:, 0] += sx * R; v[:, 1] += 0.0; (mag if True else tips).add(v, n, uv, c, f)
    v, n, uv, c, f = lathe([(0, -0.3 * S), (r * 1.02, -0.3 * S), (r * 1.02, 0.0), (0, 0.0)], 20); v[:, 0] += sx * R; tips.add(v, n, uv, c, f)
for p_ in (mag, tips):
    for k in range(len(p_.v)): p_.v[k] = p_.v[k] + np.array([0, 0.3 * S, 0])
write('magnet', [mag, tips])

# ---------- STAR STICKER (extruded bevelled star)
st = Part('star', base=(1.0, 0.70, 0.05, 1), rough=0.2, metal=0.85)
pts = []
for k in range(10):
    a = math.pi / 2 + k * math.pi / 5; rr = (1.0 if k % 2 == 0 else 0.45) * S
    pts.append([rr * math.cos(a), rr * math.sin(a)])
pts = np.array(pts); d = 0.25 * S
V, N, UV, F = [], [], [], []
for zs, nz in ((d, 1), (-d, -1)):
    o = len(V); V.append([0, 0, zs * 1.6]); N.append([0, 0, nz]); UV.append([0.5, 0.5])
    for p2 in pts: V.append([p2[0], p2[1], zs]); N.append([0, 0, nz]); UV.append([0.5 + p2[0] / S / 2, 0.5 + p2[1] / S / 2])
    for k in range(10):
        a, b = o + 1 + k, o + 1 + (k + 1) % 10
        F.append([o, a, b] if nz > 0 else [o, b, a])
for k in range(10):
    a, b = pts[k], pts[(k + 1) % 10]; e = np.array([b[0] - a[0], b[1] - a[1], 0]); nn = np.cross(e, [0, 0, 1]); nn /= np.linalg.norm(nn)
    o = len(V)
    for p2, zs in ((a, d), (b, d), (b, -d), (a, -d)): V.append([p2[0], p2[1], zs]); N.append(nn); UV.append([0, 0])
    F += [[o, o + 2, o + 1], [o, o + 3, o + 2]]
V = np.array(V); V[:, 1] += 1.1 * S
st.add(V, np.array(N, float), np.array(UV, float), np.ones((len(V), 3)), np.array(F))
write('star_sticker', [st])
