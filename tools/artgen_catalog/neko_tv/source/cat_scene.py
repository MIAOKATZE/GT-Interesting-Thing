"""Neko cabinet choreography and vanilla sources; renderer lives in texture_compositor."""
import hashlib
import io
import json
import math
import zipfile
from pathlib import Path

from PIL import Image
from texture_compositor import composite_layers, render_model

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
MODEL = json.loads((HERE / 'cat-model.json').read_text(encoding='utf-8'))
# ModelOcelot setLivingAnimations / setRotationAngles standing and sitting pivots.
STAND = [[0,15,-9,0], [0,12,-10,math.pi/2], [0,15,8,.9],
         [0,20,14,1.7278761], [1.1,18,5,0], [-1.1,18,5,0],
         [1.2,13.8,-5,0], [-1.2,13.8,-5,0]]
SIT = [[0,11.7,-8,0], [0,8,-5,math.pi/4], [0,23,6,1.7278761],
       [0,22,13.2,2.670354], [1.1,21,1,-math.pi/2], [-1.1,21,1,-math.pi/2],
       [1.2,15.8,-7,-.15707964], [-1.2,15.8,-7,-.15707964]]
CAMERA = {'projection':'perspective', 'eye':[0,-14.34,-88.85], 'target':[0,0,0],
          'up':[0,-1,0], 'fov_y':math.degrees(2*math.atan(80/(2*162))), 'center':[48,48]}
LIGHT = {'direction':[-.35,-.75,-.55], 'ambient':.7}


def smooth(a, b, t):
    q = max(0., min(1., (t-a)/(b-a)))
    return q*q*(3-2*q)


def pose_at(time, right):
    """24s fixed stage: peek, climb, look, approach, touch paws, return, descend."""
    t = time % 24
    side = -1 if right else 1
    delay = .55 if right else 0
    appear = smooth(.8+delay, 3.0+delay, t)
    climb = smooth(3.5+delay, 5.8+delay, t)
    descend = smooth(19.3+delay, 22.2+delay, t)
    approach = smooth(10,12.5,t)*(1-smooth(16.2,18.7,t))
    interact = smooth(12.7,13.6,t)*(1-smooth(15.6,16.2,t))
    look = math.sin((t-6.2)*1.15)*smooth(6,7,t)*(1-smooth(9.6,10.5,t))
    return {
        'x':(130 if right else 30)+side*26*approach,
        'baseline':104+(1-appear)*32+(1-climb)*13+descend*34,
        'yaw':-side*(.48+.76*approach),
        'head_yaw':side*(.40*look-.12*approach),
        'head_pitch':-.08+.08*math.sin(t*1.8+right)+.12*interact,
        'sitting':.65*climb*(1-approach)*(1-descend),
        'stride':math.sin(t*5.5+right*math.pi)*.28*(smooth(3.4,4.0,t)*(1-smooth(5.1,5.8,t))
                   +smooth(10,10.4,t)*(1-smooth(12,12.5,t))
                   +smooth(16.2,16.7,t)*(1-smooth(18,18.7,t))),
        'paw':interact*(.88+.12*math.cos((t-14.4)*math.pi*2)),
        'visible':appear*(1-descend), 'tail_sway':.18*math.sin(t*2.2+right)}


def part_pose(pose, right):
    result = {}
    for i, part in enumerate(MODEL['parts']):
        p = [a*(1-pose['sitting'])+b*pose['sitting'] for a,b in zip(STAND[i],SIT[i])]
        angle, yaw = p[3], 0
        if i == 0:
            angle, yaw = pose['head_pitch'], pose['head_yaw']
        if i in (2,3):
            yaw = pose['tail_sway']
        if i in (4,7):
            angle += pose['stride']
        if i in (5,6):
            angle -= pose['stride']
        if i == (7 if right else 6):
            angle -= pose['paw']*1.5
        result[part['name']] = {'translation':[a-b for a,b in zip(p[:3],part['pivot'])],
                                'rotation':[angle,yaw,0]}
    return result


def load_textures():
    provenance = MODEL['provenance']
    path = ROOT / provenance['texture_jar']
    if not path.is_file():
        raise FileNotFoundError(f'{path}: run Gradle Minecraft setup/build to obtain vanilla resources first')
    textures = []
    with zipfile.ZipFile(path) as archive:
        for name in ('red','black'):
            source = provenance['texture_entries'][name]
            data = archive.read(source['entry'])
            assert hashlib.sha256(data).hexdigest() == source['sha256'], f'{name} texture source changed'
            texture = Image.open(io.BytesIO(data)).convert('RGBA')
            assert texture.size == (64,32)
            textures.append(texture)
    return textures


def render_frames(count=240, fps=10):
    textures = load_textures()
    frames, poses = [], []
    for n in range(count):
        layers = []
        pair = [pose_at(n/fps,right) for right in (False,True)]
        for right in (False,True):
            pose = pair[right]
            if pose['visible'] <= 0:
                continue
            image = render_model(MODEL,textures[right],size=(96,80),pose=part_pose(pose,right),
                                 camera=CAMERA,light=LIGHT,
                                 root_transform={'translation':[0,-24-pose['sitting']*1.68,0],
                                                 'rotation':[0,pose['yaw'],0]})
            layers.append({'image':image,'xy':[round(pose['x'])-48,round(pose['baseline'])-48]})
        frames.append(composite_layers((160,128),layers))
        poses.append(pair)
    return frames, poses
