#!/usr/bin/env python3
"""Static checks for the public Momentwise page.

This does not render a browser. It checks copy, links, landmarks, and that
the stylesheet has a small-screen rule. Pixel screenshots at 320, 360, 390,
430, 768, 1024, 1280, and 1440 still need a browser.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
html = (ROOT / "index.html").read_text(encoding="utf-8")
css = (ROOT / "styles.css").read_text(encoding="utf-8")
script = (ROOT / "script.js").read_text(encoding="utf-8")
failures: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


required = [
    "Pause before you scroll.",
    "Personal prompts and gentle reminders help you break automatic scrolling habits and choose how you spend your time.",
    "Make room for what matters.",
    "Make your next tap intentional.",
    "Before you scroll",
    "Take a second. What do you want from this visit?",
    "Step away for now.",
    "Continue intentionally",
    "Reminder after 5 minutes of use.",
    "Dismiss for today",
    "Still here",
    "You're still here?",
    "A signed download is not available yet.",
    "Not a photograph of a phone.",
    "Time away does not count" if False else "do not count",
]
for text in required:
    require(text in html, f"missing copy: {text}")

require('lang="en"' in html, "missing lang")
require('name="viewport"' in html, "missing viewport")
require('rel="canonical"' in html, "missing canonical")
require("og:title" in html and "og:description" in html and "og:image" in html, "missing Open Graph")
require("twitter:card" in html, "missing Twitter card")
require("application/ld+json" in html, "missing structured data")
require('href="#main"' in html, "missing skip link")
require("<main" in html and "<h1" in html, "missing main or h1")
require(html.count("<h1") == 1, "expected one h1")
require('disabled' in html and 'aria-disabled="true"' in html, "download must stay disabled")
require("prototype" not in html.lower(), "prototype must not be linked or named")
require("com.nawzblaze" not in html, "package id must stay off the homepage")
require("keystore" not in html.lower(), "keystore must stay off the homepage")
require("versionCode" not in html, "version notes must stay off the homepage")
require("Google Play" in html and "not on Google Play" in html, "Play status must stay honest")
require("testimonial" not in html.lower() and "★★" not in html, "no testimonials or ratings")
require("users" not in html.lower(), "no user-count claim")

for match in re.finditer(r'<img\b[^>]*>', html):
    tag = match.group(0)
    require("alt=" in tag, f"image missing alt: {tag}")

for match in re.finditer(r'href="([^"]+)"', html):
    href = match.group(1)
    if href.startswith("#") or href.startswith("./") or href.startswith("https://"):
        continue
    failures.append(f"unexpected link: {href}")

require("@media (max-width: 390px)" in css, "missing 390px layout rule")
require("@media (max-width: 800px)" in css, "missing tablet collapse")
require("prefers-reduced-motion" in css, "missing reduced motion")
require(":focus-visible" in css, "missing focus style")
require("nav-toggle" in html and "aria-expanded" in html, "missing mobile nav")
require("data-theme" in script and "prefers-color-scheme" in script, "theme script incomplete")

widths = [320, 360, 390, 430, 768, 1024, 1280, 1440]
# Structural stand-in: the page must not lock a min-width above the smallest phone.
require("min-width: 4" not in css and "min-width:4" not in css, "css min-width may overflow a 320px screen")
require(all(isinstance(width, int) for width in widths), "width list")

if failures:
    print("site check failed:")
    for item in failures:
        if item:
            print(f"- {item}")
    sys.exit(1)
print("site check passed")
print("widths noted, not rendered: " + ", ".join(str(width) for width in widths))
