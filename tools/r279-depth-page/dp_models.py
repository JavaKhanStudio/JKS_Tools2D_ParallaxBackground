"""depth_page's GPU side (r533: split out of depth_page.py), run only in the child depth_page.in_child starts
(r396): Depth Anything V2 Small's depth, SAM 2.1 small's automatic masks, and SAM prompted across a slab cut for the
field it runs through. torch and transformers are imported inside each step: the parent never loads them."""
import numpy as np
from dp_common import runs_of
from PIL import Image
from scipy import ndimage

MODEL = "depth-anything/Depth-Anything-V2-Small-hf"


SAM = "facebook/sam2.1-hiera-small"


def _depth_of(img):
    """Relative disparity per pixel (larger = nearer), estimated on the painting padded along X with its own other
    end: the network sees across the join, and the depth loops as the painting does."""
    import torch
    from transformers import pipeline
    _, w, _ = img.shape
    pad = w // 4
    wrap = np.concatenate([img[:, -pad:], img, img[:, :pad]], axis=1)
    est = pipeline("depth-estimation", model=MODEL, device=0 if torch.cuda.is_available() else -1)
    d = est(Image.fromarray(wrap))["predicted_depth"]
    d = torch.nn.functional.interpolate(d[None, None].float(), size=wrap.shape[:2], mode="bilinear")[0, 0]
    out = d.cpu().numpy()[:, pad:pad + w]
    # The two ends are w apart in what the network saw, and it judged them a little differently: on gently sloping
    # ground that moves a band's edge by rows across the join. Each row's last 96 columns are bent up and its first
    # 96 down, each by half its ends' difference (smoothed over 2 rows), so they meet. Bent across the whole width, a
    # row whose ends differ much (a tree at one end) draws a streak through the painting.
    step = ndimage.gaussian_filter1d(out[:, -1] - out[:, 0], 2)
    x = np.arange(w)
    edge = min(96, w // 4)
    left = np.clip(1 - x / edge, 0, 1)
    right = np.clip(1 - (w - 1 - x) / edge, 0, 1)
    out = out + step[:, None] * (left / 2)[None] - step[:, None] * (right / 2)[None]
    del est
    torch.cuda.empty_cache()  # dreamshaper needs the memory next
    return out


def _masks_of(img):
    """SAM 2.1 small's automatic masks, one per thing it outlines (a pine, a hill, a cloud), each whole across the join:
    run on the painting, then on it rolled by half its width, whose masks over the middle are the ones the join cut."""
    import torch
    from transformers import pipeline
    w = img.shape[1]
    sam = pipeline("mask-generation", model=SAM, device=0 if torch.cuda.is_available() else -1)

    def run(a):
        out = sam(Image.fromarray(a), points_per_batch=64, points_per_crop=64, pred_iou_thresh=0.7,
                  stability_score_thresh=0.8)
        return [np.asarray(m, bool) for m in out["masks"]]
    keep = [m for m in run(img) if not (m[:, 0].any() or m[:, -1].any())]
    keep += [np.roll(m, -(w // 2), axis=1) for m in run(np.roll(img, w // 2, axis=1))
             if m[:, w // 2 - 1].any() or m[:, w // 2].any()]
    sam.model.to("cpu")  # dreamshaper needs the memory next
    torch.cuda.empty_cache()
    return keep


def slab_cuts(L, img, k, reach=24, apart=25):
    """Per column, the row where band k + 1 starts under band k through one surface (the same colour 2-10 px above and
    below, nothing but those two bands within reach rows): a field down a slope that depth cut in two. -1 elsewhere: a
    cut along a hedge, a tree's foot, a crown against the sky."""
    h, w = L.shape
    rgb = img.astype(np.float32)
    out = np.full(w, -1)
    for x in range(w):
        xs = [(x + d) % w for d in range(-3, 4)]
        for y in np.nonzero((L[:-1, x] == k) & (L[1:, x] == k + 1))[0] + 1:
            if not reach <= y < h - reach or not np.isin(L[y - reach:y + reach, x], (k, k + 1)).all():
                continue
            above, below = rgb[y - 10:y - 2][:, xs].reshape(-1, 3), rgb[y + 2:y + 10][:, xs].reshape(-1, 3)
            if np.linalg.norm(above.mean(0) - below.mean(0)) < apart:
                out[x] = y
                break
    return out


def _fields_of(img, L, n, every=96, least=24, off=16, sure=0.4, most=0.25, stray=0.05):
    """SAM 2.1 small prompted with two points across a slab cut (slab_cuts), off px above and below it, every every px
    along a run of least columns or more: the field the cut runs through, whole, to its painted edges (a hedge, a
    shadow's edge, the river bank), which the automatic masks never give (r374). Of its three masks, the best scored of
    those that hold both points, scored sure or more, over at most most of the painting, with under stray of it outside
    the two bands; only its piece holding both points (SAM adds a ragged patch of a lit field across a trunk, valley,
    which moved left a blocky patch of the near layer in the middle of the grass). A field that reaches one end of the
    painting and not the other is asked again with a third point just across the join, on the row it reaches the end
    at: SAM stops a field at the join for one prompt and not for another, and the two halves would go to two bands
    (river's lit field). One mask per outline."""
    import torch
    from transformers import Sam2Model, Sam2Processor
    w = L.shape[1]
    prompts = field_prompts(img, L, n, every, least, off)
    if not prompts:
        return []
    dev = "cuda" if torch.cuda.is_available() else "cpu"
    proc = Sam2Processor.from_pretrained(SAM)
    model = Sam2Model.from_pretrained(SAM).to(dev)

    def ask(k, points, shift):
        """The best-scored fitting mask for points on the painting rolled by shift, or None."""
        inp = proc(images=Image.fromarray(np.roll(img, shift, axis=1)),
                   input_points=[[[[(x + shift) % w, y] for x, y in points]]], input_labels=[[[1] * len(points)]],
                   return_tensors="pt").to(dev)
        with torch.no_grad():
            got = model(**inp, multimask_output=True)
        masks = proc.post_process_masks(got.pred_masks.cpu(), inp["original_sizes"])[0][0].numpy() > 0
        best, top = None, sure
        for m, score in zip(masks, got.iou_scores[0, 0].cpu().numpy()):
            m = piece_at(np.roll(m, -shift, axis=1), points)
            if (score >= top and m.any() and 0.005 < m.mean() <= most
                    and (~np.isin(L[m], (k, k + 1))).mean() < stray):
                best, top = m, score
        return best

    out = []
    for k, points in prompts:
        m = ask(k, points, w // 2 - points[0][0])
        if m is not None and m[:, 0].any() != m[:, -1].any():
            end = 0 if m[:, 0].any() else w - 1
            across = (w - 9 if end == 0 else 8, int(np.median(np.nonzero(m[:, end])[0])))
            whole = ask(k, points + [across], w // 2)
            m = whole if whole is not None else m
        if m is not None and not any((m & o).sum() > 0.8 * (m | o).sum() for o in out):
            out.append(m)
    model.to("cpu")  # dreamshaper needs the memory next
    torch.cuda.empty_cache()
    return out


def field_prompts(img, L, n, every, least, off):
    """_fields_of's prompts: (k, two points off px above and below the slab cut between bands k and k + 1), every
    every px along each run of least columns or more the cut holds."""
    w = L.shape[1]
    prompts = []
    for k in range(n - 1):
        cut = slab_cuts(L, img, k)
        for c, run in runs_of(cut < 0):
            if run < least:
                continue
            count = max(1, round(run / every))
            for i in range(count):
                x = (c + int((i + 0.5) * run / count)) % w
                prompts.append((k, [(x, int(cut[x] - off)), (x, int(cut[x] + off))]))
    return prompts


def piece_at(m, points):
    """The piece of mask m holding every (x, y) of points, its gaps under 2 px closed and its holes filled (SAM's
    prompted masks are hatched: moved as they come, the farther band shows through in rows of pinholes), on the
    painting laid three times side by side, each point in any copy and the piece's three copies laid back on one, so
    a piece across the join is whole; empty when they lie in two pieces."""
    w = m.shape[1]
    lab, _ = ndimage.label(ndimage.binary_closing(np.tile(m, 3), iterations=2))
    i = lab[points[0][1], w + points[0][0]]
    if not i or any(i not in lab[y, [x, w + x, 2 * w + x]] for x, y in points):
        return np.zeros_like(m)
    piece = ndimage.binary_fill_holes(lab == i)
    return piece[:, :w] | piece[:, w:2 * w] | piece[:, 2 * w:]
