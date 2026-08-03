#!/usr/bin/env python3
"""
Generates the Play Store graphics from the same geometry as the app's launcher icon.

Kept in the repo, and generated rather than hand-drawn, for one reason: the store
icon and the launcher icon must not drift apart. Both are now derived from the
coordinates in `app/src/main/res/drawable/ic_launcher_foreground.xml`, so a change to
the brand mark is a change to one set of numbers here.

Everything is drawn at 4x and downsampled, which is the cheapest way to get clean
anti-aliased edges out of Pillow without pulling in a vector renderer.

Usage:  python3 tools/store_graphics.py
Output: play/icon-512.png, play/feature-graphic-1024x500.png
"""

import os
from PIL import Image, ImageDraw, ImageFont

# Brand palette. Mirrors BrandColors in core/designsystem Color.kt.
DEEP = (0x12, 0x12, 0x2A)
SIGNAL = (0xFF, 0xD2, 0x4A)
SHELF = (0xF5, 0xF5, 0xFA)

SS = 4  # supersampling factor

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "play")


# The mark's bounding box within the 108-unit launcher canvas, taken from the
# vector drawable: the shelf spans x 29..79 and the tile-plus-shelf spans y 30..76.
MARK_X0, MARK_X1 = 29, 79
MARK_Y0, MARK_Y1 = 30, 76
MARK_W = MARK_X1 - MARK_X0
MARK_H = MARK_Y1 - MARK_Y0
MARK_CX = (MARK_X0 + MARK_X1) / 2
MARK_CY = (MARK_Y0 + MARK_Y1) / 2


def unit_for_width(available, fraction):
    """Unit size such that the mark occupies `fraction` of `available` width."""
    return available * fraction / MARK_W


def unit_for_height(available, fraction):
    """Unit size such that the mark occupies `fraction` of `available` height."""
    return available * fraction / MARK_H


def draw_mark(draw, cx, cy, unit):
    """
    Draws the brand mark centred on (cx, cy).

    `unit` is one viewport unit of the 108-unit launcher icon canvas, so every
    coordinate below is the same number that appears in the vector drawable.
    """
    def px(x, y):
        return (cx + (x - MARK_CX) * unit, cy + (y - MARK_CY) * unit)

    def box(x0, y0, x1, y1, radius, fill):
        draw.rounded_rectangle([px(x0, y0), px(x1, y1)], radius=radius * unit, fill=fill)

    # The screenshot tile.
    box(36, 30, 76, 66, 5, SIGNAL)

    # Three suggested lines of text, decreasing in length.
    for y0, x1 in ((39, 63), (47, 69), (55, 57)):
        box(43, y0, x1, y0 + 3.5, 1.2, DEEP)

    # The shelf the tile rests on.
    box(29, 70, 79, 76, 3, SHELF)


def load_font(size, bold=False):
    """
    Loads a scalable face at `size`.

    Noto Sans first, because it is Google's own family and so sits naturally next to
    Play Store chrome. The fallback passes `size` to `load_default`, which matters:
    the no-argument form returns a fixed-size bitmap font that silently ignores the
    requested size, which renders a feature graphic with unreadable 10px text.
    """
    candidates = [
        "/usr/share/fonts/google-noto/NotoSans-Bold.ttf" if bold
        else "/usr/share/fonts/google-noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold
        else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)

    return ImageFont.load_default(size=size)


def make_icon(path, size=512):
    """
    The 512x512 store icon.

    Deliberately full-bleed with no rounded corners of its own: Play applies its own
    mask, and baking corners in produces a visible double-rounding artefact.
    """
    canvas = size * SS
    image = Image.new("RGB", (canvas, canvas), DEEP)
    draw = ImageDraw.Draw(image)

    # 62% of the width. Large enough to read as a distinct shape in a crowded search
    # results row, with enough margin left that Play's circular mask cannot clip the
    # shelf's ends — which are the widest part of the mark.
    draw_mark(draw, canvas / 2, canvas / 2, unit_for_width(canvas, 0.62))

    image.resize((size, size), Image.LANCZOS).save(path, "PNG")
    return path


def make_feature_graphic(path, width=1024, height=500):
    """
    The 1024x500 feature graphic.

    Text is kept off the horizontal centre and away from all edges, because Play
    crops this differently across surfaces and anything near a boundary gets eaten.
    """
    cw, ch = width * SS, height * SS
    image = Image.new("RGB", (cw, ch), DEEP)
    draw = ImageDraw.Draw(image)

    # Mark on the left, wordmark and strapline to its right.
    draw_mark(draw, cw * 0.185, ch * 0.5, unit_for_height(ch, 0.46))

    title_font = load_font(int(92 * SS), bold=True)
    sub_font = load_font(int(37 * SS), bold=False)

    x = cw * 0.335
    draw.text((x, ch * 0.40), "Shelfie", font=title_font, fill=SHELF, anchor="ls")
    draw.text(
        (x, ch * 0.545),
        "Find any screenshot by what's in it",
        font=sub_font,
        fill=SIGNAL,
        anchor="ls",
    )
    draw.text(
        (x, ch * 0.655),
        "Works offline  ·  One payment",
        font=sub_font,
        fill=SHELF,
        anchor="ls",
    )

    image.resize((width, height), Image.LANCZOS).save(path, "PNG")
    return path


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    print("wrote", make_icon(os.path.join(OUT_DIR, "icon-512.png")))
    print("wrote", make_feature_graphic(os.path.join(OUT_DIR, "feature-graphic-1024x500.png")))
