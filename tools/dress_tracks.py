"""Generate assets/tracks/<slug>.json: validated layout + realistic prop placements that never touch the road."""
import json, math, os, sys, importlib.util
import numpy as np
spec = importlib.util.spec_from_file_location('t', '/home/claude/tools/tracks_design.py'); T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)
OUT = sys.argv[1]; os.makedirs(f'{OUT}/tracks', exist_ok=True)
props = json.load(open('/home/claude/tools/props_manifest_tmp.json'))
U = 100.0  # game units per metre (1 u = 1 cm)

THEMES = {
 'STUDY DESK': dict(slug='study_desk', surface='textures/surfaces/wood_walnut.jpg', road='textures/surfaces/paper_ruled.jpg', backdrop='textures/backdrops/room_apartment.jpg', light='DUSK_LAMP', music='audio/music/race_study_desk_loop.ogg',
   hero=['desk_lamp', 'teacup', 'water_bottle', 'vase_flowers', 'lantern', 'chess_king_w', 'chess_queen_b'], pylons=['chess_pawn_w', 'chess_pawn_b'], cone=True),
 'GAMER DESK': dict(slug='gamer_desk', surface='textures/surfaces/desk_mat_dark.jpg', road='textures/surfaces/road_asphalt.jpg', backdrop='textures/backdrops/room_studio.jpg', light='NIGHT_RGB', music='audio/music/race_gamer_desk_loop.ogg',
   hero=['scifi_helmet', 'toy_car', 'water_bottle', 'stained_glass_lamp', 'teacup', 'chess_knight_b'], pylons=['traffic_cone'], cone=True),
 'KITCHEN COUNTER': dict(slug='kitchen_counter', surface='textures/surfaces/marble_white.jpg', road='textures/surfaces/cutting_board.jpg', backdrop='textures/backdrops/room_lobby.jpg', light='MORNING_SUN', music='audio/music/race_kitchen_loop.ogg',
   hero=['olive_dish', 'mandarin_orange', 'avocado', 'teacup', 'water_bottle', 'vase_flowers', 'mandarin_orange', 'avocado'], pylons=['traffic_cone'], cone=True),
 'ART DESK': dict(slug='art_desk', surface='textures/surfaces/wood_oak_light.jpg', road='textures/surfaces/paper_paint.jpg', backdrop='textures/backdrops/room_apartment.jpg', light='SUNNY_STUDIO', music='audio/music/race_study_desk_loop.ogg',
   hero=['vase_flowers', 'potted_plant', 'teacup', 'stained_glass_lamp', 'water_bottle', 'lantern'], pylons=['traffic_cone'], cone=True),
 'BOOKSHELF SPIRAL': dict(slug='bookshelf_spiral', surface='textures/surfaces/wood_walnut.jpg', road='textures/surfaces/cardboard_kraft.jpg', backdrop='textures/backdrops/room_lobby.jpg', light='LIBRARY_WARM', music='audio/music/race_bonus_loop.ogg',
   hero=['lantern', 'desk_lamp', 'chess_rook_w', 'chess_rook_b', 'chess_bishop_w', 'potted_plant'], pylons=['chess_pawn_w', 'chess_pawn_b'], cone=False),
 'BATHTUB RIM': dict(slug='bathtub_rim', surface='textures/surfaces/tiles_bath.jpg', road='textures/surfaces/tiles_bath.jpg', backdrop='textures/backdrops/room_studio.jpg', light='BATHROOM_BRIGHT', music='audio/music/race_bonus_loop.ogg',
   hero=['water_bottle', 'potted_plant', 'vase_flowers', 'teacup', 'water_bottle'], pylons=['traffic_cone'], cone=True),
 'TOY BOX': dict(slug='toy_box', surface='textures/surfaces/rug_playroom.jpg', road='textures/surfaces/road_asphalt.jpg', backdrop='textures/backdrops/room_apartment.jpg', light='PLAYROOM_SOFT', music='audio/music/race_kitchen_loop.ogg',
   hero=['toy_car', 'chess_knight_w', 'chess_king_b', 'toy_car', 'potted_plant', 'lantern'], pylons=['traffic_cone'], cone=True),
 'MIDNIGHT OFFICE': dict(slug='midnight_office', surface='textures/surfaces/wood_walnut.jpg', road='textures/surfaces/road_asphalt.jpg', backdrop='textures/backdrops/city_day.jpg', light='MIDNIGHT_CITY', music='audio/music/race_gamer_desk_loop.ogg',
   hero=['desk_lamp', 'stained_glass_lamp', 'scifi_helmet', 'teacup', 'water_bottle', 'potted_plant'], pylons=['traffic_cone'], cone=True),
}

def footprint(name, scale):
    s = props[name]['sizeMeters']; return max(s[0], s[2]) * 0.5 * U * scale

def clearance(pts, w, x, z, r, y=0.0):
    d = np.hypot(pts[:, 0] - x, pts[:, 2] - z)
    return d.min() - (w / 2 + r)  # conservative: never under bridges either

for name, d in T.TRACKS.items():
    th = THEMES[name]; rng = np.random.default_rng(abs(hash(th['slug'])) % 2**32)
    pts = T.build(d['cps']); w = d['width']; n = len(pts)
    seg = np.linalg.norm(np.diff(np.vstack([pts, pts[:1]])[:, [0, 2]], axis=0), axis=1); cum = np.concatenate([[0], np.cumsum(seg)[:-1]]); L = seg.sum()
    tang = np.roll(pts, -1, 0) - np.roll(pts, 1, 0); tang[:, 1] = 0; tang /= np.linalg.norm(tang, axis=1, keepdims=True)
    right = np.stack([-tang[:, 2], np.zeros(n), tang[:, 0]], 1)
    # curvature sign (left/right turn) for apex placement
    a_ = tang[:, [0, 2]]; b_ = np.roll(tang, -6, 0)[:, [0, 2]]; turn = a_[:, 0] * b_[:, 1] - a_[:, 1] * b_[:, 0]
    placed = []
    def ok(x, z, r):
        if clearance(pts, w, x, z, r) < 2.5: return False
        if abs(x) > 175 or abs(z) > 160: return False
        return all(math.hypot(x - p['x'], z - p['z']) > r + p['radius'] + 1.5 for p in placed)
    def put(model, x, z, yaw, scale=1.0, solid=True, role='decor'):
        r = footprint(model, scale)
        placed.append(dict(model=props[model]['file'], name=model, role=role, x=round(float(x), 2), y=0.0, z=round(float(z), 2), yawRad=round(float(yaw), 3),
                           scale=round(scale * U, 2), radius=round(r, 2), solid=solid))
    # 1) apex cones / pawns on the INSIDE of corners, every ~22 u through bends
    pyl = th['pylons']; last = -1e9
    for i in range(0, n, 2):
        if abs(turn[i]) < 0.05 or cum[i] - last < 22 or pts[i][1] > 0.5: continue
        side = -1 if turn[i] < 0 else 1  # inside of the bend
        m = pyl[(i // 2) % len(pyl)]; r = footprint(m, 1.0)
        off = w / 2 + r + 3.0
        x, z = pts[i][0] + right[i][0] * off * side, pts[i][2] + right[i][2] * off * side
        if ok(x, z, r): put(m, x, z, rng.uniform(0, 6.28), 1.0, True, 'pylon'); last = cum[i]
    # 2) hero landmarks: pick spread-out spots 12-45 u off the outside of the road, facing the track
    for k, m in enumerate(th['hero']):
        r = footprint(m, 1.0)
        for attempt in range(400):
            i = int(rng.integers(0, n)); side = rng.choice([-1, 1]); off = w / 2 + r + rng.uniform(6, 30)
            x, z = pts[i][0] + right[i][0] * off * side, pts[i][2] + right[i][2] * off * side
            if pts[i][1] > 0.5 or not ok(x, z, r): continue
            yaw = math.atan2(pts[i][0] - x, pts[i][2] - z)
            if all(math.hypot(x - p['x'], z - p['z']) > 60 for p in placed if p['role'] == 'landmark') or attempt > 300:
                put(m, x, z, yaw, 1.0, True, 'landmark'); break
    # 3) start/finish flags (Kenney flag scaled), both sides of the line
    data = dict(name=name, slug=th['slug'], roadWidth=w, lengthApprox=round(float(L)), controlPoints=[list(map(float, c)) for c in d['cps']],
                splineSubdivisionsPerSegment=18, unitsPerMetre=U,
                surfaceTexture=th['surface'], surfaceTextureTileUnits=60, roadTexture=th['road'], roadTextureTileUnits=24,
                kerbTexture='textures/surfaces/kerb_red_white.png', finishTexture='textures/surfaces/checker_finish.png',
                backdropEquirect=th['backdrop'], lightingPreset=th['light'], music=th['music'], props=placed)
    json.dump(data, open(f"{OUT}/tracks/{th['slug']}.json", 'w'), indent=1)
    print(f"{name:18s} props={len(placed):3d} landmarks={sum(p['role']=='landmark' for p in placed)} pylons={sum(p['role']=='pylon' for p in placed)}")
