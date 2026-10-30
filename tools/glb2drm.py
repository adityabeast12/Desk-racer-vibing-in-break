"""Convert Kenney GLB models into DRM1 binary meshes for Desk Racer.
DRM1 layout (little-endian):
  4 bytes  magic 'DRM1'
  int32    partCount
  per part:
    int32 nameLen, bytes name (UTF-8)
    float32[3] pivot (part origin in model space, metres)
    int32 vertexCount
    float32[vertexCount*10]  px,py,pz, nx,ny,nz, r,g,b, tint   (tint=1 -> paintable body colour)
    int32 indexCount
    uint16[indexCount] triangle indices (CCW)
    (pad to 4 bytes)
"""
import sys, struct, numpy as np, trimesh
from PIL import Image
from collections import Counter

def sample_colors(geom):
    vis = geom.visual
    img = vis.material.baseColorTexture if hasattr(vis.material, 'baseColorTexture') else vis.material.image
    if img is None:
        return np.ones((len(geom.vertices), 3)) * 0.8
    img = img.convert('RGB'); w, h = img.size; px = np.asarray(img).astype(np.float32) / 255.0
    uv = np.asarray(vis.uv)
    u = np.clip((uv[:, 0] % 1.0) * w, 0, w - 1).astype(int)
    v = np.clip((1.0 - (uv[:, 1] % 1.0)) * h, 0, h - 1).astype(int)
    return px[v, u]

def convert(path, out, paint=True):
    s = trimesh.load(path, force='scene', process=False)
    parts = []
    for node in s.graph.nodes_geometry:
        T, g = s.graph[node]
        geom = s.geometry[g]
        pivot = T[:3, 3].copy()
        R = T.copy(); R[:3, 3] = 0
        verts = trimesh.transform_points(geom.vertices, R)
        normals = geom.vertex_normals @ R[:3, :3].T
        normals /= np.linalg.norm(normals, axis=1, keepdims=True) + 1e-9
        cols = sample_colors(geom)
        parts.append([node, pivot, verts, normals, cols, np.asarray(geom.faces)])
    # paint mask: most common saturated colour on the body part
    tint_col = None
    if paint:
        body = next((p for p in parts if p[0] == 'body'), None)
        if body is not None:
            q = [tuple((c * 20).round().astype(int)) for c in body[4] if (c.max() - c.min()) > 0.25]
            if q: tint_col = np.array(Counter(q).most_common(1)[0][0]) / 20.0
    buf = bytearray(b'DRM1') + struct.pack('<i', len(parts))
    total_v = 0
    for name, pivot, v, n, c, f in parts:
        tint = np.zeros(len(v))
        if tint_col is not None and name == 'body':
            tint = (np.abs(c - tint_col).max(axis=1) < 0.08).astype(np.float32)
        nb = name.encode()
        buf += struct.pack('<i', len(nb)) + nb + struct.pack('<3f', *pivot)
        data = np.hstack([v, n, c, tint[:, None]]).astype('<f4')
        assert len(v) < 65536
        buf += struct.pack('<i', len(v)) + data.tobytes()
        idx = f.astype('<u2').ravel()
        buf += struct.pack('<i', len(idx)) + idx.tobytes()
        if len(buf) % 4: buf += b'\0' * (4 - len(buf) % 4)
        total_v += len(v)
    open(out, 'wb').write(buf)
    b = s.bounds
    print(f"{out}: parts={[p[0] for p in parts]} verts={total_v} bounds={b.round(2).tolist()} paint={None if tint_col is None else tint_col.round(2).tolist()} bytes={len(buf)}")

if __name__ == '__main__':
    convert(sys.argv[1], sys.argv[2], paint=(len(sys.argv) < 4 or sys.argv[3] != 'nopaint'))
