"""Track layouts for the Desk Racer mega prompt + geometric validation.
Control points are (x, y, z) for a CLOSED Catmull-Rom spline, 18 subdivisions/segment,
first point = start/finish line, travel direction = list order.
"""
import math, json, sys
import numpy as np

TRACKS = {
 # ---------------- DESK CUP ----------------
 "STUDY DESK": dict(width=12, cps=[
    (0,0,90),(60,0,92),(110,0,80),(135,0,45),(128,0,5),(100,0,-15),(95,0,-50),(118,0,-85),(95,0,-118),
    (45,0,-120),(15,0,-95),(-20,3.5,-95),(-55,3.5,-100),(-95,0,-110),(-125,0,-80),(-120,0,-40),
    (-109,0,-20),(-118,0,15),(-120,0,55),(-90,0,85),(-45,0,92)]),
 "GAMER DESK": dict(width=11.5, cps=[
    (0,0,100),(55,0,100),(105,0,92),(130,0,60),(115,0,25),(80,0,20),(55,0,0),(70,0,-30),(110,0,-40),
    (130,0,-75),(105,0,-110),(60,0,-115),(25,0,-90),(0,4,-60),(-25,6,-30),(-50,6,-5),(-90,6,-10),
    (-125,4,-40),(-130,0,-85),(-100,0,-115),(-60,0,-110),(-40,0,-80),(-70,0,-50),(-110,0,-20),(-130,0,25),(-115,0,70),(-70,0,95)]),
 "KITCHEN COUNTER": dict(width=10.5, cps=[
    (0,0,100),(60,0,100),(115,0,90),(135,0,55),(110,0,30),(75,0,40),(50,0,15),(60,0,-20),(100,0,-30),
    (130,0,-60),(120,0,-100),(80,0,-120),(35,0,-110),(10,0,-80),(-20,0,-90),(-45,0,-120),(-90,0,-115),
    (-120,0,-80),(-100,0,-45),(-60,0,-40),(-45,0,-10),(-75,0,15),(-120,0,25),(-130,0,65),(-95,0,95),(-45,0,100)]),
 "ART DESK": dict(width=12, cps=[
    (0,0,95),(55,0,95),(100,0,75),(110,0,35),(85,0,5),(95,0,-30),(125,0,-55),(110,0,-95),(70,0,-110),
    (40,0,-85),(45,0,-50),(15,0,-30),(-20,0,-50),(-25,0,-90),(-60,0,-115),(-105,0,-100),(-120,0,-60),
    (-95,0,-25),(-110,0,15),(-105,0,60),(-60,0,90)]),
 # ---------------- ROOM CUP ----------------
 "BOOKSHELF SPIRAL": dict(width=11, cps=[
    (0,0,90),(55,0,90),(95,0,72),(108,0,30),(80,4,-20),(40,8,-35),(0,12,-25),(-25,16,5),(-15,20,40),
    (20,22,50),(50,22,30),(55,18,-10),(35,14,-55),(0,10,-80),(-45,6,-85),(-85,3,-65),(-105,0,-25),
    (-110,0,25),(-90,0,65),(-50,0,88)]),
 "BATHTUB RIM": dict(width=10, cps=[
    (0,0,80),(50,0,82),(95,0,75),(125,0,50),(132,0,10),(125,0,-30),(100,0,-60),(60,0,-72),
    (30,0,-55),(0,0,-70),(-30,0,-55),(-60,0,-72),(-100,0,-60),(-125,0,-30),(-132,0,10),(-125,0,50),(-95,0,75),(-50,0,82)]),
 "TOY BOX": dict(width=12, cps=[
    (0,0,100),(50,0,100),(95,0,85),(112,0,50),(101,0,25),(112,0,0),(125,0,-35),(100,0,-70),(60,5,-75),
    (25,5,-60),(10,0,-25),(-20,0,-5),(-50,0,-25),(-55,0,-65),(-38,0,-95),(-60,0,-125),(-105,0,-115),
    (-125,0,-75),(-115,0,-30),(-125,0,15),(-115,0,60),(-75,0,92),(-40,0,100)]),
 "MIDNIGHT OFFICE": dict(width=11, cps=[
    (0,0,100),(60,0,100),(115,0,95),(135,0,60),(120,0,20),(88,0,10),(68,0,-15),(82,0,-45),(120,0,-50),
    (135,0,-85),(110,0,-120),(55,0,-120),(20,0,-100),(-5,0,-65),(-35,0,-45),(-70,0,-60),(-88,0,-98),
    (-120,0,-116),(-140,0,-78),(-125,0,-35),(-109,0,-15),(-118,0,20),(-130,0,60),(-105,0,92),(-55,0,100)]),
}

def build(cps, sub=18):
    n = len(cps); P = np.array(cps, float); out = []
    for i in range(n):
        p0, p1, p2, p3 = P[(i-1) % n], P[i], P[(i+1) % n], P[(i+2) % n]
        for s in range(sub):
            t = s / sub; t2 = t*t; t3 = t2*t
            out.append(0.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t2+(-p0+3*p1-3*p2+p3)*t3))
    return np.array(out)

def analyse(name, d):
    pts = build(d['cps']); w = d['width']; n = len(pts)
    seg = np.linalg.norm(np.diff(np.vstack([pts, pts[:1]])[:, [0, 2]], axis=0), axis=1)
    L = seg.sum()
    # curvature radius using 3-point circumradius over ~6 u spacing
    radii = []
    step = 3
    for i in range(n):
        a, b, c = pts[i-step][[0, 2]], pts[i][[0, 2]], pts[(i+step) % n][[0, 2]]
        ab, bc, ca = np.linalg.norm(a-b), np.linalg.norm(b-c), np.linalg.norm(c-a)
        area = abs((b[0]-a[0])*(c[1]-a[1]) - (b[1]-a[1])*(c[0]-a[0])) / 2
        radii.append(1e9 if area < 1e-6 else ab*bc*ca/(4*area))
    rmin = min(radii)
    # overlap: points far along the spline but close in XZ with < 4 u height separation
    cum = np.concatenate([[0], np.cumsum(seg)[:-1]])
    bad = []
    for i in range(0, n, 2):
        dxz = np.linalg.norm(pts[:, [0, 2]] - pts[i, [0, 2]], axis=1)
        along = np.abs(cum - cum[i]); along = np.minimum(along, L - along)
        mask = (along > 3*w) & (dxz < w + 2.0) & (np.abs(pts[:, 1] - pts[i, 1]) < 4.5)
        if mask.any(): bad.append((round(cum[i]), round(float(dxz[mask].min()), 1)))
    # first straight (grid) – heading change over first 60 u
    k = np.searchsorted(cum, 60)
    h0 = math.atan2(pts[1][0]-pts[0][0], pts[1][2]-pts[0][2]); h1 = math.atan2(pts[k+1][0]-pts[k][0], pts[k+1][2]-pts[k][2])
    grid_turn = abs((h1-h0+math.pi) % (2*math.pi) - math.pi)
    # behind the line too (grid sits behind the start)
    kb = np.searchsorted(cum, L-30)
    hb = math.atan2(pts[(kb+1)%n][0]-pts[kb][0], pts[(kb+1)%n][2]-pts[kb][2])
    behind_turn = abs((h0-hb+math.pi) % (2*math.pi) - math.pi)
    ys = pts[:, 1]
    slope = np.max(np.abs(np.diff(np.append(ys, ys[0]))) / np.maximum(seg, 1e-6))
    ext = (pts[:, 0].min(), pts[:, 0].max(), pts[:, 2].min(), pts[:, 2].max())
    ok = rmin >= 0.9*w and not bad and grid_turn < 0.35 and behind_turn < 0.35 and slope < 0.3
    return dict(name=name, length=round(L), lap_s_at_24=round(L/24, 1), width=w, min_radius=round(rmin, 1),
                overlaps=bad[:4], grid_turn_deg=round(math.degrees(grid_turn)), behind_turn_deg=round(math.degrees(behind_turn)),
                max_slope=round(float(slope), 2), extent=[round(e) for e in ext], OK=ok), pts

if __name__ == '__main__':
    import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
    fig, axs = plt.subplots(2, 4, figsize=(20, 10))
    allok = True
    for ax, (name, d) in zip(axs.ravel(), TRACKS.items()):
        info, pts = analyse(name, d); allok &= info['OK']
        print(json.dumps(info, default=lambda o: o.item() if hasattr(o, "item") else str(o)))
        sc = ax.scatter(pts[:, 0], -pts[:, 2], c=pts[:, 1], s=d['width']*1.4, cmap='viridis')
        ax.plot(pts[:, 0], -pts[:, 2], 'k-', lw=0.5)
        ax.annotate('START', (pts[0][0], -pts[0][2]), color='red'); ax.arrow(pts[0][0], -pts[0][2], (pts[4][0]-pts[0][0]), -(pts[4][2]-pts[0][2]), color='red', width=1.5)
        ax.set_title(f"{name} L={info['length']} rmin={info['min_radius']} {'OK' if info['OK'] else 'FAIL'}"); ax.set_aspect('equal')
    plt.tight_layout(); plt.savefig(sys.argv[1] if len(sys.argv) > 1 else 'tracks.png', dpi=60)
    print('ALL OK' if allok else 'SOME FAIL')
