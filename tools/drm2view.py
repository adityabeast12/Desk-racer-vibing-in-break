import sys, struct, os, numpy as np, matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
from PIL import Image

def load(p):
    b = open(p, 'rb').read(); assert b[:4] == b'DRM2'; o = 4
    (n,) = struct.unpack_from('<i', b, o); o += 4
    bb = struct.unpack_from('<6f', b, o); o += 24
    parts = []
    for _ in range(n):
        (l,) = struct.unpack_from('<i', b, o); o += 4; name = b[o:o + l].decode(); o += l
        (l,) = struct.unpack_from('<i', b, o); o += 4; tex = b[o:o + l].decode(); o += l
        base = struct.unpack_from('<4f', b, o); o += 16
        mr = struct.unpack_from('<2f', b, o); o += 8
        flags = struct.unpack_from('<4B', b, o); o += 4
        piv = struct.unpack_from('<3f', b, o); o += 12
        (vc,) = struct.unpack_from('<i', b, o); o += 4
        d = np.frombuffer(b, '<f4', vc * 12, o).reshape(vc, 12); o += vc * 48
        (ic,) = struct.unpack_from('<i', b, o); o += 4
        idx = np.frombuffer(b, '<u2', ic, o).reshape(-1, 3); o += ic * 2
        o += (-o) % 4
        parts.append(dict(name=name, tex=tex, base=base, d=d, idx=idx, piv=piv))
    return parts, bb

def render(ax, path, root, title):
    parts, bb = load(path); polys = []; cols = []
    light = np.array([0.4, 0.8, 0.45]); light /= np.linalg.norm(light)
    for p in parts:
        d = p['d']; pos = d[:, :3]; nrm = d[:, 3:6]; uv = d[:, 6:8]
        img = None
        if p['tex']:
            img = np.asarray(Image.open(os.path.join(root, p['tex'])).convert('RGB')).astype(float) / 255
        for t in p['idx']:
            if img is not None:
                u, v = uv[t].mean(0); h, w, _ = img.shape
                c = img[int(np.clip((1 - (v % 1)) * h, 0, h - 1)), int(np.clip((u % 1) * w, 0, w - 1))]
            else:
                c = np.ones(3)
            c = c * np.array(p['base'][:3])
            shade = 0.45 + 0.55 * max(0, float(np.dot(nrm[t].mean(0), light)))
            polys.append(pos[t][:, [0, 2, 1]]); cols.append(np.clip(c * shade, 0, 1))
    ax.add_collection3d(Poly3DCollection(polys, facecolors=cols, edgecolor='none'))
    a = np.concatenate(polys); r = (a.max(0) - a.min(0)).max() / 2; c = (a.max(0) + a.min(0)) / 2
    ax.set_xlim(c[0] - r, c[0] + r); ax.set_ylim(c[1] - r, c[1] + r); ax.set_zlim(c[2] - r, c[2] + r)
    ax.view_init(20, -60); ax.set_axis_off(); ax.set_title(title, fontsize=9)

if __name__ == '__main__':
    root = sys.argv[1]; out = sys.argv[2]; files = sys.argv[3:]
    cols = 5; rows = (len(files) + cols - 1) // cols
    fig = plt.figure(figsize=(cols * 3.2, rows * 3.2))
    for i, f in enumerate(files):
        ax = fig.add_subplot(rows, cols, i + 1, projection='3d')
        render(ax, os.path.join(root, f), root, os.path.basename(f))
    plt.tight_layout(); plt.savefig(out, dpi=70)
