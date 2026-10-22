#!/usr/bin/env python3
"""Generates res/values*/themes_palettes.xml: one Pocket theme per colour palette (light + dark).
Palette values are read from a decoded MyMoney APK's colors.xml; pass its res dir as argv[1]."""
import re, sys, pathlib

REF = pathlib.Path(sys.argv[1])
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res"
PALETTES = ["classy", "darker", "nature", "rosy", "sky"]

def load(p):
    s = p.read_text()
    return {m.group(1): m.group(2) for m in re.finditer(r'<color name="(\w+)">(#[0-9a-fA-F]+)</color>', s)}

day, night = load(REF / "values/colors.xml"), load(REF / "values-night/colors.xml")

def rgb(h):
    h = h.lstrip("#")
    if len(h) == 8: h = h[2:]
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def hx(t): return "#%02x%02x%02x" % t
def blend(a, b, f): return tuple(round(x + (y - x) * f) for x, y in zip(a, b))
def lum(c):
    def ch(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = c
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
def on(c): return "#000000" if lum(c) > 0.4 else "#ffffff"

def style(name, pal, is_night):
    src = {**day, **night} if is_night else day
    g = lambda k: rgb(src[f"{pal}Color{k}"])
    bg, onsurf, accent = g("Background"), g("OnSurface"), g("Accent")
    bar = blend(bg, onsurf, 0.11)
    light = lum(bg) > 0.4
    items = {
        "colorPrimary": hx(accent), "colorOnPrimary": on(accent), "colorSecondary": hx(accent), "colorOnSecondary": on(accent),
        "colorSurface": hx(bg), "colorOnSurface": hx(onsurf), "colorSurfaceContainer": hx(bar), "colorSurfaceContainerHigh": hx(bar),
        "colorSurfaceContainerLow": hx(bg), "colorOnSurfaceVariant": hx(g("OnSurfaceDisabled")), "colorOutline": hx(g("OnSurfaceDisabled")),
        "colorOutlineVariant": hx(g("OnSurfaceTone")), "colorError": hx(g("Negative")),
        "colorSecondaryContainer": hx(g("SurfaceLight")), "colorOnSecondaryContainer": hx(onsurf),
        "colorPrimaryContainer": hx(g("SurfaceLight")), "colorOnPrimaryContainer": hx(onsurf), "colorControlActivated": hx(accent),
        "android:colorBackground": hx(bg), "android:windowBackground": hx(bg),
        "android:windowLightStatusBar": str(light).lower(), "android:windowLightNavigationBar": str(light).lower(),
        "pkNegative": hx(g("Negative")), "pkPositive": hx(g("Positive")), "pkNeutral": hx(g("Neutral")),
        "pkSurfaceLight": hx(g("SurfaceLight")), "pkDisabled": hx(g("OnSurfaceDisabled")), "pkBar": hx(bar),
        "pkBrand": hx(g("BrandText")), "pkTone": hx(g("OnSurfaceTone")),
    }
    body = "\n".join(f'        <item name="{k}">{v}</item>' for k, v in items.items())
    return f'    <style name="{name}" parent="Theme.Pocket">\n{body}\n    </style>\n'

for folder, is_night in (("values", False), ("values-night", True)):
    xml = "<resources>\n" + "".join(style(f"Theme.Pocket.{p.capitalize()}", p, is_night) for p in PALETTES) + "</resources>\n"
    (OUT / folder / "themes_palettes.xml").write_text(xml)
print("ok")
