# -*- coding: utf-8 -*-
from PIL import Image
import os
from collections import deque

DL = r"C:/Users/29322/Downloads"
DST = r"C:/Users/29322/.zcode/workspace/default/AionsHome/AionApp/app/src/main/res/drawable-nodpi"
os.makedirs(DST, exist_ok=True)

def components(im, n_expect, thresh=32, scale=4):
    """缩样连通域：返回按像素数排序的 (count, x0,y0,x1,y1)（映射回原图坐标，含 8px 余量）。"""
    w, h = im.size
    sw, sh = max(1, w // scale), max(1, h // scale)
    small = im.resize((sw, sh))
    p = small.load()
    mask = [[1 if p[x, y][3] > thresh else 0 for y in range(sh)] for x in range(sw)]
    labels = [[0] * sh for _ in range(sw)]
    comps = []
    for sx in range(sw):
        for sy in range(sh):
            if mask[sx][sy] and labels[sx][sy] == 0:
                cid = len(comps) + 1
                q = deque([(sx, sy)])
                labels[sx][sy] = cid
                minx = maxx = sx; miny = maxy = sy; cnt = 0
                while q:
                    cx, cy = q.popleft(); cnt += 1
                    if cx < minx: minx = cx
                    if cx > maxx: maxx = cx
                    if cy < miny: miny = cy
                    if cy > maxy: maxy = cy
                    for dx, dy in ((1,0),(-1,0),(0,1),(0,-1),(1,1),(-1,-1),(1,-1),(-1,1)):
                        nx, ny = cx + dx, cy + dy
                        if 0 <= nx < sw and 0 <= ny < sh and mask[nx][ny] and labels[nx][ny] == 0:
                            labels[nx][ny] = cid
                            q.append((nx, ny))
                pad = 3
                comps.append((
                    cnt,
                    max(0, (minx - pad) * scale), max(0, (miny - pad) * scale),
                    min(w, (maxx + 1 + pad) * scale), min(h, (maxy + 1 + pad) * scale),
                ))
    comps.sort(key=lambda c: -c[0])
    return comps[:n_expect]

def cut(im, box, target_max):
    part = im.crop(box)
    bb = part.getchannel("A").getbbox()
    part = part.crop(bb) if bb else part
    m = max(part.size)
    if m > target_max:
        sc = target_max / m
        part = part.resize((round(part.size[0] * sc), round(part.size[1] * sc)), Image.LANCZOS)
    return part

def avg_lum(img):
    small = img.resize((12, 12))
    p = small.load()
    tot = n = 0
    for x in range(12):
        for y in range(12):
            q = p[x, y]
            if q[3] > 128:
                tot += q[0] + q[1] + q[2]; n += 1
    return tot / n if n else 9999

# ── 1) 双猫 → 两只 ──
im = Image.open(os.path.join(DL, "file_000000007d3c81f5b9435d49ab88ed01.png")).convert("RGBA")
comps = components(im, 2)
print("双猫连通域:", [(c[0], c[1:]) for c in comps])
parts = [cut(im, c[1:], 340) for c in comps]
if len(parts) == 2:
    a, b = parts
    black, white = (a, b) if avg_lum(a) < avg_lum(b) else (b, a)
    black.save(os.path.join(DST, "plush_black.png"), optimize=True)
    white.save(os.path.join(DST, "plush_white.png"), optimize=True)
    print("plush_black", black.size, "plush_white", white.size)
else:
    raise SystemExit("双猫分割数量不对: %d" % len(parts))

# ── 2) 3×3 拼豆猫 → 9 只（连通域，不要网格切） ──
im3 = Image.open(os.path.join(DL, "file_00000000811081f5b84876ef3414d644.png")).convert("RGBA")
comps3 = components(im3, 9)
print("拼豆连通域数:", len(comps3), "面积:", [c[0] for c in comps3])
for i, c in enumerate(comps3):
    part = cut(im3, c[1:], 150)
    part.save(os.path.join(DST, f"deco_cat_{i}.png"), optimize=True)
    print(f"deco_cat_{i}", part.size, "area", c[0])
print("ASSETS DONE")
