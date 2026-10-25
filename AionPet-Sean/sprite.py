"""
桌宠精灵条规格化工具

用法：
    python3 sprite.py <输入图> <动作名> [期望帧数]

做的事：
1. 剥掉烤进图里的棋盘/纯色背景，还原 alpha
2. 扫列找真实角色块切帧（不等分，避免切错）
3. 全局统一缩放 + 底部对齐 + 水平居中
4. 输出 192x208 单帧 + 横向精灵条

为什么要统一缩放而不是逐帧拉到同高：
生图模型每帧重画，身体尺寸会漂。但有些帧变高是头发扬起、
跳跃离地这类真实动作。逐帧归一会把身体压小，看起来像在缩放。
所以取中位高度算一个共同比例，全帧套用，身体锁死，动作保留。
"""
from PIL import Image
import os
import sys

CELL_W, CELL_H = 192, 208
BODY_RATIO = 0.90
TOL = 14

# 这些动作允许腾空（脚可以离地），其余一律踩地。
# 站立类动作若保留腾空，循环播放会像在原地蹦。
AIRBORNE = {"jumping", "happy"}
LIFT_MIN = 6          # 小于这个值视为噪声，直接踩地


def sample_bg(px, W, H):
    """取四角采样，判断背景类型。棋盘返回两个色值，纯色返回一个。"""
    corners = [px[2, 2], px[W - 3, 2], px[2, H - 3], px[W - 3, H - 3]]
    grays = []
    for c in corners:
        r, g, b = c[:3]
        if abs(r - g) <= 8 and abs(g - b) <= 8:
            grays.append((r + g + b) / 3)
    if not grays:
        return []
    # 棋盘常见组合：亮格 ~250-255，暗格 ~225-235
    vals = sorted(set(round(v) for v in grays))
    out = [max(vals)]
    for v in vals:
        if abs(v - out[0]) > 8:
            out.append(v)
    return out


def make_is_bg(px, bg_vals):
    def is_bg(x, y):
        c = px[x, y]
        r, g, b = c[0], c[1], c[2]
        if len(c) > 3 and c[3] == 0:
            return True
        if abs(r - g) <= 7 and abs(g - b) <= 7:
            v = (r + g + b) / 3
            return any(abs(v - bv) <= TOL for bv in bg_vals)
        return False
    return is_bg


def find_blocks(W, H, is_bg, min_w=20):
    cols = []
    for x in range(W):
        cols.append(any(not is_bg(x, y) for y in range(0, H, 2)))
    blocks, s = [], None
    for x in range(W):
        if cols[x] and s is None:
            s = x
        elif not cols[x] and s is not None:
            if x - s > min_w:
                blocks.append((s, x))
            s = None
    if s is not None:
        blocks.append((s, W))
    return blocks


def run(src_path, name, expect=None):
    out_dir = f"/workspace/pet/out/{name}"
    os.makedirs(out_dir, exist_ok=True)

    src = Image.open(src_path)
    has_alpha = src.mode in ("RGBA", "LA")
    im = src.convert("RGBA") if has_alpha else src.convert("RGB")
    W, H = im.size
    px = im.load()

    bg_vals = sample_bg(px, W, H)
    print(f"source {W}x{H} mode={src.mode} bg={bg_vals}")
    is_bg = make_is_bg(px, bg_vals)

    rgba = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    rp = rgba.load()
    for y in range(H):
        for x in range(W):
            if not is_bg(x, y):
                c = px[x, y]
                rp[x, y] = (c[0], c[1], c[2], 255)

    blocks = find_blocks(W, H, is_bg)
    print(f"blocks found: {len(blocks)}")
    if expect and len(blocks) != expect:
        print(f"  !! 期望 {expect} 帧，实测 {len(blocks)} 帧，请检查")

    cells = []
    for i, (x0, x1) in enumerate(blocks):
        sub = rgba.crop((x0, 0, x1, H))
        bb = sub.getbbox()
        if bb is None:
            continue
        # bb[3] 是这一帧在原图里的脚底位置，离地帧会偏高
        cells.append((sub.crop(bb), bb[3]))
        print(f"  frame {i}: {bb[2]-bb[0]}x{bb[3]-bb[1]} bottom={bb[3]}")

    if not cells:
        print("没找到角色，放弃")
        return

    hs = sorted(c.size[1] for c, _ in cells)
    ref_h = hs[len(hs) // 2]
    scale = (CELL_H * BODY_RATIO) / ref_h

    # 地面基线 = 所有帧里最低的脚底。离地的帧保留真实腾空高度，
    # 否则跳跃动作会被压平贴地。
    baseline = max(b for _, b in cells)
    print(f"ref_h={ref_h} scale={scale:.4f} baseline={baseline}")

    strip = Image.new("RGBA", (CELL_W * len(cells), CELL_H), (0, 0, 0, 0))
    for i, (sub, bottom) in enumerate(cells):
        w, h = sub.size
        nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
        if nh > CELL_H:
            k = CELL_H / nh
            nw, nh = max(1, round(nw * k)), CELL_H
        if nw > CELL_W:
            k = CELL_W / nw
            nw, nh = CELL_W, max(1, round(nh * k))
        sub2 = sub.resize((nw, nh), Image.LANCZOS)

        lift = 0
        if name in AIRBORNE:
            raw = round((baseline - bottom) * scale)
            if raw >= LIFT_MIN:
                lift = raw
                print(f"    frame {i} 腾空 {lift}px")
        ox = (CELL_W - nw) // 2
        oy = max(0, CELL_H - nh - lift)
        strip.alpha_composite(sub2, (i * CELL_W + ox, oy))

        cell = Image.new("RGBA", (CELL_W, CELL_H), (0, 0, 0, 0))
        cell.alpha_composite(sub2, (ox, oy))
        cell.save(f"{out_dir}/{name}_{i:02d}.png")

    strip_path = f"/workspace/pet/out/{name}.png"
    strip.save(strip_path)
    print(f"-> {strip_path} {strip.size}")

    # QA：脚的横向中心
    sp = strip.load()
    centers = []
    for i in range(len(cells)):
        xs = [x - i * CELL_W for y in range(CELL_H - 20, CELL_H)
              for x in range(i * CELL_W, (i + 1) * CELL_W) if sp[x, y][3] > 40]
        if xs:
            centers.append((min(xs) + max(xs)) // 2)
    print(f"foot centers: {centers} (格子中心 {CELL_W // 2})")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    expect = int(sys.argv[3]) if len(sys.argv) > 3 else None
    run(sys.argv[1], sys.argv[2], expect)
