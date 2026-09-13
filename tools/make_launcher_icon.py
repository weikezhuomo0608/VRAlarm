# Generate the launcher icon as full-bleed legacy bitmaps with baked rounded corners.
# Run from the repo root:  python tools/make_launcher_icon.py [source.png]
# MagicOS shows adaptive icons flattened on a white plate and does not mask legacy
# bitmaps either, so the rounded look must be baked into the PNG itself: compose the
# gradient + mascot exactly like the approved adaptive preview (character at 72% of a
# 432 canvas), crop to the 66% window the mask would have shown, scale up, and cut
# rounded corners with transparency.
from PIL import Image, ImageFilter, ImageDraw
import sys, os

SRC = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/assets/hazel.png'
CANVAS = 432          # 108dp at 4x, same as the approved adaptive preview
FILL = 0.72           # character square as a fraction of the canvas
WINDOW = 0.66         # what the adaptive mask used to leave visible
MASTER = 1024
RADIUS = 0.24         # corner radius as a fraction of the icon
RES = 'app/src/main/res'

im = Image.open(SRC).convert('RGBA')
w, h = im.size
alpha = im.getchannel('A')

if alpha.getextrema()[0] < 250:
    minx, miny, maxx, maxy = alpha.getbbox()
    print('source has alpha; bbox', (minx, miny, maxx, maxy))
else:
    rgb = im.convert('RGB'); px = rgb.load()
    def is_bg(p):
        r, g, b = p[:3]
        return r > 236 and g > 236 and b > 236
    minx, miny, maxx, maxy = w, h, 0, 0
    for y in range(0, h, 4):
        for x in range(0, w, 4):
            if not is_bg(px[x, y]):
                minx = min(minx, x); maxx = max(maxx, x)
                miny = min(miny, y); maxy = max(maxy, y)
    print('white-bg source; character bbox', (minx, miny, maxx, maxy))

    def flood_rgba(region):
        work = region
        wp = work.load(); ww, wh = work.size
        seen = bytearray(ww * wh)
        stack = [(x, 0) for x in range(ww)] + [(x, wh - 1) for x in range(ww)]
        stack += [(0, y) for y in range(wh)] + [(ww - 1, y) for y in range(wh)]
        while stack:
            x, y = stack.pop()
            if x < 0 or y < 0 or x >= ww or y >= wh: continue
            i = y * ww + x
            if seen[i] or not is_bg(wp[x, y]): continue
            seen[i] = 1
            stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
        mask = Image.new('L', work.size, 0)
        mp = mask.load()
        for y in range(wh):
            base = y * ww
            for x in range(ww):
                if seen[base + x]: mp[x, y] = 255
        mask = mask.point(lambda v: 255 - v).filter(ImageFilter.GaussianBlur(1))
        out = work.convert('RGBA'); out.putalpha(mask); return out

ch_h = maxy - miny
# The compose window trims ~4% off each end of the art, so the crop reaches from
# generous headroom down to near the ankles: what the window leaves visible is
# "head with margin ... part of the legs", matching the original icon.
top = max(0, miny - int(0.06 * ch_h))
bottom = miny + int(0.99 * ch_h)
side = max(bottom - top, int((maxx - minx) * 1.12))
cx = (minx + maxx) // 2
left = max(0, min(cx - side // 2, w - side))
crop = im.crop((left, top, left + side, top + side))
cutout = crop if alpha.getextrema()[0] < 250 else flood_rgba(crop)

# The approved composition: gradient across the canvas, mascot centred at FILL.
composite = Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0))
cp = composite.load()
for y in range(CANVAS):
    t = y / (CANVAS - 1)
    color = (int(0xf8 + (0xe2 - 0xf8) * t), int(0xf8 + (0xe5 - 0xf8) * t), int(0xf6 + (0xe8 - 0xf6) * t))
    for x in range(CANVAS):
        cp[x, y] = color
target = int(CANVAS * FILL)
art = cutout.resize((target, target), Image.LANCZOS)
composite.alpha_composite(art, ((CANVAS - target) // 2, (CANVAS - target) // 2))

# The window the adaptive mask would have shown becomes the whole icon; rounded
# corners are baked in as transparency because no launcher on the target phone masks.
edge = int(CANVAS * (1 - WINDOW) / 2)
master = composite.crop((edge, edge, CANVAS - edge, CANVAS - edge)).resize((MASTER, MASTER), Image.LANCZOS)
rm = Image.new('L', (MASTER, MASTER), 0)
ImageDraw.Draw(rm).rounded_rectangle((0, 0, MASTER - 1, MASTER - 1), radius=int(MASTER * RADIUS), fill=255)
master.putalpha(rm)

DENSITIES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
for name, px in DENSITIES.items():
    d = os.path.join(RES, 'mipmap-' + name)
    os.makedirs(d, exist_ok=True)
    master.resize((px, px), Image.LANCZOS).save(os.path.join(d, 'ic_launcher.png'))
    print('wrote', d, px)

preview = Image.new('RGB', (MASTER, MASTER), (40, 40, 60))
preview.paste(master, (0, 0), master)
preview.save('build/icon-legacy-preview.jpg', quality=90)
print('preview written build/icon-legacy-preview.jpg')
