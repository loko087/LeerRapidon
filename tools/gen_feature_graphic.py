"""Builds the Play Store feature graphic (1024x500) from the existing icon.

The badge and the brand gradient are lifted straight out of
app/src/main/ic_launcher-playstore.png, so the banner cannot drift out of
sync with the icon. Run from the repo root:

    python tools/gen_feature_graphic.py
"""
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ICON = "app/src/main/ic_launcher-playstore.png"
OUT = "docs/store/feature-graphic-1024x500.png"
W, H = 1024, 500
BADGE = 288

INK = (13, 15, 19)          # the app's own dark ground
CREAM = (233, 228, 216)
DIM = (150, 160, 175)
SANS_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
SANS = "C:/Windows/Fonts/segoeui.ttf"

HEADLINE = ["Speed-read your own", "PDFs and EPUBs"]
SUBHEAD = "RSVP reader · text-to-speech · fully offline"

icon = Image.open(ICON).convert("RGB")


def brand_gradient(w, h):
    """The icon's own left-to-right ramp, stretched to any size."""
    strip = Image.new("RGB", (icon.width, 1))
    strip.putdata([icon.getpixel((x, 8)) for x in range(icon.width)])
    return strip.resize((w, h), Image.BILINEAR)


# --- background: dark, with the brand gradient bled up from the bottom -----
bg = Image.new("RGB", (W, H), INK)
veil = Image.new("L", (W, H), 0)
ImageDraw.Draw(veil).ellipse([-200, 120, W + 200, H + 420], fill=40)
bg = Image.composite(brand_gradient(W, H), bg, veil.filter(ImageFilter.GaussianBlur(160)))
d = ImageDraw.Draw(bg)

# --- measure the badge + text block so it can be centred as a unit ---------
h_font = ImageFont.truetype(SANS_BOLD, 44)
s_font = ImageFont.truetype(SANS, 26)
GAP = 60
text_w = max(d.textlength(line, font=h_font) for line in HEADLINE)
text_w = max(text_w, d.textlength(SUBHEAD, font=s_font))
block_w = BADGE + GAP + text_w
x0 = round((W - block_w) / 2)

# --- badge ----------------------------------------------------------------
corner = Image.new("L", (BADGE * 4, BADGE * 4), 0)
ImageDraw.Draw(corner).rounded_rectangle(
    [0, 0, BADGE * 4 - 1, BADGE * 4 - 1], radius=BADGE * 4 * 0.22, fill=255)
bg.paste(icon.resize((BADGE, BADGE), Image.LANCZOS), (x0, (H - BADGE) // 2),
         corner.resize((BADGE, BADGE), Image.LANCZOS))

# --- headline + subhead ---------------------------------------------------
tx = x0 + BADGE + GAP
line_h, sub_gap = 54, 30
text_h = line_h * len(HEADLINE) + sub_gap + s_font.getbbox(SUBHEAD)[3]
ty = (H - text_h) // 2
for i, line in enumerate(HEADLINE):
    d.text((tx, ty + i * line_h), line, font=h_font, fill=CREAM)
d.text((tx, ty + line_h * len(HEADLINE) + sub_gap), SUBHEAD, font=s_font, fill=DIM)

bg.save(OUT)
print("wrote", OUT, bg.size)
