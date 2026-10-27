"""Neko cabinet choreography and vanilla sources; renderer lives in texture_compositor."""
import hashlib
import io
import json
import math
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw
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
          'up':[0,-1,0], 'fov_y':math.degrees(2*math.atan(112/(2*162))), 'center':[48,80]}
LIGHT = {'direction':[-.35,-.75,-.55], 'ambient':.7}
STORY = {'red_peek':[.8,2.4], 'red_jump_up':[3.2,4.4], 'red_reach_coin':[5.8,10.1],
         'black_peek':[10.3,12.4], 'red_notice_and_startle':[11.2,12.2],
         'red_jump_back':[12.2,13.7], 'black_jump_up':[12.5,13.7],
         'black_watch_red_exit':[13.7,16.2], 'black_look_outside':[16.2,19.2],
         'black_jump_back':[19.8,21.4]}


def smooth(a, b, t):
    q = max(0., min(1., (t-a)/(b-a)))
    return q*q*(3-2*q)


def pose_at(time, right):
    """Asymmetric cabinet-back story. Jumps move through model-space Y/Z arcs."""
    t = time % 24
    p = {'x':128 if right else 32, 'world_y':0., 'world_z':0., 'yaw':.55 if right else -.55,
         'body_pitch':0., 'body_roll':0., 'head_yaw':0., 'head_pitch':-.08,
         'sitting':0., 'crouch':0., 'push':0., 'tuck':0., 'reach':0.,
         'visible':False, 'occlusion_y':96, 'tail_sway':.12*math.sin(t*2.2+right),
         'stage':'hidden', 'airborne':False}
    peek_start, jump_start, jump_end = (10.3,12.5,13.7) if right else (.8,3.2,4.4)
    escape_start, escape_end = (19.8,21.4) if right else (12.2,13.7)
    if t < peek_start or t >= escape_end:
        return p
    p['visible'] = True
    if t < jump_start:
        appear = smooth(peek_start,peek_start+1.15,t)
        preload = smooth(jump_start-.55,jump_start,t)
        p.update(stage='peek_from_back', world_y=20-15*appear+1.8*preload,
                 world_z=6., sitting=.95, crouch=preload,
                 head_yaw=(.32 if right else -.22)*math.sin(appear*math.pi/2))
    elif t < jump_end:
        u = (t-jump_start)/(jump_end-jump_start)
        p.update(stage='jump_up', airborne=True,
                 world_y=6.8*(1-u)-11*4*u*(1-u), world_z=6*(1-u),
                 x=p['x']+(-4 if right else 4)*u,
                 body_pitch=-.42*math.sin(math.pi*u),
                 push=max(0.,1-u*3), tuck=math.sin(math.pi*u),
                 head_pitch=-.20, occlusion_y=96 if u < .28 else 104,
                 sitting=0., tail_sway=.35*math.sin(math.pi*u))
    else:
        p.update(stage='on_counter', x=124 if right else 36, occlusion_y=104,
                 crouch=.6*(1-smooth(jump_end,jump_end+.45,t)))
        if right:
            outside = smooth(16.2,17.4,t)
            p.update(stage='watch_then_look_outside', sitting=.5,
                     head_yaw=.55*(1-outside)-1.05*outside,
                     head_pitch=-.10+.07*math.sin(t*1.6))
            turn = smooth(18.8,19.5,t)
            p['yaw'] = .55-2.7*turn
            p['head_yaw'] *= 1-turn
        else:
            reach = smooth(5.8,6.5,t)*(1-smooth(9.6,10.1,t))
            notice = smooth(11.2,11.6,t)
            turn = smooth(11.8,12.2,t)
            p.update(stage='reach_coin' if reach > 0 else 'notice_black_cat',
                     reach=reach, body_pitch=-.48*reach+.12*notice,
                     head_pitch=-.38*reach-.16*notice,
                     head_yaw=-.75*notice*(1-turn),
                     yaw=-.55+2.9*turn, body_roll=-.08*notice,
                     crouch=max(p['crouch'],smooth(11.7,12.1,t)))
        p['crouch'] = max(p['crouch'],smooth(escape_start-.45,escape_start,t))
    if t >= escape_start:
        u = (t-escape_start)/(escape_end-escape_start)
        side = 1 if right else -1
        p.update(stage='jump_back', airborne=True,
                 x=(124 if right else 36)+side*13*u,
                 world_y=13*u-13*4*u*(1-u), world_z=10*u,
                 yaw=-2.15 if right else 2.35,
                 body_pitch=.30*math.sin(math.pi*u), body_roll=side*.13*math.sin(math.pi*u),
                 head_yaw=0., head_pitch=-.12,
                 sitting=0., crouch=0., reach=0., push=max(0.,1-u*3),
                 tuck=math.sin(math.pi*u), tail_sway=side*.4*math.sin(math.pi*u),
                 occlusion_y=104 if u < .52 else 96)
    return p


def part_pose(pose, right):
    result = {}
    for i, part in enumerate(MODEL['parts']):
        p = [a*(1-pose['sitting'])+b*pose['sitting'] for a,b in zip(STAND[i],SIT[i])]
        angle, yaw = p[3], 0
        if i == 0:
            angle, yaw = pose['head_pitch'], pose['head_yaw']
        if i in (2,3):
            yaw = pose['tail_sway']
            angle += .25*pose['tuck']-.12*pose['push']
        if i in (4,5):
            angle += .85*pose['crouch']+.95*pose['tuck']-.65*pose['push']
            p[1] -= 1.5*pose['crouch']
        if i in (6,7):
            angle -= .65*pose['crouch']+1.2*pose['tuck']+.35*pose['push']
            angle -= pose['reach']*(1.5 if i == 6 else 1.15)
            p[1] -= .8*pose['reach']
        if i == 0:
            p[1] -= .6*pose['reach']
        result[part['name']] = {'translation':[a-b for a,b in zip(p[:3],part['pivot'])],
                                'rotation':[angle,yaw,0]}
    return result


def verify_story():
    """Check narrative ordering and actual curved Y/Z flights, not flat sprite lifts."""
    assert pose_at(2,False)['stage'] == 'peek_from_back' and not pose_at(2,True)['visible']
    assert pose_at(7.5,False)['reach'] > .9 and not pose_at(7.5,True)['visible']
    assert pose_at(11,True)['stage'] == 'peek_from_back'
    assert pose_at(11.6,False)['head_yaw'] < -.6
    assert not pose_at(14,False)['visible'] and pose_at(14,True)['visible']
    assert pose_at(14.5,True)['head_yaw'] > 0 and pose_at(17.5,True)['head_yaw'] < 0
    flights = []
    for right, start, end in ((False,3.2,4.4),(False,12.2,13.7),
                              (True,12.5,13.7),(True,19.8,21.4)):
        a,b,c = [pose_at(t,right) for t in (start,(start+end)/2,end-.001)]
        assert b['world_y'] < min(a['world_y'],c['world_y'])-4
        assert abs(a['world_z']-c['world_z']) > 5
        assert b['tuck'] > .9 and a['push'] > .9
        assert abs(a['x']-c['x']) > 3
        assert part_pose(a,right) != part_pose(b,right)
        flights.append({'cat':'black' if right else 'red','start':start,'end':end,
                        'apex_model_y':b['world_y'],'travel_model_z':c['world_z']-a['world_z']})
    assert all(not pose_at(t,r)['visible'] for t in (0,23.9,24) for r in (False,True))
    return {'asymmetric_story_order':True,'look_only_no_approach_or_paw_touch':True,
            'model_space_curved_flights':flights,'closed_loop_hidden_endpoints':True}


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
            if not pose['visible']:
                continue
            # The empty parent recenters the actual vanilla geometry at its paw plane.
            model = {'texture_size':MODEL['texture_size'],
                     'parts':[{'name':'paw_origin','pivot':[0,-24-pose['sitting']*1.68,0]}]
                     +[dict(part,parent='paw_origin') for part in MODEL['parts']]}
            image = render_model(model,textures[right],size=(96,112),pose=part_pose(pose,right),
                                 camera=CAMERA,light=LIGHT,
                                 root_transform={'translation':[0,pose['world_y']+pose['crouch']*1.4,pose['world_z']],
                                                 'rotation':[pose['body_pitch'],pose['yaw'],pose['body_roll']]})
            layer = composite_layers((160,128),[{'image':image,'xy':[round(pose['x'])-48,101-80]}])
            mask = Image.new('L',(160,128))
            ImageDraw.Draw(mask).rectangle((2,2,157,pose['occlusion_y']-1),fill=255)
            layers.append({'image':layer,'mask':mask})
        frames.append(composite_layers((160,128),layers))
        poses.append(pair)
    return frames, poses
