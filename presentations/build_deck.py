"""
Build the NN Greece / Spotify Model agile transformation deck as a .pptx file.
Run with:  python build_deck.py
Output:    nn-greece-agile-transformation-spotify-model.pptx (same folder)
"""

from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.util import Inches, Pt
import os

# ── Brand palette ──────────────────────────────────────────────────────────────
NN_ORANGE   = RGBColor(0xF0, 0x6A, 0x00)   # NN Group signature orange
NN_DARK     = RGBColor(0x1A, 0x1A, 0x2E)   # deep navy / near-black
NN_MID      = RGBColor(0x2E, 0x4A, 0x6E)   # mid-blue
NN_LIGHT    = RGBColor(0xF4, 0xF6, 0xF9)   # off-white slide background
NN_ACCENT   = RGBColor(0x00, 0xAA, 0xCC)   # teal accent
WHITE       = RGBColor(0xFF, 0xFF, 0xFF)
GREY_TEXT   = RGBColor(0x55, 0x55, 0x55)

SLIDE_W = Inches(13.33)
SLIDE_H = Inches(7.5)

# ── Helpers ────────────────────────────────────────────────────────────────────

def add_rect(slide, left, top, width, height, fill_color, line_color=None):
    from pptx.util import Emu
    shape = slide.shapes.add_shape(
        1,  # MSO_SHAPE_TYPE.RECTANGLE
        left, top, width, height
    )
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill_color
    if line_color:
        shape.line.color.rgb = line_color
        shape.line.width = Pt(1)
    else:
        shape.line.fill.background()
    return shape


def add_textbox(slide, text, left, top, width, height,
                font_size=18, bold=False, color=NN_DARK,
                align=PP_ALIGN.LEFT, wrap=True, italic=False):
    txBox = slide.shapes.add_textbox(left, top, width, height)
    tf = txBox.text_frame
    tf.word_wrap = wrap
    p = tf.paragraphs[0]
    p.alignment = align
    run = p.add_run()
    run.text = text
    run.font.size = Pt(font_size)
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = color
    return txBox


def add_title_slide(prs, title, subtitle, presenter="[Your Name], Agile Coach", date="April 2026"):
    slide_layout = prs.slide_layouts[6]  # blank
    slide = prs.slides.add_slide(slide_layout)

    # Full background
    add_rect(slide, 0, 0, SLIDE_W, SLIDE_H, NN_DARK)

    # Orange accent bar on left
    add_rect(slide, 0, 0, Inches(0.18), SLIDE_H, NN_ORANGE)

    # Orange accent stripe at bottom
    add_rect(slide, 0, SLIDE_H - Inches(0.12), SLIDE_W, Inches(0.12), NN_ORANGE)

    # NN label top-right
    add_textbox(slide, "NN Group", SLIDE_W - Inches(2.2), Inches(0.3), Inches(2.0), Inches(0.5),
                font_size=14, bold=True, color=NN_ORANGE, align=PP_ALIGN.RIGHT)
    add_textbox(slide, "Greece", SLIDE_W - Inches(2.2), Inches(0.7), Inches(2.0), Inches(0.4),
                font_size=11, color=WHITE, align=PP_ALIGN.RIGHT)

    # Main title
    add_textbox(slide, title, Inches(0.5), Inches(1.8), Inches(12.0), Inches(1.6),
                font_size=40, bold=True, color=WHITE, align=PP_ALIGN.LEFT)

    # Subtitle
    add_textbox(slide, subtitle, Inches(0.5), Inches(3.4), Inches(10.0), Inches(0.8),
                font_size=22, bold=False, color=NN_ORANGE, align=PP_ALIGN.LEFT)

    # Divider line
    add_rect(slide, Inches(0.5), Inches(4.3), Inches(5.0), Pt(1.5), NN_ACCENT)

    # Presenter / date
    add_textbox(slide, presenter, Inches(0.5), Inches(4.6), Inches(8.0), Inches(0.4),
                font_size=14, color=WHITE)
    add_textbox(slide, date, Inches(0.5), Inches(5.0), Inches(8.0), Inches(0.4),
                font_size=13, color=GREY_TEXT)
    add_textbox(slide, "Prepared for: NN Greece — Leadership & Change Champions",
                Inches(0.5), Inches(5.4), Inches(9.0), Inches(0.4),
                font_size=12, italic=True, color=GREY_TEXT)
    return slide


def add_section_divider(prs, section_number, section_title, subtitle=""):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_rect(slide, 0, 0, SLIDE_W, SLIDE_H, NN_MID)
    add_rect(slide, 0, 0, Inches(0.18), SLIDE_H, NN_ORANGE)
    add_rect(slide, 0, SLIDE_H - Inches(0.12), SLIDE_W, Inches(0.12), NN_ORANGE)

    add_textbox(slide, section_number, Inches(0.5), Inches(2.2), Inches(12), Inches(1.0),
                font_size=60, bold=True, color=NN_ORANGE, align=PP_ALIGN.CENTER)
    add_textbox(slide, section_title, Inches(0.5), Inches(3.3), Inches(12), Inches(1.0),
                font_size=30, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
    if subtitle:
        add_textbox(slide, subtitle, Inches(0.5), Inches(4.4), Inches(12), Inches(0.6),
                    font_size=16, color=GREY_TEXT, align=PP_ALIGN.CENTER)
    return slide


def content_slide(prs, title, body_func):
    """
    Creates a standard content slide and calls body_func(slide) to add content.
    Returns the slide.
    """
    slide = prs.slides.add_slide(prs.slide_layouts[6])

    # Light background
    add_rect(slide, 0, 0, SLIDE_W, SLIDE_H, NN_LIGHT)

    # Header band
    add_rect(slide, 0, 0, SLIDE_W, Inches(1.05), NN_DARK)
    add_rect(slide, 0, Inches(1.05), SLIDE_W, Pt(3), NN_ORANGE)

    # Title text
    add_textbox(slide, title, Inches(0.4), Inches(0.12), Inches(12.4), Inches(0.85),
                font_size=24, bold=True, color=WHITE, align=PP_ALIGN.LEFT)

    # Slide number placeholder (bottom right)
    add_textbox(slide, "", Inches(12.5), Inches(7.1), Inches(0.7), Inches(0.3),
                font_size=10, color=GREY_TEXT, align=PP_ALIGN.RIGHT)

    # Footer bar
    add_rect(slide, 0, SLIDE_H - Inches(0.35), SLIDE_W, Inches(0.35), NN_DARK)
    add_textbox(slide, "NN Greece · Agile Transformation · Spotify Model",
                Inches(0.3), SLIDE_H - Inches(0.32), Inches(8), Inches(0.28),
                font_size=9, color=GREY_TEXT)
    add_textbox(slide, "CONFIDENTIAL",
                Inches(10.5), SLIDE_H - Inches(0.32), Inches(2.5), Inches(0.28),
                font_size=9, color=GREY_TEXT, align=PP_ALIGN.RIGHT)

    body_func(slide)
    return slide


def bullet_box(slide, items, left, top, width, height,
               font_size=15, color=NN_DARK, bullet="•", indent=0):
    txBox = slide.shapes.add_textbox(left, top, width, height)
    tf = txBox.text_frame
    tf.word_wrap = True
    first = True
    for item in items:
        if first:
            p = tf.paragraphs[0]
            first = False
        else:
            p = tf.add_paragraph()
        p.space_before = Pt(3)
        run = p.add_run()
        prefix = "  " * indent + (bullet + "  " if bullet else "")
        run.text = prefix + item
        run.font.size = Pt(font_size)
        run.font.color.rgb = color
    return txBox


def two_col_table(slide, headers, rows, left, top, width, height,
                  col_widths=None, header_bg=NN_DARK, row_bg=WHITE,
                  alt_bg=NN_LIGHT, font_size=13):
    n_cols = len(headers)
    if col_widths is None:
        col_widths = [width // n_cols] * n_cols

    row_h = height // (len(rows) + 1)
    # header row
    x = left
    for i, h in enumerate(headers):
        add_rect(slide, x, top, col_widths[i], row_h, header_bg)
        add_textbox(slide, h, x + Pt(6), top + Pt(4), col_widths[i] - Pt(10), row_h,
                    font_size=font_size, bold=True, color=WHITE, align=PP_ALIGN.LEFT)
        x += col_widths[i]

    for r_idx, row in enumerate(rows):
        bg = alt_bg if r_idx % 2 == 0 else row_bg
        x = left
        y = top + row_h * (r_idx + 1)
        for i, cell in enumerate(row):
            add_rect(slide, x, y, col_widths[i], row_h, bg)
            add_textbox(slide, cell, x + Pt(6), y + Pt(3), col_widths[i] - Pt(10), row_h,
                        font_size=font_size - 1, color=NN_DARK)
            x += col_widths[i]


def orange_card(slide, title, body, left, top, width, height, font_size=13):
    add_rect(slide, left, top, width, Inches(0.38), NN_ORANGE)
    add_rect(slide, left, top + Inches(0.38), width, height - Inches(0.38), WHITE,
             line_color=NN_ORANGE)
    add_textbox(slide, title, left + Pt(8), top + Pt(4), width - Pt(14), Inches(0.34),
                font_size=13, bold=True, color=WHITE)
    add_textbox(slide, body, left + Pt(8), top + Inches(0.42), width - Pt(14),
                height - Inches(0.5), font_size=font_size, color=NN_DARK, wrap=True)


# ── Slide content builders ──────────────────────────────────────────────────────

def slide_agenda(slide):
    items = [
        "1.  What does 'Agile Transformation' actually mean?",
        "2.  Why scaling agile matters for an organisation like NN Greece",
        "3.  The Spotify Model — the four building blocks",
        "4.  Autonomy with Alignment — how it holds together",
        "5.  The culture and leadership shift",
        "6.  What it could look like at NN Greece",
        "7.  Where to start — a practical first step",
        "8.  Discussion",
    ]
    bullet_box(slide, items, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.8),
               font_size=17, bullet="", color=NN_DARK)


def slide_word_before(slide):
    add_rect(slide, Inches(0.5), Inches(1.3), Inches(12.3), Inches(1.5), NN_MID)
    add_textbox(slide,
                '"Agile transformation is not about adopting a framework.\n'
                'It is about changing how the organisation thinks, decides, and delivers."',
                Inches(0.7), Inches(1.35), Inches(11.8), Inches(1.4),
                font_size=17, italic=True, color=WHITE, align=PP_ALIGN.CENTER)

    points = [
        "Most transformations stall because they install the mechanics of agile —",
        "    stand-ups, sprints, boards — without changing the underlying operating model.",
        "",
        "Today we focus on the operating model first.",
    ]
    bullet_box(slide, points, Inches(0.6), Inches(3.1), Inches(12.0), Inches(2.5),
               font_size=16, bullet="", color=NN_DARK)


def slide_what_is(slide):
    headers = ["Traditional Organisation", "Agile Organisation"]
    rows = [
        ["Deliver to a long-term plan", "Deliver and adapt continuously"],
        ["Decisions escalate upward", "Decisions made closest to the work"],
        ["Teams organised by function (IT, Claims…)", "Teams organised around customer outcomes"],
        ["Annual budgeting cycles", "Continuous value-based funding"],
        ["Change is a risk to manage", "Change is an expected input"],
    ]
    col_w = [Inches(6.0), Inches(6.0)]
    two_col_table(slide, headers, rows,
                  left=Inches(0.5), top=Inches(1.3),
                  width=Inches(12.2), height=Inches(4.2),
                  col_widths=col_w, font_size=14)

    add_textbox(slide,
                "Transformation = shifting the entire operating model, not just IT.",
                Inches(0.5), Inches(5.7), Inches(12.0), Inches(0.5),
                font_size=15, bold=True, color=NN_ORANGE)


def slide_why_now(slide):
    cards = [
        ("1. Customer Expectations",
         "Digital-native competitors (insurtech) set the bar. "
         "Customers compare NN to Amazon, not to other insurers."),
        ("2. Regulatory Velocity",
         "DORA, IDD, IFRS 17 and future EU directives require faster adaptation cycles."),
        ("3. NN Group Alignment",
         "NN Group is already advancing its own agile maturity. "
         "NN Greece can leverage group learning rather than start from zero."),
    ]
    card_w = Inches(3.9)
    card_h = Inches(2.4)
    gap = Inches(0.22)
    start_x = Inches(0.5)
    for i, (title, body) in enumerate(cards):
        orange_card(slide, title, body,
                    left=start_x + i * (card_w + gap),
                    top=Inches(1.4), width=card_w, height=card_h, font_size=13)

    add_textbox(slide,
                "At 600 people, NN Greece is the ideal size: large enough to benefit meaningfully, "
                "small enough to move quickly.",
                Inches(0.5), Inches(4.1), Inches(12.2), Inches(0.6),
                font_size=14, bold=True, color=NN_MID)

    add_textbox(slide,
                "ING Bank Netherlands — the closest parallel — ran a Spotify-inspired transformation "
                "reorganising ~3,500 staff. Their learnings are directly available within NN Group.",
                Inches(0.5), Inches(4.85), Inches(12.2), Inches(0.8),
                font_size=13, italic=True, color=GREY_TEXT)


def slide_why_spotify(slide):
    points = [
        ("Designed for product-oriented, cross-functional teams",
         "not IT delivery projects"),
        ("Balances autonomy and alignment",
         "without heavy ceremony overhead"),
        ("Principle-based, not prescription-heavy",
         "adapts to your context rather than forcing a rigid process"),
        ("Proven in financial services",
         "ING Bank Netherlands (NN Group territory) is the flagship case"),
    ]
    y = Inches(1.3)
    for title, sub in points:
        add_rect(slide, Inches(0.5), y, Pt(6), Inches(0.55), NN_ORANGE)
        add_textbox(slide, title, Inches(0.75), y, Inches(11.5), Inches(0.32),
                    font_size=15, bold=True, color=NN_DARK)
        add_textbox(slide, sub, Inches(0.75), y + Inches(0.3), Inches(11.5), Inches(0.28),
                    font_size=13, italic=True, color=GREY_TEXT)
        y += Inches(0.85)

    add_rect(slide, Inches(0.5), Inches(5.0), Inches(12.2), Inches(1.0), NN_MID)
    add_textbox(slide,
                "Other frameworks (SAFe, LeSS, Nexus, DA) are valid — but they require "
                "heavier upfront process design. For a 600-person insurer beginning its "
                "transformation, the Spotify model offers the best ratio of simplicity to impact.",
                Inches(0.7), Inches(5.08), Inches(11.8), Inches(0.85),
                font_size=13, italic=True, color=WHITE)


def slide_four_blocks_overview(slide):
    blocks = [
        ("SQUAD", "Primary delivery unit\n6–10 people\nFully cross-functional\nOne customer journey", NN_ORANGE),
        ("TRIBE", "Cluster of related squads\n40–150 people\nOne customer domain\nOwns its own budget", NN_MID),
        ("CHAPTER", "Same-discipline peers\nWithin a tribe\nCareer development\nCraft standards", NN_ACCENT),
        ("GUILD", "Voluntary community\nCross-tribe\nShared interest/skill\nKnowledge sharing", RGBColor(0x6C, 0x63, 0xFF)),
    ]
    card_w = Inches(2.9)
    card_h = Inches(3.8)
    gap = Inches(0.22)
    start_x = Inches(0.5)
    for i, (title, body, color) in enumerate(blocks):
        x = start_x + i * (card_w + gap)
        add_rect(slide, x, Inches(1.3), card_w, Inches(0.55), color)
        add_rect(slide, x, Inches(1.85), card_w, card_h - Inches(0.55), WHITE,
                 line_color=color)
        add_textbox(slide, title, x + Pt(8), Inches(1.35), card_w - Pt(14), Inches(0.45),
                    font_size=16, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
        add_textbox(slide, body, x + Pt(10), Inches(1.95), card_w - Pt(18),
                    card_h - Inches(0.75),
                    font_size=13, color=NN_DARK)

    add_textbox(slide,
                "Each block serves a distinct, non-overlapping purpose — together they create "
                "an organisation that is autonomous at the team level and aligned at the company level.",
                Inches(0.5), Inches(5.35), Inches(12.2), Inches(0.6),
                font_size=13, italic=True, color=GREY_TEXT)


def slide_squad(slide):
    left_items = [
        "Size: 6–10 people (two-pizza rule)",
        "Fully cross-functional: product, tech, data,\n   design, operations, compliance",
        "Owns one customer journey end-to-end",
        "Chooses how to work (Scrum, Kanban, hybrid)",
        "One Product Owner sets priorities",
        "Should release value without waiting\n   for another team's approval",
    ]
    bullet_box(slide, left_items, Inches(0.5), Inches(1.3), Inches(5.8), Inches(4.5),
               font_size=14, color=NN_DARK)

    add_rect(slide, Inches(6.5), Inches(1.3), Inches(6.3), Inches(0.4), NN_ORANGE)
    add_textbox(slide, "Example squads for NN Greece",
                Inches(6.55), Inches(1.32), Inches(6.0), Inches(0.35),
                font_size=13, bold=True, color=WHITE)
    examples = [
        ("Motor Claims Squad", "End-to-end motor claims journey"),
        ("New Business Squad", "Quote-to-policy digital flow"),
        ("Customer 360 Squad", "Unified customer data & self-service portal"),
        ("Broker Connect Squad", "Broker portal and integrations"),
        ("Core Platform Squad", "Shared infrastructure & APIs"),
    ]
    y = Inches(1.75)
    for name, desc in examples:
        add_rect(slide, Inches(6.5), y, Inches(6.3), Inches(0.5),
                 NN_LIGHT if examples.index((name, desc)) % 2 == 0 else WHITE)
        add_textbox(slide, name, Inches(6.55), y + Pt(3), Inches(2.5), Inches(0.42),
                    font_size=12, bold=True, color=NN_MID)
        add_textbox(slide, desc, Inches(9.1), y + Pt(3), Inches(3.6), Inches(0.42),
                    font_size=12, color=NN_DARK)
        y += Inches(0.52)


def slide_tribe(slide):
    points = [
        "Cluster of squads on related customer journeys — typically 40–150 people",
        "Led by a Tribe Lead: servant leader, not a delivery manager",
        "Governed by a Tribe Council (all POs + Chapter Leads): aligns priorities without micromanaging",
        "Budget allocated at tribe level — not per project",
    ]
    bullet_box(slide, points, Inches(0.5), Inches(1.3), Inches(12.2), Inches(1.8),
               font_size=14, color=NN_DARK)

    headers = ["Tribe", "Focus", "Approx. Squads"]
    rows = [
        ["Personal Lines Tribe", "Motor, Home, Travel products", "4–5 squads"],
        ["Claims Experience Tribe", "All claims journeys across products", "3–4 squads"],
        ["Digital & Data Tribe", "Digital channels, analytics, AI/ML", "3–4 squads"],
        ["Operations & Compliance Tribe", "Policy admin, regulatory, finance ops", "3–4 squads"],
    ]
    col_w = [Inches(4.0), Inches(5.2), Inches(2.8)]
    two_col_table(slide, headers, rows,
                  left=Inches(0.5), top=Inches(3.3), width=Inches(12.0),
                  height=Inches(2.4), col_widths=col_w, font_size=13)

    add_textbox(slide,
                "At 600 people, NN Greece would have 3–4 tribes — significantly simpler than ING Netherlands (13 tribes).",
                Inches(0.5), Inches(6.9), Inches(12.2), Inches(0.4),
                font_size=12, italic=True, color=GREY_TEXT)


def slide_chapter(slide):
    add_rect(slide, Inches(0.5), Inches(1.3), Inches(12.2), Inches(0.9), NN_MID)
    add_textbox(slide,
                "The Chapter solves the 'who develops my people?' problem when everyone is in a cross-functional squad.",
                Inches(0.7), Inches(1.38), Inches(11.8), Inches(0.75),
                font_size=14, italic=True, color=WHITE)

    points = [
        "Groups people with the same skill across all squads within a tribe",
        "Led by a Chapter Lead who is also a working member of one squad (not a full-time manager)",
        "Chapter Lead handles: career development, hiring, craft standards, skill-building",
        "Squad handles: day-to-day work assignments and priorities",
    ]
    bullet_box(slide, points, Inches(0.5), Inches(2.4), Inches(12.2), Inches(2.0),
               font_size=14, color=NN_DARK)

    add_textbox(slide, "Example Chapters for NN Greece",
                Inches(0.5), Inches(4.6), Inches(5.0), Inches(0.35),
                font_size=13, bold=True, color=NN_ORANGE)
    chapters = ["Engineering (per tribe)", "Data & Analytics", "Product Management",
                "Claims Operations", "Compliance & Risk"]
    bullet_box(slide, chapters, Inches(0.5), Inches(5.0), Inches(5.5), Inches(1.6),
               font_size=13, color=NN_DARK)

    add_textbox(slide,
                "Key innovation:\nSeparates people development\nfrom work management.",
                Inches(7.5), Inches(4.5), Inches(5.0), Inches(1.5),
                font_size=16, bold=True, color=NN_ORANGE, align=PP_ALIGN.CENTER)


def slide_guild(slide):
    add_textbox(slide,
                "The Guild is an organic, cross-tribe community of practice.",
                Inches(0.5), Inches(1.25), Inches(12.0), Inches(0.4),
                font_size=16, bold=True, color=NN_DARK)

    left_pts = [
        "Completely voluntary — anyone across the entire organisation can join",
        "No budget, no formal authority — driven by passion and shared interest",
        "Spreads knowledge, avoids reinventing the wheel, builds culture",
        "Meets regularly: monthly sessions, Slack channels, demo days",
    ]
    bullet_box(slide, left_pts, Inches(0.5), Inches(1.8), Inches(6.0), Inches(2.2),
               font_size=14, color=NN_DARK)

    guilds = [
        "Agile Practices Guild",
        "Security & Privacy Guild",
        "Customer Experience Guild",
        "AI & Automation Guild",
        "Accessibility & Inclusive Design Guild",
        "Greek Insurance Regulation Guild",
    ]
    add_textbox(slide, "Example Guilds for NN Greece",
                Inches(7.0), Inches(1.8), Inches(5.8), Inches(0.35),
                font_size=13, bold=True, color=NN_ORANGE)
    bullet_box(slide, guilds, Inches(7.0), Inches(2.2), Inches(5.8), Inches(2.0),
               font_size=13, color=NN_DARK)

    add_rect(slide, Inches(0.5), Inches(4.3), Inches(12.2), Inches(0.9), NN_LIGHT)
    add_textbox(slide,
                "Guilds are how NN Greece stays connected to NN Group best practices "
                "without creating formal bureaucracy.",
                Inches(0.7), Inches(4.38), Inches(11.8), Inches(0.75),
                font_size=14, italic=True, color=NN_MID, align=PP_ALIGN.CENTER)


def slide_autonomy_alignment(slide):
    # Matrix diagram
    add_rect(slide, Inches(1.2), Inches(1.3), Inches(5.5), Inches(4.8), WHITE,
             line_color=GREY_TEXT)

    mid_x = Inches(1.2) + Inches(2.75)
    mid_y = Inches(1.3) + Inches(2.4)

    # Axes
    add_rect(slide, Inches(1.2), mid_y - Pt(1), Inches(5.5), Pt(2), GREY_TEXT)
    add_rect(slide, mid_x - Pt(1), Inches(1.3), Pt(2), Inches(4.8), GREY_TEXT)

    quadrant_data = [
        (Inches(1.25), Inches(1.35), "HIGH ALIGNMENT\nLOW AUTONOMY", "Micromanaged\n(slow, no trust)", NN_MID),
        (mid_x + Pt(5), Inches(1.35), "HIGH ALIGNMENT\nHIGH AUTONOMY", "High-performing\n(THE GOAL)", NN_ORANGE),
        (Inches(1.25), mid_y + Pt(5), "LOW ALIGNMENT\nLOW AUTONOMY", "Chaos\n(no direction)", GREY_TEXT),
        (mid_x + Pt(5), mid_y + Pt(5), "LOW ALIGNMENT\nHIGH AUTONOMY", "Spinning wheels\n(misaligned effort)", NN_ACCENT),
    ]
    for x, y, label, desc, col in quadrant_data:
        add_textbox(slide, label, x, y, Inches(2.6), Inches(0.5),
                    font_size=9, bold=True, color=col)
        add_textbox(slide, desc, x, y + Inches(0.45), Inches(2.6), Inches(0.6),
                    font_size=11, color=NN_DARK)

    # Axis labels
    add_textbox(slide, "↑ ALIGNMENT", Inches(1.25), Inches(1.3), Inches(2), Inches(0.3),
                font_size=9, color=GREY_TEXT)
    add_textbox(slide, "AUTONOMY →", Inches(3.8), Inches(6.0), Inches(2), Inches(0.3),
                font_size=9, color=GREY_TEXT)

    # Right: tools for alignment
    add_textbox(slide, "Tools for alignment without micromanagement",
                Inches(7.1), Inches(1.3), Inches(5.9), Inches(0.4),
                font_size=14, bold=True, color=NN_DARK)
    tools = [
        ("Company OKRs", "Set the 'why' and 'where' for everyone"),
        ("Tribe OKRs", "Translate company direction to tribe context"),
        ("Squad OKRs", "Squad's contribution — squad chooses the 'how'"),
        ("Tribe Council syncs", "Weekly cross-squad dependency check"),
        ("Demo Days", "Visibility into what every squad is shipping"),
        ("Architecture principles", "Guardrails, not gates"),
    ]
    y = Inches(1.85)
    for tool, desc in tools:
        add_textbox(slide, tool, Inches(7.1), y, Inches(2.5), Inches(0.38),
                    font_size=12, bold=True, color=NN_ORANGE)
        add_textbox(slide, desc, Inches(9.7), y, Inches(3.5), Inches(0.38),
                    font_size=12, color=NN_DARK)
        y += Inches(0.5)

    add_textbox(slide, '"Be loosely coupled, but tightly aligned." — Spotify principle',
                Inches(0.5), Inches(6.5), Inches(12.2), Inches(0.35),
                font_size=13, italic=True, color=NN_MID, align=PP_ALIGN.CENTER)


def slide_leadership_shift(slide):
    add_rect(slide, Inches(0.5), Inches(1.25), Inches(12.2), Inches(0.5), NN_ORANGE)
    add_textbox(slide,
                "The Spotify model does not just reorganise teams — it fundamentally changes what leaders do.",
                Inches(0.6), Inches(1.3), Inches(12.0), Inches(0.4),
                font_size=14, bold=True, color=WHITE)

    headers = ["Old Leadership Behaviour", "New Leadership Behaviour"]
    rows = [
        ["Assign work to teams", "Set clear outcomes; let teams find the path"],
        ["Approve decisions", "Define decision boundaries; remove obstacles"],
        ["Protect departmental territory", "Share resources and expertise across tribes"],
        ["Manage by activity (timesheets, hours)", "Manage by outcomes (OKRs, customer metrics)"],
        ["Escalation culture", '"Disagree and commit" culture'],
        ["Annual performance review", "Continuous coaching (Chapter Lead model)"],
    ]
    col_w = [Inches(5.8), Inches(6.2)]
    two_col_table(slide, headers, rows,
                  left=Inches(0.5), top=Inches(1.95), width=Inches(12.2),
                  height=Inches(3.6), col_widths=col_w, font_size=13)

    add_rect(slide, Inches(0.5), Inches(5.8), Inches(12.2), Inches(0.8), NN_MID)
    add_textbox(slide,
                "The hardest part of the Spotify model is not the structure — it is the leadership culture shift.\n"
                "Tribes and squads fail when leaders revert to command-and-control under pressure.",
                Inches(0.7), Inches(5.88), Inches(11.8), Inches(0.7),
                font_size=13, italic=True, color=WHITE)


def slide_middle_management(slide):
    add_textbox(slide, "A common concern: What happens to my managers?",
                Inches(0.5), Inches(1.25), Inches(12.2), Inches(0.4),
                font_size=15, bold=True, color=NN_DARK)
    add_textbox(slide, "In the Spotify model, managerial functions are redistributed — not eliminated.",
                Inches(0.5), Inches(1.7), Inches(12.2), Inches(0.35),
                font_size=14, color=GREY_TEXT)

    headers = ["Traditional Manager Role", "Who does it in Spotify"]
    rows = [
        ["Sets priorities and backlog", "Product Owner"],
        ["Develops team members", "Chapter Lead"],
        ["Removes blockers", "Agile Coach / Scrum Master"],
        ["Strategic direction", "Tribe Lead"],
        ["Architecture decisions", "Tech Lead (within squad)"],
        ["Budget accountability", "Tribe Lead + Finance"],
    ]
    col_w = [Inches(6.0), Inches(6.0)]
    two_col_table(slide, headers, rows,
                  left=Inches(0.5), top=Inches(2.2), width=Inches(12.2),
                  height=Inches(3.2), col_widths=col_w, font_size=13)

    add_rect(slide, Inches(0.5), Inches(5.6), Inches(12.2), Inches(0.7), NN_LIGHT)
    add_textbox(slide,
                "Strong middle managers often become excellent Tribe Leads, Chapter Leads, or Product Owners. "
                "The transformation is an opportunity — not a threat.",
                Inches(0.7), Inches(5.67), Inches(11.8), Inches(0.6),
                font_size=13, bold=True, color=NN_MID, align=PP_ALIGN.CENTER)


def slide_nn_in_numbers(slide):
    headers = ["Metric", "NN Greece", "Spotify Guidance", "Status"]
    rows = [
        ["Total staff", "~600", "No upper limit", "✓"],
        ["Squad size", "6–10", "6–10", "✓ Match"],
        ["Max squads per tribe", "—", "8–10", "✓ Feasible"],
        ["Estimated squads", "~60–70", "Any", "✓ Feasible"],
        ["Recommended tribes", "3–4", "No limit", "✓ Simple"],
        ["Tribe size (est.)", "~120–160 per tribe", "40–150", "~ Slightly above; manageable"],
    ]
    col_w = [Inches(3.5), Inches(2.2), Inches(2.8), Inches(3.5)]
    two_col_table(slide, headers, rows,
                  left=Inches(0.5), top=Inches(1.3), width=Inches(12.2),
                  height=Inches(3.4), col_widths=col_w, font_size=13)

    add_rect(slide, Inches(0.5), Inches(4.95), Inches(12.2), Inches(0.8), NN_ORANGE)
    add_textbox(slide,
                "NN Greece is significantly simpler than ING Netherlands (3,500 people, 13 tribes) — "
                "the group reference case. Aligning NN Greece's tribe structure with NN Group's taxonomy "
                "creates natural interfaces for group-level initiatives without losing local autonomy.",
                Inches(0.7), Inches(5.03), Inches(11.8), Inches(0.7),
                font_size=13, italic=True, color=WHITE)


def slide_three_horizons(slide):
    horizons = [
        ("HORIZON 1\nPrepare & Pilot\nMonths 1–6",
         "• Leadership alignment workshops\n"
         "• OKR design: company & first tribe\n"
         "• Select pilot tribe (Digital & Data)\n"
         "• Train POs, Chapter Leads, SMs\n"
         "• First 2 sprints with coaching\n\n"
         "Outcome: One tribe running at\nbasic squad level.",
         NN_ORANGE),
        ("HORIZON 2\nScale & Stabilise\nMonths 7–18",
         "• Roll out to remaining tribes\n"
         "• Launch Tribe Councils\n"
         "• Launch first Guilds\n"
         "• Connect to NN Group agile CoP\n"
         "• Reform HR: Chapter Lead paths\n"
         "• Shift to tribe-based budgeting\n\n"
         "Outcome: Full org in new model.",
         NN_MID),
        ("HORIZON 3\nOptimise & Lead\nMonth 18+",
         "• Measure: speed, NPS, engagement\n"
         "• NN Greece as NN Group reference\n"
         "• Evolve — no framework is static\n"
         "• Extend to non-IT functions\n\n"
         "Outcome: Agile is the operating\nsystem, not a programme.",
         NN_ACCENT),
    ]
    card_w = Inches(3.9)
    card_h = Inches(5.0)
    gap = Inches(0.22)
    start_x = Inches(0.5)
    for i, (title, body, color) in enumerate(horizons):
        x = start_x + i * (card_w + gap)
        add_rect(slide, x, Inches(1.3), card_w, Inches(0.75), color)
        add_rect(slide, x, Inches(2.05), card_w, card_h - Inches(0.75), WHITE,
                 line_color=color)
        add_textbox(slide, title, x + Pt(8), Inches(1.35), card_w - Pt(14), Inches(0.65),
                    font_size=13, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
        add_textbox(slide, body, x + Pt(10), Inches(2.1), card_w - Pt(18),
                    card_h - Inches(0.95), font_size=12, color=NN_DARK)


def slide_90_days(slide):
    weeks = [
        ("Weeks 1–2", "Leadership offsite — align on vision, agree pilot tribe, appoint sponsors"),
        ("Weeks 3–4", "Map current state — teams, ownership, leads"),
        ("Weeks 5–6", "Design pilot tribe: squads, POs, Chapter Leads"),
        ("Weeks 7–8", "OKR workshop: company → tribe → squad"),
        ("Weeks 9–10", "Agile Fundamentals training for all pilot tribe members"),
        ("Weeks 11–12", "First sprint. Coaches embedded. First retrospective."),
    ]
    bar_w = Inches(10.5)
    bar_h = Inches(0.52)
    y = Inches(1.3)
    for i, (week, desc) in enumerate(weeks):
        bg = NN_ORANGE if i == 5 else (NN_LIGHT if i % 2 == 0 else WHITE)
        fc = WHITE if i == 5 else NN_DARK
        add_rect(slide, Inches(0.5), y, Inches(1.5), bar_h, NN_MID)
        add_textbox(slide, week, Inches(0.55), y + Pt(4), Inches(1.4), bar_h,
                    font_size=12, bold=True, color=WHITE)
        add_rect(slide, Inches(2.1), y, bar_w - Inches(1.6), bar_h, bg,
                 line_color=GREY_TEXT)
        add_textbox(slide, desc, Inches(2.18), y + Pt(4), bar_w - Inches(1.7), bar_h,
                    font_size=13, color=fc)
        y += bar_h + Pt(3)

    add_rect(slide, Inches(0.5), Inches(5.6), Inches(12.2), Inches(0.7), NN_ORANGE)
    add_textbox(slide,
                "Single most important decision: Who are the Product Owners?\n"
                "They are the engine of the model. Identify and develop them first.",
                Inches(0.7), Inches(5.68), Inches(11.8), Inches(0.6),
                font_size=14, bold=True, color=WHITE, align=PP_ALIGN.CENTER)


def slide_risks(slide):
    headers = ["Risk", "Likelihood", "Mitigation"]
    rows = [
        ["Leaders revert to command-and-control under pressure",
         "High",
         "Executive coaching, OKR accountability, leadership retros"],
        ['"Agile" becomes a rename of the same waterfall work',
         "High",
         "Focus on outcomes (OKRs), not activities (velocity)"],
        ["HR processes don't support the new model",
         "Medium",
         "Early HR involvement; Chapter Lead role formalised"],
        ["Compliance teams feel excluded",
         "Medium",
         "Embed compliance in squads; create a Compliance Chapter"],
        ["NN Group mandates conflict with local squad autonomy",
         "Medium",
         'Agree "group non-negotiables" upfront; everything else is squad choice'],
        ["Pilot tribe chosen poorly (too much regulatory risk)",
         "Low–Med",
         "Start with Digital & Data or a greenfield product area"],
    ]
    col_w = [Inches(5.0), Inches(1.5), Inches(5.5)]
    two_col_table(slide, headers, rows,
                  left=Inches(0.5), top=Inches(1.3), width=Inches(12.2),
                  height=Inches(4.8), col_widths=col_w, font_size=12)


def slide_success(slide):
    add_textbox(slide, "Quantitative signals (12 months)",
                Inches(0.5), Inches(1.3), Inches(6.0), Inches(0.4),
                font_size=14, bold=True, color=NN_ORANGE)
    q_points = [
        "Feature time-to-market: 6–9 months → 4–6 weeks",
        "Releases per quarter: measurable increase",
        "Employee engagement (pilot tribe): +10–15 pts vs baseline",
        "Customer NPS on digital journeys: measurable improvement",
    ]
    bullet_box(slide, q_points, Inches(0.5), Inches(1.8), Inches(6.0), Inches(2.0),
               font_size=13, color=NN_DARK)

    add_textbox(slide, "Qualitative signals",
                Inches(7.0), Inches(1.3), Inches(6.0), Inches(0.4),
                font_size=14, bold=True, color=NN_ORANGE)
    ql_points = [
        "Leaders spend more time removing obstacles than attending status meetings",
        '"We can\'t do that, it\'s another department\'s problem" is said less and less',
        "Teams talk about customer outcomes, not internal deliverables",
        "NN Group uses NN Greece as a reference for local-entity agile transformation",
    ]
    bullet_box(slide, ql_points, Inches(7.0), Inches(1.8), Inches(6.0), Inches(2.5),
               font_size=13, color=NN_DARK)

    cards = [
        ("Speed", "Features ship in weeks,\nnot quarters"),
        ("Culture", "Autonomy, trust, and\ncontinuous learning are felt"),
        ("Alignment", "Every squad links to\na company OKR"),
        ("Group value", "NN Greece is a model\nfor the group"),
    ]
    card_w = Inches(2.9)
    card_h = Inches(1.3)
    gap = Inches(0.22)
    x = Inches(0.5)
    for title, desc in cards:
        add_rect(slide, x, Inches(4.6), card_w, Inches(0.38), NN_MID)
        add_rect(slide, x, Inches(4.98), card_w, card_h - Inches(0.38), NN_LIGHT)
        add_textbox(slide, title, x + Pt(6), Inches(4.63), card_w, Inches(0.32),
                    font_size=12, bold=True, color=WHITE)
        add_textbox(slide, desc, x + Pt(6), Inches(5.02), card_w - Pt(10), Inches(0.8),
                    font_size=12, color=NN_DARK)
        x += card_w + gap


def slide_takeaways(slide):
    takeaways = [
        ("1", "Agile transformation is an operating model change, not a tooling or process change."),
        ("2", "The Spotify model gives NN Greece a proven, principle-based blueprint balancing autonomy with alignment."),
        ("3", "At 600 people, NN Greece is the ideal size — large enough to benefit, small enough to move quickly."),
        ("4", "The four building blocks (Squad, Tribe, Chapter, Guild) each serve a distinct, non-overlapping purpose."),
        ("5", "Leadership behaviour change is the single biggest success factor — and the single biggest risk."),
        ("6", "Start small, learn fast. A well-run pilot tribe in 90 days teaches more than 12 months of planning."),
        ("7", "NN Group is an asset: group agile CoPs, ING learnings, and group OKRs can accelerate the journey."),
    ]
    y = Inches(1.3)
    for num, text in takeaways:
        add_rect(slide, Inches(0.5), y, Inches(0.55), Inches(0.5), NN_ORANGE)
        add_textbox(slide, num, Inches(0.5), y + Pt(4), Inches(0.55), Inches(0.45),
                    font_size=16, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
        add_textbox(slide, text, Inches(1.2), y + Pt(5), Inches(11.5), Inches(0.42),
                    font_size=13, color=NN_DARK)
        y += Inches(0.62)


def slide_discussion(slide):
    questions = [
        "Which part of the business do you believe is most ready to pilot this model?",
        "Where do you see the biggest leadership behaviour gap between today and where we need to be?",
        "What does NN Greece need to stop doing to create space for squads to self-organise?",
        "How do we ensure compliance and regulatory obligations are embedded in the model, not bolted on as a gate?",
        'What would make you say, in 12 months, "the transformation is working"?',
    ]
    add_rect(slide, Inches(0.5), Inches(1.25), Inches(12.2), Inches(0.5), NN_MID)
    add_textbox(slide, "Discussion Questions",
                Inches(0.7), Inches(1.3), Inches(12.0), Inches(0.4),
                font_size=16, bold=True, color=WHITE)

    y = Inches(2.0)
    for i, q in enumerate(questions):
        add_rect(slide, Inches(0.5), y, Inches(0.55), Inches(0.55), NN_ORANGE)
        add_textbox(slide, str(i + 1), Inches(0.5), y + Pt(5), Inches(0.55), Inches(0.45),
                    font_size=15, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
        add_textbox(slide, q, Inches(1.2), y + Pt(6), Inches(11.5), Inches(0.5),
                    font_size=14, color=NN_DARK)
        y += Inches(0.72)


def slide_appendix_reading(slide):
    headers = ["Resource", "Why it's relevant"]
    rows = [
        ["Spotify Engineering Culture videos\n(Henrik Kniberg, 2014)",
         "The original articulation — watch before any leadership session"],
        ["Accelerate — Forsgren, Humble, Kim",
         "Data-driven proof of what high-performing agile organisations do differently"],
        ["ING Bank Agile Transformation Case Study\n(McKinsey, 2017)",
         "Closest real-world parallel; financial services, Netherlands, NN Group territory"],
        ["Team Topologies — Skelton & Pais",
         "Modern evolution of squad/tribe thinking; directly compatible with Spotify model"],
        ["NN Group Agile Community of Practice",
         "Internal resource — connect with group agile leads early in the journey"],
    ]
    col_w = [Inches(5.5), Inches(6.5)]
    two_col_table(slide, headers, rows,
                  left=Inches(0.5), top=Inches(1.3), width=Inches(12.2),
                  height=Inches(4.5), col_widths=col_w, font_size=13)


def slide_glossary(slide):
    terms = [
        ("Squad", "Small (6–10), fully cross-functional, autonomous team owning one product area"),
        ("Tribe", "Cluster of related squads (40–150 people) sharing a customer domain"),
        ("Chapter", "Horizontal grouping of same-discipline people within a tribe"),
        ("Guild", "Voluntary, cross-tribe community of practice around a shared interest or skill"),
        ("Product Owner", "Sets squad priorities; accountable for squad's business outcomes"),
        ("Chapter Lead", "Part-time manager of a skill chapter; also a working member of a squad"),
        ("Tribe Lead", "Servant leader of a tribe; removes obstacles, sets tribe direction"),
        ("OKR", "Objectives and Key Results — goal-setting framework linking company, tribe, and squad"),
        ("Scrum Master / Agile Coach", "Facilitates squad ceremonies; coaches the team on agile practices"),
    ]
    y = Inches(1.3)
    col_break = 5
    for i, (term, defn) in enumerate(terms):
        col = 0 if i < col_break else 1
        row = i if i < col_break else i - col_break
        x = Inches(0.5) if col == 0 else Inches(6.8)
        cy = Inches(1.3) + row * Inches(0.95)
        add_rect(slide, x, cy, Inches(6.0), Inches(0.35), NN_ORANGE if col == 0 else NN_MID)
        add_textbox(slide, term, x + Pt(6), cy + Pt(3), Inches(5.8), Inches(0.3),
                    font_size=12, bold=True, color=WHITE)
        add_textbox(slide, defn, x + Pt(6), cy + Inches(0.37), Inches(5.8), Inches(0.5),
                    font_size=11, color=NN_DARK)


def closing_slide(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_rect(slide, 0, 0, SLIDE_W, SLIDE_H, NN_DARK)
    add_rect(slide, 0, 0, Inches(0.18), SLIDE_H, NN_ORANGE)
    add_rect(slide, 0, SLIDE_H - Inches(0.12), SLIDE_W, Inches(0.12), NN_ORANGE)

    add_textbox(slide, "Thank you", Inches(0.5), Inches(2.2), Inches(12), Inches(1.2),
                font_size=48, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
    add_textbox(slide, "Let's start the conversation.",
                Inches(0.5), Inches(3.5), Inches(12), Inches(0.6),
                font_size=22, italic=True, color=NN_ORANGE, align=PP_ALIGN.CENTER)
    add_rect(slide, Inches(4.5), Inches(4.3), Inches(4.3), Pt(2), NN_ACCENT)
    add_textbox(slide, "[Your Name] · Agile Coach",
                Inches(0.5), Inches(4.7), Inches(12), Inches(0.4),
                font_size=14, color=WHITE, align=PP_ALIGN.CENTER)
    add_textbox(slide, "NN Greece · Agile Transformation · April 2026",
                Inches(0.5), Inches(5.15), Inches(12), Inches(0.4),
                font_size=13, color=GREY_TEXT, align=PP_ALIGN.CENTER)
    return slide


# ── Main build ─────────────────────────────────────────────────────────────────

def build():
    prs = Presentation()
    prs.slide_width = SLIDE_W
    prs.slide_height = SLIDE_H

    add_title_slide(prs,
                    "Agile Transformation at NN Greece",
                    "Understanding the Spotify Scaling Model")

    content_slide(prs, "Agenda", slide_agenda)
    content_slide(prs, "A Word Before We Start", slide_word_before)
    content_slide(prs, "What is an Agile Transformation?", slide_what_is)

    add_section_divider(prs, "PART 1", "Why Now, Why Spotify?",
                        "Context for NN Greece")

    content_slide(prs, "Why Now for NN Greece?", slide_why_now)
    content_slide(prs, "Why the Spotify Model?", slide_why_spotify)

    add_section_divider(prs, "PART 2", "The Four Building Blocks",
                        "Squad · Tribe · Chapter · Guild")

    content_slide(prs, "Overview — The Four Building Blocks", slide_four_blocks_overview)
    content_slide(prs, "The Squad — Primary Unit of Delivery", slide_squad)
    content_slide(prs, "The Tribe — Cluster of Related Squads", slide_tribe)
    content_slide(prs, "The Chapter — People Development Without Line Management", slide_chapter)
    content_slide(prs, "The Guild — Cross-Tribe Communities of Practice", slide_guild)

    add_section_divider(prs, "PART 3", "Autonomy with Alignment",
                        "How it holds together")

    content_slide(prs, "Autonomy with Alignment — The Key Tension", slide_autonomy_alignment)

    add_section_divider(prs, "PART 4", "Leadership & Culture Shift",
                        "The hardest — and most important — part")

    content_slide(prs, "What Changes for Leadership?", slide_leadership_shift)
    content_slide(prs, "What About Middle Management?", slide_middle_management)

    add_section_divider(prs, "PART 5", "NN Greece in Practice",
                        "Numbers, roadmap, risks")

    content_slide(prs, "NN Greece in Numbers — Is It Feasible?", slide_nn_in_numbers)
    content_slide(prs, "The Transformation Journey — 3 Horizons", slide_three_horizons)
    content_slide(prs, "Where to Start — The First 90 Days", slide_90_days)
    content_slide(prs, "Risks and How to Mitigate Them", slide_risks)
    content_slide(prs, "What Success Looks Like (12 Months)", slide_success)

    add_section_divider(prs, "PART 6", "Takeaways & Discussion", "")

    content_slide(prs, "Key Takeaways", slide_takeaways)
    content_slide(prs, "Discussion Questions", slide_discussion)

    closing_slide(prs)

    add_section_divider(prs, "APPENDIX", "Further Reading & Glossary", "")
    content_slide(prs, "Appendix A — Further Reading", slide_appendix_reading)
    content_slide(prs, "Appendix B — Glossary", slide_glossary)

    out_path = os.path.join(os.path.dirname(__file__),
                            "nn-greece-agile-transformation-spotify-model.pptx")
    prs.save(out_path)
    print(f"Saved: {out_path}")
    print(f"Slides: {len(prs.slides)}")


if __name__ == "__main__":
    build()
