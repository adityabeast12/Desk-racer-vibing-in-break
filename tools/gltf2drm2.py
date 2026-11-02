"""glTF/GLB -> DRM2 textured mesh for Desk Racer (mobile-optimised).

DRM2 layout (little-endian):
  4 bytes  'DRM2'
  int32    partCount
  float32[6] model AABB (minX,minY,minZ,maxX,maxY,maxZ) in metres
  per part:
    int32 nameLen, bytes name
    int32 texLen, bytes texture path relative to assets/ ('' = untextured)
    float32[4] baseColorFactor RGBA (multiply with texture / vertex colour)
    float32 metallic, float32 roughness
    uint8 alphaMode (0 opaque, 1 mask, 2 blend), uint8 doubleSided, uint8[2] pad
    float32[3] pivot (metres)
    int32 vertexCount
    float32[vertexCount*12]  px,py,pz, nx,ny,nz, u,v, r,g,b, tint   (positions relative to pivot)
    int32 indexCount
    uint16[indexCount]
    pad to 4 bytes
Coordinates: metres, +Y up, model's own forward kept as authored (manifest notes facing).
"""
import sys, os, io, json, struct, math
import numpy as np, trimesh, fast_simplification
from PIL import Image

def mat_info(geom):
    vis = geom.visual
    m = getattr(vis, 'material', None)
    tex = None; base = [1, 1, 1, 1]; metal = 0.0; rough = 0.8; alpha = 0; double = 0
    if m is not None:
        if hasattr(m, 'baseColorTexture') and m.baseColorTexture is not None:
            tex = m.baseColorTexture
        elif getattr(m, 'image', None) is not None:
            tex = m.image
        bcf = getattr(m, 'baseColorFactor', None)
        if bcf is not None:
            base = (np.array(bcf, float) / (255.0 if np.max(bcf) > 1.0 else 1.0)).tolist()
        mf = getattr(m, 'metallicFactor', None); rf = getattr(m, 'roughnessFactor', None)
        metal = float(mf) if mf is not None else (0.0 if tex is not None else 0.0)
        rough = float(rf) if rf is not None else 0.8
        am = str(getattr(m, 'alphaMode', 'OPAQUE') or 'OPAQUE').upper()
        alpha = {'OPAQUE': 0, 'MASK': 1, 'BLEND': 2}.get(am, 0)
        double = 1 if getattr(m, 'doubleSided', False) else 0
    uv = getattr(vis, 'uv', None)
    vc = None
    if uv is None and hasattr(vis, 'vertex_colors') and vis.kind == 'vertex':
        vc = np.asarray(vis.vertex_colors)[:, :3] / 255.0
    return tex, base, metal, rough, alpha, double, uv, vc

def simplify_transfer(v, f, uv, vc, target_tris):
    """Returns (verts, faces, uv, colors) with UVs re-projected from the original surface per corner."""
    if len(f) <= target_tris:
        return v, f, (uv if uv is not None else np.zeros((len(v), 2))), (vc if vc is not None else np.ones((len(v), 3)))
    orig = trimesh.Trimesh(v, f, process=False)
    w = trimesh.Trimesh(v.copy(), f.copy(), process=False)
    w.merge_vertices(merge_tex=True, merge_norm=True)
    red = 1.0 - target_tris / max(len(w.faces), 1)
    if red > 0:
        nv, nf = fast_simplification.simplify(w.vertices.astype(np.float32), w.faces.astype(np.int32), target_reduction=float(red), agg=3)
    else:
        nv, nf = w.vertices, w.faces
    nv = np.asarray(nv, np.float64); nf = np.asarray(nf)
    simp = trimesh.Trimesh(nv, nf, process=False)
    vn = simp.vertex_normals
    cent = nv[nf].mean(1)
    _, _, tri_id = trimesh.proximity.closest_point(orig, cent)
    corners = nv[nf].reshape(-1, 3)
    tri_rep = np.repeat(tri_id, 3)
    bary = trimesh.triangles.points_to_barycentric(orig.triangles[tri_rep], corners)
    bary = np.nan_to_num(np.clip(bary, -0.25, 1.25), nan=1/3); bary /= bary.sum(1, keepdims=True)
    fv = f[tri_rep]
    cuv = (uv[fv] * bary[:, :, None]).sum(1) if uv is not None else np.zeros((len(corners), 2))
    ccol = (vc[fv] * bary[:, :, None]).sum(1) if vc is not None else np.ones((len(corners), 3))
    vid = nf.reshape(-1)
    # per-vertex UV from the nearest original surface point; reuse it unless this face sits in another chart
    _, _, vtri = trimesh.proximity.closest_point(orig, nv)
    vb = trimesh.triangles.points_to_barycentric(orig.triangles[vtri], nv)
    vb = np.nan_to_num(np.clip(vb, -0.25, 1.25), nan=1/3); vb /= vb.sum(1, keepdims=True)
    vuv = (uv[f[vtri]] * vb[:, :, None]).sum(1) if uv is not None else np.zeros((len(nv), 2))
    cuv = np.nan_to_num(cuv)
    same = np.abs(cuv - vuv[vid]).max(1) < 0.02
    cuv[same] = vuv[vid[same]]
    key = np.round(np.hstack([vid[:, None].astype(float), cuv * 1024.0]))
    _, uniq, inv = np.unique(key, axis=0, return_index=True, return_inverse=True)
    outv = nv[vid[uniq]]; outn = vn[vid[uniq]]
    return outv, inv.reshape(-1, 3), cuv[uniq], ccol[uniq], outn

def save_tex(img, out_assets, rel, max_size, has_alpha):
    if img.mode not in ('RGB', 'RGBA'):
        img = img.convert('RGBA' if has_alpha else 'RGB')
    if not has_alpha:
        img = img.convert('RGB')
    w, h = img.size; s = min(1.0, max_size / max(w, h))
    if s < 1.0:
        img = img.resize((max(4, int(w * s)), max(4, int(h * s))), Image.LANCZOS)
    p = os.path.join(out_assets, rel); os.makedirs(os.path.dirname(p), exist_ok=True)
    if has_alpha:
        rel = rel.rsplit('.', 1)[0] + '.png'; p = os.path.join(out_assets, rel); img.save(p, optimize=True)
    else:
        rel = rel.rsplit('.', 1)[0] + '.jpg'; p = os.path.join(out_assets, rel); img.save(p, quality=84, optimize=True, progressive=False)
    return rel

def convert(src, out_assets, name, tri_budget, tex_size=512, keep_parts=None, drop_parts=(), height=None, rot=(0, 0, 0), only_parts=None):
    scene = trimesh.load(src, force='scene', process=False)
    nodes = list(scene.graph.nodes_geometry)
    items = []
    total = 0
    for node in nodes:
        T, g = scene.graph[node]
        if any(d.lower() in node.lower() for d in drop_parts):
            continue
        if only_parts is not None and not any(k.lower() in node.lower() for k in only_parts):
            continue
        geom = scene.geometry[g]
        if not isinstance(geom, trimesh.Trimesh) or len(geom.faces) == 0:
            continue
        items.append((node, T, geom)); total += len(geom.faces)
    parts = []; tex_cache = {}
    bbmin = np.full(3, 1e9); bbmax = np.full(3, -1e9)
    for node, T, geom in items:
        share = max(64, int(tri_budget * len(geom.faces) / max(total, 1)))
        tex, base, metal, rough, alpha, double, uv, vc = mat_info(geom)
        v = trimesh.transform_points(geom.vertices, T)
        f = np.asarray(geom.faces)
        res = simplify_transfer(v, f, None if uv is None else np.asarray(uv, float), None if vc is None else np.asarray(vc, float), share)
        if len(res) == 4:
            nv, nf, nuv, ncol = res; nn = trimesh.Trimesh(nv, nf, process=False).vertex_normals
        else:
            nv, nf, nuv, ncol, nn = res
        if len(nf) == 0:
            continue
        # split into <=65535-vertex chunks (rare after simplification)
        assert len(nv) < 65535, (name, node, len(nv))
        texrel = ''
        if tex is not None:
            key = id(tex)
            if key not in tex_cache:
                has_alpha = alpha > 0 and tex.mode in ('RGBA', 'LA', 'P')
                tex_cache[key] = save_tex(tex, out_assets, f"textures/props/{name}_{len(tex_cache)}.jpg", tex_size, has_alpha)
            texrel = tex_cache[key]
        bbmin = np.minimum(bbmin, nv.min(0)); bbmax = np.maximum(bbmax, nv.max(0))
        parts.append(dict(name=node, tex=texrel, base=base, metal=metal, rough=rough, alpha=alpha, double=double,
                          v=nv, n=nn, uv=nuv, c=ncol, f=nf))
    # orient, scale to real-world height, centre on XZ, rest on y=0
    R = trimesh.transformations.euler_matrix(*[math.radians(a) for a in rot])[:3, :3]
    for p in parts:
        p['v'] = p['v'] @ R.T; p['n'] = p['n'] @ R.T
    allv = np.vstack([p['v'] for p in parts])
    lo, hi = allv.min(0), allv.max(0)
    sc = (height / (hi[1] - lo[1])) if height else 1.0
    off = np.array([(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2])
    for p in parts:
        p['v'] = (p['v'] - off) * sc
    allv = np.vstack([p['v'] for p in parts]); bbmin, bbmax = allv.min(0), allv.max(0)
    # write
    buf = bytearray(b'DRM2') + struct.pack('<i', len(parts)) + struct.pack('<6f', *bbmin, *bbmax)
    tris = 0
    for p in parts:
        nb = p['name'].encode()[:120]; tb = p['tex'].encode()
        buf += struct.pack('<i', len(nb)) + nb + struct.pack('<i', len(tb)) + tb
        b = (list(p['base']) + [1, 1, 1, 1])[:4]
        buf += struct.pack('<4f', *b) + struct.pack('<2f', p['metal'], p['rough']) + struct.pack('<4B', p['alpha'], p['double'], 0, 0)
        buf += struct.pack('<3f', 0, 0, 0)
        uvtl = np.column_stack([p['uv'][:, 0], 1.0 - p['uv'][:, 1]])  # top-left origin: v=0 is the first image row
        data = np.hstack([p['v'], p['n'], uvtl, p['c'], np.zeros((len(p['v']), 1))]).astype('<f4')
        buf += struct.pack('<i', len(p['v'])) + data.tobytes()
        idx = p['f'].astype('<u2').ravel(); tris += len(p['f'])
        buf += struct.pack('<i', len(idx)) + idx.tobytes()
        if len(buf) % 4: buf += b'\0' * (4 - len(buf) % 4)
    out = os.path.join(out_assets, 'models', 'props', name + '.drm')
    os.makedirs(os.path.dirname(out), exist_ok=True); open(out, 'wb').write(buf)
    size = (bbmax - bbmin)
    print(f"{name:22s} parts={len(parts):2d} tris={tris:6d} (src {total}) size_m={size.round(3).tolist()} textures={len(tex_cache)} bytes={len(buf)}")
    return dict(file=f"models/props/{name}.drm", parts=len(parts), triangles=tris, sizeMeters=[round(x, 4) for x in size.tolist()],
                aabbMin=[round(x, 4) for x in bbmin.tolist()], aabbMax=[round(x, 4) for x in bbmax.tolist()],
                textures=sorted(set(tex_cache.values())))

if __name__ == '__main__':
    print(json.dumps(convert(sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5]) if len(sys.argv) > 5 else 512)))
