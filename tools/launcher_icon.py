"""Regenerates the launcher icon layers from the app logo (art/launcher-logo.jpg).

    python tools/launcher_icon.py

Reads art/launcher-logo.jpg, keys the "T + arrow" glyph out of the navy
background, and writes app/src/main/res/mipmap-{mdpi..xxxhdpi}/ic_launcher_foreground.png (the
glyph, 50 dp tall, centred in the adaptive icon's 66 dp safe zone) and ic_launcher_monochrome.png
(the same alpha as a white silhouette, for Android 13+ themed icons), plus
app/src/main/res/drawable-{mdpi..xxxhdpi}/ic_brand_glyph.png — the glyph alone, tightly cropped,
24 dp tall, for the in-app brand header. The background colour is
`@color/launcher_background` in res/values/colors.xml — update it by hand if the logo changes
(the script prints the sampled value). Needs Pillow and numpy.
"""
from __future__ import annotations

import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
ICON_DP = 108
GLYPH_HEIGHT_DP = 50.0   # the source has the glyph at ~72 % of its square; the visible icon is ~72 dp
OFFSET_X_DP = 1.5        # the glyph sits slightly right of centre in the source (the arrow tip)
BRAND_GLYPH_DP = 24.0    # height of the app-bar brand glyph
KEY_SOFT_FROM = 25.0     # RGB distance from the background where alpha starts rising…
KEY_SOFT_RANGE = 50.0    # …and the range over which it reaches 1 (anti-aliased edges survive)


def key_glyph(path: str) -> tuple[Image.Image, tuple[int, int, int]]:
    rgb = np.asarray(Image.open(path).convert("RGB")).astype(np.float32)
    h, w, _ = rgb.shape
    # The source is a rounded square on a darker field; sample the flat navy inside it.
    inner = rgb[int(h * 0.21):int(h * 0.78), int(w * 0.17):int(w * 0.24)].reshape(-1, 3)
    bg = np.median(inner, axis=0)
    dist = np.sqrt(((rgb - bg) ** 2).sum(axis=2))
    region = np.zeros((h, w), bool)
    region[int(h * 0.16):int(h * 0.84), int(w * 0.16):int(w * 0.84)] = True
    alpha = np.clip((dist - KEY_SOFT_FROM) / KEY_SOFT_RANGE, 0, 1) * region
    ys, xs = np.where(alpha > 0.5)
    a = alpha[..., None]
    # Un-premultiply against the background so soft edges keep the glyph's own colour.
    colour = np.clip(np.where(a > 0.02, bg + (rgb - bg) / np.maximum(a, 0.02), rgb), 0, 255)
    rgba = np.dstack([colour, alpha * 255]).astype(np.uint8)
    glyph = Image.fromarray(rgba, "RGBA").crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    return glyph, tuple(int(round(v)) for v in bg)


def main() -> None:
    source = os.path.join(ROOT, "art", "launcher-logo.jpg")
    if not os.path.isfile(source):
        raise SystemExit("missing art/launcher-logo.jpg")
    glyph, bg = key_glyph(source)
    gw, gh = glyph.size
    print(f"source {os.path.basename(source)}: glyph {gw}x{gh}, background #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}")
    for name, scale in DENSITIES.items():
        size = int(round(ICON_DP * scale))
        h = int(round(GLYPH_HEIGHT_DP * scale))
        w = int(round(gw * h / gh))
        scaled = glyph.resize((w, h), Image.LANCZOS)
        x = int(round((size - w) / 2 + OFFSET_X_DP * scale))
        y = int(round((size - h) / 2))
        out_dir = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(out_dir, exist_ok=True)

        foreground = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        foreground.paste(scaled, (x, y), scaled)
        foreground.save(os.path.join(out_dir, "ic_launcher_foreground.png"), optimize=True)

        silhouette = Image.new("RGBA", (w, h), (255, 255, 255, 255))
        silhouette.putalpha(scaled.getchannel("A"))
        monochrome = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        monochrome.paste(silhouette, (x, y), silhouette)
        monochrome.save(os.path.join(out_dir, "ic_launcher_monochrome.png"), optimize=True)

        # In-app brand glyph: no canvas, just the glyph at BRAND_GLYPH_DP tall.
        bh = int(round(BRAND_GLYPH_DP * scale))
        bw = int(round(gw * bh / gh))
        brand_dir = os.path.join(RES, f"drawable-{name}")
        os.makedirs(brand_dir, exist_ok=True)
        glyph.resize((bw, bh), Image.LANCZOS).save(os.path.join(brand_dir, "ic_brand_glyph.png"), optimize=True)
        print(f"  {name}: {size}px, glyph {w}x{h} at ({x},{y}); brand glyph {bw}x{bh}")


if __name__ == "__main__":
    main()
