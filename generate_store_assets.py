"""
Generate Play Store assets for NewsThread:
1. Feature Graphic (1024x500) - dark slate bg, app icon, tagline
2. 6 Framed Screenshots (1080x1920 each) - device frame with caption
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

BASE = r"C:\Users\lweis\Documents\newsthread"
OUT = os.path.join(BASE, "store_assets")
os.makedirs(OUT, exist_ok=True)

SCREENSHOTS = os.path.join(BASE, "screenshots")
ICON_PATH = os.path.join(BASE, ".planning", "phases", "16-identity-store-assets", "app_icon_store.png")

# Brand colors (from Color.kt)
SLATE_950 = (2, 6, 23)
SLATE_900 = (15, 23, 43)
SLATE_800 = (30, 41, 59)
SLATE_700 = (51, 65, 85)
AMBER_500 = (245, 158, 11)
AMBER_300 = (252, 211, 77)
AMBER_600 = (217, 119, 6)
BIAS_LEFT = (96, 165, 250)
BIAS_CENTER = (167, 139, 250)
BIAS_RIGHT = (248, 113, 113)
WHITE = (255, 255, 255)

def get_font(size, bold=False):
    """Try to load a system font, fall back to default."""
    font_paths = [
        r"C:\Windows\Fonts\segoeui.ttf",
        r"C:\Windows\Fonts\segoeuib.ttf",
        r"C:\Windows\Fonts\arial.ttf",
        r"C:\Windows\Fonts\arialbd.ttf",
    ]
    if bold:
        font_paths = [
            r"C:\Windows\Fonts\segoeuib.ttf",
            r"C:\Windows\Fonts\arialbd.ttf",
        ] + font_paths
    for fp in font_paths:
        if os.path.exists(fp):
            try:
                return ImageFont.truetype(fp, size)
            except:
                continue
    return ImageFont.load_default()

def draw_spectrum_bar(draw, x, y, width, height):
    """Draw a horizontal bias spectrum bar (blue -> violet -> red)."""
    for i in range(width):
        ratio = i / width
        if ratio < 0.5:
            t = ratio * 2
            r = int(BIAS_LEFT[0] + (BIAS_CENTER[0] - BIAS_LEFT[0]) * t)
            g = int(BIAS_LEFT[1] + (BIAS_CENTER[1] - BIAS_LEFT[1]) * t)
            b = int(BIAS_LEFT[2] + (BIAS_CENTER[2] - BIAS_LEFT[2]) * t)
        else:
            t = (ratio - 0.5) * 2
            r = int(BIAS_CENTER[0] + (BIAS_RIGHT[0] - BIAS_CENTER[0]) * t)
            g = int(BIAS_CENTER[1] + (BIAS_RIGHT[1] - BIAS_CENTER[1]) * t)
            b = int(BIAS_CENTER[2] + (BIAS_RIGHT[2] - BIAS_CENTER[2]) * t)
        draw.line([(x + i, y), (x + i, y + height)], fill=(r, g, b))

def create_feature_graphic():
    """Create 1024x500 feature graphic."""
    W, H = 1024, 500
    img = Image.new("RGB", (W, H), SLATE_950)
    draw = ImageDraw.Draw(img)

    # Subtle gradient overlay (darker at bottom)
    for y in range(H):
        alpha = int(30 * (y / H))
        draw.line([(0, y), (W, y)], fill=(
            max(0, SLATE_950[0] - alpha),
            max(0, SLATE_950[1] - alpha),
            max(0, SLATE_950[2] - alpha),
        ))

    # Draw spectrum bar across the full width at bottom
    bar_height = 4
    draw_spectrum_bar(draw, 0, H - bar_height, W, bar_height)

    # Load and place the app icon (left side)
    icon = Image.open(ICON_PATH).convert("RGBA")
    icon_size = 160
    icon = icon.resize((icon_size, icon_size), Image.LANCZOS)
    # Place icon on left-center
    icon_x = 80
    icon_y = (H - icon_size) // 2
    # Create a rounded mask for the icon
    mask = Image.new("L", (icon_size, icon_size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle([(0, 0), (icon_size, icon_size)], radius=32, fill=255)
    img.paste(icon, (icon_x, icon_y), mask)

    # App name - right of icon
    font_title = get_font(52, bold=True)
    font_tagline = get_font(24)
    font_subtitle = get_font(18)

    text_x = icon_x + icon_size + 50
    text_y_start = 130

    draw.text((text_x, text_y_start), "NewsThread", fill=WHITE, font=font_title)
    draw.text((text_x, text_y_start + 70), "Follow the thread of every story.", fill=AMBER_300, font=font_tagline)

    # Subtitle description
    draw.text((text_x, text_y_start + 115), "See how every source covers the same story.", fill=SLATE_700, font=font_subtitle)
    draw.text((text_x, text_y_start + 142), "Plotted along the political bias spectrum.", fill=SLATE_700, font=font_subtitle)

    # Draw a wider spectrum bar as a visual element
    bar_y = text_y_start + 190
    bar_w = 380
    bar_h = 8
    # Rounded ends
    draw_spectrum_bar(draw, text_x, bar_y, bar_w, bar_h)
    # Labels
    font_tiny = get_font(12)
    draw.text((text_x, bar_y + 14), "Left", fill=BIAS_LEFT, font=font_tiny)
    draw.text((text_x + bar_w // 2 - 15, bar_y + 14), "Center", fill=BIAS_CENTER, font=font_tiny)
    draw.text((text_x + bar_w - 25, bar_y + 14), "Right", fill=BIAS_RIGHT, font=font_tiny)

    # Add small dots on the spectrum bar for visual interest
    dot_positions = [0.15, 0.35, 0.48, 0.52, 0.7, 0.85]
    for pos in dot_positions:
        dx = int(text_x + bar_w * pos)
        dy = bar_y + bar_h // 2
        draw.ellipse([(dx-4, dy-4), (dx+4, dy+4)], fill=WHITE, outline=None)

    # Right side: faded screenshot preview
    try:
        preview = Image.open(os.path.join(SCREENSHOTS, "NewsThread_Feed_Page_with_Story_Source_Bias_Rating.png"))
        # Crop to top portion, resize to fit
        crop_h = int(preview.width * 1.6)
        preview = preview.crop((0, 0, preview.width, min(crop_h, preview.height)))
        preview_h = H - 40
        preview_w = int(preview.width * preview_h / preview.height)
        preview = preview.resize((preview_w, preview_h), Image.LANCZOS)

        # Create a faded version
        fade = Image.new("RGBA", (preview_w, preview_h), (0, 0, 0, 0))
        preview_rgba = preview.convert("RGBA")
        # Apply left-side fade gradient
        for x in range(min(120, preview_w)):
            alpha = int(255 * (x / 120))
            for y_px in range(preview_h):
                r, g, b, a = preview_rgba.getpixel((x, y_px))
                fade.putpixel((x, y_px), (r, g, b, alpha))
        for x in range(120, preview_w):
            for y_px in range(preview_h):
                fade.putpixel((x, y_px), preview_rgba.getpixel((x, y_px)))

        # Place on right side
        paste_x = W - preview_w - 20
        img.paste(fade, (paste_x, 20), fade)
    except Exception as e:
        print(f"Warning: Could not add preview screenshot: {e}")

    # Save
    img.save(os.path.join(OUT, "feature_graphic_1024x500.png"), "PNG")
    print(f"Feature graphic saved: {os.path.join(OUT, 'feature_graphic_1024x500.png')}")
    return img

def create_framed_screenshot(screenshot_path, caption, subtitle, output_name):
    """Create a framed screenshot with caption header for Play Store."""
    # Play Store phone screenshot: 1080x1920 recommended
    W, H = 1080, 1920

    img = Image.new("RGB", (W, H), SLATE_950)
    draw = ImageDraw.Draw(img)

    # Caption area at top (dark with amber accent)
    caption_h = 280
    # Subtle gradient in caption area
    for y in range(caption_h):
        t = y / caption_h
        r = int(SLATE_950[0] + (SLATE_900[0] - SLATE_950[0]) * t)
        g = int(SLATE_950[1] + (SLATE_900[1] - SLATE_950[1]) * t)
        b = int(SLATE_950[2] + (SLATE_900[2] - SLATE_950[2]) * t)
        draw.line([(0, y), (W, y)], fill=(r, g, b))

    # Caption text
    font_caption = get_font(42, bold=True)
    font_sub = get_font(24)

    # Center the caption text
    bbox = draw.textbbox((0, 0), caption, font=font_caption)
    tw = bbox[2] - bbox[0]
    tx = (W - tw) // 2
    draw.text((tx, 80), caption, fill=WHITE, font=font_caption)

    # Subtitle
    bbox2 = draw.textbbox((0, 0), subtitle, font=font_sub)
    tw2 = bbox2[2] - bbox2[0]
    tx2 = (W - tw2) // 2
    draw.text((tx2, 140), subtitle, fill=AMBER_300, font=font_sub)

    # Small spectrum bar under subtitle
    bar_w = 200
    bar_x = (W - bar_w) // 2
    bar_y = 195
    draw_spectrum_bar(draw, bar_x, bar_y, bar_w, 3)

    # Load and place screenshot below caption
    screenshot = Image.open(screenshot_path).convert("RGB")

    # Available space for screenshot
    ss_area_top = caption_h + 10
    ss_area_bottom = H - 20
    ss_area_h = ss_area_bottom - ss_area_top

    # Scale screenshot to fit width with padding
    padding = 40
    ss_target_w = W - (padding * 2)
    ss_scale = ss_target_w / screenshot.width
    ss_target_h = int(screenshot.height * ss_scale)

    # If too tall, crop from bottom
    if ss_target_h > ss_area_h:
        # Crop the source screenshot from top
        crop_h = int(ss_area_h / ss_scale)
        screenshot = screenshot.crop((0, 0, screenshot.width, crop_h))
        ss_target_h = ss_area_h

    screenshot = screenshot.resize((ss_target_w, ss_target_h), Image.LANCZOS)

    # Add thin rounded border (device frame effect)
    frame_padding = 8
    frame_x = padding - frame_padding
    frame_y = ss_area_top - frame_padding
    frame_w = ss_target_w + frame_padding * 2
    frame_h = ss_target_h + frame_padding * 2
    draw.rounded_rectangle(
        [(frame_x, frame_y), (frame_x + frame_w, frame_y + frame_h)],
        radius=24,
        fill=SLATE_800,
        outline=SLATE_700,
        width=2
    )

    # Paste screenshot
    img.paste(screenshot, (padding, ss_area_top))

    # Round the corners of the screenshot area
    # (draw rounded rect mask over the corners)
    corner_r = 16
    # Top-left corner
    draw.pieslice([(padding - 1, ss_area_top - 1), (padding + corner_r * 2, ss_area_top + corner_r * 2)], 180, 270, fill=SLATE_800)
    # Top-right corner
    draw.pieslice([(padding + ss_target_w - corner_r * 2, ss_area_top - 1), (padding + ss_target_w + 1, ss_area_top + corner_r * 2)], 270, 360, fill=SLATE_800)

    img.save(os.path.join(OUT, output_name), "PNG")
    print(f"Screenshot saved: {os.path.join(OUT, output_name)}")
    return img

# ─── Generate Feature Graphic ─────────────────────────────────────────────
print("=== Generating Feature Graphic ===")
create_feature_graphic()

# ─── Generate 6 Framed Screenshots ────────────────────────────────────────
print("\n=== Generating Framed Screenshots ===")

screenshots_config = [
    {
        "file": "NewsThread_Feed_Page_with_Story_Source_Bias_Rating.png",
        "caption": "Your News Feed",
        "subtitle": "Bias ratings and source badges at a glance",
        "output": "screenshot_01_feed_badges.png"
    },
    {
        "file": "compare_perspectives_updated.png",
        "caption": "Compare Perspectives",
        "subtitle": "See every side of the story on the bias spectrum",
        "output": "screenshot_02_bias_spectrum.png"
    },
    {
        "file": "tracking_updated.png",
        "caption": "Track Developing Stories",
        "subtitle": "Get notified as news evolves across sources",
        "output": "screenshot_03_tracking.png"
    },
    {
        "file": "story_analysis_updated.png",
        "caption": "Story Analysis",
        "subtitle": "Coverage bias breakdown with Left, Center, Right views",
        "output": "screenshot_04_story_analysis.png"
    },
    {
        "file": "feed_updated.png",
        "caption": "Clean, Fast Feed",
        "subtitle": "Rich article cards with images and source reliability",
        "output": "screenshot_05_feed_clean.png"
    },
    {
        "file": "Article_Page_with_New_Updates_Toast.jpg",
        "caption": "Real-Time Updates",
        "subtitle": "Stay informed as stories develop with push alerts",
        "output": "screenshot_06_article_updates.png"
    },
]

for config in screenshots_config:
    ss_path = os.path.join(SCREENSHOTS, config["file"])
    if os.path.exists(ss_path):
        create_framed_screenshot(
            ss_path,
            config["caption"],
            config["subtitle"],
            config["output"]
        )
    else:
        print(f"WARNING: Screenshot not found: {ss_path}")

print(f"\n=== Done! Assets in {OUT} ===")
