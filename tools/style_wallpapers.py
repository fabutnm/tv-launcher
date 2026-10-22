"""Draws the wallpapers of the style themes in tcl-c845/theme-library and tcl-p725/theme-library: abstract 1920x1080
pictures in the spirit of popular TV and desktop looks (soft dunes, a bloom, a mesh gradient, neon light, an aurora),
from code with NumPy and Pillow, so they are this repo's own work. Every picture keeps its lower third darker,
where the dock, the grid and the text sit.

    python tools/style_wallpapers.py <output folder>     # writes dunes.jpg, bloom.jpg, material.jpg, neon.jpg, ...

Fixed seeds: the same code draws the same pictures (up to the JPEG encoder).
"""
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

W, H = 1920, 1080
Y, X = np.mgrid[0:H, 0:W].astype(np.float32)
U, V = X / W, Y / H


def rgb(hex_colour):
    h = hex_colour.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255.0


def vertical(stops):
    """A top-to-bottom gradient from (offset, colour) stops."""
    img = np.zeros((H, W, 3), np.float32)
    offs = [s[0] for s in stops]
    for c in range(3):
        img[..., c] = np.interp(V, offs, [rgb(s[1])[c] for s in stops])
    return img


def glow(img, cx, cy, radius, colour, strength=1.0, aspect=1.0):
    """Adds a soft round light (screen blend)."""
    d2 = ((U - cx) * aspect) ** 2 + (V - cy) ** 2
    a = np.exp(-d2 / (2 * radius ** 2))[..., None] * strength
    return 1 - (1 - img) * (1 - a * rgb(colour))


def layer(img, mask, colour, alpha=1.0):
    m = np.clip(mask, 0, 1)[..., None] * alpha
    return img * (1 - m) + rgb(colour) * m


def shade_bottom(img, start=0.55, amount=0.55):
    """Darkens the lower part, where the dock and the grid are."""
    t = np.clip((V - start) / (1 - start), 0, 1)[..., None]
    return img * (1 - amount * t ** 1.4)


def finish(img, path, blur=0, grain=0.012, seed=1):
    rng = np.random.default_rng(seed)
    img = np.clip(img + rng.normal(0, grain, img.shape).astype(np.float32), 0, 1)
    pic = Image.fromarray((img * 255).astype(np.uint8))
    if blur:
        pic = pic.filter(ImageFilter.GaussianBlur(blur))
    pic.save(path, quality=86, optimize=True, progressive=True)


def wave(base, amp, freq, phase, tilt=0.0):
    return base + amp * np.sin(U * freq * 2 * np.pi + phase) + tilt * U


def dunes(path):
    """Warm desert dusk with layered soft dunes (macOS-like)."""
    img = vertical([(0, '#2a1640'), (0.35, '#8a3a5c'), (0.55, '#e0794f'), (1, '#3a1830')])
    img = glow(img, 0.72, 0.42, 0.18, '#ffd29a', 0.9, 1.6)
    cols = ['#b4505a', '#8c3a52', '#6a2a4a', '#4a1e3c', '#2c1228']
    for i, c in enumerate(cols):
        edge = wave(0.50 + i * 0.09, 0.035 - i * 0.004, 0.8 + i * 0.35, i * 1.7, 0.03 * (-1) ** i)
        img = layer(img, (V - edge) * 60, c)
    finish(shade_bottom(img, 0.6, 0.35), path, blur=1.2, seed=2)


def bloom(path):
    """A blue flower of translucent petals on near-black (Windows-like)."""
    img = vertical([(0, '#050914'), (1, '#02040a')])
    cx, cy = 0.5, 0.48
    ang = np.arctan2((V - cy) * H, (U - cx) * W)
    r = np.hypot((U - cx) * W, (V - cy) * H) / H
    for k, (rot, col, size) in enumerate([(0.0, '#1f5bd6', 0.42), (0.39, '#3d86ff', 0.36),
                                          (0.78, '#7ab8ff', 0.28), (1.18, '#b9dcff', 0.18)]):
        petal = size * (0.55 + 0.45 * np.abs(np.cos(3 * (ang - rot)))) - r
        img = layer(img, petal * 18, col, 0.55)
    img = glow(img, cx, cy, 0.22, '#3a7bff', 0.45, 1.0)
    finish(shade_bottom(img, 0.6, 0.6), path, blur=2.5, seed=3)


def material(path):
    """A deep mesh gradient in teal, green and violet (Material You-like)."""
    img = np.zeros((H, W, 3), np.float32) + rgb('#0b1416')
    for cx, cy, rad, col, s in [(0.15, 0.2, 0.35, '#1f8a7a', 0.9), (0.85, 0.15, 0.32, '#5b4bb3', 0.85),
                                (0.55, 0.7, 0.38, '#2f6b4a', 0.7), (0.95, 0.85, 0.25, '#2a5a8a', 0.6)]:
        img = glow(img, cx, cy, rad, col, s, 1.4)
    finish(shade_bottom(img, 0.55, 0.45), path, blur=2, seed=4)


def neon(path):
    """Night city light: magenta and cyan streaks over deep navy (cyberpunk)."""
    img = vertical([(0, '#070616'), (0.6, '#120a2a'), (1, '#05040c')])
    img = glow(img, 0.2, 0.3, 0.25, '#ff2fb3', 0.7, 1.2)
    img = glow(img, 0.85, 0.25, 0.22, '#21e3ff', 0.65, 1.2)
    rng = np.random.default_rng(7)
    for _ in range(14):
        y0 = rng.uniform(0.05, 0.6)
        slope = rng.uniform(-0.25, 0.25)
        col = '#ff3fc4' if rng.random() < 0.5 else '#3ff0ff'
        line = np.abs(V - (y0 + slope * (U - 0.5)))
        img = 1 - (1 - img) * (1 - np.exp(-(line / 0.0025) ** 2)[..., None] * rgb(col) * rng.uniform(0.35, 0.8))
    finish(shade_bottom(img, 0.55, 0.6), path, blur=1.5, seed=5)


def aurora(path):
    """Green and violet aurora curtains over a night sky."""
    img = vertical([(0, '#020611'), (1, '#050b18')])
    for i, (col, base, amp) in enumerate([('#2bd99a', 0.42, 0.07), ('#38b6ff', 0.36, 0.06), ('#9b5cff', 0.30, 0.05)]):
        edge = wave(base, amp, 0.9 + i * 0.35, i * 2.1)
        # A curtain: bright at its lower edge, fading upwards, with fine vertical rays.
        above = edge - V
        curtain = np.where(above > 0, np.exp(-above / (0.10 + 0.03 * i)), np.exp(-(above / 0.012) ** 2))
        rays = 0.75 + 0.25 * np.sin(U * 140 + 3 * np.sin(U * 9 + i))
        img = 1 - (1 - img) * (1 - (curtain * rays)[..., None] * rgb(col) * (0.75 - 0.12 * i))
    rng = np.random.default_rng(11)
    stars = (rng.random((H, W)) > 0.9993).astype(np.float32) * (V < 0.6)
    img = np.clip(img + stars[..., None] * 0.8, 0, 1)
    finish(shade_bottom(img, 0.5, 0.5), path, blur=1, seed=6)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    os.makedirs(out, exist_ok=True)
    for name, draw in [('dunes', dunes), ('bloom', bloom), ('material', material), ('neon', neon), ('aurora', aurora)]:
        path = os.path.join(out, name + '.jpg')
        draw(path)
        print(path, os.path.getsize(path))


if __name__ == '__main__':
    main()
