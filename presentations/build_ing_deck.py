"""
Build the ING Bank Agile Transformation deck (McKinsey case study) as a .pptx file.
Tailored for NN Greece as the audience.
Run with:  python build_ing_deck.py
Output:    nn-greece-ing-agile-transformation.pptx (same folder)
"""

from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
import os

# ── Palette ────────────────────────────────────────────────────────────────────
ING_ORANGE   = RGBColor(0xFF, 0x60, 0x00)   # ING brand orange
NN_ORANGE    = RGBColor(0xF0, 0x6A, 0x00)   # NN Group orange (close but distinct)
DARK_NAVY    = RGBColor(0x1A, 0x1A, 0x2E)
MID_BLUE     = RGBColor(0x2E, 0x4A, 0x6E)
TEAL         = RGBColor(0x00, 0xAA, 0xCC)
LIGHT_BG     = RGBColor(0xF4, 0xF6, 0xF9)
WHITE        = RGBColor(0xFF, 0xFF, 0xFF)
GREY         = RGBColor(0x55, 0x55, 0x55)
GREEN        = RGBColor(0x00, 0x96, 0x58)
AMBER        = RGBColor(0xFF, 0xBF, 0x00)

SLIDE_W = Inches(13.33)
SLIDE_H = Inches(7.5)

# ── Primitive helpers ──────────────────────────────────────────────────────────

def rect(slide, l, t, w, h, fill, line=None, line_w=Pt(1)):
    s = slide.shapes.add_shape(1, l, t, w, h)
    s.fill.solid()
    s.fill.fore_color.rgb = fill
    if line:
        s.line.color.rgb = line
        s.line.width = line_w
    else:
        s.line.fill.background()
    return s


def tb(slide, text, l, t, w, h, size=14, bold=False, italic=False,
       color=DARK_NAVY, align=PP_ALIGN.LEFT, wrap=True):
    box = slide.shapes.add_textbox(l, t, w, h)
    tf = box.text_frame
    tf.word_wrap = wrap
    p = tf.paragraphs[0]
    p.alignment = align
    run = p.add_run()
    run.text = text
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = color
    return box


def bullets(slide, items, l, t, w, h, size=14, color=DARK_NAVY,
            bullet="•", spacing=3):
    box = slide.shapes.add_textbox(l, t, w, h)
    tf = box.text_frame
    tf.word_wrap = True
    first = True
    for item in items:
        p = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        p.space_before = Pt(spacing)
        run = p.add_run()
        run.text = (bullet + "  " if bullet else "") + item
        run.font.size = Pt(size)
        run.font.color.rgb = color
    return box


def table(slide, headers, rows, l, t, w, h,
          col_widths=None, hdr_bg=DARK_NAVY, even_bg=LIGHT_BG,
          odd_bg=WHITE, size=13):
    nc = len(headers)
    cw = col_widths or [w // nc] * nc
    rh = h // (len(rows) + 1)
    x = l
    for i, h_ in enumerate(headers):
        rect(slide, x, t, cw[i], rh, hdr_bg)
        tb(slide, h_, x + Pt(5), t + Pt(3), cw[i] - Pt(8), rh,
           size=size, bold=True, color=WHITE)
        x += cw[i]
    for ri, row in enumerate(rows):
        bg = even_bg if ri % 2 == 0 else odd_bg
        x = l
        y = t + rh * (ri + 1)
        for ci, cell in enumerate(row):
            rect(slide, x, y, cw[ci], rh, bg)
            tb(slide, cell, x + Pt(5), y + Pt(3), cw[ci] - Pt(8), rh,
               size=size - 1, color=DARK_NAVY)
            x += cw[ci]


def card(slide, title, body, l, t, w, h, accent=ING_ORANGE, size=12):
    rect(slide, l, t, w, Inches(0.4), accent)
    rect(slide, l, t + Inches(0.4), w, h - Inches(0.4), WHITE, line=accent)
    tb(slide, title, l + Pt(7), t + Pt(4), w - Pt(12), Inches(0.36),
       size=12, bold=True, color=WHITE)
    tb(slide, body, l + Pt(7), t + Inches(0.44), w - Pt(12), h - Inches(0.52),
       size=size, color=DARK_NAVY, wrap=True)

# ── Slide chrome ───────────────────────────────────────────────────────────────

def chrome(slide, title_text):
    """Standard content slide chrome (header, footer, background)."""
    rect(slide, 0, 0, SLIDE_W, SLIDE_H, LIGHT_BG)
    rect(slide, 0, 0, SLIDE_W, Inches(1.05), DARK_NAVY)
    rect(slide, 0, Inches(1.05), SLIDE_W, Pt(3), ING_ORANGE)
    tb(slide, title_text, Inches(0.4), Inches(0.12), Inches(12.4), Inches(0.85),
       size=24, bold=True, color=WHITE)
    rect(slide, 0, SLIDE_H - Inches(0.35), SLIDE_W, Inches(0.35), DARK_NAVY)
    tb(slide, "NN Greece · ING Bank Agile Case Study (McKinsey, 2017)",
       Inches(0.3), SLIDE_H - Inches(0.32), Inches(9), Inches(0.28),
       size=9, color=GREY)
    tb(slide, "CONFIDENTIAL",
       Inches(10.5), SLIDE_H - Inches(0.32), Inches(2.5), Inches(0.28),
       size=9, color=GREY, align=PP_ALIGN.RIGHT)


def divider(prs, part_no, title, sub=""):
    sl = prs.slides.add_slide(prs.slide_layouts[6])
    rect(sl, 0, 0, SLIDE_W, SLIDE_H, MID_BLUE)
    rect(sl, 0, 0, Inches(0.18), SLIDE_H, ING_ORANGE)
    rect(sl, 0, SLIDE_H - Inches(0.12), SLIDE_W, Inches(0.12), ING_ORANGE)
    tb(sl, part_no, Inches(0.5), Inches(2.0), Inches(12), Inches(1.1),
       size=60, bold=True, color=ING_ORANGE, align=PP_ALIGN.CENTER)
    tb(sl, title, Inches(0.5), Inches(3.2), Inches(12), Inches(0.9),
       size=28, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
    if sub:
        tb(sl, sub, Inches(0.5), Inches(4.2), Inches(12), Inches(0.55),
           size=15, color=GREY, align=PP_ALIGN.CENTER)
    return sl


def content(prs, title, fn):
    sl = prs.slides.add_slide(prs.slide_layouts[6])
    chrome(sl, title)
    fn(sl)
    return sl

# ── Slide content functions ────────────────────────────────────────────────────

def s_title(prs):
    sl = prs.slides.add_slide(prs.slide_layouts[6])
    rect(sl, 0, 0, SLIDE_W, SLIDE_H, DARK_NAVY)
    rect(sl, 0, 0, Inches(0.18), SLIDE_H, ING_ORANGE)
    rect(sl, 0, SLIDE_H - Inches(0.12), SLIDE_W, Inches(0.12), ING_ORANGE)
    # ING orange stripe across middle
    rect(sl, Inches(0.18), Inches(3.6), SLIDE_W - Inches(0.18), Pt(2), ING_ORANGE)
    tb(sl, "NN Group", SLIDE_W - Inches(2.2), Inches(0.3), Inches(2.0), Inches(0.5),
       size=13, bold=True, color=ING_ORANGE, align=PP_ALIGN.RIGHT)
    tb(sl, "Greece", SLIDE_W - Inches(2.2), Inches(0.72), Inches(2.0), Inches(0.4),
       size=11, color=WHITE, align=PP_ALIGN.RIGHT)
    tb(sl, "Agile Transformation at NN Greece",
       Inches(0.5), Inches(1.5), Inches(12.0), Inches(1.2),
       size=38, bold=True, color=WHITE)
    tb(sl, "Lessons from the ING Bank Transformation",
       Inches(0.5), Inches(2.8), Inches(10.0), Inches(0.7),
       size=22, color=ING_ORANGE)
    tb(sl, "Based on: ING's agile transformation — McKinsey Quarterly, 2017",
       Inches(0.5), Inches(3.75), Inches(10.0), Inches(0.4),
       size=13, italic=True, color=GREY)
    rect(sl, Inches(0.5), Inches(4.4), Inches(4.5), Pt(1.5), TEAL)
    tb(sl, "[Your Name], Agile Coach",
       Inches(0.5), Inches(4.7), Inches(8.0), Inches(0.38), size=14, color=WHITE)
    tb(sl, "April 2026",
       Inches(0.5), Inches(5.1), Inches(8.0), Inches(0.35), size=13, color=GREY)
    tb(sl, "Prepared for: NN Greece — Leadership & Change Champions",
       Inches(0.5), Inches(5.5), Inches(9.0), Inches(0.35),
       size=12, italic=True, color=GREY)


def s_agenda(sl):
    items = [
        "1.  Why the ING story matters for NN Greece",
        "2.  The context: ING in 2014 — why transform?",
        "3.  The model: squads, tribes, chapters, and alliances",
        "4.  What made ING different from Spotify",
        "5.  The HR and leadership transformation",
        "6.  Results: what ING actually achieved",
        "7.  What NN Greece can directly apply",
        "8.  The transformation journey — 3 horizons",
        "9.  Where to start: the first 90 days",
        "10. Risks and how to mitigate them",
        "11. Discussion",
    ]
    bullets(sl, items, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.8),
            size=16, bullet="", color=DARK_NAVY)


def s_why_ing(sl):
    rect(sl, Inches(0.5), Inches(1.3), Inches(12.2), Inches(0.55), MID_BLUE)
    tb(sl, "Of the dozens of agile-at-scale case studies available, ING is the most directly relevant to NN Greece.",
       Inches(0.65), Inches(1.37), Inches(11.8), Inches(0.44),
       size=14, italic=True, color=WHITE)

    reasons = [
        ("Dutch financial institution",
         "ING is headquartered in Amsterdam. The regulatory environment (DNB, ECB, GDPR) and market context are nearly identical to NN Group."),
        ("Financial services — not a tech company",
         "Unlike Spotify (music streaming), ING had legacy systems, strict compliance obligations, and a traditional workforce — exactly like NN Greece."),
        ("NN Group connection",
         "ING and NN Group are institutionally adjacent. ING's lessons are directly available through NN Group's agile community of practice."),
        ("Documented at scale",
         "McKinsey published a detailed 2017 case study. The results, failures, and critical success factors are publicly available — not proprietary."),
        ("Proven in retail and commercial banking",
         "Products like mortgages, insurance, and everyday banking are structurally similar to NN's personal lines, claims, and broker operations."),
    ]
    y = Inches(2.05)
    for i, (title, body) in enumerate(reasons):
        bg = LIGHT_BG if i % 2 == 0 else WHITE
        rect(sl, Inches(0.5), y, Inches(12.2), Inches(0.62), bg)
        rect(sl, Inches(0.5), y, Pt(5), Inches(0.62), ING_ORANGE)
        tb(sl, title, Inches(0.65), y + Pt(3), Inches(3.0), Inches(0.3),
           size=13, bold=True, color=ING_ORANGE)
        tb(sl, body, Inches(3.75), y + Pt(3), Inches(8.8), Inches(0.5),
           size=13, color=DARK_NAVY)
        y += Inches(0.65)


def s_ing_context(sl):
    # Left: ING snapshot
    tb(sl, "ING Bank Netherlands — 2014 snapshot",
       Inches(0.5), Inches(1.3), Inches(5.8), Inches(0.38),
       size=14, bold=True, color=DARK_NAVY)
    snap = [
        "~52,000 employees globally",
        "~3,500 in-scope for initial transformation (Amsterdam HQ)",
        "Traditional siloed IT / business structure",
        "6–12 month delivery cycles for digital features",
        "Multiple competing priorities, no single backlog",
        "Customer NPS lagging behind fintech challengers",
        "Legacy core banking systems running in parallel",
    ]
    bullets(sl, snap, Inches(0.5), Inches(1.8), Inches(5.8), Inches(3.0),
            size=13, color=DARK_NAVY)

    # Right: The burning platform
    rect(sl, Inches(7.0), Inches(1.25), Inches(5.8), Inches(0.42), ING_ORANGE)
    tb(sl, "The burning platform",
       Inches(7.1), Inches(1.3), Inches(5.5), Inches(0.32),
       size=14, bold=True, color=WHITE)
    platform = [
        "ING N26, Revolut, and Monzo were acquiring customers at near-zero marginal cost",
        "Customers expected real-time digital banking — ING took weeks to release features",
        "Internal surveys showed low employee engagement and high frustration with bureaucracy",
        "Board commitment: 'We must become a tech company that happens to do banking'",
    ]
    bullets(sl, platform, Inches(7.0), Inches(1.8), Inches(5.8), Inches(2.2),
            size=13, color=DARK_NAVY)

    # The decision
    rect(sl, Inches(0.5), Inches(5.1), Inches(12.2), Inches(1.05), DARK_NAVY)
    tb(sl, "The decision (2015):",
       Inches(0.7), Inches(5.18), Inches(2.5), Inches(0.35),
       size=13, bold=True, color=ING_ORANGE)
    tb(sl, "Dissolve all traditional departments at Amsterdam HQ. "
           "Every employee reapplies for a role in the new structure. "
           "~5,800 roles eliminated; ~6,000 new roles created. "
           "Go live in one weekend — September 2015.",
       Inches(3.2), Inches(5.18), Inches(9.0), Inches(0.9),
       size=13, italic=True, color=WHITE)


def s_model_overview(sl):
    # Four building blocks as cards
    blocks = [
        ("SQUAD", "6–10 people\nCross-functional\nOne customer journey\nFull autonomy on 'how'", ING_ORANGE),
        ("TRIBE", "Collection of squads\nOne business domain\n40–150 people\nTribe Lead + Tribe Council", MID_BLUE),
        ("CHAPTER", "Same discipline\nWithin a tribe\nCareer development\nChapter Lead model", TEAL),
        ("ALLIANCE", "ING-specific layer\nMultiple tribes\nMajor customer journey\nStrategic alignment", GREEN),
    ]
    cw = Inches(2.95)
    ch = Inches(3.6)
    gap = Inches(0.2)
    sx = Inches(0.5)
    for i, (title, body, col) in enumerate(blocks):
        x = sx + i * (cw + gap)
        rect(sl, x, Inches(1.3), cw, Inches(0.55), col)
        rect(sl, x, Inches(1.85), cw, ch - Inches(0.55), WHITE, line=col)
        tb(sl, title, x + Pt(6), Inches(1.34), cw - Pt(10), Inches(0.46),
           size=15, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
        tb(sl, body, x + Pt(10), Inches(1.92), cw - Pt(18), ch - Inches(0.72),
           size=13, color=DARK_NAVY)

    rect(sl, Inches(0.5), Inches(5.1), Inches(12.2), Inches(0.7), LIGHT_BG)
    tb(sl, "Key difference from Spotify:  "
           "ING added the 'Alliance' layer above tribes to handle large, cross-tribe customer journeys "
           "(e.g. 'Daily Banking', 'Home Ownership') — absent in the original Spotify model.",
       Inches(0.7), Inches(5.18), Inches(11.8), Inches(0.58),
       size=13, italic=True, color=MID_BLUE, align=PP_ALIGN.CENTER)


def s_squad(sl):
    tb(sl, "The Squad — unchanged from Spotify",
       Inches(0.5), Inches(1.25), Inches(8), Inches(0.38),
       size=15, bold=True, color=DARK_NAVY)

    left = [
        "6–10 people, fully cross-functional (dev, test, data, UX, ops, compliance)",
        "Owns one customer journey end-to-end",
        "One Product Owner (PO) holds the backlog and outcome accountability",
        "One Agile Coach embedded (not shared across squads at ING)",
        '"You build it, you run it" — DevOps ownership, no hand-off to ops',
        "Chooses its own ways of working (Scrum, Kanban, or hybrid)",
        "Can release to production independently, without another squad's approval",
    ]
    bullets(sl, left, Inches(0.5), Inches(1.75), Inches(6.0), Inches(3.8),
            size=13, color=DARK_NAVY)

    rect(sl, Inches(6.8), Inches(1.25), Inches(6.0), Inches(0.38), ING_ORANGE)
    tb(sl, "Example squads for NN Greece",
       Inches(6.88), Inches(1.28), Inches(5.8), Inches(0.32),
       size=13, bold=True, color=WHITE)
    examples = [
        ("Motor Claims Squad", "End-to-end motor claim journey"),
        ("New Business Squad", "Quote-to-policy digital flow"),
        ("Customer 360 Squad", "Unified customer data & self-service"),
        ("Broker Connect Squad", "Broker portal and API integrations"),
        ("Payments & Collections Squad", "Premium collection and refund flows"),
        ("Core Platform Squad", "Shared APIs and infrastructure"),
    ]
    y = Inches(1.72)
    for j, (name, desc) in enumerate(examples):
        bg = LIGHT_BG if j % 2 == 0 else WHITE
        rect(sl, Inches(6.8), y, Inches(6.0), Inches(0.5), bg)
        tb(sl, name, Inches(6.88), y + Pt(3), Inches(2.6), Inches(0.42),
           size=12, bold=True, color=MID_BLUE)
        tb(sl, desc, Inches(9.55), y + Pt(3), Inches(3.1), Inches(0.42),
           size=12, color=DARK_NAVY)
        y += Inches(0.52)


def s_tribe(sl):
    left = [
        "Typically 40–150 people (ING averaged ~120 per tribe)",
        "Led by a Tribe Lead — servant leader, not a delivery manager",
        "Governed by a Tribe Council: all Squad POs + Chapter Leads",
        "Budget allocated at tribe level, not per project",
        "ING had ~13 tribes across Amsterdam HQ at launch",
        "Physical co-location: each tribe occupies its own floor/wing",
        "The Tribe Council reviews OKR progress weekly, not daily",
    ]
    bullets(sl, left, Inches(0.5), Inches(1.3), Inches(6.0), Inches(3.5),
            size=13, color=DARK_NAVY)

    headers = ["Proposed Tribe", "Domain", "~Squads"]
    rows = [
        ["Personal Lines Tribe", "Motor, Home, Travel products", "4–5"],
        ["Claims Experience Tribe", "All claims journeys", "3–4"],
        ["Digital & Data Tribe", "Channels, analytics, AI/ML", "3–4"],
        ["Operations & Compliance Tribe", "Policy admin, regulatory, finance", "3–4"],
    ]
    cw = [Inches(4.2), Inches(4.8), Inches(1.8)]
    table(sl, headers, rows,
          Inches(6.8), Inches(1.3), Inches(10.8), Inches(2.5),
          col_widths=cw, size=13)

    rect(sl, Inches(0.5), Inches(5.05), Inches(12.2), Inches(0.7), DARK_NAVY)
    tb(sl, "ING insight:  "
           "Physical co-location was non-negotiable at ING — tribes that were geographically split "
           "consistently underperformed. For NN Greece, consider which building layout enables this.",
       Inches(0.7), Inches(5.13), Inches(11.8), Inches(0.58),
       size=13, italic=True, color=WHITE)


def s_chapter(sl):
    rect(sl, Inches(0.5), Inches(1.25), Inches(12.2), Inches(0.55), MID_BLUE)
    tb(sl, "The Chapter solved ING's hardest problem: "
           "who manages people when there are no departments?",
       Inches(0.65), Inches(1.32), Inches(11.8), Inches(0.44),
       size=14, italic=True, color=WHITE)

    left = [
        "Groups people with the same skill across squads within a tribe",
        "Led by a Chapter Lead — working member of one squad AND people manager",
        "Chapter Lead responsibilities: career dev, hiring, performance, craft standards",
        "At ING: Chapter Leads went through a rigorous new selection process",
        "Traditional managers who did not become Chapter Leads or POs left the organisation",
        "Chapter sizes at ING: typically 10–25 people",
    ]
    bullets(sl, left, Inches(0.5), Inches(2.0), Inches(6.0), Inches(2.8),
            size=13, color=DARK_NAVY)

    tb(sl, "ING Chapter examples",
       Inches(7.0), Inches(2.0), Inches(5.8), Inches(0.38),
       size=13, bold=True, color=ING_ORANGE)
    chapters = [
        "Software Engineering", "Data Engineering", "Product Management",
        "UX & Design", "Risk & Compliance", "Business Analysis",
        "Agile Coaching", "IT Operations / SRE",
    ]
    bullets(sl, chapters, Inches(7.0), Inches(2.5), Inches(5.8), Inches(2.0),
            size=13, color=DARK_NAVY)

    rect(sl, Inches(0.5), Inches(5.05), Inches(12.2), Inches(0.7), LIGHT_BG)
    tb(sl, "Critical ING learning:  "
           "Chapter Lead selection was the single most visible signal of cultural change. "
           "Appointing the wrong people (technical experts without coaching mindset) "
           "caused significant morale problems in the first year.",
       Inches(0.7), Inches(5.12), Inches(11.8), Inches(0.58),
       size=13, italic=True, color=MID_BLUE)


def s_alliance(sl):
    rect(sl, Inches(0.5), Inches(1.25), Inches(12.2), Inches(0.55), ING_ORANGE)
    tb(sl, "The Alliance — ING's own addition. Not in the Spotify model.",
       Inches(0.65), Inches(1.32), Inches(11.8), Inches(0.44),
       size=15, bold=True, color=WHITE)

    left = [
        "An Alliance groups multiple tribes that work on a major end-to-end customer journey",
        "Provides a coordination layer without adding bureaucracy",
        "Led by an Alliance Lead — a very senior, cross-functional leader",
        "The Alliance Council meets monthly to resolve cross-tribe dependencies",
        "Budget is set at Alliance level; distributed to tribes; tribes distribute to squads",
        "ING launched with 3 main Alliances at HQ",
    ]
    bullets(sl, left, Inches(0.5), Inches(2.0), Inches(6.2), Inches(2.8),
            size=13, color=DARK_NAVY)

    alliances_ing = [
        ("Daily Banking Alliance",
         "Tribes responsible for\ncurrent accounts, payments,\nmobile banking app"),
        ("Home Ownership Alliance",
         "Tribes covering mortgages,\nhome insurance, and\nproperty data services"),
        ("Investments Alliance",
         "Tribes for savings, investment\nproducts, and wealth\nmanagement journeys"),
    ]
    cw2 = Inches(1.9)
    ch2 = Inches(2.5)
    gap2 = Inches(0.18)
    sx = Inches(7.0)
    tb(sl, "ING's three launch Alliances",
       sx, Inches(2.0), Inches(5.9), Inches(0.35),
       size=13, bold=True, color=DARK_NAVY)
    for i, (title, body) in enumerate(alliances_ing):
        card(sl, title, body,
             sx + i * (cw2 + gap2), Inches(2.45),
             cw2, ch2, accent=GREEN, size=12)

    rect(sl, Inches(0.5), Inches(5.05), Inches(12.2), Inches(0.9), DARK_NAVY)
    tb(sl, "For NN Greece:  "
           "An Alliance layer makes sense once 3+ tribes are running. "
           "Candidate: a 'Protection Alliance' spanning Personal Lines, Claims, and Digital — "
           "the end-to-end customer protection journey from quote through claim settlement.",
       Inches(0.7), Inches(5.12), Inches(11.8), Inches(0.78),
       size=13, italic=True, color=WHITE)


def s_ing_vs_spotify(sl):
    headers = ["Dimension", "Spotify Model", "ING Model"]
    rows = [
        ["Origin", "Music streaming, Silicon Valley culture", "Retail banking, Dutch financial services"],
        ["Alliance layer", "Not present", "Yes — groups of tribes for major journeys"],
        ["Compliance embedding", "Not specifically addressed", "Dedicated Risk & Compliance Chapter; embedded in squads"],
        ["HR transformation", "Described in principle", "Full reapplication process; ~5,800 roles dissolved"],
        ["Physical layout", "Recommended but flexible", "Non-negotiable; tribes co-locate on dedicated floors"],
        ["Agile Coach", "Shared, chapter-level", "One dedicated coach per squad at launch"],
        ["Legacy systems", "Not a constraint", "Explicitly managed — 'strangler fig' pattern"],
        ["Scale of change", "Gradual, opt-in", "Big-bang: entire HQ restructured over one weekend"],
        ["OKRs", "Implied", "Formally deployed at company, tribe, and squad level"],
    ]
    cw = [Inches(2.4), Inches(4.6), Inches(5.0)]
    table(sl, headers, rows,
          Inches(0.4), Inches(1.3), Inches(12.4), Inches(5.5),
          col_widths=cw, size=12)


def s_hr_transformation(sl):
    rect(sl, Inches(0.5), Inches(1.25), Inches(12.2), Inches(0.55), ING_ORANGE)
    tb(sl, "ING's HR transformation was as radical as the structural transformation — "
           "arguably more so.",
       Inches(0.65), Inches(1.32), Inches(11.8), Inches(0.44),
       size=14, italic=True, color=WHITE)

    steps = [
        ("All roles dissolved", "Every employee at Amsterdam HQ had their role formally eliminated. No grandfathering."),
        ("New roles designed", "~6,000 new roles created: Product Owner, Chapter Lead, Tribe Lead, Agile Coach, Engineer."),
        ("Open application", "Every employee applied for roles in the new structure in an open, merit-based process."),
        ("External hiring", "~30% of roles filled externally — skills the organisation did not have (digital POs, data engineers)."),
        ("Voluntary departure", "~1,200 employees chose not to reapply and left with a package. Culture change by attrition."),
        ("New performance mgmt", "Annual reviews replaced by continuous Chapter Lead coaching + quarterly peer feedback."),
    ]
    y = Inches(2.0)
    for i, (title, body) in enumerate(steps):
        bg = LIGHT_BG if i % 2 == 0 else WHITE
        rect(sl, Inches(0.5), y, Inches(12.2), Inches(0.57), bg)
        rect(sl, Inches(0.5), y, Pt(5), Inches(0.57), ING_ORANGE)
        tb(sl, title, Inches(0.65), y + Pt(4), Inches(2.8), Inches(0.3),
           size=13, bold=True, color=ING_ORANGE)
        tb(sl, body, Inches(3.55), y + Pt(4), Inches(9.0), Inches(0.46),
           size=13, color=DARK_NAVY)
        y += Inches(0.6)

    rect(sl, Inches(0.5), Inches(5.75), Inches(12.2), Inches(0.55), MID_BLUE)
    tb(sl, "For NN Greece:  "
           "A full reapplication process may be too radical for a first step. "
           "A staged approach — pilot tribe first, then expand — reduces disruption. "
           "But HR must be involved from day one, not retrofitted.",
       Inches(0.7), Inches(5.82), Inches(11.8), Inches(0.44),
       size=13, italic=True, color=WHITE)


def s_leadership(sl):
    headers = ["ING Leadership Shift", "Before", "After"]
    rows = [
        ["Role", "Functional department head", "Tribe Lead / Chapter Lead / Alliance Lead"],
        ["Authority source", "Positional (hierarchy)", "Expertise + servant leadership"],
        ["Decision style", "Approve / reject upward escalations", "Set boundaries; let squads decide within them"],
        ["Success metric", "Budget spent, milestones hit", "OKRs achieved, customer outcomes improved"],
        ["People management", "Annual appraisal cycle", "Continuous Chapter Lead coaching"],
        ["Relationship to IT", "IT is a service provider", "Tech and business are one squad"],
        ["Meeting culture", "Status reporting to seniors", "Weekly tribe council; monthly alliance review"],
    ]
    cw = [Inches(2.6), Inches(4.4), Inches(5.0)]
    table(sl, headers, rows,
          Inches(0.5), Inches(1.3), Inches(12.2), Inches(4.5),
          col_widths=cw, size=13)

    rect(sl, Inches(0.5), Inches(6.0), Inches(12.2), Inches(0.75), ING_ORANGE)
    tb(sl, '"The biggest shift was not the org chart — it was that leaders had to let go. '
           'Many found this harder than any technical challenge." — ING Executive, McKinsey case study',
       Inches(0.7), Inches(6.07), Inches(11.8), Inches(0.62),
       size=13, italic=True, color=WHITE)


def s_results(sl):
    tb(sl, "What ING achieved — McKinsey-reported outcomes (2015–2017)",
       Inches(0.5), Inches(1.25), Inches(12.2), Inches(0.38),
       size=15, bold=True, color=DARK_NAVY)

    metrics = [
        ("Time-to-market", "Weeks → Days", "Features that took 3–6 months now released in days to weeks", ING_ORANGE),
        ("Employee engagement", "+20 points", "Engagement scores rose sharply in the first year post-transformation", GREEN),
        ("Customer NPS", "Significant increase", "Measurable NPS improvement on digital journeys within 12 months", TEAL),
        ("Productivity", "~30% improvement", "Same output with fewer coordination overhead costs", MID_BLUE),
    ]
    mw = Inches(2.95)
    mh = Inches(2.1)
    gap = Inches(0.22)
    sx = Inches(0.5)
    for i, (label, number, desc, col) in enumerate(metrics):
        x = sx + i * (mw + gap)
        rect(sl, x, Inches(1.8), mw, mh, col)
        tb(sl, label, x + Pt(8), Inches(1.87), mw - Pt(14), Inches(0.35),
           size=12, bold=True, color=WHITE)
        tb(sl, number, x + Pt(8), Inches(2.22), mw - Pt(14), Inches(0.6),
           size=20, bold=True, color=WHITE)
        tb(sl, desc, x + Pt(8), Inches(2.82), mw - Pt(14), Inches(0.85),
           size=11, color=WHITE)

    tb(sl, "Qualitative outcomes",
       Inches(0.5), Inches(4.1), Inches(5), Inches(0.38),
       size=14, bold=True, color=ING_ORANGE)
    ql = [
        "Leadership spent more time removing blockers than attending status reviews",
        "Cross-functional squads eliminated the blame culture between IT and business",
        "ING became a reference case cited by regulators as a model for digital resilience",
        "ING Group subsequently rolled out the model to other country units",
    ]
    bullets(sl, ql, Inches(0.5), Inches(4.6), Inches(5.8), Inches(1.7),
            size=13, color=DARK_NAVY)

    tb(sl, "What did NOT go perfectly",
       Inches(7.0), Inches(4.1), Inches(5.8), Inches(0.38),
       size=14, bold=True, color=MID_BLUE)
    not_perfect = [
        "First 6 months were turbulent — squads lacked clear OKRs",
        "Some Chapter Leads struggled with the dual role (manager + squad member)",
        "Legacy system dependencies slowed squad autonomy in practice",
        "Not all tribes reached the same maturity — variance was high",
    ]
    bullets(sl, not_perfect, Inches(7.0), Inches(4.6), Inches(5.8), Inches(1.7),
            size=13, color=DARK_NAVY)


def s_apply_nn(sl):
    rect(sl, Inches(0.5), Inches(1.25), Inches(12.2), Inches(0.5), MID_BLUE)
    tb(sl, "Direct applications from ING's experience to NN Greece",
       Inches(0.65), Inches(1.3), Inches(11.8), Inches(0.42),
       size=15, bold=True, color=WHITE)

    headers = ["ING Experience", "NN Greece Application"]
    rows = [
        ["Alliance layer for cross-tribe journeys",
         "Create a 'Protection Alliance' once 2+ tribes are running"],
        ["Compliance Chapter embedded in squads",
         "Assign a compliance specialist to each squad from day one — do not treat it as a gate"],
        ["Dedicated Agile Coach per squad at launch",
         "Budget for coaches in the pilot tribe; reduce ratio over time as squads mature"],
        ["Physical co-location mandatory",
         "Design the pilot tribe workspace before the first sprint — layout shapes culture"],
        ["OKRs deployed company → tribe → squad before go-live",
         "Run OKR design workshop as part of the 90-day preparation"],
        ["Chapter Lead selection as a visible culture signal",
         "Select Chapter Leads rigorously and publicly — wrong choices cost 12 months of trust"],
        ["Big-bang vs. staged — ING chose big-bang",
         "NN Greece should choose staged (pilot tribe first) — lower risk, preserves continuity"],
        ["Legacy systems acknowledged explicitly",
         "Map legacy system dependencies early; create a Platform Squad to own the interfaces"],
    ]
    cw = [Inches(5.6), Inches(6.5)]
    table(sl, headers, rows,
          Inches(0.5), Inches(1.9), Inches(12.2), Inches(4.7),
          col_widths=cw, size=12)


def s_three_horizons(sl):
    horizons = [
        (
            "HORIZON 1\nPrepare & Pilot\nMonths 1–6",
            "• Leadership alignment (ING model deep-dive)\n"
            "• OKR design: company → pilot tribe\n"
            "• Select pilot tribe: Digital & Data\n"
            "• Design workspace for co-location\n"
            "• Hire / develop Product Owners\n"
            "• Assign dedicated Agile Coaches\n"
            "• First sprints with full coaching support\n\n"
            "Outcome: one tribe operational,\nOKRs in place, early results visible.",
            ING_ORANGE,
        ),
        (
            "HORIZON 2\nScale & Stabilise\nMonths 7–18",
            "• Roll out to remaining 2–3 tribes\n"
            "• Launch Tribe Councils\n"
            "• Reform HR: Chapter Lead role formalised\n"
            "• Shift to tribe-level budgeting\n"
            "• Launch Alliance layer (if 3+ tribes active)\n"
            "• Connect to NN Group agile CoP\n"
            "• Measure and publish tribe OKR results\n\n"
            "Outcome: full organisation in\nnew model, HR aligned.",
            MID_BLUE,
        ),
        (
            "HORIZON 3\nOptimise & Lead\nMonth 18+",
            "• Evolve — no model is static\n"
            "• Extend to non-IT functions\n"
            "• Contribute to NN Group reference library\n"
            "• Introduce advanced practices:\n"
            "  – Continuous deployment\n"
            "  – AI-assisted squad tooling\n"
            "  – Customer journey analytics\n\n"
            "Outcome: agile is the operating\nsystem, not a programme.",
            GREEN,
        ),
    ]
    cw2 = Inches(3.9)
    ch2 = Inches(5.0)
    gap2 = Inches(0.22)
    sx = Inches(0.5)
    for i, (title, body, col) in enumerate(horizons):
        x = sx + i * (cw2 + gap2)
        rect(sl, x, Inches(1.3), cw2, Inches(0.75), col)
        rect(sl, x, Inches(2.05), cw2, ch2 - Inches(0.75), WHITE, line=col)
        tb(sl, title, x + Pt(7), Inches(1.35), cw2 - Pt(12), Inches(0.65),
           size=13, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
        tb(sl, body, x + Pt(10), Inches(2.12), cw2 - Pt(18), ch2 - Inches(0.95),
           size=12, color=DARK_NAVY)


def s_90_days(sl):
    weeks = [
        ("Weeks 1–2",  "Leadership ING deep-dive workshop — watch Kniberg videos, read McKinsey article, agree the 'why'"),
        ("Weeks 3–4",  "Map current state: existing teams, ownership, system dependencies, physical layout options"),
        ("Weeks 5–6",  "Design pilot tribe: squad boundaries, PO candidates, Chapter Lead candidates"),
        ("Weeks 7–8",  "OKR workshop: company OKRs → pilot tribe OKRs → squad OKRs (first cycle)"),
        ("Weeks 9–10", "Workspace redesign for co-location. Agile Fundamentals training for all pilot tribe members"),
        ("Weeks 11–12","First sprint. Dedicated coaches embedded. Daily stand-up + weekly tribe council. First retro."),
    ]
    y = Inches(1.3)
    for i, (wk, desc) in enumerate(weeks):
        bg = LIGHT_BG if i % 2 == 0 else WHITE
        hl = i == 5
        fb = ING_ORANGE if hl else MID_BLUE
        fc = WHITE if hl else WHITE
        tb_c = WHITE if hl else DARK_NAVY
        bg2 = ING_ORANGE if hl else bg
        rect(sl, Inches(0.5), y, Inches(1.5), Inches(0.52), fb)
        tb(sl, wk, Inches(0.55), y + Pt(5), Inches(1.38), Inches(0.42),
           size=12, bold=True, color=fc)
        rect(sl, Inches(2.1), y, Inches(10.7), Inches(0.52), bg2,
             line=GREY if not hl else None)
        tb(sl, desc, Inches(2.18), y + Pt(5), Inches(10.5), Inches(0.42),
           size=13, color=tb_c)
        y += Inches(0.52) + Pt(2)

    rect(sl, Inches(0.5), Inches(5.6), Inches(12.2), Inches(0.72), DARK_NAVY)
    tb(sl, "ING lesson:  "
           "The single biggest first-90-days failure mode is starting squads before OKRs exist. "
           "Squads without clear outcomes default to feature factories. Design the OKRs first.",
       Inches(0.7), Inches(5.67), Inches(11.8), Inches(0.6),
       size=13, italic=True, color=WHITE)


def s_risks(sl):
    headers = ["Risk", "ING Experience", "Likelihood\nfor NN Greece", "Mitigation"]
    rows = [
        ["Leadership reverts under pressure",
         "Happened in first 6 months — managers rebuilt shadow hierarchies",
         "High",
         "Executive coaching; OKR accountability; leadership retros"],
        ["Chapter Lead selection errors",
         "Wrong Chapter Leads caused 12-month trust deficits in some tribes",
         "High",
         "Rigorous, transparent selection; coach new Chapter Leads intensively"],
        ["Squads lack real OKRs",
         "First ING squads had no clear outcomes; worked on pet projects",
         "High",
         "OKRs designed before first sprint; PO accountable for outcome clarity"],
        ["Legacy systems block squad autonomy",
         "Core banking dependencies forced cross-squad coordination for months",
         "Medium",
         "Map dependencies early; create dedicated Platform Squad"],
        ["HR processes misaligned",
         "ING had to rebuild performance management from scratch",
         "Medium",
         "Involve HR in design from day one; formalise Chapter Lead role"],
        ["Physical co-location not prioritised",
         "Remote/split tribes underperformed co-located ones significantly",
         "Medium",
         "Workspace design is a pre-condition, not an afterthought"],
        ["Compliance treated as a gate, not embedded",
         "ING had compliance in squads; non-ING models that did not failed audit",
         "Low–Med",
         "Embed compliance specialist in each squad from the start"],
    ]
    cw = [Inches(2.5), Inches(3.0), Inches(1.4), Inches(5.1)]
    table(sl, headers, rows,
          Inches(0.4), Inches(1.3), Inches(12.5), Inches(5.5),
          col_widths=cw, size=11)


def s_takeaways(sl):
    items = [
        ("1", "ING is the most directly relevant agile transformation case for NN Greece — same regulatory context, same financial services complexity."),
        ("2", "The ING model builds on Spotify with one key addition: the Alliance layer for cross-tribe customer journeys."),
        ("3", "The HR transformation was as important as the structural one — Chapter Lead selection and performance management must change."),
        ("4", "Physical co-location was non-negotiable at ING. NN Greece's workspace design is a pre-condition for success."),
        ("5", "OKRs must exist before the first squad sprint — squads without clear outcomes become feature factories."),
        ("6", "ING ran a big-bang change. NN Greece should run a staged pilot — same model, lower risk, preserving business continuity."),
        ("7", "The ING Group subsequently rolled the model to other country units. NN Greece can be NN Group's reference case."),
    ]
    y = Inches(1.3)
    for num, text in items:
        rect(sl, Inches(0.5), y, Inches(0.55), Inches(0.5), ING_ORANGE)
        tb(sl, num, Inches(0.5), y + Pt(5), Inches(0.55), Inches(0.44),
           size=16, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
        tb(sl, text, Inches(1.2), y + Pt(6), Inches(11.6), Inches(0.44),
           size=13, color=DARK_NAVY)
        y += Inches(0.62)


def s_discussion(sl):
    rect(sl, Inches(0.5), Inches(1.25), Inches(12.2), Inches(0.5), MID_BLUE)
    tb(sl, "Discussion Questions",
       Inches(0.7), Inches(1.3), Inches(12.0), Inches(0.4),
       size=16, bold=True, color=WHITE)
    qs = [
        "ING dissolved all roles and made everyone reapply. What is NN Greece's appetite for that level of change — and what is a realistic alternative?",
        "ING embedded a compliance specialist in every squad. Which regulatory obligations would be most challenging to embed at squad level for NN Greece?",
        "ING's Chapter Lead selection was the most visible cultural signal. Who in NN Greece today would make credible Chapter Leads — and who would not?",
        "ING co-located tribes on dedicated floors. What would need to change in NN Greece's office to enable this?",
        "ING ran a big-bang change over one weekend. Should NN Greece follow the same approach or run a slower, staged pilot?",
    ]
    y = Inches(2.0)
    for i, q in enumerate(qs):
        rect(sl, Inches(0.5), y, Inches(0.55), Inches(0.55), ING_ORANGE)
        tb(sl, str(i + 1), Inches(0.5), y + Pt(5), Inches(0.55), Inches(0.45),
           size=15, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
        tb(sl, q, Inches(1.2), y + Pt(6), Inches(11.6), Inches(0.52),
           size=13, color=DARK_NAVY)
        y += Inches(0.72)


def s_closing(prs):
    sl = prs.slides.add_slide(prs.slide_layouts[6])
    rect(sl, 0, 0, SLIDE_W, SLIDE_H, DARK_NAVY)
    rect(sl, 0, 0, Inches(0.18), SLIDE_H, ING_ORANGE)
    rect(sl, 0, SLIDE_H - Inches(0.12), SLIDE_W, Inches(0.12), ING_ORANGE)
    tb(sl, "Thank you", Inches(0.5), Inches(2.2), Inches(12), Inches(1.2),
       size=48, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
    tb(sl, "Let's start the conversation.",
       Inches(0.5), Inches(3.5), Inches(12), Inches(0.6),
       size=22, italic=True, color=ING_ORANGE, align=PP_ALIGN.CENTER)
    rect(sl, Inches(4.5), Inches(4.3), Inches(4.3), Pt(2), TEAL)
    tb(sl, "[Your Name] · Agile Coach",
       Inches(0.5), Inches(4.7), Inches(12), Inches(0.4),
       size=14, color=WHITE, align=PP_ALIGN.CENTER)
    tb(sl, "NN Greece · ING Bank Case Study · April 2026",
       Inches(0.5), Inches(5.15), Inches(12), Inches(0.4),
       size=13, color=GREY, align=PP_ALIGN.CENTER)
    tb(sl, "Source: 'ING's agile transformation' — McKinsey Quarterly, 2017",
       Inches(0.5), Inches(5.6), Inches(12), Inches(0.35),
       size=11, italic=True, color=GREY, align=PP_ALIGN.CENTER)


def s_appendix_reading(sl):
    headers = ["Source", "What it covers", "Where to find it"]
    rows = [
        ["ING's agile transformation",
         "The primary McKinsey case study. Covers org design, HR transformation, results.",
         "McKinsey Quarterly, Jan 2017 (mckinsey.com)"],
        ["Spotify Engineering Culture (Kniberg)",
         "The original model that inspired ING. 2-part video series.",
         "YouTube — 'Spotify Engineering Culture Part 1 & 2'"],
        ["Accelerate (Forsgren, Humble, Kim)",
         "Data-driven proof of what high-performing agile orgs do differently.",
         "Book / PDF — ISBN 978-1942788331"],
        ["Team Topologies (Skelton & Pais)",
         "Modern evolution of squad/tribe design, directly compatible with ING model.",
         "Book — teamtopologies.com"],
        ["ING Group Annual Report 2016–2017",
         "First public reporting on agile transformation results and employee impact.",
         "ing.com/investor-relations"],
        ["NN Group Agile CoP",
         "Internal community with ING learnings already available at group level.",
         "NN Group intranet / contact group agile lead"],
    ]
    cw = [Inches(2.8), Inches(5.0), Inches(4.3)]
    table(sl, headers, rows,
          Inches(0.4), Inches(1.3), Inches(12.5), Inches(5.2),
          col_widths=cw, size=12)


def s_glossary(sl):
    terms = [
        ("Squad", "6–10 cross-functional people owning one customer journey end-to-end"),
        ("Tribe", "40–150 people across related squads sharing one business domain"),
        ("Chapter", "Same-skill peers within a tribe; led by a Chapter Lead (people manager + squad member)"),
        ("Alliance", "ING addition: multiple tribes grouped around a major end-to-end customer journey"),
        ("Product Owner", "Sets squad backlog; accountable for business outcomes"),
        ("Chapter Lead", "Part-time people manager for a skill chapter; also working squad member"),
        ("Tribe Lead", "Servant leader of a tribe; removes blockers, sets tribe direction"),
        ("Alliance Lead", "Senior cross-functional leader coordinating multiple tribes"),
        ("OKR", "Objectives & Key Results — goal framework linking company direction to squad work"),
        ("Agile Coach", "Embedded facilitator; coaches squad on practices; not a project manager"),
    ]
    col_break = 5
    for i, (term, defn) in enumerate(terms):
        col = 0 if i < col_break else 1
        row = i if i < col_break else i - col_break
        x = Inches(0.5) if col == 0 else Inches(6.8)
        cy = Inches(1.3) + row * Inches(1.0)
        accent = ING_ORANGE if col == 0 else MID_BLUE
        rect(sl, x, cy, Inches(6.0), Inches(0.35), accent)
        tb(sl, term, x + Pt(6), cy + Pt(3), Inches(5.8), Inches(0.3),
           size=12, bold=True, color=WHITE)
        tb(sl, defn, x + Pt(6), cy + Inches(0.37), Inches(5.8), Inches(0.55),
           size=11, color=DARK_NAVY)

# ── Build ──────────────────────────────────────────────────────────────────────

def build():
    prs = Presentation()
    prs.slide_width = SLIDE_W
    prs.slide_height = SLIDE_H

    s_title(prs)
    content(prs, "Agenda", s_agenda)

    divider(prs, "PART 1", "Why the ING Story Matters",
            "The most relevant agile transformation case for NN Greece")
    content(prs, "Why the ING Bank Case Study?", s_why_ing)
    content(prs, "ING in 2014 — Why Transform?", s_ing_context)

    divider(prs, "PART 2", "The ING Model",
            "Squads · Tribes · Chapters · Alliances")
    content(prs, "Overview — The Four Building Blocks", s_model_overview)
    content(prs, "The Squad — Primary Unit of Delivery", s_squad)
    content(prs, "The Tribe — Cluster of Related Squads", s_tribe)
    content(prs, "The Chapter — People Development Reimagined", s_chapter)
    content(prs, "The Alliance — ING's Own Addition", s_alliance)

    divider(prs, "PART 3", "ING vs. Spotify — Key Differences",
            "Same foundation, different adaptations for financial services")
    content(prs, "ING Model vs. Spotify Model — Side by Side", s_ing_vs_spotify)

    divider(prs, "PART 4", "HR & Leadership Transformation",
            "The hardest — and most important — part of the ING story")
    content(prs, "The HR Transformation at ING", s_hr_transformation)
    content(prs, "What Changed for Leadership at ING", s_leadership)

    divider(prs, "PART 5", "Results & What NN Greece Can Apply",
            "What ING achieved — and what to take directly")
    content(prs, "Results — What ING Actually Achieved", s_results)
    content(prs, "Applying ING's Experience to NN Greece", s_apply_nn)

    divider(prs, "PART 6", "The NN Greece Journey",
            "Roadmap · First 90 Days · Risks")
    content(prs, "The Transformation Journey — 3 Horizons", s_three_horizons)
    content(prs, "Where to Start — The First 90 Days", s_90_days)
    content(prs, "Risks and How to Mitigate Them", s_risks)

    divider(prs, "PART 7", "Takeaways & Discussion", "")
    content(prs, "Key Takeaways", s_takeaways)
    content(prs, "Discussion Questions", s_discussion)

    s_closing(prs)

    divider(prs, "APPENDIX", "Further Reading & Glossary", "")
    content(prs, "Appendix A — Further Reading", s_appendix_reading)
    content(prs, "Appendix B — Glossary", s_glossary)

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "nn-greece-ing-agile-transformation.pptx")
    prs.save(out)
    print(f"Saved: {out}")
    print(f"Slides: {len(prs.slides)}")


if __name__ == "__main__":
    build()
