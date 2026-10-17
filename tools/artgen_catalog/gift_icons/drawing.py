"""Hand-authored gift boxes, drawn one native pixel at a time (no source images).

Every theme has a lid, wrapped body and bow. Material construction and front-face
emblems distinguish themes even when viewed at Minecraft's usual small size.
"""
from PIL import ImageDraw
from palette import P, ramp


def parcel(p, material, ribbon, shape="square", pattern=None):
    """Stepped corners, a broad lid, offset ribbon and a shaded right face."""
    mid, hi, lo = ramp(material)
    if shape == "tall":
        x, r, top, bottom = 7, 25, 12, 29
    elif shape == "wide":
        x, r, top, bottom = 3, 28, 14, 28
    else:
        x, r, top, bottom = 5, 26, 12, 28
    if shape == "cut":
        p.poly([(x+2,top),(r-2,top),(r,top+2),(r,bottom-2),
                (r-2,bottom),(x+2,bottom),(x,bottom-2),(x,top+2)], material)
        p.line([(x+1,top+3),(x+1,bottom-3),(x+3,bottom-1)], hi)
    else:
        p.box((x, top, r, bottom), material)
    p.rect((r-3,top+2,r-1,bottom-2), lo)
    p.line([(x+2,bottom-2),(r-4,bottom-2)], mid)
    if pattern == "wood":
        for y in (18,23):
            p.line([(x+1,y),(r-4,y)], "copper_lo")
        p.line([(x+2,20),(x+4,20)], "copper_hi")
        p.line([(r-7,26),(r-5,26)], "copper_hi")
    elif pattern == "weave":
        for y in range(top+5,bottom-1,3):
            p.line([(x+1,y),(r-4,y)], lo)
        for xx in range(x+3,r-3,4):
            p.line([(xx,top+5),(xx,bottom-2)], hi)
    elif pattern == "metal":
        p.rivets(x+2,top+5,r-2,bottom-2)
        p.line([(r-3,top+6),(r-3,bottom-4)], hi)
    elif pattern == "dots":
        for xx, yy in ((7,18),(13,24),(23,17),(22,25),(15,17)):
            p.rect((xx,yy,xx,yy), hi)
    elif pattern == "stripe":
        for xx in range(x+3,r-3,6):
            p.line([(xx,top+5),(xx,bottom-2)], hi)
    # The loose ribbon ends and raised knot make each silhouette read as a gift.
    rm, rh, rl = ramp(ribbon)
    rx = 10 if shape != "tall" else 11
    p.rect((rx-1,top+2,rx+3,bottom-1), "ink")
    p.rect((rx,top+2,rx+2,bottom-2), rm)
    p.line([(rx,top+3),(rx,bottom-3)], rh)
    p.rect((rx+2,top+4,rx+2,bottom-2), rl)
    p.box((x-1,top-2,r+1,top+2), material)
    p.rect((rx,top-1,rx+2,top+1), rm)
    p.rect((rx,top-1,rx,top+1), rh)
    p.poly([(13,9),(11,12),(9,12),(11,8)], rm)
    p.poly([(17,9),(21,12),(18,12),(16,9)], rl)
    p.bow(15, top-4, ribbon)
    # One deliberately broken highlight is more like a drawn edge than a gradient.
    p.line([(x+1,top-1),(rx-2,top-1)], hi)
    p.rect((r-2,top,r-1,top), hi)


def star(p,x,y,color="yellow_hi"):
    p.poly([(x,y-4),(x+1,y-1),(x+4,y-1),(x+2,y+1),
            (x+3,y+4),(x,y+2),(x-3,y+4),(x-2,y+1),(x-4,y-1),(x-1,y-1)],color)


def leaf(p,x,y,color="green"):
    p.poly([(x-4,y+2),(x-3,y-2),(x+3,y-5),(x+4,y-1),(x+1,y+3)],color)
    p.line([(x-3,y+3),(x+2,y-3)],"green_hi")


def snow(p,x,y):
    p.line([(x,y-4),(x,y+4)],"white")
    p.line([(x-4,y),(x+4,y)],"white")
    p.line([(x-3,y-3),(x+3,y+3)],"blue_hi")
    p.line([(x-3,y+3),(x+3,y-3)],"blue_hi")
    p.rect((x-1,y-1,x+1,y+1),"white")


def draw_gift(key, Pixel):
    p = Pixel()
    styles = {
        "starter":("cloth","red","square","dots"),
        "steam":("copper","red","wide","metal"),
        "lv":("green","yellow","square","metal"),
        "mv":("blue","steel","tall","metal"),
        "hv":("steel","yellow","cut","metal"),
        "ev":("purple","pink","cut","metal"),
        "iv":("steel","teal","tall","metal"),
        "luv":("blue","steel","cut","metal"),
        "festival":("red","yellow","tall","dots"),
        "mail":("cloth","blue","wide",None),
        "team":("green","orange","wide","wood"),
        "energy":("steel","yellow","tall","metal"),
        "ae":("blue","purple","cut","metal"),
        "decor":("pink","green","square","stripe"),
        "mining":("wood","steel","wide","wood"),
        "farming":("cloth","green","wide","weave"),
        "food":("red","cloth","wide","weave"),
        "tools":("copper","blue","wide","metal"),
        "building":("red","steel","square","wood"),
        "magic":("purple","yellow","tall",None),
        "space":("steel","blue","cut","metal"),
        "ocean":("teal","cloth","square","stripe"),
        "forest":("green","wood","tall","wood"),
        "winter":("blue","red","square","dots"),
        "spring":("pink","green","wide","weave"),
        "summer":("teal","orange","wide","stripe"),
        "autumn":("orange","green","square",None),
        "birthday":("pink","blue","tall","dots"),
        "lottery":("purple","yellow","square","dots"),
        "achievement":("yellow","red","tall","metal"),
        "admin":("steel","red","square","metal"),
        "trade":("wood","red","wide","wood"),
    }
    parcel(p,*styles[key])
    # Front emblems are authored individually, with one-pixel ink separation.
    if key == "starter":
        p.cat(20,21,"cream")
        p.rect((15,24,16,24),"pink_hi")
    elif key == "steam":
        p.ellipse((15,16,25,26),"copper_lo")
        p.ellipse((16,17,24,25),"cream")
        p.line([(20,21),(22,18)],"red")
        for x,y in ((17,21),(20,18),(23,21)):
            p.rect((x,y,x,y),"copper")
        # Exposed pipe on the left differentiates the copper boiler wrapping.
        p.box((2,17,5,24),"copper")
        p.rect((3,18,3,22),"copper_hi")
    elif key == "lv":
        p.box((15,17,25,26),"green")
        p.box((17,19,22,24),"steel")
        for y in (20,23):
            p.line([(15,y),(17,y)],"green_hi")
            p.line([(22,y),(24,y)],"yellow_hi")
        p.rect((19,20,20,22),"green_lo")
        p.rect((19,20,19,21),"green_hi")
    elif key == "mv":
        p.box((15,16,24,26),"steel")
        p.rect((16,17,23,24),"blue_lo")
        p.line([(17,22),(19,20),(20,22),(22,19)],"blue_hi")
        p.rect((18,26,21,27),"steel_hi")
        p.rect((22,25,22,25),"yellow_hi")
    elif key == "hv":
        p.poly([(20,16),(26,26),(14,26)],"yellow")
        p.poly([(20,18),(18,22),(20,22),(19,25),(23,20),(21,20)],"ink",None)
        for y in (17,22,26):
            p.line([(6,y),(8,y+2)],"yellow_hi")
    elif key == "ev":
        p.ellipse((14,16,26,27),"purple_lo")
        p.diamond(20,21,"pink")
        p.line([(14,19),(12,19)],"purple_hi")
        p.rect((24,16,25,17),"pink_hi")
    elif key == "iv":
        p.diamond(20,21,"teal")
        p.line([(16,25),(20,28),(24,25)],"white")
        p.line([(23,16),(25,18)],"blue_hi")
    elif key == "luv":
        p.poly([(20,17),(25,20),(25,24),(20,27),(15,24),(15,20)],"white")
        p.poly([(16,21),(19,23),(19,26),(16,24)],"blue",None)
        p.poly([(21,23),(24,21),(24,24),(21,26)],"blue_lo",None)
        p.line([(13,23),(17,25),(23,25),(27,22)],"teal_hi")
        p.rect((14,23,14,23),"white")
    elif key == "festival":
        p.ellipse((15,16,24,25),"yellow_lo")
        p.ellipse((16,17,23,24),"yellow")
        p.line([(18,18),(18,23)],"yellow_hi")
        p.rect((17,15,22,16),"copper_hi")
        p.rect((18,25,21,25),"yellow_hi")
        p.line([(20,26),(20,29)],"red_hi")
        p.rect((19,29,21,29),"yellow")
    elif key == "mail":
        p.box((14,18,26,26),"cloth")
        p.rect((15,19,25,25),"cream")
        p.line([(15,19),(20,23),(25,19)],"copper")
        p.line([(15,25),(18,22)],"copper")
        p.line([(25,25),(22,22)],"copper")
        p.rect((23,19,24,20),"red")
        p.rect((20,23,20,23),"red_hi")
    elif key == "team":
        p.cat(16,19,"cream",tiny=True)
        p.cat(24,19,"yellow_hi",tiny=True)
        p.cat(20,24,"cream",tiny=True)
        p.line([(16,23),(16,25),(17,25)],"green_hi")
    elif key == "energy":
        p.rect((18,16,21,16),"yellow_hi")
        p.box((15,17,24,27),"steel")
        p.rect((16,18,23,26),"steel_lo")
        p.poly([(20,18),(17,22),(20,22),(18,26),(23,21),(20,21)],"yellow_hi",None)
    elif key == "ae":
        p.diamond(20,21,"purple")
        p.line([(20,17),(17,20),(20,25)],"white")
        p.line([(21,18),(23,20),(21,24)],"blue_hi")
        p.line([(14,17),(14,21),(16,21)],"purple_hi")
        p.line([(24,24),(25,24),(25,27)],"purple_hi")
    elif key == "decor":
        p.poly([(14,21),(20,16),(26,21)],"red")
        p.box((16,21,24,27),"cloth")
        p.rect((18,23,20,26),"wood")
        p.rect((22,22,23,23),"blue_hi")
        p.rect((22,16,23,18),"copper")
    elif key == "mining":
        p.line([(16,26),(23,17)],"ink",3)
        p.line([(16,26),(23,17)],"copper_hi")
        p.poly([(17,16),(21,15),(25,17),(27,20),(24,19),(21,17),(18,18)],"steel_hi")
        p.poly([(15,23),(17,21),(19,23),(17,25)],"teal_hi")
    elif key == "farming":
        for x,y in ((17,18),(23,17)):
            p.line([(x,y),(x-1,26)],"yellow_lo")
            for dy in (0,3):
                p.poly([(x,y+dy),(x-3,y+dy-2),(x-3,y+dy),(x,y+dy+2)],"yellow",None)
                p.poly([(x,y+dy),(x+2,y+dy-2),(x+2,y+dy),(x,y+dy+2)],"yellow_hi",None)
        p.line([(16,26),(23,26)],"green_lo")
    elif key == "food":
        p.poly([(14,22),(16,19),(23,19),(26,22),(24,26),(16,26)],"copper_lo")
        p.ellipse((15,18,25,24),"cloth")
        p.line([(18,20),(17,22)],"cream")
        p.line([(21,20),(20,22)],"cream")
        p.rect((23,18,23,18),"green_hi")
    elif key == "tools":
        p.line([(16,26),(23,18)],"ink",3)
        p.line([(16,26),(23,18)],"steel_hi")
        p.poly([(20,17),(21,15),(21,18),(23,19),(25,17),(25,20),(22,21)],"steel_hi")
        p.line([(16,17),(24,26)],"ink",3)
        p.line([(16,17),(24,26)],"copper_hi")
        p.box((14,16,18,19),"steel")
    elif key == "building":
        p.box((14,17,25,26),"red")
        p.line([(15,21),(24,21)],"cream")
        p.line([(15,25),(24,25)],"cream")
        p.line([(20,18),(20,20)],"cream")
        p.line([(17,22),(17,24)],"cream")
        p.line([(23,22),(23,24)],"cream")
    elif key == "magic":
        p.ellipse((15,17,23,25),"yellow_hi")
        p.ellipse((18,16,25,23),"purple",None)
        p.rect((23,17,23,19),"white")
        p.rect((22,18,24,18),"white")
        p.rect((16,27,16,27),"purple_hi")
    elif key == "space":
        p.poly([(20,16),(23,20),(23,24),(17,24),(17,20)],"white")
        p.rect((19,19,21,21),"blue_lo")
        p.rect((20,19,20,19),"blue_hi")
        p.poly([(17,22),(15,26),(18,25)],"red")
        p.poly([(23,22),(25,26),(22,25)],"red")
        p.poly([(19,25),(21,25),(20,28)],"orange_hi",None)
        p.rect((25,17,25,18),"yellow_hi")
        p.rect((24,17,26,17),"yellow_hi")
    elif key == "ocean":
        p.poly([(14,23),(16,20),(19,20),(21,22),(25,22),(25,26),(14,26)],"blue_lo")
        p.line([(14,23),(16,21),(18,21),(20,24),(23,24),(25,22)],"white")
        p.poly([(20,17),(23,16),(25,18),(23,20)],"cream")
        p.line([(23,17),(23,19)],"copper")
    elif key == "forest":
        leaf(p,20,21)
        p.line([(15,27),(23,19)],"wood",2)
        p.rect((23,18,23,18),"cream")
        p.rect((25,25,25,25),"green_hi")
    elif key == "winter":
        snow(p,20,22)
        p.rect((7,16,11,18),"cream")
        p.rect((9,20,11,21),"cream")
        p.rect((25,16,25,16),"white")
    elif key == "spring":
        for x,y,c in ((17,21,"pink_hi"),(24,23,"purple_hi")):
            p.line([(x,y+1),(x,y+5)],"green_lo")
            for dx,dy in ((-2,0),(2,0),(0,-2),(0,2)):
                p.rect((x+dx,y+dy,x+dx,y+dy),c)
            p.rect((x,y,x,y),"yellow_hi")
        p.rect((18,25,20,25),"green_hi")
    elif key == "summer":
        p.ellipse((16,17,25,26),"green_lo")
        p.ellipse((17,18,24,25),"yellow_hi")
        for xy in ([(20,18),(20,25)],[(17,21),(24,21)],[(18,19),(23,24)]):
            p.line(xy,"green")
        p.rect((25,16,26,17),"white")
    elif key == "autumn":
        p.rect((19,16,20,18),"green_lo")
        p.ellipse((14,19,25,27),"orange_lo")
        p.ellipse((15,19,24,26),"orange_hi")
        p.line([(18,20),(17,22),(18,25)],"orange")
        p.line([(21,20),(22,22),(21,25)],"orange")
        p.rect((19,19,20,25),"orange")
        p.line([(22,16),(24,17)],"green_hi")
    elif key == "birthday":
        p.rect((19,17,20,20),"blue_hi")
        p.poly([(20,14),(21,16),(20,17),(19,16)],"yellow_hi",None)
        p.box((15,21,24,27),"pink")
        p.rect((16,21,23,22),"cream")
        p.rect((16,23,17,24),"cream")
        p.rect((21,23,22,24),"cream")
        p.rect((18,25,18,25),"red_hi")
    elif key == "lottery":
        p.box((14,17,25,26),"cloth")
        p.rect((15,18,24,25),"yellow_hi")
        p.rect((15,21,15,22),"purple")
        p.rect((24,21,24,22),"purple")
        star(p,20,21,"red_hi")
    elif key == "achievement":
        p.poly([(16,16),(19,16),(20,20),(17,22)],"red")
        p.poly([(21,16),(24,16),(23,22),(20,20)],"blue")
        p.ellipse((15,20,24,28),"yellow_lo")
        p.ellipse((16,21,23,27),"yellow_hi")
        p.rect((19,22,20,25),"copper")
        p.rect((18,25,21,25),"copper")
    elif key == "admin":
        p.ellipse((16,16,24,24),"copper")
        p.ellipse((18,18,22,22),"steel_lo")
        p.box((15,21,25,27),"copper")
        p.rect((19,23,21,24),"ink")
        p.rect((20,25,20,25),"ink")
    elif key == "trade":
        p.ellipse((14,19,22,27),"copper_lo")
        p.ellipse((15,20,21,26),"yellow")
        p.ellipse((19,17,27,25),"copper_lo")
        p.ellipse((20,18,26,24),"yellow_hi")
        p.rect((22,19,23,22),"copper")
        p.rect((17,21,17,24),"cream")
    return p.im


def animate_gift(key,base,frame,Pixel):
    p = Pixel()
    p.im = base.copy()
    p.d = ImageDraw.Draw(p.im)
    level = (0,1,2,3,3,2,1,0)[frame]
    pulse = (P["blue"],P["blue_hi"],P["teal_hi"],P["white"])[level]
    if key == "starter":
        p.rect((10,16+frame,10,17+frame),(P["red"],P["red_hi"],P["pink_hi"],P["cream"])[level])
    elif key == "steam":
        p.ellipse((16,17,24,25),"cream")
        needle = ((18,19),(19,18),(20,18),(22,18),(23,20),(22,18),(20,18),(19,18))[frame]
        p.line([(20,21),needle],"red")
        p.rect((3,18+frame%4,3,19+frame%4),"copper_hi")
    elif key == "lv":
        coords=((15,20),(16,20),(17,20),(18,20),(19,20),(19,21),(20,21),(21,21))
        x,y=coords[frame]
        p.rect((x,y,x,y),"white")
    elif key == "mv":
        p.rect((16,17+frame,16,17+frame),"white")
        p.rect((22,25,22,25),pulse)
    elif key == "hv":
        p.rect((24,15+frame,24,16+frame),pulse)
        if frame in (1,2,5,6):
            p.line([(14,17),(13,19),(15,20),(13,22)],"blue_hi")
    elif key == "ev":
        p.line([(20,17),(18,20),(20,24)],(P["pink"],P["pink_hi"],P["cream"],P["white"])[level])
        p.rect((12,18+frame%6,12,18+frame%6),pulse)
    elif key == "iv":
        p.line([(20,17),(17,20),(20,25)],pulse)
        p.rect((24,19+frame%5,24,19+frame%5),"white")
    elif key == "luv":
        coords=((13,23),(15,24),(17,25),(20,25),(23,25),(25,24),(27,22),(25,23))
        x,y=coords[frame]
        p.rect((x,y,x,y),"white")
        p.rect((20,19,20,21),pulse)
    p.im.putalpha(base.getchannel("A"))
    return p.im
