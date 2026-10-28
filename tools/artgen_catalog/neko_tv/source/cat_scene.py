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
         'black_peek':[10.3,12.2], 'red_notice_and_startle':[11.2,12.2],
         'red_jump_back':[12.2,13.7], 'black_climb_up':[12.2,15.2],
         'black_watch_red_exit':[15.2,16.2], 'black_look_outside':[16.2,17.3],
         'black_walk_left':[17.3,21.2], 'black_jump_down_left':[21.2,22.7]}


def smooth(a, b, t):
    q = max(0., min(1., (t-a)/(b-a)))
    return q*q*(3-2*q)


def pose_at(time, right):
    """Reach inward, climb over the rear ledge, then walk to the opposite exit."""
    t = time % 24
    p = {'time':t, 'x':128 if right else 32, 'world_y':0., 'world_z':0., 'yaw':.55 if right else -.55,
         'body_pitch':0., 'body_roll':0., 'head_yaw':0., 'head_pitch':-.08,
         'sitting':0., 'crouch':0., 'push':0., 'tuck':0., 'reach':0.,
         'climb':0., 'walk':0., 'walk_phase':0.,
         'visible':False, 'occlusion_y':96, 'tail_sway':.12*math.sin(t*2.2+right),
         'stage':'hidden', 'airborne':False}
    peek_start, jump_start, jump_end = (10.3,12.2,15.2) if right else (.8,3.2,4.4)
    escape_start, escape_end = (21.2,22.7) if right else (12.2,13.7)
    if t < peek_start or t >= escape_end:
        return p
    p['visible'] = True
    if t < jump_start:
        appear = smooth(peek_start,peek_start+1.15,t)
        preload = smooth(jump_start-.55,jump_start,t)
        p.update(stage='peek_from_back', world_y=20-(7 if right else 15)*appear+(0 if right else 1.8)*preload,
                 world_z=6., sitting=.95*(1-preload), crouch=preload,
                 head_yaw=(.32 if right else -.22)*math.sin(appear*math.pi/2))
        if right:
            grip = smooth(11.5,12.2,t)
            p.update(sitting=.95*(1-grip), crouch=0., body_pitch=-.55*grip,
                     world_z=6-2*grip, climb=grip, head_yaw=.32*(1-grip),
                     occlusion_y=104 if grip > .1 else 96)
    elif t < jump_end:
        u = (t-jump_start)/(jump_end-jump_start)
        if right:
            # The forepaws remain on the same rear-ledge points during the pull.
            # Each hind leg takes a separate step; there is no ballistic flight.
            lift = smooth(12.2,14.9,t)
            settle = smooth(14.6,15.2,t)
            p.update(stage='climb_up', world_y=13*(1-lift), world_z=4*(1-lift),
                     body_pitch=-.55*(1-lift), climb=1-settle,
                     head_pitch=-.16, occlusion_y=104, x=128,
                     tail_sway=.15*math.sin(t*2.2))
        else:
            p.update(stage='jump_up', airborne=True,
                 world_y=6.8*(1-u)-11*4*u*(1-u), world_z=6*(1-u),
                 x=p['x']+(-4 if right else 4)*u,
                 body_pitch=-.42*math.sin(math.pi*u),
                 push=max(0.,1-u*3), tuck=math.sin(math.pi*u),
                 head_pitch=-.20, occlusion_y=96 if u < .28 else 104,
                 sitting=0., tail_sway=.35*math.sin(math.pi*u))
    else:
        p.update(stage='on_counter', x=128 if right else 36, occlusion_y=104,
                 crouch=0. if right else .6*(1-smooth(jump_end,jump_end+.45,t)))
        if right:
            outside = smooth(16.2,16.9,t)
            p.update(stage='watch_then_look_outside', sitting=.3*smooth(15.2,15.6,t)*(1-smooth(17,17.3,t)),
                     head_yaw=.55*(1-outside)-1.05*outside,
                     head_pitch=-.10+.07*math.sin(t*1.6))
            turn = smooth(17.,17.5,t)
            p['yaw'] = .55+(math.pi/2-.55)*turn
            p['head_yaw'] *= 1-turn
            if t >= 17.3:
                travel = smooth(17.3,21.2,t)
                stride = smooth(17.3,17.6,t)*(1-smooth(20.9,21.2,t))
                p.update(stage='walk_left', x=128-92*travel,
                         walk=stride, walk_phase=travel*math.pi*8,
                         world_y=-.25*stride*(1-math.cos(travel*math.pi*16)),
                         sitting=0., head_pitch=-.06,
                         tail_sway=.18*math.sin(travel*math.pi*8))
        else:
            reach = smooth(5.8,6.5,t)*(1-smooth(9.6,10.1,t))
            notice = smooth(11.2,11.6,t)
            turn = smooth(11.8,12.2,t)
            p.update(stage='reach_coin' if reach > 0 else 'notice_black_cat',
                     reach=reach, body_pitch=-.48*reach+.12*notice,
                     head_pitch=-.38*reach-.16*notice,
                     head_yaw=.8*notice*(1-turn),
                     yaw=-.55-1.85*smooth(4.4,5.8,t)-(2*math.pi-4.75)*turn,
                     body_roll=-.08*notice,
                     crouch=max(p['crouch'],smooth(11.7,12.1,t)))
        p['crouch'] = max(p['crouch'],smooth(escape_start-.45,escape_start,t))
    if t >= escape_start:
        u = (t-escape_start)/(escape_end-escape_start)
        side = -1
        p.update(stage='jump_down_left' if right else 'jump_back', airborne=True,
                 x=36-17*u,
                 world_y=17*u-15*4*u*(1-u), world_z=10*u,
                 yaw=math.pi/2 if right else 2.35,
                 body_pitch=(0 if right else .12)*(1-smooth(0,.18,u))+.30*math.sin(math.pi*u),
                 body_roll=(0 if right else -.08)*(1-smooth(0,.18,u))+side*.13*math.sin(math.pi*u),
                 head_yaw=0., head_pitch=-.12,
                 sitting=0., crouch=1-smooth(0,.18,u), reach=0., walk=0., push=max(0.,1-u*3),
                 tuck=math.sin(math.pi*u), tail_sway=side*.4*math.sin(math.pi*u),
                 occlusion_y=104 if u < .52 else 96)
    return p


def root_rotate(point, pose, inverse=False):
    """Scene-space root rotation; also used to pin paws to cabinet ledge points."""
    x,y,z = point
    pitch,yaw,roll = pose['body_pitch'],pose['yaw'],pose['body_roll']
    if inverse:
        x,y = x*math.cos(roll)+y*math.sin(roll), -x*math.sin(roll)+y*math.cos(roll)
        x,z = x*math.cos(yaw)-z*math.sin(yaw), x*math.sin(yaw)+z*math.cos(yaw)
        y,z = y*math.cos(pitch)+z*math.sin(pitch), -y*math.sin(pitch)+z*math.cos(pitch)
    else:
        y,z = y*math.cos(pitch)-z*math.sin(pitch), y*math.sin(pitch)+z*math.cos(pitch)
        x,z = x*math.cos(yaw)+z*math.sin(yaw), -x*math.sin(yaw)+z*math.cos(yaw)
        x,y = x*math.cos(roll)-y*math.sin(roll), x*math.sin(roll)+y*math.cos(roll)
    return [x,y,z]


def ledge_paw(pose, side):
    # Standing front paw centers on the rear half of the counter. Keep these
    # world-space contacts fixed while the shoulder and rear half are pulled up.
    return [side*1.2*math.cos(.55)-4*math.sin(.55), -3.2,
            -side*1.2*math.sin(.55)-4*math.cos(.55)]


def pin_front_leg(p, angle, pose, side):
    target = ledge_paw(pose, side)
    target[1] -= pose['world_y']+pose['crouch']*1.4
    target[2] -= pose['world_z']
    target = root_rotate(target,pose,inverse=True)
    target[1] += 24+pose['sitting']*1.68
    dy,dz = target[1]-p[1],target[2]-p[2]
    pinned_angle = math.atan2(dz,dy)-math.atan2(1,10)
    foot_y = 10*math.cos(pinned_angle)-math.sin(pinned_angle)
    foot_z = 10*math.sin(pinned_angle)+math.cos(pinned_angle)
    blend = pose['climb']
    p[:3] = [a*(1-blend)+b*blend for a,b in zip(p[:3],
              [target[0],target[1]-foot_y,target[2]-foot_z])]
    return angle*(1-blend)+pinned_angle*blend


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
            if right and pose['climb']:
                start,end = (13.4,14.5) if i == 4 else (14.1,15.1)
                step = smooth(start,end,pose.get('time',0))
                angle -= 1.15*math.sin(math.pi*step)*pose['climb']
                p[1] -= 1.8*math.sin(math.pi*step)*pose['climb']
        if i in (6,7):
            angle -= .65*pose['crouch']+1.2*pose['tuck']+.35*pose['push']
            angle -= pose['reach']*((1.65 if i == 6 else 1.5)
                     +.13*math.sin(pose['time']*5.2+(0 if i == 6 else math.pi)))
            p[1] -= .8*pose['reach']
            if right and pose['climb']:
                angle = pin_front_leg(p,angle,pose,1 if i == 6 else -1)
        if i in (4,5,6,7):
            # Diagonal pairs alternate in the original four-legged model.
            phase = pose['walk_phase']+(math.pi if i in (5,6) else 0)
            angle += .6*pose['walk']*math.sin(phase)
            p[1] -= .75*pose['walk']*max(0,math.sin(phase))
        if i == 0:
            p[1] -= .6*pose['reach']
        result[part['name']] = {'translation':[a-b for a,b in zip(p[:3],part['pivot'])],
                                'rotation':[angle,yaw,0]}
    return result


def part_point(pose, right, index, point):
    """A point on the actual animated ModelBox, in rendered scene coordinates."""
    part = MODEL['parts'][index]
    transform = part_pose(pose,right)[part['name']]
    local = root_rotate(point,dict(zip(('body_pitch','yaw','body_roll'),transform['rotation'])))
    local = [a+b+c for a,b,c in zip(local,part['pivot'],transform['translation'])]
    local[1] -= 24+pose['sitting']*1.68
    world = root_rotate(local,pose)
    world[1] += pose['world_y']+pose['crouch']*1.4
    world[2] += pose['world_z']
    return world


def project_point(pose, point):
    x,y,z = point
    ey,ez = CAMERA['eye'][1:]
    distance = math.hypot(ey,ez)
    fy,fz = -ey/distance,-ez/distance
    depth = fy*(y-ey)+fz*(z-ez)
    return [round(pose['x'])+162*x/depth,101+162*(fz*y-fy*z)/depth]


def verify_story():
    """Check actual nose/paw directions, ledge contacts, gait and exit geometry."""
    assert pose_at(2,False)['stage'] == 'peek_from_back' and not pose_at(2,True)['visible']
    red = pose_at(7.5,False)
    assert red['reach'] > .9 and not pose_at(7.5,True)['visible']
    assert pose_at(11,True)['stage'] == 'peek_from_back'
    assert pose_at(11.6,False)['head_yaw'] > .6
    assert not pose_at(14,False)['visible'] and pose_at(14,True)['visible']
    assert pose_at(15.8,True)['head_yaw'] > 0 and pose_at(16.9,True)['head_yaw'] < 0
    nose,head = [part_point(red,False,0,p) for p in ((0,0,-4),(0,0,0))]
    assert nose[2] > head[2], 'Red nose must face away from the viewer toward the coin screen'
    reach = []
    for i in (6,7):
        paw,shoulder = [part_point(red,False,i,p) for p in ((0,10,1),(0,0,0))]
        px,sx = project_point(red,paw),project_point(red,shoulder)
        assert paw[2] > shoulder[2] and px[0] > sx[0]+4 and px[1] < sx[1]-1
        reach.append({'part':MODEL['parts'][i]['name'],'paw_screen':px,'shoulder_screen':sx,
                      'toward_screen_delta_z':paw[2]-shoulder[2]})
    for n in range(65,97):
        p = pose_at(n/10,False)
        for i in (6,7):
            paw,shoulder = [part_point(p,False,i,q) for q in ((0,10,1),(0,0,0))]
            a,b = project_point(p,paw),project_point(p,shoulder)
            assert paw[2] > shoulder[2] and a[0] > b[0]+4 and a[1] < b[1]-1
    climb = [pose_at(n/10,True) for n in range(122,153)]
    assert all(not p['airborne'] for p in climb)
    assert all(a['world_y'] >= b['world_y'] for a,b in zip(climb,climb[1:]))
    contacts = []
    for i in (6,7):
        points = [part_point(p,True,i,(0,10,1)) for p in climb if p['time'] <= 14.6]
        drift = max(math.dist(a,b) for a in points for b in points)
        assert drift < 1e-8, 'Climbing forepaw slipped off its fixed ledge contact'
        contacts.append({'part':MODEL['parts'][i]['name'],'max_world_drift':drift,
                         'screen_contact':project_point(climb[0],points[0])})
    hind = [part_pose(pose_at(t,True),True) for t in (13.4,14.1,14.6,15.1)]
    assert hind[1]['ocelotBackLeftLeg']['rotation'][0] < hind[1]['ocelotBackRightLeg']['rotation'][0]-.3
    assert hind[2]['ocelotBackRightLeg']['rotation'][0] < hind[2]['ocelotBackLeftLeg']['rotation'][0]-.3
    walk = [pose_at(n/10,True) for n in range(173,212)]
    assert all(a['x'] > b['x'] for a,b in zip(walk,walk[1:]))
    assert abs(pose_at(21.2,True)['x']-pose_at(7.5,False)['x']) < 1e-8
    gait = [part_pose(pose_at(t,True),True) for t in (18.2,18.5)]
    assert gait[0]['ocelotBackLeftLeg']['rotation'] != gait[1]['ocelotBackLeftLeg']['rotation']
    # Cats are on opposite ends until red has left; walking starts much later.
    assert all(abs(pose_at(n/10,True)['x']-pose_at(n/10,False)['x']) > 80
               for n in range(103,137))
    flights = []
    for right, start, end in ((False,3.2,4.4),(False,12.2,13.7),(True,21.2,22.7)):
        a,b,c = [pose_at(t,right) for t in (start,(start+end)/2,end-.001)]
        assert b['world_y'] < min(a['world_y'],c['world_y'])-4
        assert abs(a['world_z']-c['world_z']) > 5
        assert b['tuck'] > .9 and a['push'] > .9
        assert abs(a['x']-c['x']) > 3
        assert part_pose(a,right) != part_pose(b,right)
        flights.append({'cat':'black' if right else 'red','start':start,'end':end,
                        'apex_model_y':b['world_y'],'travel_model_z':c['world_z']-a['world_z']})
    assert all(not pose_at(t,r)['visible'] for t in (0,23.9,24) for r in (False,True))
    # Resolve continuity at stage joins using real animated geometry, not only roots.
    joins = []
    for right,times in ((False,(3.2,4.4,5.8,10.1,11.2,11.8,12.2)),
                        (True,(11.5,12.2,14.6,15.2,16.2,17.3,21.2))):
        for t in times:
            a,b = pose_at(t-1e-6,right),pose_at(t,right)
            jump = max(math.dist(part_point(a,right,i,(0,0,0)),part_point(b,right,i,(0,0,0)))
                       for i in range(8))
            assert jump < 3.5, (right,t,jump)
            joins.append({'cat':'black' if right else 'red','time':t,'max_part_pivot_delta':jump})
    return {'asymmetric_story_order':True,'look_only_no_cat_contact':True,
            'red_nose_toward_screen_delta_z':nose[2]-head[2],'red_reaching_paws':reach,
            'red_full_reach_direction_frames':32,
            'black_climb_no_airborne_frames':len(climb),'black_climb_fixed_forepaws':contacts,
            'black_hind_steps_alternate':True,'black_walk_left_frames':len(walk),
            'black_walk_destination_x':36,'stage_join_continuity':joins,
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
