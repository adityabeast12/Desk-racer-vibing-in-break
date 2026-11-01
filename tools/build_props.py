import glob, json, importlib.util, sys
spec=importlib.util.spec_from_file_location('g','/home/claude/tools/gltf2drm2.py'); g=importlib.util.module_from_spec(spec); spec.loader.exec_module(g)
K='/home/claude/assets_src/khronos/Models'; OUT='/home/claude/assetpack_v3'
def src(m):
    b=glob.glob(f'{K}/{m}/glTF-Binary/*.glb'); return b[0] if b else glob.glob(f'{K}/{m}/glTF/*.gltf')[0]
plan=[
 ('traffic_cone','TrafficCone',2500,512,dict(only_parts=['Cone Normal'],height=0.05)),
 ('water_bottle','WaterBottle',4500,512,dict(height=0.26)),
 ('avocado','Avocado',682,512,dict(height=0.10)),
 ('mandarin_orange','MandarinOrange',8000,512,dict(height=0.06)),
 ('teacup','DiffuseTransmissionTeacup',8000,512,dict(height=0.07)),
 ('desk_lamp','AnisotropyBarnLamp',10203,512,dict(height=0.45)),
 ('vase_flowers','GlassVaseFlowers',14036,512,dict(height=0.30)),
 ('potted_plant','DiffuseTransmissionPlant',14000,512,dict(height=0.38)),
 ('chair','SheenChair',14000,512,dict(height=0.80)),
 ('sofa','SheenWoodLeatherSofa',14000,512,dict(height=0.90)),
 ('scifi_helmet','SciFiHelmet',18000,1024,dict(height=0.25)),
 ('toy_car','ToyCar',14000,1024,dict(only_parts=['ToyCar','Glass'])),
 ('lantern','Lantern',5394,512,dict(height=0.45)),
 ('olive_dish','IridescentDishWithOlives',12000,512,dict(height=0.06)),
 ('stained_glass_lamp','StainedGlassLamp',12000,512,dict(height=0.55)),
]
for piece in ['King_W','Queen_W','Bishop_W1','Knight_W1','Castle_W1','Pawn_Body_W1','King_B','Queen_B','Bishop_B1','Knight_B1','Castle_B1','Pawn_Body_B1']:
    nm='chess_'+piece.lower().replace('pawn_body','pawn').replace('1','').replace('castle','rook')
    parts=[piece]+(['Pawn_Top_'+piece.split('_')[-1]] if piece.startswith('Pawn') else [])
    plan.append((nm,'ABeautifulGame',4000,512,dict(only_parts=parts)))

only=set(sys.argv[1:])
man=json.load(open('/home/claude/tools/props_manifest_tmp.json')) if only else {}
for n,m,b,t,kw in plan:
    if only and n not in only: continue
    try: man[n]=dict(source=m, **g.convert(src(m),OUT,n,b,t,**kw))
    except Exception as e: print('FAIL',n,repr(e)[:300])
json.dump(man,open('/home/claude/tools/props_manifest_tmp.json','w'),indent=1)
