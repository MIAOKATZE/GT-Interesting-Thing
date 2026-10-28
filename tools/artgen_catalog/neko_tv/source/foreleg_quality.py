"""Scene-only foreleg attachment / penetration evidence using the renderer's matrices.

Run: python -B tools/artgen_catalog/neko_tv/source/foreleg_quality.py
Optional --baseline path preserves a negative control and before/after close views.
"""
import argparse
import copy
import json
import sys
import types
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0,str(ROOT.parent / '贴图复合渲染/src'))
from texture_compositor import render_model
from texture_compositor.renderer import _part_matrices
import cat_scene

OUT = ROOT / 'build/neko-tv-preview/foreleg-fix'


def model_at(scene,p):
    return {'texture_size':scene.MODEL['texture_size'],
            'parts':[{'name':'paw_origin','pivot':[0,-24-p['sitting']*1.68,0]}]
                    +[dict(part,parent='paw_origin') for part in scene.MODEL['parts']]}


def root_at(p):
    return {'translation':[0,p['world_y']+p['crouch']*1.4,p['world_z']],
            'rotation':[p['body_pitch'],p['yaw'],p['body_roll']]}


def measure(scene):
    worst = {'head':{'depth':0},'chest':{'depth':0}}
    shoulder_shift = 0.
    shoulder_chest_gap = 0.
    matrix_error = 0.
    hits = {'head':0,'chest':0}
    checked = 0
    # Actual interior points of the original front-leg box. The first 2 units
    # are the joint. Chest overlap in its proximal 6 units is vanilla anatomy;
    # only the distal portion should be outside the chest. Head has no such joint.
    grid = np.array([[x,y,z,1] for x in (-.75,0,.75)
                     for y in np.arange(2.25,9.76,.5) for z in (.25,1,1.75)])
    for n in range(240):
        for right in (False,True):
            p = scene.pose_at(n/10,right)
            if not p['visible']:
                continue
            model = model_at(scene,p)
            pose = scene.part_pose(p,right)
            matrices = _part_matrices(model,pose,root_at(p))
            for i in (6,7):
                name = scene.MODEL['parts'][i]['name']
                matrix = matrices[name]
                expected = np.array(scene.STAND[i][:3])*(1-p['sitting'])+np.array(scene.SIT[i][:3])*p['sitting']
                actual = np.array(scene.MODEL['parts'][i]['pivot'])+pose[name]['translation']
                shoulder_shift = max(shoulder_shift,float(np.linalg.norm(actual-expected)))
                chest_inverse = np.linalg.inv(matrices['ocelotBody'])
                joint = (chest_inverse @ matrix @ np.array([0,0,0,1]))[:3]
                chest_box = scene.MODEL['parts'][1]['boxes'][0]
                low = np.array(chest_box['origin']); high = low+chest_box['size']
                gap = np.maximum(np.maximum(low-joint,joint-high),0)
                shoulder_chest_gap = max(shoulder_chest_gap,float(np.linalg.norm(gap)))
                world = grid @ matrix.T
                for label,j in (('head',0),('chest',1)):
                    local = world @ np.linalg.inv(matrices[scene.MODEL['parts'][j]['name']]).T
                    per_point = np.zeros(len(grid))
                    for box in scene.MODEL['parts'][j]['boxes']:
                        low = np.array(box['origin']); high = low+box['size']
                        depth = np.min(np.minimum(local[:,:3]-low,high-local[:,:3]),axis=1)
                        per_point = np.maximum(per_point,depth)
                    if label == 'chest':
                        per_point[grid[:,1] < 6.25] = 0
                    peak = float(per_point.max())
                    if peak > worst[label]['depth']:
                        worst[label] = dict(depth=peak,time=n/10,cat='black' if right else 'red',part=name)
                    hits[label] += int(np.count_nonzero(per_point > .3))
                # Cross-check choreography helpers against the exact matrices the
                # triangle renderer consumes, including both parent and root.
                if hasattr(scene,'part_point'):
                    for q in ((0,0,0),(0,10,1),(-1,5,2)):
                        actual_point = (matrix @ np.array([*q,1]))[:3]
                        matrix_error = max(matrix_error,float(np.linalg.norm(actual_point-scene.part_point(p,right,i,q))))
                checked += 1
    return dict(scope='All visible front legs in 240 timeline frames; renderer matrices and original boxes',
                sampled_legs=checked,max_shoulder_displacement_from_standing_sitting_joint=shoulder_shift,
                max_shoulder_center_distance_outside_actual_chest=shoulder_chest_gap,
                renderer_vs_scene_point_max_error=matrix_error,worst_penetration=worst,
                interior_samples_deeper_than_point3=hits,
                proximal_joint_exclusion_units=2,chest_distal_start_units=6.25)


def diagnostic_frame(scene,t,right,colored=False,side=False):
    p = scene.pose_at(t,right)
    model = model_at(scene,p)
    texture = scene.load_textures()[right]
    if colored:
        texture = Image.new('RGBA',(64,32),'#789a9c')
        model = copy.deepcopy(model)
        for part in model['parts']:
            name = part['name']
            color_index = {'ocelotFrontLeftLeg':1,'ocelotFrontRightLeg':2,
                           'ocelotHead':3,'ocelotBody':4}.get(name,0)
            for box in part.get('boxes',[]):
                box['uv'] = [0,0]
                # A separate solid texture is supplied through tiny disjoint UV bands.
                # All box UV faces fit within a 32px region for this model.
                box['uv'] = [0,color_index*32]
        texture = Image.new('RGBA',(64,160),'#789a9c')
        for k,color in enumerate(('#789a9c','#ef4b38','#428cfa','#e7cb59','#47a96e')):
            ImageDraw.Draw(texture).rectangle((0,k*32,63,(k+1)*32-1),fill=color)
        model['texture_size'] = [64,160]
    camera = scene.CAMERA.copy()
    camera.update(center=[120,115])
    root = root_at(p)
    if side:
        # Remove root yaw only for an orthogonal side diagnostic; anatomical poses stay identical.
        root['rotation'] = [p['body_pitch'],np.pi/2,0]
    camera['fov_y'] = 2*np.degrees(np.arctan(180/(2*300)))
    image = render_model(model,texture,size=(240,180),pose=scene.part_pose(p,right),
                         camera=camera,light=scene.LIGHT,root_transform=root)
    background = Image.new('RGBA',image.size,'#30291f')
    background.alpha_composite(image)
    ImageDraw.Draw(background).text((4,4),f'{t:.1f}s {"side" if side else "camera"}',fill='white')
    return background


def evidence(scene,label):
    keys = [(7.5,False),(12.2,True),(13,True),(13.8,True),(14.2,True),(14.7,True)]
    sheet = Image.new('RGBA',(240*3,180*4),'#30291f')
    for i,(t,right) in enumerate(keys):
        sheet.paste(diagnostic_frame(scene,t,right,True),((i%3)*240,(i//3)*360))
        sheet.paste(diagnostic_frame(scene,t,right,True,True),((i%3)*240,(i//3)*360+180))
    sheet.resize((1440,1440),Image.Resampling.NEAREST).save(OUT / f'{label}-colored-contact.png')
    for start,end,right,name in ((5.8,10.1,False,'reach'),(11.5,15.2,True,'climb')):
        frames = [diagnostic_frame(scene,n/10,right).convert('RGB')
                  for n in range(round(start*10),round(end*10)+1)]
        frames[0].save(OUT / f'{label}-{name}.gif',save_all=True,append_images=frames[1:],duration=100,loop=0)
        for n,t in enumerate((7.5,) if not right else (12.2,13.8,14.7)):
            diagnostic_frame(scene,t,right).resize((720,540),Image.Resampling.NEAREST).save(
                OUT / f'{label}-{name}-{t:.1f}.png')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--baseline',type=Path)
    parser.add_argument('--capture-only',action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    report = {}
    if args.baseline:
        old = types.ModuleType('cat_scene_baseline')
        old.__file__ = str(HERE/'cat_scene.py')
        exec(compile(args.baseline.read_text(encoding='utf-8'),str(args.baseline),'exec'),old.__dict__)
        report['before'] = measure(old)
        report['negative_control_detected'] = (
            report['before']['max_shoulder_displacement_from_standing_sitting_joint'] > .4
            and report['before']['worst_penetration']['head']['depth'] > .4
            and report['before']['worst_penetration']['chest']['depth'] > .4)
        evidence(old,'before')
    report['after'] = measure(cat_scene)
    evidence(cat_scene,'after')
    if args.baseline:
        for start,end,right,name in ((5.8,10.1,False,'reach'),(11.5,15.2,True,'climb')):
            frames = []
            for n in range(round(start*10),round(end*10)+1):
                tile = Image.new('RGB',(480,200),'#30291f')
                for x,scene,label in ((0,old,'BEFORE'),(240,cat_scene,'AFTER')):
                    tile.paste(diagnostic_frame(scene,n/10,right),(x,20))
                    ImageDraw.Draw(tile).text((x+5,3),label,fill='white')
                frames.append(tile)
            frames[0].save(OUT / f'compare-{name}.gif',save_all=True,
                           append_images=frames[1:],duration=100,loop=0)
    (OUT/'geometry.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2))
    if not args.capture_only:
        assert report['after']['max_shoulder_displacement_from_standing_sitting_joint'] < 1e-8
        assert report['after']['max_shoulder_center_distance_outside_actual_chest'] <= .25
        assert report['after']['renderer_vs_scene_point_max_error'] < 1e-8
        assert report['after']['worst_penetration']['head']['depth'] <= .4
        assert report['after']['worst_penetration']['chest']['depth'] <= .4
        if args.baseline:
            assert report['negative_control_detected']


if __name__ == '__main__':
    main()
