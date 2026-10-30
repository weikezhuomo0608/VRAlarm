# Generate the launcher icon as full-bleed legacy bitmaps with baked rounded corners.
# Run from the repo root:  python tools/make_launcher_icon.py [source.png]
# MagicOS shows adaptive icons flattened on a white plate and does not mask legacy
# bitmaps either, so the rounded look must be baked into the PNG itself. The source
# for this fork (七七闹钟) is a complete square illustration with its own background:
# the whole square becomes the icon, only the corners are cut as transparency.
from PIL import Image, ImageDraw
import sys, os

SRC = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/assets/nana.png'
MASTER = 1024
RADIUS = 0.24         # corner radius as a fraction of the icon, same look as before
RES = 'app/src/main/res'

im = Image.open(SRC).convert('RGB')
w, h = im.size
side = min(w, h)
cx, cy = w // 2, h // 2
crop = im.crop((cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side))
master = crop.resize((MASTER, MASTER), Image.LANCZOS).convert('RGBA')
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
os.makedirs('build', exist_ok=True)
preview.save('build/icon-legacy-preview.jpg', quality=90)
print('preview written build/icon-legacy-preview.jpg')
