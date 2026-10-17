"""sheet.py OUT.png — the detail scenes (r464) at 6 s beside the scene each is a level of, libGDX | Godot | jME, from the
stills the frame checks left in build/godot/<round>/ and build/jme/<round>/ (run both checks on engines/rounds/shaders
and engines/rounds/glint first: a mutate run leaves its mutant's stills there)."""
import sys

from PIL import Image, ImageDraw

ROWS = [('shaders', 's14', 'HIGH: MIRROR as stored'), ('shaders', 's15', 'MEDIUM: through SWELL'),
        ('shaders', 's17', 'MEDIUM, every layer keeps its mirror'),
        ('shaders', 's09', 'HIGH: SWELL'), ('shaders', 's16', 'OFF: the painting'),
        ('shaders', 's03', 'HIGH: WAVE, FOG, the mist'), ('shaders', 's18', 'OFF, the mist shown from MEDIUM only'),
        ('glint', 'g05', 'HIGH: MIRROR + glint, its sun'), ('glint', 'g06', "MEDIUM: SWELL + glint, SWELL's sun"),
        ('glint', 'g07', 'MEDIUM, glint from HIGH only')]
W, H, LABEL = 426, 240, 22
sheet = Image.new('RGB', (W * 3, (H + LABEL) * len(ROWS)), 'black')
draw = ImageDraw.Draw(sheet)
for row, (round_name, scene, title) in enumerate(ROWS):
    y = row * (H + LABEL)
    draw.text((6, y + 5), f'{scene}  {title}   (libGDX | Godot | jME, 6 s)', fill='white')
    for col, path in enumerate([f'build/godot/{round_name}/gdx/{scene}-t6.png', f'build/godot/{round_name}/godot/{scene}-t6.png',
                                f'build/jme/{round_name}/jme/{scene}-t6.png']):
        sheet.paste(Image.open(path).convert('RGB').resize((W, H)), (col * W, y + LABEL))
sheet.save(sys.argv[1])
