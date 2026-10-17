"""The pictures zoom.py compares (r452, r486): a panel cut out of a canvas's screenshot, and how far two are apart.
margins.py replays the same on what ZOOM_DUMP kept."""
import numpy as np
from PIL import Image

# A canvas its page reduces (a phone's, transfert's on a desktop) is compared within REACH pixels of each other; and a
# zoom is drawn anew at twice the pixels (a painted tree's speckles are not the same), so there a zoom fails only
# CLEARLY closer to another panel where the two differ: right ones are at least 0.94 as far from the others as from
# their own (fog's B on a phone, a slider's value from A and C), a wrong one in each lab 0.40 at most (anim's) (r486,
# margins.py). One shown 1:1 is compared pixel for pixel.
PHONE_REACH, PHONE_CLEARLY = 1, 0.7


def zoom_box(zw, zh, pw, ph):
    """Where a pw x ph panel is drawn zoomed on a zw x zh canvas: its shape, scaled about the middle (fractions)."""
    k = min(zw / pw, zh / ph)
    return ((zw - pw * k) / 2 / zw, (zh - ph * k) / 2 / zh, (zw + pw * k) / 2 / zw, (zh + ph * k) / 2 / zh)


def thumb(image, box, size=(48, 24)):
    """The part of image in box (fractions of its size: left, top, right, bottom), size wide and tall, for a likeness."""
    h, w = image.shape[:2]
    # Cut at a fraction of a pixel: a phone's panels fall between its pixels, each by another fraction, and cut to whole
    # ones they lie up to a row apart, a difference along every edge (r486).
    part = (box[0] * w, box[1] * h, max(box[0] * w + 1, box[2] * w), max(box[1] * h + 1, box[3] * h))
    return np.asarray(Image.fromarray(image.astype(np.uint8)).resize(size, Image.BOX, box=part), dtype=float)


def inset(box, pw, ph, d=2):
    """box (fractions) less d of the panel's pw x ph pixels at each side: a phone's canvas is scaled to its page, and the
    panels' edges, cut where they fall, take a column or a row of the black between them (r486)."""
    dx, dy = (box[2] - box[0]) * d / pw, (box[3] - box[1]) * d / ph
    return (box[0] + dx, box[1] + dy, box[2] - dx, box[3] - dy)


def near(a, b, r):
    """Each pixel's least difference from b's pixels within r of it, a channel's mean: a phone's panels are reduced
    by its page each at another fraction of a pixel, and a sharp edge cut a fraction apart is no other picture (r486)."""
    padded = np.pad(b, ((r, r), (r, r), (0, 0)), mode="edge")
    h, w = a.shape[:2]
    return np.min([np.abs(a - padded[y:y + h, x:x + w]).mean(axis=2) for y in range(2 * r + 1) for x in range(2 * r + 1)], axis=0)


def where_apart(a, b):
    """The pixels where pictures a and b differ by 8/255 a channel or more; None when fewer than 40 do (panels alike)."""
    mask = np.abs(a - b).mean(axis=2) >= 8
    return mask if mask.sum() >= 40 else None
